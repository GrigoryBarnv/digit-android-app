package com.opentouch.sensorapp.presentation.activity

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Camera fragment handles USB permission retries after runtime permission changes.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate()/setContent(). MainActivity
        // is the launcher activity directly (see AndroidManifest.xml). This
        // dismisses Android's own unavoidable cold-start icon-only screen
        // (Theme.Digitapp.Starting) once this activity's first Compose
        // frame - SplashScreen(), see setContent() below - is ready, and
        // ONLY once we call splashScreenViewProvider.remove() ourselves
        // (setting an exit animation listener disables the default
        // auto-dismiss). Baking "OpenTouch" into that system screen isn't
        // reliable (confirmed by testing - it center-crops custom images),
        // and there's no way to skip it outright - so instead of a hard cut
        // from icon-only to icon+name, this gives the system's icon a small
        // settle/pulse right as it's removed, timed to land the instant our
        // own matching icon+"OpenTouch" screen (already composed
        // underneath) is revealed - one continuous motion rather than two
        // screens back to back.
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val icon = splashScreenViewProvider.iconView
            icon.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(180)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        icon.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    splashScreenViewProvider.remove()
                                }
                            })
                            .start()
                    }
                })
                .start()
        }
        super.onCreate(savedInstanceState)
        // Keep the screen on for as long as the app is in the foreground -
        // Roberto reported the screen timing out and turning off mid-use
        // (v1.1.7 feedback). Cleared automatically once the app is closed
        // or backgrounded; no manual teardown needed.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureRuntimePermissions()
        setContent {
            // SplashScreen() (logo + "OpenTouch") is this activity's very
            // first Compose frame - already sitting underneath the system
            // icon screen by the time its exit animation above runs. Held
            // briefly, then MainScreen() takes over. Kept as a state switch
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
                MainScreen()
            }
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
