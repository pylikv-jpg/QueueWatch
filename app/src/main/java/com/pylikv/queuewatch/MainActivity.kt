package com.pylikv.queuewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            QueueWatchApp()
        }
    }
}

@Composable
fun QueueWatchApp() {

    var trackingStarted by rememberSaveable {
        mutableStateOf(false)
    }

    var carNumber by rememberSaveable {
        mutableStateOf("")
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            if (!trackingStarted) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(
                        text = "QueueWatch",
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Text(
                        text = "Мониторинг электронной очереди",
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Button(
                        onClick = {
                            trackingStarted = true
                        },
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text("Начать отслеживание")
                    }
                }

            } else {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(
                        text = "Настройка отслеживания",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "Введите номер автомобиля"
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    OutlinedTextField(
                        value = carNumber,
                        onValueChange = {
                            carNumber = it
                        },
                        label = {
                            Text("Номер автомобиля")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Button(
                        onClick = {
                            // Следующим этапом здесь подключим выбор пункта пропуска.
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Продолжить")
                    }
                }
            }
        }
    }
}
