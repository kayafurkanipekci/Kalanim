package com.example.kalanim

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object WidgetUpdater {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("tr", "TR"))
    private val dateFmt = DateTimeFormatter.ofPattern("d MMMM", Locale("tr", "TR"))

    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val status = KalanStorage.getStatus(context)
        val isQuerying = KalanStorage.isQuerying(context)
        val lastUpdated = KalanStorage.getLastUpdatedTime(context)

        // 1. Quick Widget
        val quickIds = appWidgetManager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))
        if (quickIds != null && quickIds.isNotEmpty()) {
            val views = buildQuickViews(context, isQuerying)
            appWidgetManager.updateAppWidget(quickIds, views)
        }

        // 2. Medium Widget
        val mediumIds = appWidgetManager.getAppWidgetIds(ComponentName(context, MediumWidgetProvider::class.java))
        if (mediumIds != null && mediumIds.isNotEmpty()) {
            val views = buildMediumViews(context, status, isQuerying, lastUpdated)
            appWidgetManager.updateAppWidget(mediumIds, views)
        }

        // 3. Large Widget
        val largeIds = appWidgetManager.getAppWidgetIds(ComponentName(context, LargeWidgetProvider::class.java))
        if (largeIds != null && largeIds.isNotEmpty()) {
            val views = buildLargeViews(context, status, isQuerying, lastUpdated)
            appWidgetManager.updateAppWidget(largeIds, views)
        }
    }

    private fun getQueryPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, KalanQueryReceiver::class.java).apply {
            action = KalanQueryReceiver.ACTION_START_QUERY
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getOpenAppPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildQuickViews(context: Context, isQuerying: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick)
        val queryPi = getQueryPendingIntent(context, 101)
        views.setOnClickPendingIntent(R.id.quickWidgetRoot, queryPi)
        views.setOnClickPendingIntent(R.id.quickBtn, queryPi)

        views.setTextViewText(
            R.id.quickStatusText,
            if (isQuerying) "Sorgulanıyor…" else "Sorgula"
        )
        return views
    }

    fun buildMediumViews(
        context: Context,
        status: Status,
        isQuerying: Boolean,
        lastUpdated: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_medium)
        val queryPi = getQueryPendingIntent(context, 102)
        val openAppPi = getOpenAppPendingIntent(context, 202)

        views.setOnClickPendingIntent(R.id.mediumRefreshBtn, queryPi)
        views.setOnClickPendingIntent(R.id.mediumWidgetRoot, openAppPi)

        // Balance
        val balanceStr = status.balanceTl?.let { formatTl(it) } ?: "-- TL"
        views.setTextViewText(R.id.mediumBalanceText, balanceStr)

        // Days left
        val daysStr = formatDaysLeft(status.expiry)
        views.setTextViewText(R.id.mediumDaysText, daysStr)

        // Button / Footer text
        views.setTextViewText(
            R.id.mediumRefreshBtn,
            if (isQuerying) "Bekleyin…" else "Sorgula"
        )

        val footerText = if (isQuerying) {
            "Sorgulanıyor, lütfen bekleyin…"
        } else if (lastUpdated > 0) {
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdated), ZoneId.systemDefault())
            "Güncellendi: ${dt.format(timeFmt)}" + (status.expiry?.let { " • Bitiş: ${it.format(dateFmt)}" } ?: "")
        } else {
            "Sorgulamak için butona dokunun"
        }
        views.setTextViewText(R.id.mediumFooterText, footerText)

        return views
    }

    fun buildLargeViews(
        context: Context,
        status: Status,
        isQuerying: Boolean,
        lastUpdated: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_large)
        val queryPi = getQueryPendingIntent(context, 103)
        val openAppPi = getOpenAppPendingIntent(context, 203)

        views.setOnClickPendingIntent(R.id.largeRefreshBtn, queryPi)
        views.setOnClickPendingIntent(R.id.largeWidgetRoot, openAppPi)

        // Balance
        val balanceStr = status.balanceTl?.let { formatTl(it) } ?: "-- TL"
        views.setTextViewText(R.id.largeBalanceText, balanceStr)

        // Days left
        val daysStr = formatDaysLeft(status.expiry)
        views.setTextViewText(R.id.largeDaysText, daysStr)

        // Minutes
        val minStr = status.minutes?.let { "$it DK" } ?: "-- DK"
        views.setTextViewText(R.id.largeMinutesText, minStr)

        // Internet
        val netStr = status.mb?.let { formatInternet(it) } ?: "-- GB"
        views.setTextViewText(R.id.largeInternetText, netStr)

        // SMS
        val smsStr = status.sms?.let { "$it SMS" } ?: "-- SMS"
        views.setTextViewText(R.id.largeSmsText, smsStr)

        // Button text
        views.setTextViewText(
            R.id.largeRefreshBtn,
            if (isQuerying) "Bekleyin…" else "Sorgula & Dinle"
        )

        // Footer
        val footerText = if (isQuerying) {
            "Sorgulanıyor, yanıt bekleniyor…"
        } else if (lastUpdated > 0) {
            val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdated), ZoneId.systemDefault())
            "Son Güncelleme: ${dt.format(timeFmt)}" + (status.expiry?.let { " • Bitiş: ${it.format(dateFmt)}" } ?: "")
        } else {
            "Sorgulamak için butona dokunun"
        }
        views.setTextViewText(R.id.largeFooterText, footerText)

        return views
    }

    private fun formatTl(d: Double): String {
        return if (d == Math.floor(d)) "${d.toLong()} TL"
        else String.format(Locale("tr", "TR"), "%.2f TL", d)
    }

    private fun formatDaysLeft(expiry: LocalDateTime?): String {
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
}

