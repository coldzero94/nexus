package com.nexus.app.ui

import androidx.compose.ui.unit.dp

/**
 * 엘리베이션 토큰 (#253, E16-3) — 카드 배경은 톤 엘리베이션(surfaceContainer, #251)로 처리하므로
 * 그림자는 절제한다. 명시적 그림자가 필요한 표면(떠 있는 시트·다이얼로그 강조)만 이 스케일을 쓴다.
 */
object NexusElevation {
    val level0 = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val overlay = 6.dp
}
