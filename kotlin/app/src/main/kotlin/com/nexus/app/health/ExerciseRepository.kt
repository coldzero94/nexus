package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nexus.core.ActivityType
import java.time.Duration
import java.time.Instant

/**
 * 운동 세션 요약 (#8). [type]은 3축(걷기/러닝/근력) 매핑 — 그 외는 null(표시만, XP 제외 후보).
 * [avgHeartRate]는 세션 범위 심박 평균 — 있으면 Tier A 판정 입력(#9).
 */
data class ExerciseSummary(
    val id: String,
    val type: ActivityType?,
    val exerciseTypeRaw: Int,
    val start: Instant,
    val end: Instant,
    val durationMinutes: Long,
    val avgHeartRate: Long?,
)

/** 운동 세션 읽기 (#8) — ExerciseSession 3축 매핑 + 세션 범위 심박 연계. */
class ExerciseRepository(private val client: HealthConnectClient) {

    suspend fun readRecentSessions(days: Int = 7): List<ExerciseSummary> {
        require(days >= 1) { "days must be >= 1" }
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days.toLong()))
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        return response.records
            .sortedByDescending { it.startTime }
            .map { session ->
                ExerciseSummary(
                    id = session.metadata.id,
                    type = mapType(session.exerciseType),
                    exerciseTypeRaw = session.exerciseType,
                    start = session.startTime,
                    end = session.endTime,
                    durationMinutes = Duration.between(session.startTime, session.endTime).toMinutes(),
                    avgHeartRate = avgHeartRate(session.startTime, session.endTime),
                )
            }
    }

    /** 세션 범위 심박 평균. 실패/없음 시 null (심박 없는 세션 = Tier B 후보). */
    private suspend fun avgHeartRate(start: Instant, end: Instant): Long? =
        runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(HeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )[HeartRateRecord.BPM_AVG]
        }.getOrNull()

    private fun mapType(raw: Int): ActivityType? = when (raw) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ActivityType.WALKING
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        -> ActivityType.RUNNING
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> ActivityType.STRENGTH
        else -> null
    }
}
