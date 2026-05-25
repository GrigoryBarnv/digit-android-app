package com.example.digit_app.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.digit_app.presentation.component.RgbControls

@Composable
fun DemoScreen() {
    val red = remember { mutableFloatStateOf(50f) }
    val green = remember { mutableFloatStateOf(50f) }
    val blue = remember { mutableFloatStateOf(50f) }
    var showRgbControls by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DIGIT", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D))) { Text("FPS") }
                    Button(
                        onClick = { showRgbControls = !showRgbControls },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showRgbControls) Color(0xFF4A4A4A) else Color(0xFF2D2D2D)
                        )
                    ) { Text("RGB") }
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D))) { Text("MODEL") }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .background(Color.Black, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Camera Preview Placeholder", color = Color(0xFFAAAAAA), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))) { Text("Gallery") }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color(0xFF303030), CircleShape)
                )
                Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303030))) { Text("Mode") }
            }
        }

        if (showRgbControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 96.dp)
                    .background(Color(0xFF262626), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF3D3D3D), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                RgbControls(
                    red = red.floatValue,
                    green = green.floatValue,
                    blue = blue.floatValue,
                    onRedChange = { red.floatValue = it },
                    onGreenChange = { green.floatValue = it },
                    onBlueChange = { blue.floatValue = it }
                )
            }
        }
    }
}
