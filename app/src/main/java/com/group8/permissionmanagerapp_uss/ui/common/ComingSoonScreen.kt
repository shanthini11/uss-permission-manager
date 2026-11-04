package com.group8.permissionmanagerapp_uss.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState

/** Temporary placeholder for screens scheduled later in the build plan. */
@Composable
fun ComingSoonScreen(title: String, description: String, modifier: Modifier = Modifier) {
    EmptyState(title = title, message = description, modifier = modifier.fillMaxSize())
}
