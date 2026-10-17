package __PKG__.core.ui.theme

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

// Brand palette. Screens never use these directly: they read MaterialTheme.colorScheme roles so
// light, dark and dynamic color all work.
private val Primary = Color(0xFF3557D6)
private val PrimaryDark = Color(0xFFB8C4FF)
private val Secondary = Color(0xFF5B5D72)
private val SecondaryDark = Color(0xFFC4C5DD)
private val Tertiary = Color(0xFF77536D)
private val TertiaryDark = Color(0xFFE7B9D6)

private val LightColors = lightColorScheme(primary = Primary, secondary = Secondary, tertiary = Tertiary)
private val DarkColors = darkColorScheme(primary = PrimaryDark, secondary = SecondaryDark, tertiary = TertiaryDark)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
