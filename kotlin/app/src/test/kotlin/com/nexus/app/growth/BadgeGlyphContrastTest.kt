package com.nexus.app.growth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.junit4.createComposeRule
import com.nexus.app.ui.NexusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/**
 * 배지 상태 채널 대비 (#266) — **형태 채널이 실제로 보이는지**.
 *
 * ## 이 테스트가 잡은 것
 *
 * 처음 구현은 미획득 링을 `outlineVariant`로, 획득 원반을 `primaryContainer` 채움만으로 그렸다.
 * 실측하니 카드 서피스(`surfaceContainerLow`) 대비 **1.53:1 / 1.16:1**이었다 — 라이트 테마에서
 * 링도 원반 경계도 사실상 보이지 않는다. 즉 "채운 원반 vs 빈 원반"이라는, 색을 못 보는 사용자를
 * 위한 안전장치가 **존재하지 않았다**. 색 하나에 상태 전부가 걸려 있던 셈이다.
 *
 * `VizColorsContrastTest`가 있는 이유와 같다 — 눈에 안 보이는 회귀는 테스트만 잡는다.
 *
 * ## 기준
 *
 * `docs/DESIGN.md §5`의 비텍스트 UI 3:1. 배지 원반은 차트가 아니지만 **상태를 형태로 전달하는
 * 비텍스트 요소**라 같은 기준을 적용한다.
 */
@RunWith(RobolectricTestRunner::class)
class BadgeGlyphContrastTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 배지 행이 실제로 앉는 서피스와 그 위에 놓이는 색들. */
    private data class Palette(
        val card: Color,
        val lockedRing: Color,
        val lockedGlyph: Color,
        val earnedDisc: Color,
        val earnedRing: Color,
        val earnedGlyph: Color,
    )

    /**
     * 라이트·다크 팔레트를 **한 번의 `setContent`로** 잡는다 — `setContent`는 테스트당 한 번만
     * 호출할 수 있어서, 테마별로 나눠 부르면 두 번째에서 터진다.
     */
    private fun palettes(): Map<Boolean, Palette> {
        val captured = mutableMapOf<Boolean, Palette>()
        composeRule.setContent {
            listOf(false, true).forEach { dark ->
                NexusTheme(useDarkTheme = dark) {
                    val s = MaterialTheme.colorScheme
                    captured[dark] = Palette(
                        card = s.surfaceContainerLow,
                        lockedRing = s.outline,
                        lockedGlyph = s.onSurfaceVariant,
                        earnedDisc = s.primaryContainer,
                        earnedRing = s.primary,
                        earnedGlyph = s.onPrimaryContainer,
                    )
                }
            }
        }
        return captured
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** 비텍스트 UI AA. */
    private val minContrast = 3.0

    private fun assertStateChannelsVisible(dark: Boolean) {
        val p = palettes().getValue(dark)
        val theme = if (dark) "다크" else "라이트"

        // 형태 채널 — 양쪽 링이 카드 위에서 보여야 '채움 vs 윤곽'이 성립한다
        assertTrue(
            contrast(p.lockedRing, p.card) >= minContrast,
            "$theme: 미획득 링이 카드에서 안 보인다 — 빈 원반이라는 형태 신호가 사라진다 " +
                "(${contrast(p.lockedRing, p.card)})",
        )
        assertTrue(
            contrast(p.earnedRing, p.card) >= minContrast,
            "$theme: 획득 원반 경계가 안 보인다 — 채움과 윤곽이 같은 모양이 된다 " +
                "(${contrast(p.earnedRing, p.card)})",
        )

        // 글리프 — 미획득은 감쇠 후의 실제 색으로 잰다(알파를 무시하면 통과하는 척한다)
        val fadedGlyph = p.lockedGlyph.copy(alpha = LOCKED_GLYPH_ALPHA).compositeOver(p.card)
        assertTrue(
            contrast(fadedGlyph, p.card) >= minContrast,
            "$theme: 미획득 글리프가 너무 흐리다 — LOCKED_GLYPH_ALPHA를 낮췄나 (${contrast(fadedGlyph, p.card)})",
        )
        assertTrue(
            contrast(p.earnedGlyph, p.earnedDisc) >= minContrast,
            "$theme: 획득 글리프가 원반에서 안 보인다 (${contrast(p.earnedGlyph, p.earnedDisc)})",
        )
    }

    @Test
    fun `라이트 - 획득·미획득 상태 채널이 모두 보인다`() = assertStateChannelsVisible(dark = false)

    @Test
    fun `다크 - 획득·미획득 상태 채널이 모두 보인다`() = assertStateChannelsVisible(dark = true)

    /**
     * 두 상태의 글리프가 서로도 구분돼야 한다. 링이 같은 굵기라 글리프 명도가 두 번째 단서다.
     */
    @Test
    fun `획득 글리프와 미획득 글리프가 서로 구분된다`() {
        palettes().forEach { (dark, p) ->
            val faded = p.lockedGlyph.copy(alpha = LOCKED_GLYPH_ALPHA).compositeOver(p.card)
            val theme = if (dark) "다크" else "라이트"

            assertTrue(
                contrast(p.earnedGlyph, faded) >= 1.5,
                "$theme: 획득/미획득 글리프가 같은 밝기다 (${contrast(p.earnedGlyph, faded)})",
            )
        }
    }
}
