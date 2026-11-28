package com.group8.permissionmanagerapp_uss.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.group8.permissionmanagerapp_uss.AppInfo
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

/** Shown instead of the app when the APK isn't signed with the official certificate (repackaged copy). */
@Composable
fun UnofficialCopyScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.GppBad, contentDescription = null, tint = LocalRiskColors.current.high, modifier = Modifier.size(64.dp))
            Text("This copy isn't the official ${AppInfo.NAME}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "It was signed by someone else, so it may have been modified to steal data. " +
                    "Uninstall it and install the APK from the official link, after checking its SHA-256 checksum in the user guide.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
