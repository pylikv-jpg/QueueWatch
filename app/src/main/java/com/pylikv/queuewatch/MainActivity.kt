package com.pylikv.queuewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QueueWatchApp()
        }
    }
}


/* ============================================================
   ГЛАВНОЕ ПРИЛОЖЕНИЕ
   ============================================================ */

@Composable
fun QueueWatchApp() {

    var carNumber by rememberSaveable {
        mutableStateOf("")
    }

    var checkpoint by rememberSaveable {
        mutableStateOf("")
    }

    var trackingStarted by rememberSaveable {
        mutableStateOf(false)
    }

    /*
     * Настройки оповещений.
     */

    var positionAlertEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var positionAlertThreshold by rememberSaveable {
        mutableStateOf("100")
    }

    var forecastAlertEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var forecastAlertMinutes by rememberSaveable {
        mutableStateOf("30")
    }

    var calledAlertEnabled by rememberSaveable {
        mutableStateOf(true)
    }


    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            if (!trackingStarted) {

                SetupScreen(
                    carNumber = carNumber,

                    onCarNumberChange = {
                        carNumber = it
                    },

                    checkpoint = checkpoint,

                    onCheckpointSelected = {
                        checkpoint = it
                    },

                    positionAlertEnabled =
                        positionAlertEnabled,

                    onPositionAlertEnabledChange = {
                        positionAlertEnabled = it
                    },

                    positionAlertThreshold =
                        positionAlertThreshold,

                    onPositionAlertThresholdChange = {
                        positionAlertThreshold = it
                    },

                    forecastAlertEnabled =
                        forecastAlertEnabled,

                    onForecastAlertEnabledChange = {
                        forecastAlertEnabled = it
                    },

                    forecastAlertMinutes =
                        forecastAlertMinutes,

                    onForecastAlertMinutesChange = {
                        forecastAlertMinutes = it
                    },

                    calledAlertEnabled =
                        calledAlertEnabled,

                    onCalledAlertEnabledChange = {
                        calledAlertEnabled = it
                    },

                    onStartTracking = {
                        trackingStarted = true
                    }
                )

            } else {

                TrackingScreen(
                    carNumber = carNumber,
                    checkpointName = checkpoint,

                    positionAlertEnabled =
                        positionAlertEnabled,

                    positionAlertThreshold =
                        positionAlertThreshold.toIntOrNull()
                            ?: 100,

                    forecastAlertEnabled =
                        forecastAlertEnabled,

                    forecastAlertMinutes =
                        forecastAlertMinutes.toIntOrNull()
                            ?: 30,

                    calledAlertEnabled =
                        calledAlertEnabled
                )
            }
        }
    }
}


