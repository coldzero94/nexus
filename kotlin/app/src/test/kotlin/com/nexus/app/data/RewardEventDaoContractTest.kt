package com.nexus.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 원장 DAO 서버 계약 회귀 (#235, E15-7) — **실 Room**으로 `BACKEND §1`의 4대 서버 전환 계약을 못박는다.
 *
 * 지금까지 원장 검증은 손수 만든 `FakeDao`로만 했다(`RewardLedgerRepositoryTest`). 페이크는 유니크
 * 제약·`OnConflictStrategy.IGNORE`·`GROUP BY` 의미를 **사람이 재현한 것**이라, 실 Room과 발산하면
 * 테스트는 green인데 프로덕션과 향후 서버 임포트가 깨진다. 원장은 이 프로젝트의 crown jewel이라
 * (BACKEND §1: "각 이벤트를 당시 산식으로 재검산") 그 발산이 조용히 일어나면 안 된다.
 *
 * 각 테스트는 **독립적으로 실패 가능**하다 — 한 계약이 깨지면 그 계약의 테스트만 빨개진다.
 * 에뮬레이터 불요(#232 하네스).
 */
@RunWith(RobolectricTestRunner::class)
class RewardEventDaoContractTest {
    private lateinit var db: NexusDatabase
    private lateinit var dao: RewardEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NexusDatabase::class.java).build()
        dao = db.rewardEventDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 계약 ①: 멱등키 — 같은 (key, GRANT)는 두 번 적립되지 않는다 ──

    @Test
    fun duplicateGrant_isIgnored_andRowCountUnchanged() = runBlocking<Unit> {
        val first = dao.insert(grant(key = "hc-uuid-1", xp = 30))
        val second = dao.insert(grant(key = "hc-uuid-1", xp = 30))

        assertTrue(first > 0, "첫 GRANT는 삽입돼야 한다(rowId=$first)")
        assertEquals(-1L, second, "중복 GRANT는 IGNORE로 -1을 돌려줘야 한다")
        assertEquals(1L, dao.count(), "중복 삽입으로 행이 늘면 안 된다")
    }

    @Test
    fun sameKey_differentType_coexists_soCancellationIsAppend() = runBlocking<Unit> {
        // 취소는 원본 행을 고치는 게 아니라 **상쇄 행을 append**한다 — 유니크 키가 (key, type)인 이유
        dao.insert(grant(key = "hc-uuid-2", xp = 40))
        val cancel = dao.insert(cancellation(key = "hc-uuid-2", xp = -40))

        assertTrue(cancel > 0, "같은 키의 CANCELLATION은 type이 달라 공존해야 한다")
        assertEquals(2L, dao.count(), "GRANT + CANCELLATION 두 행이 남아야 한다")
    }

    @Test
    fun duplicateCancellation_isAlsoIgnored() = runBlocking<Unit> {
        // 취소도 멱등이어야 한다 — 재시도로 이중 상쇄되면 XP가 음수로 흐른다
        dao.insert(grant(key = "hc-uuid-3", xp = 25))
        dao.insert(cancellation(key = "hc-uuid-3", xp = -25))
        val secondCancel = dao.insert(cancellation(key = "hc-uuid-3", xp = -25))

        assertEquals(-1L, secondCancel, "중복 CANCELLATION도 IGNORE 대상")
        assertEquals(2L, dao.count())
    }

    // ── 계약 ②: 단조 시퀀스 — append 순서가 보존된다 ──

    @Test
    fun sequence_isStrictlyIncreasing_inInsertionOrder() = runBlocking<Unit> {
        repeat(5) { i -> dao.insert(grant(key = "seq-$i", xp = 10)) }

        val sequences = dao.all().map { it.sequence }
        assertEquals(5, sequences.size)
        assertEquals(sequences.sorted(), sequences, "all()은 sequence 오름차순이어야 한다")
        sequences.zipWithNext { a, b ->
            assertTrue(b > a, "sequence가 엄격히 증가해야 한다: $a → $b")
        }
    }

    @Test
    fun ignoredInsert_doesNotConsumeSequence_gapIsAcceptableButOrderHolds() = runBlocking<Unit> {
        dao.insert(grant(key = "gap-1", xp = 10))
        dao.insert(grant(key = "gap-1", xp = 10)) // IGNORE
        dao.insert(grant(key = "gap-2", xp = 10))

        val sequences = dao.all().map { it.sequence }
        assertEquals(2, sequences.size, "무시된 삽입은 행을 만들지 않는다")
        assertTrue(sequences[1] > sequences[0], "무시가 끼어도 순서는 단조여야 한다")
    }

    // ── 계약 ③: 불변 — 저장된 행은 수정·삭제 경로가 없다 ──

    @Test
    fun dao_exposesNoMutationPath() {
        // DAO에 @Update·@Delete가 생기면 여기서 잡힌다 — 정정은 상쇄 행 append로만 한다(BACKEND §1)
        val methods = RewardEventDao::class.java.declaredMethods.map { it.name }.toSet()
        val forbidden = methods.filter { name ->
            name.startsWith("update") || name.startsWith("delete") || name.startsWith("clear")
        }
        assertTrue(
            forbidden.isEmpty(),
            "원장은 불변이라 수정·삭제 메서드가 있으면 안 된다: $forbidden",
        )
    }

    @Test
    fun grantOf_returnsOnlyGrant_notCancellation() = runBlocking<Unit> {
        dao.insert(grant(key = "only-grant", xp = 15))
        dao.insert(cancellation(key = "only-grant", xp = -15))

        val found = dao.grantOf("only-grant")
        assertNotNull(found, "GRANT 행을 찾아야 한다")
        assertEquals("GRANT", found.type, "grantOf는 CANCELLATION을 돌려주면 안 된다")
        assertEquals(15, found.xp)
        assertNull(dao.grantOf("nonexistent"), "없는 키는 null")
    }

    // ── 계약 ④: 일자 합산 — 같은 날 GRANT + CANCELLATION이 부호까지 정확히 상쇄된다 ──

    @Test
    fun xpByDay_sumsGrantAndCancellation_withSign() = runBlocking<Unit> {
        val day = 20_000L
        dao.insert(grant(key = "d1", xp = 50, epochDay = day))
        dao.insert(grant(key = "d2", xp = 30, epochDay = day))
        dao.insert(cancellation(key = "d1", xp = -50, epochDay = day))

        val rows = dao.xpByDay()
        assertEquals(1, rows.size, "같은 epochDay는 한 행으로 묶여야 한다")
        assertEquals(day, rows.first().epochDay)
        assertEquals(30.0, rows.first().xp, TOLERANCE, "50 + 30 - 50 = 30")
    }

    @Test
    fun xpByDay_separatesDays_andCancellationKeepsGrantDay() = runBlocking<Unit> {
        // 취소 행은 **지급일**을 보존한다 — 취소한 날이 아니라(일 상한 정합, 엔티티 KDoc)
        dao.insert(grant(key = "a", xp = 100, epochDay = 20_000L))
        dao.insert(grant(key = "b", xp = 40, epochDay = 20_001L))
        dao.insert(cancellation(key = "a", xp = -100, epochDay = 20_000L))

        val byDay = dao.xpByDay().associate { it.epochDay to it.xp }
        assertEquals(0.0, byDay.getValue(20_000L), TOLERANCE, "지급일에서 상쇄돼 0")
        assertEquals(40.0, byDay.getValue(20_001L), TOLERANCE, "다른 날은 영향 없음")
    }

    // ── 계약 ⑤: 실 Room이 페이크가 가정하던 계약과 일치한다 ──

    @Test
    fun realRoom_matchesFakeContract_usedByRepositoryTests() = runBlocking<Unit> {
        // RewardLedgerRepositoryTest의 FakeDao가 손으로 재현하던 규칙을 실 Room으로 재확인:
        // "같은 (key,type)이면 insert가 -1이고 행 수는 그대로"
        dao.insert(grant(key = "parity", xp = 20))
        val before = dao.count()
        val ignored = dao.insert(grant(key = "parity", xp = 999)) // xp가 달라도 키가 같으면 무시
        val after = dao.count()

        assertEquals(-1L, ignored)
        assertEquals(before, after)
        assertEquals(20, dao.grantOf("parity")?.xp, "무시된 삽입이 기존 값을 덮어쓰면 안 된다")
    }

    private fun grant(key: String, xp: Int, epochDay: Long = 20_000L) = RewardEventEntity(
        idempotencyKey = key,
        xp = xp,
        type = "GRANT",
        dataOrigin = "com.sec.android.app.shealth",
        recordingMethod = "AUTO_RECORDED",
        formulaVersion = 1,
        epochMillis = 1_700_000_000_000L,
        epochDay = epochDay,
    )

    private fun cancellation(key: String, xp: Int, epochDay: Long = 20_000L) =
        grant(key, xp, epochDay).copy(type = "CANCELLATION")

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
