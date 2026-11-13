package com.novaaforgee.novacapture.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Sky400 = Color(0xFF38BDF8)
val Sky500 = Color(0xFF0EA5E9)
val Rose500 = Color(0xFFF43F5E)
val Emerald400 = Color(0xFF34D399)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

val NovaColorScheme = darkColorScheme(
    primary = Sky400,
    onPrimary = Slate900,
    primaryContainer = Slate800,
    onPrimaryContainer = Sky400,
    secondary = Rose500,
    background = Slate900,
    surface = Slate800,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)
