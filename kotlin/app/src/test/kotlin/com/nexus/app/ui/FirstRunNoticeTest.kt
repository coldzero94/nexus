package com.nexus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * 빈 상태 컴포넌트 자체의 계약 (#213) — 렌더되는가, 문구가 실시간을 약속하지 않는가.
 *
 * **노출 조건 검증은 여기 없다.** 이전에는 이 파일이 `FirstRun.isAwaitingFirstData` 분기를 로컬에
 * 다시 구현해 검증했는데, 그건 화면을 타지 않아 "분기 배치를 지워도 통과"했다(#213 리뷰 지적).
 * 지금은 실제 화면 렌더 테스트가 그 계약을 검증한다 —
 * `ActivityScreenRenderTest`·`GrowthScreenRenderTest`(#320). 규칙 자체는 core `FirstRunTest`.
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
     *
     * 실행기는 [ManualSyncEnqueueTest]와 **같은 '실행하지 않는' 것**을 쓴다 — 인스턴스를 공유하는 이상
     * 어느 클래스가 먼저 도느냐에 따라 실행기가 달라지면 KEEP 가드 검증이 조용히 무너진다.
     * 이 테스트는 워크가 실제로 돌 필요가 없다(구독만 한다).
     */
    @Before
    fun initWorkManagerIfAbsent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        runCatching { WorkManager.getInstance(context) }.onFailure {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor { /* 실행하지 않는다 */ }.build(),
            )
        }
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
