package com.nexus.app.data

import com.nexus.core.RecordingMethod
import com.nexus.core.RewardEventType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 원장 저장소 계약 고정 (#162) — 인메모리 페이크 DAO가 실제 DB의
 * (idempotencyKey, type) 유니크 제약을 재현한다.
 */
class RewardLedgerRepositoryTest {

    private class FakeDao : RewardEventDao {
        val rows = mutableListOf<RewardEventEntity>()
        private var seq = 1L

        override suspend fun insert(event: RewardEventEntity): Long {
            if (rows.any { it.idempotencyKey == event.idempotencyKey && it.type == event.type }) return -1L
            val stored = event.copy(sequence = seq++)
            rows += stored
            return stored.sequence
        }

        override suspend fun grantOf(key: String): RewardEventEntity? =
            rows.firstOrNull { it.idempotencyKey == key && it.type == RewardEventType.GRANT.name }

        override suspend fun xpByDay(): List<DayXpRow> =
            rows.groupBy { it.epochDay }.map { (day, list) -> DayXpRow(day, list.sumOf { it.xp }.toDouble()) }

        override suspend fun count(): Long = rows.size.toLong()

        override suspend fun all(): List<RewardEventEntity> = rows.toList()
    }

    private suspend fun RewardLedgerRepository.grantSample(key: String, xp: Int, day: Long = 0L): Boolean = grant(
        idempotencyKey = key,
        xp = xp,
        dataOrigin = "com.test",
        recordingMethod = RecordingMethod.AUTO_RECORDED,
        epochMillis = 1L,
        epochDay = day,
    )

    @Test
    fun grant_isIdempotent_perRecordKey() = runTest {
        val dao = FakeDao()
        val repo = RewardLedgerRepository(dao)
        assertTrue(repo.grantSample("hc-1", xp = 60))
        assertFalse(repo.grantSample("hc-1", xp = 60)) // 같은 HC 레코드 재도착 → 중복 지급 없음
        assertEquals(1, dao.rows.size)
        assertEquals(60, repo.cappedTotalXp())
    }

    @Test
    fun cancel_appendsCompensation_preservingGrantDay() = runTest {
        val dao = FakeDao()
        val repo = RewardLedgerRepository(dao)
        repo.grantSample("hc-1", xp = 60, day = 5L)
        assertTrue(repo.cancel("hc-1", epochMillis = 2L))

        val cancellation = dao.rows.single { it.type == RewardEventType.CANCELLATION.name }
        assertEquals(-60, cancellation.xp) // 부호 반전 append — 원본 행 불변
        assertEquals(5L, cancellation.epochDay) // 지급일 보존 → 일 상한 합산에서 정확히 상쇄
        assertEquals(0, repo.cappedTotalXp())
    }

    @Test
    fun cancel_withoutGrant_orTwice_isRejected() = runTest {
        val repo = RewardLedgerRepository(FakeDao())
        assertFalse(repo.cancel("ghost", epochMillis = 1L)) // 미지급 취소 불가
        repo.grantSample("hc-1", xp = 60)
        assertTrue(repo.cancel("hc-1", epochMillis = 2L))
        assertFalse(repo.cancel("hc-1", epochMillis = 3L)) // 중복 취소는 유니크 제약이 무시
    }

    @Test
    fun cappedTotal_appliesDailyCapPerDay() = runTest {
        val repo = RewardLedgerRepository(
            FakeDao().also { dao ->
                // 하루에 raw 500 지급(세션 다건) → 표시 총합은 하드캡 300
            },
        )
        repo.grantSample("a", xp = 300, day = 0L)
        repo.grantSample("b", xp = 200, day = 0L)
        repo.grantSample("c", xp = 100, day = 1L)
        assertEquals(400, repo.cappedTotalXp()) // 300(캡) + 100
    }

    @Test
    fun cappedXpOn_singleDay_withCancellation() = runTest {
        val repo = RewardLedgerRepository(FakeDao())
        repo.grantSample("a", xp = 400, day = 3L) // 상한: 200 + 100 = 300
        repo.grantSample("b", xp = 50, day = 4L)
        repo.grantSample("c", xp = 60, day = 3L)
        repo.cancel("c", epochMillis = 9L) // 취소가 지급일(3)로 상쇄
        assertEquals(300, repo.cappedXpOn(3L)) // 400+60-60=400 → 캡 300
        assertEquals(50, repo.cappedXpOn(4L))
        assertEquals(0, repo.cappedXpOn(99L)) // 무기록 일자
    }
}
