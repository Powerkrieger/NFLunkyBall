package com.example.nflunkyball.ui.theme

import androidx.compose.ui.graphics.Color

// Derived from the app icon (green bottle, amber ball, cream backdrop) rather than the default
// Material purple template — every role below is filled in explicitly (not left to M3's default
// purple/grey fallbacks) so there's no leftover template color anywhere in the app. Light-theme
// on-color pairs skew deliberately dark for contrast margin; dark-theme pairs skew light for the
// same reason — see the dynamic-color-disabled fix in Theme.kt for why contrast here has burned
// this app before.

// Bottle green (primary)
val GreenPrimaryLight = Color(0xFF146C43)
val OnGreenPrimaryLight = Color(0xFFFFFFFF)
val GreenPrimaryContainerLight = Color(0xFFA6F1C6)
val OnGreenPrimaryContainerLight = Color(0xFF002111)

val GreenPrimaryDark = Color(0xFF7CD6A5)
val OnGreenPrimaryDark = Color(0xFF00391E)
val GreenPrimaryContainerDark = Color(0xFF00522E)
val OnGreenPrimaryContainerDark = Color(0xFFA6F1C6)

// Amber ball (secondary)
val AmberSecondaryLight = Color(0xFF93590A)
val OnAmberSecondaryLight = Color(0xFFFFFFFF)
val AmberSecondaryContainerLight = Color(0xFFFFDDB0)
val OnAmberSecondaryContainerLight = Color(0xFF2E1800)

val AmberSecondaryDark = Color(0xFFFFB870)
val OnAmberSecondaryDark = Color(0xFF4B2700)
val AmberSecondaryContainerDark = Color(0xFF6C3B00)
val OnAmberSecondaryContainerDark = Color(0xFFFFDDB0)

// Warm brown (tertiary — used sparingly)
val BrownTertiaryLight = Color(0xFF5C4326)
val OnBrownTertiaryLight = Color(0xFFFFFFFF)
val BrownTertiaryContainerLight = Color(0xFFE8CDA6)
val OnBrownTertiaryContainerLight = Color(0xFF211200)

val BrownTertiaryDark = Color(0xFFD7C0A0)
val OnBrownTertiaryDark = Color(0xFF332000)
val BrownTertiaryContainerDark = Color(0xFF493109)
val OnBrownTertiaryContainerDark = Color(0xFFE8CDA6)

// Warm cream neutrals (background/surface)
val CreamBackgroundLight = Color(0xFFFBF7EF)
val OnCreamBackgroundLight = Color(0xFF1B1C18)
val CreamSurfaceVariantLight = Color(0xFFE7E3D3)
val OnCreamSurfaceVariantLight = Color(0xFF48473C)
val CreamOutlineLight = Color(0xFF79786B)

val CreamBackgroundDark = Color(0xFF14150F)
val OnCreamBackgroundDark = Color(0xFFE4E2D6)
val CreamSurfaceVariantDark = Color(0xFF48473C)
val OnCreamSurfaceVariantDark = Color(0xFFC9C6B8)
val CreamOutlineDark = Color(0xFF928F80)

// Standard M3 error roles — kept as Material's own baseline red (not palette-tinted) since
// "red = danger" is already relied on throughout (Abandon tournament, Revoked status, etc.) and
// shouldn't compete visually with the amber accent.
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)
