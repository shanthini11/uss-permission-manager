package com.group8.permissionmanagerapp_uss.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.group8.permissionmanagerapp_uss.ui.components.CategoryIconBox
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.PreinstalledChip
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.components.SensitivityBadge

/**
 * Lo-fi "Permission Categories" page: browse by resource (R2, R15).
 * Has its own labelled tab, because the old lock-icon/"Top Access" entry point was hard to find (UP3, UP4).
 */
@Composable
fun CategoriesScreen(
    onAppClick: (packageName: String, category: PermissionCategory) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openCategory by rememberSaveable { mutableStateOf<PermissionCategory?>(null) }

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
        item { CategoriesHeader(state) }
        if (state.categories.isEmpty()) {
            item { EmptyState("No permissions to show", "None of your apps have requested sensitive permissions.") }
        }
        items(state.categories, key = { it.category.name }) { summary ->
            CategoryCard(summary, onReview = { openCategory = summary.category })
        }
    }

    val sheetSummary = openCategory?.let { cat -> state.categories.firstOrNull { it.category == cat } }
    if (sheetSummary != null) {
        CategoryAppsSheet(
            summary = sheetSummary,
            onDismiss = { openCategory = null },
            onAppClick = { pkg ->
                openCategory = null
                onAppClick(pkg, sheetSummary.category)
            },
        )
    }
}

@Composable
private fun CategoriesHeader(state: OverviewUiState) {
    SectionCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Insights, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.totalAllowed} permissions allowed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${state.criticalAllowed} critical · ${state.sensitiveAllowed} sensitive · across ${plural(state.appCount, "app")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(summary: CategorySummary, onReview: () -> Unit) {
    val category = summary.category
    SectionCard {
        Column(
            Modifier
                .clickable(role = Role.Button, onClickLabel = "Review ${category.title}", onClick = onReview)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBox(category, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.title.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${plural(summary.grantedCount, "app")} ${if (summary.grantedCount == 1) "has" else "have"} access",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SensitivityBadge(category.sensitivity)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // A few app avatars, like the lo-fi "G I T +12" row.
                summary.apps.filter { it.permission.access.isGranted }.take(3).forEach {
                    AppAvatar(it.app.packageName, it.app.label, size = 24.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(summary.highlight, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("Review", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }

            LinearProgressIndicator(
                progress = { if (summary.requestedCount == 0) 0f else summary.grantedCount.toFloat() / summary.requestedCount },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                drawStopIndicator = {},
            )
        }
    }
}

/** Lo-fi "Camera Category Details" sheet: every app that asked for this permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryAppsSheet(
    summary: CategorySummary,
    onDismiss: () -> Unit,
    onAppClick: (String) -> Unit,
) {
    val category = summary.category
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBox(category, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.title.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${summary.grantedCount} of ${plural(summary.requestedCount, "app")} allowed · This device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(4.dp))
            Text(category.privacyRisk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(summary.apps, key = { it.app.packageName }) { access ->
                    SectionCard {
                        Row(
                            Modifier
                                .clickable(role = Role.Button) { onAppClick(access.app.packageName) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppAvatar(access.app.packageName, access.app.label, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(access.app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AccessChip(access.permission.access)
                                    if (access.app.isSystemApp) PreinstalledChip()
                                }
                            }
                            Text("Review", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
