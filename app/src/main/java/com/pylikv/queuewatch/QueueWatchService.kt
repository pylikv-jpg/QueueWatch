package com.pylikv.queuewatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Long-running, user initiated queue monitoring.
 *
 * The active monitoring session is persisted in SharedPreferences. If Android
 * kills the process and later recreates this START_STICKY service with a null
 * Intent, the service restores the session and continues monitoring without
 * asking the user to enter the vehicle again.
 */
class QueueWatchService : Service() {

    companion object {
        const val PREFS_NAME = "queuewatch_service_state"

        const val EXTRA_CAR_NUMBER = "car_number"
        const val EXTRA_CHECKPOINT = "checkpoint"
        const val EXTRA_POSITION_ALERT_ENABLED = "position_alert_enabled"
        const val EXTRA_POSITION_THRESHOLD = "position_threshold"
        const val EXTRA_FORECAST_ALERT_ENABLED = "forecast_alert_enabled"
        const val EXTRA_FORECAST_MINUTES = "forecast_minutes"
        const val EXTRA_CALLED_ALERT_ENABLED = "called_alert_enabled"

        const val ACTION_ACKNOWLEDGE_ALERT = "com.pylikv.queuewatch.ACKNOWLEDGE_ALERT"
        const val ACTION_STOP_TRACKING = "com.pylikv.queuewatch.STOP_TRACKING"

        const val KEY_TRACKING_ACTIVE = "tracking_active"
        const val KEY_CAR_NUMBER = "session_car_number"
        const val KEY_CHECKPOINT = "session_checkpoint"
        const val KEY_POSITION_ALERT_ENABLED = "session_position_alert_enabled"
        const val KEY_POSITION_THRESHOLD = "session_position_threshold"
        const val KEY_CALLED_ALERT_ENABLED = "session_called_alert_enabled"

        const val KEY_POSITION = "position"
        const val KEY_STATE = "state"
        const val KEY_QUEUE_COUNT = "queue_count"
        const val KEY_SPEED = "speed"
        const val KEY_FORECAST = "forecast"
        const val KEY_MESSAGE = "message"
        const val KEY_LAST_UPDATE = "last_update"
        const val KEY_ALERT_ACTIVE = "alert_active"
        const val KEY_ALERT_TITLE = "alert_title"
        const val KEY_ALERT_MESSAGE = "alert_message"
        const val KEY_ALERT_EVENT_ID = "alert_event_id"
        const val KEY_ACKNOWLEDGED_EVENT_ID = "acknowledged_event_id"

        private const val MONITORING_CHANNEL_ID = "queuewatch_monitoring"
        private const val ALERT_CHANNEL_ID = "queuewatch_alerts_v2"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val UPDATE_INTERVAL = 20_000L
        private const val ALERT_NOTIFICATION_UPDATE_INTERVAL = 60_000L
    }

