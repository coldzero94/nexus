package com.nexus.app.onboarding

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.settings.GoalStore
import com.nexus.app.ui.NexusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 온보딩 내비게이션 (#225, E14-15) — 실제 화면을 태워 **뒤로 갔을 때 값이 남는지**까지 본다.
 *
 * Robolectric엔 Health Connect가 없어 `availability()`가 `Unavailable`이다 → 경로는 3단계
 * (WELCOME → SAMSUNG_HEALTH → WEEKLY_GOAL)이고 권한 설명은 건너뛴다. 그게 이 티켓에서 가장
 * 까다로운 경로라 오히려 검증 가치가 크다: **건너뛴 스텝으로 되돌아가면 안 된다.**
 *
 * ## 여기서 못 잡는 것
 *
 * 가용성이 **바뀌는** 경로는 이 하네스로 세울 수 없다(Robolectric은 항상 Unavailable이고
 * `HealthConnectManager`는 구체 클래스다). 그래서 `healthAvailable`을 `rememberSaveable`로 둔 이유 —
 * 복원 후 stage와 경로가 어긋나 "2 / 3"이 "3 / 4"가 되거나, RATIONALE이 복원됐는데 가용성이 false여서
 * **진행 표시도 뒤로가기도 사라지는** 상태 — 는 이 파일이 아니라 `OnboardingFlowTest`의
 * `경로 밖 스텝은 위치가 없다`·`경로 밖 스텝에서는 뒤로 갈 곳이 없다`가 그 결과를 고정한다.
 * 어긋남 자체를 막는 건 저장 상태이고, 그건 실기기 회전·프로세스 사망으로 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(onFinished: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            NexusTheme { OnboardingScreen(manager = HealthConnectManager(context), onFinished = onFinished) }
        }
    }

    private fun back() = composeRule.onNodeWithContentDescription(string(R.string.onboarding_back))

    /**
     * 진행 표시는 한 시맨틱 노드로 묶여 있어(#224 규칙) 안쪽 숫자 텍스트가 트리에 없다.
     * 그래서 단언도 스크린리더가 보는 노드로 한다.
     */
    private fun progress(current: Int, total: Int) =
        composeRule.onNodeWithContentDescription(string(R.string.a11y_onboarding_progress, current, total))

    @Test
    fun `첫 스텝은 1단계이고 뒤로가 없다`() {
        render()

        progress(1, 3).assertIsDisplayed()
        back().assertDoesNotExist()
    }

    @Test
    fun `다음을 누르면 2단계로 가고 뒤로가 생긴다`() {
        render()

        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()

        progress(2, 3).assertIsDisplayed()
        back().assertIsDisplayed()
    }

    /**
     * HC 미가용 경로에서 2단계는 삼성헬스 안내다. 뒤로 가면 **권한 설명이 아니라 환영**이어야 한다 —
     * 건너뛴 스텝으로 되돌리면 권한 요청이 실패하는 화면에 갇힌다.
     */
    @Test
    fun `뒤로 가면 건너뛴 스텝이 아니라 첫 스텝으로 돌아간다`() {
        render()
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()

        back().performClick()

        progress(1, 3).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.onboarding_welcome_title)).assertIsDisplayed()
        // 권한 설명은 이 경로에 없다
        composeRule.onNodeWithText(string(R.string.permission_rationale_title)).assertDoesNotExist()
    }

    @Test
    fun `마지막 스텝까지 진행 번호가 이어진다`() {
        render()
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()

        composeRule.onNodeWithText(string(R.string.samsung_health_done)).performClick()

        progress(3, 3).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.onboarding_goal_title)).assertIsDisplayed()
    }

    /**
     * AC의 핵심: **뒤로 이동해도 선택한 목표가 남는다.** 목표 선택을 스텝 컴포저블 안에 두면 뒤로
     * 갔다 오는 사이 컴포지션을 떠나 초기값으로 돌아가고, 사용자는 방금 고른 걸 다시 고르게 된다.
     */
    @Test
    fun `뒤로 갔다 와도 고른 목표가 남는다`() {
        val initial = GoalStore(context).weeklyGoalDays
        val picked = if (initial == PICK_A) PICK_B else PICK_A
        render()
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()
        composeRule.onNodeWithText(string(R.string.samsung_health_done)).performClick()
        composeRule.onNodeWithText(string(R.string.goal_days_format, picked)).performClick()

        back().performClick()
        composeRule.onNodeWithText(string(R.string.samsung_health_done)).performClick()

        // 확정 버튼을 눌러 저장된 값으로 확인한다 — 선택 상태는 시맨틱에 직접 드러나지 않는다
        composeRule.onNodeWithText(string(R.string.onboarding_goal_confirm)).performClick()
        assertEquals(picked, GoalStore(context).weeklyGoalDays, "뒤로 갔다 오는 사이 선택이 초기화됐다")
    }

    /**
     * AC ④ — 회전·프로세스 사망에도 스텝·진행이 유지된다.
     *
     * [StateRestorationTester]는 저장 상태만 남기고 컴포지션을 버렸다 다시 만든다(프로세스 사망 후
     * 복원과 같은 경로). **진행 번호까지** 확인하는 게 중요하다: 스텝만 `rememberSaveable`이고 경로
     * 계산의 입력이 아니면, 복원 후 "2 / 3"이 "3 / 4"로 바뀌는 어긋남이 생긴다.
     */
    @Test
    fun `복원 후에도 스텝과 진행 번호가 유지된다`() {
        val restorer = StateRestorationTester(composeRule)
        restorer.setContent {
            NexusTheme { OnboardingScreen(manager = HealthConnectManager(context), onFinished = {}) }
        }
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()
        progress(2, 3).assertIsDisplayed()

        restorer.emulateSavedInstanceStateRestore()

        progress(2, 3).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.samsung_health_done)).assertIsDisplayed()
    }

    @Test
    fun `복원 후에도 고른 목표가 남는다`() {
        val initial = GoalStore(context).weeklyGoalDays
        val picked = if (initial == PICK_A) PICK_B else PICK_A
        val restorer = StateRestorationTester(composeRule)
        restorer.setContent {
            NexusTheme { OnboardingScreen(manager = HealthConnectManager(context), onFinished = {}) }
        }
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()
        composeRule.onNodeWithText(string(R.string.samsung_health_done)).performClick()
        composeRule.onNodeWithText(string(R.string.goal_days_format, picked)).performClick()

        restorer.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(string(R.string.onboarding_goal_confirm)).performClick()
        assertEquals(picked, GoalStore(context).weeklyGoalDays, "복원 후 선택이 초기화됐다")
    }

    @Test
    fun `목표를 확정하면 온보딩이 끝난다`() {
        var finished: Boolean? = null
        render(onFinished = { finished = it })
        composeRule.onNodeWithText(string(R.string.onboarding_next)).performClick()
        composeRule.onNodeWithText(string(R.string.samsung_health_done)).performClick()

        composeRule.onNodeWithText(string(R.string.onboarding_goal_confirm)).performClick()

        // HC가 없으니 connected=false — 데모 모드로 끝나야 한다
        assertEquals(false, finished)
    }

    private companion object {
        const val PICK_A = 3
        const val PICK_B = 5
    }
}
