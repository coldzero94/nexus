package com.nexus.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.nexus.core.AmbienceSlot
import com.nexus.core.Season
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 앰비언스 워시가 **대비를 깨지 않는지** (#115, E16-19).
 *
 * 라이트/다크 × 시간대 4 × 계절 4 = **32조합**이다. 손으로 볼 수 없는 수라, 조합을 전부 돌려
 * 본문 대비를 검사한다. 배경은 시맨틱으로도 픽셀로도 관측할 수 없어(#338) 그리기 결정을 순수
 * 함수로 끌어낸 게 이걸 가능하게 한다.
 *
 * 이 테스트가 없으면 "밤 + 겨울 + 다크"처럼 특정 조합에서만 본문이 안 읽히는 상태를 그 시각·그 계절에
 * 실기기로 만나야만 발견한다.
 */
class AmbienceContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble() + 0.05
        val l2 = b.luminance().toDouble() + 0.05
        return max(l1, l2) / min(l1, l2)
    }

    private data class Scheme(val label: String, val surface: Color, val onSurface: Color)

    // 화면 여백(surface)과 **히어로 카드**(surfaceContainerHigh) 둘 다 워시를 받는다 (#115).
    // 히어로가 실제로 보이는 쪽이라 여기가 빠지면 대비 보장이 절반만 도는 셈이다.
    private val schemes = listOf(
        Scheme("라이트/배경", NexusLightColors.surface, NexusLightColors.onSurface),
        Scheme("다크/배경", NexusDarkColors.surface, NexusDarkColors.onSurface),
        Scheme("라이트/히어로", NexusLightColors.surfaceContainerHigh, NexusLightColors.onSurface),
        Scheme("다크/히어로", NexusDarkColors.surfaceContainerHigh, NexusDarkColors.onSurface),
    )

    private fun combos(): List<Triple<Scheme, AmbienceSlot, Season>> =
        schemes.flatMap { s -> AmbienceSlot.entries.flatMap { t -> Season.entries.map { Triple(s, t, it) } } }

    /** 본문 대비 4.5:1(WCAG AA)을 어느 조합에서도 깨지 않는다. */
    @Test
    fun `모든 조합에서 본문 대비가 유지된다`() {
        combos().forEach { (scheme, slot, season) ->
            val bg = AmbienceColors.wash(
                surface = scheme.surface,
                slot = AmbienceColors.tint(slot),
                season = AmbienceColors.seasonTint(season),
            )
            val ratio = contrast(bg, scheme.onSurface)

            assertTrue(ratio >= AA_TEXT, "${scheme.label}/$slot/$season 대비 ${"%.2f".format(ratio)}")
        }
    }

    /**
     * 워시가 **보여야** 한다 — 대비만 지키려고 알파를 0으로 만들면 테스트는 통과하지만 기능이 없다.
     * 표면과 눈에 띄게 다르되(양성 대조) 과하지 않은(위 테스트) 구간에 있어야 한다.
     */
    @Test
    fun `워시가 표면과 구분된다`() {
        schemes.forEach { scheme ->
            val night = AmbienceColors.wash(
                surface = scheme.surface,
                slot = AmbienceColors.tint(AmbienceSlot.NIGHT),
                season = AmbienceColors.seasonTint(Season.WINTER),
            )

            val delta = abs(night.luminance() - scheme.surface.luminance())

            assertTrue(delta > MIN_VISIBLE_DELTA, "${scheme.label}: 워시가 표면과 사실상 같다 ($delta)")
        }
    }

    /**
     * 시간대끼리 **눈에 띄게** 달라야 한다.
     *
     * "다르다"만 보면 1/255 차이도 통과하는데, 실제로 그런 일이 있었다: 아침·저녁을 둘 다 주황으로
     * 잡았더니 실기기 페이지 배경이 RGB 5 차이라 사실상 같은 화면이었다(#115 실측). 세기를 올리면
     * 대비가 흔들리므로 구분은 **색상**으로 내고, 그 결과를 여기서 수치로 못 박는다.
     */
    @Test
    fun `시간대별 색이 눈에 띄게 다르다`() {
        schemes.forEach { scheme ->
            val byslot = AmbienceSlot.entries.associateWith { slot ->
                AmbienceColors.wash(
                    surface = scheme.surface,
                    slot = AmbienceColors.tint(slot),
                    season = AmbienceColors.seasonTint(Season.SPRING),
                )
            }

            byslot.entries.forEachIndexed { i, a ->
                byslot.entries.drop(i + 1).forEach { b ->
                    val distance = channelDistance(a.value, b.value)
                    assertTrue(
                        distance >= MIN_SLOT_DISTANCE,
                        "${scheme.label}: ${a.key}·${b.key} 거리 ${"%.4f".format(distance)} — 같은 화면으로 보인다",
                    )
                }
            }
        }
    }

    /** 채널별 차이의 합(0~3). 색상 차이를 세기 차이와 섞지 않고 보기 위한 단순 지표. */
    private fun channelDistance(a: Color, b: Color): Float =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    /** 알파 상한이 느슨해지면 위 대비 보장이 무너진다 — 상한 자체를 못 박는다. */
    @Test
    fun `워시 알파가 상한 안에 있다`() {
        assertTrue(AmbienceColors.WASH_ALPHA <= MAX_WASH_ALPHA, "시간대 워시가 너무 진하다")
        assertTrue(AmbienceColors.SEASON_ALPHA < AmbienceColors.WASH_ALPHA, "계절이 시간대를 덮는다")
    }

    private companion object {
        const val AA_TEXT = 4.5
        const val MIN_VISIBLE_DELTA = 0.002f
        const val MAX_WASH_ALPHA = 0.14f

        /** 실측 기준 — 9/255 ≈ 0.035를 세 채널 합으로 환산한 하한. */
        const val MIN_SLOT_DISTANCE = 0.03f
    }
}
