package com.nexus.app.growth

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.nexus.app.R
import com.nexus.app.data.NexusDatabase
import com.nexus.app.data.RewardLedgerRepository
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.FIRST_RUN_NOTICE_TAG
import com.nexus.app.ui.NexusTheme
import com.nexus.core.ClassAffinity
import com.nexus.core.DayXpExplanation
import com.nexus.core.GrowthSummary
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executor

/**
 * 성장 화면 로드 분기 전수 렌더 (#320) — 활동과 같은 방식. 실제 화면 컴포저블을 태운다.
 */
@RunWith(RobolectricTestRunner::class)
class GrowthScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun initWorkManager() {
        runCatching { WorkManager.getInstance(context) }.onFailure {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(Executor { }).build(),
            )
        }
    }

    private fun render(load: GrowthLoad) {
        composeRule.setContent {
            NexusTheme {
                GrowthScreen(
                    manager = HealthConnectManager(context),
                    controller = GrowthUiController(
                        context = context,
                        manager = HealthConnectManager(context),
                        exerciseRepo = null,
                        ledger = RewardLedgerRepository(NexusDatabase.get(context).rewardEventDao()),
                        stateStore = GrowthStateStore(context),
                        initialLoad = load,
                    ),
                )
            }
        }
    }

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun successState(awaiting: Boolean) = GrowthLoad.Success(
        GrowthUiState(
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
            awaitingFirstData = awaiting,
        ),
    )

    @Test
    fun `제목은 어느 분기에서나 보인다`() {
        render(GrowthLoad.Failure)

        composeRule.onNodeWithText(string(R.string.growth_title)).assertIsDisplayed()
    }

    @Test
    fun `미연결이면 연결 안내를 그린다`() {
        render(GrowthLoad.PermissionDenied)

        composeRule.onNodeWithText(string(R.string.growth_demo_body, 28)).assertIsDisplayed()
    }

    @Test
    fun `실패면 다시 시도를 그린다`() {
        render(GrowthLoad.Failure)

        composeRule.onNodeWithText(string(R.string.growth_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
    }

    /**
     * 레벨 카드는 #224에서 [androidx.compose.ui.semantics.clearAndSetSemantics]로 묶었다 — 안쪽
     * "레벨 3" 텍스트는 시맨틱 트리에 없다(스크린리더가 한 문장으로 듣게 하려는 의도).
     * 그래서 단언도 **스크린리더가 보는 노드**로 한다: 게이지 라벨.
     */
    @Test
    fun `성공이면 레벨 게이지를 그리고 빈 상태는 없다`() {
        render(successState(awaiting = false))

        composeRule.onNodeWithContentDescription(string(R.string.a11y_level_gauge)).assertExists()
        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertDoesNotExist()
    }

    @Test
    fun `첫 데이터 대기면 빈 상태만 그리고 레벨 게이지는 숨긴다`() {
        render(successState(awaiting = true))

        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.a11y_level_gauge)).assertDoesNotExist()
    }
}
