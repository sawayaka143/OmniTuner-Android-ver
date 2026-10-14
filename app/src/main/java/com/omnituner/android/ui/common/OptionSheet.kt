package com.omnituner.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class PickerOption<T>(
    val key: T,
    val label: String,
    val supporting: String? = null,
    val section: String? = null,
)

@Composable
fun PickerRow(
    headline: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(headline) },
        trailingContent = {
            // ListItem's trailing slot defaults to labelSmall; values read at row size.
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}

// Every MenuPickerRow menu is the same width, whatever its labels, so menus don't jump in size
// between rows. The check glyph at this box size is ~14 dp, matching a 14 dp leading dot.
private val MenuWidth = 220.dp
private val MenuCheckSize = 20.dp

/**
 * A row whose value opens an anchored menu (iOS-style pull-down) instead of a bottom sheet: the
 * right fit for short lists. The menu hangs off the value, the selected option gets a check, and
 * [leading] (e.g. a color dot) is drawn before the label both in the row and in the menu.
 */
@Composable
fun <T> MenuPickerRow(
    headline: String,
    options: List<PickerOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    menuColor: Color = MenuDefaults.containerColor,
    leading: (@Composable (T) -> Unit)? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.key == selected }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(headline) },
        trailingContent = {
            // The menu anchors to this Box, so it opens under the value rather than the headline.
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current != null) leading?.invoke(current.key)
                    Text(
                        text = current?.label.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = open,
                    onDismissRequest = { open = false },
                    modifier = Modifier.width(MenuWidth),
                    shape = MaterialTheme.shapes.large,
                    containerColor = menuColor,
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    leading?.invoke(option.key)
                                    Text(option.label)
                                }
                            },
                            leadingIcon = {
                                if (option.key == selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(MenuCheckSize),
                                    )
                                } else {
                                    Spacer(Modifier.size(MenuCheckSize))
                                }
                            },
                            onClick = {
                                open = false
                                onSelect(option.key)
                            },
                        )
                    }
                }
            }
        },
        modifier = modifier.clickable { open = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<PickerOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    trailing: (@Composable (T) -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            options.groupBy { it.section }.forEach { (section, group) ->
                if (section != null) {
                    item(key = "section-$section") {
                        Text(
                            text = section,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(group) { option ->
                    OptionItem(
                        option = option,
                        selected = option.key == selected,
                        onSelect = onSelect,
                        trailing = trailing,
                    )
                }
            }
        }
        footer?.invoke(this)
    }
}

@Composable
private fun <T> OptionItem(
    option: PickerOption<T>,
    selected: Boolean,
    onSelect: (T) -> Unit,
    trailing: (@Composable (T) -> Unit)?,
) {
    val supportingContent: (@Composable () -> Unit)? = option.supporting?.let { text ->
        val content: @Composable () -> Unit = { Text(text) }
        content
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(option.label) },
        supportingContent = supportingContent,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke(option.key)
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        modifier = Modifier.clickable { onSelect(option.key) },
    )
}
