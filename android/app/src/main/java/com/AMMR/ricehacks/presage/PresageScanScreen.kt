package com.AMMR.ricehacks.presage

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.presagetech.smartspectra.ProcessingStatus
import kotlinx.coroutines.launch

@Composable
fun PresageScanScreen(
    onReadingReady: (VitalsReading) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { PresageController() }
    val scope = rememberCoroutineScope()

    val processingStatus by controller.sdk.processingStatus.observeAsState()
    val validationStatus by controller.sdk.validationStatus.observeAsState()
    val metrics by controller.sdk.metrics.observeAsState()
    val sdkError by controller.sdk.error.observeAsState()

    var latestPulse by remember { mutableStateOf<VitalMetric?>(null) }
    var latestBreathing by remember { mutableStateOf<VitalMetric?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(metrics) {
        metrics?.cardio?.pulseRateList
            ?.lastOrNull()
            ?.let { measurement ->
                latestPulse = VitalMetric(
                    value = measurement.value,
                    confidence = measurement.confidence,
                    stable = measurement.stable
                )
            }

        metrics?.breathing?.rateList
            ?.lastOrNull()
            ?.let { measurement ->
                latestBreathing = VitalMetric(
                    value = measurement.value,
                    confidence = measurement.confidence,
                    stable = measurement.stable
                )
            }
    }

    LaunchedEffect(sdkError) {
        sdkError?.let {
            errorMessage = it.message ?: "Presage could not complete the measurement."
        }
    }

    val startScan: () -> Unit = {
        latestPulse = null
        latestBreathing = null
        errorMessage = null

        scope.launch {
            runCatching {
                controller.start()
            }.onFailure {
                errorMessage = it.message ?: "Unable to start the camera."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScan()
        } else {
            errorMessage = "Camera permission is required for a health scan."
        }
    }

    val scanIsRunning =
        processingStatus == ProcessingStatus.STARTING ||
            processingStatus == ProcessingStatus.RUNNING

    val readingIsUsable =
        latestPulse?.stable == true &&
            latestBreathing?.stable == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Health scan",
            style = MaterialTheme.typography.headlineLarge
        )

        Text(
            text = "Keep your face and upper chest visible, remain still, and avoid talking."
        )

        AndroidView(
            factory = { previewContext ->
                PreviewView(previewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    controller.configure(this)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        )

        Text(
            text = validationStatus?.hint
                ?: "Press Start scan when you are positioned correctly."
        )

        Text(
            text = "Pulse: ${
                latestPulse?.let { "${it.value.toInt()} bpm (${it.confidence.toInt()}% confidence)" }
                    ?: "Waiting..."
            }"
        )

        Text(
            text = "Breathing: ${
                latestBreathing?.let {
                    "${it.value.toInt()} breaths/min (${it.confidence.toInt()}% confidence)"
                } ?: "Waiting..."
            }"
        )

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                if (scanIsRunning) {
                    scope.launch {
                        runCatching { controller.stop() }
                    }
                } else if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startScan()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            enabled = processingStatus != ProcessingStatus.STOPPING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (scanIsRunning) "Stop scan" else "Start scan")
        }

        OutlinedButton(
            onClick = {
                onReadingReady(
                    VitalsReading(
                        capturedAtMillis = System.currentTimeMillis(),
                        pulseRateBpm = latestPulse,
                        breathingRatePerMinute = latestBreathing,
                        validationCode = validationStatus?.code?.name ?: "UNKNOWN"
                    )
                )
            },
            enabled = readingIsUsable,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use this reading")
        }

        Text(
            text = "Wellness information only. This is not a medical diagnosis."
        )
    }
}