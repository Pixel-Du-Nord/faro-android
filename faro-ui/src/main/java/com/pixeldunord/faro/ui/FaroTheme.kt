package com.pixeldunord.faro.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Light, dark, or whatever the system says. */
public enum class FaroDarkMode {
    /** Follow the system setting. */
    SYSTEM,

    /** Always light. */
    LIGHT,

    /** Always dark. */
    DARK,
}

/**
 * The look of the ready-made screen.
 *
 * By default it wears Faro's own colors (a lighthouse: amber light over sand by day, over deep sea by
 * night). Pass your brand color as [accent] and it builds both palettes around it; or pass full
 * [lightColors]/[darkColors]; or set [useHostTheme] to inherit the `MaterialTheme` your app already
 * provides.
 *
 * ```kotlin
 * FaroHelpScreen(
 *     onClose = { navController.popBackStack() },
 *     theme = FaroTheme(accent = Color(0xFF6D28D9), cornerRadius = 20.dp, fontFamily = myFont),
 * )
 * ```
 *
 * @property accent The color of what matters: the send button, the chosen kind, focus, the progress
 *   beam. Text on it is black or white, whichever reads better.
 * @property darkMode Light, dark or the system's.
 * @property cornerRadius Roundness of inputs, cards and buttons (0.dp for square).
 * @property fontFamily A font for every text, keeping Material's sizes.
 * @property typography A full Material 3 typography (wins over [fontFamily]).
 * @property lightColors A full light palette (wins over [accent]).
 * @property darkColors A full dark palette (wins over [accent]).
 * @property useHostTheme Use the surrounding `MaterialTheme` as is (only for the composable).
 */
@Immutable
public data class FaroTheme(
    val accent: Color = FaroAmber,
    val darkMode: FaroDarkMode = FaroDarkMode.SYSTEM,
    val cornerRadius: Dp = 14.dp,
    val fontFamily: FontFamily? = null,
    val typography: Typography? = null,
    val lightColors: ColorScheme? = null,
    val darkColors: ColorScheme? = null,
    val useHostTheme: Boolean = false,
) {
    public companion object {
        /** Faro's amber, the lighthouse light. */
        public val FaroAmber: Color = Color(0xFFE39A0B)
    }
}

/** Applies [theme] around [content]; tells [content] whether it is dark. */
@Composable
internal fun ProvideFaroTheme(theme: FaroTheme, content: @Composable (dark: Boolean) -> Unit) {
    val dark = when (theme.darkMode) {
        FaroDarkMode.SYSTEM -> isSystemInDarkTheme()
        FaroDarkMode.LIGHT -> false
        FaroDarkMode.DARK -> true
    }
    if (theme.useHostTheme) {
        content(MaterialTheme.colorScheme.background.luminance() < 0.4f)
        return
    }
    val colors = remember(theme, dark) {
        if (dark) theme.darkColors ?: darkScheme(theme.accent) else theme.lightColors ?: lightScheme(theme.accent)
    }
    val base = MaterialTheme.typography
    val typography = remember(theme.typography, theme.fontFamily, base) {
        theme.typography ?: theme.fontFamily?.let { base.withFont(it) } ?: base
    }
    val shapes = remember(theme.cornerRadius) {
        val r = theme.cornerRadius
        Shapes(
            extraSmall = RoundedCornerShape(r / 3),
            small = RoundedCornerShape(r * 2 / 3),
            medium = RoundedCornerShape(r),
            large = RoundedCornerShape(r * 4 / 3),
            extraLarge = RoundedCornerShape(r * 2),
        )
    }
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) { content(dark) }
}

/** Black-ish or white on [color], whichever has more contrast (WCAG ratio). */
internal fun onAccent(color: Color): Color {
    val l = color.luminance()
    val dark = Color(0xFF14110A)
    val withDark = (l + 0.05f) / (dark.luminance() + 0.05f)
    val withWhite = 1.05f / (l + 0.05f)
    return if (withDark >= withWhite) dark else Color.White
}

/** Faro by day: sand, paper cards, navy ink; [accent] for what matters. */
internal fun lightScheme(accent: Color): ColorScheme {
    val ink = Color(0xFF17213A)
    val paper = Color(0xFFFFFDF8)
    val sand = Color(0xFFF4F1EA)
    return lightColorScheme(
        primary = accent,
        onPrimary = onAccent(accent),
        primaryContainer = accent.copy(alpha = 0.16f).compositeOver(paper),
        onPrimaryContainer = ink,
        secondary = Color(0xFF3B5B8C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1E8F3),
        onSecondaryContainer = ink,
        background = sand,
        onBackground = ink,
        surface = sand,
        onSurface = ink,
        surfaceVariant = Color(0xFFE9E4D8),
        onSurfaceVariant = Color(0xFF55607A),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = paper,
        surfaceContainer = Color(0xFFF9F6EF),
        surfaceContainerHigh = Color(0xFFEFEADF),
        surfaceContainerHighest = Color(0xFFE7E1D3),
        outline = Color(0xFF8A90A0),
        outlineVariant = Color(0xFFD9D3C5),
        error = Color(0xFFC0362C),
        onError = Color.White,
        errorContainer = Color(0xFFFBE3E0),
        onErrorContainer = Color(0xFF5F120C),
        inverseSurface = ink,
        inverseOnSurface = sand,
    )
}

/** Faro by night: the deep sea, slate cards, pale text; [accent] is the light. */
internal fun darkScheme(accent: Color): ColorScheme {
    val sea = Color(0xFF0C1322)
    val card = Color(0xFF141D30)
    val text = Color(0xFFE8ECF4)
    return darkColorScheme(
        primary = accent,
        onPrimary = onAccent(accent),
        primaryContainer = accent.copy(alpha = 0.22f).compositeOver(card),
        onPrimaryContainer = text,
        secondary = Color(0xFF9DB6DD),
        onSecondary = sea,
        secondaryContainer = Color(0xFF22324F),
        onSecondaryContainer = text,
        background = sea,
        onBackground = text,
        surface = sea,
        onSurface = text,
        surfaceVariant = Color(0xFF1F2A40),
        onSurfaceVariant = Color(0xFFA7B1C6),
        surfaceContainerLowest = Color(0xFF080D18),
        surfaceContainerLow = card,
        surfaceContainer = Color(0xFF172136),
        surfaceContainerHigh = Color(0xFF1C273D),
        surfaceContainerHighest = Color(0xFF243049),
        outline = Color(0xFF6E7A92),
        outlineVariant = Color(0xFF2B3750),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF3B0905),
        errorContainer = Color(0xFF4A1612),
        onErrorContainer = Color(0xFFFFDAD5),
        inverseSurface = text,
        inverseOnSurface = sea,
    )
}

private fun Typography.withFont(font: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = font)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

/**
 * The accent as TEXT on the screen's surfaces. A light accent (Faro's amber, a yellow brand color)
 * does not read on sand, so it is deepened towards the ink until it does.
 */
internal fun readableAccent(colors: ColorScheme, dark: Boolean): Color {
    val accent = colors.primary
    if (dark) return if (accent.luminance() < 0.12f) androidx.compose.ui.graphics.lerp(accent, Color.White, 0.45f) else accent
    var color = accent
    var step = 0
    while (color.luminance() > 0.22f && step < 6) {
        color = androidx.compose.ui.graphics.lerp(color, colors.onSurface, 0.18f)
        step++
    }
    return color
}
