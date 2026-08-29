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


        private const val CHANNEL_ID =
            "queuewatch_monitoring"

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


    private var alertManager:
        QueueAlertManager? = null


    private var wakeLock:
        PowerManager.WakeLock? = null


    private lateinit var preferences:
        android.content.SharedPreferences


    override fun onCreate() {

        super.onCreate()


        preferences =
            getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )


        createNotificationChannel()


        /*
         * Foreground Service обязан
         * сразу показать постоянное
         * уведомление.
         */

        startForeground(
            NOTIFICATION_ID,
            createServiceNotification(
                "QueueWatch: мониторинг очереди"
            )
        )


        /*
         * Не даём CPU полностью заснуть
         * во время активного мониторинга.
         */

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


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

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


        val forecastAlertEnabled =
            intent.getBooleanExtra(
                EXTRA_FORECAST_ALERT_ENABLED,
                true
            )


        val forecastMinutes =
            intent.getIntExtra(
                EXTRA_FORECAST_MINUTES,
                30
            )


        val calledAlertEnabled =
            intent.getBooleanExtra(
                EXTRA_CALLED_ALERT_ENABLED,
                true
            )


        /*
         * Если сервис был перезапущен
         * с новыми параметрами,
         * старый цикл мониторинга
         * останавливаем.
         */

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


        /*
         * Сбрасываем только состояние
         * текущего сеанса анализатора.
         *
         * Историческая статистика
         * сохраняется.
         */

        analyzer.reset()


        var previousPosition:
            Int? = null


        var vehicleWasConfirmed =
            false


        /*
         * Событие пересечения позиции.
         *
         * true:
         * текущий порог уже был достигнут
         * и повторно тревожить до выхода
         * из порога не нужно.
         *
         * false:
         * можно сформировать новое событие.
         */

        var positionAlertTriggered =
            false


        /*
         * Аналогичное состояние
         * для временного прогноза.
         */

        var forecastAlertTriggered =
            false


        /*
         * Фактический вызов автомобиля
         * является одноразовым состоянием.
         */

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


        if (checkpointId == null) {

            saveMessage(
                "Неизвестный пункт пропуска."
            )

            return
        }


        while (scope.isActive) {

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

                        /*
                         * Ошибка одного запроса
                         * НЕ останавливает мониторинг.
                         */

                        saveMessage(
                            "Ошибка получения данных. " +
                                "Повторяем попытку..."
                        )
                    }
                )

            } catch (_: Exception) {

                /*
                 * Любая ошибка обработки
                 * также не должна завершать
                 * основной цикл мониторинга.
                 */

                saveMessage(
                    "Временная ошибка. " +
                        "Мониторинг продолжается."
                )
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
       ОБРАБОТКА СНИМКА
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


            /*
             * Временное отсутствие автомобиля
             * НЕ означает вызов.
             */

            if (vehicle == null) {

                if (!vehicleWasConfirmed) {

                    saveState(
                        state = "",
                        position = null
                    )

                    saveMessage(
                        "Автомобиль пока не обнаружен."
                    )

                } else {

                    saveMessage(
                        "Данные автомобиля временно " +
                            "отсутствуют. Последняя " +
                            "подтверждённая позиция сохраняется."
                    )
                }

                return
            }


            onVehicleConfirmedChange(
                true
            )


            val state =
                analyzer.determineState(
                    vehicle
                )


            when (state) {

                /* =================================================
                   АВТОМОБИЛЬ В ЖИВОЙ ОЧЕРЕДИ
                   ================================================= */

                VehicleState.IN_QUEUE -> {

                    val currentPosition =
                        vehicle.position


                    saveState(
                        state = "IN_QUEUE",
                        position = currentPosition
                    )


                    /*
                     * -------------------------------------------------
                     * ПОЗИЦИОННОЕ ОПОВЕЩЕНИЕ
                     * -------------------------------------------------
                     *
                     * Событие возникает при пересечении порога
                     * сверху вниз.
                     *
                     * Например:
                     *
                     * 105 -> 99
                     *
                     * при пороге 100.
                     *
                     * После окончания пяти предупреждений
                     * positionAlertTriggered остаётся true.
                     *
                     * Чтобы получить НОВОЕ событие,
                     * автомобиль должен сначала выйти
                     * из порога:
                     *
                     * 99 -> 101
                     *
                     * а затем снова пересечь:
                     *
                     * 101 -> 99
                     */

                    if (
                        currentPosition != null
                    ) {

                        /*
                         * Вышли обратно из порога.
                         *
                         * Это вооружает новое событие.
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
                            }
                        }


                        if (
                            positionAlertEnabled &&
                            !positionAlertTriggered
                        ) {

                            val crossed =
                                if (
                                    previousPosition == null
                                ) {

                                    currentPosition <=
                                        positionThreshold

                                } else {

                                    previousPosition >
                                        positionThreshold &&
                                        currentPosition <=
                                            positionThreshold
                                }


                            if (crossed) {

                                onPositionAlertTriggeredChange(
                                    true
                                )


                                triggerAlert(

                                    AlertType.POSITION,

                                    "Автомобиль достиг позиции " +
                                        "$positionThreshold или меньше."
                                )
                            }
                        }
                    }


                    onPreviousPositionChange(
                        currentPosition
                    )


                    /*
                     * -------------------------------------------------
                     * АДАПТИВНЫЙ ПРОГНОЗ
                     * -------------------------------------------------
                     */

                    val forecast =
                        analyzer.calculateForecast(

                            regnum =
                                vehicle.regnum,

                            checkpointName =
                                checkpointName
                        )


                    if (
                        forecast?.speed != null
                    ) {

                        saveSpeed(
                            forecast.speed.positionsPerHour
                        )
                    }


                    if (
                        forecast?.estimatedMinutes != null
                    ) {

                        saveForecast(
                            forecast.estimatedMinutes
                        )

                    } else {

                        preferences
                            .edit()
                            .remove(
                                KEY_FORECAST
                            )
                            .apply()
                    }


                    val estimated =
                        forecast?.estimatedMinutes


                    /*
                     * -------------------------------------------------
                     * ВРЕМЕННОЕ ОПОВЕЩЕНИЕ
                     * -------------------------------------------------
                     *
                     * Например:
                     *
                     * установлен порог = 30 минут.
                     *
                     * 42 -> 31
                     * ещё не тревожим.
                     *
                     * 31 -> 29
                     * создаём событие.
                     *
                     * После пяти предупреждений
                     * событие заканчивается.
                     *
                     * Если прогноз снова выйдет выше 30,
                     * а потом снова войдёт в диапазон,
                     * будет создано новое событие.
                     */

                    if (
                        estimated != null
                    ) {

                        /*
                         * Прогноз вышел за порог.
                         *
                         * Вооружаем новое событие.
                         */

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
                            }
                        }


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
                                    .coerceAtLeast(0)


                            triggerAlert(

                                AlertType.FORECAST,

                                "До вызова автомобиля " +
                                    "ориентировочно " +
                                    "$rounded минут."
                            )
                        }
                    }


                    saveMessage(

                        if (
                            currentPosition != null
                        ) {

                            "Автомобиль находится " +
                                "в живой очереди."

                        } else {

                            "Автомобиль найден, " +
                                "но позиция не передана."
                        }
                    )
                }


                /* =================================================
                   ФАКТИЧЕСКИЙ ВЫЗОВ
                   ================================================= */

                VehicleState.CALLED -> {

                    saveState(
                        state = "CALLED",
                        position = null
                    )


                    saveMessage(
                        "Автомобиль вызван " +
                            "в пункт пропуска."
                    )


                    /*
                     * Только подтверждённый
                     * VehicleState.CALLED вызывает
                     * это предупреждение.
                     */

                    if (
                        calledAlertEnabled &&
                        !calledAlertTriggered
                    ) {

                        onCalledAlertTriggeredChange(
                            true
                        )


                        triggerAlert(

                            AlertType.CALLED,

                            "Автомобиль вызван " +
                                "в пункт пропуска."
                        )
                    }


                    preferences
                        .edit()
                        .remove(KEY_SPEED)
                        .remove(KEY_FORECAST)
                        .apply()
                }


                /* =================================================
                   НЕОПРЕДЕЛЁННОЕ СОСТОЯНИЕ
                   ================================================= */

                VehicleState.UNKNOWN -> {

                    saveState(
                        state = "UNKNOWN",
                        position = null
                    )


                    saveMessage(
                        "Автомобиль найден, " +
                            "но сервер не дал " +
                            "однозначного состояния."
                    )
                }
            }

        } catch (_: Exception) {

            saveMessage(
                "Ошибка обработки данных. " +
                    "Мониторинг продолжается."
            )
        }
    }


    /* ========================================================
       ЗАПУСК ОПОВЕЩЕНИЯ
       ======================================================== */

    private fun triggerAlert(
        type: AlertType,
        message: String
    ) {

        /*
         * Если уже существует активное
         * неподтверждённое событие,
         * новое не создаём.
         *
         * QueueAlertManager самостоятельно
         * ограничивает цикл пятью
         * предупреждениями.
         */

        if (
            alertManager?.activeAlert != null
        ) {
            return
        }


        alertManager?.trigger(
            type,
            message
        )


        /*
         * Первичное системное уведомление.
         *
         * Звуковой и голосовой цикл
         * выполняется QueueAlertManager.
         */

        showAlertNotification(
            message
        )


        /*
         * Обновляем визуальное уведомление
         * раз в минуту, пока событие активно.
         *
         * Это НЕ создаёт дополнительный
         * звуковой цикл.
         *
         * Звуковые повторы ограничены
         * QueueAlertManager пятью.
         */

        alertNotificationJob?.cancel()


        alertNotificationJob =
            scope.launch {

                while (isActive) {

                    delay(
                        ALERT_NOTIFICATION_UPDATE_INTERVAL
                    )


                    if (
                        alertManager?.activeAlert ==
                            null
                    ) {
                        break
                    }


                    showAlertNotification(
                        message
                    )
                }
            }
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


        if (position != null) {

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

        preferences
            .edit()
            .putInt(
                KEY_QUEUE_COUNT,
                count
            )
            .apply()
    }


    private fun saveSpeed(
        value: Double
    ) {

        preferences
            .edit()
            .putFloat(
                KEY_SPEED,
                value.toFloat()
            )
            .apply()
    }


    private fun saveForecast(
        value: Double
    ) {

        preferences
            .edit()
            .putFloat(
                KEY_FORECAST,
                value.toFloat()
            )
            .apply()
    }


    private fun saveMessage(
        value: String
    ) {

        preferences
            .edit()
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


        preferences
            .edit()
            .putString(
                KEY_LAST_UPDATE,
                formatter.format(
                    Date()
                )
            )
            .apply()
    }


    /* ========================================================
       FOREGROUND NOTIFICATION
       ======================================================== */

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        val channel =
            NotificationChannel(

                CHANNEL_ID,

                "QueueWatch",

                NotificationManager.IMPORTANCE_LOW
            )


        channel.description =
            "Фоновый мониторинг электронной очереди"


        manager.createNotificationChannel(
            channel
        )
    }


    private fun createServiceNotification(
        text: String
    ): Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )


        val pendingIntent =
            PendingIntent.getActivity(

                this,

                0,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )


        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
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
                pendingIntent
            )

            .setOngoing(
                true
            )

            .setOnlyAlertOnce(
                true
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
       УВЕДОМЛЕНИЕ ОБ ОПОВЕЩЕНИИ
       ======================================================== */

    private fun showAlertNotification(
        message: String
    ) {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )

                .setSmallIcon(
                    android.R.drawable.ic_dialog_alert
                )

                .setContentTitle(
                    "QueueWatch — ВНИМАНИЕ"
                )

                .setContentText(
                    message
                )

                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message)
                )

                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )

                .setAutoCancel(
                    false
                )

                .build()


        manager.notify(

            ALERT_NOTIFICATION_ID,

            notification
        )
    }


    /* ========================================================
       ОСТАНОВКА
       ======================================================== */

    override fun onDestroy() {

        monitoringJob?.cancel()

        alertNotificationJob?.cancel()


        alertManager?.release()

        alertManager = null


        try {

            wakeLock?.release()

        } catch (_: Exception) {
        }


        wakeLock = null


        super.onDestroy()
    }


    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
