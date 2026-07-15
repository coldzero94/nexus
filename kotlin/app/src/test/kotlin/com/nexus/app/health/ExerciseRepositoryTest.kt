package com.nexus.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.response.ReadRecordsResponse
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 심박 버킷팅 의미론 고정 (#140 리뷰에서 제안): 경계 포함성(시작 포함·끝 제외),
 * 샘플 없음 = null(Tier B 후보), 세션 겹침 이중 집계, 세션 읽기 페이지네이션.
 */
class ExerciseRepositoryTest {

    private val t0: Instant = Instant.parse("2026-07-15T10:00:00Z")

    private fun session(id: String, start: Instant, end: Instant) = ExerciseSessionRecord(
        startTime = start,
        startZoneOffset = null,
        endTime = end,
        endZoneOffset = null,
        metadata = autoMetadata(id = id),
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    )

    private fun hrRecord(samples: List<Pair<Instant, Long>>) = HeartRateRecord(
        startTime = samples.minOf { it.first },
        startZoneOffset = null,
        endTime = samples.maxOf { it.first }.plusSeconds(1),
        endZoneOffset = null,
        samples = samples.map { (time, bpm) -> HeartRateRecord.Sample(time, bpm) },
        metadata = autoMetadata(id = "hr"),
    )

    private fun client(
        sessionPages: List<List<ExerciseSessionRecord>>,
        heartRatePages: List<List<HeartRateRecord>> = listOf(emptyList()),
    ) = FakeHealthConnectClient().apply {
        readPagesByType[ExerciseSessionRecord::class] = ArrayDeque(
            sessionPages.mapIndexed { i, page ->
                ReadRecordsResponse(page, if (i < sessionPages.lastIndex) "p${i + 1}" else null)
            },
        )
        readPagesByType[HeartRateRecord::class] = ArrayDeque(
            heartRatePages.mapIndexed { i, page ->
                ReadRecordsResponse(page, if (i < heartRatePages.lastIndex) "h${i + 1}" else null)
            },
        )
    }

    @Test
    fun boundary_startInclusive_endExclusive_matchesOldAggregate() = runTest {
        val end = t0.plusSeconds(600)
        val client = client(
            sessionPages = listOf(listOf(session("s1", t0, end))),
            heartRatePages = listOf(
                listOf(hrRecord(listOf(t0 to 100L, end to 200L))), // 시작 정각 포함, 끝 정각 제외
            ),
        )

        val result = ExerciseRepository(client).readRecentSessions()

        assertEquals(100L, result.single().avgHeartRate)
    }

    @Test
    fun sessionWithoutSamples_hasNullHeartRate() = runTest {
        val client = client(
            sessionPages = listOf(listOf(session("s1", t0, t0.plusSeconds(600)))),
            heartRatePages = listOf(emptyList()),
        )

        val result = ExerciseRepository(client).readRecentSessions()

        assertNull(result.single().avgHeartRate) // 실패가 아닌 "심박 없음" — Tier B 후보 (#130)
    }

    @Test
    fun overlappingSessions_sampleCountsForBoth() = runTest {
        val client = client(
            sessionPages = listOf(
                listOf(
                    session("s1", t0, t0.plusSeconds(600)),
                    session("s2", t0.plusSeconds(300), t0.plusSeconds(900)),
                ),
            ),
            heartRatePages = listOf(listOf(hrRecord(listOf(t0.plusSeconds(450) to 150L)))),
        )

        val result = ExerciseRepository(client).readRecentSessions()

        assertEquals(listOf(150L, 150L), result.map { it.avgHeartRate }) // 기존 aggregate 의미론
    }

    @Test
    fun sessionRead_followsPagination() = runTest {
        val client = client(
            sessionPages = listOf(
                listOf(session("s1", t0, t0.plusSeconds(60))),
                listOf(session("s2", t0.plusSeconds(3_600), t0.plusSeconds(3_660))),
            ),
        )

        val result = ExerciseRepository(client).readRecentSessions()

        assertEquals(listOf("s2", "s1"), result.map { it.id }) // 페이지 초과분 무절단 + 최신순 정렬
    }
}
