package com.AMMR.ricehacks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.AMMR.ricehacks.data.ApprovalResult
import com.AMMR.ricehacks.data.DoctorScanRequest
import com.AMMR.ricehacks.data.FakeQrAccessRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PatientApprovalDialog(
    request: DoctorScanRequest,
    onApprove: () -> Unit,
    onDeny: (timedOut: Boolean) -> Unit,
    submitDecision: suspend (approved: Boolean) -> ApprovalResult
) {
    var secondsRemaining by remember(request.requestId) {
        mutableStateOf(FakeQrAccessRepository.APPROVAL_TIMEOUT_SECONDS)
    }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(request.requestId) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining -= 1
        }

        submitDecision(false)
        onDeny(true)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(54.dp),
                    tint = colorScheme.primary
                )
                Text(
                    text = "Approve doctor access?",
                    color = colorScheme.onBackground,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = "Only approve if this doctor is with you now.",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                    lineHeight = 30.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ApprovalDetail("Doctor", request.doctorName)
                        ApprovalDetail("Hospital", request.hospitalName)
                        ApprovalDetail("Device", request.deviceInfo)
                    }
                }

                Text(
                    text = "Denying automatically in $secondsRemaining seconds",
                    color = colorScheme.error,
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp)
                )

                Button(
                    onClick = {
                        scope.launch {
                            submitDecision(true)
                            onApprove()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .height(58.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "Approve",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            submitDecision(false)
                            onDeny(false)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "Deny",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalDetail(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme

    Column {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Text(
            text = value,
            color = colorScheme.onSurface,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
