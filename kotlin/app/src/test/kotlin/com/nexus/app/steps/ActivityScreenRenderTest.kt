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
import com.nexus.app.health.DailySteps
import com.nexus.app.health.ExerciseSummary
import com.nexus.app.health.HealthConnectManager
import com.nexus.app.ui.FIRST_RUN_NOTICE_TAG
import com.nexus.app.ui.NexusTheme
import com.nexus.core.ActivityType
import com.nexus.core.RecordingMethod
import com.nexus.core.TrustTier
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
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

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

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

    private fun session(minutes: Long, startEpochSecond: Long) = ExerciseSummary(
        id = "session-$startEpochSecond",
        type = ActivityType.WALKING,
        exerciseTypeRaw = 0,
        start = Instant.ofEpochSecond(startEpochSecond),
        end = Instant.ofEpochSecond(startEpochSecond + minutes * 60),
        durationMinutes = minutes,
        avgHeartRate = null,
        dataOrigin = "com.sec.android.app.shealth",
        recordingMethod = RecordingMethod.AUTO_RECORDED,
        trustTier = TrustTier.B,
    )

    /**
     * 세 섹션이 **제목을 가진 카드로** 구획됐는지 (#260). 이전에는 동기화 상태가 제목 없는
     * 푸터 한 줄이라 무슨 값인지 알 수 없었다.
     */
    @Test
    fun `성공이면 걸음·운동·동기화 세 섹션 제목이 모두 보인다`() {
        render(
            ActivityLoad.Success(
                ActivityData(steps = emptyList(), manualSteps = 0L, sessions = emptyList(), awaitingFirstData = false),
            ),
        )

        composeRule.onNodeWithText(string(R.string.steps_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.sessions_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.sync_title)).assertIsDisplayed()
    }

    /**
     * 안내는 섹션 카드 **안이 아니라 형제로** 온다 (#260).
     *
     * `RetryNotice`·`ConnectNotice`가 이미 `NexusCard`라서 섹션 카드 안에 넣으면 같은 색 카드가
     * 두 겹으로 겹치고 제목도 두 개가 되어 렌더 오류처럼 보인다. 홈·성장이 형제로 두는 이유가
     * 그것이고, 활동도 같은 배치를 쓴다 — 섹션 제목이 함께 뜨지 **않아야** 맞다.
     */
    @Test
    fun `실패하면 안내만 그리고 섹션 카드는 그리지 않는다`() {
        render(ActivityLoad.Failure)

        composeRule.onNodeWithText(string(R.string.steps_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.steps_title)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.sessions_title)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.sync_title)).assertDoesNotExist()
    }

    @Test
    fun `미연결이면 안내만 그리고 섹션 카드는 그리지 않는다`() {
        // 제목만 있고 내용이 빈 카드는 빈 상태가 아니라 고장으로 읽힌다
        render(ActivityLoad.PermissionDenied)

        composeRule.onNodeWithText(string(R.string.activity_demo_body)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.sessions_title)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.sync_title)).assertDoesNotExist()
    }

    @Test
    fun `세션이 있으면 시간을 우측 값 열로 그린다`() {
        // 종류·심박과 한 문장으로 붙어 있으면 목록에서 시간을 세로로 비교할 수 없다 (#260)
        render(
            ActivityLoad.Success(
                ActivityData(
                    steps = emptyList(),
                    manualSteps = 0L,
                    sessions = listOf(session(minutes = 42, startEpochSecond = 1_700_000_000L)),
                    awaitingFirstData = false,
                ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.session_duration_format, 42)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.sessions_empty)).assertDoesNotExist()
    }

    @Test
    fun `세션이 없으면 빈 문구를 그린다`() {
        render(
            ActivityLoad.Success(
                ActivityData(steps = emptyList(), manualSteps = 0L, sessions = emptyList(), awaitingFirstData = false),
            ),
        )

        composeRule.onNodeWithText(string(R.string.sessions_empty)).assertIsDisplayed()
    }

    @Test
    fun `데이터가 있으면 걸음·세션 섹션을 그린다`() {
        render(
            ActivityLoad.Success(
                ActivityData(
                    steps = listOf(DailySteps(LocalDate.of(2026, 7, 30), 8_000L)),
                    manualSteps = 0L,
                    sessions = listOf(session(minutes = 30, startEpochSecond = 1_700_000_000L)),
                    awaitingFirstData = false,
                ),
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
