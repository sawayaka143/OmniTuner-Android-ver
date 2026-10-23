package com.omnituner.android.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** What the current screen puts into the app's top bar; `AppRoot` owns the bar and reads these. */
@Stable
class TopBarSlots {
    var title: (@Composable () -> Unit)? by mutableStateOf(null)
    var actions: (@Composable RowScope.() -> Unit)? by mutableStateOf(null)
    internal var owner: Any? = null
}

val LocalTopBarSlots = staticCompositionLocalOf { TopBarSlots() }

/**
 * Lets a screen replace the top bar's title and/or add actions before the Settings button.
 * Only the screen that set the slots clears them, so a leaving screen disposing after the next one
 * has registered (during the tab transition) doesn't wipe the new controls.
 */
@Composable
fun TopBarContent(title: (@Composable () -> Unit)? = null, actions: (@Composable RowScope.() -> Unit)? = null) {
    val slots = LocalTopBarSlots.current
    val owner = remember { Any() }
    SideEffect {
        slots.owner = owner
        slots.title = title
        slots.actions = actions
    }
    DisposableEffect(slots) {
        onDispose {
            if (slots.owner === owner) {
                slots.owner = null
                slots.title = null
                slots.actions = null
            }
        }
    }
}
