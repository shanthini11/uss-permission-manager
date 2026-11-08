package com.group8.permissionmanagerapp_uss.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.ui.components.AccessChip
import com.group8.permissionmanagerapp_uss.ui.components.AppAvatar
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.PreinstalledChip
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

/** Lo-fi "Background Risk Page": apps that can use sensitive data while closed. */
@Composable
fun BackgroundRiskScreen(
    onAppClick: (packageName: String, category: PermissionCategory) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val risk = LocalRiskColors.current

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isDemo) item { DemoBanner() }
        item {
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = if (state.backgroundAccess.isEmpty()) risk.low else risk.high)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${plural(state.backgroundAppCount, "app")} can use your data while closed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("•  Background access means an app can collect data even when you aren't using it.", style = MaterialTheme.typography.bodyMedium)
                    Text("•  It also uses more battery and increases how much data is collected about you.", style = MaterialTheme.typography.bodyMedium)
                    Text("•  For most apps, \"Only while using the app\" is enough.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.backgroundAccess.isEmpty()) {
            item {
                EmptyState(
                    title = "No background access",
                    message = "No app can use your location or sensors while it's closed.",
                )
            }
        } else {
            item {
                Text("REVIEW THESE (${state.backgroundAccess.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.backgroundAccess, key = { it.app.packageName + it.permission.category.name }) { access ->
            SectionCard {
                Row(
                    Modifier
                        .clickable(role = Role.Button) { onAppClick(access.app.packageName, access.permission.category) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppAvatar(access.app.packageName, access.app.label)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(access.app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(access.permission.category.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AccessChip(access.permission.access)
                            if (access.app.isSystemApp) PreinstalledChip()
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}
