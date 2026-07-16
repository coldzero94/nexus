package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

/**
 * 수면 읽기 (#180, E4-12) — 지난밤 총 수면 시간을 [ConditionEngine.applySleep] 입력으로 낸다.
 * 삼성헬스→HC 동기화가 희소·지연될 수 있어(#122류) 기록이 없으면 null(호출자가 무보정 처리).
 */
class SleepRepository(private val client: HealthConnectClient) {

    /**
     * 가장 최근 밤의 총 수면 시간(시간). [lookbackHours] 내 세션을 읽고, 가장 늦게 끝난 세션 기준
     * [nightSpanHours] 안의 조각(분절 수면)만 합산해 두 밤이 섞이는 것을 막는다. 기록 없으면 null.
     */
    suspend fun lastNightSleepHours(lookbackHours: Long = LOOKBACK_HOURS): Double? {
        val end = Instant.now()
        val start = end.minus(Duration.ofHours(lookbackHours))
        val records = mutableListOf<SleepSessionRecord>()
        var pageToken: String? = null
        do {
            val page =
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken,
                    ),
                )
            records += page.records
            pageToken = page.pageToken
        } while (pageToken != null)
        if (records.isEmpty()) return null
        val latestEnd = records.maxOf { it.endTime }
        val nightStart = latestEnd.minus(Duration.ofHours(NIGHT_SPAN_HOURS))
        val nightMinutes = records
            .filter { it.endTime >= nightStart }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        return nightMinutes / MINUTES_PER_HOUR
    }

    private companion object {
        const val LOOKBACK_HOURS = 36L
        const val NIGHT_SPAN_HOURS = 18L
        const val MINUTES_PER_HOUR = 60.0
    }
}
