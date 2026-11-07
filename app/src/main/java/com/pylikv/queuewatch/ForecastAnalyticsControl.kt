package com.pylikv.queuewatch

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pylikv.queuewatch.forecast.AnalyticsConsent

@Composable
internal fun ForecastAnalyticsControl() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(AnalyticsConsent.PREFERENCES, Context.MODE_PRIVATE) }
    val consent = remember { analyticsConsent(context) }
    var enabled by remember { mutableStateOf(consent.isEnabled()) }
    var confirm by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = consent.isEnabled()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun policy() {
        context.startActivity(Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/pylikv-jpg/QueueWatch/blob/main/privacy-policy.md")))
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF1B232C), RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(L.text(R.string.analytics_help), Modifier.weight(1f), color = Color(0xFFF1F5F9), fontSize = 14.sp)
            Switch(modifier = Modifier.semantics { contentDescription = L.text(R.string.analytics_help) },
                checked = enabled, onCheckedChange = { requested ->
                if (requested) confirm = true else { consent.disable(); enabled = false }
            })
        }
        Text(L.text(R.string.analytics_summary), color = Color(0xFF9AA6B2), fontSize = 12.sp)
        Row {
            TextButton(onClick = { policy() }) { Text(L.text(R.string.privacy)) }
            TextButton(onClick = { info = true }) { Text(L.text(R.string.data_deletion)) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(L.text(R.string.analytics_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(L.text(R.string.analytics_consent_body))
                TextButton(onClick = { policy() }) { Text(L.text(R.string.privacy_policy)) }
            }
        },
        confirmButton = { TextButton(onClick = { consent.enable(); enabled = true; confirm = false }) {
            Text(L.text(R.string.allow_sending))
        } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(L.text(R.string.do_not_send)) } }
    )
    if (info) {
        val code = context.getSharedPreferences(AnalyticsConsent.DATA_PREFERENCES, Context.MODE_PRIVATE)
            .getString("forecast_install_id", null)
        AlertDialog(onDismissRequest = { info = false }, title = { Text(L.text(R.string.data_deletion)) },
            text = { SelectionContainer { Text(L.text(R.string.analytics_code,
                code ?: L.text(R.string.not_created)) + "\n\n" + L.text(R.string.analytics_deletion_body)) } },
            confirmButton = { TextButton(onClick = { info = false }) { Text(L.text(R.string.close)) } })
    }
}
