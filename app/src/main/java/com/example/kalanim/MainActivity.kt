package com.example.kalanim

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private companion object {
        const val SHORT_CODE = "5555"
        const val TIMEOUT_MS = 35_000L
        const val PARTIAL_TIMEOUT_MS = 12_000L
        const val REQ_PERMS = 1
    }

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private lateinit var queryBtn: Button
    private lateinit var replayBtn: Button
    private lateinit var statusView: TextView

    private lateinit var tvBalance: TextView
    private lateinit var tvDays: TextView
    private lateinit var tvMinutes: TextView
    private lateinit var tvInternet: TextView
    private lateinit var tvSms: TextView
    private lateinit var tvExpiry: TextView
    private lateinit var tvUpdated: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var collected = Status()
    private var gotBalance = false
    private var gotPackage = false
    private var lastText = ""
    private val timeout = Runnable { finishQuery() }

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("tr", "TR"))
    private val fullDateFmt = DateTimeFormatter.ofPattern("d MMMM EEEE 'saat' HH:mm", Locale("tr", "TR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        queryBtn = findViewById(R.id.queryBtn)
        replayBtn = findViewById(R.id.replayBtn)
        statusView = findViewById(R.id.statusView)

        tvBalance = findViewById(R.id.tvBalance)
        tvDays = findViewById(R.id.tvDays)
        tvMinutes = findViewById(R.id.tvMinutes)
        tvInternet = findViewById(R.id.tvInternet)
        tvSms = findViewById(R.id.tvSms)
        tvExpiry = findViewById(R.id.tvExpiry)
        tvUpdated = findViewById(R.id.tvUpdated)

        tts = TextToSpeech(this, this)

        // Show stored data immediately on launch
        val status = KalanStorage.getStatus(this)
        val summary = KalanStorage.getSummary(this)
        renderStatus(status, summary)

        queryBtn.setOnClickListener { startQuery() }
        replayBtn.setOnClickListener {
            if (lastText.isNotEmpty()) speak(lastText)
        }
    }

    override fun onResume() {
        super.onResume()
        val status = KalanStorage.getStatus(this)
        val summary = KalanStorage.getSummary(this)
        renderStatus(status, summary)
    }

    private fun renderStatus(status: Status, summary: String = "") {
        tvBalance.text = status.balanceTl?.let { formatTl(it) } ?: "-- TL"
        tvDays.text = formatDays(status.expiry)
        tvMinutes.text = status.minutes?.let { "$it DK" } ?: "-- DK"
        tvInternet.text = status.mb?.let { formatInternet(it) } ?: "-- GB"
        tvSms.text = status.sms?.let { "$it SMS" } ?: "-- SMS"

        tvExpiry.text = status.expiry?.let {
            it.format(fullDateFmt)
        } ?: "Bilgi bekleniyor"

        val lastUpdated = KalanStorage.getLastUpdatedTime(this)
        if (lastUpdated > 0) {
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdated), ZoneId.systemDefault())
            tvUpdated.text = "Son güncelleme: ${dt.format(timeFmt)}"
        } else {
            tvUpdated.text = "Son güncelleme: Henüz yapılmadı"
        }

        lastText = if (summary.isNotEmpty()) summary else Parser.summary(status)
    }

    private fun formatTl(d: Double): String {
        return if (d == Math.floor(d)) "${d.toLong()} TL"
        else String.format(Locale("tr", "TR"), "%.2f TL", d)
    }

    private fun formatDays(expiry: LocalDateTime?): String {
        if (expiry == null) return "-- gün"
        val today = LocalDateTime.now().toLocalDate()
        val expDate = expiry.toLocalDate()
        val days = ChronoUnit.DAYS.between(today, expDate)
        return when {
            days < 0 -> "Bitti"
            days == 0L -> "Bugün"
            days == 1L -> "Yarın"
            else -> "$days gün"
        }
    }

    private fun formatInternet(mb: Int): String {
        return if (mb >= 1024) {
            val gb = Math.round(mb / 1024.0 * 10) / 10.0
            if (gb == Math.floor(gb)) "${gb.toLong()} GB"
            else String.format(Locale("tr", "TR"), "%.1f GB", gb)
        } else {
            "$mb MB"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale("tr", "TR"))
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kalan_main")
    }

    private fun hasPerms() =
        checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == REQ_PERMS) {
            if (hasPerms()) startQuery() else statusView.text = "SMS izni verilmedi, uygulama çalışamaz."
        }
    }

    private fun startQuery() {
        if (!hasPerms()) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS), REQ_PERMS)
            return
        }
        collected = Status()
        gotBalance = false
        gotPackage = false
        statusView.text = "Sorgulanıyor, lütfen bekleyin…"
        queryBtn.isEnabled = false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        KalanStorage.setQuerying(this, true)
        WidgetUpdater.updateAllWidgets(this)

        registerSmsReceiver()
        try {
            val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sm.sendTextMessage(SHORT_CODE, null, "KALAN", null, null)
            sm.sendTextMessage(SHORT_CODE, null, "KALAN TL", null, null)
        } catch (e: Exception) {
            statusView.text = "SMS gönderilemedi: ${e.message}"
            cleanup()
            KalanStorage.setQuerying(this, false)
            WidgetUpdater.updateAllWidgets(this)
            return
        }
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    private fun registerSmsReceiver() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
                val sender = msgs.firstOrNull()?.displayOriginatingAddress
                    ?: msgs.firstOrNull()?.originatingAddress
                    ?: ""
                onSms(sender, msgs.joinToString("") { it.messageBody ?: "" })
            }
        }
        val f = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply { priority = 999 }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(r, f)
        receiver = r
    }

    private fun onSms(sender: String, body: String) {
        val st = Parser.parse(body, sender)
        if (st.balanceTl != null) gotBalance = true
        if (st.hasPackage) gotPackage = true
        if (st.balanceTl == null && !st.hasPackage) return

        collected = collected.merge(st)

        if (gotBalance && gotPackage) {
            finishQuery()
            return
        }

        // Wait only 12 seconds for the second SMS
        statusView.text = "Bilgiler alınıyor…"
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, PARTIAL_TIMEOUT_MS)
    }

    private fun cleanup() {
        handler.removeCallbacks(timeout)
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        queryBtn.isEnabled = true
    }

    private fun finishQuery() {
        cleanup()
        statusView.text = ""
        val text: String
        if (!gotBalance && !gotPackage) {
            text = "Cevap gelmedi. Biraz sonra tekrar dene."
        } else {
            text = Parser.summary(collected)
            KalanStorage.saveStatus(this, collected, text)
        }

        KalanStorage.setQuerying(this, false)
        WidgetUpdater.updateAllWidgets(this)

        renderStatus(KalanStorage.getStatus(this), text)
        speak(text)
    }

    override fun onDestroy() {
        cleanup()
        tts.shutdown()
        super.onDestroy()
    }
}
