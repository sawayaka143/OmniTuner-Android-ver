package com.omnituner.android.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.omnituner.android.ui.theme.LocalGroupedListColors
import com.omnituner.android.ui.theme.SmoothShape

private val SectionShape = SmoothShape(20.dp)

/**
 * One section of an inset grouped list (iOS-style): a secondary-grey title over a card in the group
 * color, meant for a page in [LocalGroupedListColors]' page color (Settings, the metronome options).
 * Rows inside are transparent `ListItem`s so the card shows through; menus opened from them pass the
 * group color as `menuColor`.
 */
@Composable
fun GroupedSection(
    title: String?,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    titleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SectionShape,
            colors = CardDefaults.cardColors(containerColor = LocalGroupedListColors.current.group),
        ) {
            Column(content = content)
        }
    }
}
