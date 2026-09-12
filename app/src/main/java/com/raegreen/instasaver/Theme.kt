package com.raegreen.instasaver

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Charcoal background with a slightly lifted surface tone, so list rows read as sitting
// just above the page rather than floating flat against pure black on OLED
private val Charcoal = Color(0xFF16161A)
private val ElevatedSurface = Color(0xFF202027)

// Warm amber accent for buttons, progress indicators and highlights, with dark text/icons
// on top of it for contrast
private val Amber = Color(0xFFF5B14C)
private val OnAmber = Color(0xFF1B1500)

// Near-white for primary content, muted grey for secondary lines like timestamps and sizes
private val TextPrimary = Color(0xFFF2F2F2)
private val TextSecondary = Color(0xFF98979E)

// A true red, well clear of amber's hue, so failures never read as just another accent
private val ErrorRed = Color(0xFFEF4444)
private val OnErrorRed = Color(0xFF2A0605)

// Visible but unobtrusive borders for outlined fields and buttons
private val Outline = Color(0xFF4A4A52)

private val InstaSaverColorScheme = darkColorScheme(
    background = Charcoal,
    onBackground = TextPrimary,
    surface = ElevatedSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = TextSecondary,
    primary = Amber,
    onPrimary = OnAmber,
    secondary = Amber,
    onSecondary = OnAmber,
    error = ErrorRed,
    onError = OnErrorRed,
    outline = Outline,
)

// Always dark by design — the owner chose a fixed palette rather than following the
// system setting, so there is no light scheme to keep in sync
@Composable
fun InstaSaverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InstaSaverColorScheme,
        content = content,
    )
}
