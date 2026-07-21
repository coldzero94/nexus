package com.nexus.app.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 브랜드 컬러 스킴 (#251, E16-1) — 따뜻·다정한 톤의 앰버(성장 동료) 시드에서 파생한 정적 M3 롤.
 * 캐릭터의 앰버(#FFB74D)와 어울리는 warm primary + 성장의 sage-green tertiary. 두 모드 AA 대비.
 * 색은 오직 여기서만 정의하고 화면은 MaterialTheme 토큰만 참조한다(하드코딩 Color 금지 규칙).
 *
 * 값은 Material 표준 톤 규약(primary=T40·onPrimary=T100·container=T90·onContainer=T10, 다크는 반전)을
 * 따르는 앰버 시드 팔레트다. 다이내믹 컬러 정책은 [NexusTheme] KDoc·docs/DESIGN.md 참고.
 */
internal val NexusLightColors = lightColorScheme(
    primary = Color(0xFF7C5800),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF271900),
    secondary = Color(0xFF6C5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E0BB),
    onSecondaryContainer = Color(0xFF251A04),
    tertiary = Color(0xFF4E6542),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0EBBF),
    onTertiaryContainer = Color(0xFF0C2005),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    outline = Color(0xFF7F7767),
    outlineVariant = Color(0xFFD0C5B4),
    // surfaceContainer 계열 — Card(surfaceContainerLow)·NavigationBar(surfaceContainer) 기본 배경.
    // 미지정 시 M3 기본 라벤더로 폴백하므로 warm 중성 톤으로 채운다(#251 리뷰).
    surfaceDim = Color(0xFFE3D7C8),
    surfaceBright = Color(0xFFFFF8F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF1E4),
    surfaceContainer = Color(0xFFF7EBDE),
    surfaceContainerHigh = Color(0xFFF1E6D8),
    surfaceContainerHighest = Color(0xFFEBE0D3),
)

internal val NexusDarkColors = darkColorScheme(
    primary = Color(0xFFF1BF6D),
    onPrimary = Color(0xFF422C00),
    primaryContainer = Color(0xFF5F4100),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFD8C4A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF53452A),
    onSecondaryContainer = Color(0xFFF5E0BB),
    tertiary = Color(0xFFB4CFA4),
    onTertiary = Color(0xFF213619),
    tertiaryContainer = Color(0xFF374D2E),
    onTertiaryContainer = Color(0xFFD0EBBF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17130B),
    onBackground = Color(0xFFEAE1D2),
    surface = Color(0xFF17130B),
    onSurface = Color(0xFFEAE1D2),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD0C5B4),
    outline = Color(0xFF99917F),
    outlineVariant = Color(0xFF4D4639),
    // warm 다크 중성 surface 계열 — 카드·내비바가 M3 기본이 아닌 브랜드 톤으로 (#251 리뷰).
    surfaceDim = Color(0xFF17130B),
    surfaceBright = Color(0xFF3E3830),
    surfaceContainerLowest = Color(0xFF110E07),
    surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF241F16),
    surfaceContainerHigh = Color(0xFF2E2920),
    surfaceContainerHighest = Color(0xFF39342A),
)
