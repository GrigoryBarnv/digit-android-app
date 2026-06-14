package com.opentouch.sensorapp.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
//Creates a Compose UI component called RgbControls.
//This component can be reused anywhere.
fun RgbControls(
    red: Float,
    green: Float,
    blue: Float,
    onRedChange: (Float) -> Unit, //Receives float, returns nothing
    onGreenChange: (Float) -> Unit,
    onBlueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("R: ${red.toInt()}", color = Color.White)
        Slider(value = red, onValueChange = onRedChange, valueRange = -50f..50f)

        Text("G: ${green.toInt()}", color = Color.White)
        Slider(value = green, onValueChange = onGreenChange, valueRange = -50f..50f)

        Text("B: ${blue.toInt()}", color = Color.White)
        Slider(value = blue, onValueChange = onBlueChange, valueRange = -50f..50f)
    }
}
