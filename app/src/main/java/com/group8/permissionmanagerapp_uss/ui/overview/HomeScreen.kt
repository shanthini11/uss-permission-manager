package com.group8.permissionmanagerapp_uss.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.ui.apps.AppRow
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

/** Lo-fi "Home Page / Permission Dashboard": a centralized overview (R1). */
@Composable
fun HomeScreen(
    onAppClick: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBackgroundRisk: () -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        item { ProtectionCard(state) }
        item {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoriesMiniCard(state, onOpenCategories, Modifier.weight(1f).fillMaxHeight())
                BackgroundRiskCard(state, onOpenBackgroundRisk, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Needs your attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Apps you installed with the broadest access",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenApps) { Text("All ${state.appCount} apps") }
            }
        }
        if (state.attentionApps.isEmpty()) {
            item {
                SectionCard {
                    Text(
                        "Nothing needs your attention right now. None of the apps you installed have broad access.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        items(state.attentionApps, key = { it.packageName }) { app ->
            AppRow(app = app, onClick = { onAppClick(app.packageName) })
        }
    }
}

/** Lo-fi "15% PROTECTED" ring with Critical / Sensitive / Total counts. */
@Composable
private fun ProtectionCard(state: OverviewUiState) {
    val percent = state.protectionPercent
    SectionCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(104.dp)
                    .semantics { contentDescription = "$percent percent protected" },
            ) {
                CircularProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("PROTECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your privacy overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$percent% of sensitive permission requests are blocked or limited.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatLine("Critical access allowed", state.criticalAllowed)
                StatLine("Sensitive access allowed", state.sensitiveAllowed)
                StatLine("Total allowed", state.totalAllowed)
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("•  $label", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        Text(value.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoriesMiniCard(state: OverviewUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val top = state.categories.filter { it.grantedCount > 0 }.sortedByDescending { it.grantedCount }.take(3)
    val max = (top.maxOfOrNull { it.grantedCount } ?: 1).coerceAtLeast(1)
    SectionCard(modifier) {
        Column(
            Modifier.clickable(role = Role.Button, onClickLabel = "Open permission categories", onClick = onClick).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PERMISSION CATEGORIES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            if (top.isEmpty()) Text("No access allowed", style = MaterialTheme.typography.bodySmall)
            top.forEach { summary ->
                Column {
                    Row {
                        Text(summary.category.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(plural(summary.grantedCount, "app"), style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { summary.grantedCount.toFloat() / max },
                        modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 2.dp),
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundRiskCard(state: OverviewUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val risk = LocalRiskColors.current
    val count = state.backgroundAppCount
    SectionCard(modifier) {
        Column(
            Modifier.clickable(role = Role.Button, onClickLabel = "Open background risk", onClick = onClick).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BACKGROUND RISK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                count.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) risk.high else risk.low,
            )
            Text(
                if (count == 1) "app can use your data while it's closed" else "apps can use your data while they're closed",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
