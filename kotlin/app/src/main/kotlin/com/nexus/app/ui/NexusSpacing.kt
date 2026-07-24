package com.nexus.app.ui

import androidx.compose.ui.unit.dp

/**
 * 간격 토큰 (#253, E16-3) — 8pt 기반 스케일. 화면·카드의 패딩·섹션 간격을 이 스케일로 통일해
 * 세로 리듬을 일관되게 한다(치수 하드코딩 금지 규칙의 단일 원천, CLAUDE.md·docs/DESIGN.md).
 * 값이 아니라 이름(의미)으로 참조한다 — `NexusSpacing.lg`.
 */
object NexusSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 화면 최상위 콘텐츠 인셋 — 전 화면 통일(홈·활동·성장·설정·온보딩). */
    val screen = 20.dp
}
