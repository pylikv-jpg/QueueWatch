package com.pylikv.queuewatch.forecast

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun ForecastCard(car: String, checkpointName: String, state: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("queuewatch_forecast", Context.MODE_PRIVATE) }
    val telemetry = remember { context.getSharedPreferences("queuewatch_forecast_telemetry", Context.MODE_PRIVATE) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sharing by remember { mutableStateOf(telemetry.getBoolean("enabled", true)) }
    LaunchedEffect(car, checkpointName) { while (true) { tick = System.currentTimeMillis(); delay(1000) } }
    val localCar = car.uppercase().replace(Regex("[\\s-]"), "")
    val updated = prefs.getLong("updated", 0)
    val sameCar = prefs.getString("car_key", "").orEmpty().substringBefore('|') == localCar
    val fresh = tick - updated in 0..90_000L && sameCar && prefs.getString("checkpoint_name", "") == checkpointName
    val available = fresh && state == "IN_QUEUE" && prefs.getBoolean("available", false)
    Column(Modifier.fillMaxWidth().background(Color(0xFF172D36), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("ПРОБНЫЙ ПРОГНОЗ", color = Color(0xFF68D7C7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (available) {
            val front = prefs.getInt("position", 0) == 1
            Text(if (front) "Вы в начале очереди" else "Ориентировочно ${duration(prefs.getFloat("eta", 0f))}",
                color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            if (front) Text("Ожидайте подтверждённого вызова от сервера.", color = Color.LightGray, fontSize = 13.sp)
            else {
                Text("Диапазон: ${duration(prefs.getFloat("low", 0f))} — ${duration(prefs.getFloat("high", 0f))}", color = Color.White, fontSize = 15.sp)
                val confidence = when (prefs.getString("confidence", "LOW")) { "HIGH" -> "высокая"; "MEDIUM" -> "средняя"; else -> "низкая" }
                Text("Надёжность: $confidence · ${String.format(Locale.getDefault(), "%.1f", prefs.getFloat("speed", 0f))} поз./ч", color = Color.LightGray, fontSize = 13.sp)
                if (!prefs.getBoolean("calibrated", false)) Text("Диапазон предварительный: точность ещё проверяется.", color = Color(0xFFEAC56D), fontSize = 12.sp)
            }
        } else Text(when {
            state == "CALLED" -> "Вызов подтверждён. Результат сохранён для проверки прогноза."
            !fresh -> "Ожидаем свежие данные очереди…"
            else -> prefs.getString("message", "Недостаточно данных для прогноза").orEmpty()
        }, color = Color.White, fontSize = 15.sp)
        if (fresh) Text("Пересчёт: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(updated))}", color = Color.LightGray, fontSize = 11.sp)
        val cutoff = prefs.getLong("history_cutoff", 0)
        if (available && cutoff > 0 && tick - cutoff > 7 * 86_400_000L) Text("Архив по ${SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(cutoff))}: учитываем давность данных.", color = Color(0xFFEAC56D), fontSize = 11.sp)
        Text("Оценка до начала очереди. Время пересечения границы может быть больше.", color = Color.LightGray, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Помогать проверять точность", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(top = 12.dp))
            Switch(checked = sharing, onCheckedChange = {
                sharing = it
                val edit = telemetry.edit().putBoolean("enabled", it)
                if (!it) edit.remove("pending")
                edit.apply()
            })
        }
        Text("В общую базу отправляются прогноз, позиция и факт вызова. Номер машины не отправляется.", color = Color.LightGray, fontSize = 11.sp)
    }
}

private fun duration(value: Float): String {
    val minutes = ((value.coerceAtLeast(0f) / 5).roundToLong() * 5).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60} ч ${minutes % 60} мин" else "$minutes мин"
}

@Composable
fun ForecastPrivacyNote() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("queuewatch_forecast_telemetry", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    Column(Modifier.fillMaxWidth().background(Color(0xFF172D36), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text("Проверяем точность прогноза", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("Прогноз, позиция и факт вызова поступают в общую базу. Номер машины не отправляется. Это можно отключить.", color = Color.LightGray, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Участвовать в проверке", color = Color.White, modifier = Modifier.weight(1f).padding(top = 12.dp), fontSize = 13.sp)
            Switch(enabled, { enabled = it; val e = prefs.edit().putBoolean("enabled", it); if (!it) e.remove("pending"); e.apply() })
        }
    }
}