/* ============================================================
   ЭКРАН НАСТРОЙКИ
   ============================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(
    carNumber: String,
    onCarNumberChange: (String) -> Unit,

    checkpoint: String,
    onCheckpointSelected: (String) -> Unit,

    positionAlertEnabled: Boolean,
    onPositionAlertEnabledChange: (Boolean) -> Unit,

    positionAlertThreshold: String,
    onPositionAlertThresholdChange: (String) -> Unit,

    forecastAlertEnabled: Boolean,
    onForecastAlertEnabledChange: (Boolean) -> Unit,

    forecastAlertMinutes: String,
    onForecastAlertMinutesChange: (String) -> Unit,

    calledAlertEnabled: Boolean,
    onCalledAlertEnabledChange: (Boolean) -> Unit,

    onStartTracking: () -> Unit
) {

    var expanded by remember {
        mutableStateOf(false)
    }

    val checkpoints = listOf(
        "Бенякони",
        "Берестовица",
        "Брест",
        "Брузги",
        "Григоровщина",
        "Каменный Лог",
        "Козловичи"
    )

    val canStart =
        carNumber.isNotBlank() &&
            checkpoint.isNotBlank()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .imePadding()
            .navigationBarsPadding(),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "QueueWatch",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Мониторинг электронной очереди",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )


        /* ----------------------------------------------------
           НОМЕР АВТОМОБИЛЯ
           ---------------------------------------------------- */

        OutlinedTextField(
            value = carNumber,

            onValueChange = {
                onCarNumberChange(it)
            },

            label = {
                Text("Введите номер")
            },

            placeholder = {
                Text("Например: 1234AB7")
            },

            singleLine = true,

            modifier = Modifier.fillMaxWidth()
        )


        Spacer(
            modifier = Modifier.height(16.dp)
        )


        /* ----------------------------------------------------
           ВЫБОР КПП
           ---------------------------------------------------- */

        ExposedDropdownMenuBox(
            expanded = expanded,

            onExpandedChange = {
                expanded = !expanded
            },

            modifier = Modifier.fillMaxWidth()
        ) {

            OutlinedTextField(
                value = checkpoint,

                onValueChange = {},

                readOnly = true,

                label = {
                    Text("Выберите пункт пропуска")
                },

                trailingIcon = {

                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )
                },

                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )


            ExposedDropdownMenu(
                expanded = expanded,

                onDismissRequest = {
                    expanded = false
                }
            ) {

                checkpoints.forEach { name ->

                    androidx.compose.material3.DropdownMenuItem(

                        text = {
                            Text(name)
                        },

                        onClick = {

                            onCheckpointSelected(name)

                            expanded = false
                        }
                    )
                }
            }
        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        /* ====================================================
           НАСТРОЙКИ ОПОВЕЩЕНИЙ
           ==================================================== */

        Text(
            text = "Оповещения",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )


        /* ----------------------------------------------------
           ПОЗИЦИЯ
           ---------------------------------------------------- */

        Button(
            onClick = {
                onPositionAlertEnabledChange(
                    !positionAlertEnabled
                )
            },

            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    if (positionAlertEnabled) {
                        "✓ Оповещать по позиции"
                    } else {
                        "Оповещение по позиции выключено"
                    }
            )
        }


        if (positionAlertEnabled) {

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = positionAlertThreshold,

                onValueChange = {
                    onPositionAlertThresholdChange(
                        it.filter { char ->
                            char.isDigit()
                        }
                    )
                },

                label = {
                    Text("Позиция: или меньше")
                },

                placeholder = {
                    Text("100")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth()
            )
        }


        Spacer(
            modifier = Modifier.height(12.dp)
        )


        /* ----------------------------------------------------
           ПРЕДУПРЕЖДЕНИЕ ДО ВЫЗОВА
           ---------------------------------------------------- */

        Button(
            onClick = {
                onForecastAlertEnabledChange(
                    !forecastAlertEnabled
                )
            },

            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    if (forecastAlertEnabled) {
                        "✓ Предупреждать до вызова"
                    } else {
                        "Предупреждение до вызова выключено"
                    }
            )
        }


        if (forecastAlertEnabled) {

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = forecastAlertMinutes,

                onValueChange = {
                    onForecastAlertMinutesChange(
                        it.filter { char ->
                            char.isDigit()
                        }
                    )
                },

                label = {
                    Text("Предупредить за минут")
                },

                placeholder = {
                    Text("30")
                },

                singleLine = true,

                modifier = Modifier.fillMaxWidth()
            )
        }


        Spacer(
            modifier = Modifier.height(12.dp)
        )


        /* ----------------------------------------------------
           ФАКТИЧЕСКИЙ ВЫЗОВ
           ---------------------------------------------------- */

        Button(
            onClick = {
                onCalledAlertEnabledChange(
                    !calledAlertEnabled
                )
            },

            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    if (calledAlertEnabled) {
                        "✓ Оповещать о вызове"
                    } else {
                        "Оповещение о вызове выключено"
                    }
            )
        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        /* ----------------------------------------------------
           НАЧАЛО
           ---------------------------------------------------- */

        Button(
            onClick = onStartTracking,

            enabled = canStart,

            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {

            Text(
                text = "Начать отслеживание"
            )
        }
    }
}


