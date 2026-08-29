package com.pylikv.queuewatch

import android.content.Intent
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


    /*
     * Запускаем Foreground Service.
     *
     * После этого API опрашивается
     * не Activity, а сервисом.
     */

    LaunchedEffect(
        carNumber,
        checkpointName
    ) {

        val intent =
            Intent(
                context,
                QueueWatchService::class.java
            ).apply {

                putExtra(
                    QueueWatchService.EXTRA_CAR_NUMBER,
                    carNumber
                )

                putExtra(
                    QueueWatchService.EXTRA_CHECKPOINT,
                    checkpointName
                )

                putExtra(
                    QueueWatchService.EXTRA_POSITION_ALERT_ENABLED,
                    positionAlertEnabled
                )

                putExtra(
                    QueueWatchService.EXTRA_POSITION_THRESHOLD,
                    positionAlertThreshold
                )

                putExtra(
                    QueueWatchService.EXTRA_FORECAST_ALERT_ENABLED,
                    forecastAlertEnabled
                )

                putExtra(
                    QueueWatchService.EXTRA_FORECAST_MINUTES,
                    forecastAlertMinutes
                )

                putExtra(
                    QueueWatchService.EXTRA_CALLED_ALERT_ENABLED,
                    calledAlertEnabled
                )
            }


        androidx.core.content.ContextCompat.startForegroundService(
            context,
            intent
        )
    }


    /*
     * Читаем состояние сервиса.
     *
     * Это позволяет экрану обновляться,
     * даже если сам API-цикл работает
     * независимо от Activity.
     */

    val preferences =
        remember {

            context.getSharedPreferences(
                QueueWatchService.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
        }


    var position by remember {
        mutableStateOf<Int?>(null)
    }

    var vehicleState by remember {
        mutableStateOf<String>("")
    }

    var queueCount by remember {
        mutableStateOf<Int?>(null)
    }

    var speed by remember {
        mutableStateOf<Double?>(null)
    }

    var forecastMinutes by remember {
        mutableStateOf<Double?>(null)
    }

    var message by remember {
        mutableStateOf("Подготовка...")
    }

    var lastUpdate by remember {
        mutableStateOf("")
    }


    LaunchedEffect(Unit) {

        while (true) {

            position =
                if (
                    preferences.contains(
                        QueueWatchService.KEY_POSITION
                    )
                ) {

                    preferences.getInt(
                        QueueWatchService.KEY_POSITION,
                        0
                    )
                } else {
                    null
                }


            vehicleState =
                preferences.getString(
                    QueueWatchService.KEY_STATE,
                    ""
                ) ?: ""


            queueCount =
                if (
                    preferences.contains(
                        QueueWatchService.KEY_QUEUE_COUNT
                    )
                ) {

                    preferences.getInt(
                        QueueWatchService.KEY_QUEUE_COUNT,
                        0
                    )
                } else {
                    null
                }


            speed =
                if (
                    preferences.contains(
                        QueueWatchService.KEY_SPEED
                    )
                ) {

                    preferences.getFloat(
                        QueueWatchService.KEY_SPEED,
                        0f
                    ).toDouble()

                } else {
                    null
                }


            forecastMinutes =
                if (
                    preferences.contains(
                        QueueWatchService.KEY_FORECAST
                    )
                ) {

                    preferences.getFloat(
                        QueueWatchService.KEY_FORECAST,
                        0f
                    ).toDouble()

                } else {
                    null
                }


            message =
                preferences.getString(
                    QueueWatchService.KEY_MESSAGE,
                    "Подготовка..."
                ) ?: "Подготовка..."


            lastUpdate =
                preferences.getString(
                    QueueWatchService.KEY_LAST_UPDATE,
                    ""
                ) ?: ""


            delay(1_000)
        }
    }


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

            "IN_QUEUE" -> {

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


            "CALLED" -> {

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


            "UNKNOWN" -> {

                Text(
                    text = "СОСТОЯНИЕ НЕ ОПРЕДЕЛЕНО",
                    style = MaterialTheme.typography.titleLarge
                )
            }


            else -> {

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
                text =
                    String.format(
                        Locale.getDefault(),
                        "Скорость очереди: %.2f поз./ч",
                        speed
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
}
