package com.opentouch.sensorapp.presentation.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentouch.sensorapp.R

/**
 * Logo + "OpenTouch" name, drawn as MainActivity's very first Compose frame
 * - already sitting underneath Android's own unavoidable cold-start
 * icon-only screen (Theme.Digitapp.Starting) by the time that screen's exit
 * animation runs (see MainActivity.onCreate()'s setOnExitAnimationListener).
 * That exit animation gives the system's icon a small settle/pulse right as
 * it's removed, timed to land the instant this screen (already fully
 * composed underneath, same icon position/size) is revealed - so the
 * handoff reads as one continuous motion instead of the plain icon just
 * being replaced outright by icon+text a beat later.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E0E0E), Color(0xFF1F1F1F))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Same ic_launcher_foreground asset the system splash icon
            // (Theme.Digitapp.Starting, see themes.xml) uses - original
            // purple/white artwork, unmodified. Android automatically masks
            // windowSplashScreenAnimatedIcon into a 192dp-diameter circle
            // when no windowSplashScreenIconBackgroundColor is set (per the
            // SplashScreen API spec - confirmed on-device: the system phase
            // shows a round white circle, not our square asset's actual
            // shape). This clips the exact same asset into a circle at the
            // same 192dp size, so it's the same round shape in both places
            // and the settle-pulse handoff in MainActivity.onCreate() reads
            // as one continuous animation instead of circle-then-square.
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "OpenTouch logo",
                modifier = Modifier
                    .size(192.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
