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

    private fun <T : androidx.health.connect.client.records.Record> pages(
        pages: List<List<T>>,
        prefix: String,
    ): Map<String?, ReadRecordsResponse<out androidx.health.connect.client.records.Record>> =
        pages.mapIndexed { i, page ->
            val requestToken: String? = if (i == 0) null else "$prefix$i"
            val nextToken = if (i < pages.lastIndex) "$prefix${i + 1}" else null
            requestToken to ReadRecordsResponse(page, nextToken)
        }.toMap()

    private fun client(
        sessionPages: List<List<ExerciseSessionRecord>>,
        heartRatePages: List<List<HeartRateRecord>>? = listOf(emptyList()),
    ) = FakeHealthConnectClient().apply {
        readPagesByType[ExerciseSessionRecord::class] = pages(sessionPages, "p")
        // null = 심박 읽기 미스크립트 — 호출 자체가 실패해 "심박을 읽지 않았다"를 단언한다
        if (heartRatePages != null) readPagesByType[HeartRateRecord::class] = pages(heartRatePages, "h")
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
    fun heartRateRead_followsPagination_acrossPages() = runTest {
        // 페이지 1·2에 샘플 분산 — 루프가 1페이지만 읽으면 평균이 100으로 틀어진다 (#140 감사의 실패 모드)
        val client = client(
            sessionPages = listOf(listOf(session("s1", t0, t0.plusSeconds(600)))),
            heartRatePages = listOf(
                listOf(hrRecord(listOf(t0.plusSeconds(100) to 100L))),
                listOf(hrRecord(listOf(t0.plusSeconds(200) to 200L))),
            ),
        )

        val result = ExerciseRepository(client).readRecentSessions()

        assertEquals(150L, result.single().avgHeartRate)
    }

    @Test
    fun sampleBetweenSessions_countsForNeither() = runTest {
        // 창 [minStart, maxEnd] 안이지만 어떤 세션에도 속하지 않는 샘플 — 인접 버킷 배정 회귀 방지
        val client = client(
            sessionPages = listOf(
                listOf(
                    session("s1", t0, t0.plusSeconds(600)),
                    session("s2", t0.plusSeconds(1_200), t0.plusSeconds(1_800)),
                ),
            ),
            heartRatePages = listOf(listOf(hrRecord(listOf(t0.plusSeconds(900) to 150L)))),
        )

        val result = ExerciseRepository(client).readRecentSessions()

        assertEquals(listOf(null, null), result.map { it.avgHeartRate })
    }

    @Test
    fun emptySessionList_skipsHeartRateRead() = runTest {
        // 심박 페이지 미스크립트 — 심박 읽기를 시도하면 페이크가 즉시 실패한다
        val client = client(sessionPages = listOf(emptyList()), heartRatePages = null)

        val result = ExerciseRepository(client).readRecentSessions()

        assertEquals(emptyList(), result)
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
