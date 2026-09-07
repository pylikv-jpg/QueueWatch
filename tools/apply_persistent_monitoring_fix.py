from pathlib import Path

SERVICE = Path("app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt")
MAIN = Path("app/src/main/java/com/pylikv/queuewatch/MainActivity.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"persistent monitoring: anchor not found: {label}")
    return text.replace(old, new, 1)


# -----------------------------------------------------------------------------
# QueueWatchService: persist a user-started session and recover it after Android
# recreates the service with a null Intent. Only ACTION_STOP_MONITORING clears the
# persistent active flag.
# -----------------------------------------------------------------------------
service = SERVICE.read_text(encoding="utf-8")

anchor = '''        const val ACTION_ACKNOWLEDGE_ALERT =
            "com.pylikv.queuewatch.ACKNOWLEDGE_ALERT"
'''
addition = '''        const val ACTION_ACKNOWLEDGE_ALERT =
            "com.pylikv.queuewatch.ACKNOWLEDGE_ALERT"

        const val ACTION_STOP_MONITORING =
            "com.pylikv.queuewatch.STOP_MONITORING"

        const val ACTION_RESTORE_MONITORING =
            "com.pylikv.queuewatch.RESTORE_MONITORING"

        const val KEY_TRACKING_ACTIVE =
            "tracking_active"

        const val KEY_TRACKING_CAR_NUMBER =
            "tracking_car_number"

        const val KEY_TRACKING_CHECKPOINT =
            "tracking_checkpoint"

        const val KEY_TRACKING_POSITION_ALERT_ENABLED =
            "tracking_position_alert_enabled"

        const val KEY_TRACKING_POSITION_THRESHOLD =
            "tracking_position_threshold"

        const val KEY_TRACKING_CALLED_ALERT_ENABLED =
            "tracking_called_alert_enabled"
'''
service = replace_once(
    service,
    anchor,
    addition,
    "service persistent constants"
)

start_marker = '''    override fun onStartCommand(
'''
end_marker = '''

    /* ========================================================
       ОСНОВНОЙ МОНИТОРИНГ
       ======================================================== */
'''
start = service.find(start_marker)
end = service.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit("persistent monitoring: onStartCommand section not found")

new_start_section = '''    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action ==
            ACTION_ACKNOWLEDGE_ALERT
        ) {
            acknowledgeAlert()

            return if (
                preferences.getBoolean(
                    KEY_TRACKING_ACTIVE,
                    false
                )
            ) {
                START_STICKY
            } else {
                START_NOT_STICKY
            }
        }

        if (
            intent?.action ==
            ACTION_STOP_MONITORING
        ) {
            stopMonitoringByUser()
            return START_NOT_STICKY
        }

        val intentCarNumber =
            intent?.getStringExtra(
                EXTRA_CAR_NUMBER
            )?.trim()

        val intentCheckpoint =
            intent?.getStringExtra(
                EXTRA_CHECKPOINT
            )?.trim()

        val explicitUserStart =
            !intentCarNumber.isNullOrBlank() &&
                !intentCheckpoint.isNullOrBlank()

        if (
            explicitUserStart
        ) {
            preferences.edit()
                .putBoolean(
                    KEY_TRACKING_ACTIVE,
                    true
                )
                .putString(
                    KEY_TRACKING_CAR_NUMBER,
                    intentCarNumber
                )
                .putString(
                    KEY_TRACKING_CHECKPOINT,
                    intentCheckpoint
                )
                .putBoolean(
                    KEY_TRACKING_POSITION_ALERT_ENABLED,
                    intent!!.getBooleanExtra(
                        EXTRA_POSITION_ALERT_ENABLED,
                        true
                    )
                )
                .putInt(
                    KEY_TRACKING_POSITION_THRESHOLD,
                    intent.getIntExtra(
                        EXTRA_POSITION_THRESHOLD,
                        100
                    )
                )
                .putBoolean(
                    KEY_TRACKING_CALLED_ALERT_ENABLED,
                    intent.getBooleanExtra(
                        EXTRA_CALLED_ALERT_ENABLED,
                        true
                    )
                )
                .apply()
        }

        if (
            !preferences.getBoolean(
                KEY_TRACKING_ACTIVE,
                false
            )
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val carNumber =
            if (
                explicitUserStart
            ) {
                intentCarNumber!!
            } else {
                preferences.getString(
                    KEY_TRACKING_CAR_NUMBER,
                    ""
                ) ?: ""
            }

        val checkpoint =
            if (
                explicitUserStart
            ) {
                intentCheckpoint!!
            } else {
                preferences.getString(
                    KEY_TRACKING_CHECKPOINT,
                    ""
                ) ?: ""
            }

        if (
            carNumber.isBlank() ||
            checkpoint.isBlank()
        ) {
            preferences.edit()
                .putBoolean(
                    KEY_TRACKING_ACTIVE,
                    false
                )
                .apply()

            stopSelf(startId)
            return START_NOT_STICKY
        }

        val positionAlertEnabled =
            if (
                explicitUserStart
            ) {
                intent!!.getBooleanExtra(
                    EXTRA_POSITION_ALERT_ENABLED,
                    true
                )
            } else {
                preferences.getBoolean(
                    KEY_TRACKING_POSITION_ALERT_ENABLED,
                    true
                )
            }

        val positionThreshold =
            if (
                explicitUserStart
            ) {
                intent!!.getIntExtra(
                    EXTRA_POSITION_THRESHOLD,
                    100
                )
            } else {
                preferences.getInt(
                    KEY_TRACKING_POSITION_THRESHOLD,
                    100
                )
            }

        val calledAlertEnabled =
            if (
                explicitUserStart
            ) {
                intent!!.getBooleanExtra(
                    EXTRA_CALLED_ALERT_ENABLED,
                    true
                )
            } else {
                preferences.getBoolean(
                    KEY_TRACKING_CALLED_ALERT_ENABLED,
                    true
                )
            }

        monitoringJob?.cancel()

        monitoringJob =
            scope.launch {
                monitor(
                    carNumber = carNumber,
                    checkpointName = checkpoint,
                    positionAlertEnabled =
                        positionAlertEnabled,
                    positionThreshold =
                        positionThreshold,
                    forecastAlertEnabled =
                        false,
                    forecastAlertMinutes =
                        0,
                    calledAlertEnabled =
                        calledAlertEnabled
                )
            }

        saveMessage(
            if (
                explicitUserStart
            ) {
                "Мониторинг запущен."
            } else {
                "Мониторинг автоматически восстановлен."
            }
        )

        updateServiceNotification(
            "QueueWatch: $checkpoint"
        )

        return START_STICKY
    }


    private fun stopMonitoringByUser() {
        preferences.edit()
            .putBoolean(
                KEY_TRACKING_ACTIVE,
                false
            )
            .putString(
                KEY_MESSAGE,
                "Отслеживание остановлено пользователем."
            )
            .apply()

        monitoringJob?.cancel()
        monitoringJob = null

        alertNotificationJob?.cancel()
        alertNotificationJob = null

        alertManager?.acknowledge()
        cancelAlertNotification()
        clearAlertState()

        try {
            stopForeground(
                STOP_FOREGROUND_REMOVE
            )
        } catch (_: Exception) {
        }

        stopSelf()
    }
'''
service = service[:start] + new_start_section + service[end:]

required_service = [
    "ACTION_STOP_MONITORING",
    "ACTION_RESTORE_MONITORING",
    "KEY_TRACKING_ACTIVE",
    "explicitUserStart",
    "Мониторинг автоматически восстановлен.",
]
for marker in required_service:
    if marker not in service:
        raise SystemExit(f"persistent monitoring service validation failed: {marker}")

SERVICE.write_text(service, encoding="utf-8")


# -----------------------------------------------------------------------------
# MainActivity: restore the active session from SharedPreferences and expose a
# single explicit STOP button on the live tracking screen.
# -----------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

anchor = '''fun QueueWatchApp() {

    var carNumber by rememberSaveable {
        mutableStateOf("")
    }
'''
replacement = '''fun QueueWatchApp() {

    val context =
        LocalContext.current

    val servicePreferences =
        remember {
            context.getSharedPreferences(
                QueueWatchService.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
        }

    var carNumber by rememberSaveable {
        mutableStateOf(
            servicePreferences.getString(
                QueueWatchService.KEY_TRACKING_CAR_NUMBER,
                ""
            ) ?: ""
        )
    }
'''
main = replace_once(
    main,
    anchor,
    replacement,
    "QueueWatchApp persistent preferences"
)

main = replace_once(
    main,
    '''    var checkpoint by rememberSaveable {
        mutableStateOf("")
    }
''',
    '''    var checkpoint by rememberSaveable {
        mutableStateOf(
            servicePreferences.getString(
                QueueWatchService.KEY_TRACKING_CHECKPOINT,
                ""
            ) ?: ""
        )
    }
''',
    "checkpoint restore"
)

main = replace_once(
    main,
    '''    var trackingStarted by rememberSaveable {
        mutableStateOf(false)
    }
''',
    '''    var trackingStarted by rememberSaveable {
        mutableStateOf(
            servicePreferences.getBoolean(
                QueueWatchService.KEY_TRACKING_ACTIVE,
                false
            )
        )
    }
''',
    "tracking active restore"
)

main = replace_once(
    main,
    '''    var hasTrackingSession by rememberSaveable {
        mutableStateOf(false)
    }
''',
    '''    var hasTrackingSession by rememberSaveable {
        mutableStateOf(
            servicePreferences.getBoolean(
                QueueWatchService.KEY_TRACKING_ACTIVE,
                false
            )
        )
    }
''',
    "tracking session restore"
)

main = replace_once(
    main,
    '''    var positionAlertEnabled by rememberSaveable {
        mutableStateOf(true)
    }
''',
    '''    var positionAlertEnabled by rememberSaveable {
        mutableStateOf(
            servicePreferences.getBoolean(
                QueueWatchService.KEY_TRACKING_POSITION_ALERT_ENABLED,
                true
            )
        )
    }
''',
    "position alert restore"
)

main = replace_once(
    main,
    '''    var positionAlertThreshold by rememberSaveable {
        mutableStateOf("100")
    }
''',
    '''    var positionAlertThreshold by rememberSaveable {
        mutableStateOf(
            servicePreferences.getInt(
                QueueWatchService.KEY_TRACKING_POSITION_THRESHOLD,
                100
            ).toString()
        )
    }
''',
    "position threshold restore"
)

main = replace_once(
    main,
    '''    var calledAlertEnabled by rememberSaveable {
        mutableStateOf(true)
    }
''',
    '''    var calledAlertEnabled by rememberSaveable {
        mutableStateOf(
            servicePreferences.getBoolean(
                QueueWatchService.KEY_TRACKING_CALLED_ALERT_ENABLED,
                true
            )
        )
    }
''',
    "called alert restore"
)

call_anchor = '''                    calledAlertEnabled =
                        calledAlertEnabled
                )
'''
call_replacement = '''                    calledAlertEnabled =
                        calledAlertEnabled,

                    onStopTracking = {
                        val stopIntent =
                            Intent(
                                context,
                                QueueWatchService::class.java
                            ).apply {
                                action =
                                    QueueWatchService.ACTION_STOP_MONITORING
                            }

                        try {
                            context.startService(
                                stopIntent
                            )
                        } catch (_: Exception) {
                        }

                        servicePreferences.edit()
                            .putBoolean(
                                QueueWatchService.KEY_TRACKING_ACTIVE,
                                false
                            )
                            .apply()

                        hasTrackingSession = false
                        trackingStarted = false
                    }
                )
'''
main = replace_once(
    main,
    call_anchor,
    call_replacement,
    "TrackingScreen stop callback call"
)

signature_anchor = '''    positionAlertThreshold: Int,

    calledAlertEnabled: Boolean
) {
'''
signature_replacement = '''    positionAlertThreshold: Int,

    calledAlertEnabled: Boolean,

    onStopTracking: () -> Unit
) {
'''
main = replace_once(
    main,
    signature_anchor,
    signature_replacement,
    "TrackingScreen stop callback signature"
)

active_block = '''            Text(
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
'''
active_with_stop = active_block + '''

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp
                    )
            )

            Button(
                onClick =
                    onStopTracking,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        52.dp
                    ),

                shape =
                    RoundedCornerShape(
                        16.dp
                    ),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            RedColor,
                        contentColor =
                            Color.White
                    )
            ) {
                Text(
                    text =
                        "ОСТАНОВИТЬ ОТСЛЕЖИВАНИЕ",
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
'''
main = replace_once(
    main,
    active_block,
    active_with_stop,
    "tracking stop button"
)

required_main = [
    "servicePreferences.getBoolean(",
    "QueueWatchService.KEY_TRACKING_ACTIVE",
    "onStopTracking: () -> Unit",
    "ОСТАНОВИТЬ ОТСЛЕЖИВАНИЕ",
]
for marker in required_main:
    if marker not in main:
        raise SystemExit(f"persistent monitoring main validation failed: {marker}")

MAIN.write_text(main, encoding="utf-8")

print("Applied persistent QueueWatch monitoring and explicit stop control")
