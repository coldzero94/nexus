package com.nexus.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/**
 * 행 규격 계약 (#260) — 가장 중요한 단언은 **라벨과 값이 한 노드로 들린다**는 것이다.
 *
 * 값을 오른쪽 열로 옮긴 게 이 티켓의 시각적 개선인데, 그냥 옮기면 시맨틱 트리에서 라벨과 값이
 * 흩어져 TalkBack이 `"42분"`을 맥락 없이 맨 뒤에 읽는다 — `docs/A11Y-TALKBACK.md`가 금지하는
 * 형태이고, 눈으로 보는 리뷰로는 절대 안 잡힌다.
 */
@RunWith(RobolectricTestRunner::class)
class NexusListRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        label: String = "11월 15일 09:33",
        supporting: String? = "걷기 · 심박 없음",
        value: String? = "42분",
        withContent: Boolean = false,
    ) {
        composeRule.setContent {
            NexusTheme {
                NexusListRow(
                    label = label,
                    supporting = supporting,
                    value = value,
                    content = if (withContent) {
                        { Text("B등급") }
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun tree() = composeRule.onRoot().printToString(maxDepth = 20)

    @Test
    fun `라벨과 보조와 값이 한 노드로 합쳐진다`() {
        render()

        // 합쳐지면 셋이 같은 노드의 Text 목록에 함께 나온다. 흩어지면 각각 별 노드가 된다.
        val merged = tree().lines().firstOrNull { line ->
            "11월 15일 09:33" in line && "42분" in line
        }
        assertTrue(merged != null, "라벨과 값이 별개 노드로 흩어졌다 — TalkBack이 값을 맥락 없이 읽는다\n${tree()}")
    }

    @Test
    fun `본문 슬롯은 묶음 밖에 남는다`() {
        // 신뢰 등급 칩은 탭 대상(#222) — 함께 묶으면 그 동작이 흡수된다
        render(withContent = true)

        val mergedLine = tree().lines().firstOrNull { "11월 15일 09:33" in it && "42분" in it }
        assertTrue(mergedLine != null)
        assertTrue("B등급" !in mergedLine, "본문 슬롯이 라벨 묶음에 흡수됐다 — 칩의 탭 동작이 사라진다")
        composeRule.onNodeWithText("B등급").assertIsDisplayed()
    }

    @Test
    fun `값이 없으면 라벨만 그린다`() {
        render(value = null, supporting = null)

        composeRule.onNodeWithText("11월 15일 09:33").assertIsDisplayed()
        composeRule.onNodeWithText("42분").assertDoesNotExist()
    }

    @Test
    fun `보조 설명이 없으면 한 줄 행이다`() {
        render(supporting = null)

        composeRule.onNodeWithText("걷기 · 심박 없음").assertDoesNotExist()
        composeRule.onNodeWithText("11월 15일 09:33").assertIsDisplayed()
    }

    /**
     * 긴 값이 들어와도 라벨이 살아남는지. 값에 `weight`가 없으면 값이 먼저 측정돼 라벨 열을
     * 0폭으로 짜부실 수 있다 — 좁은 폭에서만 나는 회귀라 폭을 강제해 본다.
     */
    @Test
    fun `값이 아주 길어도 라벨이 사라지지 않는다`() {
        composeRule.setContent {
            NexusTheme {
                Box(Modifier.width(200.dp)) {
                    NexusListRow(label = "11월 15일 09:33", value = "9".repeat(60) + "분")
                }
            }
        }

        composeRule.onNodeWithText("11월 15일 09:33").assertIsDisplayed()
    }
}
