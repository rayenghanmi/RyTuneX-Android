package com.rayenghanmi.rytunex.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rayenghanmi.rytunex.R

/**
 * Google Sans (Product Sans) - the iconic typeface used across Google Play Store,
 * Pixel devices, and Google products.
 */
val GoogleSansFontFamily = FontFamily(
    Font(resId = R.font.google_sans_regular, weight = FontWeight.Normal),
    Font(resId = R.font.google_sans_regular, weight = FontWeight.Light),
    Font(resId = R.font.google_sans_regular, weight = FontWeight.Thin),
    Font(resId = R.font.google_sans_medium, weight = FontWeight.Medium),
    Font(resId = R.font.google_sans_medium, weight = FontWeight.SemiBold),
    Font(resId = R.font.google_sans_bold, weight = FontWeight.Bold),
    Font(resId = R.font.google_sans_bold, weight = FontWeight.ExtraBold),
    Font(resId = R.font.google_sans_bold, weight = FontWeight.Black),
)

// Aliases for compatibility across the codebase
val AppFontFamily = GoogleSansFontFamily
val RobotoFontFamily = GoogleSansFontFamily
val RobotoFlexFontFamily = GoogleSansFontFamily
val Material3ExpressiveFontFamily = GoogleSansFontFamily

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFontFamily,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    ),
)

// Alias for backward-compatibility across existing screens
val RyTuneXTypography = AppTypography
