package com.group8.permissionmanagerapp_uss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.group8.permissionmanagerapp_uss.ui.navigation.PermissionManagerRoot
import com.group8.permissionmanagerapp_uss.ui.theme.PermissionManagerAppUSSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PermissionManagerAppUSSTheme {
                PermissionManagerRoot()
            }
        }
    }
}
