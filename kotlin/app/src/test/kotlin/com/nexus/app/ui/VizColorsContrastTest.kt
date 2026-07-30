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

/**
 * 카테고리 색 CVD 인접쌍 분리 검증 (#263 AC③) — 성향 비중바에서 **붙어 있는 두 세그먼트**가
 * 색약 시야에서도 구분되는지.
 *
 * ## 왜 인접쌍만인가
 *
 * 스택 바에서 혼동이 실제 문제가 되는 건 **맞닿은 구획**이다. 걷기|달리기와 달리기|근력이 각각
 * 구분되면 바는 세 덩어리로 읽힌다. 걷기와 근력은 서로 붙지 않으므로(순서가 고정이다) 그 쌍이
 * 비슷해도 바를 오독하게 만들지 않는다.
 *
 * ## 두 채널 중 하나면 통과
 *
 * 색조(CVD 시뮬레이션 거리)와 **명도**(WCAG 대비비) 둘 중 하나가 충분하면 구분된다. 이색형 시야에서
 * 색조가 붕괴해도 명도차가 남으면 두 덩어리로 보이기 때문이다 — 접근성 팔레트의 표준 기법이다.
 * 둘 다 미달인 쌍만 실패로 잡는다.
 *
 * ## 이 테스트가 실제로 팔레트를 고쳤다
 *
 * 처음 실측에서 **라이트 걷기|달리기가 두 채널 모두 미달**이었다: 이색형 색조 거리 0.025,
 * 명도 대비 1.01:1(사실상 같은 밝기). 두 색이 다 따뜻한 중간톤이라 적록 색약에서 한 덩어리로
 * 보였다는 뜻이다. 임계값을 낮춰 통과시키는 대신 **색조를 유지하고 명도만 내려**(8A6D2E → 6E5724)
 * 명도 채널로 분리했다 — 지금은 1.42:1이다. 서피스 대비도 3.96→5.59로 함께 올랐다.
 *
 * ## 완료 기준의 '미달 시 라벨 2차 인코딩'과의 관계
 *
 * 라벨은 이미 **1차와 동등한 채널**로 들어가 있다([com.nexus.app.growth.AxisShareStackBar]의 범례는
 * 항상 라벨+퍼센트를 병기하고, 지배 성향은 굵기로도 표시한다). 그래서 이 테스트는 "라벨을 넣을지"의
 * 판단이 아니라 **색 채널도 제 몫을 하는가**의 검증이고, 실패하면 팔레트를 고치는 신호다.
 *
 * ## 시뮬레이션 방법
 *
 * Viénot·Brettel·Mollon(1999)의 선형 RGB 투영 행렬. 정확한 지각 모델은 아니지만, "두 색이 색약
 * 시야에서 같은 색으로 붕괴하는가"를 잡는 데는 충분한 표준 근사다. 감마는 sRGB 근사(2.2)로 푼다.
 */
class VizCategoryCvdTest {
    private enum class Cvd { PROTANOPIA, DEUTERANOPIA, TRITANOPIA }

    private fun toLinear(c: Float) = c.toDouble().let { if (it <= 0.0) 0.0 else Math.pow(it, GAMMA) }

    private fun toSrgb(v: Double) = Math.pow(v.coerceIn(0.0, 1.0), 1.0 / GAMMA).toFloat()

    /** Viénot 1999 — 선형 sRGB 공간에서의 이색형 투영. */
    private fun simulate(color: Color, kind: Cvd): Color {
        val r = toLinear(color.red)
        val g = toLinear(color.green)
        val b = toLinear(color.blue)
        val (nr, ng, nb) = when (kind) {
            Cvd.PROTANOPIA -> Triple(
                0.1121 * r + 0.8853 * g - 0.0005 * b,
                0.1127 * r + 0.8897 * g - 0.0001 * b,
                0.0045 * r + 0.0000 * g + 1.0019 * b,
            )

            Cvd.DEUTERANOPIA -> Triple(
                0.2920 * r + 0.7054 * g - 0.0003 * b,
                0.2934 * r + 0.7089 * g + 0.0000 * b,
                -0.0209 * r + 0.0270 * g + 0.9754 * b,
            )

            Cvd.TRITANOPIA -> Triple(
                1.0175 * r + 0.0000 * g - 0.0100 * b,
                0.0000 * r + 0.9995 * g + 0.0000 * b,
                -0.0578 * r + 0.9722 * g + 0.0000 * b,
            )
        }
        return Color(toSrgb(nr), toSrgb(ng), toSrgb(nb))
    }

    /** 선형 채널 유클리드 거리 — 붕괴(≈0) 여부 판정이 목적이라 정밀 색차식은 과하다. */
    private fun hueDistance(a: Color, b: Color): Double {
        val dr = toLinear(a.red) - toLinear(b.red)
        val dg = toLinear(a.green) - toLinear(b.green)
        val db = toLinear(a.blue) - toLinear(b.blue)
        return Math.sqrt(dr * dr + dg * dg + db * db)
    }

    /** WCAG 상대 휘도. */
    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * 색조 채널 임계. 이색형 투영 후에도 이 정도 떨어져 있으면 색으로 구분된다.
     * 현 팔레트에서 통과하는 쌍들은 0.12~0.38이고, 붕괴 쌍은 0.03 이하다.
     */
    private val minHueDistance = 0.10

