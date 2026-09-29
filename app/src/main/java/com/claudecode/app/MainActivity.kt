package com.claudecode.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.claudecode.app.service.ClaudeService
import com.claudecode.app.ui.nav.Routes
import com.claudecode.app.ui.screens.AgentChatScreen
import com.claudecode.app.ui.screens.HomeScreen
import com.claudecode.app.ui.screens.LocalChatScreen
import com.claudecode.app.ui.screens.ModelsScreen
import com.claudecode.app.ui.screens.SettingsScreen
import com.claudecode.app.ui.theme.ClaudeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBatteryExemption()
        startService(Intent(this, ClaudeService::class.java))
        setContent {
            ClaudeTheme {
                ClaudeNav()
            }
        }
    }

    private fun requestBatteryExemption() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        } catch (_: Exception) {
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.AGENT, "Agent", Icons.Filled.SmartToy),
    Tab(Routes.CHAT, "Local", Icons.Filled.Person),
    Tab(Routes.MODELS, "Models", Icons.Filled.Storage),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

@Composable
private fun ClaudeNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { go(t.route) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(pad),
        ) {
            composable(Routes.HOME) {
                HomeScreen(onOpenTab = ::go, onOpenSettings = { go(Routes.SETTINGS) })
            }
            composable(Routes.AGENT) {
                AgentChatScreen(onOpenSettings = { go(Routes.SETTINGS) })
            }
            composable(Routes.CHAT) {
                LocalChatScreen(
                    onOpenModels = { go(Routes.MODELS) },
                    onOpenEngine = { go(Routes.AGENT) },
                    onOpenSettings = { go(Routes.SETTINGS) },
                )
            }
            composable(Routes.MODELS) { ModelsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
