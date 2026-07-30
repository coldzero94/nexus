package com.nexus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 카드 강조 3단계가 **서로 다른 색**인지 (#254·#263).
 *
 * 이게 왜 테스트할 만한 일인가: #263에서 성장 히어로를 `Highlight`로 올렸더니 축하 카드(#61)가
 * 하드코딩으로 쓰던 `primaryContainer`와 **같은 색**이 됐다. 레벨업 직후 성장 탭에 같은 색 카드
 * 두 장이 붙어, 축하가 이벤트가 아니라 '중복된 헤더'로 읽혔다. 강조 단계가 색으로 구분되지 않으면
 * 위계 시스템 자체가 무의미하다.
 */
@RunWith(RobolectricTestRunner::class)
class CardEmphasisTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun containerColors(dark: Boolean): List<Color> {
        val colors = mutableListOf<Color>()
        composeRule.setContent {
            NexusTheme(useDarkTheme = dark) {
                CardEmphasis.entries.forEach { emphasis -> colors += emphasis.colors().containerColor }
            }
        }
        return colors
    }

    private fun assertAllDistinct(dark: Boolean) {
        val colors = containerColors(dark)
        val theme = if (dark) "다크" else "라이트"

        assertEquals(
            CardEmphasis.entries.size,
            colors.distinct().size,
            "$theme 테마에서 카드 강조 단계가 같은 색을 쓴다: $colors — 위계가 색으로 읽히지 않는다",
        )
    }

    @Test
    fun `라이트 - 세 강조 단계가 모두 다른 색이다`() = assertAllDistinct(dark = false)

    @Test
    fun `다크 - 세 강조 단계가 모두 다른 색이다`() = assertAllDistinct(dark = true)
}
