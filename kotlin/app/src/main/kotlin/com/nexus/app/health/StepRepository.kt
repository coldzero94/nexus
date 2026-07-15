package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nexus.core.RecordingMethod
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period

/** 하루치 걸음 집계 결과 (일별 요약의 걸음 축 — DailySummary의 일부). */
data class DailySteps(
    val date: LocalDate,
    val steps: Long,
)

/**
 * 걸음 읽기 (#7). **aggregate(COUNT_TOTAL)만 사용** — readRecords 직접 사용 금지(이중 카운트).
 * LocalDateTime 기반 슬라이싱이라 버킷 경계 = 기기 로컬(KST) 자정.
 */
class StepRepository(
    private val client: HealthConnectClient,
) {
    suspend fun readDailySteps(days: Int = 7): List<DailySteps> {
        require(days >= 1) { "days must be >= 1" }
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).toLong()).atStartOfDay()
        val end = LocalDateTime.of(today, LocalTime.MAX)

        val buckets =
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
        // 데이터 없는 날은 버킷이 빠질 수 있어, 요청 구간 전체를 0으로 채운 뒤 덮어쓴다.
        val byDate = buckets.associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        return (0 until days).map { offset ->
            val date = today.minusDays((days - 1 - offset).toLong())
            DailySteps(date = date, steps = byDate[date] ?: 0L)
        }
    }

    /**
     * 신뢰 필터(#9): 수기 입력(MANUAL_ENTRY) 걸음 합. XP 제외 대상 식별용.
     * per-record recordingMethod는 aggregate로 볼 수 없어 여기서만 readRecords 사용 —
     * 신뢰 총합은 여전히 [readDailySteps](aggregate)를 쓴다(이중 카운트 방지). dedup 정밀화는 E3.
     */
    suspend fun readManualStepCount(days: Int = 7): Long {
        require(days >= 1) { "days must be >= 1" }
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days.toLong()))
        val response =
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
        return response.records
            .filter { it.metadata.recordingMethod.toRecordingMethod() == RecordingMethod.MANUAL_ENTRY }
            .sumOf { it.count }
    }
}
