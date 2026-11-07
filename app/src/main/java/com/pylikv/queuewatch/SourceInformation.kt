package com.pylikv.queuewatch

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OfficialSourceNotice() {
    var open by remember { mutableStateOf(false) }
    Text(L.text(R.string.source_short), modifier = Modifier.fillMaxWidth().clickable { open = true }
        .padding(vertical = 8.dp), color = Color(0xFF9AA6B2), fontSize = 12.sp,
        textAlign = TextAlign.Center)
    if (open) SourceInformationDialog { open = false }
}

@Composable
internal fun SourceInformationDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onClose,
        title = { Text(L.text(R.string.sources_about)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("QueueWatch")
                Spacer(Modifier.height(12.dp))
                Text(L.text(R.string.official_source), modifier = Modifier.clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://belarusborder.by/"))
                            .addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, L.text(R.string.browser_unavailable), Toast.LENGTH_LONG).show()
                    }
                })
                Spacer(Modifier.height(12.dp))
                Text(L.text(R.string.independent_notice))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(L.text(R.string.close)) } })
}
