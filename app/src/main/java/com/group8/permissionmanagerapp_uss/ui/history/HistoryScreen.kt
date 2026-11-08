package com.group8.permissionmanagerapp_uss.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.data.history.EventSource
import com.group8.permissionmanagerapp_uss.data.history.PermissionEvent
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.ui.common.TimeFormat
import com.group8.permissionmanagerapp_uss.ui.components.AccessChip
import com.group8.permissionmanagerapp_uss.ui.components.AppAvatar
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

@Composable
fun HistoryScreen(
    onReviewPermission: (packageName: String, category: PermissionCategory) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()

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
            // Explains how History differs from Notifications (lo-fi finding UP2).
            Text(
                "A full record of permission changes on this device for the last 30 days. " +
                    "Dismissing an alert in Notifications doesn't remove it from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.historyEnabled) {
            item {
                SectionCard {
                    Text(
                        "Permission History is turned off, so new changes aren't being recorded. Turn it on in Settings.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item { WeekChartCard(state) }
        if (state.share.isNotEmpty()) item { ShareCard(state) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chronological logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogFilter.entries.forEach { f ->
                        FilterChip(selected = state.filter == f, onClick = { viewModel.onFilterChange(f) }, label = { Text(f.label) })
                    }
                }
            }
        }

        if (state.logs.isEmpty()) {
            item {
                EmptyState(
                    title = "No changes recorded yet",
                    message = "When an app's permissions change, whether through this app, Android Settings or an " +
                        "app asking you, it will appear here.",
                )
            }
        }

        val grouped = state.logs.groupBy { TimeFormat.dayGroup(it.timestamp, now) }
        grouped.forEach { (group, events) ->
            item(key = "h-$group") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(group.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("${events.size} ${if (events.size == 1) "record" else "records"}", style = MaterialTheme.typography.labelSmall)
                }
            }
            items(events, key = { it.id }) { event -> EventRow(event, onClick = { selectedId = event.id }) }
        }
    }

    val selected = selectedId?.let { id -> state.logs.firstOrNull { it.id == id } }
    if (selected != null) {
        EventDetailSheet(
            event = selected,
            onDismiss = { selectedId = null },
            onReview = {
                selectedId = null
                selected.category?.let { onReviewPermission(selected.packageName, it) }
            },
        )
    }
}

/** Lo-fi "7-Day Permission Frequency" bars: allowed vs denied changes per day. */
@Composable
private fun WeekChartCard(state: HistoryUiState) {
    val allowedColor = MaterialTheme.colorScheme.outline
    val deniedColor = MaterialTheme.colorScheme.primary
    val max = (state.week.maxOfOrNull { maxOf(it.allowed, it.denied) } ?: 0).coerceAtLeast(1)
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("7-day permission changes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${state.weekAllowed + state.weekDenied} changes · ${state.weekAllowed} allowed, ${state.weekDenied} denied",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(allowedColor, "Allowed")
                Legend(deniedColor, "Denied")
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .semantics {
                        contentDescription = state.week.joinToString { "${it.label}: ${it.allowed} allowed, ${it.denied} denied" }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                state.week.forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Row(Modifier.height(84.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Bar(day.allowed.toFloat() / max, allowedColor)
                            Bar(day.denied.toFloat() / max, deniedColor)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(day.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .width(10.dp)
            .fillMaxHeight(fraction.coerceIn(0.03f, 1f))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(if (fraction == 0f) color.copy(alpha = 0.2f) else color)
    )
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Lo-fi "Sensor Share": what your apps can currently access, by type. */
@Composable
private fun ShareCard(state: HistoryUiState) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.outline,
        MaterialTheme.colorScheme.outlineVariant,
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What your apps can access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 14.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        var start = -90f
                        state.share.forEachIndexed { i, slice ->
                            val sweep = slice.percent * 3.6f
                            drawArc(colors[i % colors.size], start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.shareTypes.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Types", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.share.forEachIndexed { i, slice ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                            Spacer(Modifier.width(8.dp))
                            Text(slice.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${slice.percent}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** One log row: app, permission, new access and time. Shared with Notifications. */
@Composable
fun EventRow(event: PermissionEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SectionCard(modifier) {
        Row(
            Modifier.clickable(role = Role.Button, onClickLabel = "Show details", onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAvatar(event.packageName, event.appLabel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(event.category?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                event.to?.let { AccessChip(it) }
                Text(TimeFormat.dateTime(context, event.timestamp), style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Lo-fi "Instagram Event Details": time, change, how it happened, device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailSheet(event: PermissionEvent, onDismiss: () -> Unit, onReview: () -> Unit) {
    val context = LocalContext.current
    val risk = LocalRiskColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(event.packageName, event.appLabel, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(event.appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${event.category?.title.orEmpty()} — ${event.to?.label.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Event time", TimeFormat.dateTime(context, event.timestamp))
                    DetailRow("Change", "${event.from?.label ?: "Not requested before"}  →  ${event.to?.label.orEmpty()}")
                    DetailRow("How", event.source.label)
                    DetailRow("Device", "This device")
                    HorizontalDivider()
                    Text(
                        when (event.source) {
                            EventSource.IN_APP -> "You made this change using Permission Manager, and we confirmed it in Android Settings."
                            EventSource.DETECTED -> "We noticed this change during a routine check. It may have been made in Android Settings, " +
                                "or the app asked and you answered."
                            EventSource.REVIEW_REMINDER -> "Weekly review reminder."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (event.to?.isGranted == true && event.from?.isGranted != true) {
                        Text(
                            "This app gained access. If you don't remember allowing it, review it now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = risk.high,
                        )
                    }
                }
            }
            if (event.category != null) {
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Review this permission") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
