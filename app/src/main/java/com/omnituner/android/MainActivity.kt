package com.omnituner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnituner.android.ui.tuner.TunerScreen
import com.omnituner.android.ui.theme.OmniTunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniTunerTheme {
                OmniTunerRoot()
            }
        }
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TABS = listOf(
    Tab("tuner", "Tuner", Icons.Filled.RadioButtonChecked),
    Tab("scales", "Scales", Icons.Filled.LibraryMusic),
    Tab("chords", "Chords", Icons.Filled.Tune),
    Tab("metronome", "Metronome", Icons.Filled.MusicNote),
)

@Composable
fun OmniTunerRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                for (tab in TABS) {
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                modifier = Modifier.semantics { contentDescription = tab.label },
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "tuner",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable("tuner") { TunerScreen() }
            composable("scales") { PlaceholderScreen("Scales") }
            composable("chords") { PlaceholderScreen("Chord Finder") }
            composable("metronome") { PlaceholderScreen("Metronome") }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text("$title — coming in the next milestone")
    }
}
