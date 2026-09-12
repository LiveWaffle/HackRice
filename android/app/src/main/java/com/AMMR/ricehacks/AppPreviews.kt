package com.AMMR.ricehacks

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.AMMR.ricehacks.data.AiAgent
import com.AMMR.ricehacks.data.AllergyData
import com.AMMR.ricehacks.data.AuthenticatedUser
import com.AMMR.ricehacks.data.ConditionData
import com.AMMR.ricehacks.data.FakeQrAccessRepository
import com.AMMR.ricehacks.data.HealthAiAnswer
import com.AMMR.ricehacks.data.HealthAiRepository
import com.AMMR.ricehacks.data.PatientDataRepository
import com.AMMR.ricehacks.data.PatientHealthData
import com.AMMR.ricehacks.data.PatientProfileData
import com.AMMR.ricehacks.ui.theme.RiceHacksTheme

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoggedInHomeScreenPreview() {
    RiceHacksTheme(dynamicColor = false) {
        LoggedInHomeScreen(
            patientSession = AuthenticatedUser(
                accessToken = "preview-token",
                userId = "preview-id",
                email = "gilliamandrew22@gmail.com"
            ),
            qrAccessRepository = FakeQrAccessRepository(),
            patientDataRepository = PreviewPatientDataRepository,
            aiRepository = PreviewHealthAiRepository
        )
    }
}

private object PreviewHealthAiRepository : HealthAiRepository {
    override suspend fun askQuestion(
        patientSessionToken: String,
        message: String,
        agent: AiAgent
    ): HealthAiAnswer {
        return HealthAiAnswer("[$agent] Bring your medicine list and ask about any side effects.")
    }
}

private object PreviewPatientDataRepository : PatientDataRepository {
    override suspend fun getMyHealthRecord(accessToken: String, patientId: String?): PatientHealthData {
        return PatientHealthData(
            profile = PatientProfileData(
                displayName = "Andrew Gilliam",
                phoneNumber = "555-0199",
                emergencyContact = "Jane Doe (555-0100)"
            ),
            healthRecord = null,
            activeMedications = emptyList(),
            pastMedications = emptyList(),
            allergies = listOf(
                AllergyData(
                    allergen = "Peanuts",
                    reaction = "Hives",
                    severity = "Moderate",
                    status = "Active",
                    firstObservedDate = null,
                    notes = null
                )
            ),
            conditions = listOf(
                ConditionData(
                    name = "Hay Fever",
                    diagnosisCode = "J30.1",
                    status = "Active",
                    severity = "Mild",
                    diagnosedDate = null,
                    notes = null
                )
            ),
            recentObservations = emptyList(),
            providers = emptyList(),
            accessLogs = emptyList()
        )
    }
}
