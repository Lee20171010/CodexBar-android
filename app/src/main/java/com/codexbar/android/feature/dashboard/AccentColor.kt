package com.codexbar.android.feature.dashboard

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.codexbar.android.R

/** Mac accent-color equivalents; keys are persisted in DisplayOptions.accent. */
val ACCENT_COLORS = listOf(
    "blue" to Color(0xFF0A84FF),
    "purple" to Color(0xFFBF5AF2),
    "pink" to Color(0xFFFF375F),
    "red" to Color(0xFFFF453A),
    "orange" to Color(0xFFFF9F0A),
    "yellow" to Color(0xFFFFCC00),
    "green" to Color(0xFF30D158),
    "graphite" to Color(0xFF8E8E93)
)

fun accentColor(key: String?): Color? = ACCENT_COLORS.firstOrNull { it.first == key }?.second

/** Localized labels for the accent keys in [ACCENT_COLORS]; unknown keys fall back to graphite. */
@StringRes
fun accentLabelRes(key: String): Int = when (key) {
    "blue" -> R.string.accent_blue
    "purple" -> R.string.accent_purple
    "pink" -> R.string.accent_pink
    "red" -> R.string.accent_red
    "orange" -> R.string.accent_orange
    "yellow" -> R.string.accent_yellow
    "green" -> R.string.accent_green
    else -> R.string.accent_graphite
}
