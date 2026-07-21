package com.nexus.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 브랜드 타이포 (#252, E16-2) — 따뜻·또렷한 위계. 시스템 폰트(무료, 유료 서체 금지 — OFL 번들은 후속)
 * 위에 앱이 실제 쓰는 롤(제목·본문·라벨)의 굵기·행간·자간을 브랜드 톤으로 조정한다. 한글 가독을 위해
 * 넉넉한 행간. 나머지 롤은 M3 기본(전부 동일 시스템 폰트라 정합). 화면은 MaterialTheme.typography만 참조.
 */
private val brandFont = FontFamily.Default

internal val NexusTypography = Typography(
    // 화면 제목 — 또렷한 앵커
    headlineSmall = TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    // 카드 제목
    titleMedium = TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    // 대사·강조 본문
    bodyLarge = TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.2.sp,
    ),
    // 일반 본문
    bodyMedium = TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    // 설명·보조
    bodySmall = TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    ),
    // 라벨·칩
    labelMedium = TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)
