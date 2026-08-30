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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {
            QueueWatchApp()
        }
    }
}


/* ============================================================
   ОБЩИЕ ЦВЕТА
   ============================================================ */

private val ScreenBackground =
    Color(0xFF0B0F14)

private val PanelColor =
    Color(0xFF151B22)

private val SecondaryPanelColor =
    Color(0xFF1B232C)

private val MainTextColor =
    Color(0xFFF1F5F9)

private val SecondaryTextColor =
    Color(0xFF9AA6B2)

private val GreenColor =
    Color(0xFF3DDC84)

private val YellowColor =
    Color(0xFFFFC857)

private val RedColor =
    Color(0xFFFF5A5F)

private val BlueColor =
    Color(0xFF4DA3FF)

private val BorderColor =
    Color(0xFF35414D)


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
            modifier =
                Modifier.fillMaxSize(),

            color =
                ScreenBackground
        ) {

            if (
                !trackingStarted
            ) {

                SetupScreen(

                    carNumber =
                        carNumber,

                    onCarNumberChange = {
                        carNumber = it
                    },

                    checkpoint =
                        checkpoint,

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

                    carNumber =
                        carNumber,

                    checkpointName =
                        checkpoint,

                    positionAlertEnabled =
                        positionAlertEnabled,

                    positionAlertThreshold =
                        positionAlertThreshold
                            .toIntOrNull()
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

@OptIn(
    ExperimentalMaterial3Api::class
)
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


    val checkpoints =
        listOf(
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


    val scrollState =
        rememberScrollState()


    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            ScreenBackground
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    scrollState
                )
                .padding(
                    horizontal = 20.dp,
                    vertical = 22.dp
                )
                .imePadding()
                .navigationBarsPadding(),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {


            /* ------------------------------------------------
               ЗАГОЛОВОК
               ------------------------------------------------ */

            Text(
                text =
                    "QueueWatch",

                color =
                    MainTextColor,

                fontSize =
                    34.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        2.dp
                    )
            )


            Text(
                text =
                    "ЭЛЕКТРОННАЯ ОЧЕРЕДЬ",

                color =
                    BlueColor,

                fontSize =
                    13.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )


            Text(
                text =
                    "Мониторинг автомобиля на пункте пропуска",

                color =
                    SecondaryTextColor,

                fontSize =
                    13.sp,

                textAlign =
                    TextAlign.Center
            )


            Spacer(
                modifier =
                    Modifier.height(
                        22.dp
                    )
            )


            /* ------------------------------------------------
               АВТОМОБИЛЬ И ПУНКТ ПРОПУСКА
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            22.dp
                        )
                    )
                    .background(
                        PanelColor
                    )
                    .padding(
                        18.dp
                    )
            ) {

                Column {

                    Text(
                        text =
                            "АВТОМОБИЛЬ",

                        color =
                            SecondaryTextColor,

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    OutlinedTextField(
                        value =
                            carNumber,

                        onValueChange = {

                            onCarNumberChange(
                                it.uppercase()
                            )
                        },

                        label = {

                            Text(
                                text =
                                    "Регистрационный номер"
                            )
                        },

                        placeholder = {

                            Text(
                                text =
                                    "Например: 1234AB7"
                            )
                        },

                        singleLine =
                            true,

                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            OutlinedTextFieldDefaults.colors(

                                focusedTextColor =
                                    MainTextColor,

                                unfocusedTextColor =
                                    MainTextColor,

                                focusedBorderColor =
                                    BlueColor,

                                unfocusedBorderColor =
                                    BorderColor,

                                focusedLabelColor =
                                    BlueColor,

                                unfocusedLabelColor =
                                    SecondaryTextColor,

                                cursorColor =
                                    BlueColor,

                                focusedContainerColor =
                                    SecondaryPanelColor,

                                unfocusedContainerColor =
                                    SecondaryPanelColor,

                                focusedPlaceholderColor =
                                    SecondaryTextColor,

                                unfocusedPlaceholderColor =
                                    SecondaryTextColor
                            )
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                18.dp
                            )
                    )


                    Text(
                        text =
                            "ПУНКТ ПРОПУСКА",

                        color =
                            SecondaryTextColor,

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )


                    ExposedDropdownMenuBox(

                        expanded =
                            expanded,

                        onExpandedChange = {
                            expanded = !expanded
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        OutlinedTextField(

                            value =
                                checkpoint,

                            onValueChange = {},

                            readOnly =
                                true,

                            label = {

                                Text(
                                    text =
                                        "Выберите пункт"
                                )
                            },

                            placeholder = {

                                Text(
                                    text =
                                        "Пункт пропуска"
                                )
                            },

                            trailingIcon = {

                                ExposedDropdownMenuDefaults
                                    .TrailingIcon(
                                        expanded =
                                            expanded
                                    )
                            },

                            singleLine =
                                true,

                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),

                            colors =
                                OutlinedTextFieldDefaults.colors(

                                    focusedTextColor =
                                        MainTextColor,

                                    unfocusedTextColor =
                                        MainTextColor,

                                    focusedBorderColor =
                                        BlueColor,

                                    unfocusedBorderColor =
                                        BorderColor,

                                    focusedLabelColor =
                                        BlueColor,

                                    unfocusedLabelColor =
                                        SecondaryTextColor,

                                    focusedContainerColor =
                                        SecondaryPanelColor,

                                    unfocusedContainerColor =
                                        SecondaryPanelColor,

                                    focusedPlaceholderColor =
                                        SecondaryTextColor,

                                    unfocusedPlaceholderColor =
                                        SecondaryTextColor
                                )
                        )


                        ExposedDropdownMenu(

                            expanded =
                                expanded,

                            onDismissRequest = {
                                expanded = false
                            }
                        ) {

                            checkpoints.forEach { name ->

                                DropdownMenuItem(

                                    text = {

                                        Text(
                                            text =
                                                name
                                        )
                                    },

                                    onClick = {

                                        onCheckpointSelected(
                                            name
                                        )

                                        expanded =
                                            false
                                    }
                                )
                            }
                        }
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            /* ------------------------------------------------
               ОПОВЕЩЕНИЯ
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            22.dp
                        )
                    )
                    .background(
                        PanelColor
                    )
                    .padding(
                        18.dp
                    )
            ) {

                Column {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column {

                            Text(
                                text =
                                    "ОПОВЕЩЕНИЯ",

                                color =
                                    MainTextColor,

                                fontSize =
                                    17.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )


                            Text(
                                text =
                                    "Сигнал и голосовое предупреждение",

                                color =
                                    SecondaryTextColor,

                                fontSize =
                                    12.sp
                            )
                        }


                        Text(
                            text =
                                "●",

                            color =
                                GreenColor,

                            fontSize =
                                18.sp
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.height(
                                18.dp
                            )
                    )


                    /* ----------------------------------------
                       ОПОВЕЩЕНИЕ ПО ПОЗИЦИИ
                       ---------------------------------------- */

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    16.dp
                                )
                            )
                            .background(
                                SecondaryPanelColor
                            )
                            .padding(
                                14.dp
                            )
                    ) {

                        Column {

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.SpaceBetween,

                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Column(
                                    modifier =
                                        Modifier.padding(
                                            end = 8.dp
                                        )
                                ) {

                                    Text(
                                        text =
                                            "ПО ПОЗИЦИИ",

                                        color =
                                            MainTextColor,

                                        fontSize =
                                            15.sp,

                                        fontWeight =
                                            FontWeight.Bold
                                    )


                                    Text(
                                        text =
                                            if (
                                                positionAlertEnabled
                                            ) {

                                                "Предупреждение включено"

                                            } else {

                                                "Предупреждение выключено"
                                            },

                                        color =
                                            if (
                                                positionAlertEnabled
                                            ) {

                                                GreenColor

                                            } else {

                                                SecondaryTextColor
                                            },

                                        fontSize =
                                            12.sp
                                    )
                                }


                                Button(

                                    onClick = {

                                        onPositionAlertEnabledChange(
                                            !positionAlertEnabled
                                        )
                                    },

                                    colors =
                                        ButtonDefaults.buttonColors(

                                            containerColor =
                                                if (
                                                    positionAlertEnabled
                                                ) {

                                                    GreenColor

                                                } else {

                                                    Color(
                                                        0xFF303A45
                                                    )
                                                },

                                            contentColor =
                                                if (
                                                    positionAlertEnabled
                                                ) {

                                                    Color.Black

                                                } else {

                                                    MainTextColor
                                                }
                                        )
                                ) {

                                    Text(
                                        text =
                                            if (
                                                positionAlertEnabled
                                            ) {

                                                "ВКЛ"

                                            } else {

                                                "ВЫКЛ"
                                            },

                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                }
                            }


                            if (
                                positionAlertEnabled
                            ) {

                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            12.dp
                                        )
                                )


                                OutlinedTextField(

                                    value =
                                        positionAlertThreshold,

                                    onValueChange = {

                                        onPositionAlertThresholdChange(

                                            it.filter { char ->
                                                char.isDigit()
                                            }
                                        )
                                    },

                                    label = {

                                        Text(
                                            text =
                                                "Предупредить при позиции"
                                        )
                                    },

                                    supportingText = {

                                        Text(
                                            text =
                                                "Сигнал сработает при этой позиции или меньше"
                                        )
                                    },

                                    placeholder = {

                                        Text(
                                            text =
                                                "100"
                                        )
                                    },

                                    singleLine =
                                        true,

                                    modifier =
                                        Modifier.fillMaxWidth(),

                                    colors =
                                        OutlinedTextFieldDefaults.colors(

                                            focusedTextColor =
                                                MainTextColor,

                                            unfocusedTextColor =
                                                MainTextColor,

                                            focusedBorderColor =
                                                GreenColor,

                                            unfocusedBorderColor =
                                                BorderColor,

                                            focusedLabelColor =
                                                GreenColor,

                                            unfocusedLabelColor =
                                                SecondaryTextColor,

                                            cursorColor =
                                                GreenColor,

                                            focusedContainerColor =
                                                PanelColor,

                                            unfocusedContainerColor =
                                                PanelColor,

                                            focusedSupportingTextColor =
                                                SecondaryTextColor,

                                            unfocusedSupportingTextColor =
                                                SecondaryTextColor
                                        )
                                )
                            }
                        }
                    }


                    Spacer(
                        modifier =
                            Modifier.height(
                                12.dp
                            )
                    )


                    /* ----------------------------------------
                       ОПОВЕЩЕНИЕ О ВЫЗОВЕ
                       ---------------------------------------- */

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    16.dp
                                )
                            )
                            .background(
                                SecondaryPanelColor
                            )
                            .padding(
                                14.dp
                            )
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Column(
                                modifier =
                                    Modifier.padding(
                                        end = 8.dp
                                    )
                            ) {

                                Text(
                                    text =
                                        "ВЫЗОВ НА КПП",

                                    color =
                                        MainTextColor,

                                    fontSize =
                                        15.sp,

                                    fontWeight =
                                        FontWeight.Bold
                                )


                                Text(
                                    text =
                                        if (
                                            calledAlertEnabled
                                        ) {

                                            "Обязательное предупреждение включено"

                                        } else {

                                            "Предупреждение выключено"
                                        },

                                    color =
                                        if (
                                            calledAlertEnabled
                                        ) {

                                            GreenColor

                                        } else {

                                            SecondaryTextColor
                                        },

                                    fontSize =
                                        12.sp
                                )
                            }


                            Button(

                                onClick = {

                                    onCalledAlertEnabledChange(
                                        !calledAlertEnabled
                                    )
                                },

                                colors =
                                    ButtonDefaults.buttonColors(

                                        containerColor =
                                            if (
                                                calledAlertEnabled
                                            ) {

                                                GreenColor

                                            } else {

                                                Color(
                                                    0xFF303A45
                                                )
                                            },

                                        contentColor =
                                            if (
                                                calledAlertEnabled
                                            ) {

                                                Color.Black

                                            } else {

                                                MainTextColor
                                            }
                                    )
                            ) {

                                Text(
                                    text =
                                        if (
                                            calledAlertEnabled
                                        ) {

                                            "ВКЛ"

                                        } else {

                                            "ВЫКЛ"
                                        },

                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            /* ------------------------------------------------
               КНОПКА ЗАПУСКА
               ------------------------------------------------ */

            Button(

                onClick =
                    onStartTracking,

                enabled =
                    canStart,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        58.dp
                    ),

                shape =
                    RoundedCornerShape(
                        18.dp
                    ),

                colors =
                    ButtonDefaults.buttonColors(

                        containerColor =
                            BlueColor,

                        contentColor =
                            Color.White,

                        disabledContainerColor =
                            Color(
                                0xFF26313B
                            ),

                        disabledContentColor =
                            Color(
                                0xFF687581
                            )
                    )
            ) {

                Text(
                    text =
                        "НАЧАТЬ ОТСЛЕЖИВАНИЕ",

                    fontSize =
                        15.sp,

                    fontWeight =
                        FontWeight.Bold
                )
            }


            if (
                !canStart
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )


                Text(
                    text =
                        "Введите номер автомобиля и выберите пункт пропуска",

                    color =
                        SecondaryTextColor,

                    fontSize =
                        12.sp,

                    textAlign =
                        TextAlign.Center
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )


            Text(
                text =
                    "●  ГОТОВО К МОНИТОРИНГУ",

                color =
                    if (
                        canStart
                    ) {

                        GreenColor

                    } else {

                        SecondaryTextColor
                    },

                fontSize =
                    11.sp,

                fontWeight =
                    FontWeight.SemiBold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
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


    var alertVisible by remember {
        mutableStateOf(false)
    }


    var alertTitle by remember {
        mutableStateOf(
            "Оповещение QueueWatch"
        )
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
                    QueueWatchService
                        .EXTRA_CAR_NUMBER,
                    carNumber
                )

                putExtra(
                    QueueWatchService
                        .EXTRA_CHECKPOINT,
                    checkpointName
                )

                putExtra(
                    QueueWatchService
                        .EXTRA_POSITION_ALERT_ENABLED,
                    positionAlertEnabled
                )

                putExtra(
                    QueueWatchService
                        .EXTRA_POSITION_THRESHOLD,
                    positionAlertThreshold
                )

                /*
                 * Прогнозные уведомления отключены.
                 */

                putExtra(
                    QueueWatchService
                        .EXTRA_FORECAST_ALERT_ENABLED,
                    false
                )

                putExtra(
                    QueueWatchService
                        .EXTRA_FORECAST_MINUTES,
                    0
                )

                putExtra(
                    QueueWatchService
                        .EXTRA_CALLED_ALERT_ENABLED,
                    calledAlertEnabled
                )
            }


        androidx.core.content.ContextCompat
            .startForegroundService(
                context,
                intent
            )
    }


    /* --------------------------------------------------------
       ЧТЕНИЕ ДАННЫХ СЕРВИСА
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
        mutableStateOf(
            "Подготовка..."
        )
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

                alertVisible =
                    true
            }


            delay(
                1_000
            )
        }
    }


    /* --------------------------------------------------------
       ПОДТВЕРЖДЕНИЕ ОПОВЕЩЕНИЯ
       -------------------------------------------------------- */

    if (
        alertVisible
    ) {

        AlertDialog(

            onDismissRequest = {},

            title = {

                Text(
                    text =
                        alertTitle
                )
            },

            text = {

                Text(
                    text =
                        alertMessage
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
                                    QueueWatchService
                                        .ACTION_ACKNOWLEDGE_ALERT
                            }


                        try {

                            context.startService(
                                acknowledgeIntent
                            )

                        } catch (_: Exception) {
                        }


                        alertVisible =
                            false
                    }
                ) {

                    Text(
                        text =
                            "ПОДТВЕРДИТЬ"
                    )
                }
            }
        )
    }


    /* ========================================================
       СТАТУС
       ======================================================== */

    val statusColor =
        when (
            vehicleState
        ) {

            "IN_QUEUE" ->
                GreenColor

            "CALLED" ->
                RedColor

            "UNKNOWN" ->
                YellowColor

            else ->
                BlueColor
        }


    val statusText =
        when (
            vehicleState
        ) {

            "IN_QUEUE" ->
                "●  ЖИВАЯ ОЧЕРЕДЬ"

            "CALLED" ->
                "●  ВЫЗВАН НА КПП"

            "UNKNOWN" ->
                "●  СОСТОЯНИЕ НЕ ОПРЕДЕЛЕНО"

            else ->
                "●  ПОИСК АВТОМОБИЛЯ"
        }


    val thresholdProgress =
        if (
            position != null &&
            position!! > 0 &&
            positionAlertThreshold > 0
        ) {

            (
                positionAlertThreshold
                    .toFloat() /
                    position!!
                        .toFloat()
            ).coerceIn(
                0f,
                1f
            )

        } else {

            0f
        }


    /* ========================================================
       ЭКРАН
       ======================================================== */

    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            ScreenBackground
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 20.dp,
                    vertical = 20.dp
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Top
        ) {


            Text(
                text =
                    "QueueWatch",

                color =
                    SecondaryTextColor,

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.SemiBold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            /* ------------------------------------------------
               НОМЕР И ПУНКТ
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            18.dp
                        )
                    )
                    .background(
                        PanelColor
                    )
                    .padding(
                        horizontal = 16.dp,
                        vertical = 14.dp
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
                            MainTextColor,

                        fontSize =
                            36.sp,

                        fontWeight =
                            FontWeight.Bold,

                        textAlign =
                            TextAlign.Center
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                4.dp
                            )
                    )


                    Text(
                        text =
                            checkpointName.uppercase(),

                        color =
                            SecondaryTextColor,

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.Medium,

                        textAlign =
                            TextAlign.Center
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )


            /* ------------------------------------------------
               ПОЗИЦИЯ
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            24.dp
                        )
                    )
                    .background(
                        PanelColor
                    )
                    .padding(
                        horizontal = 18.dp,
                        vertical = 18.dp
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
                            SecondaryTextColor,

                        fontSize =
                            14.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                2.dp
                            )
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
                    Modifier.height(
                        14.dp
                    )
            )


            /* ------------------------------------------------
               ШКАЛА ПОРОГА
               ------------------------------------------------ */

            if (
                positionAlertEnabled &&
                vehicleState ==
                "IN_QUEUE"
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                18.dp
                            )
                        )
                        .background(
                            SecondaryPanelColor
                        )
                        .padding(
                            16.dp
                        )
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
                                    SecondaryTextColor,

                                fontSize =
                                    13.sp
                            )


                            Text(
                                text =
                                    "≤ $positionAlertThreshold",

                                color =
                                    MainTextColor,

                                fontSize =
                                    14.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }


                        Spacer(
                            modifier =
                                Modifier.height(
                                    10.dp
                                )
                        )


                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    10.dp
                                )
                                .clip(
                                    RoundedCornerShape(
                                        50.dp
                                    )
                                )
                                .background(
                                    Color(
                                        0xFF303A45
                                    )
                                )
                        ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        thresholdProgress
                                    )
                                    .height(
                                        10.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            50.dp
                                        )
                                    )
                                    .background(
                                        GreenColor
                                    )
                            )
                        }


                        Spacer(
                            modifier =
                                Modifier.height(
                                    7.dp
                                )
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
                                    SecondaryTextColor,

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

                                        GreenColor

                                    } else {

                                        SecondaryTextColor
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
                        Modifier.height(
                            14.dp
                        )
                )
            }


            /* ------------------------------------------------
               ИНФОРМАЦИЯ
               ------------------------------------------------ */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            18.dp
                        )
                    )
                    .background(
                        SecondaryPanelColor
                    )
                    .padding(
                        16.dp
                    )
            ) {

                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                "МАШИН В ОЧЕРЕДИ",

                            color =
                                SecondaryTextColor,

                            fontSize =
                                12.sp
                        )


                        Text(
                            text =
                                queueCount
                                    ?.toString()
                                    ?: "—",

                            color =
                                MainTextColor,

                            fontSize =
                                23.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.height(
                                10.dp
                            )
                    )


                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text =
                                "ПОСЛЕДНЕЕ ОБНОВЛЕНИЕ",

                            color =
                                SecondaryTextColor,

                            fontSize =
                                12.sp
                        )


                        Text(
                            text =
                                lastUpdate.ifEmpty {
                                    "—"
                                },

                            color =
                                MainTextColor,

                            fontSize =
                                16.sp,

                            fontWeight =
                                FontWeight.SemiBold,

                            textAlign =
                                TextAlign.End
                        )
                    }
                }
            }


            if (
                vehicleState !=
                "IN_QUEUE" &&
                vehicleState !=
                "CALLED" &&
                message.isNotBlank() &&
                message !=
                "Подготовка..."
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )


                Text(
                    text =
                        message,

                    color =
                        SecondaryTextColor,

                    fontSize =
                        13.sp,

                    textAlign =
                        TextAlign.Center
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            Text(
                text =
                    "●  ОТСЛЕЖИВАНИЕ АКТИВНО",

                color =
                    if (
                        vehicleState ==
                        "UNKNOWN"
                    ) {

                        YellowColor

                    } else {

                        GreenColor
                    },

                fontSize =
                    12.sp,

                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}
