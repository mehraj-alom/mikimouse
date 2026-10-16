package com.mikimouse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mikimouse.app.ui.GlassBackground
import com.mikimouse.app.ui.OperatorDark
import com.mikimouse.app.ui.OperatorScreen
import com.mikimouse.app.ui.OutputsScreen
import com.mikimouse.app.ui.ResourcesScreen
import com.mikimouse.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = filesDir.resolve("operator")
        setContent {
            MaterialTheme(colorScheme = OperatorDark) {
                var tab by remember { mutableStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf("Home", "Outputs", "Resources", "Settings").forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i },
                                    icon = {}, label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.fillMaxSize().padding(pad)) {
                        GlassBackground()
                        when (tab) {
                            0 -> OperatorScreen(root)
                            1 -> OutputsScreen(root)
                            2 -> ResourcesScreen(root)
                            3 -> SettingsScreen(root)
                        }
                    }
                }
            }
        }
    }
}
