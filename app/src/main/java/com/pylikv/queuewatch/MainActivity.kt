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
import androidx.compose.material3.Button
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


@Composable
fun QueueWatchApp() {

    var started by rememberSaveable {
        mutableStateOf(false)
    }

    var carNumber by rememberSaveable {
        mutableStateOf("")
    }

    var checkpoint by rememberSaveable {
        mutableStateOf("")
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            if (!started) {

                StartScreen(
                    onStart = {
                        started = true
                    }
                )

            } else if (checkpoint.isEmpty()) {

                SetupScreen(
                    carNumber = carNumber,
                    onCarNumberChange = {
                        carNumber = it
                    },
                    onCheckpointSelected = {
                        checkpoint = it
                    }
                )

            } else {

                TrackingScreen(
                    carNumber = carNumber,
                    checkpointName = checkpoint
                )
            }
        }
    }
}


/* ============================================================
   СТАРТОВЫЙ ЭКРАН
   ============================================================ */

@Composable
private fun StartScreen(
    onStart: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "QueueWatch",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Мониторинг электронной очереди"
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onStart
        ) {

            Text(
                text = "Начать отслеживание"
            )
        }
    }
}


/* ============================================================
   ЭКРАН НАСТРОЙКИ
   ============================================================ */

@Composable
private fun SetupScreen(
    carNumber: String,
    onCarNumberChange: (String) -> Unit,
    onCheckpointSelected: (String) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Настройка отслеживания",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedTextField(
            value = carNumber,

            onValueChange = {
                onCarNumberChange(it)
            },

            label = {
                Text("Номер автомобиля")
            },

            singleLine = true,

            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "Выберите пункт пропуска",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * Подключённые и подтверждённые КПП.
         */

        CheckpointButton(
            name = "Бенякони",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Берестовица",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Брест",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Брузги",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Григоровщина",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Каменный Лог",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Козловичи",
            onClick = onCheckpointSelected
        )
    }
}


/* ============================================================
   КНОПКА КПП
   ============================================================ */

@Composable
private fun CheckpointButton(
    name: String,
    onClick: (String) -> Unit
) {

    Button(
        onClick = {
            onClick(name)
        },

        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {

        Text(
            text = name
        )
    }
}


/* ============================================================
   ЭКРАН ОТСЛЕЖИВАНИЯ
   ============================================================ */

@Composable
private fun TrackingScreen(
    carNumber: String,
    checkpointName: String
) {

    val api = remember {
        QueueApi()
    }

    val analyzer = remember {
        QueueAnalyzer()
    }


    /*
     * Реальные checkpointId, полученные из API.
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
     * Этот флаг нужен для правильной обработки
     * временного исчезновения автомобиля.
     *
     * Исчезновение из JSON НЕ считается вызовом.
     */

    var vehicleWasConfirmed by remember {
        mutableStateOf(false)
    }


    /* ========================================================
       МОНИТОРИНГ
       ======================================================== */

    LaunchedEffect(
        carNumber,
        checkpointName
    ) {

        analyzer.reset()

        position = null
        vehicleState = null
        queueCount = null
        lastUpdate = ""
        speed = null
        forecastMinutes = null
        vehicleWasConfirmed = false


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

                            /*
                             * Разбираем полный ответ API.
                             *
                             * QueueAnalyzer самостоятельно
                             * обрабатывает truckLiveQueue,
                             * carLiveQueue, busLiveQueue
                             * и motorcycleLiveQueue.
                             */

                            val vehicles =
                                analyzer.processSnapshot(
                                    json
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


                            /*
                             * Ищем конкретный автомобиль
                             * по регистрационному номеру.
                             */

                            val vehicle =
                                analyzer.findVehicle(
                                    json,
                                    carNumber
                                )


                            if (vehicle != null) {

                                vehicleWasConfirmed = true


                                val detectedState =
                                    analyzer.determineState(
                                        vehicle
                                    )


                                vehicleState =
                                    detectedState


                                when (detectedState) {

                                    VehicleState.IN_QUEUE -> {

                                        /*
                                         * Используем реальный
                                         * order_id из API.
                                         */

                                        position =
                                            vehicle.position


                                        /*
                                         * После первого наблюдения
                                         * прогноза может ещё не быть.
                                         */

                                        val forecast =
                                            analyzer.calculateForecast(
                                                vehicle.regnum
                                            )


                                        speed =
                                            forecast?.speed


                                        forecastMinutes =
                                            forecast?.estimatedMinutes


                                        message =
                                            if (
                                                vehicle.position != null
                                            ) {

                                                "Автомобиль находится " +
                                                    "в живой очереди."

                                            } else {

                                                "Автомобиль найден, " +
                                                    "но позиция не передана."
                                            }
                                    }


                                    VehicleState.CALLED -> {

                                        /*
                                         * Только status == 3
                                         * считается подтверждённым
                                         * вызовом.
                                         */

                                        message =
                                            "Автомобиль вызван " +
                                                "в пункт пропуска."

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
                                 * Машины нет в текущем JSON.
                                 *
                                 * Это НЕ означает вызов.
                                 */

                                if (!vehicleWasConfirmed) {

                                    vehicleState = null

                                    message =
                                        "Автомобиль пока не обнаружен. " +
                                            "Ожидаем следующее обновление."

                                } else {

                                    /*
                                     * Сохраняем последнее известное
                                     * состояние и позицию.
                                     */

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

                        /*
                         * Ошибка сети не меняет
                         * состояние автомобиля.
                         */

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


            /*
             * Обновление каждые 20 секунд.
             */

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

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
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
                "Автомобилей получено: " +
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
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    "Ориентировочно до вызова: " +
                        "${forecastMinutes!!.toInt()} мин."
            )
        }
    }
}
