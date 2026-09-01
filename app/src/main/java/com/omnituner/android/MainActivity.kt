package com.omnituner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnituner.android.R
import com.omnituner.android.ui.chords.ChordFinderScreen
import com.omnituner.android.ui.metronome.MetronomeScreen
import com.omnituner.android.ui.scales.ScalesScreen
import com.omnituner.android.ui.theme.OmniTunerTheme
import com.omnituner.android.ui.theme.THEME_DARK
import com.omnituner.android.ui.theme.THEME_LIGHT
import com.omnituner.android.ui.tuner.SettingsScreen
import com.omnituner.android.ui.tuner.TunerScreen
import com.omnituner.android.ui.tuner.TunerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OmniTunerApp).container
        val initialThemeMode = container.themePreferences.mode()
        setContent {
            var themeMode by remember { mutableStateOf(initialThemeMode) }
            val darkTheme = when (themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            OmniTunerTheme(darkTheme = darkTheme) {
                OmniTunerRoot(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        container.themePreferences.setMode(mode)
                    },
                )
            }
        }
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val iconRes: Int,
)

private val TABS = listOf(
    Tab("tuner", "Tuner", R.drawable.tabler_circle_dot),
    Tab("scales", "Scales", R.drawable.tabler_playlist),
    Tab("chords", "Chords", R.drawable.tabler_guitar_pick),
    Tab("metronome", "Metronome", R.drawable.tabler_music),
)

@Composable
fun OmniTunerRoot(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val app = LocalContext.current.applicationContext as OmniTunerApp
    val container = app.container

    // Single tuner ViewModel shared by the Tuner tab and the settings page
    val tunerViewModel: TunerViewModel = viewModel()
    val tunerState by tunerViewModel.ui.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (currentRoute != "settings") {
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
                                        painterResource(tab.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.semantics { contentDescription = tab.label },
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "tuner",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Keep clear space for the floating settings button
                    .padding(top = 64.dp),
            ) {
                composable("tuner") { TunerScreen(viewModel = tunerViewModel) }
                composable("scales") { ScalesScreen(app) }
                composable("chords") { ChordFinderScreen(app) }
                composable("metronome") { MetronomeScreen(container.metronomePreferences) }
                composable(
                    "settings",
                    enterTransition = {
                        slideInHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) { it }
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) { it }
                    },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                ) {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        state = tunerState,
                        onBack = { navController.popBackStack() },
                        viewModel = tunerViewModel,
                    )
                }
            }
        }

        if (currentRoute != "settings") {
            SmallFloatingActionButton(
                onClick = { navController.navigate("settings") },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.tabler_settings),
                    contentDescription = "Settings",
                    modifier = Modifier.semantics { contentDescription = "Settings" },
                )
            }
        }
    }
}

