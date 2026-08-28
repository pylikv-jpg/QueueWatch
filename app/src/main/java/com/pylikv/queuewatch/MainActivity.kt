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
import kotlinx.coroutines.delay

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

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text = "Мониторинг электронной очереди"
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
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
   ЭКРАН ВВОДА НОМЕРА И ВЫБОРА КПП
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
                Text(
                    "Номер автомобиля"
                )
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
     * СПРАВОЧНИК КПП.
     *
     * Пока мы точно подтвердили
     * реальный ID только для Беняконей.
     *
     * ВАЖНО:
     * здесь используется правильный ID,
     * полученный из реального ответа API.
     */

    val checkpointId =
        when (checkpointName) {

            "Бенякони" ->
                "53d94097-2b34-11ec-8467-ac1f6bf889c0"

            else ->
                null
        }


    /*
     * Текущее сообщение.
     */

    var message by remember {

        mutableStateOf(
            "Подготовка к подключению..."
        )
    }


    /*
     * Последняя подтверждённая позиция.
     *
     * Если автомобиль временно исчезнет
     * из JSON, значение НЕ сбрасываем.
     */

    var position by remember {

        mutableStateOf<Int?>(null)
    }


    /*
     * Текущее состояние автомобиля.
     */

    var state by remember {

        mutableStateOf<VehicleState?>(null)
    }


    /*
     * Прогноз времени.
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
     * Был ли автомобиль хотя бы один раз
     * подтверждён сервером.
     */

    var vehicleWasConfirmed by remember {

        mutableStateOf(false)
    }


    /*
     * Количество автомобилей,
     * полученных от сервера.
     *
     * Это добавлено специально для диагностики.
     */

    var queueCount by remember {

        mutableStateOf<Int?>(null)
    }


    /*
     * Время последнего успешного
     * получения данных.
     */

    var lastUpdate by remember {

        mutableStateOf("")
    }


    /*
     * Главный цикл мониторинга.
     */

    LaunchedEffect(
        carNumber,
        checkpointName
    ) {

        /*
         * Начинаем новый мониторинг.
         */

        analyzer.reset()

        position = null
        state = null
        estimatedMinutes = null
        speed = null
        vehicleWasConfirmed = false
        queueCount = null
        lastUpdate = ""


        /*
         * Проверяем номер.
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
         * Бесконечный мониторинг.
         *
         * Запрос каждые 60 секунд.
         */

        while (true) {

            try {

                message =
                    "Получение данных очереди..."


                /*
                 * Получаем свежий JSON.
                 */

                val response =
                    api.getMonitoring(
                        checkpointId
                    )


                response.fold(

                    onSuccess = { json ->

                        /*
                         * Разбираем полученный JSON.
                         */

                        val vehicles =
                            analyzer.processSnapshot(
                                json
                            )


                        /*
                         * Сохраняем количество
                         * автомобилей в ответе.
                         */

                        queueCount =
                            vehicles.size


                        /*
                         * Запоминаем время
                         * успешного получения.
                         */

                        lastUpdate =
                            java.text.SimpleDateFormat(
                                "HH:mm:ss",
                                java.util.Locale.getDefault()
                            ).format(
                                java.util.Date()
                            )


                        /*
                         * Ищем именно наш номер.
                         */

                        val vehicle =
                            analyzer.findVehicle(
                                json,
                                carNumber
                            )


                        if (vehicle != null) {

                            /*
                             * Автомобиль найден.
                             */

                            vehicleWasConfirmed = true


                            /*
                             * Определяем состояние
                             * по данным сервера.
                             */

                            val detectedState =
                                analyzer.determineState(
                                    vehicle
                                )


                            when (detectedState) {

                                VehicleState.IN_QUEUE -> {

                                    state =
                                        VehicleState.IN_QUEUE


                                    /*
                                     * Позиция берётся
                                     * непосредственно
                                     * из order_id.
                                     */

                                    position =
                                        vehicle.position


                                    /*
                                     * Рассчитываем прогноз.
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

                                    state =
                                        VehicleState.CALLED

                                    position = null

                                    estimatedMinutes = null

                                    speed = null

                                    message =
                                        "Автомобиль вызван " +
                                            "в пункт пропуска."
                                }


                                VehicleState.UNKNOWN -> {

                                    state =
                                        VehicleState.UNKNOWN

                                    message =
                                        "Автомобиль обнаружен, " +
                                            "но его состояние " +
                                            "пока не определено."
                                }
                            }


                        } else {

                            /*
                             * Автомобиль отсутствует
                             * в текущем снимке.
                             *
                             * ВАЖНО:
                             *
                             * это НЕ означает вызов.
                             */

                            if (!vehicleWasConfirmed) {

                                /*
                                 * Первый запуск.
                                 */

                                state = null

                                message =
                                    "Автомобиль пока не обнаружен. " +
                                        "Ожидаем следующее обновление."

                            } else {

                                /*
                                 * Автомобиль раньше был найден,
                                 * но сейчас временно отсутствует.
                                 *
                                 * Состояние и последнюю позицию
                                 * сохраняем.
                                 */

                                message =
                                    "Данные по автомобилю " +
                                        "временно отсутствуют. " +
                                        "Последняя подтверждённая " +
                                        "позиция сохраняется."
                            }
                        }
                    },


                    onFailure = { error ->

                        /*
                         * Ошибка сети НЕ считается
                         * изменением состояния автомобиля.
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
                 * Защита от неожиданной ошибки.
                 */

                message =
                    "Ошибка мониторинга: " +
                        (
                            e.message
                                ?: "неизвестная ошибка"
                        )
            }


            /*
             * Следующее обновление через 60 секунд.
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
                Modifier.height(20.dp)
        )


        Text(
            text = "Автомобиль: $carNumber"
        )

        Spacer(
            modifier =
          
