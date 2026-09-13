package com.AMMR.ricehacks.presage

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.AMMR.ricehacks.BuildConfig
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class PresageCameraAvailability(
    val frontAvailable: Boolean,
    val backAvailable: Boolean
) {
    val hasAnyCamera: Boolean
        get() = frontAvailable || backAvailable

    val preferredPosition: CameraPosition?
        get() = when {
            frontAvailable -> CameraPosition.FRONT
            backAvailable -> CameraPosition.BACK
            else -> null
        }
}

class PresageController {
    val sdk = SmartSpectraSdk.shared

    fun hasApiKey(): Boolean = BuildConfig.PRESAGE_API_KEY.isNotBlank()

    fun configure(previewView: PreviewView, cameraPosition: CameraPosition) {
        if (!hasApiKey()) {
            return
        }

        sdk.config.apiKey = BuildConfig.PRESAGE_API_KEY
        sdk.config.cameraPosition = cameraPosition
        sdk.config.imageOutputEnabled = false
        sdk.config.previewSurfaceProvider = previewView.surfaceProvider
        sdk.config.requestedMetrics = listOf(
            MetricType.PULSE_RATE,
            MetricType.BREATHING_RATE
        )
    }

    suspend fun inspectCameraAvailability(context: Context): PresageCameraAvailability {
        val provider = suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(context)
            )
        }

        return PresageCameraAvailability(
            frontAvailable = runCatching {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            }.getOrDefault(false),
            backAvailable = runCatching {
                provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            }.getOrDefault(false)
        )
    }

    suspend fun start() {
        sdk.start()
    }

    suspend fun stop() {
        sdk.stop()
    }
}
