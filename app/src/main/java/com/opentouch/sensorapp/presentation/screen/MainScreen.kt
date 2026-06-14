package com.opentouch.sensorapp.presentation.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

@Composable
fun MainScreen() {
    // Top-level app surface.
    MaterialTheme {
        Surface {
            DemoScreen()
        }
    }
}
