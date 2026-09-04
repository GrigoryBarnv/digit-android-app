package com.opentouch.sensorapp.presentation.screen

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

@Composable
fun MainScreen(
    incomingModelUri: Uri? = null,
    onIncomingModelHandled: () -> Unit = {}
) {
    // Top-level app surface.
    MaterialTheme {
        Surface {
            DemoScreen(
                incomingModelUri = incomingModelUri,
                onIncomingModelHandled = onIncomingModelHandled
            )
        }
    }
}