/* ============================================================
   ЭКРАН ОТСЛЕЖИВАНИЯ
   ============================================================ */

@Composable
private fun TrackingScreen(
    carNumber: String,
    checkpointName: String,

    positionAlertEnabled: Boolean,
    positionAlertThreshold: Int,

    forecastAlertEnabled: Boolean,
    forecastAlertMinutes: Int,

    calledAlertEnabled: Boolean
) {

    val context =
        LocalContext.current


    val api = remember {
        QueueApi()
    }


    val analyzer = remember {
        QueueAnalyzer(context)
    }


    /*
     * Менеджер оповещений.
     *
     * Он живёт весь текущий сеанс
     * отслеживания.
     */

    val alertManager = remember {
        QueueAlertManager(context)
    }


    /*
     * Освобождаем TextToSpeech
     * при уничтожении экрана.
     */

    androidx.compose.runtime.DisposableEffect(
        Unit
    ) {

        onDispose {
            alertManager.release()
        }
    }


    /*
     * Реальные checkpointId API.
     */

    val checkpointId = when (checkpointName) {

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


    var message by remember {
        mutableStateOf("Подготовка...")
    }


    var position by remember {
        mutableStateOf<Int?>(null)
    }


    var previousPosition by remember {
        mutableStateOf<Int?>(null)
    }


    var vehicleState by remember {
        mutableStateOf<VehicleState?>(null)
    }


    var queueCount by remember {
        mutableStateOf<Int?>(null)
    }


    var lastUpdate by remember {
        mutableStateOf("")
    }


    var speed by remember {
        mutableStateOf<QueueSpeed?>(null)
    }


    var forecastMinutes by remember {
        mutableStateOf<Double?>(null)
    }


    /*
     * Исчезновение автомобиля из JSON
     * не считается вызовом.
     */

    var vehicleWasConfirmed by remember {
        mutableStateOf(false)
    }


    /*
     * Однократность событий.
     */

    var positionAlertTriggered by remember {
        mutableStateOf(false)
    }

    var forecastAlertTriggered by remember {
        mutableStateOf(false)
    }

    var calledAlertTriggered by remember {
        mutableStateOf(false)
    }


    /*
     * Если пользователь уже подтвердил
     * позиционное оповещение, оно больше
     * не запускается в этом сеансе.
     */

    LaunchedEffect(
        carNumber,
        checkpointName
    ) {

        analyzer.reset()

        alertManager.reset()

        position = null
        previousPosition = null
        vehicleState = null
        queueCount = null
        lastUpdate = ""
        speed = null
        forecastMinutes = null
        vehicleWasConfirmed = false

        positionAlertTriggered = false
        forecastAlertTriggered = false
        calledAlertTriggered = false


        if (carNumber.isBlank()) {

            message =
                "Номер автомобиля не введён."

            return@LaunchedEffect
        }


        if (checkpointId == null) {

            message =
                "Для КПП «$checkpointName» " +
                    "ID API пока не подключён."

            return@LaunchedEffect
        }


        while (true) {

            message =
                "Получение данных очереди..."


            try {

                val result =
                    api.getMonitoring(
                        checkpointId
                    )


                result.fold(

                    onSuccess = { json ->

                        try {

                            val vehicles =
                                analyzer.processSnapshot(
                                    json = json,

                                    checkpointName =
                                        checkpointName
                                )


                            queueCount =
                                vehicles.size


                            lastUpdate =
                                SimpleDateFormat(
                                    "HH:mm:ss",
                                    Locale.getDefault()
                                ).format(
                                    Date()
                                )


                            val vehicle =
                                analyzer.findVehicle(
                                    json,
                                    carNumber
                                )


                            if (vehicle != null) {

                                vehicleWasConfirmed =
                                    true


                                val detectedState =
                                    analyzer.determineState(
                                        vehicle
                                    )


                                vehicleState =
                                    detectedState


                                when (detectedState) {

                                    VehicleState.IN_QUEUE -> {

                                        val newPosition =
                                            vehicle.position


                                        /*
                                         * Проверяем переход
                                         * через заданный порог.
                                         *
                                         * Например:
                                         *
                                         * 105 -> 95
                                         *
                                         * считается достижением
                                         * позиции 100 или меньше.
                                         */

                                        if (
                                            positionAlertEnabled &&
                                            !positionAlertTriggered &&
                                            newPosition != null
                                        ) {

                                            val crossedThreshold =
                                                if (
                                                    previousPosition == null
                                                ) {

                                                    newPosition <=
                                                        positionAlertThreshold

                                                } else {

                                                    previousPosition!! >
                                                        positionAlertThreshold &&
                                                        newPosition <=
                                                        positionAlertThreshold
                                                }


                                            if (
                                                crossedThreshold
                                            ) {

                                                positionAlertTriggered =
                                                    true

                                                alertManager.trigger(
                                                    AlertType.POSITION,

                                                    "Автомобиль достиг позиции " +
                                                        "$positionAlertThreshold " +
                                                        "или меньше."
                                                )
                                            }
                                        }


                                        previousPosition =
                                            newPosition


                                        position =
                                            newPosition


                                        val forecast =
                                            analyzer.calculateForecast(

                                                regnum =
                                                    vehicle.regnum,

                                                checkpointName =
                                                    checkpointName
                                            )


                                        speed =
                                            forecast?.speed


                                        forecastMinutes =
                                            forecast?.estimatedMinutes


                                        /*
                                         * Предупреждение по прогнозу.
                                         *
                                         * Срабатывает один раз,
                                         * когда прогноз впервые
                                         * становится <= заданного
                                         * времени.
                                         */

                                        if (
                                            forecastAlertEnabled &&
                                            !forecastAlertTriggered &&
                                            forecastMinutes != null &&
                                            forecastMinutes!! <=
                                                forecastAlertMinutes
                                        ) {

                                            forecastAlertTriggered =
                                                true

                                            val roundedMinutes =
                                                forecastMinutes!!
                                                    .toInt()
                                                    .coerceAtLeast(0)

                                            alertManager.trigger(
                                                AlertType.FORECAST,

                                                "До вызова автомобиля " +
                                                    "ориентировочно " +
                                                    "$roundedMinutes минут."
                                            )
                                        }


                                        message =
                                            if (
                                                newPosition != null
                                            ) {

                                                "Автомобиль находится " +
                                                    "в живой очереди."

                                            } else {

                                                "Автомобиль найден, " +
                                                    "но позиция не передана."
                                            }
                                    }


                                    VehicleState.CALLED -> {

                                        message =
                                            "Автомобиль вызван " +
                                                "в пункт пропуска."


                                        /*
                                         * Фактический вызов.
                                         *
                                         * Только status == 3.
                                         */

                                        if (
                                            calledAlertEnabled &&
                                            !calledAlertTriggered
                                        ) {

                                            calledAlertTriggered =
                                                true

                                            alertManager.trigger(
                                                AlertType.CALLED,

                                                "Автомобиль вызван " +
                                                    "в пункт пропуска."
                                            )
                                        }


                                        position = null

                                        speed = null

                                        forecastMinutes = null
                                    }


                                    VehicleState.UNKNOWN -> {

                                        message =
                                            "Автомобиль найден, " +
                                                "но сервер не дал " +
                                                "однозначного состояния."
                                    }
                                }


                            } else {

                                /*
                                 * Автомобиль отсутствует.
                                 *
                                 * Никакого вызова здесь
                                 * не генерируем.
                                 */

                                if (!vehicleWasConfirmed) {

                                    vehicleState = null

                                    message =
                                        "Автомобиль пока не обнаружен. " +
                                            "Ожидаем следующее обновление."

                                } else {

                                    message =
                                        "Данные автомобиля временно " +
                                            "отсутствуют. Последняя " +
                                            "подтверждённая позиция " +
                                            "сохраняется."
                                }
                            }

                        } catch (e: Exception) {

                            message =
                                "Ошибка обработки ответа: " +
                                    (
                                        e.message
                                            ?: "неизвестная ошибка"
                                    )
                        }
                    },


                    onFailure = { error ->

                        message =
                            "Ошибка получения данных: " +
                                (
                                    error.message
                                        ?: "неизвестная ошибка"
                                )
                    }
                )

            } catch (e: Exception) {

                message =
                    "Ошибка мониторинга: " +
                        (
                            e.message
                                ?: "неизвестная ошибка"
                        )
            }


            delay(20_000)
        }
    }


    /* ========================================================
       ИНТЕРФЕЙС
       ======================================================== */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Отслеживание",
            style = MaterialTheme.typography.headlineMedium
        )


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        Text(
            text = "Автомобиль: $carNumber"
        )


        Spacer(
            modifier = Modifier.height(8.dp)
        )


        Text(
            text = "Пункт пропуска: $checkpointName"
        )


        Spacer(
            modifier = Modifier.height(24.dp)
        )


        when (vehicleState) {

            VehicleState.IN_QUEUE -> {

                Text(
                    text = "АВТОМОБИЛЬ В ОЧЕРЕДИ",
                    style = MaterialTheme.typography.titleLarge
                )


                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                Text(
                    text =
                        "Позиция: " +
                            (
                                position?.toString()
                                    ?: "—"
                            )
                )


                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                Text(
                    text = "Статус: живая очередь"
                )
            }


            VehicleState.CALLED -> {

                Text(
                    text = "АВТОМОБИЛЬ ВЫЗВАН",
                    style = MaterialTheme.typography.titleLarge
                )


                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                Text(
                    text = "Вызов подтверждён сервером."
                )
            }


            VehicleState.UNKNOWN -> {

                Text(
                    text = "СОСТОЯНИЕ НЕ ОПРЕДЕЛЕНО",
                    style = MaterialTheme.typography.titleLarge
                )
            }


            null -> {

                Text(
                    text = "ОЖИДАНИЕ АВТОМОБИЛЯ",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        Text(
            text = message
        )


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        Text(
            text =
                "Автомобилей в очереди: " +
                    (
                        queueCount?.toString()
                            ?: "—"
                    )
        )


        Spacer(
            modifier = Modifier.height(6.dp)
        )


        Text(
            text =
                "Последнее обновление: " +
                    (
                        lastUpdate.ifEmpty {
                            "—"
                        }
                    )
        )


        if (speed != null) {

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = String.format(
                    Locale.getDefault(),
                    "Скорость очереди: %.2f поз./ч",
                    speed!!.positionsPerHour
                )
            )
        }


        if (forecastMinutes != null) {

            Spacer(
                modifier = Modifier.height(12.dp)
            )


            val totalMinutes =
                forecastMinutes!!
                    .toInt()
                    .coerceAtLeast(0)


            val hours =
                totalMinutes / 60


            val minutes =
                totalMinutes % 60


            Text(
                text =
                    if (hours > 0) {

                        "Ориентировочно до вызова: " +
                            "$hours ч $minutes мин"

                    } else {

                        "Ориентировочно до вызова: " +
                            "$minutes мин"
                    }
            )
        }
    }


    /*
     * Всплывающее окно активного оповещения.
     *
     * Оно остаётся открытым,
     * пока пользователь не подтвердит
     * сообщение.
     */

    val activeAlert =
        alertManager.activeAlert


    if (activeAlert != null) {

        AlertDialog(

            onDismissRequest = {
                /*
                 * Нельзя закрыть окно
                 * простым нажатием снаружи.
                 *
                 * Требуется подтверждение.
                 */
            },

            title = {

                Text(
                    text = activeAlert.title
                )
            },

            text = {

                Text(
                    text = activeAlert.message
                )
            },

            confirmButton = {

                Button(
                    onClick = {
                        alertManager.acknowledge()
                    }
                ) {

                    Text(
                        text = "ПОДТВЕРДИТЬ"
                    )
                }
            }
        )
    }
}
