package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Theo's material palette is deliberately achromatic: obsidian, smoke and neutral optical highlights.
// Colour is reserved for meaningful status feedback, never used to tint the glass itself.
val TheoDarkBackground = Color(0xFF060607)
val TheoSurfaceDeep = Color(0x66131315)
val TheoSurfaceCard = Color(0x541D1D20)
val TheoSurfaceGlass = Color(0x24FFFFFF)
val TheoSurfaceGlassLight = Color(0x24FFFFFF)
val TheoSurfaceBorder = Color(0x30FFFFFF)
val TheoSurfaceBorderSubtle = Color(0x18FFFFFF)

// Historical accent tokens remain for source compatibility, but now resolve to neutral silver/charcoal.
val TheoCyan = Color(0xFFF5F5F6)
val TheoIceBlue = Color(0xFFE2E2E4)
val TheoDeepCyan = Color(0xFF555559)
val TheoViolet = Color(0xFFDADADD)
val TheoDeepViolet = Color(0xFF29292C)
val TheoMagenta = Color(0xFFECECEE)

// Semantic states only: ready, warning and failure remain distinguishable.
val TheoOnlineGreen = Color(0xFF72E7BA)
val TheoWarningAmber = Color(0xFFFFCD78)
val TheoErrorRed = Color(0xFFFF8295)

val TheoTextPrimary = Color(0xFFF5F5F7)
val TheoTextSecondary = Color(0xFFC9C9CE)
val TheoTextMuted = Color(0xFF9B9BA1)
val TheoTextCyan = Color(0xFFEAEAEC)
val TheoTextViolet = Color(0xFFDADADD)
