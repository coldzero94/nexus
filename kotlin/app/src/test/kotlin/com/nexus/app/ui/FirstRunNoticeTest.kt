package com.nexus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.nexus.core.FirstRun
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 빈 상태 3분기 렌더 (#213 완료 기준) — 데이터 없음·0XP / 걸음만 있고 0XP / 기존 사용자.
 *
 * 가운데 분기가 이 티켓의 핵심이다: 걸음은 XP를 만들지 않으므로 걷기만 하는 사용자는 XP가 계속 0인데,
 * 여기서 빈 상태를 띄우면 실제 걸음 막대를 가려 **고치려던 "고장처럼 보임"을 오히려 만든다**.
 *
 * 에뮬 불요(#232 하네스) — Robolectric + compose ui-test.
 */
@RunWith(RobolectricTestRunner::class)
class FirstRunNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 빈 상태의 '지금 확인'이 WorkManager를 구독한다 — 하네스에도 인메모리 인스턴스가 필요하다.
     *
     * 이미 있으면 **다시 만들지 않는다**: 재초기화는 이전 인스턴스의 인메모리 DB를 닫는데, 앞 테스트가
     * 남긴 WorkInfo 구독이 아직 살아 있으면 "database ':memory:' is not open"으로 터진다
     * (개별 실행은 통과하고 전체 스위트에서만 깨지는 테스트 간 오염).
     */
    @Before
    fun initWorkManagerIfAbsent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runCatching { WorkManager.getInstance(context) }.onFailure {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
            )
        }
    }

    /**
     * 화면들이 하는 것과 같은 분기 — 판정은 core, 렌더는 여기.
     *
     * 한계: 세 화면 각각의 분기 **배치**(어느 로드 상태 앞뒤에 놓였는지)는 이 테스트가 잡지 못한다.
     * 화면 컴포저블이 HealthConnectManager를 요구해 호스트에서 세울 수 없어서다. 현재는 실기기
     * 3탭 확인으로 대신하며, 화면 단위 렌더 하네스는 후속 티켓으로 분리했다.
     */
    @Composable
    private fun Subject(lifetimeXp: Int, hasAnyHealthData: Boolean) {
        if (FirstRun.isAwaitingFirstData(lifetimeXp, hasAnyHealthData)) FirstRunNotice()
    }

    @Test
    fun `데이터 없음 0XP — 빈 상태를 그린다`() {
        composeRule.setContent { Subject(lifetimeXp = 0, hasAnyHealthData = false) }
        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertIsDisplayed()
    }

    @Test
    fun `걸음만 있고 0XP — 빈 상태를 그리지 않는다`() {
        // 걸음은 XP를 만들지 않는다 — 여기서 빈 상태를 띄우면 실제 걸음 막대를 가린다
        composeRule.setContent { Subject(lifetimeXp = 0, hasAnyHealthData = true) }
        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertDoesNotExist()
    }

    @Test
    fun `기존 사용자 — 빈 상태를 그리지 않는다`() {
        composeRule.setContent { Subject(lifetimeXp = 250, hasAnyHealthData = true) }
        composeRule.onNodeWithTag(FIRST_RUN_NOTICE_TAG).assertDoesNotExist()
    }

    @Test
    fun `문구는 실시간을 약속하지 않는다`() {
        composeRule.setContent { FirstRunNotice() }
        // 표시 문구에 '실시간·즉시·바로 반영'이 없어야 한다 (불변식 ⑤)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val copy = listOf(
            context.getString(com.nexus.app.R.string.first_run_title),
            context.getString(com.nexus.app.R.string.first_run_body),
        ).joinToString(" ")
        val forbidden = listOf("실시간", "즉시", "바로 반영")
        assertEquals(
            emptyList(),
            forbidden.filter { it in copy },
            "빈 상태 문구가 실시간성을 약속한다 — HC 전파는 30~60분이라 지킬 수 없는 약속이다",
        )
        composeRule.onNodeWithText(context.getString(com.nexus.app.R.string.first_run_title)).assertIsDisplayed()
    }
}
