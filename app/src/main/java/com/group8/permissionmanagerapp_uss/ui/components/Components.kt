package com.group8.permissionmanagerapp_uss.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.RiskLevel
import com.group8.permissionmanagerapp_uss.data.model.Sensitivity
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icon for each permission category, used consistently across all screens. */
val PermissionCategory.icon: ImageVector
    get() = when (this) {
        PermissionCategory.LOCATION -> Icons.Outlined.LocationOn
        PermissionCategory.CAMERA -> Icons.Outlined.PhotoCamera
        PermissionCategory.MICROPHONE -> Icons.Outlined.Mic
        PermissionCategory.FILES_PHOTOS -> Icons.Outlined.PhotoLibrary
        PermissionCategory.CONTACTS -> Icons.Outlined.Contacts
        PermissionCategory.SMS -> Icons.Outlined.Sms
        PermissionCategory.PHONE -> Icons.Outlined.Phone
        PermissionCategory.CALENDAR -> Icons.Outlined.CalendarMonth
        PermissionCategory.BODY_SENSORS -> Icons.Outlined.MonitorHeart
        PermissionCategory.PHYSICAL_ACTIVITY -> Icons.Outlined.FitnessCenter
        PermissionCategory.NEARBY_DEVICES -> Icons.Outlined.Bluetooth
        PermissionCategory.NOTIFICATIONS -> Icons.Outlined.Notifications
    }

/**
 * App icon. Real apps show their launcher icon; demo apps (and any icon that fails to load)
 * show a letter avatar like the lo-fi.
 */
@Composable
fun AppAvatar(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = if (packageName.startsWith("demo.")) null else withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun RiskBadge(level: RiskLevel, modifier: Modifier = Modifier) {
    val c = LocalRiskColors.current
    val (container, content) = when (level) {
        RiskLevel.HIGH -> c.highContainer to c.high
        RiskLevel.MEDIUM -> c.mediumContainer to c.medium
        RiskLevel.LOW -> c.lowContainer to c.low
    }
    Pill(level.label, container, content, modifier)
}

@Composable
fun SensitivityBadge(sensitivity: Sensitivity, modifier: Modifier = Modifier) {
    val c = LocalRiskColors.current
    val (container, content) = when (sensitivity) {
        Sensitivity.CRITICAL -> c.highContainer to c.high
        Sensitivity.SENSITIVE -> c.mediumContainer to c.medium
        Sensitivity.LOW -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(sensitivity.label.uppercase(), container, content, modifier)
}

/** Shows the current access level. Background ("always") access is highlighted as a risk. */
@Composable
fun AccessChip(access: AccessLevel, modifier: Modifier = Modifier) {
    val c = LocalRiskColors.current
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (access) {
        AccessLevel.ALWAYS -> c.highContainer to c.high
        AccessLevel.WHILE_IN_USE, AccessLevel.ALLOWED -> scheme.primaryContainer to scheme.onPrimaryContainer
        AccessLevel.LIMITED -> c.lowContainer to c.low
        AccessLevel.NOT_ALLOWED -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Pill(access.label, container, content, modifier)
}

/** Rounded white card used for every grouped section, matching the lo-fi cards. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        content()
    }
}

/** Shown on every data screen while Demo mode is on, so sample data is never mistaken for real data. */
@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    val c = LocalRiskColors.current
    Surface(
        color = c.mediumContainer,
        contentColor = c.medium,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Demo mode is on. Showing sample apps, not your real apps." },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Demo mode: showing sample apps. Turn it off in Settings to see your real apps.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Small circular icon holder for permission categories. */
@Composable
fun CategoryIconBox(category: PermissionCategory, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Centered message for empty lists and errors (e.g. search with no results). */
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
