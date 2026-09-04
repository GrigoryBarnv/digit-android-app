package com.opentouch.sensorapp.ml

import android.app.Activity
import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.io.Closeable

enum class MlRuntimeState {
    DOWNLOADING,
    READY,
    UNAVAILABLE,
    FAILED
}

/** Requests the signed ML feature once when the app starts. */
class MlRuntimeInstaller(
    context: Context,
    private val onStateChanged: (MlRuntimeState) -> Unit
) : Closeable {
    companion object {
        const val MODULE_NAME = "mlruntime"
    }

    private val activity = context as? Activity
    private val manager: SplitInstallManager = SplitInstallManagerFactory.create(context.applicationContext)
    private val listener = SplitInstallStateUpdatedListener { state ->
        if (MODULE_NAME !in state.moduleNames()) return@SplitInstallStateUpdatedListener
        when (state.status()) {
            SplitInstallSessionStatus.INSTALLED -> {
                activity?.let { SplitCompat.installActivity(it) }
                onStateChanged(MlRuntimeState.READY)
            }
            SplitInstallSessionStatus.FAILED,
            SplitInstallSessionStatus.CANCELED -> onStateChanged(MlRuntimeState.FAILED)
            else -> onStateChanged(MlRuntimeState.DOWNLOADING)
        }
    }

    fun start() {
        manager.registerListener(listener)
        if (MODULE_NAME in manager.installedModules) {
            activity?.let { SplitCompat.installActivity(it) }
            onStateChanged(MlRuntimeState.READY)
            return
        }

        onStateChanged(MlRuntimeState.DOWNLOADING)
        manager.startInstall(
            SplitInstallRequest.newBuilder().addModule(MODULE_NAME).build()
        ).addOnFailureListener {
            onStateChanged(MlRuntimeState.UNAVAILABLE)
        }
    }

    override fun close() {
        manager.unregisterListener(listener)
    }
}
