package com.nexus.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.ui.NexusTheme
import com.nexus.core.HealthAvailability
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 데모 랜딩 3상태 렌더 (#236) — **고칠 수 있는 상태를 막다른 길로 보이지 않게** 하는 게 요점.
 *
 * 갤럭시 프리인스톨 Health Connect는 구버전인 경우가 흔하다. 예전엔 가용성을 이진으로 뭉개
 * `UPDATE_REQUIRED`를 "이 기기에서 쓸 수 없음"으로 접었고, 테스터는 업데이트 한 번으로 될 일을
 * 영구 불가로 이해했다. 12명 표본에서 이런 오탈락은 전환 지표를 직접 훼손한다.
 *
 * 실기기 없이 세 상태를 다 그릴 수 있다 — #320이 화면 렌더 테스트를 열어준 덕이다.
 */
@RunWith(RobolectricTestRunner::class)
class DemoLandingRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun render(availability: HealthAvailability) {
        composeRule.setContent { NexusTheme { DemoLanding(availability) {} } }
    }

    private fun string(id: Int) = context.getString(id)

    @Test
    fun `업데이트 필요면 업데이트 CTA를 준다`() {
        render(HealthAvailability.UpdateRequired)

        composeRule.onNodeWithText(string(R.string.status_update_required_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_update_health_connect)).assertIsDisplayed()
        // '영구 불가' 문구가 섞이면 안 된다 — 그게 이 티켓이 고치는 오해다
        composeRule.onNodeWithText(string(R.string.status_unavailable_title)).assertDoesNotExist()
    }

    @Test
    fun `진짜 불가면 액션 없는 안내만`() {
        render(HealthAvailability.Unavailable)

        composeRule.onNodeWithText(string(R.string.status_unavailable_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_update_health_connect)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.action_retry_permission)).assertDoesNotExist()
    }

    @Test
    fun `가용하면 권한 재연결을 준다`() {
        render(HealthAvailability.Available)

        composeRule.onNodeWithText(string(R.string.status_demo_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry_permission)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_update_health_connect)).assertDoesNotExist()
    }
}
