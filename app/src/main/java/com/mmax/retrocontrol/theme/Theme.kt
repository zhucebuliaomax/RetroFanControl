@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mmax.retrocontrol.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalRippleThemeConfiguration
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleThemeConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme = expressiveLightColorScheme()

private val OutlineFocusRingConfiguration = RippleThemeConfiguration(
  focus = RippleThemeConfiguration.Focus.InsetRing(
    outerStrokeInset = 0.dp,
    outerStrokeWidth = 2.dp,
    innerStrokeInset = 2.dp,
    innerStrokeWidth = 2.dp,
  ),
)

@Composable
fun RetroControlTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window ?: return@SideEffect
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    motionScheme = MotionScheme.expressive(),
    typography = Typography,
  ) {
    CompositionLocalProvider(
      LocalRippleThemeConfiguration provides OutlineFocusRingConfiguration,
      content = content,
    )
  }
}
