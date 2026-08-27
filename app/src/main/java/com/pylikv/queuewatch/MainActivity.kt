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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

    var trackingStarted by rememberSaveable {
        mutableStateOf(false)
    }

    var carNumber by rememberSaveable {
        mutableStateOf("")
    }

    var checkpointSelected by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedCheckpoint by rememberSaveable {
        mutableStateOf("")
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            if (!trackingStarted) {

                StartScreen(
                    onStart = {
                        trackingStarted = true
                    }
                )

            } else if (!checkpointSelected) {

                CheckpointScreen(
                    carNumber = carNumber,
                    onCarNumberChange = {
                        carNumber = it
                    },
                    onCheckpointSelected = {
                        selectedCheckpoint = it
                        checkpointSelected = true
                    }
                )

            } else {

                TrackingScreen(
                    carNumber = carNumber,
                    checkpointName = selectedCheckpoint
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

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "QueueWatch",
            style =
                MaterialTheme.typography.headlineLarge
        )

        Text(
            text =
                "Мониторинг электронной очереди",
            modifier =
                Modifier.padding(top = 12.dp)
        )

        Button(
            onClick = onStart,
            modifier =
                Modifier.padding(top = 24.dp)
        ) {

            Text(
                text = "Начать отслеживание"
            )
        }
    }
}


/* ============================================================
   ВЫБОР АВТОМОБИЛЯ И КПП
   ============================================================ */

