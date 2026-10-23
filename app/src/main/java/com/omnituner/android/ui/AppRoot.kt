package com.omnituner.android.ui

import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.Surface
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnituner.android.ui.chords.ChordFinderScreen
import com.omnituner.android.ui.common.LocalTopBarSlots
import com.omnituner.android.ui.common.TopBarSlots
import com.omnituner.android.ui.metronome.MetronomeScreen
import com.omnituner.android.ui.scales.ScalesScreen
import com.omnituner.android.ui.theme.LocalAccentColors
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.LocalNavBarColors
import com.omnituner.android.ui.theme.SmoothShape
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
    val slots = remember { TopBarSlots() }
    // Settings is a grouped list, so its page (and the top bar over it) drops to the grouped-list
    // page color; every other screen sits on the plain surface.
    val pageColor = if (current == Destination.Settings) {
        LocalGroupedListColors.current.page
    } else {
        MaterialTheme.colorScheme.background
    }
    val selectTab: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openSettings = { navController.navigate(Destination.Settings.route) }

    // In landscape the tabs leave the bottom edge for a side rail; like the bar, it hides on Settings.
    // The rail also takes over the top bar there (it already names the page, and holds the Settings
    // button), so the screens get the full height.
    val railSide = railSide().takeIf { current.topLevel }
    val railInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
        if (railSide == RailSide.Left) WindowInsetsSides.Left else WindowInsetsSides.Right,
    )

    Row(Modifier.fillMaxSize().background(pageColor)) {
        if (railSide == RailSide.Left) SideRail(current, selectTab, openSettings, railInsets)
        Scaffold(
            // The rail already pads its edge for the system bars and cutout, so the top bar and the
            // content must not pad that side a second time.
            modifier = Modifier
                .weight(1f)
                .then(if (railSide != null) Modifier.consumeWindowInsets(railInsets) else Modifier),
            containerColor = pageColor,
            // The cutout too: in landscape the camera sits on a side edge, beside the content.
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
            topBar = {
                if (railSide == null) TopAppBar(
                    title = { (if (current.topLevel) slots.title else null)?.invoke() ?: Text(current.title) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = pageColor),
                    navigationIcon = {
                        if (!current.topLevel) {
                            // The tonal container matches the grouped-list page in light mode, so the
                            // button takes the group card color to stay visible on it.
                            FilledTonalIconButton(
                                onClick = { navController.popBackStack() },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = LocalGroupedListColors.current.group,
                                ),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    actions = {
                        if (current.topLevel) {
                            slots.actions?.invoke(this)
                            SettingsButton(onClick = openSettings)
                        }
                    },
                )
            },
            bottomBar = {
                if (current.topLevel && railSide == null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        NavTabs(
                            current = current,
                            onSelect = selectTab,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
        ) { padding ->
            CompositionLocalProvider(LocalTopBarSlots provides slots) {
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
        if (railSide == RailSide.Right) SideRail(current, selectTab, openSettings, railInsets)
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
    }
}

private enum class RailSide { Left, Right }

/**
 * Which edge the landscape rail sits on, or null in portrait. The rail follows the phone's
 * physical bottom edge (where the bottom bar was, and where 3-button navigation goes), so it
 * swaps sides when the phone is turned the other way.
 */
@Composable
private fun railSide(): RailSide? {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rotation by displayRotation()
    return when {
        !landscape -> null
        rotation == Surface.ROTATION_270 -> RailSide.Left
        else -> RailSide.Right
    }
}

// Turning straight from one landscape to the other is no configuration change (the activity is
// neither recreated nor told), so the rotation is watched on the display itself.
@Composable
private fun displayRotation(): State<Int> {
    val view = LocalView.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val rotation = remember(configuration) { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(context, view, rotation) {
        val displays = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                val display = view.display ?: return
                if (display.displayId == displayId) rotation.intValue = display.rotation
            }

            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        displays.registerDisplayListener(listener, null)
        onDispose { displays.unregisterDisplayListener(listener) }
    }
    return rotation
}

@Composable
private fun SideRail(
    current: Destination,
    onSelect: (Destination) -> Unit,
    onSettings: () -> Unit,
    windowInsets: WindowInsets,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        header = { SettingsButton(onClick = onSettings) },
        windowInsets = windowInsets.union(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
    ) {
        // Centred on its edge, like a dock, rather than stacked from the top.
        Spacer(Modifier.weight(1f))
        NavRailTabs(current = current, onSelect = onSelect)
        Spacer(Modifier.weight(1f))
    }
}

// M3 "fade through": the old screen fades out quickly, then the new one fades in while
// growing slightly, so unrelated tabs never visibly overlap.
private const val FadeOutMillis = 150
private const val FadeInMillis = 300

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis, easing = LinearOutSlowInEasing)) +
        scaleIn(tween(FadeInMillis, delayMillis = FadeOutMillis, easing = LinearOutSlowInEasing), initialScale = 0.92f)

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(FadeOutMillis, easing = FastOutLinearInEasing))

// Pill slide: a low-stiffness spring, settling in roughly 600 ms with a hint of overshoot.
private const val PillStiffness = 120f
private const val PillDamping = 0.8f

// Icon pop: ease up to IconPopScale, then drift back on a soft, lightly damped spring so it
// settles with one gentle overshoot instead of a sharp bounce.
private const val IconPopScale = 1.15f
private const val IconPopMillis = 220
private const val IconSettleStiffness = 150f
private const val IconSettleDamping = 0.6f

private val TabHeight = 64.dp
private val TabInset = 4.dp
private val TabShape = SmoothShape.Full

// The rail is the stock 80 dp wide; its tabs keep the bar's inset on either side.
private val RailTabWidth = 72.dp
private val RailSlot = TabHeight + TabInset * 2

@Composable
private fun animatePillOffset(slot: Dp, index: Int): State<Dp> = animateDpAsState(
    targetValue = slot * index.coerceAtLeast(0),
    animationSpec = spring(dampingRatio = PillDamping, stiffness = PillStiffness),
    label = "navPill",
)

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
        val pillOffset = animatePillOffset(slot, tabs.indexOf(current))
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
                    modifier = Modifier.weight(1f).padding(horizontal = TabInset),
                )
            }
        }
    }
}

// The same tabs and pill stood on end for the landscape rail: fixed-height slots, the pill
// sliding vertically between them.
@Composable
private fun NavRailTabs(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = Destination.tabs
    val pillOffset = animatePillOffset(RailSlot, tabs.indexOf(current))
    Box(modifier) {
        Box(
            Modifier
                .offset { IntOffset(0, pillOffset.value.roundToPx()) }
                .width(RailTabWidth)
                .height(RailSlot)
                .padding(vertical = TabInset)
                .background(LocalNavBarColors.current.indicator, TabShape),
        )
        Column(Modifier.selectableGroup()) {
            tabs.forEach { destination ->
                NavTab(
                    destination = destination,
                    selected = destination == current,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.width(RailTabWidth).padding(vertical = TabInset),
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
            iconScale.animateTo(IconPopScale, tween(IconPopMillis, easing = FastOutSlowInEasing))
            iconScale.animateTo(1f, spring(dampingRatio = IconSettleDamping, stiffness = IconSettleStiffness))
        }
    }

    Column(
        modifier = modifier
            .height(TabHeight)
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
