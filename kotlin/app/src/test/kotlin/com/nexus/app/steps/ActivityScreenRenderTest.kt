package com.nexus.app.steps

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nexus.app.R
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.FIRST_RUN_NOTICE_TAG
import com.nexus.app.ui.NexusTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

/**
 * 활동 화면 로드 분기 전수 렌더 (#320, E15-22) — **실제 화면 컴포저블을 태운다.**
 *
 * 이전에는 이런 테스트를 쓸 수 없었다. 화면이 `HealthConnectManager`에서 리포지토리를 내부에서
 * 만들고 Robolectric엔 Health Connect가 없어, 무엇을 하든 미연결 분기만 렌더됐다. #213 리뷰가
 * "3분기 렌더 테스트가 화면을 안 타고 조건식을 로컬에 다시 구현했다"고 지적한 잠금이 이것이다.
 *
 * 이제 컨트롤러에 로드 상태를 주입해 각 분기를 세운다. 검증은 **화면에 실제로 그려진 노드**로 한다 —
 * 조건식이 아니라 결과를 본다.
 */
@RunWith(RobolectricTestRunner::class)
class ActivityScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        // 빈 상태의 '지금 확인'이 WorkManager를 구독한다 — 없으면 렌더가 터진다
        runCatching { WorkManager.getInstance(context) }.onFailure {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(Executor { }).build(),
            )
        }
    }

    /**
     * 로딩 분기(`load == null`)는 이 시임으로 세울 수 없다 — null은 "주입 없음"이라 실제 로드가 돌고,
     * repo가 null인 Robolectric에선 즉시 미연결로 확정된다. 과도기 상태를 붙잡으려면 로드를 서스펜드
     * 시키는 장치가 필요한데 얻는 값에 비해 기계가 커진다. 나머지 네 분기가 실제 사용자가 보는 화면이다.
     */
    private fun render(load: ActivityLoad) {
        composeRule.setContent {
            NexusTheme {
                ActivityScreen(
                    manager = HealthConnectManager(context),
                    controller = ActivityUiController(
                        context = context,
                        stepRepo = null,
                        exerciseRepo = null,
                        initialLoad = load,
                    ),
                )
            }
        }
    }

    private fun string(id: Int) = context.getString(id)

    @Test
    fun `미연결이면 연결 안내를 그린다`() {
        render(ActivityLoad.PermissionDenied)

        composeRule.onNodeWithText(string(R.string.activity_demo_body)).assertIsDisplayed()
    }

    @Test
    fun `실패면 다시 시도를 그린다`() {
        render(ActivityLoad.Failure)

        composeRule.onNodeWithText(string(R.string.steps_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
    }

    @Test
    fun `데이터가 있으면 걸음·세션 섹션을 그린다`() {
        render(
            ActivityLoad.Success(
                ActivityData(steps = emptyList(), manualSteps = 0L, sessions = emptyList(), awaitingFirstData = false),
            ),
        )

        composeRule.onNodeWithText(string(R.string.steps_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.sessions_title)).assertIsDisplayed()
        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertDoesNotExist()
    }

    @Test
    fun `첫 데이터 대기면 빈 상태만 그리고 섹션은 숨긴다`() {
        // #213의 핵심 계약 — 이전 테스트는 조건식만 봐서 이 '숨김'을 검증하지 못했다
        render(
            ActivityLoad.Success(
                ActivityData(steps = emptyList(), manualSteps = 0L, sessions = emptyList(), awaitingFirstData = true),
            ),
        )

        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.steps_title)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.sessions_title)).assertDoesNotExist()
    }
}
