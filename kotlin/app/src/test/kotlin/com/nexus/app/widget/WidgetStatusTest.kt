package com.nexus.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.R
import com.nexus.core.ExpeditionEngine
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 잔여 표기 순수 함수 고정 (#72 리뷰) — 마지막 1시간 "약 0시간 남음" 회귀 방지 + 반올림 계약. */
@RunWith(RobolectricTestRunner::class)
class WidgetStatusTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * 표시 분류 (#246) — 상태 줄 렌더와 무변화 스킵 판정이 **이 함수 하나**를 공유하게 되면서
     * 분류가 곧 화면이 됐다. 여기가 틀리면 위젯 문구와 갱신 시점이 함께 틀어진다.
     */
    @Test
    fun expeditionDisplayBuckets() {
        val start = 1_800_000_000_000L
        val done = start + ExpeditionEngine.DURATION_MILLIS

        assertEquals(ExpeditionDisplay.None, expeditionDisplay(0L, start), "미진행")
        assertEquals(ExpeditionDisplay.Hours(8), expeditionDisplay(start, start), "출발 직후")
        assertEquals(ExpeditionDisplay.Soon, expeditionDisplay(start, done - 1), "마지막 1시간")
        assertEquals(ExpeditionDisplay.Ready, expeditionDisplay(start, done), "개봉 대기")
        assertEquals(ExpeditionDisplay.Ready, expeditionDisplay(start, done + 1), "개봉 대기 유지")
    }

    @Test
    fun lastHourIsSoonBranch() {
        assertNull(remainingDisplayHours(0L))
        assertNull(remainingDisplayHours(59 * 60_000L))
        assertNull(remainingDisplayHours(3_599_999L))
    }

    @Test
    fun roundsToNearestHour() {
        assertEquals(1L, remainingDisplayHours(3_600_000L)) // 정확히 1시간
        assertEquals(1L, remainingDisplayHours(89 * 60_000L)) // 1시간 29분 → 1
        assertEquals(2L, remainingDisplayHours(90 * 60_000L)) // 1시간 30분 → 2
        assertEquals(7L, remainingDisplayHours(6 * 3_600_000L + 59 * 60_000L)) // 6시간 59분 → 7
        assertEquals(8L, remainingDisplayHours(8 * 3_600_000L)) // 출발 직후 → 8
    }

    // ── 상태 한 줄 우선순위 (#72, #246 리팩터 회귀 방지) ──

    /**
     * `statusLine`은 이번에 [expeditionDisplay] 위로 다시 쓰였는데 **테스트가 하나도 없었다** —
     * "위젯 회귀 없음"(AC ⑤)이 읽기에만 기대고 있었다는 뜻이다. 우선순위 사슬을 표로 고정한다.
     */
    private fun line(
        expeditionStartedAt: Long = 0L,
        morningPending: Boolean = false,
        journalPending: Boolean = false,
    ) = statusLine(
        context,
        WidgetSnapshot(
            level = 1,
            condition = 70,
            todayXp = 0,
            spriteState = "idle",
            expeditionStartedAt = expeditionStartedAt,
            morningPending = morningPending,
            journalPending = journalPending,
        ),
    )

    @Test
    fun `원정이 없고 알림도 없으면 상태 줄이 없다`() {
        assertNull(line())
    }

    @Test
    fun `원정 개봉 대기가 다른 모든 것을 이긴다`() {
        val started = System.currentTimeMillis() - ExpeditionEngine.DURATION_MILLIS
        assertEquals(
            context.getString(R.string.widget_expedition_ready),
            line(expeditionStartedAt = started, morningPending = true, journalPending = true),
        )
    }

    @Test
    fun `원정 진행이 아침 저녁 알림을 이긴다`() {
        assertEquals(
            context.getString(R.string.widget_expedition_progress, ExpeditionEngine.DURATION_HOURS),
            line(expeditionStartedAt = System.currentTimeMillis(), morningPending = true, journalPending = true),
        )
    }

    @Test
    fun `원정이 없으면 저녁 일지가 아침 카드를 이긴다`() {
        assertEquals(
            context.getString(R.string.widget_journal_pending),
            line(morningPending = true, journalPending = true),
        )
    }

    @Test
    fun `저녁 일지가 없으면 아침 카드가 나온다`() {
        assertEquals(context.getString(R.string.widget_morning_pending), line(morningPending = true))
    }
}