@Composable
private fun CheckpointScreen(
    carNumber: String,
    onCarNumberChange: (String) -> Unit,
    onCheckpointSelected: (String) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Text(
            text = "Настройка отслеживания",
            style =
                MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Text(
            text = "Введите номер автомобиля"
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
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

            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(28.dp)
        )

        Text(
            text = "Выберите пункт пропуска",
            style =
                MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        /* ---------- Польша ---------- */

        Text(
            text = "🇵🇱 Польша",
            style =
                MaterialTheme.typography.titleMedium,

            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        CheckpointButton(
            name = "Брест",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Козловичи",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Берестовица",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Брузги",
            onClick = onCheckpointSelected
        )


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        /* ---------- Литва ---------- */

        Text(
            text = "🇱🇹 Литва",
            style =
                MaterialTheme.typography.titleMedium,

            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        CheckpointButton(
            name = "Каменный Лог",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Бенякони",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Котловка",
            onClick = onCheckpointSelected
        )


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        /* ---------- Латвия ---------- */

        Text(
            text = "🇱🇻 Латвия",
            style =
                MaterialTheme.typography.titleMedium,

            modifier =
                Modifier.fillMaxWidth()
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        CheckpointButton(
            name = "Григоровщина",
            onClick = onCheckpointSelected
        )

        CheckpointButton(
            name = "Урбаны",
            onClick = onCheckpointSelected
        )

        Spacer(
            modifier =
                Modifier.height(32.dp)
        )
    }
}


@Composable
private fun CheckpointButton(
    name: String,
    onClick: (String) -> Unit
) {

    Button(
        onClick = {
            onClick(name)
        },

        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            text = name
        )
    }

    Spacer(
        modifier =
            Modifier.height(8.dp)
    )
}


/* ============================================================
   ЭКРАН ОТСЛЕЖИВАНИЯ
   ============================================================ */

@Composable
private fun TrackingScreen(
    carNumber: String,
    checkpointName: String
) {

    val api =
        remember {
            QueueApi()
        }

    val analyzer =
        remember {
            QueueAnalyzer()
        }


    /*
     * Пока реальный checkpointId подтверждён
     * только для Беняконей.
     */
    val checkpointId =
        when (checkpointName) {

            "Бенякони" ->
                "53d94097-2b34-11ec-8467-ac1f6fb889c0"

            else ->
                null
        }


    var message by remember {

        mutableStateOf(
            "Подготовка к подключению..."
        )
    }


    var position by remember {

        mutableStateOf<Int?>(null)
    }


    var state by remember {

        mutableStateOf<VehicleState?>(null)
    }


    var estimatedMinutes by remember {

        mutableStateOf<Double?>(null)
    }


    var speed by remember {

        mutableStateOf<QueueSpeed?>(null)
    }


    LaunchedEffect(
        carNumber,
        checkpointName
    ) {

        /*
         * Новый мониторинг —
         * очищаем старую историю.
         */
        analyzer.reset()


        if (carNumber.isBlank()) {

            message =
                "Введите номер автомобиля."

            return@LaunchedEffect
        }


        if (checkpointId == null) {

            message =
                "API для КПП «$checkpointName» " +
                "пока не подключено."

            return@LaunchedEffect
        }


        /*
         * Бесконечный цикл мониторинга.
         *
         * Новый запрос каждые 60 секунд.
         */
        while (true) {

            try {

                message =
                    "Получение данных очереди..."


                val response =
                    withContext(
                        Dispatchers.IO
                    ) {

                        api.getMonitoring(
                            checkpointId
                        )
                    }


                response.fold(

                    onSuccess = { json ->

                        /*
                         * Передаём полученный JSON
                         * в анализатор.
                         */
                        val queue =
                            analyzer.processSnapshot(
                                json
                            )


                        /*
                         * Нормализуем номер.
                         */
                        val normalized =
                            normalizeRegnum(
                                carNumber
                            )


                        /*
                         * Ищем автомобиль
                         * в живой очереди.
                         */
                        val vehicle =
                            queue.firstOrNull {

                                normalizeRegnum(
                                    it.regnum
                                ) == normalized
                            }


                        if (vehicle != null) {

                            /*
                             * Автомобиль имеет
                             * реальный order_id.
                             */
                            state =
                                VehicleState.IN_QUEUE

                            position =
                                vehicle.position


                            val forecast =
                                analyzer.calculateForecast(
                                    vehicle.regnum
                                )


                            estimatedMinutes =
                                forecast
                                    ?.estimatedMinutes


                            speed =
                                forecast?.speed


                            message =
                                "Автомобиль находится " +
                                "в живой очереди."

                        } else {

                            /*
                             * Машины с order_id
                             * в живой очереди нет.
                             *
                             * Проверяем весь исходный JSON
                             * на случай status = 3.
                             */
                            val allVehicles =
                                analyzer.parseQueue(
                                    json
                                )


                            val rawVehicle =
                                allVehicles.firstOrNull {

                                    normalizeRegnum(
                                        it.regnum
                                    ) == normalized
                                }


                            if (
                                rawVehicle != null
                            ) {

                                val detectedState =
                                    analyzer.determineState(
                                        rawVehicle
                                    )

                                state =
                                    detectedState

                                position = null
                                estimatedMinutes = null
                                speed = null


                                message =
                                    when (
                                        detectedState
                                    ) {

                                        VehicleState.CALLED ->
                                            "Автомобиль " +
                                            "вызван в пункт пропуска."

                                        VehicleState.UNKNOWN ->
                                            "Автомобиль обнаружен, " +
                                            "но его состояние " +
                                            "пока не определено."

                                        VehicleState.IN_QUEUE ->
                                            "Автомобиль находится " +
                                            "в живой очереди."
                                    }

                            } else {

                                /*
                                 * В текущем снимке
                                 * автомобиля нет.
                                 *
                                 * Не объявляем его сразу
                                 * вызванным.
                                 */
                                state =
                                    VehicleState.UNKNOWN

                                position = null
                                estimatedMinutes = null
                                speed = null

                                message =
                                    "Автомобиль не найден " +
                                    "в текущем снимке. " +
                                    "Ожидаем следующее обновление."
                            }
                        }
                    },


                    onFailure = { error ->

                        message =
                            "Ошибка получения данных: " +
                            (error.message
                                ?: "неизвестная ошибка")
                    }
                )

            } catch (e: Exception) {

                message =
                    "Ошибка: " +
                    (e.message
                        ?: "неизвестная ошибка")
            }


            /*
             * Ждём одну минуту
             * перед следующим запросом.
             */
            delay(60_000)
        }
    }


    /* ========================================================
       ИНТЕРФЕЙС
       ======================================================== */

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Отслеживание",
            style =
                MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Text(
            text =
                "Автомобиль: $carNumber"
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text =
                "Пункт пропуска: $checkpointName"
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        Text(
            text =
                when (state) {

                    VehicleState.IN_QUEUE ->
                        "В ЖИВОЙ ОЧЕРЕДИ"

                    VehicleState.CALLED ->
                        "ВЫЗВАН В ПУНКТ ПРОПУСКА"

                    VehicleState.UNKNOWN ->
                        "СОСТОЯНИЕ НЕ ОПРЕДЕЛЕНО"

                    null ->
                        "ПОДКЛЮЧЕНИЕ..."
                },

            style =
                MaterialTheme.typography.titleLarge
        )


        if (position != null) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    "Позиция: №$position"
            )
        }


        if (speed != null) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    "Скорость очереди: " +
                    "${"%.1f".format(
                        speed!!.positionsPerHour
                    )} поз./час"
            )
        }


        if (
            estimatedMinutes != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    "Прогноз до вызова: " +
                    formatMinutes(
                        estimatedMinutes!!
                    )
            )
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        Text(
            text = message
        )
    }
}


/* ============================================================
   ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
   ============================================================ */

private fun normalizeRegnum(
    value: String
): String {

    return value
        .uppercase()
        .replace(" ", "")
        .replace("-", "")
        .trim()
}


private fun formatMinutes(
    minutes: Double
): String {

    val rounded =
        minutes
            .coerceAtLeast(0.0)
            .toInt()

    val hours =
        rounded / 60

    val remaining =
        rounded % 60

    return if (hours > 0) {

        "$hours ч $remaining мин"

    } else {

        "$remaining мин"
    }
}
