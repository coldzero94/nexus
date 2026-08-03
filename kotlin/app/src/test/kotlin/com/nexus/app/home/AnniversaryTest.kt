package com.nexus.app.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.loadAnniversaries
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 함께한 N일 기념일 — 앱 배선 (#111, E5-16). 계산 자체는 core [com.nexus.core.Anniversaries]가 검증한다.
 *
 * ## 여기서만 잡히는 것
 *
 * "언제 만났는가"의 **확정 시점**이다. 매번 다시 계산하면 원장이 정리되거나 백업이 복원될 때
 * 만난 날이 흔들려 기념일이 앞뒤로 오간다. 반대로 이미 쓰던 사용자에게 "오늘 만났다"고 하면
 * 기념일 시계가 리셋되는데, 그건 이 기능이 만들려는 것(누적된 시간 = 애착)의 정반대다.
 */
@RunWith(RobolectricTestRunner::class)
class AnniversaryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun store() = TogetherStore(context)

    private val table get() = CharacterAssets(context).loadAnniversaries()

    @Test
    fun `기념일 표가 파싱되고 비어 있지 않다`() {
        assertTrue(table.anniversaries.isNotEmpty())
    }

    /** 알파 기간(14일)에 최소 하나는 닿아야 한다 — 아무도 못 보는 연출은 없는 것과 같다. */
    @Test
    fun `알파 기간 안에 닿는 기념일이 있다`() {
        val earliest = table.anniversaries.minOf { it.days }

        assertTrue(earliest <= ALPHA_DAYS, "가장 이른 기념일이 ${earliest}일 — 알파(${ALPHA_DAYS}일) 안에 아무도 못 본다")
    }

    /**
     * 기념일 카피는 **활동을 세지 않는다**. 수치를 적는 순간 성취 축이 되고, 못 한 사람에게는
     * 축하가 아니라 성적표가 된다 — 이 축의 값어치는 노력과 무관하게 온다는 데 있다.
     */
    @Test
    fun `기념일 카피가 활동을 세지 않는다`() {
        val offenders = table.anniversaries.filter { a ->
            FORBIDDEN.any { it in a.title || it in a.body }
        }

        assertTrue(offenders.isEmpty(), "성취 축 어휘가 섞였다: ${offenders.map { it.days }}")
    }

    /** 새 설치: 원장이 비어 있으면 오늘이 첫 만남. */
    @Test
    fun `신규 설치는 오늘이 첫 만남이다`() {
        assertEquals(TODAY, store().firstMetEpochDay(ledgerFirstEpochDay = null, todayEpochDay = TODAY))
    }

    /**
     * 이미 쓰던 사용자: 원장 첫날로 소급한다. 오늘로 잡으면 기념일 시계가 리셋된다 —
     * 누적된 시간을 자산으로 만들자는 티켓에서 정확히 반대되는 결과다.
     */
    @Test
    fun `기존 사용자는 원장 첫날로 소급한다`() {
        val ninetyDaysAgo = TODAY - 90

        assertEquals(ninetyDaysAgo, store().firstMetEpochDay(ninetyDaysAgo, TODAY))
    }

    /** 한 번 정해지면 안 바뀐다 — 원장이 정리돼도 기념일이 뒤로 밀리면 안 된다. */
    @Test
    fun `첫 만남은 한 번 정해지면 고정이다`() {
        val first = store().firstMetEpochDay(TODAY - 90, TODAY)

        // 원장이 비워진 뒤 다시 물어봐도
        val again = store().firstMetEpochDay(ledgerFirstEpochDay = null, todayEpochDay = TODAY)

        assertEquals(first, again)
    }

    /** 시계 조작으로 미래 원장이 들어와도 "만난 지 -3일"이 되면 안 된다. */
    @Test
    fun `첫 만남이 오늘보다 뒤일 수 없다`() {
        assertEquals(TODAY, store().firstMetEpochDay(ledgerFirstEpochDay = TODAY + 10, todayEpochDay = TODAY))
    }

    /** 축하 기록은 뒤로 가지 않는다 — 낮은 값이 들어오면 이미 축하한 기념일이 다시 뜬다. */
    @Test
    fun `축하 기록은 뒤로 가지 않는다`() {
        val s = store()
        s.celebratedDays = 30

        s.celebratedDays = 7

        assertEquals(30, s.celebratedDays)
    }

    /** 앱 진입 시 로드 — 만난 지 오래됐으면 기념일이 나온다. */
    @Test
    fun `오래 함께했으면 기념일이 잡힌다`() {
        val s = store()
        s.firstMetEpochDay(ledgerFirstEpochDay = TODAY - 400, todayEpochDay = TODAY)

        val pending = com.nexus.core.Anniversaries.pendingAt(
            table,
            daysTogether = com.nexus.core.Anniversaries.daysTogether(TODAY - 400, TODAY),
            celebratedDays = s.celebratedDays,
        )

        assertEquals(365, pending?.days)
    }

    /** 양성 대조 — 방금 만났으면 아무것도 안 뜬다. */
    @Test
    fun `방금 만났으면 기념일이 없다`() {
        assertNull(
            com.nexus.core.Anniversaries.pendingAt(
                table,
                daysTogether = com.nexus.core.Anniversaries.daysTogether(TODAY, TODAY),
                celebratedDays = 0,
            ),
        )
    }
}

/** 2026-08-03 근처의 임의 epochDay — 값 자체는 무의미하고 상대 거리만 쓴다. */
private const val TODAY = 20_669L

/** 알파 기간 (docs/SPRINTS.md) — 이 안에 닿는 기념일이 하나는 있어야 한다. */
private const val ALPHA_DAYS = 14

/** 성취 축 어휘 — 기념일 카피에 섞이면 축하가 성적표가 된다. */
private val FORBIDDEN = listOf("운동", "XP", "레벨", "달성", "기록했", "번 완료")
