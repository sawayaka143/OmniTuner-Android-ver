package com.omnituner.android.ui.tuner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omnituner.android.ui.common.GroupedSection

private const val APACHE_2_URL = "https://www.apache.org/licenses/LICENSE-2.0"

private data class Library(val name: String, val author: String)

// Hand-kept list of the runtime dependencies of :app and :shared (debug/test-only artifacts are
// left out). All of them are Apache 2.0; update this when a dependency is added or removed.
private val libraries = listOf(
    Library("AndroidX Activity", "The Android Open Source Project"),
    Library("AndroidX Core", "The Android Open Source Project"),
    Library("AndroidX DataStore", "The Android Open Source Project"),
    Library("AndroidX Graphics Shapes", "The Android Open Source Project"),
    Library("AndroidX Lifecycle", "The Android Open Source Project"),
    Library("AndroidX Navigation", "The Android Open Source Project"),
    Library("Jetpack Compose", "The Android Open Source Project"),
    Library("Compose Material 3", "The Android Open Source Project"),
    Library("Compose Material Icons", "The Android Open Source Project"),
    Library("Kotlin Standard Library", "JetBrains"),
    Library("kotlinx.coroutines", "JetBrains"),
    Library("kotlinx.serialization", "JetBrains"),
)

@Composable
fun LicensesScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        GroupedSection(title = "Libraries") {
            libraries.forEach { library ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text("${library.author} · Apache License 2.0") },
                )
            }
        }

        GroupedSection(title = "License") {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Apache License 2.0") },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { openInBrowser(context, APACHE_2_URL) },
            )
        }
    }
}
