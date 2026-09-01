package com.omnituner.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omnituner.android.ui.theme.THEME_DARK
import com.omnituner.android.ui.theme.THEME_LIGHT
import com.omnituner.android.ui.theme.THEME_SYSTEM
import com.omnituner.android.ui.tuner.TunerSettingsSheet
import com.omnituner.android.ui.tuner.TunerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onBack: () -> Unit,
    tunerViewModel: TunerViewModel = viewModel(),
) {
    val tunerState by tunerViewModel.ui.collectAsState()
    var tunerSettingsOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Back" },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Theme", style = MaterialTheme.typography.bodyLarge)
            val themeOptions = listOf(
                THEME_SYSTEM to "System",
                THEME_LIGHT to "Light",
                THEME_DARK to "Dark",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeOptions.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = themeMode == value,
                        onClick = { onThemeModeChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = themeOptions.size,
                        ),
                    ) { Text(label) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { tunerSettingsOpen = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tuner settings", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Reference pitch, tolerance, in-tune feedback",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }

    if (tunerSettingsOpen) {
        TunerSettingsSheet(
            state = tunerState,
            onDismiss = { tunerSettingsOpen = false },
            viewModel = tunerViewModel,
        )
    }
}
