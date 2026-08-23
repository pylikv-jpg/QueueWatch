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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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

    var checkpointSelected by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedCheckpoint by rememberSaveable {
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

            } else if (!checkpointSelected) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

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
                        onValueChange = { newValue ->
                            carNumber = newValue
                        },
                        label = {
                            Text("Номер автомобиля")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(28.dp)
                    )

                    Text(
                        text = "Выберите пункт пропуска",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "🇵🇱 Польша",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Брест"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Брест")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Козловичи"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Козловичи")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Берестовица"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Берестовица")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Брузги"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Брузги")
                    }

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "🇱🇹 Литва",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Каменный Лог"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Каменный Лог")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Бенякони"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Бенякони")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Котловка"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Котловка")
                    }

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "🇱🇻 Латвия",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Григоровщина"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Григоровщина")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {
                            selectedCheckpoint = "Урбаны"
                            checkpointSelected = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Урбаны")
                    }

                    Spacer(
                        modifier = Modifier.height(32.dp)
                    )
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
                        text = "Отслеживание",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "Автомобиль: $carNumber"
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = "Пункт пропуска: $selectedCheckpoint"
                    )

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Text(
                        text = "Отслеживание очереди будет подключено следующим этапом."
                    )
                }
            }
        }
    }
}
