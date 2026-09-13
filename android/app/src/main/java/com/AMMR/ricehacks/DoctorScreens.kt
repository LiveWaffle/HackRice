package com.AMMR.ricehacks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.AMMR.ricehacks.data.PatientDataRepository
import com.AMMR.ricehacks.data.PatientHealthData
import kotlinx.coroutines.launch

@Composable
fun DoctorScannerScreen(
    patientDataRepository: PatientDataRepository
) {
    var scannedToken by remember { mutableStateOf("") }
    var patientData by remember { mutableStateOf<PatientHealthData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Patient Scanner",
            color = colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        if (patientData == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Scan Patient QR",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Scan the QR code shown on the patient's Nora app to securely view their medical history.",
                        fontSize = 18.sp,
                        color = colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    
                    OutlinedTextField(
                        value = scannedToken,
                        onValueChange = { scannedToken = it },
                        label = { Text("Enter Token (Demo)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                runCatching {
                                    // In a real app, we'd use the token to fetch data
                                    // For now, we simulate fetching Margaret's data
                                    patientDataRepository.getMyHealthRecord("preview-token", null) 
                                }.onSuccess {
                                    patientData = it
                                    isLoading = false
                                }.onFailure {
                                    errorMessage = "Could not verify patient token."
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) "Verifying..." else "Simulate Scan")
                    }
                }
            }
        } else {
            // Display Patient Data
            Button(
                onClick = { patientData = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan Another Patient")
            }
            
            patientData?.let { data ->
                HealthRecordOverviewCard(data)
                
                MyDataSectionCard(
                    title = "Current prescriptions",
                    items = data.activeMedications.map { med ->
                        HealthRecordItem(med.name, med.dose ?: "", med.notes ?: "")
                    },
                    emptyText = "No current prescriptions."
                )
                
                MyDataSectionCard(
                    title = "Allergies",
                    items = data.allergies.map { allergy ->
                        HealthRecordItem(allergy.allergen, allergy.reaction ?: "", allergy.severity ?: "")
                    },
                    emptyText = "No allergies listed."
                )
            }
        }

        errorMessage?.let {
            Text(it, color = colorScheme.error)
        }
    }
}
