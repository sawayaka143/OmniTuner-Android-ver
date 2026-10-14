package com.omnituner.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnituner.android.ui.chords.ChordFinderScreen
import com.omnituner.android.ui.metronome.MetronomeScreen
import com.omnituner.android.ui.scales.ScalesScreen
import com.omnituner.android.ui.tuner.SettingsScreen
import com.omnituner.android.ui.tuner.TunerScreen

enum class Destination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val topLevel: Boolean,
) {
    Tuner("tuner", "Tuner", Icons.Filled.GraphicEq, topLevel = true),
    Chords("chords", "Chords", Icons.Filled.LibraryMusic, topLevel = true),
    Scales("scales", "Scales", Icons.AutoMirrored.Filled.QueueMusic, topLevel = true),
    Metronome("metronome", "Metronome", Icons.Filled.Timer, topLevel = true),
    Settings("settings", "Settings", Icons.Filled.Settings, topLevel = false);

    companion object {
        val tabs: List<Destination> = entries.filter { it.topLevel }

        fun fromRoute(route: String?): Destination = entries.firstOrNull { it.route == route } ?: Tuner
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(entry?.destination?.route)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title) },
                navigationIcon = {
                    if (!current.topLevel) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (current.topLevel) {
                        IconButton(onClick = { navController.navigate(Destination.Settings.route) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current.topLevel) {
                NavigationBar {
                    Destination.tabs.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Tuner.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Tuner.route) { TunerScreen() }
            composable(Destination.Chords.route) { ChordFinderScreen() }
            composable(Destination.Scales.route) { ScalesScreen() }
            composable(Destination.Metronome.route) { MetronomeScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}
