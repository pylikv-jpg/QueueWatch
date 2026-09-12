package com.pylikv.queuewatch

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Launcher that restores an active monitoring session after process death.
 *
 * If there is no durable session, the normal setup MainActivity is opened.
 * If monitoring is active, the user goes straight to the tracking screen and
 * the service is explicitly nudged to restore from SharedPreferences.
 */
class SessionLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            QueueWatchService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)) {
            openSetupAndFinish()
            return
        }

        // User opening the app is a valid foreground-service start trigger.
        // No extras are needed: QueueWatchService restores the durable session.
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, QueueWatchService::class.java)
            )
        } catch (_: Exception) {
        }

        setContent {
            RestoredTrackingScreen()
        }
    }

    private fun openSetupAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

private val RestoredBackground = Color(0xFF0B0F14)
private val RestoredPanel = Color(0xFF151B22)
private val RestoredPanel2 = Color(0xFF1B232C)
private val RestoredText = Color(0xFFF1F5F9)
private val RestoredSecondary = Color(0xFF9AA6B2)
private val RestoredGreen = Color(0xFF3DDC84)
private val RestoredYellow = Color(0xFFFFC857)
private val RestoredRed = Color(0xFFFF5A5F)
private val RestoredBlue = Color(0xFF4DA3FF)

@Composable
private fun RestoredTrackingScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember {
        context.getSharedPreferences(
            QueueWatchService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    var active by remember {
        mutableStateOf(prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false))
    }
    var carNumber by remember {
        mutableStateOf(prefs.getString(QueueWatchService.KEY_CAR_NUMBER, "").orEmpty())
    }
    var checkpoint by remember {
        mutableStateOf(prefs.getString(QueueWatchService.KEY_CHECKPOINT, "").orEmpty())
    }
    var position by remember { mutableStateOf<Int?>(null) }
    var queueCount by remember { mutableStateOf<Int?>(null) }
    var state by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Восстановление мониторинга…") }
    var lastUpdate by remember { mutableStateOf("") }
    var alertVisible by remember { mutableStateOf(false) }
    var alertTitle by remember { mutableStateOf("Оповещение QueueWatch") }
    var alertMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            active = prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)
            carNumber = prefs.getString(QueueWatchService.KEY_CAR_NUMBER, "").orEmpty()
            checkpoint = prefs.getString(QueueWatchService.KEY_CHECKPOINT, "").orEmpty()

            position = if (prefs.contains(QueueWatchService.KEY_POSITION)) {
                prefs.getInt(QueueWatchService.KEY_POSITION, 0).takeIf { it > 0 }
            } else {
                null
            }

            queueCount = if (prefs.contains(QueueWatchService.KEY_QUEUE_COUNT)) {
                prefs.getInt(QueueWatchService.KEY_QUEUE_COUNT, 0)
            } else {
                null
            }

            state = prefs.getString(QueueWatchService.KEY_STATE, "").orEmpty()
            message = prefs.getString(
                QueueWatchService.KEY_MESSAGE,
                "Восстановление мониторинга…"
            ).orEmpty()
            lastUpdate = prefs.getString(QueueWatchService.KEY_LAST_UPDATE, "").orEmpty()

            val serviceAlertActive = prefs.getBoolean(
                QueueWatchService.KEY_ALERT_ACTIVE,
                false
            )
            if (serviceAlertActive) {
                alertTitle = prefs.getString(
                    QueueWatchService.KEY_ALERT_TITLE,
                    "Оповещение QueueWatch"
                ).orEmpty()
                alertMessage = prefs.getString(
                    QueueWatchService.KEY_ALERT_MESSAGE,
                    ""
                ).orEmpty()
                alertVisible = alertMessage.isNotBlank()
            }

            if (!active) {
                context.startActivity(Intent(context, MainActivity::class.java))
                activity?.finish()
                break
            }

            delay(1_000)
        }
    }

    if (alertVisible) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(alertTitle) },
            text = { Text(alertMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            context.startService(
                                Intent(context, QueueWatchService::class.java).apply {
                                    action = QueueWatchService.ACTION_ACKNOWLEDGE_ALERT
                                }
                            )
                        } catch (_: Exception) {
                        }
                        alertVisible = false
                    }
                ) {
                    Text("ПОДТВЕРДИТЬ")
                }
            }
        )
    }

    val statusColor = when (state) {
        "IN_QUEUE" -> RestoredGreen
        "CALLED" -> RestoredRed
        "UNKNOWN" -> RestoredYellow
        else -> RestoredBlue
    }

    val statusText = when (state) {
        "IN_QUEUE" -> "●  ЖИВАЯ ОЧЕРЕДЬ"
        "CALLED" -> "●  ВЫЗВАН НА КПП"
        "UNKNOWN" -> "●  СОСТОЯНИЕ НЕ ОПРЕДЕЛЕНО"
        else -> "●  ВОССТАНОВЛЕНИЕ / ПОИСК"
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = RestoredBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "QueueWatch",
                    color = RestoredSecondary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RestoredPanel)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = carNumber.uppercase(),
                            color = RestoredText,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = checkpoint.uppercase(),
                            color = RestoredSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(RestoredPanel)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ТЕКУЩАЯ ПОЗИЦИЯ",
                            color = RestoredSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = position?.toString() ?: "—",
                            color = statusColor,
                            fontSize = 82.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RestoredPanel2)
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "МАШИН В ОЧЕРЕДИ",
                                color = RestoredSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                queueCount?.toString() ?: "—",
                                color = RestoredText,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "ПОСЛЕДНЕЕ ОБНОВЛЕНИЕ",
                                color = RestoredSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                lastUpdate.ifBlank { "—" },
                                color = RestoredText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (message.isNotBlank() && state != "IN_QUEUE" && state != "CALLED") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        color = RestoredSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "●  ОТСЛЕЖИВАНИЕ АКТИВНО",
                    color = if (state == "UNKNOWN") RestoredYellow else RestoredGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        try {
                            context.startService(
                                Intent(context, QueueWatchService::class.java).apply {
                                    action = QueueWatchService.ACTION_STOP_TRACKING
                                }
                            )
                        } catch (_: Exception) {
                        }

                        // Route immediately back to setup. The service also
                        // clears the remaining durable session state.
                        prefs.edit()
                            .putBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)
                            .commit()
                        active = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF303A45),
                        contentColor = RestoredText
                    )
                ) {
                    Text(
                        "ОСТАНОВИТЬ ОТСЛЕЖИВАНИЕ",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