    private data class TrackingSession(
        val carNumber: String,
        val checkpoint: String,
        val positionAlertEnabled: Boolean,
        val positionThreshold: Int,
        val calledAlertEnabled: Boolean
    )

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)

    private var monitoringJob: Job? = null
    private var alertNotificationJob: Job? = null
    private var alertManager: QueueAlertManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var preferences: android.content.SharedPreferences

    override fun onCreate() {
        super.onCreate()

        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannels()

        // A foreground service must promote itself immediately after creation.
        // The text is replaced with the restored/current checkpoint once the
        // monitoring loop starts.
        startForeground(
            NOTIFICATION_ID,
            createServiceNotification("QueueWatch: восстановление мониторинга…")
        )

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "QueueWatch::Monitoring"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }

        alertManager = QueueAlertManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACKNOWLEDGE_ALERT -> {
                acknowledgeAlert()
                return START_STICKY
            }

            ACTION_STOP_TRACKING -> {
                stopTrackingByUser()
                return START_NOT_STICKY
            }
        }

        val sessionFromIntent = intent?.let { readSessionFromIntent(it) }

        val session = if (sessionFromIntent != null) {
            saveSession(sessionFromIntent)
            sessionFromIntent
        } else {
            // This is the critical START_STICKY recovery path. Android is
            // allowed to recreate the service with intent == null.
            loadSavedSession()
        }

        if (session == null) {
            // No active user session exists. Do not leave an empty foreground
            // service running forever.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startMonitoring(session)
        return START_STICKY
    }

    private fun readSessionFromIntent(intent: Intent): TrackingSession? {
        val carNumber = intent.getStringExtra(EXTRA_CAR_NUMBER)
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val checkpoint = intent.getStringExtra(EXTRA_CHECKPOINT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return TrackingSession(
            carNumber = carNumber,
            checkpoint = checkpoint,
            positionAlertEnabled = intent.getBooleanExtra(
                EXTRA_POSITION_ALERT_ENABLED,
                true
            ),
            positionThreshold = intent.getIntExtra(
                EXTRA_POSITION_THRESHOLD,
                100
            ).coerceAtLeast(1),
            calledAlertEnabled = intent.getBooleanExtra(
                EXTRA_CALLED_ALERT_ENABLED,
                true
            )
        )
    }

    private fun saveSession(session: TrackingSession) {
        // commit() is deliberate here: the session must be durable before the
        // UI can disappear or Android can kill the process.
        preferences.edit()
            .putBoolean(KEY_TRACKING_ACTIVE, true)
            .putString(KEY_CAR_NUMBER, session.carNumber)
            .putString(KEY_CHECKPOINT, session.checkpoint)
            .putBoolean(KEY_POSITION_ALERT_ENABLED, session.positionAlertEnabled)
            .putInt(KEY_POSITION_THRESHOLD, session.positionThreshold)
            .putBoolean(KEY_CALLED_ALERT_ENABLED, session.calledAlertEnabled)
            .commit()
    }

    private fun loadSavedSession(): TrackingSession? {
        if (!preferences.getBoolean(KEY_TRACKING_ACTIVE, false)) {
            return null
        }

        val carNumber = preferences.getString(KEY_CAR_NUMBER, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val checkpoint = preferences.getString(KEY_CHECKPOINT, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return TrackingSession(
            carNumber = carNumber,
            checkpoint = checkpoint,
            positionAlertEnabled = preferences.getBoolean(
                KEY_POSITION_ALERT_ENABLED,
                true
            ),
            positionThreshold = preferences.getInt(
                KEY_POSITION_THRESHOLD,
                100
            ).coerceAtLeast(1),
            calledAlertEnabled = preferences.getBoolean(
                KEY_CALLED_ALERT_ENABLED,
                true
            )
        )
    }

    private fun startMonitoring(session: TrackingSession) {
        monitoringJob?.cancel()
        monitoringJob = scope.launch {
            monitor(session)
        }
    }

    private suspend fun monitor(session: TrackingSession) {
        val api = QueueApi()
        val analyzer = QueueAnalyzer(applicationContext)
        analyzer.reset()

        var previousPosition: Int? = if (preferences.contains(KEY_POSITION)) {
            preferences.getInt(KEY_POSITION, 0).takeIf { it > 0 }
        } else {
            null
        }

        var vehicleWasConfirmed =
            preferences.getString(KEY_STATE, "").orEmpty().isNotBlank()

        var positionAlertTriggered = false
        var calledAlertTriggered = false

        val checkpointId = checkpointId(session.checkpoint)
        if (checkpointId == null) {
            saveMessage("Неизвестный пункт пропуска.")
            return
        }

        updateServiceNotification(
            "${session.carNumber} • ${session.checkpoint} • отслеживание активно"
        )

        while (preferences.getBoolean(KEY_TRACKING_ACTIVE, false)) {
            try {
                val result = api.getMonitoring(checkpointId)

                result.fold(
                    onSuccess = { json ->
                        val vehicles = analyzer.processSnapshot(
                            json = json,
                            checkpointName = session.checkpoint
                        )

                        saveLastUpdate()

                        val vehicle = analyzer.findVehicle(json, session.carNumber)

                        if (vehicle == null) {
                            if (!vehicleWasConfirmed) {
                                saveState("", null)
                                saveMessage("Автомобиль пока не обнаружен.")
                            } else {
                                saveMessage(
                                    "Данные автомобиля временно отсутствуют. " +
                                        "Последняя подтверждённая позиция сохраняется."
                                )
                            }
                        } else {
                            vehicleWasConfirmed = true

                            // Count only live-queue vehicles of the same type
                            // as the vehicle currently being tracked.
                            val sameTypeLiveQueueCount = vehicles.count { candidate ->
                                candidate.vehicleType == vehicle.vehicleType &&
                                    analyzer.determineState(candidate) == VehicleState.IN_QUEUE
                            }
                            saveQueueCount(sameTypeLiveQueueCount)

                            when (analyzer.determineState(vehicle)) {
                                VehicleState.IN_QUEUE -> {
                                    val currentPosition = vehicle.position
                                    saveState("IN_QUEUE", currentPosition)

                                    if (currentPosition != null) {
                                        if (currentPosition > session.positionThreshold) {
                                            if (positionAlertTriggered) {
                                                positionAlertTriggered = false
                                                clearAcknowledgement(
                                                    buildEventId(
                                                        session.checkpoint,
                                                        session.carNumber,
                                                        AlertType.POSITION
                                                    )
                                                )
                                            }
                                        }

                                        if (
                                            session.positionAlertEnabled &&
                                            !positionAlertTriggered
                                        ) {
                                            val crossed = previousPosition == null &&
                                                currentPosition <= session.positionThreshold ||
                                                previousPosition != null &&
                                                previousPosition!! > session.positionThreshold &&
                                                currentPosition <= session.positionThreshold

                                            if (crossed) {
                                                positionAlertTriggered = true
                                                triggerAlert(
                                                    type = AlertType.POSITION,
                                                    message =
                                                        "Автомобиль достиг позиции " +
                                                            "${session.positionThreshold} или меньше.",
                                                    eventId = buildEventId(
                                                        session.checkpoint,
                                                        session.carNumber,
                                                        AlertType.POSITION
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    previousPosition = currentPosition
                                    saveMessage("Автомобиль находится в живой очереди.")
                                }

                                VehicleState.CALLED -> {
                                    saveState("CALLED", null)
                                    saveMessage("Автомобиль вызван в пункт пропуска.")

                                    val eventId = buildEventId(
                                        session.checkpoint,
                                        session.carNumber,
                                        AlertType.CALLED
                                    )

                                    if (
                                        session.calledAlertEnabled &&
                                        !calledAlertTriggered &&
                                        !isAcknowledged(eventId)
                                    ) {
                                        calledAlertTriggered = true
                                        triggerAlert(
                                            type = AlertType.CALLED,
                                            message = "Автомобиль вызван в пункт пропуска.",
                                            eventId = eventId
                                        )
                                    }

                                    preferences.edit()
                                        .remove(KEY_SPEED)
                                        .remove(KEY_FORECAST)
                                        .apply()
                                }

                                VehicleState.UNKNOWN -> {
                                    saveState("UNKNOWN", null)
                                    saveMessage(
                                        "Автомобиль найден, но сервер не дал " +
                                            "однозначного состояния."
                                    )
                                }
                            }
                        }
                    },
                    onFailure = {
                        saveMessage("Ошибка получения данных. Повторяем попытку…")
                    }
                )
            } catch (_: Exception) {
                saveMessage("Временная ошибка. Мониторинг продолжается.")
            }

            if (alertManager?.activeAlert == null) {
                clearAlertState()
                cancelAlertNotification()
            }

            updateServiceNotification(
                "${session.carNumber} • ${session.checkpoint} • отслеживание активно"
            )

            delay(UPDATE_INTERVAL)
        }
    }

    private fun checkpointId(checkpointName: String): String? = when (checkpointName) {
        "Бенякони" -> "53d94097-2b34-11ec-8467-ac1f6bf889c0"
        "Берестовица" -> "7e46a2d1-ab2f-11ec-bafb-ac1f6bf889c1"
        "Брест" -> "a9173a85-3fc0-424c-84f0-defa632481e4"
        "Брузги" -> "3b797d4d-706a-440f-a1a4-826c191e1e36"
        "Григоровщина" -> "ffe81c11-00d6-11e8-a967-b0dd44bde851"
        "Каменный Лог" -> "b60677d4-8a00-4f93-a781-e129e1692a03"
        "Козловичи" -> "98b5be92-d3a5-4ba2-9106-76eb4eb3df49"
        else -> null
    }

    private fun buildEventId(
        checkpoint: String,
        carNumber: String,
        type: AlertType
    ): String = "$checkpoint|${carNumber.uppercase()}|${type.name}"

    private fun isAcknowledged(eventId: String): Boolean =
        preferences.getString(KEY_ACKNOWLEDGED_EVENT_ID, null) == eventId

    private fun clearAcknowledgement(eventId: String) {
        if (isAcknowledged(eventId)) {
            preferences.edit().remove(KEY_ACKNOWLEDGED_EVENT_ID).apply()
        }
    }

    private fun triggerAlert(type: AlertType, message: String, eventId: String) {
        if (isAcknowledged(eventId)) return
        if (alertManager?.activeAlert != null) return

        val title = when (type) {
            AlertType.POSITION -> "Оповещение по очереди"
            AlertType.FORECAST -> "Оповещение о приближении вызова"
            AlertType.CALLED -> "ВНИМАНИЕ: ВЫЗОВ"
        }

        preferences.edit()
            .putBoolean(KEY_ALERT_ACTIVE, true)
            .putString(KEY_ALERT_TITLE, title)
            .putString(KEY_ALERT_MESSAGE, message)
            .putString(KEY_ALERT_EVENT_ID, eventId)
            .apply()

        alertManager?.trigger(type, message)
        showAlertNotification(title, message)

        alertNotificationJob?.cancel()
        alertNotificationJob = scope.launch {
            while (isActive) {
                delay(ALERT_NOTIFICATION_UPDATE_INTERVAL)

                if (alertManager?.activeAlert == null) {
                    clearAlertState()
                    cancelAlertNotification()
                    break
                }

                showAlertNotification(title, message)
            }
        }
    }

    private fun acknowledgeAlert() {
        val eventId = preferences.getString(KEY_ALERT_EVENT_ID, null)

        if (!eventId.isNullOrBlank()) {
            preferences.edit()
                .putString(KEY_ACKNOWLEDGED_EVENT_ID, eventId)
                .apply()
        }

        alertManager?.acknowledge()
        alertNotificationJob?.cancel()
        alertNotificationJob = null
        cancelAlertNotification()
        clearAlertState()
    }

    private fun stopTrackingByUser() {
        // Only an explicit user stop clears the durable session. Android
        // destroying the service must never do this.
        preferences.edit()
            .putBoolean(KEY_TRACKING_ACTIVE, false)
            .remove(KEY_CAR_NUMBER)
            .remove(KEY_CHECKPOINT)
            .remove(KEY_POSITION_ALERT_ENABLED)
            .remove(KEY_POSITION_THRESHOLD)
            .remove(KEY_CALLED_ALERT_ENABLED)
            .remove(KEY_POSITION)
            .remove(KEY_STATE)
            .remove(KEY_QUEUE_COUNT)
            .remove(KEY_SPEED)
            .remove(KEY_FORECAST)
            .remove(KEY_MESSAGE)
            .remove(KEY_LAST_UPDATE)
            .remove(KEY_ALERT_ACTIVE)
            .remove(KEY_ALERT_TITLE)
            .remove(KEY_ALERT_MESSAGE)
            .remove(KEY_ALERT_EVENT_ID)
            .remove(KEY_ACKNOWLEDGED_EVENT_ID)
            .commit()

        monitoringJob?.cancel()
        alertNotificationJob?.cancel()
        alertManager?.acknowledge()
        cancelAlertNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun clearAlertState() {
        preferences.edit()
            .putBoolean(KEY_ALERT_ACTIVE, false)
            .remove(KEY_ALERT_TITLE)
            .remove(KEY_ALERT_MESSAGE)
            .remove(KEY_ALERT_EVENT_ID)
            .apply()
    }

    private fun saveState(state: String, position: Int?) {
        val editor = preferences.edit().putString(KEY_STATE, state)
        if (position != null) {
            editor.putInt(KEY_POSITION, position)
        } else {
            editor.remove(KEY_POSITION)
        }
        editor.apply()
    }

    private fun saveQueueCount(count: Int) {
        preferences.edit().putInt(KEY_QUEUE_COUNT, count).apply()
    }

    private fun saveMessage(value: String) {
        preferences.edit().putString(KEY_MESSAGE, value).apply()
    }

    private fun saveLastUpdate() {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        preferences.edit()
            .putString(KEY_LAST_UPDATE, formatter.format(Date()))
            .apply()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitoringChannel = NotificationChannel(
            MONITORING_CHANNEL_ID,
            "QueueWatch — мониторинг",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Постоянный мониторинг выбранного автомобиля в электронной очереди"
        }
        manager.createNotificationChannel(monitoringChannel)

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "QueueWatch — важные оповещения",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Вызов автомобиля и достижение заданной позиции"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 180, 300, 180, 500)
            setSound(null, null)
        }
        manager.createNotificationChannel(alertChannel)
    }

    private fun createServiceNotification(text: String): Notification {
        val openIntent = Intent(this, SessionLauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, QueueWatchService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("QueueWatch")
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "ОСТАНОВИТЬ",
                stopPendingIntent
            )
            .build()
    }

    private fun updateServiceNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createServiceNotification(text))
    }

    private fun showAlertNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(this, SessionLauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            20,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acknowledgeIntent = Intent(this, QueueWatchService::class.java).apply {
            action = ACTION_ACKNOWLEDGE_ALERT
        }
        val acknowledgePendingIntent = PendingIntent.getService(
            this,
            30,
            acknowledgeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_save,
                "ПОДТВЕРДИТЬ",
                acknowledgePendingIntent
            )
            .build()

        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun cancelAlertNotification() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(ALERT_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        alertNotificationJob?.cancel()
        alertManager?.release()
        alertManager = null
        cancelAlertNotification()

        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null

        // IMPORTANT: do not clear KEY_TRACKING_ACTIVE here. onDestroy() can be
        // called because Android reclaimed the process. START_STICKY + the
        // persisted session are what allow monitoring to recover.
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
