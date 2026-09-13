package com.AMMR.ricehacks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DemoPatientRecord(
    val name: String,
    val age: String,
    val bloodType: String,
    val height: String,
    val weight: String,
    val gender: String,
    val allergies: List<HealthRecordItem>,
    val conditions: List<HealthRecordItem>,
    val medications: List<HealthRecordItem>,
    val providers: List<HealthRecordItem>
)

private val margaretRecord = DemoPatientRecord(
    name = "Margaret Chen",
    age = "83",
    bloodType = "O+",
    height = "5 ft 4 in",
    weight = "142 lb",
    gender = "Female",
    allergies = listOf(
        HealthRecordItem(
            "Penicillin",
            "Moderate",
            "Causes hives"
        ),
        HealthRecordItem(
            "Latex",
            "Mild",
            "Causes skin irritation"
        )
    ),
    conditions = listOf(
        HealthRecordItem(
            "High blood pressure",
            "Managed",
            "Treated with medication"
        ),
        HealthRecordItem(
            "Type 2 diabetes",
            "Monitoring",
            "Managed by primary care provider"
        )
    ),
    medications = listOf(
        HealthRecordItem(
            "Lisinopril",
            "10 mg, once daily",
            "For high blood pressure"
        ),
        HealthRecordItem(
            "Metformin",
            "500 mg, twice daily",
            "For type 2 diabetes"
        ),
        HealthRecordItem(
            "Atorvastatin",
            "20 mg, every evening",
            "For cholesterol management"
        )
    ),
    providers = listOf(
        HealthRecordItem(
            "Dr. Tung Sahur",
            "Primary Care Physician",
            "Rice Medical Group"
        ),
        HealthRecordItem(
            "Dr. Hayitsmi Itsverity",
            "Cardiologist",
            "Houston Heart Center"
        )
    )
)

@Composable
fun MargaretRecordScreen() {
    val record = margaretRecord

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Health record",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Synthetic demonstration data—not a real patient.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 16.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = record.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                SettingsValueRow("Age", record.age)
                SettingsValueRow("Blood type", record.bloodType)
                SettingsValueRow("Height", record.height)
                SettingsValueRow("Weight", record.weight)
                SettingsValueRow("Gender", record.gender)
            }
        }

        MargaretRecordSectionCard(
            title = "Allergies",
            items = record.allergies,
            emptyText = "No allergies are listed."
        )

        MargaretRecordSectionCard(
            title = "Conditions",
            items = record.conditions,
            emptyText = "No conditions are listed."
        )

        MargaretRecordSectionCard(
            title = "Current medications",
            items = record.medications,
            emptyText = "No medications are listed."
        )

        MargaretRecordSectionCard(
            title = "Providers",
            items = record.providers,
            emptyText = "No providers are listed."
        )
    }
}

@Composable
private fun MargaretRecordSectionCard(
    title: String,
    items: List<HealthRecordItem>,
    emptyText: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHigh
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )

            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = item.detail,
                            fontSize = 17.sp
                        )
                        Text(
                            text = item.note,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}