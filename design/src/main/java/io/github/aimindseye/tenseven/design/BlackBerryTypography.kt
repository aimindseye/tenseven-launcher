package io.github.aimindseye.tenseven.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Adobe Source Sans 3, SIL OFL 1.1; see design/SourceSans3-OFL.txt. Android supplies script fallback. */
private val BlackBerryFont =
    FontFamily(
        Font(R.font.source_sans_light, FontWeight.Light),
        Font(R.font.source_sans_regular, FontWeight.Normal),
        Font(R.font.source_sans_semibold, FontWeight.SemiBold),
    )

/** Keep the responsive type scale and user font size; change only the approved theme's family. */
fun blackBerryTypography(base: Typography): Typography =
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = BlackBerryFont),
        displayMedium = base.displayMedium.copy(fontFamily = BlackBerryFont),
        displaySmall = base.displaySmall.copy(fontFamily = BlackBerryFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = BlackBerryFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = BlackBerryFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = BlackBerryFont),
        titleLarge = base.titleLarge.copy(fontFamily = BlackBerryFont),
        titleMedium = base.titleMedium.copy(fontFamily = BlackBerryFont),
        titleSmall = base.titleSmall.copy(fontFamily = BlackBerryFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = BlackBerryFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = BlackBerryFont),
        bodySmall = base.bodySmall.copy(fontFamily = BlackBerryFont),
        labelLarge = base.labelLarge.copy(fontFamily = BlackBerryFont),
        labelMedium = base.labelMedium.copy(fontFamily = BlackBerryFont),
        labelSmall = base.labelSmall.copy(fontFamily = BlackBerryFont),
    )
