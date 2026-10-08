package ir.itsetak.repair

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection

val Ink = Color(0xFF12151A)
val Red = Color(0xFFE5202B)
val Soft = Color(0xFFF3F5F8)

val VazirFamily = FontFamily(
    Font(R.font.vazir, FontWeight.Normal),
    Font(R.font.vazir_bold, FontWeight.Bold)
)

private fun typographyWith(f: FontFamily): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f),
        displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f),
        headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f),
        headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f),
        titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f),
        bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f),
        labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f)
    )
}

@Composable
fun SetakTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Red,
                onPrimary = Color.White,
                secondary = Ink,
                onSecondary = Color.White,
                background = Soft,
                surface = Color.White,
                onSurface = Ink,
                onBackground = Ink,
                error = Color(0xFFB91C1C)
            ),
            typography = typographyWith(VazirFamily),
            content = content
        )
    }
}

fun statusColor(name: String): Color = when (name) {
    "blue" -> Color(0xFF1D4ED8)
    "purple" -> Color(0xFF6D28D9)
    "orange" -> Color(0xFFB45309)
    "teal" -> Color(0xFF0F766E)
    "green" -> Color(0xFF047857)
    "red" -> Color(0xFFB91C1C)
    else -> Color(0xFF64748B)
}
