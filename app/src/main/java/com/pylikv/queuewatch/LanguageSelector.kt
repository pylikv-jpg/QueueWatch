package com.pylikv.queuewatch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
internal fun LanguageSelector() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val selected = AppLanguage.selected(context)
    val currentName = if (selected.isEmpty()) L.text(R.string.device_language)
        else Locale.forLanguageTag(selected).getDisplayLanguage(Locale.forLanguageTag(selected))
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("${L.text(R.string.language)}: $currentName")
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(L.text(R.string.language)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(L.text(R.string.language_hint))
                (listOf("") + LanguageChoice.supported).forEach { tag ->
                    val name = if (tag.isEmpty()) L.text(R.string.device_language)
                        else Locale.forLanguageTag(tag).getDisplayLanguage(Locale.forLanguageTag(tag))
                    TextButton(onClick = {
                        AppLanguage.select(context, tag)
                        open = false
                        val prefs = context.getSharedPreferences(QueueWatchService.PREFS_NAME, Context.MODE_PRIVATE)
                        if (prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)) {
                            context.startService(Intent(context, QueueWatchService::class.java)
                                .setAction(QueueWatchService.ACTION_REFRESH_LANGUAGE))
                        }
                        if (Build.VERSION.SDK_INT < 33) context.activity()?.recreate()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selected == tag) "✓  " else "") + name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(L.text(R.string.close)) } }
    )
}
