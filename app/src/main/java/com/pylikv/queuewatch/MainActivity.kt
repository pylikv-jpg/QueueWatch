package com.pylikv.queuewatch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var carNumber by rememberSaveable {
        mutableStateOf("")
    }

    var checkpoint by rememberSaveable {
        mutableStateOf("")
    }

    var trackingStarted by rememberSaveable {
        mutableStateOf(false)
    }

    var positionAlertEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var positionAlertThreshold by rememberSaveable {
        mutableStateOf("100")
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

    calledAlertEnabled: Boolean
) {

    val context =
        LocalContext.current


    /* --------------------------------------------------------
       ВСПЛЫВАЮЩЕЕ ПРЕДУПРЕЖДЕНИЕ
       -------------------------------------------------------- */

    var alertVisible by remember {
        mutableStateOf(false)
    }

    var alertTitle by remember {
        mutableStateOf("Оповещение QueueWatch")
    }

    var alertMessage by remember {
        mutableStateOf("")
    }


    /* --------------------------------------------------------
       ЗАПУСК СЕРВИСА
       -------------------------------------------------------- */

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

                /*
                 * Прогнозное оповещение отключено.
                 */
                putExtra(
                    QueueWatchService.EXTRA_FORECAST_ALERT_ENABLED,
                    false
                )

                putExtra(
                    QueueWatchService.EXTRA_FORECAST_MINUTES,
                    0
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


    /* --------------------------------------------------------
       ДАННЫЕ СЕРВИСА
       -------------------------------------------------------- */

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
        mutableStateOf("")
    }

    var queueCount by remember {
        mutableStateOf<Int?>(null)
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


            val serviceAlertActive =
                preferences.getBoolean(
                    QueueWatchService.KEY_ALERT_ACTIVE,
                    false
                )


            val serviceAlertMessage =
                preferences.getString(
                    QueueWatchService.KEY_ALERT_MESSAGE,
                    ""
                ) ?: ""


            val serviceAlertTitle =
                preferences.getString(
                    QueueWatchService.KEY_ALERT_TITLE,
                    "Оповещение QueueWatch"
                ) ?: "Оповещение QueueWatch"


            if (
                serviceAlertActive &&
                serviceAlertMessage.isNotBlank()
            ) {

                alertTitle =
                    serviceAlertTitle

                alertMessage =
                    serviceAlertMessage

                alertVisible = true
            }


            delay(1_000)
        }
    }


    /* --------------------------------------------------------
       ПОДТВЕРЖДЕНИЕ ОПОВЕЩЕНИЯ
       -------------------------------------------------------- */

    if (alertVisible) {

        AlertDialog(

            onDismissRequest = {
                /*
                 * Системное закрытие не считается
                 * подтверждением события.
                 */
            },

            title = {

                Text(
                    text = alertTitle
                )
            },

            text = {

                Text(
                    text = alertMessage
                )
            },

            confirmButton = {

                Button(

                    onClick = {

                        val acknowledgeIntent =
                            Intent(
                                context,
                                QueueWatchService::class.java
                            ).apply {

                                action =
                                    QueueWatchService.ACTION_ACKNOWLEDGE_ALERT
                            }


                        try {

                            context.startService(
                                acknowledgeIntent
                            )

                        } catch (_: Exception) {
                        }


                        alertVisible = false
                    }
                ) {

                    Text(
                        text = "ПОДТВЕРДИТЬ"
                    )
                }
            }
        )
    }


    /* ========================================================
       НОВЫЙ АВТОМОБИЛЬНЫЙ ЭКРАН
       ======================================================== */

    val screenBackground =
        Color(0xFF0B0F14)

    val panelColor =
        Color(0xFF151B22)

    val secondaryPanelColor =
        Color(0xFF1B232C)

    val mainTextColor =
        Color(0xFFF1F5F9)

    val secondaryTextColor =
        Color(0xFF9AA6B2)

    val greenColor =
        Color(0xFF3DDC84)

    val yellowColor =
        Color(0xFFFFC857)

    val redColor =
        Color(0xFFFF5A5F)

    val blueColor =
        Color(0xFF4DA3FF)


    val statusColor =
        when (vehicleState) {

            "IN_QUEUE" ->
                greenColor

            "CALLED" ->
                redColor

            "UNKNOWN" ->
                yellowColor

            else ->
                blueColor
        }


    val statusText =
        when (vehicleState) {

            "IN_QUEUE" ->
                "●  ЖИВАЯ ОЧЕРЕДЬ"

            "CALLED" ->
                "●  ВЫЗВАН НА КПП"

            "UNKNOWN" ->
                "●  СОСТОЯНИЕ НЕ ОПРЕДЕЛЕНО"

            else ->
                "●  ПОИСК АВТОМОБИЛЯ"
        }


    /*
     * Шкала показывает не прогноз времени,
     * а приближение текущей позиции к заданному порогу.
     *
     * Например:
     * текущая позиция 200, порог 100 -> 50 %
     * текущая позиция 125, порог 100 -> 80 %
     * текущая позиция 100 -> 100 %
     */

    val thresholdProgress =
        if (
            position != null &&
            position!! > 0 &&
            positionAlertThreshold > 0
        ) {

            (
                positionAlertThreshold.toFloat() /
                    position!!.toFloat()
            ).coerceIn(
                0f,
                1f
            )

        } else {

            0f
        }


    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            screenBackground
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 20.dp,
                    vertical = 22.dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            /* ------------------------------------------------
               ЗАГОЛОВОК
               ------------------------------------------------ */

            Text(
                text = "QueueWatch",

                color =
                    secondaryTextColor,

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.SemiBold
            )


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            /* ------------------------------------------------
               НОМЕР АВТОМОБИЛЯ
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(18.dp)
                    )
                    .background(
                        panelColor
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 16.dp
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            carNumber.uppercase(),

                        color =
                            mainTextColor,

                        fontSize =
                            36.sp,

                        fontWeight =
                            FontWeight.Bold,

                        textAlign =
                            TextAlign.Center
                    )


                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )


                    Text(
                        text =
                            checkpointName.uppercase(),

                        color =
                            secondaryTextColor,

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Medium
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            /* ------------------------------------------------
               ГЛАВНЫЙ БЛОК — ПОЗИЦИЯ
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(24.dp)
                    )
                    .background(
                        panelColor
                    )
                    .padding(
                        horizontal = 18.dp,
                        vertical = 22.dp
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            "ТЕКУЩАЯ ПОЗИЦИЯ",

                        color =
                            secondaryTextColor,

                        fontSize =
                            14.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            position?.toString()
                                ?: "—",

                        color =
                            statusColor,

                        fontSize =
                            82.sp,

                        fontWeight =
                            FontWeight.Bold,

                        textAlign =
                            TextAlign.Center
                    )


                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )


                    Text(
                        text =
                            statusText,

                        color =
                            statusColor,

                        fontSize =
                            17.sp,

                        fontWeight =
                            FontWeight.Bold,

                        textAlign =
                            TextAlign.Center
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            /* ------------------------------------------------
               ШКАЛА ПРИБЛИЖЕНИЯ К ПОРОГУ
               ------------------------------------------------ */

            if (
                positionAlertEnabled &&
                vehicleState == "IN_QUEUE"
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(18.dp)
                        )
                        .background(
                            secondaryPanelColor
                        )
                        .padding(16.dp)
                ) {

                    Column {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Text(
                                text =
                                    "До заданного порога",

                                color =
                                    secondaryTextColor,

                                fontSize =
                                    13.sp
                            )


                            Text(
                                text =
                                    "≤ $positionAlertThreshold",

                                color =
                                    mainTextColor,

                                fontSize =
                                    14.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }


                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )


                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(
                                    RoundedCornerShape(50)
                                )
                                .background(
                                    Color(0xFF303A45)
                                )
                        ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        thresholdProgress
                                    )
                                    .height(10.dp)
                                    .clip(
                                        RoundedCornerShape(50)
                                    )
                                    .background(
                                        greenColor
                                    )
                            )
                        }


                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )


                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Text(
                                text =
                                    "ДАЛЕКО",

                                color =
                                    secondaryTextColor,

                                fontSize =
                                    11.sp
                            )


                            Text(
                                text =
                                    if (
                                        position != null &&
                                        position!! <=
                                        positionAlertThreshold
                                    ) {

                                        "ПОРОГ ДОСТИГНУТ"

                                    } else {

                                        "БЛИЗКО"
                                    },

                                color =
                                    if (
                                        position != null &&
                                        position!! <=
                                        positionAlertThreshold
                                    ) {

                                        greenColor

                                    } else {

                                        secondaryTextColor
                                    },

                                fontSize =
                                    11.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }


                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )
            }


            /* ------------------------------------------------
               ИНФОРМАЦИОННЫЙ БЛОК
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(18.dp)
                    )
                    .background(
                        secondaryPanelColor
                    )
                    .padding(16.dp)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {


                    Column(
                        modifier =
                            Modifier.weight(1f),

                        horizontalAlignment =
                            Alignment.Start
                    ) {

                        Text(
                            text =
                                "МАШИН В ОЧЕРЕДИ",

                            color =
                                secondaryTextColor,

                            fontSize =
                                11.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )


                        Text(
                            text =
                                queueCount?.toString()
                                    ?: "—",

                            color =
                                mainTextColor,

                            fontSize =
                                24.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    Column(
                        modifier =
                            Modifier.weight(1f),

                        horizontalAlignment =
                            Alignment.End
                    ) {

                        Text(
                            text =
                                "ОБНОВЛЕНО",

                            color =
                                secondaryTextColor,

                            fontSize =
                                11.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )


                        Text(
                            text =
                                lastUpdate.ifEmpty {
                                    "—"
                                },

                            color =
                                mainTextColor,

                            fontSize =
                                17.sp,

                            fontWeight =
                                FontWeight.SemiBold,

                            textAlign =
                                TextAlign.End
                        )
                    }
                }
            }


            /*
             * Дополнительное сообщение показываем только
             * для неопределённого состояния или поиска,
             * чтобы рабочий экран живой очереди оставался
             * чистым и не перегруженным.
             */

            if (
                vehicleState != "IN_QUEUE" &&
                vehicleState != "CALLED" &&
                message.isNotBlank() &&
                message != "Подготовка..."
            ) {

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                Text(
                    text =
                        message,

                    color =
                        secondaryTextColor,

                    fontSize =
                        13.sp,

                    textAlign =
                        TextAlign.Center
                )
            }


            Spacer(
                modifier =
                    Modifier.weight(1f)
            )


            /* ------------------------------------------------
               НИЖНИЙ ИНДИКАТОР
               ------------------------------------------------ */

            Text(
                text =
                    "●  ОТСЛЕЖИВАНИЕ АКТИВНО",

                color =
                    if (
                        vehicleState == "UNKNOWN"
                    ) {

                        yellowColor

                    } else {

                        greenColor
                    },

                fontSize =
                    12.sp,

                fontWeight =
                    FontWeight.SemiBold
            )


            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )
        }
    }
}
