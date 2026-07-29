package com.nexus.app.ui

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertTrue

/**
 * 컨디션 게이지 대비 회귀 방지 (#308) — 팔레트를 손대도 바닥 마커가 다시 사라지지 않게.
 *
 * 원래 결함: 마커를 `floorMarker` 단색으로 칠했는데 3존 채움색과 명도가 거의 같았다(라이트 0.02·
 * 다크 0.01 차). 채움 최소 폭이 한 캡이라 마커는 **항상 채움 위**에 놓이므로, 정상 상태에서 늘
 * 보이지 않았다. "20 아래로 안 내려가요"의 시각 증거가 통째로 죽어 있던 셈이다.
 *
 * 지금은 트랙색으로 그려 '채움에 파인 홈'으로 만든다. 이 테스트는 **트랙색이 모든 존 채움색과
 * 충분히 떨어져 있는지**를 고정한다 — 색을 조정하다 대비가 무너지면 여기서 잡힌다.
 */
class VizColorsContrastTest {
    /** 지각 명도 근사(NTSC luma) — 정확한 WCAG 대비가 아니라 '눈에 띄는가'의 대용치다. */
    private fun luma(color: Color): Float = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue

    /**
     * 최소 명도차. 결함 당시 값이 0.01~0.02였고 트랙색은 0.35 이상이라, 그 사이에서 여유 있게 잡는다.
     * 이보다 낮아지면 홈이 어떤 존이든 마커를 잃는다.
     */
    private val minDelta = 0.2f

    private fun assertTrackStandsOut(dark: Boolean) {
        val viz = VizColors.palette(dark)
        val zones = mapOf(
            "recovering" to viz.conditionRecovering,
            "stable" to viz.conditionStable,
            "good" to viz.conditionGood,
        )
        val theme = if (dark) "다크" else "라이트"
        zones.forEach { (name, fill) ->
            val delta = kotlin.math.abs(luma(viz.conditionTrack) - luma(fill))
            assertTrue(
                delta >= minDelta,
                "$theme 테마 '$name' 존에서 바닥 마커가 묻힌다 — 트랙색과 명도차 $delta (최소 $minDelta)",
            )
        }
    }

    @Test
    fun `라이트 - 트랙색이 모든 존 채움색과 구분된다`() = assertTrackStandsOut(dark = false)

    @Test
    fun `다크 - 트랙색이 모든 존 채움색과 구분된다`() = assertTrackStandsOut(dark = true)
}
