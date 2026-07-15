package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period

/** 하루치 걸음 집계 결과 (일별 요약의 걸음 축 — DailySummary의 일부). */
data class DailySteps(val date: LocalDate, val steps: Long)

/**
 * 걸음 읽기 (#7). **aggregate(COUNT_TOTAL)만 사용** — readRecords 직접 사용 금지(이중 카운트).
 * LocalDateTime 기반 슬라이싱이라 버킷 경계 = 기기 로컬(KST) 자정.
 */
class StepRepository(private val client: HealthConnectClient) {

    suspend fun readDailySteps(days: Int = 7): List<DailySteps> {
        require(days >= 1) { "days must be >= 1" }
        val today = LocalDate.now()
        val start = today.minusDays((days - 1).toLong()).atStartOfDay()
        val end = LocalDateTime.of(today, LocalTime.MAX)

        val buckets = client.aggregateGroupByPeriod(
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
}
