package com.pylikv.queuewatch

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pylikv.queuewatch.forecast.SharedPreferencesForecastStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay


class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

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

private const val OFFICIAL_SOURCE_URL =
    "https://belarusborder.by/"


/* ============================================================
   ГЛАВНОЕ ПРИЛОЖЕНИЕ
   ============================================================ */

@Composable
fun QueueWatchApp() {
    ParticipationWelcome()
    val appContext = LocalContext.current

    var carNumber by rememberSaveable {
        mutableStateOf("")
    }

    var checkpoint by rememberSaveable {
        mutableStateOf("")
    }

    var trackingStarted by rememberSaveable {
        mutableStateOf(false)
    }

    var hasTrackingSession by rememberSaveable {
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
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Tracking remains usable if the driver declines notifications.
        hasTrackingSession = true
        trackingStarted = true
    }



    BackHandler(
        enabled = trackingStarted
    ) {
        trackingStarted = false
    }


    MaterialTheme {

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    trackingStarted,
                    hasTrackingSession
                ) {
                    var horizontalDrag = 0f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDrag = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            horizontalDrag += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            when {
                                horizontalDrag > 100f && trackingStarted ->
                                    trackingStarted = false

                                horizontalDrag < -100f &&
                                    !trackingStarted &&
                                    hasTrackingSession ->
                                    trackingStarted = true
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = {
                            horizontalDrag = 0f
                        }
                    )
                },

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
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                                appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            hasTrackingSession = true
                            trackingStarted = true
                        }
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
            QueueSettingsButton()


            Spacer(
                modifier =
                    Modifier.height(
                        2.dp
                    )
            )


            Text(
                text =
                    L.text(R.string.queue_title),

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
                    L.text(R.string.queue_subtitle),

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
                            L.text(R.string.vehicle),

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
                                it.uppercase(java.util.Locale.ROOT)
                            )
                        },

                        label = {

                            Text(
                                text =
                                    L.text(R.string.registration_number)
                            )
                        },

                        placeholder = {

                            Text(
                                text =
                                    L.text(R.string.registration_example)
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
                            L.text(R.string.checkpoint_heading),

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
                                L.checkpoint(checkpoint),

                            onValueChange = {},

                            readOnly =
                                true,

                            label = {

                                Text(
                                    text =
                                        L.text(R.string.choose_checkpoint)
                                )
                            },

                            placeholder = {

                                Text(
                                    text =
                                        L.text(R.string.checkpoint)
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
                                                L.checkpoint(name)
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
                                    L.text(R.string.alerts),

                                color =
                                    MainTextColor,

                                fontSize =
                                    17.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )


                            Text(
                                text =
                                    L.text(R.string.sound_and_voice),

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
                                            L.text(R.string.by_position),

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

                                                L.text(R.string.alert_enabled)

                                            } else {

                                                L.text(R.string.alert_disabled)
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

                                                L.text(R.string.on)

                                            } else {

                                                L.text(R.string.off)
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
                                                L.text(R.string.position_threshold)
                                        )
                                    },

                                    supportingText = {

                                        Text(
                                            text =
                                                L.text(R.string.position_threshold_hint)
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
                                        L.text(R.string.checkpoint_call_heading),

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

                                            L.text(R.string.required_alert_enabled)

                                        } else {

                                            L.text(R.string.alert_disabled)
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

                                            L.text(R.string.on)

                                        } else {

                                            L.text(R.string.off)
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


            RegistrationButton()
            Spacer(Modifier.height(10.dp))

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
                        L.text(R.string.start_tracking),

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
                        L.text(R.string.setup_hint),

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
                    L.text(R.string.ready),

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


            OfficialSourceNotice()


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )
        }
    }
}


