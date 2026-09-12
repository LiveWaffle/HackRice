package com.AMMR.ricehacks.presage

import androidx.camera.view.PreviewView
import com.AMMR.ricehacks.BuildConfig
import com.presagetech.smartspectra.CameraPosition
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType

class PresageController {
    val sdk = SmartSpectraSdk.shared

    fun configure(previewView: PreviewView) {
        check(BuildConfig.PRESAGE_API_KEY.isNotBlank()) {
            "PRESAGE_API_KEY is missing from android/local.properties"
        }

        sdk.config.apiKey = BuildConfig.PRESAGE_API_KEY
        sdk.config.cameraPosition = CameraPosition.FRONT
        sdk.config.imageOutputEnabled = false
        sdk.config.previewSurfaceProvider = previewView.surfaceProvider
        sdk.config.requestedMetrics = listOf(
            MetricType.PULSE_RATE,
            MetricType.BREATHING_RATE
        )
    }

    suspend fun start() {
        sdk.start()
    }

    suspend fun stop() {
        sdk.stop()
    }
}