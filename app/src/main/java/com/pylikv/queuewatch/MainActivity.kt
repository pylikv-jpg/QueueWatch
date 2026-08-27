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


/* ============================================================
   ГЛАВНОЕ ПРИЛОЖЕНИЕ
   ============================================================ */

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
            text = "Мониторинг электронной очереди",
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
     * На данный момент реальный checkpointId
     * подтверждён только для Беняконей.
     *
     * Остальные КПП не получают выдуманный ID.
     */
    val checkpointId =
        when (checkpointName) {

            "Бенякони" ->
                "53d94097-2b34-11ec-8467-ac1f6fb889c0"

            else ->
                null
        }


    /*
     * Текст текущего сообщения.
     */
    var message by remember {

        mutableStateOf(
            "Подготовка к подключению..."
        )
    }


    /*
     * Последняя подтверждённая позиция.
     *
     * ВАЖНО:
     * если автомобиль временно исчез из JSON,
     * эта позиция НЕ обнуляется.
     */
    var position by remember {

        mutableStateOf<Int?>(null)
    }


    /*
     * Текущее подтверждённое состояние.
     */
    var state by remember {

        mutableStateOf<VehicleState?>(null)
    }


    /*
     * Прогноз.
     */
    var estimatedMinutes by remember {

        mutableStateOf<Double?>(null)
    }


    /*
     * Скорость очереди.
     */
    var speed by remember {

        mutableStateOf<QueueSpeed?>(null)
    }


    /*
     * Счётчик успешных наблюдений автомобиля.
     *
     * Нужен для корректного поведения первого
     * снимка и последующих временных исчезновений.
     */
    var vehicleWasConfirmed by remember {

        mutableStateOf(false)
    }


    LaunchedEffect(
        carNumber,
        checkpointName
    ) {

        /*
         * Новый мониторинг —
         * полностью очищаем старую историю.
         */
        analyzer.reset()

        position = null
        state = null
        estimatedMinutes = null
        speed = null
        vehicleWasConfirmed = false


        /*
         * Проверяем номер автомобиля.
         */
        if (carNumber.isBlank()) {

            message =
                "Введите номер автомобиля."

            return@LaunchedEffect
        }


        /*
         * Проверяем наличие ID КПП.
         */
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


                /*
                 * Получаем свежий JSON.
                 */
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
                         * Передаём весь снимок анализатору.
                         *
                         * Анализатор сохраняет историю
                         * реальных серверных позиций.
                         */
                        analyzer.processSnapshot(
                            json
                        )


                        /*
                         * Ищем автомобиль непосредственно
                         * в текущем серверном JSON.
                         *
                         * Важно:
                         * поиск НЕ зависит от order_id.
                         */
                        val vehicle =
                            analyzer.findVehicle(
                                json,
                                carNumber
                            )


                        if (vehicle != null) {

                            /*
                             * Автомобиль реально присутствует
                             * в текущем ответе сервера.
                             */
                            vehicleWasConfirmed = true


                            /*
                             * Определяем состояние только
                             * по данным текущего сервера.
                             */
                            val detectedState =
                                analyzer.determineState(
                                    vehicle
                                )


                            when (detectedState) {

                                VehicleState.IN_QUEUE -> {

                                    /*
                                     * Сервер передал order_id.
                                     *
                                     * Это подтверждённое нахождение
                                     * в живой очереди.
                                     */
                                    state =
                                        VehicleState.IN_QUEUE


                                    /*
                                     * Позиция берётся только
                                     * из серверного order_id.
                                     */
                                    position =
                                        vehicle.position


                                    /*
                                     * После нового подтверждения
                                     * пересчитываем прогноз.
                                     */
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
                                        if (
                                            position != null
                                        ) {

                                            "Автомобиль находится " +
                                                "в живой очереди."

                                        } else {

                                            "Автомобиль найден, " +
                                                "но сервер пока не передал " +
                                                "позицию."
                                        }
                                }


                                VehicleState.CALLED -> {

                                    /*
                                     * status == 3 —
                                     * единственный подтверждённый
                                     * сервером вызов.
                                     */
                                    state =
                                        VehicleState.CALLED


                                    /*
                                     * После подтверждённого вызова
                                     * позиция очереди больше не нужна.
                                     */
                                    position = null

                                    estimatedMinutes = null

                                    speed = null


                                    message =
                                        "Автомобиль вызван " +
                                            "в пункт пропуска."
                                }


                                VehicleState.UNKNOWN -> {

                                    /*
                                     * Автомобиль есть в JSON,
                                     * но сервер не дал однозначного
                                     * состояния.
                                     */
                                    state =
                                        VehicleState.UNKNOWN


                                    position = null

                                    estimatedMinutes = null

                                    speed = null


                                    message =
                                        "Автомобиль обнаружен, " +
                                            "но его состояние " +
                                            "пока не определено."
                                }
                            }


                        } else {

                            /*
                             * КРИТИЧЕСКОЕ ИЗМЕНЕНИЕ.
                             *
                             * Автомобиль отсутствует в текущем
                             * JSON.
                             *
                             * Мы НЕ считаем это вызовом.
                             *
                             * Мы НЕ меняем state.
                             *
                             * Мы НЕ обнуляем position.
                             *
                             * Мы НЕ обнуляем прогноз.
                             *
                             * Мы ждём следующий снимок.
                             */
                            if (vehicleWasConfirmed) {

                                message =
                                    "Автомобиль временно отсутствует " +
                                        "в текущем снимке. " +
                                        "Сохраняем последнее подтверждённое " +
                                        "состояние и ждём обновление."

                            } else {

                                /*
                                 * Автомобиль ещё ни разу
                                 * не был найден.
                                 */
                                message =
                                    "Автомобиль не найден " +
                                        "в текущем снимке. " +
                                        "Ожидаем следующее обновление."
                            }
                        }
                    },


                    onFailure = { error ->

                        /*
                         * Ошибка сети НЕ означает,
                         * что автомобиль исчез или вызван.
                         *
                         * Последнее подтверждённое состояние
                         * сохраняем.
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

                /*
                 * Исключение сети/API также не меняет
                 * состояние автомобиля.
                 */
                message =
                    "Ошибка: " +
                        (
                            e.message
                                ?: "неизвестная ошибка"
                        )
  
