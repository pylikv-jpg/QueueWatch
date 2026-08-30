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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class QueueWatchService : Service() {

    companion object {

        const val PREFS_NAME =
            "queuewatch_service_state"

        const val EXTRA_CAR_NUMBER =
            "car_number"

        const val EXTRA_CHECKPOINT =
            "checkpoint"

        const val EXTRA_POSITION_ALERT_ENABLED =
            "position_alert_enabled"

        const val EXTRA_POSITION_THRESHOLD =
            "position_threshold"

        const val EXTRA_FORECAST_ALERT_ENABLED =
            "forecast_alert_enabled"

        const val EXTRA_FORECAST_MINUTES =
            "forecast_minutes"

        const val EXTRA_CALLED_ALERT_ENABLED =
            "called_alert_enabled"


        const val ACTION_ACKNOWLEDGE_ALERT =
            "com.pylikv.queuewatch.ACKNOWLEDGE_ALERT"


        const val KEY_POSITION =
            "position"

        const val KEY_STATE =
            "state"

        const val KEY_QUEUE_COUNT =
            "queue_count"

        const val KEY_SPEED =
            "speed"

        const val KEY_FORECAST =
            "forecast"

        const val KEY_MESSAGE =
            "message"

        const val KEY_LAST_UPDATE =
            "last_update"

        const val KEY_ALERT_ACTIVE =
            "alert_active"

        const val KEY_ALERT_TITLE =
            "alert_title"

        const val KEY_ALERT_MESSAGE =
            "alert_message"

        const val KEY_ALERT_EVENT_ID =
            "alert_event_id"

        const val KEY_ACKNOWLEDGED_EVENT_ID =
            "acknowledged_event_id"


        /*
         * Обычный канал фонового мониторинга.
         */
        private const val MONITORING_CHANNEL_ID =
            "queuewatch_monitoring"


        /*
         * ВАЖНО:
         *
         * Используем НОВЫЙ идентификатор канала.
         *
         * Android не позволяет программно повысить
         * важность уже созданного старого канала.
         * Поэтому создаём новый канал тревог.
         */
        private const val ALERT_CHANNEL_ID =
            "queuewatch_alerts_v2"


        private const val NOTIFICATION_ID =
            1001

        private const val ALERT_NOTIFICATION_ID =
            1002


        private const val UPDATE_INTERVAL =
            20_000L


        private const val ALERT_NOTIFICATION_UPDATE_INTERVAL =
            60_000L
    }


    private val scope =
        CoroutineScope(
            Dispatchers.IO
        )


    private var monitoringJob: Job? =
        null


    private var alertNotificationJob: Job? =
        null


    private var alertManager: QueueAlertManager? =
        null


    private var wakeLock: PowerManager.WakeLock? =
        null


    private lateinit var preferences:
        android.content.SharedPreferences


    /* ========================================================
       СОЗДАНИЕ СЕРВИСА
       ======================================================== */

    override fun onCreate() {

        super.onCreate()


        preferences =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )


        createNotificationChannels()


        startForeground(
            NOTIFICATION_ID,
            createServiceNotification(
                "QueueWatch: мониторинг очереди"
            )
        )


        try {

            val powerManager =
                getSystemService(
                    Context.POWER_SERVICE
                ) as PowerManager


            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "QueueWatch::Monitoring"
                )


            wakeLock?.acquire()

        } catch (_: Exception) {
        }


        alertManager =
            QueueAlertManager(
                applicationContext
            )
    }


    /* ========================================================
       КОМАНДЫ СЕРВИСА
       ======================================================== */

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {


        /*
         * Эта команда приходит как:
         *
         * 1. из кнопки ПОДТВЕРДИТЬ в приложении;
         * 2. из кнопки ПОДТВЕРДИТЬ
         *    в системном уведомлении.
         */

        if (
            intent?.action ==
            ACTION_ACKNOWLEDGE_ALERT
        ) {

            acknowledgeAlert()

            return START_STICKY
        }


        val carNumber =
            intent?.getStringExtra(
                EXTRA_CAR_NUMBER
            ) ?: return START_STICKY


        val checkpoint =
            intent.getStringExtra(
                EXTRA_CHECKPOINT
            ) ?: return START_STICKY


        val positionAlertEnabled =
            intent.getBooleanExtra(
                EXTRA_POSITION_ALERT_ENABLED,
                true
            )


        val positionThreshold =
            intent.getIntExtra(
                EXTRA_POSITION_THRESHOLD,
                100
            )


        /*
         * Эти параметры пока сохраняем
         * для совместимости с текущей архитектурой.
         *
         * MainActivity передаёт forecastAlertEnabled = false.
         */

        val forecastAlertEnabled =
            intent.getBooleanExtra(
                EXTRA_FORECAST_ALERT_ENABLED,
                false
            )


        val forecastMinutes =
            intent.getIntExtra(
                EXTRA_FORECAST_MINUTES,
                0
            )


        val calledAlertEnabled =
            intent.getBooleanExtra(
                EXTRA_CALLED_ALERT_ENABLED,
                true
            )


        monitoringJob?.cancel()


        monitoringJob =
            scope.launch {

                monitor(
                    carNumber = carNumber,
                    checkpointName = checkpoint,

                    positionAlertEnabled =
                        positionAlertEnabled,

                    positionThreshold =
                        positionThreshold,

                    forecastAlertEnabled =
                        forecastAlertEnabled,

                    forecastAlertMinutes =
                        forecastMinutes,

                    calledAlertEnabled =
                        calledAlertEnabled
                )
            }


        return START_STICKY
    }


    /* ========================================================
       ОСНОВНОЙ МОНИТОРИНГ
       ======================================================== */

    private suspend fun monitor(
        carNumber: String,
        checkpointName: String,

        positionAlertEnabled: Boolean,
        positionThreshold: Int,

        forecastAlertEnabled: Boolean,
        forecastAlertMinutes: Int,

        calledAlertEnabled: Boolean
    ) {

        val api =
            QueueApi()


        val analyzer =
            QueueAnalyzer(
                applicationContext
            )


        analyzer.reset()


        var previousPosition: Int? =
            null


        var vehicleWasConfirmed =
            false


        var positionAlertTriggered =
            false


        var forecastAlertTriggered =
            false


        var calledAlertTriggered =
            false


        val checkpointId =
            when (checkpointName) {

                "Бенякони" ->
                    "53d94097-2b34-11ec-8467-ac1f6bf889c0"

                "Берестовица" ->
                    "7e46a2d1-ab2f-11ec-bafb-ac1f6bf889c1"

                "Брест" ->
                    "a9173a85-3fc0-424c-84f0-defa632481e4"

                "Брузги" ->
                    "3b797d4d-706a-440f-a1a4-826c191e1e36"

                "Григоровщина" ->
                    "ffe81c11-00d6-11e8-a967-b0dd44bde851"

                "Каменный Лог" ->
                    "b60677d4-8a00-4f93-a781-e129e1692a03"

                "Козловичи" ->
                    "98b5be92-d3a5-4ba2-9106-76eb4eb3df49"

                else ->
                    null
            }


        if (
            checkpointId == null
        ) {

            saveMessage(
                "Неизвестный пункт пропуска."
            )

            return
        }


        while (
            scope.isActive
        ) {

            try {

                val result =
                    api.getMonitoring(
                        checkpointId
                    )


                result.fold(

                    onSuccess = { json ->

                        processSnapshot(
                            json = json,

                            analyzer = analyzer,

                            carNumber = carNumber,

                            checkpointName =
                                checkpointName,

                            previousPosition =
                                previousPosition,

                            onPreviousPositionChange = {
                                previousPosition = it
                            },

                            vehicleWasConfirmed =
                                vehicleWasConfirmed,

                            onVehicleConfirmedChange = {
                                vehicleWasConfirmed = it
                            },

                            positionAlertEnabled =
                                positionAlertEnabled,

                            positionThreshold =
                                positionThreshold,

                            positionAlertTriggered =
                                positionAlertTriggered,

                            onPositionAlertTriggeredChange = {
                                positionAlertTriggered = it
                            },

                            forecastAlertEnabled =
                                forecastAlertEnabled,

                            forecastAlertMinutes =
                                forecastAlertMinutes,

                            forecastAlertTriggered =
                                forecastAlertTriggered,

                            onForecastAlertTriggeredChange = {
                                forecastAlertTriggered = it
                            },

                            calledAlertEnabled =
                                calledAlertEnabled,

                            calledAlertTriggered =
                                calledAlertTriggered,

                            onCalledAlertTriggeredChange = {
                                calledAlertTriggered = it
                            }
                        )
                    },


                    onFailure = {

                        saveMessage(
                            "Ошибка получения данных. Повторяем попытку..."
                        )
                    }
                )

            } catch (_: Exception) {

                saveMessage(
                    "Временная ошибка. Мониторинг продолжается."
                )
            }


            /*
             * Если QueueAlertManager закончил
             * пять предупреждений —
             * убираем активное состояние
             * и системное тревожное уведомление.
             */

            if (
                alertManager?.activeAlert == null
            ) {

                clearAlertState()

                cancelAlertNotification()
            }


            updateServiceNotification(
                "QueueWatch: $checkpointName"
            )


            delay(
                UPDATE_INTERVAL
            )
        }
    }


    /* ========================================================
       ОБРАБОТКА ДАННЫХ
       ======================================================== */

    private fun processSnapshot(
        json: String,

        analyzer: QueueAnalyzer,

        carNumber: String,

        checkpointName: String,

        previousPosition: Int?,

        onPreviousPositionChange:
            (Int?) -> Unit,

        vehicleWasConfirmed: Boolean,

        onVehicleConfirmedChange:
            (Boolean) -> Unit,

        positionAlertEnabled: Boolean,

        positionThreshold: Int,

        positionAlertTriggered: Boolean,

        onPositionAlertTriggeredChange:
            (Boolean) -> Unit,

        forecastAlertEnabled: Boolean,

        forecastAlertMinutes: Int,

        forecastAlertTriggered: Boolean,

        onForecastAlertTriggeredChange:
            (Boolean) -> Unit,

        calledAlertEnabled: Boolean,

        calledAlertTriggered: Boolean,

        onCalledAlertTriggeredChange:
            (Boolean) -> Unit
    ) {

        try {

            val vehicles =
                analyzer.processSnapshot(
                    json = json,
                    checkpointName =
                        checkpointName
                )


            saveQueueCount(
                vehicles.size
            )


            saveLastUpdate()


            val vehicle =
                analyzer.findVehicle(
                    json,
                    carNumber
                )


            if (
                vehicle == null
            ) {

                /*
                 * Один пропавший ответ
                 * НЕ считается вызовом.
                 */

                if (
                    !vehicleWasConfirmed
                ) {

                    saveState(
                        "",
                        null
                    )


                    saveMessage(
                        "Автомобиль пока не обнаружен."
                    )

                } else {

                    saveMessage(
                        "Данные автомобиля временно отсутствуют. Последняя подтверждённая позиция сохраняется."
                    )
                }


                return
            }


            onVehicleConfirmedChange(
                true
            )


            when (
                analyzer.determineState(
                    vehicle
                )
            ) {


                /* =================================================
                   ЖИВАЯ ОЧЕРЕДЬ
                   ================================================= */

                VehicleState.IN_QUEUE -> {

                    val currentPosition =
                        vehicle.position


                    saveState(
                        "IN_QUEUE",
                        currentPosition
                    )


                    if (
                        currentPosition != null
                    ) {

                        /*
                         * Если автомобиль снова оказался
                         * выше установленного порога,
                         * разрешаем новое пересечение
                         * этого порога в будущем.
                         */

                        if (
                            currentPosition >
                            positionThreshold
                        ) {

                            if (
                                positionAlertTriggered
                            ) {

                                onPositionAlertTriggeredChange(
                                    false
                                )


                                clearAcknowledgement(
                                    buildEventId(
                                        checkpointName,
                                        carNumber,
                                        AlertType.POSITION
                                    )
                                )
                            }
                        }


                        /*
                         * Проверка пересечения порога.
                         */

                        if (
                            positionAlertEnabled &&
                            !positionAlertTriggered
                        ) {

                            val crossed =
                                if (
                                    previousPosition ==
                                    null
                                ) {

                                    currentPosition <=
                                        positionThreshold

                                } else {

                                    previousPosition >
                                        positionThreshold &&

                                        currentPosition <=
                                        positionThreshold
                                }


                            if (
                                crossed
                            ) {

                                onPositionAlertTriggeredChange(
                                    true
                                )


                                triggerAlert(
                                    type =
                                        AlertType.POSITION,

                                    message =
                                        "Автомобиль достиг позиции $positionThreshold или меньше.",

                                    eventId =
                                        buildEventId(
                                            checkpointName,
                                            carNumber,
                                            AlertType.POSITION
                                        )
                                )
                            }
                        }
                    }


                    onPreviousPositionChange(
                        currentPosition
                    )


                    /*
                     * Внутренний расчёт прогноза
                     * оставляем для совместимости.
                     *
                     * В текущем интерфейсе и уведомлениях
                     * прогноз отключён.
                     */

                    val forecast =
                        analyzer.calculateForecast(
                            vehicle.regnum,
                            checkpointName
                        )


                    forecast?.speed?.let {

                        saveSpeed(
                            it.positionsPerHour
                        )
                    }


                    if (
                        forecast?.estimatedMinutes != null
                    ) {

                        saveForecast(
                            forecast.estimatedMinutes
                        )

                    } else {

                        preferences.edit()
                            .remove(
                                KEY_FORECAST
                            )
                            .apply()
                    }


                    val estimated =
                        forecast?.estimatedMinutes


                    if (
                        estimated != null
                    ) {

                        if (
                            estimated >
                            forecastAlertMinutes
                        ) {

                            if (
                                forecastAlertTriggered
                            ) {

                                onForecastAlertTriggeredChange(
                                    false
                                )


                                clearAcknowledgement(
                                    buildEventId(
                                        checkpointName,
                                        carNumber,
                                        AlertType.FORECAST
                                    )
                                )
                            }
                        }


                        /*
                         * В MainActivity сейчас передаётся
                         * forecastAlertEnabled = false.
                         */

                        if (
                            forecastAlertEnabled &&
                            !forecastAlertTriggered &&
                            estimated <=
                            forecastAlertMinutes
                        ) {

                            onForecastAlertTriggeredChange(
                                true
                            )


                            val rounded =
                                estimated
                                    .toInt()
                                    .coerceAtLeast(
                                        0
                                    )


                            triggerAlert(
                                type =
                                    AlertType.FORECAST,

                                message =
                                    "До вызова автомобиля ориентировочно $rounded минут.",

                                eventId =
                                    buildEventId(
                                        checkpointName,
                                        carNumber,
                                        AlertType.FORECAST
                                    )
                            )
                        }
                    }


                    saveMessage(
                        "Автомобиль находится в живой очереди."
                    )
                }


                /* =================================================
                   ФАКТИЧЕСКИЙ ВЫЗОВ
                   ================================================= */

                VehicleState.CALLED -> {

                    saveState(
                        "CALLED",
                        null
                    )


                    saveMessage(
                        "Автомобиль вызван в пункт пропуска."
                    )


                    val eventId =
                        buildEventId(
                            checkpointName,
                            carNumber,
                            AlertType.CALLED
                        )


                    /*
                     * Если событие уже подтверждено,
                     * сигнал повторно не запускается.
                     */

                    if (
                        calledAlertEnabled &&
                        !calledAlertTriggered &&
                        !isAcknowledged(
                            eventId
                        )
                    ) {

                        onCalledAlertTriggeredChange(
                            true
                        )


                        triggerAlert(
                            type =
                                AlertType.CALLED,

                            message =
                                "Автомобиль вызван в пункт пропуска.",

                            eventId =
                                eventId
                        )
                    }


                    preferences.edit()
                        .remove(
                            KEY_SPEED
                        )
                        .remove(
                            KEY_FORECAST
                        )
                        .apply()
                }


                /* =================================================
                   НЕОПРЕДЕЛЁННОЕ СОСТОЯНИЕ
                   ================================================= */

                VehicleState.UNKNOWN -> {

                    saveState(
                        "UNKNOWN",
                        null
                    )


                    saveMessage(
                        "Автомобиль найден, но сервер не дал однозначного состояния."
                    )
                }
            }

        } catch (_: Exception) {

            saveMessage(
                "Ошибка обработки данных. Мониторинг продолжается."
            )
        }
    }


    /* ========================================================
       ID СОБЫТИЯ
       ======================================================== */

    private fun buildEventId(
        checkpoint: String,
        carNumber: String,
        type: AlertType
    ): String {

        return checkpoint +
            "|" +
            carNumber.uppercase() +
            "|" +
            type.name
    }


    /* ========================================================
       ПРОВЕРКА ПОДТВЕРЖДЕНИЯ
       ======================================================== */

    private fun isAcknowledged(
        eventId: String
    ): Boolean {

        return preferences.getString(
            KEY_ACKNOWLEDGED_EVENT_ID,
            null
        ) == eventId
    }


    private fun clearAcknowledgement(
        eventId: String
    ) {

        if (
            isAcknowledged(
                eventId
            )
        ) {

            preferences.edit()
                .remove(
                    KEY_ACKNOWLEDGED_EVENT_ID
                )
                .apply()
        }
    }


    /* ========================================================
       ЗАПУСК ОПОВЕЩЕНИЯ
       ======================================================== */

    private fun triggerAlert(
        type: AlertType,
        message: String,
        eventId: String
    ) {

        /*
         * Подтверждённое событие
         * повторно не запускаем.
         */

        if (
            isAcknowledged(
                eventId
            )
        ) {

            return
        }


        /*
         * Одновременно не запускаем
         * два разных предупреждения.
         */

        if (
            alertManager?.activeAlert != null
        ) {

            return
        }


        val title =
            when (type) {

                AlertType.POSITION ->
                    "Оповещение по очереди"

                AlertType.FORECAST ->
                    "Оповещение о приближении вызова"

                AlertType.CALLED ->
                    "ВНИМАНИЕ: ВЫЗОВ"
            }


        preferences.edit()

            .putBoolean(
                KEY_ALERT_ACTIVE,
                true
            )

            .putString(
                KEY_ALERT_TITLE,
                title
            )

            .putString(
                KEY_ALERT_MESSAGE,
                message
            )

            .putString(
                KEY_ALERT_EVENT_ID,
                eventId
            )

            .apply()


        /*
         * Звук + голос.
         */

        alertManager?.trigger(
            type,
            message
        )


        /*
         * Системное heads-up уведомление.
         */

        showAlertNotification(
            title = title,
            message = message
        )


        /*
         * Пока событие активно,
         * обновляем тревожное уведомление.
         */

        alertNotificationJob?.cancel()


        alertNotificationJob =
            scope.launch {

                while (
                    isActive
                ) {

                    delay(
                        ALERT_NOTIFICATION_UPDATE_INTERVAL
                    )


                    if (
                        alertManager?.activeAlert ==
                        null
                    ) {

                        clearAlertState()

                        cancelAlertNotification()

                        break
                    }


                    showAlertNotification(
                        title = title,
                        message = message
                    )
                }
            }
    }


    /* ========================================================
       ПОДТВЕРЖДЕНИЕ
       ======================================================== */

    private fun acknowledgeAlert() {

        val eventId =
            preferences.getString(
                KEY_ALERT_EVENT_ID,
                null
            )


        /*
         * Запоминаем именно подтверждённое событие.
         */

        if (
            !eventId.isNullOrBlank()
        ) {

            preferences.edit()

                .putString(
                    KEY_ACKNOWLEDGED_EVENT_ID,
                    eventId
                )

                .apply()
        }


        /*
         * Прекращаем звук,
         * голос и внутренние повторы.
         */

        alertManager?.acknowledge()


        /*
         * Прекращаем повтор системного
         * уведомления.
         */

        alertNotificationJob?.cancel()

        alertNotificationJob =
            null


        cancelAlertNotification()


        /*
         * Убираем флаг активного события,
         * чтобы диалог внутри MainActivity
         * тоже исчез.
         */

        clearAlertState()
    }


    /* ========================================================
       ОЧИСТКА АКТИВНОГО СОБЫТИЯ
       ======================================================== */

    private fun clearAlertState() {

        preferences.edit()

            .putBoolean(
                KEY_ALERT_ACTIVE,
                false
            )

            .remove(
                KEY_ALERT_TITLE
            )

            .remove(
                KEY_ALERT_MESSAGE
            )

            .remove(
                KEY_ALERT_EVENT_ID
            )

            .apply()
    }


    /* ========================================================
       СОХРАНЕНИЕ СОСТОЯНИЯ
       ======================================================== */

    private fun saveState(
        state: String,
        position: Int?
    ) {

        val editor =
            preferences.edit()


        editor.putString(
            KEY_STATE,
            state
        )


        if (
            position != null
        ) {

            editor.putInt(
                KEY_POSITION,
                position
            )

        } else {

            editor.remove(
                KEY_POSITION
            )
        }


        editor.apply()
    }


    private fun saveQueueCount(
        count: Int
    ) {

        preferences.edit()

            .putInt(
                KEY_QUEUE_COUNT,
                count
            )

            .apply()
    }


    private fun saveSpeed(
        value: Double
    ) {

        preferences.edit()

            .putFloat(
                KEY_SPEED,
                value.toFloat()
            )

            .apply()
    }


    private fun saveForecast(
        value: Double
    ) {

        preferences.edit()

            .putFloat(
                KEY_FORECAST,
                value.toFloat()
            )

            .apply()
    }


    private fun saveMessage(
        value: String
    ) {

        preferences.edit()

            .putString(
                KEY_MESSAGE,
                value
            )

            .apply()
    }


    private fun saveLastUpdate() {

        val formatter =
            SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            )


        preferences.edit()

            .putString(
                KEY_LAST_UPDATE,
                formatter.format(
                    Date()
                )
            )

            .apply()
    }


    /* ========================================================
       КАНАЛЫ УВЕДОМЛЕНИЙ
       ======================================================== */

    private fun createNotificationChannels() {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        /*
         * Канал обычного постоянного уведомления.
         *
         * Он тихий, потому что обновляется
         * каждые 20 секунд.
         */

        val monitoringChannel =
            NotificationChannel(
                MONITORING_CHANNEL_ID,
                "QueueWatch — мониторинг",
                NotificationManager.IMPORTANCE_LOW
            )


        monitoringChannel.description =
            "Фоновый мониторинг электронной очереди"


        manager.createNotificationChannel(
            monitoringChannel
        )


        /*
         * Отдельный канал ВАЖНЫХ событий.
         *
         * Именно IMPORTANCE_HIGH позволяет
         * Android показать heads-up уведомление
         * поверх текущего приложения.
         */

        val alertChannel =
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "QueueWatch — важные оповещения",
                NotificationManager.IMPORTANCE_HIGH
            )


        alertChannel.description =
            "Вызов автомобиля и достижение заданной позиции"


        /*
         * Дополнительная вибрация помогает,
         * если телефон находится в кармане.
         */

        alertChannel.enableVibration(
            true
        )


        alertChannel.vibrationPattern =
            longArrayOf(
                0,
                300,
                180,
                300,
                180,
                500
            )


        /*
         * Отдельный системный звук канала
         * не включаем, потому что QueueAlertManager
         * уже выдаёт наш звонкий тройной сигнал
         * и затем голос.
         *
         * Иначе два разных звука накладывались бы.
         */

        alertChannel.setSound(
            null,
            null
        )


        manager.createNotificationChannel(
            alertChannel
        )
    }


    /* ========================================================
       ПОСТОЯННОЕ УВЕДОМЛЕНИЕ СЕРВИСА
       ======================================================== */

    private fun createServiceNotification(
        text: String
    ): Notification {

        val openIntent =
            Intent(
                this,
                MainActivity::class.java
            )


        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                10,
                openIntent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )


        return NotificationCompat.Builder(
            this,
            MONITORING_CHANNEL_ID
        )

            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )

            .setContentTitle(
                "QueueWatch"
            )

            .setContentText(
                text
            )

            .setContentIntent(
                openPendingIntent
            )

            .setOngoing(
                true
            )

            .setOnlyAlertOnce(
                true
            )

            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )

            .build()
    }


    private fun updateServiceNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        manager.notify(
            NOTIFICATION_ID,
            createServiceNotification(
                text
            )
        )
    }


    /* ========================================================
       ВСПЛЫВАЮЩЕЕ ТРЕВОЖНОЕ УВЕДОМЛЕНИЕ
       ======================================================== */

    private fun showAlertNotification(
        title: String,
        message: String
    ) {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        /*
         * Нажатие на само уведомление
         * открывает QueueWatch.
         */

        val openIntent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }


        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                20,
                openIntent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )


        /*
         * Кнопка ПОДТВЕРДИТЬ работает
         * непосредственно через Service.
         *
         * Открывать приложение не требуется.
         */

        val acknowledgeIntent =
            Intent(
                this,
                QueueWatchService::class.java
            ).apply {

                action =
                    ACTION_ACKNOWLEDGE_ALERT
            }


        val acknowledgePendingIntent =
            PendingIntent.getService(
                this,
                30,
                acknowledgeIntent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )


        val notification =
            NotificationCompat.Builder(
                this,
                ALERT_CHANNEL_ID
            )

                .setSmallIcon(
                    android.R.drawable.ic_dialog_alert
                )

                .setContentTitle(
                    title
                )

                .setContentText(
                    message
                )

                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            message
                        )
                )

                .setContentIntent(
                    openPendingIntent
                )

                /*
                 * Максимальный приоритет
                 * для старых версий Android.
                 *
                 * Для новых Android основную роль
                 * играет IMPORTANCE_HIGH канала.
                 */

                .setPriority(
                    NotificationCompat.PRIORITY_MAX
                )

                .setCategory(
                    NotificationCompat.CATEGORY_ALARM
                )

                .setVisibility(
                    NotificationCompat.VISIBILITY_PUBLIC
                )

                .setAutoCancel(
                    false
                )

                .setOngoing(
                    true
                )

                /*
                 * КНОПКА ПОДТВЕРЖДЕНИЯ.
                 */

                .addAction(
                    android.R.drawable.ic_menu_save,
                    "ПОДТВЕРДИТЬ",
                    acknowledgePendingIntent
                )

                .build()


        manager.notify(
            ALERT_NOTIFICATION_ID,
            notification
        )
    }


    /* ========================================================
       УДАЛЕНИЕ ТРЕВОЖНОГО УВЕДОМЛЕНИЯ
       ======================================================== */

    private fun cancelAlertNotification() {

        try {

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager


            manager.cancel(
                ALERT_NOTIFICATION_ID
            )

        } catch (_: Exception) {
        }
    }


    /* ========================================================
       ОСТАНОВКА СЕРВИСА
       ======================================================== */

    override fun onDestroy() {

        monitoringJob?.cancel()


        alertNotificationJob?.cancel()


        alertManager?.release()

        alertManager =
            null


        cancelAlertNotification()


        clearAlertState()


        try {

            wakeLock?.release()

        } catch (_: Exception) {
        }


        wakeLock =
            null


        super.onDestroy()
    }


    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
