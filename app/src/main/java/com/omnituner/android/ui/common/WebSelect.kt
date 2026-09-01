package com.omnituner.android.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omnituner.android.R
import com.omnituner.android.ui.theme.currentWebPalette

/**
 * One selectable entry in a WebSelectMenu (web .dropdown-item).
 * [alt] renders as the trailing muted text (.item-alt), [dotColor] as the
 * leading color swatch dot.
 */
data class WebSelectOption<T>(
    val value: T,
    val label: String,
    val alt: String? = null,
    val dotColor: Color? = null,
)

private val MenuShape = RoundedCornerShape(16.dp)
private val ItemShape = RoundedCornerShape(8.dp)

/**
 * Web dropdown pill trigger + menu (web .dropdown-trigger / .dropdown-menu):
 * kicker label + semibold value + rotating chevron, opening an anchored
 * popover menu pinned under the row (ChatGPT-Android pattern: rounded surface,
 * checkmark on the selected entry, no scrim dimming the page behind it).
 */
@Composable
fun <T> WebSelectRow(
    label: String,
    value: String,
    options: List<WebSelectOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemTrailing: (@Composable RowScope.(WebSelectOption<T>) -> Unit)? = null,
    menuFooter: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }

    WebSelectTrigger(
        label = label,
        value = value,
        expanded = open,
        onClick = { open = true },
        modifier = modifier,
        enabled = enabled,
    )

    WebSelectMenu(
        expanded = open,
        options = options,
        selected = selected,
        onSelect = {
            open = false
            onSelect(it)
        },
        onDismiss = { open = false },
        itemTrailing = itemTrailing,
        footer = menuFooter,
    )
}

/** The pill trigger alone, for callers that host the menu themselves. */
@Composable
fun WebSelectTrigger(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = currentWebPalette()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick, role = Role.DropdownList)
            .semantics { contentDescription = "$label: $value" }
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = palette.dim,
                letterSpacing = 0.08.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing()
        Icon(
            painter = painterResource(R.drawable.tabler_chevron_down),
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier
                .size(20.dp)
                .rotate(chevronRotation),
        )
    }
}

/**
 * The anchored popover menu itself (web .dropdown-menu desktop presentation):
 * rounded surface-container-low panel with a hairline border and soft shadow,
 * items capped at 320dp with internal scrolling, footer slot for actions.
 */
@Composable
fun <T> WebSelectMenu(
    expanded: Boolean,
    options: List<WebSelectOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    itemTrailing: (@Composable RowScope.(WebSelectOption<T>) -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val palette = currentWebPalette()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = 0.dp, y = 4.dp),
        shape = MenuShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.widthIn(min = 220.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.dim,
                    letterSpacing = 0.08.em,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            options.forEach { option ->
                val isSelected = option.value == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .clip(ItemShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelect(option.value) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (option.dotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(option.dotColor, CircleShape),
                        )
                    }
                    Text(
                        text = option.label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) palette.accentText else palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (option.alt != null) {
                        Text(
                            text = option.alt,
                            fontSize = 10.sp,
                            color = palette.dim,
                            letterSpacing = 0.04.em,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (itemTrailing != null) itemTrailing(option)
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.tabler_check),
                            contentDescription = "Selected",
                            tint = palette.accentText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (footer != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                footer()
            }
        }
    }
}

/** Labeled switch row (web Toggle in a settings list). */
@Composable
fun WebToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}
