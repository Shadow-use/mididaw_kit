package com.example.mididaw.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Фірмовий колір застосунку — використовується як fallback на пристроях
// без Material You (Android < 12) або якщо динамічний колір вимкнено.
private val BrandPurple = Color(0xFF5E35B1)
private val BrandPurpleLight = Color(0xFFB39DDB)

private val FallbackDarkColors = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    secondary = BrandPurpleLight
)

private val FallbackLightColors = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    secondary = BrandPurpleLight
)

/**
 * Тема всього застосунку (кнопки, меню, діалоги, фон екрана) — бере
 * кольори з теми телефону (Material You, Android 12+) і слідує
 * системному світлому/темному режиму. На старіших версіях Android
 * використовує статичну фіолетову тему як fallback.
 *
 * Полотно піано-ролу (сама сітка нот) навмисно лишається темним завжди —
 * це окреме дизайнерське рішення для зручності редагування нот, як у
 * більшості DAW-застосунків.
 */
@Composable
fun MidiDawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDarkColors
        else -> FallbackLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
