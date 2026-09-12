package com.AMMR.ricehacks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    Home("Home", Icons.Filled.Home),
    Vitals("Vitals", Icons.Filled.MonitorHeart),
    MyData("My Data", Icons.Filled.Folder),
    Settings("Settings", Icons.Filled.Settings),
    Ai("AI", Icons.Filled.AutoAwesome)
}

enum class SettingsPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val action: String
) {
    Profile(
        "Profile",
        "Name, birthday, phone number, and emergency contact.",
        Icons.Filled.Person,
        "Update profile"
    ),
    Preferences(
        "Preferences",
        "Text size, language, and how the app should feel.",
        Icons.Filled.Tune,
        "Change preferences"
    ),
    Accessibility(
        "Accessibility",
        "Bigger controls, voice help, captions, and reduced motion.",
        Icons.Filled.Accessibility,
        "Save accessibility"
    ),
    Notifications(
        "Notifications",
        "Reminders for appointments, medicines, and shared records.",
        Icons.Filled.Notifications,
        "Manage notifications"
    ),
    Privacy(
        "Privacy",
        "Provider access, sign-in security, and data sharing choices.",
        Icons.Filled.Lock,
        "Review privacy"
    ),
    About(
        "About",
        "App version, medical disclaimer, and project information.",
        Icons.Filled.Info,
        "View about"
    ),
    HelpSupport(
        "Help & support",
        "Get help signing in or using your health record.",
        Icons.Filled.SupportAgent,
        "Contact support"
    )
}

data class HealthRecordItem(
    val title: String,
    val detail: String,
    val note: String
)

val currentPrescriptions = listOf(
    HealthRecordItem("Lisinopril", "10 mg, once daily", "Blood pressure"),
    HealthRecordItem("Metformin", "500 mg, twice daily", "Blood sugar"),
    HealthRecordItem("Atorvastatin", "20 mg at night", "Cholesterol")
)

val pastPrescriptions = listOf(
    HealthRecordItem("Amoxicillin", "500 mg, ended May 2026", "Sinus infection"),
    HealthRecordItem("Prednisone", "5 day pack, ended Jan 2026", "Inflammation"),
    HealthRecordItem("Ibuprofen", "As needed, stopped Dec 2025", "Back pain")
)

val allergies = listOf(
    HealthRecordItem("Penicillin", "Rash", "Tell every provider before new medicine"),
    HealthRecordItem("Latex", "Skin irritation", "Use latex-free gloves")
)

val conditions = listOf(
    HealthRecordItem("High blood pressure", "Managed", "Last reading 128/82"),
    HealthRecordItem("Type 2 diabetes", "Monitoring", "A1C due next visit")
)

val recentVisits = listOf(
    HealthRecordItem("Primary care visit", "Aug 28, 2026", "Medication review completed"),
    HealthRecordItem("Cardiology follow-up", "Jul 16, 2026", "No urgent changes")
)
