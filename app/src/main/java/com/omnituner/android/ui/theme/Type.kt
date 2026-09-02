package com.omnituner.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.omnituner.android.R

@OptIn(ExperimentalTextApi::class)
private fun instrumentSans(weight: FontWeight) = Font(
    resId = R.font.instrument_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val InstrumentSans = FontFamily(
    instrumentSans(FontWeight.Normal),
    instrumentSans(FontWeight.Medium),
    instrumentSans(FontWeight.SemiBold),
    instrumentSans(FontWeight.Bold),
)

private val defaults = Typography()

val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = InstrumentSans),
    displayMedium = defaults.displayMedium.copy(fontFamily = InstrumentSans),
    displaySmall = defaults.displaySmall.copy(fontFamily = InstrumentSans),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = InstrumentSans),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = InstrumentSans),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = InstrumentSans),
    titleLarge = defaults.titleLarge.copy(fontFamily = InstrumentSans),
    titleMedium = defaults.titleMedium.copy(fontFamily = InstrumentSans),
    titleSmall = defaults.titleSmall.copy(fontFamily = InstrumentSans),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = InstrumentSans),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = InstrumentSans),
    bodySmall = defaults.bodySmall.copy(fontFamily = InstrumentSans),
    labelLarge = defaults.labelLarge.copy(fontFamily = InstrumentSans),
    labelMedium = defaults.labelMedium.copy(fontFamily = InstrumentSans),
    labelSmall = defaults.labelSmall.copy(fontFamily = InstrumentSans),
)

fun appTextStyle(block: TextStyle.() -> Unit): TextStyle =
    TextStyle(fontFamily = InstrumentSans).apply(block)
