package com.example.kalanim

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Telephony
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
import java.util.Locale

class KalanQueryService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val SHORT_CODE = "5555"
        const val TIMEOUT_MS = 35_000L
        const val PARTIAL_TIMEOUT_MS = 12_000L
        const val CHANNEL_ID = "kalan_query_channel"
        const val NOTIF_ID = 1001

        fun startQuery(context: Context) {
            val intent = Intent(context, KalanQueryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var collected = Status()
    private var gotBalance = false
    private var gotPackage = false
    private var isFinished = false
    private var textToSpeak: String? = null

    private val timeoutRunnable = Runnable { finishQuery() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Sorgulanıyor, lütfen bekleyin…"))

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kalanim:query_wakelock")?.apply {
            acquire(90_000L) // Safe wakelock for entire query + speech
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasPermissions()) {
            KalanStorage.setQuerying(this, false)
            WidgetUpdater.updateAllWidgets(this)
            val openApp = Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(openApp)
            stopSelf()
            return START_NOT_STICKY
        }

        KalanStorage.setQuerying(this, true)
        WidgetUpdater.updateAllWidgets(this)

        collected = Status()
        gotBalance = false
        gotPackage = false
        isFinished = false

        registerSmsReceiver()
        sendSmsQueries()
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        return START_NOT_STICKY
    }

    private fun hasPermissions(): Boolean {
        return checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendSmsQueries() {
        try {
            val sm = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sm.sendTextMessage(SHORT_CODE, null, "KALAN", null, null)
            sm.sendTextMessage(SHORT_CODE, null, "KALAN TL", null, null)
        } catch (e: Exception) {
            finishWithText("SMS gönderilemedi: ${e.message}")
        }
    }

    private fun registerSmsReceiver() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
                val sender = msgs.firstOrNull()?.displayOriginatingAddress
                    ?: msgs.firstOrNull()?.originatingAddress
                    ?: ""
                val body = msgs.joinToString("") { it.messageBody ?: "" }
                onSms(sender, body)
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

        // Both arrived: finish immediately
        if (gotBalance && gotPackage) {
            finishQuery()
            return
        }

        // Wait only 12 seconds for the second SMS
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, PARTIAL_TIMEOUT_MS)
    }

    private fun finishQuery() {
        if (isFinished) return
        isFinished = true
        cleanupReceiver()

        val text: String
        if (!gotBalance && !gotPackage) {
            text = "Cevap gelmedi. Biraz sonra tekrar dene."
        } else {
            text = Parser.summary(collected)
            KalanStorage.saveStatus(this, collected, text)
        }

        KalanStorage.setQuerying(this, false)
        WidgetUpdater.updateAllWidgets(this)

        updateNotification(text)
        speakText(text)
    }

    private fun finishWithText(text: String) {
        if (isFinished) return
        isFinished = true
        cleanupReceiver()
        KalanStorage.setQuerying(this, false)
        WidgetUpdater.updateAllWidgets(this)
        speakText(text)
    }

    private fun speakText(text: String) {
        textToSpeak = text
        if (ttsReady) {
            doSpeak(text)
        } else {
            // Wait up to 10s for TTS onInit
            handler.postDelayed({ stopServiceGracefully() }, 10_000L)
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttrs)
                    .build()
                focusRequest = req
                audioManager?.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun doSpeak(text: String) {
        requestAudioFocus()

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                // Critical fix: Wait 3 seconds for audio hardware buffer to completely finish playback
                handler.postDelayed({ stopServiceGracefully() }, 3000L)
            }

            override fun onError(utteranceId: String?) {
                handler.postDelayed({ stopServiceGracefully() }, 1000L)
            }
        })

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kalan_widget_query")

        // Safety timeout scaled to text length (never cuts off prematurely)
        val safeDelay = maxOf(45_000L, (text.length * 200L) + 20_000L)
        handler.postDelayed({ stopServiceGracefully() }, safeDelay)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale("tr", "TR"))
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            textToSpeak?.let {
                if (ttsReady) doSpeak(it)
                textToSpeak = null
            }
        }
    }

    private fun stopServiceGracefully() {
        cleanupReceiver()
        abandonAudioFocus()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupReceiver() {
        handler.removeCallbacks(timeoutRunnable)
        receiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kalanım Sorgulama",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Bakiye ve paket sorgulama durumu"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Kalanım")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(content: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIF_ID, buildNotification(content))
    }

    override fun onDestroy() {
        cleanupReceiver()
        abandonAudioFocus()
        try {
            tts.shutdown()
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
