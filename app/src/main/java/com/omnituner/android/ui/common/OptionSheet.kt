package com.omnituner.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// Every pull-down menu starts at the same width, whatever its labels, so menus don't jump in size
// between rows; long labels may widen it up to the max and then ellipsize. The check glyph at this
// box size is ~14 dp, matching a 14 dp leading dot.
private val MenuMinWidth = 220.dp
private val MenuMaxWidth = 280.dp
private val MenuCheckSize = 20.dp

/**
 * The app's one pull-down menu: every anchored menu (picker rows, the tuner's tuning menu, the
 * metronome's meter and preset menus) goes through this so they share width, shape and color.
 */
@Composable
fun PullDownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MenuDefaults.containerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = MenuMinWidth, max = MenuMaxWidth),
        shape = MaterialTheme.shapes.large,
        containerColor = containerColor,
        content = content,
    )
}

/**
 * A [PullDownMenu] row. The leading slot holds a check when [selected], or [icon] for action rows
 * ("New tuning…"), or stays empty so labels line up. [leading] is drawn just before the label (a
 * color dot), [supporting] under it, [trailing] at the end (e.g. edit/delete buttons).
 */
@Composable
fun PullDownMenuItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    icon: ImageVector? = null,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Column {
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (supporting != null) {
                        Text(
                            text = supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        leadingIcon = {
            when {
                selected -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(MenuCheckSize),
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(MenuCheckSize),
                )
                else -> Spacer(Modifier.size(MenuCheckSize))
            }
        },
        trailingIcon = trailing,
        onClick = onClick,
    )
}

/**
 * A section title inside a [PullDownMenu], aligned with the item labels. [emphasized] sets it larger
 * than the items, for titles that name what the items belong to (the tuner's instruments).
 */
@Composable
fun PullDownMenuSection(title: String, emphasized: Boolean = false) {
    Text(
        text = title,
        style = if (emphasized) {
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.labelLarge
        },
        color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun PullDownMenuDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

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
                PullDownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = menuColor) {
                    options.forEach { option ->
                        PullDownMenuItem(
                            text = option.label,
                            selected = option.key == selected,
                            supporting = option.supporting,
                            leading = leading?.let { draw -> { draw(option.key) } },
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
            options.groupBy { it.section }.entries.forEachIndexed { index, (section, group) ->
                if (section != null) {
                    // A divider and a bolder title, so a section header can't be mistaken for the
                    // grey supporting line of the option above it.
                    item(key = "section-$section") {
                        Column {
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            Text(
                                text = section,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
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
