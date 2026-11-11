package com.example.kalanim

import android.content.Context
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object KalanStorage {
    private const val PREF_NAME = "kalan_prefs"
    private const val KEY_BALANCE = "balance"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_SMS = "sms"
    private const val KEY_MB = "mb"
    private const val KEY_EXPIRY_EPOCH = "expiry_epoch"
    private const val KEY_UPDATED_EPOCH = "updated_epoch"
    private const val KEY_SUMMARY = "summary"
    private const val KEY_IS_QUERYING = "is_querying"

    fun saveStatus(context: Context, status: Status, summary: String = "") {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = getStatus(context)
        // Keep existing info if new info doesn't overwrite it
        val merged = existing.merge(status)

        val finalSummary = if (summary.isNotEmpty()) summary else Parser.summary(merged)

        sp.edit().apply {
            merged.balanceTl?.let { putFloat(KEY_BALANCE, it.toFloat()) }
            merged.minutes?.let { putInt(KEY_MINUTES, it) }
            merged.sms?.let { putInt(KEY_SMS, it) }
            merged.mb?.let { putInt(KEY_MB, it) }
            merged.expiry?.let {
                val epoch = it.atZone(ZoneId.systemDefault()).toEpochSecond()
                putLong(KEY_EXPIRY_EPOCH, epoch)
            }
            putLong(KEY_UPDATED_EPOCH, System.currentTimeMillis())
            putString(KEY_SUMMARY, finalSummary)
            putBoolean(KEY_IS_QUERYING, false)
            apply()
        }
    }

    fun setQuerying(context: Context, querying: Boolean) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_IS_QUERYING, querying).apply()
    }

    fun isQuerying(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_IS_QUERYING, false)

    fun getStatus(context: Context): Status {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val balance = if (sp.contains(KEY_BALANCE)) sp.getFloat(KEY_BALANCE, 0f).toDouble() else null
        val minutes = if (sp.contains(KEY_MINUTES)) sp.getInt(KEY_MINUTES, 0) else null
        val sms = if (sp.contains(KEY_SMS)) sp.getInt(KEY_SMS, 0) else null
        val mb = if (sp.contains(KEY_MB)) sp.getInt(KEY_MB, 0) else null
        val expiryEpoch = if (sp.contains(KEY_EXPIRY_EPOCH)) sp.getLong(KEY_EXPIRY_EPOCH, 0L) else null
        val expiry = expiryEpoch?.let {
            LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault())
        }
        return Status(balance, minutes, sms, mb, expiry)
    }

    fun getLastUpdatedTime(context: Context): Long =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getLong(KEY_UPDATED_EPOCH, 0L)

    fun getSummary(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_SUMMARY, "") ?: ""
}
