package com.example.kalanim

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Status(
    val balanceTl: Double? = null,
    val minutes: Int? = null,
    val sms: Int? = null,
    val mb: Int? = null,
    val expiry: LocalDateTime? = null,
) {
    val hasPackage get() = minutes != null || sms != null || mb != null || expiry != null

    fun merge(o: Status) = Status(
        balanceTl ?: o.balanceTl,
        minutes ?: o.minutes,
        sms ?: o.sms,
        mb ?: o.mb,
        expiry ?: o.expiry,
    )
}

object Parser {
    private val ic = setOf(RegexOption.IGNORE_CASE)
    private val tlRe = Regex("""(?:(\d+(?:[.,]\d+)*)\s*TL\s+bakiye|bakiye[^\d\n]{1,25}?(\d+(?:[.,]\d+)*)\s*TL)""", ic)
    private val smsRe = Regex("""(\d+)\s+adet[^,.]*?SMS""", ic)
    private val minRe = Regex("""(\d+)\s+dakika""", ic)
    private val mbRe = Regex("""(\d+(?:[.,]\d+)?)\s*MB""", ic)
    private val gbRe = Regex("""(\d+(?:[.,]\d+)?)\s*GB""", ic)
    private val dateRe = Regex("""(\d{2}\.\d{2}\.\d{4})\s+(\d{2}:\d{2})""")
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
    private val outFmt = DateTimeFormatter.ofPattern("d MMMM EEEE 'saat' HH:mm", Locale("tr", "TR"))

    /** Bimcell / Türk Telekom operatör cevaplarını işler. */
    fun parse(body: String, sender: String = ""): Status {
        val isOperator = body.contains("B001", ignoreCase = true) ||
            sender.contains("5555") ||
            sender.contains("BIM", ignoreCase = true) ||
            sender.contains("TELEKOM", ignoreCase = true)
        if (!isOperator) return Status()

        val tl = tlRe.find(body)?.let { m ->
            val v = m.groupValues[1].ifEmpty { m.groupValues[2] }
            toDouble(v)
        }
        val expiry = dateRe.findAll(body).mapNotNull { toDate(it.groupValues[1], it.groupValues[2]) }.minOrNull()
        return Status(tl, sum(minRe, body), sum(smsRe, body), sumMb(body), expiry)
    }

    private fun sum(re: Regex, s: String): Int? {
        val m = re.findAll(s).toList()
        return if (m.isEmpty()) null else m.sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
    }

    private fun sumMb(s: String): Int? {
        val mbMatches = mbRe.findAll(s).toList()
        val gbMatches = gbRe.findAll(s).toList()
        if (mbMatches.isEmpty() && gbMatches.isEmpty()) return null

        val mbTotal = mbMatches.sumOf { toDouble(it.groupValues[1])?.toInt() ?: 0 }
        val gbTotal = gbMatches.sumOf { ((toDouble(it.groupValues[1]) ?: 0.0) * 1024).toInt() }
        return mbTotal + gbTotal
    }

    private fun toDouble(s: String): Double? {
        val t = when {
            s.contains(',') -> s.replace(".", "").replace(',', '.')
            s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        return t.toDoubleOrNull()
    }

    private fun toDate(d: String, t: String): LocalDateTime? =
        try { LocalDateTime.parse("$d $t", dateFmt) } catch (e: Exception) { null }

    private fun fmt(d: Double, decimals: Int): String =
        if (d == Math.floor(d)) d.toLong().toString()
        else String.format(Locale("tr", "TR"), "%.${decimals}f", d)

    private fun internet(mb: Int): String =
        if (mb >= 1024) "${fmt(Math.round(mb / 1024.0 * 10) / 10.0, 1)} gigabayt internet"
        else "$mb megabayt internet"

    fun summary(s: Status, now: LocalDateTime = LocalDateTime.now()): String {
        val lines = mutableListOf<String>()
        s.balanceTl?.let { lines += "Hattında ${fmt(it, 2)} lira bakiye var." }

        val hak = mutableListOf<String>()
        s.minutes?.let { hak += "$it dakika konuşma" }
        s.sms?.let { hak += "$it SMS" }
        s.mb?.let { hak += internet(it) }
        if (hak.isNotEmpty()) lines += "Kalan hakların: " + hak.joinToString(", ") + "."

        s.expiry?.let {
            val days = ChronoUnit.DAYS.between(now.toLocalDate(), it.toLocalDate())
            val left = when {
                days < 0 -> "Süresi dolmuş görünüyor."
                days == 0L -> "Bugün bitiyor."
                days == 1L -> "Yarın bitiyor."
                else -> "$days gün kaldı."
            }
            lines += "Paket sonu: ${it.format(outFmt)}. $left"
        }
        return lines.joinToString("\n")
    }
}
