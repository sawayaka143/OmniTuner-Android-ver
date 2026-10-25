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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import com.omnituner.android.ui.tuner.LicensesScreen
import com.omnituner.android.ui.tuner.ReferencePitchScreen
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
    Settings("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings, topLevel = false),
    ReferencePitch("settings/reference-pitch", "Reference pitch", Icons.Outlined.Settings, Icons.Filled.Settings, topLevel = false),
    Licenses("licenses", "Open-source licenses", Icons.Outlined.Settings, Icons.Filled.Settings, topLevel = false);

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
    // 0 on a tab, 1 on Settings and its sub-pages; it drives the Settings button's trip, the bars'
    // cross-fade, the rail's slide and the page color, following the page transition (and the
    // predictive back gesture) frame by frame.
    val motion = remember { SettingsMotion(restingLevel = current.motionLevel) }

    // A bar that is fading out keeps naming its own page, though `current` already names the next.
    // Saved, so a back swipe after the activity is recreated on Settings still names the right tab.
    var lastTab by rememberSaveable { mutableStateOf(if (current.topLevel) current else Destination.Tuner) }
    var lastSettingsPage by rememberSaveable { mutableStateOf(if (current.topLevel) Destination.Settings else current) }
    if (current.topLevel) lastTab = current else lastSettingsPage = current

    // Settings and its sub-pages are grouped lists, so their page (and the top bar over it) drops to
    // the grouped-list page color; the tabs sit on the plain surface. It blends along the motion.
    val tabPageColor = MaterialTheme.colorScheme.background
    val settingsPageColor = LocalGroupedListColors.current.page
    // The tonal container matches the grouped-list page in light mode, so on Settings the button
    // takes the group card color to stay visible on it.
    val settingsButtonColor = LocalGroupedListColors.current.group

    val selectTab: (Destination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // In landscape the tabs leave the bottom edge for a side rail; it slides away on Settings.
    // The rail also takes over the top bar there (it already names the page, and holds the Settings
    // button), so the screens get the full height.
    val railSide = railSide()
    val landscape = railSide != null
    val railInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
        if (railSide == RailSide.Left) WindowInsetsSides.Left else WindowInsetsSides.Right,
    )
    val spots = remember(railSide) { ButtonSpots() }

    // Each flips once per transition. The space the rail and the top bar take follows the page on
    // screen, which changes at the fade-through's hand-over, so the reflow is never seen.
    val tabPageShowing by remember { derivedStateOf { motion.tabPageShowing } }
    val tabChromeShown by remember { derivedStateOf { motion.progress < 1f } }
    val settingsBarShown by remember { derivedStateOf { motion.progress > 0f } }
    val railReserved = landscape && tabPageShowing
    val topBarReserved = !landscape || !tabPageShowing

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(lerp(tabPageColor, settingsPageColor, motion.progress)) },
    ) {
        Row(Modifier.fillMaxSize()) {
            if (railSide == RailSide.Left && tabChromeShown) {
                SideRail(lastTab, selectTab, railSide, railReserved, motion, spots, railInsets)
            }
            Scaffold(
                // The rail already pads its edge for the system bars and cutout, so the top bar and
                // the content must not pad that side a second time.
                modifier = Modifier
                    .weight(1f)
                    .then(if (railReserved) Modifier.consumeWindowInsets(railInsets) else Modifier),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                // The cutout too: in landscape the camera sits on a side edge, beside the content.
                contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                // The bars themselves are drawn over the whole width below; this only keeps their room.
                topBar = { if (topBarReserved) TopBarSpace() },
                bottomBar = {
                    // Like the rail, the bar slides off its edge on the way to Settings and gives its
                    // room back at the hand-over, when neither page shows.
                    if (!landscape && tabChromeShown) {
                        NavigationBar(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    if (tabPageShowing) {
                                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                    } else {
                                        // Reported as empty (0 x 0), so the Scaffold pads the page for
                                        // the system bar alone; Scaffold places it at the very bottom,
                                        // so it is drawn up from there.
                                        layout(0, 0) { placeable.place(0, -placeable.height) }
                                    }
                                }
                                .graphicsLayer { translationY = size.height * motion.chromeOffEdge },
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            NavTabs(
                                // The tab it leaves stays selected while it slides away.
                                current = lastTab,
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
                        page(Destination.Tuner, motion) { TunerScreen() }
                        page(Destination.Chords, motion) { ChordFinderScreen() }
                        page(Destination.Scales, motion) { ScalesScreen() }
                        page(Destination.Metronome, motion) { MetronomeScreen() }
                        page(Destination.Settings, motion) {
                            SettingsScreen(
                                onOpenReferencePitch = { navController.navigate(Destination.ReferencePitch.route) },
                                onOpenLicenses = { navController.navigate(Destination.Licenses.route) },
                            )
                        }
                        page(Destination.ReferencePitch, motion) {
                            ReferencePitchScreen(onDone = { navController.popBackStack() })
                        }
                        page(Destination.Licenses, motion) { LicensesScreen() }
                    }
                }
            }
            if (railSide == RailSide.Right && tabChromeShown) {
                SideRail(lastTab, selectTab, railSide, railReserved, motion, spots, railInsets)
            }
        }

        // Both top bars sit over the full width, where each lies on its own page. Their containers
        // are clear (the page color shows through), and the Settings button is left to the
        // travelling button below, over an empty slot.
        Box(
            Modifier
                .fillMaxWidth()
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                }
                // In portrait both pages have a bar, so it keeps the opaque backing it always had.
                .then(
                    if (landscape) {
                        Modifier
                    } else {
                        Modifier.drawBehind { drawRect(lerp(tabPageColor, settingsPageColor, motion.progress)) }
                    },
                ),
        ) {
            if (!landscape && tabChromeShown) {
                TopAppBar(
                    title = { slots.title?.invoke() ?: Text(lastTab.title) },
                    // The tab's title and actions clear out quickly as the button sets off.
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - (motion.progress / TabBarFadeOutEnd).coerceIn(0f, 1f)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        slots.actions?.invoke(this)
                        ButtonSlot { spots.tab = it.centerInRoot() }
                    },
                )
            }
            if (settingsBarShown) {
                // Settings and its sub-pages: a centred title between Back and the page's edge.
                CenterAlignedTopAppBar(
                    title = { Text(lastSettingsPage.title) },
                    // The title arrives as the arrow lands.
                    modifier = Modifier.graphicsLayer {
                        alpha = ((motion.progress - SettingsTitleFadeInStart) / (1f - SettingsTitleFadeInStart))
                            .coerceIn(0f, 1f)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = { ButtonSlot { spots.settings = it.centerInRoot() } },
                )
            }
        }

        Box(
            Modifier.semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            },
        ) {
            SettingsBackButton(
                motion = motion,
                spots = spots,
                // Fixed, not by distance: the rail's spot is further from Back than the bar's.
                turns = if (landscape) 2 else 1,
                settingsOpen = !current.topLevel,
                settingsContainer = settingsButtonColor,
                onClick = {
                    if (current.topLevel) {
                        navController.navigate(Destination.Settings.route)
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}

private val Destination.motionLevel: Float get() = if (topLevel) 0f else 1f

// A destination whose page reports its share of the tab <-> Settings motion.
private fun NavGraphBuilder.page(
    destination: Destination,
    motion: SettingsMotion,
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(destination.route) { entry ->
        motion.Track(this, entry.id, destination.motionLevel)
        content(entry)
    }
}

// The tab's bar is gone by this point of the motion; the Settings title starts from this one.
private const val TabBarFadeOutEnd = 0.25f
private const val SettingsTitleFadeInStart = 0.8f

/** The height of a top app bar (and the status bar above it), which the Scaffold pads the content by. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarSpace() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top))
            .height(TopAppBarDefaults.TopAppBarExpandedHeight),
    )
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
    side: RailSide,
    reserved: Boolean,
    motion: SettingsMotion,
    spots: ButtonSpots,
    windowInsets: WindowInsets,
) {
    NavigationRail(
        modifier = Modifier
            // Slides over the page, not under it.
            .zIndex(1f)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // Once Settings shows, the rail gives its room back to the page but stays in place,
                // sliding off its edge.
                val width = if (reserved) placeable.width else 0
                layout(width, placeable.height) {
                    placeable.place(if (side == RailSide.Right) width - placeable.width else 0, 0)
                }
            }
            .onGloballyPositioned {
                spots.railOuter = it
                spots.updateRail()
            }
            .graphicsLayer {
                val offEdge = size.width * motion.chromeOffEdge
                translationX = if (side == RailSide.Left) -offEdge else offEdge
            }
            .onGloballyPositioned {
                spots.railInner = it
                spots.updateRail()
            },
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            ButtonSlot {
                spots.railSlot = it
                spots.updateRail()
            }
        },
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
internal const val FadeOutMillis = 150
internal const val FadeInMillis = 300
internal const val PageTransitionMillis = FadeOutMillis + FadeInMillis

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

// Padding around the bar's row, so the outer pills line up with the screens' 16 dp card margin.
private val BarEdgePadding = 16.dp - TabInset

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
    BoxWithConstraints(modifier.padding(horizontal = BarEdgePadding)) {
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
