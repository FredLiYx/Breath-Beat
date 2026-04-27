package com.example.breathbeat.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Activity to show the rationale for Health Connect permissions.
 * This is a requirement for using the Health Connect API.
 */
class HealthConnectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Health Connect Permissions",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "This app requires access to your heart rate and SpO2 data to provide personalized breathing analysis and health monitoring.",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
