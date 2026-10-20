package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Theo's material palette is deliberately achromatic: obsidian, smoke and neutral optical highlights.
// Colour is reserved for meaningful status feedback, never used to tint the glass itself.
val TheoDarkBackground = Color(0xFF070910)
val TheoSurfaceDeep = Color(0x66101420)
val TheoSurfaceCard = Color(0x54161D2B)
val TheoSurfaceGlass = Color(0x24E5EFFF)
val TheoSurfaceGlassLight = Color(0x28EAF3FF)
val TheoSurfaceBorder = Color(0x35DDEAFF)
val TheoSurfaceBorderSubtle = Color(0x1CDDEAFF)

// Historical accent tokens remain for source compatibility, but now resolve to neutral silver/charcoal.
val TheoCyan = Color(0xFF9BE8FF)
val TheoIceBlue = Color(0xFFD3F0FF)
val TheoDeepCyan = Color(0xFF294B62)
val TheoViolet = Color(0xFFB6A8FF)
val TheoDeepViolet = Color(0xFF332D55)
val TheoMagenta = Color(0xFFE5BAFF)

// Semantic states only: ready, warning and failure remain distinguishable.
val TheoOnlineGreen = Color(0xFF72E7BA)
val TheoWarningAmber = Color(0xFFFFCD78)
val TheoErrorRed = Color(0xFFFF8295)

val TheoTextPrimary = Color(0xFFF5F5F7)
val TheoTextSecondary = Color(0xFFC9C9CE)
val TheoTextMuted = Color(0xFF9B9BA1)
val TheoTextCyan = Color(0xFF9BE8FF)
val TheoTextViolet = Color(0xFFC5B5FF)
