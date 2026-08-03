package com.nexus.app.growth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.loadMilestoneTable
import com.nexus.core.BadgeContext
import com.nexus.core.BadgeEvaluator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 마일스톤 표 (#113, E5-13).
 *
 * 표는 "고치면 코드 무수정"이 계약이라, 잘못된 표가 조용히 통과하면 마일스톤이 영영 안 열리거나
 * 처음부터 다 열린 채로 시작한다. 둘 다 화면만 봐서는 구분되지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class MilestoneTableTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val table get() = CharacterAssets(context).loadMilestoneTable()

    @Test
    fun `표가 파싱되고 비어 있지 않다`() {
        assertTrue(table.badges.isNotEmpty())
    }

    /** 신규 사용자에게 **하나도** 열려 있으면 안 된다 — 열린 채로 시작하면 성취가 아니다. */
    @Test
    fun `아무 기록도 없으면 하나도 열리지 않는다`() {
        val unlocked = BadgeEvaluator.unlocked(table, BadgeContext())

        assertTrue(unlocked.isEmpty(), "빈 상태에서 열린 마일스톤: $unlocked")
    }

    /** 반대로 충분히 쌓이면 전부 열려야 한다 — 도달 불가능한 조건이 섞이면 여기서 드러난다. */
    @Test
    fun `충분히 쌓이면 전부 열린다`() {
        val maxed = BadgeContext(
            cumulativeXp = 1_000_000,
            expeditionsCompleted = 10_000,
            activeDaysLifetime = 10_000,
        )

        val unlocked = BadgeEvaluator.unlocked(table, maxed)

        assertEquals(table.badges.map { it.id }.toSet(), unlocked, "도달 불가능한 조건이 있다")
    }

    /**
     * 마일스톤은 **평생 축**이라 창 기반 신호(`activeDaysTotal`·`streakDays`·`bestDaySteps`)에
     * 기대면 안 된다. 기대는 순간 28일 창 밖으로 나간 기록이 빠져 장기 성취가 열리지 않는다.
     */
    @Test
    fun `창 기반 신호에 의존하지 않는다`() {
        val windowOnly = BadgeContext(activeDaysTotal = 9_999, streakDays = 9_999, bestDaySteps = 9_999)

        val unlocked = BadgeEvaluator.unlocked(table, windowOnly)

        assertTrue(unlocked.isEmpty(), "창 기반 신호로 열리는 마일스톤이 있다: $unlocked")
    }

    /** 배지와 id가 겹치면 두 목록이 서로의 획득을 덮어쓴다(저장소는 id 집합이다). */
    @Test
    fun `배지와 id가 겹치지 않는다`() {
        val badgeIds = CharacterAssets(context).loadBadgeTable().badges.map { it.id }.toSet()

        val overlap = table.badges.map { it.id }.filter { it in badgeIds }

        assertTrue(overlap.isEmpty(), "배지와 겹치는 마일스톤 id: $overlap")
    }
}