    /**
     * 명도 채널 임계. 색조가 붕괴해도 이 대비면 두 덩어리로 보인다.
     * 비텍스트 3:1을 요구하지 않는 이유는 이건 서피스 대비가 아니라 **인접 요소끼리**의 구분이라서다.
     */
    private val minAdjacentContrast = 1.35

    /** 비중바에서 실제로 맞닿는 쌍 — 순서는 walk → run → strength로 고정돼 있다. */
    private fun adjacentPairs(dark: Boolean): List<Triple<String, Color, Color>> {
        val viz = VizColors.palette(dark)
        return listOf(
            Triple("걷기|달리기", viz.walking, viz.running),
            Triple("달리기|근력", viz.running, viz.strength),
        )
    }

    private fun assertAdjacentPairsSeparate(dark: Boolean) {
        val theme = if (dark) "다크" else "라이트"
        adjacentPairs(dark).forEach { (name, a, b) ->
            val worstHue = Cvd.entries.minOf { hueDistance(simulate(a, it), simulate(b, it)) }
            val contrast = contrastRatio(a, b)
            assertTrue(
                worstHue >= minHueDistance || contrast >= minAdjacentContrast,
                "$theme 테마 $name 세그먼트가 색약 시야에서 한 덩어리가 된다 — " +
                    "색조 $worstHue (최소 $minHueDistance), 명도 대비 $contrast (최소 $minAdjacentContrast)",
            )
        }
    }

    @Test
    fun `라이트 - 인접 세그먼트가 색약 시야에서도 분리된다`() = assertAdjacentPairsSeparate(dark = false)

    @Test
    fun `다크 - 인접 세그먼트가 색약 시야에서도 분리된다`() = assertAdjacentPairsSeparate(dark = true)

    /**
     * 라이트 걷기|달리기는 **명도 채널로만** 통과한다 — 색조는 여전히 붕괴한다.
     *
     * 이걸 명시적으로 고정하는 이유: 누군가 걷기 색을 원래 밝기로 되돌리면 위 테스트가 실패하는데,
     * 그때 "왜 이 색이 이 밝기여야 하는가"의 근거가 여기 남아 있어야 한다.
     */
    @Test
    fun `라이트 걷기와 달리기는 명도로 분리된다`() {
        val viz = VizColors.palette(dark = false)
        val worstHue = Cvd.entries.minOf { hueDistance(simulate(viz.walking, it), simulate(viz.running, it)) }

        assertTrue(worstHue < minHueDistance, "색조가 이미 충분하다 — 이 테스트의 전제가 바뀌었다 (실측 $worstHue)")
        assertTrue(
            contrastRatio(viz.walking, viz.running) >= minAdjacentContrast,
            "걷기 색을 밝게 되돌렸다 — 색조가 붕괴하는 쌍이라 명도차가 유일한 구분 채널이다",
        )
    }

    @Test
    fun `걷기 색을 낮춰도 서피스 대비 3대1을 유지한다`() {
        // #258의 계약 — 카테고리색은 히어로 서피스 대비 3:1↑
        val heroSurfaceLight = Color(0xFFF1E6D8)

        assertTrue(
            contrastRatio(VizColors.palette(dark = false).walking, heroSurfaceLight) >= 3.0,
            "걷기 막대가 서피스에서 안 보인다 (#258)",
        )
    }

    @Test
    fun `걷기 강조와 물러난 톤이 여전히 구분된다`() {
        // 걷기 색을 낮추면 walkingMuted와 가까워질 수 있다 — 오늘/과거 막대 구분이 #258의 요점이다
        val viz = VizColors.palette(dark = false)

        assertTrue(
            contrastRatio(viz.walking, viz.walkingMuted) >= minAdjacentContrast,
            "오늘 막대와 과거 막대가 구분되지 않는다 (#258)",
        )
    }

    @Test
    fun `시뮬레이션이 실제로 색을 바꾼다`() {
        // 시뮬레이션이 항등함수면 위 테스트들이 원본 색만 비교하며 조용히 통과한다
        val red = Color(0xFFCC3333)
        Cvd.entries.forEach { kind ->
            assertTrue(hueDistance(red, simulate(red, kind)) > 0.0, "$kind 시뮬레이션이 색을 바꾸지 않는다")
        }
    }

    @Test
    fun `두 채널 모두 미달인 쌍은 실패로 잡힌다`() {
        // 임계값이 의미 있는지 — 원래 걷기 색과 달리기가 정확히 그 경우였다
        val oldWalking = Color(0xFF8A6D2E)
        val running = VizColors.palette(dark = false).running
        val worstHue = Cvd.entries.minOf { hueDistance(simulate(oldWalking, it), simulate(running, it)) }

        assertTrue(worstHue < minHueDistance, "색조 임계가 너무 낮다 (실측 $worstHue)")
        assertTrue(
            contrastRatio(oldWalking, running) < minAdjacentContrast,
            "명도 임계가 너무 낮아 원래의 붕괴 쌍도 통과한다 (실측 ${contrastRatio(oldWalking, running)})",
        )
    }

    private companion object {
        const val GAMMA = 2.2
    }
}
