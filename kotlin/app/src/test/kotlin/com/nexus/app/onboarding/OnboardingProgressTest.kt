package com.nexus.app.onboarding

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.ui.NexusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 온보딩 진행 표시 + 뒤로 어포던스 (#225, E14-15).
 *
 * 진행 표시는 점의 **색·길이**로 위치를 말한다 — 스크린리더에는 무의미하므로 행 전체를 한 노드로
 * 묶고 사람이 읽는 문장을 준다. 그 계약을 여기서 못박는다.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingProgressTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(current: Int, total: Int, onBack: (() -> Unit)? = null) {
        composeRule.setContent {
            NexusTheme { OnboardingProgress(current = current, total = total, onBack = onBack) }
        }
    }

    /**
     * 진행 표시는 `clearAndSetSemantics`로 **한 노드**다 — 점이 색·길이로만 위치를 말해서
     * 스크린리더에는 아무것도 아니기 때문이다(#224 게이지 규칙과 같은 형태). 그래서 안쪽 숫자
     * 텍스트는 시맨틱 트리에 없고, 단언도 **스크린리더가 보는 노드**로 한다.
     */
    @Test
    fun `진행 표시가 한 문장으로 낭독된다`() {
        render(current = 2, total = 4)

        composeRule.onNodeWithContentDescription(string(R.string.a11y_onboarding_progress, 2, 4))
            .assertIsDisplayed()
        // 안쪽 숫자는 묶음에 흡수됐다 — 이게 깨지면 같은 값이 두 번 낭독된다는 뜻이다
        composeRule.onNodeWithText(string(R.string.onboarding_progress, 2, 4)).assertDoesNotExist()
    }

    @Test
    fun `경로가 짧으면 전체도 그만큼만 낭독된다`() {
        // HC 미가용 기기는 3단계다 — "4단계 중"으로 들리면 없는 단계를 약속하는 셈이다
        render(current = 2, total = 3)

        composeRule.onNodeWithContentDescription(string(R.string.a11y_onboarding_progress, 2, 3))
            .assertIsDisplayed()
    }

    @Test
    fun `뒤로 콜백이 있으면 뒤로 버튼이 보인다`() {
        render(current = 2, total = 4, onBack = {})

        composeRule.onNodeWithContentDescription(string(R.string.onboarding_back)).assertIsDisplayed()
    }

    @Test
    fun `뒤로 콜백이 없으면 뒤로 버튼이 없다`() {
        // 첫 스텝 — 뒤로 갈 곳이 없는데 버튼을 두면 눌러도 아무 일이 없는 막다른 길이 된다
        render(current = 1, total = 4, onBack = null)

        composeRule.onNodeWithContentDescription(string(R.string.onboarding_back)).assertDoesNotExist()
    }

    @Test
    fun `뒤로 버튼을 누르면 콜백이 호출된다`() {
        var backs = 0
        render(current = 3, total = 4, onBack = { backs++ })

        composeRule.onNodeWithContentDescription(string(R.string.onboarding_back)).performClick()

        assertEquals(1, backs)
    }
}
