package com.omnituner.android.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnituner.android.R
import com.omnituner.android.ui.theme.currentWebPalette

data class WebSelectOption<T>(
    val value: T,
    val label: String,
    val alt: String? = null,
    val dotColor: Color? = null,
)

@Composable
fun WebSettingGroup(
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val groupAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.3f else 1f,
        label = "groupDim",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(groupAlpha)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}

@Composable
fun WebSettingDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(0.dp),
    )
}

@Composable
fun <T> WebSelectRow(
    label: String,
    value: String,
    options: List<WebSelectOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    valueDotColor: Color? = null,
    enabled: Boolean = true,
    itemTrailing: (@Composable RowScope.(WebSelectOption<T>) -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val palette = currentWebPalette()
    val chevronRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        label = "chevron",
    )

    Column(modifier = modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(enabled = enabled) {
                    val newOpen = !open
                    open = newOpen
                    onExpandedChange?.invoke(newOpen)
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (valueDotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(valueDotColor, CircleShape),
                        )
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.tabler_chevron_down),
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(option.value) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                palette.muted
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (option.alt != null) {
                            Text(
                                text = option.alt,
                                fontSize = 11.sp,
                                color = palette.dim,
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
}

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
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
