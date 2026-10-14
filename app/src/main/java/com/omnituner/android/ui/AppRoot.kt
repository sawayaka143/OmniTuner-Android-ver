package com.omnituner.android.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnituner.android.ui.chords.ChordFinderScreen
import com.omnituner.android.ui.metronome.MetronomeScreen
import com.omnituner.android.ui.scales.ScalesScreen
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalNavBarColors
import com.omnituner.android.ui.tuner.SettingsScreen
import com.omnituner.android.ui.tuner.TunerScreen

enum class Destination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val topLevel: Boolean,
) {
    Tuner("tuner", "Tuner", Icons.Outlined.GraphicEq, Icons.Filled.GraphicEq, topLevel = true),
    Chords("chords", "Chords", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic, topLevel = true),
    Scales("scales", "Scales", Icons.AutoMirrored.Outlined.QueueMusic, Icons.AutoMirrored.Filled.QueueMusic, topLevel = true),
    Metronome("metronome", "Metronome", Icons.Outlined.Timer, Icons.Filled.Timer, topLevel = true),
    Settings("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings, topLevel = false);

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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    NavTabs(
                        current = current,
                        onSelect = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Tuner.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() },
        ) {
            composable(Destination.Tuner.route) { TunerScreen() }
            composable(Destination.Chords.route) { ChordFinderScreen() }
            composable(Destination.Scales.route) { ScalesScreen() }
            composable(Destination.Metronome.route) { MetronomeScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

// M3 "fade through": the old screen fades out quickly, then the new one fades in while
// growing slightly, so unrelated tabs never visibly overlap.
private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.92f)

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(90, easing = FastOutLinearInEasing))

private val TabHeight = 64.dp
private val TabInset = 4.dp
private val TabShape = RoundedCornerShape(20.dp)

// Stock NavigationBarItem only puts its indicator behind the icon; here one pill covers icon and
// label and slides between the equal-width tabs instead of each tab fading its own.
@Composable
private fun NavTabs(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = Destination.tabs
    BoxWithConstraints(modifier) {
        val slot = maxWidth / tabs.size
        val pillOffset = animateDpAsState(
            targetValue = slot * tabs.indexOf(current).coerceAtLeast(0),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "navPill",
        )
        Box(
            Modifier
                .offset { IntOffset(pillOffset.value.roundToPx(), 0) }
                .width(slot)
                .height(TabHeight)
                .padding(horizontal = TabInset)
                .background(LocalNavBarColors.current.indicator, TabShape),
        )
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            tabs.forEach { destination ->
                NavTab(
                    destination = destination,
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) LocalAccentColors.current.accent else MaterialTheme.colorScheme.onSurface

    // Pop the icon when the tab becomes selected, but not on first composition (app start, or the
    // bar reappearing after Settings), where the selection is not news.
    val iconScale = remember { Animatable(1f) }
    val firstRun = remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstRun.value) {
            firstRun.value = false
        } else if (selected) {
            iconScale.animateTo(1.2f, tween(90))
            iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    Column(
        modifier = modifier
            .height(TabHeight)
            .padding(horizontal = TabInset)
            .clip(TabShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(color = color),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale.value
                scaleY = iconScale.value
            },
        )
        Text(
            text = destination.title,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