@Composable
private fun OfficialSourceNotice() {

    val context =
        LocalContext.current


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

            Text(
                text =
                    L.text(R.string.source_heading),

                color =
                    MainTextColor,

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
                    L.text(R.string.official_source),

                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                OFFICIAL_SOURCE_URL
                            )
                        )
                    )
                },

                color =
                    BlueColor,

                fontSize =
                    13.sp,

                fontWeight =
                    FontWeight.SemiBold
            )


            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )


            Text(
                text =
                    L.text(R.string.independent_notice),

                color =
                    SecondaryTextColor,

                fontSize =
                    12.sp
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
            L.text(R.string.app_alert)
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
            L.text(R.string.preparing)
        )
    }


    var lastUpdate by remember {
        mutableStateOf("")
    }


    var trackingEvidence by remember { mutableStateOf<TrackingEvidence?>(null) }

    var trackingStartPosition by remember {
        mutableStateOf<Int?>(null)
    }

    var trackingStartTime by remember {
        mutableStateOf<Long?>(null)
    }

    var movementElapsedMs by remember {
        mutableStateOf(0L)
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
                    L.text(R.string.preparing)
                ) ?: L.text(R.string.preparing)


            message = L.stored(message)
            lastUpdate =
                preferences.getString(
                    QueueWatchService.KEY_LAST_UPDATE,
                    ""
                ) ?: ""


            val movementSessionId =
                carNumber.trim().uppercase(java.util.Locale.ROOT) + "|" + checkpointName.trim().uppercase(java.util.Locale.ROOT)
            // movement_session_id is now owned by the service evidence store.
            trackingEvidence = TrackingEvidenceStore(SharedPreferencesForecastStorage(preferences)).read(movementSessionId)
            trackingStartPosition = trackingEvidence?.firstPosition
            trackingStartTime = trackingEvidence?.firstAt
            movementElapsedMs = trackingEvidence?.elapsedMillis ?: 0L
            vehicleState = displayedTrackingState(vehicleState, trackingEvidence, System.currentTimeMillis())
            if (vehicleState != "IN_QUEUE") position = null
            if (vehicleState == "SOURCE_ERROR" || vehicleState == "STALE") queueCount = null

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
                    L.text(R.string.app_alert)
                ) ?: L.text(R.string.app_alert)


            if (
                serviceAlertActive &&
                serviceAlertMessage.isNotBlank()
            ) {

                alertTitle =
                    L.stored(serviceAlertTitle)

                alertMessage =
                    L.stored(serviceAlertMessage)

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
                            L.text(R.string.acknowledge)
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

            "UNKNOWN", "MISSING", "SOURCE_ERROR", "STALE" ->
                YellowColor

            else ->
                BlueColor
        }


    val positionsPassed = trackingEvidence?.positionsPassed

    val positionsToCall =
        when {
            vehicleState == "CALLED" -> 0
            position != null && position!! > 0 -> position
            else -> null
        }

    val elapsedTotalMinutes = movementElapsedMs / 60_000L
    val elapsedHours = elapsedTotalMinutes / 60L
    val elapsedMinutes = elapsedTotalMinutes % 60L
    val elapsedText =
        if (elapsedHours > 0L) {
            L.text(R.string.duration_hours, elapsedHours, elapsedMinutes)
        } else {
            L.text(R.string.duration_minutes, elapsedMinutes)
        }

    val statusText =
        when (
            vehicleState
        ) {

            "IN_QUEUE" ->
                L.text(R.string.status_in_queue)

            "CALLED" ->
                L.text(R.string.status_called)

            "MISSING" -> L.text(R.string.status_missing)
            "SOURCE_ERROR" -> L.text(R.string.status_source_error)
            "STALE" -> L.text(R.string.status_stale)
            "UNKNOWN" -> L.text(R.string.status_unknown)

            else ->
                L.text(R.string.status_searching)
        }


    val callProgress =
        when {

            vehicleState == "CALLED" ->
                1f

            position != null &&
                position!! > 0 &&
                queueCount != null &&
                queueCount!! > 0 ->
                (
                    (queueCount!! - position!! + 1)
                        .toFloat() /
                        queueCount!!
                            .toFloat()
                ).coerceIn(
                    0f,
                    1f
                )

            else ->
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
            QueueSettingsButton()


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
                            carNumber.uppercase(java.util.Locale.ROOT),

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
                            L.checkpoint(checkpointName).uppercase(L.locale()),

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
                            L.text(R.string.current_position),

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
               ШКАЛА ДО ВЫЗОВА
               ------------------------------------------------ */

            if (vehicleState != "IN_QUEUE" && trackingEvidence != null) {
                val last = trackingEvidence!!
                val lastTime = SimpleDateFormat("d MMM yyyy, HH:mm", L.locale()).apply {
                    timeZone = TimeZone.getTimeZone("Europe/Minsk")
                }.format(Date(last.lastAt))
                Text(L.text(R.string.last_position, last.lastPosition, lastTime), color = SecondaryTextColor, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
            }

            if (
                vehicleState == "IN_QUEUE" ||
                vehicleState == "CALLED"
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
                                    L.text(R.string.until_call),

                                color =
                                    SecondaryTextColor,

                                fontSize =
                                    13.sp
                            )


                            Text(
                                text =
                                    if (vehicleState == "CALLED") L.text(R.string.called) else L.text(R.string.first_position),

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
                                    20.dp
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
                                        callProgress
                                    )
                                    .height(
                                        20.dp
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
                                    L.text(R.string.queue_start),

                                color =
                                    SecondaryTextColor,

                                fontSize =
                                    11.sp
                            )


                            Text(
                                text =
                                    if (
                                        vehicleState == "CALLED"
                                    ) {

                                        L.text(R.string.called)

                                    } else if (
                                        position != null &&
                                        position!! <= 5
                                    ) {

                                        L.text(R.string.call_soon)

                                    } else {

                                        L.text(R.string.closer_to_call)
                                    },

                                color =
                                    if (
                                        vehicleState == "CALLED" ||
                                        (position != null && position!! <= 5)
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

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text =
                                if (positionsPassed != null) {
                                    L.text(R.string.movement, elapsedText, positionsPassed!!)
                                } else {
                                    L.text(R.string.movement_unavailable)
                                },
                            color = MainTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                L.text(R.string.remaining_positions, positionsToCall?.toString() ?: "—"),
                            color =
                                if (vehicleState == "CALLED") GreenColor else SecondaryTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
                                L.text(R.string.queue_count),

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
                                L.text(R.string.source_check_time),

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
                L.text(R.string.preparing)
            ) {

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )


                Text(
                    text =
                        if (vehicleState == "STALE") L.text(R.string.freshness_hint) else message,

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


            TrackingHealthStatus()


            Spacer(
                modifier =
                    Modifier.height(
                        18.dp
                    )
            )


            OfficialSourceNotice()


            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )
        }
    }
}
