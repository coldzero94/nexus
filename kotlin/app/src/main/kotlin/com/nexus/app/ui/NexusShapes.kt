package com.nexus.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 브랜드 모양 (#252, E16-2) — M3 기본보다 살짝 둥근 코너로 다정한 톤. Card는 medium(16dp),
 * 버튼·칩은 small/full을 가져간다. 화면은 MaterialTheme.shapes 토큰만 참조(하드코딩 Shape 금지).
 */
internal val NexusShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
