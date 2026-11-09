package com.group8.permissionmanagerapp_uss.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.group8.permissionmanagerapp_uss.AppInfo
import com.group8.permissionmanagerapp_uss.ui.auth.Logo
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard

/**
 * Lo-fi "Privacy Information". Every statement here must match what the app actually does:
 * peer testers will compare these claims with the app's behaviour.
 */
@Composable
fun PrivacyInfoScreen(modifier: Modifier = Modifier) {
    InfoColumn(modifier, "How your data is collected and used") {
        InfoCard(
            "DATA WE COLLECT",
            "• Your account: name and email address, stored by Google Firebase so you can sign in.\n" +
                "• App permissions: which apps on this phone have which permissions, read on this phone by Android.\n" +
                "• Permission history: changes we notice (app name, permission, before and after, time).\n" +
                "We don't collect your location, contacts, photos, messages or any app's content, and there is no " +
                "advertising or analytics tracking.",
        )
        InfoCard(
            "HOW WE USE YOUR DATA",
            "• To show your apps' permissions, risk levels and history.\n" +
                "• To alert you when permissions change and remind you to review them weekly (you can turn these off).\n" +
                "• Your email address is used to send your 6-digit sign-in code. The code is sent by our verification " +
                "service (Google Apps Script, delivered via Brevo or Gmail) and expires after 5 minutes.",
        )
        InfoCard(
            "LOCAL STORAGE & RETENTION",
            "Permission data and history stay on this phone, encrypted with a key held by Android's secure hardware. " +
                "They are not uploaded to any server. History is kept for the last 30 days. " +
                "Android's automatic cloud backup is turned off for this app.",
        )
        InfoCard(
            "BACKUP & RESTORE",
            "Backups are optional files you create and store yourself, encrypted with a password you choose. " +
                "They contain your history and preferences. Importing a backup does not change Android's permissions.",
        )
        InfoCard(
            "YOUR CONTROL",
            "• Delete Local Data removes history and preferences from this phone at any time.\n" +
                "• Logging out removes your sign-in from this phone.\n" +
                "• We never sell or share your data.",
        )
        InfoCard(
            "WHAT THIS APP CAN AND CAN'T DO",
            "For your security, Android only lets its own Settings app change another app's permissions. " +
                "${AppInfo.NAME} explains, tracks and guides; you make the final switch in Android Settings, and we " +
                "check that it worked.",
        )
    }
}

/** Lo-fi "About Permission Manager" (now Permission Tracker). */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    InfoColumn(modifier, null) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(size = 72)
            Text(AppInfo.NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("Version ${AppInfo.VERSION}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoCard(
            "WHAT IT DOES",
            "${AppInfo.NAME} helps you see, understand and manage which apps can access your data. It explains each " +
                "permission in plain language, highlights risky and background access, keeps a private history of " +
                "changes, and guides you through changing permissions in Android Settings.",
        )
        InfoCard(
            "BUILT FROM USER RESEARCH",
            "Designed from a survey of 71 university students and usability testing of our prototype with 50 " +
                "participants. Features like labelled navigation, risk explanations, a separate History page and " +
                "password checks before big changes came directly from that feedback.",
        )
        InfoCard(
            "TEAM",
            "Group 8, Usable Security and Privacy (CSE 347/652 | DES306/525), IIIT-Delhi, Monsoon 2026\n\n" +
                "Alen Sujoy · Manojna Reddy Kamaram · Shanthini Muralidhar · Shreeya Chawla · Vaishnavi Rai",
        )
        InfoCard(
            "BUILT WITH",
            "Kotlin, Jetpack Compose, Material 3, AndroidX (Navigation, Lifecycle, DataStore, WorkManager), " +
                "Firebase Authentication and kotlinx.serialization.",
        )
    }
}

@Composable
private fun InfoColumn(modifier: Modifier, subtitle: String?, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        content()
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
