package com.omnituner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.omnituner.android.ui.AppRoot
import com.omnituner.android.ui.theme.OmniTunerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OmniTunerApp).container
        setContent {
            val themeMode by container.theme.flow.collectAsState()
            OmniTunerTheme(themeMode = themeMode) {
                AppRoot()
            }
        }
    }
}
