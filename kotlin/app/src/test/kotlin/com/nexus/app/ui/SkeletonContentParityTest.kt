package com.nexus.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.growth.GrowthContent
import com.nexus.app.growth.GrowthUiState
import com.nexus.core.ClassAffinity
import com.nexus.core.DayXpExplanation
import com.nexus.core.GrowthSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/**
 * AC ④ — 로딩→완료 **레이아웃 시프트 최소화** (#268).
 *
 * "스켈레톤 높이가 400px보다 크다"로는 이걸 볼 수 없다. Robolectric 기본 뷰포트가 470px이라
 * 스크롤 컨테이너 없이 재면 무엇을 그리든 470에 잘려 사실상 `470 > 400`을 단언하게 되고,
 * 실제로 성장 탭은 스켈레톤 430px 대 콘텐츠 880px로 **절반**이었다(리뷰 실측).
 *
 * 그래서 **같은 조건(프로덕션과 같은 스크롤 컨테이너, 높이 무제한)** 에서 스켈레톤과 진짜 콘텐츠를
 * 나란히 재고 비율을 본다. 픽셀 일치는 필요 없다 — 완료 순간 화면이 크게 튀지 않을 만큼이면 된다.
 */
@RunWith(RobolectricTestRunner::class)
class SkeletonContentParityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun loading() = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getString(R.string.a11y_loading)

    @Test
    fun `성장 스켈레톤 높이가 실제 콘텐츠와 비슷하다`() {
        composeRule.setContent {
            NexusTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GrowthSkeleton()
                    // 루트는 뷰포트에 잘리므로 콘텐츠 높이는 태그로 직접 잰다
                    Column(Modifier.testTag(CONTENT_TAG)) { GrowthContent(contentState()) }
                }
            }
        }
        composeRule.waitForIdle()

        val skeleton = composeRule.onNodeWithContentDescription(loading()).fetchSemanticsNode().size.height
        val content = composeRule.onNodeWithTag(CONTENT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().size.height

        // 양성 대조 — 둘 다 실제로 그려졌는가. 0 대 0이면 비율 단언이 늘 참이다
        assertTrue(skeleton > 0 && content > 0, "스켈레톤 ${skeleton}px, 콘텐츠 ${content}px — 뭔가 안 그려졌다")
        assertTrue(
            skeleton >= content * LOWER && skeleton <= content * UPPER,
            "스켈레톤 ${skeleton}px 대 콘텐츠 ${content}px — 완료 순간 화면이 크게 튄다",
        )
    }

    /** 최소 성공 상태 — 카드 구성이 실제와 같아야 높이 비교가 의미를 갖는다. */
    private fun contentState() = GrowthUiState(
        summary = GrowthSummary(
            level = 3,
            totalXp = 520,
            progress = 0.4,
            affinity = ClassAffinity.BALANCED,
            axisShares = emptyMap(),
            stats = emptyMap(),
        ),
        today = DayXpExplanation(
            lines = emptyList(),
            rawPoints = 0,
            cappedXp = 0,
            kneeApplied = false,
            kneeReducedPoints = 0,
            hardCapped = false,
        ),
        awaitingFirstData = false,
    )

    private companion object {
        const val CONTENT_TAG = "growth-content"
        const val LOWER = 0.6
        const val UPPER = 1.6
    }
}
