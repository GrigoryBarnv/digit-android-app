package com.opentouch.sensorapp.presentation.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.opentouch.sensorapp.presentation.screen.MainScreen

class MainActivity : FragmentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Camera fragment handles USB permission retries after runtime permission changes.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the screen on for as long as the app is in the foreground -
        // Roberto reported the screen timing out and turning off mid-use
        // (v1.1.7 feedback). Cleared automatically once the app is closed
        // or backgrounded; no manual teardown needed.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ensureRuntimePermissions()
        setContent {
            MainScreen()
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
