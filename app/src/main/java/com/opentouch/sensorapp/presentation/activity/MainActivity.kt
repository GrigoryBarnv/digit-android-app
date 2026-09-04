package com.opentouch.sensorapp.presentation.activity

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.opentouch.sensorapp.presentation.screen.MainScreen
import com.opentouch.sensorapp.presentation.screen.SplashScreen
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    private var incomingModelUri by mutableStateOf<Uri?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Camera fragment handles USB permission retries after runtime permission changes.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate()/setContent(). MainActivity
        // is the launcher activity directly (see AndroidManifest.xml). This
        // shows Android's own unavoidable cold-start icon-only screen
        // (Theme.Digitapp.Starting), which the system dismisses on its own
        // once this activity's first Compose frame - SplashScreen(), see
        // setContent() below - is drawn. No custom exit-animation listener
        // here on purpose: an earlier version pulsed the system's icon via
        // SplashScreenViewProvider.iconView on exit, but that API throws a
        // NullPointerException on some OEM Android skins (confirmed
        // crashing on every launch on a Vivo phone) - not worth the crash
        // risk for a small transition flourish, especially heading into a
        // Play Store release that needs to work across arbitrary devices.
        // Baking "OpenTouch" into the system screen itself isn't reliable
        // either (confirmed by testing - it center-crops custom images), so
        // the two screens (system icon, then our icon+"OpenTouch") just
        // hand off to each other plainly instead.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        incomingModelUri = modelUriFromIntent(intent)
        // Keep the screen on for as long as the app is in the foreground -
        // Roberto reported the screen timing out and turning off mid-use
        // (v1.1.7 feedback). Cleared automatically once the app is closed
        // or backgrounded; no manual teardown needed.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureRuntimePermissions()
        setContent {
            // SplashScreen() (logo + "OpenTouch") is this activity's very
            // first Compose frame, drawn right after the system's own
            // icon-only screen dismisses. Held briefly, then MainScreen()
            // takes over. Kept as a state switch
            // within this same activity/window rather than a separate
            // Activity, so there's no second Activity-launch transition on
            // top of the system's own splash.
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(900)
                showSplash = false
            }
            if (showSplash) {
                SplashScreen()
            } else {
                MainScreen(
                    incomingModelUri = incomingModelUri,
                    onIncomingModelHandled = { incomingModelUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingModelUri = modelUriFromIntent(intent)
    }

    private fun modelUriFromIntent(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            else -> null
        }
    }

    private fun ensureRuntimePermissions() {
        val required = arrayOf(
            Manifest.permission.CAMERA
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
