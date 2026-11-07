package com.pylikv.queuewatch

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.*

enum class AlertType { POSITION, FORECAST, CALLED }

data class QueueAlert(val type: AlertType, val title: String, val message: String)

class QueueAlertManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repeatJob: Job? = null
    private var toneJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechReady = false
    private var pendingSpeech: QueueAlert? = null
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var originalVolume: Int? = null
    private var raisedVolume: Int? = null
    @Volatile var activeAlert: QueueAlert? = null
        private set

    init {
        toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        textToSpeech = TextToSpeech(context) { status ->
            scope.launch {
                speechReady = status == TextToSpeech.SUCCESS
                if (speechReady) {
                    textToSpeech?.setAudioAttributes(attributes)
                    textToSpeech?.setSpeechRate(0.95f)
                    pendingSpeech?.takeIf { it == activeAlert }?.let { speak(it) }
                }
            }
        }
    }

    fun trigger(type: AlertType, message: String) {
        // Publish synchronously: the IO monitoring loop reads this immediately
        // after trigger() to decide whether the notification should stay visible.
        if (activeAlert != null && type != AlertType.CALLED) return
        val title = L.text(when (type) {
            AlertType.POSITION -> R.string.position_alert_title
            AlertType.FORECAST -> R.string.forecast_alert_title
            AlertType.CALLED -> R.string.called_alert_title
        })
        val alert = QueueAlert(type, title, message)
        activeAlert = alert
        scope.launch {
            if (activeAlert != alert) return@launch
            stopPlayback(clearActive = false)
            raiseAlarmVolume()
            repeatJob = scope.launch {
                repeat(5) {
                    if (activeAlert != alert) return@launch
                    playAlert(alert)
                    delay(60_000L)
                }
                // Let the fifth signal and speech finish before clearing it.
                if (activeAlert == alert) stopPlayback()
            }
        }
    }

    private fun playAlert(alert: QueueAlert) {
        toneJob?.cancel()
        runCatching {
            vibrator?.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 450, 180, 450, 180, 650), -1), attributes)
        }
        toneJob = scope.launch {
            for (duration in listOf(450, 450, 650)) {
                if (activeAlert != alert) return@launch
                runCatching { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, duration) }
                // Wait for each complete pulse; overlapping startTone calls truncate it.
                delay(duration.toLong() + 180L)
            }
            if (activeAlert == alert) speak(alert)
        }
    }

    private fun speak(alert: QueueAlert) {
        if (!speechReady) { pendingSpeech = alert; return }
        pendingSpeech = null
        runCatching {
            val engine = textToSpeech ?: return
            if (engine.setLanguage(L.locale()) < TextToSpeech.LANG_AVAILABLE) return
            engine.setAudioAttributes(attributes)
            engine.speak(L.stored(alert.message), TextToSpeech.QUEUE_FLUSH,
                Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }, "QueueWatchAlert")
        }
    }

    private fun raiseAlarmVolume() {
        runCatching {
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val minimum = kotlin.math.ceil(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 0.7).toInt()
            if (current < minimum) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, minimum, 0)
                originalVolume = current
                raisedVolume = minimum
            }
        }
    }

    private fun restoreAlarmVolume() {
        runCatching {
            val previous = originalVolume
            // Respect any volume change the driver makes during the warning.
            if (previous != null && audio.getStreamVolume(AudioManager.STREAM_ALARM) == raisedVolume) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
            }
        }
        originalVolume = null
        raisedVolume = null
    }

    private fun stopPlayback(clearActive: Boolean = true) {
        repeatJob?.cancel(); repeatJob = null
        toneJob?.cancel(); toneJob = null
        if (clearActive) activeAlert = null
        pendingSpeech = null
        runCatching { toneGenerator?.stopTone() }
        runCatching { textToSpeech?.stop() }
        runCatching { vibrator?.cancel() }
        restoreAlarmVolume()
    }

    fun acknowledge() { scope.launch { stopPlayback() } }
    fun reset() { scope.launch { stopPlayback() } }
    fun release() {
        scope.launch {
            stopPlayback()
            runCatching { textToSpeech?.shutdown() }
            runCatching { toneGenerator?.release() }
            textToSpeech = null
            toneGenerator = null
            scope.cancel()
        }
    }
}
