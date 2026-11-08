package com.group8.permissionmanagerapp_uss.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.ui.components.AppAvatar
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.PreinstalledChip
import com.group8.permissionmanagerapp_uss.ui.components.RiskBadge
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard

@Composable
fun AppsScreen(
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsViewModel = viewModel(factory = AppsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Permissions may have been changed in system Settings while we were in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    AppsContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onAppClick = onAppClick,
        modifier = modifier,
    )
}

@Composable
fun AppsContent(
    state: AppsUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isDemo) item { DemoBanner() }

        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        }

        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { onFilterChange(f) },
                        label = { Text(f.label) },
                    )
                }
            }
        }

        item {
            Text(
                text = if (state.isLoading) "Loading apps…"
                else "Showing ${state.apps.size} of ${state.totalApps} apps · your apps first, riskiest first",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.apps.isEmpty() -> item {
                if (state.query.isNotBlank()) {
                    EmptyState(
                        title = "No apps match \"${state.query.trim()}\"",
                        message = "Check the spelling, or clear the search to see all apps.",
                    )
                } else {
                    EmptyState(
                        title = "No apps to show",
                        message = "No apps match this filter. Try \"All\", or turn on Demo mode in Settings.",
                    )
                }
            }

            else -> items(state.apps, key = { it.packageName }) { app ->
                AppRow(app = app, onClick = { onAppClick(app.packageName) })
            }
        }
    }
}

@Composable
fun AppRow(app: InstalledApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(packageName = app.packageName, label = app.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summaryLine(app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RiskBadge(app.riskLevel)
                if (app.isSystemApp) PreinstalledChip()
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun summaryLine(app: InstalledApp): String {
    val granted = app.grantedPermissions
    if (granted.isEmpty()) return "No sensitive access allowed"
    val names = granted.take(3).joinToString(", ") { it.category.title }
    val more = if (granted.size > 3) " +${granted.size - 3}" else ""
    val bg = if (app.hasBackgroundAccess) " · uses in background" else ""
    return "$names$more$bg"
}
