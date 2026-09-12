package com.AMMR.ricehacks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsContent(
    selectedPage: SettingsPage?,
    onSelectPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    if (selectedPage == null) {
        SettingsListScreen(onSelectPage = onSelectPage)
    } else {
        SettingsDetailScreen(
            page = selectedPage,
            onBack = onBack,
            darkTheme = darkTheme,
            onDarkThemeChange = onDarkThemeChange
        )
    }
}

@Composable
private fun SettingsListScreen(onSelectPage: (SettingsPage) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = colorScheme.onBackground,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {},
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search settings",
                    modifier = Modifier.size(30.dp),
                    tint = colorScheme.primary
                )
            }
        }

        Text(
            text = "Account",
            color = colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            SettingsPage.entries.forEachIndexed { index, page ->
                SettingsRow(
                    page = page,
                    onClick = { onSelectPage(page) }
                )
                if (index < SettingsPage.entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    page: SettingsPage,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = colorScheme.primary
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title,
                color = colorScheme.onSurface,
                fontSize = 21.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = page.description,
                color = colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsDetailScreen(
    page: SettingsPage,
    onBack: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to settings",
                modifier = Modifier.size(30.dp)
            )
        }

        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 20.dp)
                .size(44.dp),
            tint = colorScheme.primary
        )
        Text(
            text = page.title,
            color = colorScheme.onBackground,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            text = page.description,
            color = colorScheme.onSurfaceVariant,
            fontSize = 20.sp,
            lineHeight = 30.sp,
            modifier = Modifier.padding(top = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            SettingsDetailFields(page = page, darkTheme = darkTheme, onDarkThemeChange = onDarkThemeChange)
        }
    }
}

@Composable
private fun SettingsDetailFields(
    page: SettingsPage,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (page) {
            SettingsPage.Profile -> {
                SettingsValueRow("Full name", "Mary Johnson")
                SettingsValueRow("Date of birth", "May 14, 1951")
                SettingsValueRow("Phone", "(555) 014-2201")
                SettingsValueRow("Emergency contact", "Daniel Johnson")
                SettingsActionButton("Save profile")
            }
            SettingsPage.Preferences -> {
                AiSettingsPreferences()
                ControlledSettingsToggleRow("Use dark mode", darkTheme, onDarkThemeChange)
                SettingsToggleRow("High contrast mode", false)
                SettingsToggleRow("Use simple visit summaries", true)
                SettingsActionButton("Save preferences")
            }
            SettingsPage.Accessibility -> {
                SettingsValueRow("Button size", "Large")
                SettingsValueRow("Reading speed", "Slow")
                SettingsToggleRow("Always show captions", true)
                SettingsToggleRow("Speak screen changes aloud", false)
                SettingsToggleRow("Reduce motion", true)
                SettingsToggleRow("Stronger touch feedback", true)
                SettingsToggleRow("Confirm before leaving forms", true)
                SettingsActionButton("Save accessibility")
            }
            SettingsPage.Notifications -> {
                SettingsToggleRow("Medicine reminders", true)
                SettingsToggleRow("Appointment reminders", true)
                SettingsToggleRow("Doctor access alerts", true)
                SettingsToggleRow("Weekly record summary", false)
                SettingsActionButton("Save notifications")
            }
            SettingsPage.Privacy -> {
                SettingsToggleRow("Require approval for every scan", true)
                SettingsToggleRow("Hide sensitive notes by default", true)
                SettingsValueRow("Trusted devices", "This phone only")
                SettingsValueRow("Last password change", "Not set")
                SettingsActionButton("Review access history")
            }
            SettingsPage.About -> {
                SettingsValueRow("App", "HealthBridge")
                SettingsValueRow("Version", "1.0")
                SettingsValueRow("Project", "HackRice 2026")
                Text(
                    text = "HealthBridge helps patients organize and share health information. It does not replace medical advice from a doctor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 17.sp,
                    lineHeight = 25.sp
                )
            }
            SettingsPage.HelpSupport -> {
                SettingsValueRow("Support hours", "8 AM to 8 PM")
                SettingsValueRow("Email", "support@healthbridge.local")
                SettingsValueRow("Phone help", "(555) 010-1040")
                SettingsActionButton("Contact support")
            }
        }
    }
}

@Composable
private fun ControlledSettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsValueRow(label: String, value: String) {
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
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(label: String, initialChecked: Boolean) {
    var checked by remember { mutableStateOf(initialChecked) }
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colorScheme.onSurface,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}

@Composable
private fun SettingsActionButton(text: String) {
    Button(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
