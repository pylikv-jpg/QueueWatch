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
    var enabled by remember { mutableStateOf(consent.currentGrant() != null) }
    var confirm by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            enabled = consent.currentGrant() != null
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
            Text("Помогать улучшать QueueWatch", Modifier.weight(1f), color = Color(0xFFF1F5F9), fontSize = 14.sp)
            Switch(modifier = Modifier.semantics { contentDescription = "Помогать улучшать QueueWatch" },
                checked = enabled, onCheckedChange = { requested ->
                if (requested) confirm = true else { consent.disable(); enabled = false }
            })
        }
        Text("Добровольная отправка статистики ожидания для проверки точности будущего прогноза. " +
            "Отказ не влияет на отслеживание и уведомления.", color = Color(0xFF9AA6B2), fontSize = 12.sp)
        Row {
            TextButton(onClick = { policy() }) { Text("Конфиденциальность") }
            TextButton(onClick = { info = true }) { Text("Данные и удаление") }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Статистика для улучшения прогноза") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Если вы разрешите, QueueWatch будет отправлять на сервер аналитики выбранный КПП, " +
                    "тип транспорта, позиции и время наблюдений, расчёты времени ожидания, момент вызова, " +
                    "случайные коды установки и сеанса. Эти данные нужны для сравнения точности двух способов расчёта.\n\n" +
                    "Отправка происходит во время включённого отслеживания, в том числе когда экран приложения закрыт. " +
                    "Номер автомобиля, контакты и GPS-координаты в статистику не отправляются.\n\n" +
                    "Участие добровольное. Отправку можно отключить в любой момент; отслеживание и уведомления продолжат работать. " +
                    "Отключение не удаляет ранее отправленные записи с сервера.")
                TextButton(onClick = { policy() }) { Text("Политика конфиденциальности") }
            }
        },
        confirmButton = { TextButton(onClick = { consent.enable(); enabled = true; confirm = false }) {
            Text("Разрешить отправку")
        } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Не отправлять") } }
    )
    if (info) {
        val code = context.getSharedPreferences(AnalyticsConsent.DATA_PREFERENCES, Context.MODE_PRIVATE)
            .getString("forecast_install_id", null)
        AlertDialog(onDismissRequest = { info = false }, title = { Text("Данные и удаление") },
            text = { SelectionContainer { Text("Код статистики: ${code ?: "ещё не создан"}\n\n" +
                "Для запроса удаления ранее отправленных записей напишите на vasilipylik@gmail.com " +
                "и укажите этот код. Номер автомобиля для поиска статистики не нужен. " +
                "Отключение переключателя прекращает новые расчёты и отправку статистики и очищает ожидающие отправки события на телефоне.") } },
            confirmButton = { TextButton(onClick = { info = false }) { Text("Закрыть") } })
    }
}
