package com.nexus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 데이터 시각화 색 토큰 (#257, E16-7) — M3 [androidx.compose.material3.ColorScheme] 밖의 차트
 * 전용 팔레트. 차트 요소는 서피스 대비 3:1↑(비텍스트 UI 컴포넌트 AA)를 만족해야 해서 본문 텍스트용
 * on* 롤과 분리한다. 라이트/다크 각각 정의하고 [NexusTheme]가 다크 판정에 맞춰 주입한다.
 *
 * 공유 대상: 컨디션 게이지(#257) · 걸음 차트(#258) · XP 게이지(#259). 값 텍스트에는 이 색을
 * 재사용하지 않는다(가독 대비 확보 위해 ink=onSurface 사용, #257 AC).
 *
 * 컨디션 존은 무처벌 원칙상 **적색 계열 금지** — 회복중도 따뜻한 테라코타(경고가 아니라 '쉬는 중').
 */
@Immutable
data class VizPalette(
    /** 게이지 빈 트랙(채움 밖 구간) — 서피스보다 살짝 진한 컨테이너 톤. */
    val conditionTrack: Color,
    /** 회복중(< 40) — 따뜻한 테라코타(경고 아님). */
    val conditionRecovering: Color,
    /** 안정(40~70) — 브랜드 앰버. */
    val conditionStable: Color,
    /** 좋음(>= 70) — 세이지 그린(성장). */
    val conditionGood: Color,
    /** 바닥 마커 선 — 불퇴행 하한 표식. */
    val floorMarker: Color,
    /** 걷기 카테고리(#258~) — 강조(오늘) 막대. */
    val walking: Color,
    /** 걷기 물러난 톤(#258) — 과거 활동일 막대. 알파 감쇠는 라이트에서 3:1 붕괴라 **고정 톤**으로 정의. */
    val walkingMuted: Color,
    /** 러닝 카테고리(#258~). */
    val running: Color,
    /** 근력 카테고리(#258~). */
    val strength: Color,
)

/** 라이트 팔레트 — 히어로 서피스(surfaceContainerHigh #F1E6D8) 기준 3:1↑ 검증됨(#257). */
private val LightViz = VizPalette(
    conditionTrack = Color(0xFFDFCDB4),
    conditionRecovering = Color(0xFFB5652F),
    conditionStable = Color(0xFF8A6614),
    conditionGood = Color(0xFF4F7A2F),
    floorMarker = Color(0xFF7A5B2E),
    walking = Color(0xFF8A6D2E),
    walkingMuted = Color(0xFFA5854A),
    running = Color(0xFFB5562F),
    strength = Color(0xFF2F6E6A),
)

/** 다크 팔레트 — 히어로 서피스(surfaceContainerHigh #2E2920) 기준 3:1↑ 검증됨(#257). */
private val DarkViz = VizPalette(
    conditionTrack = Color(0xFF3B342A),
    conditionRecovering = Color(0xFFE8A468),
    conditionStable = Color(0xFFE9C56E),
    conditionGood = Color(0xFFA9CA80),
    floorMarker = Color(0xFFC9B48C),
    walking = Color(0xFFE6C271),
    walkingMuted = Color(0xFF9A7C4E),
    running = Color(0xFFEC9A6B),
    strength = Color(0xFF79C4BD),
)

/** [NexusTheme]가 다크 판정에 맞춰 [LightViz]/[DarkViz]를 주입. 기본값은 라이트. */
val LocalVizColors = staticCompositionLocalOf { LightViz }

/** 현 테마의 데이터 시각화 팔레트 — `VizColors.current`로 접근. */
object VizColors {
    val current: VizPalette
        @Composable @ReadOnlyComposable
        get() = LocalVizColors.current

    /** [NexusTheme] 주입용 — 다크면 [DarkViz], 아니면 [LightViz]. */
    internal fun palette(dark: Boolean): VizPalette = if (dark) DarkViz else LightViz
}
