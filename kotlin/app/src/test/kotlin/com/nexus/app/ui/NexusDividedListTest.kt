package com.nexus.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 구분선 규칙 (#260) — **행 사이에만, 마지막 뒤에는 없다.**
 *
 * 마지막 뒤에 넣으면 카드 아래 여백과 겹쳐 두 겹 경계로 보인다. 눈으로는 잘 안 띄고, 호출부마다
 * 이 판단을 반복하면 어딘가는 틀린다 — 그래서 컴포넌트가 정하고 여기서 개수로 못박는다.
 *
 * 구분선은 시맨틱이 없어 태그로 센다([LIST_DIVIDER_TAG]).
 */
@RunWith(RobolectricTestRunner::class)
class NexusDividedListTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(count: Int) {
        composeRule.setContent {
            NexusTheme {
                NexusDividedList((1..count).toList()) { Text("행 $it") }
            }
        }
    }

    private fun dividerCount() = composeRule.onAllNodesWithTag(LIST_DIVIDER_TAG).fetchSemanticsNodes().size

    @Test
    fun `세 행이면 구분선은 둘`() {
        render(3)

        assertEquals(2, dividerCount(), "행 사이에만 — 마지막 뒤에는 없다")
    }

    @Test
    fun `한 행이면 구분선이 없다`() {
        render(1)

        assertEquals(0, dividerCount())
        composeRule.onNodeWithText("행 1").assertExists()
    }

    @Test
    fun `빈 목록이면 아무것도 그리지 않는다`() {
        // 빈 상태 문구는 호출부 책임 — 목록이 "없음"까지 정하면 화면마다 다른 문구를 못 쓴다
        render(0)

        assertEquals(0, dividerCount())
        composeRule.onNodeWithTag(LIST_DIVIDER_TAG).assertDoesNotExist()
    }

    @Test
    fun `모든 행이 그려진다`() {
        render(4)

        (1..4).forEach { composeRule.onNodeWithText("행 $it").assertExists() }
    }
}
