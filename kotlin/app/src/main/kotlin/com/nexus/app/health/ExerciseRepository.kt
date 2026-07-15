package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nexus.core.ActivityType
import com.nexus.core.RecordingMethod
import com.nexus.core.TrustPolicy
import com.nexus.core.TrustTier
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * 운동 세션 요약 (#8·#9). [type]은 3축 매핑 — 그 외 null. [avgHeartRate] 있으면 Tier A 판정 입력.
 * [trustTier]는 provenance(#9) 판정 결과 — C는 XP 제외.
 */
data class ExerciseSummary(
    val id: String,
    val type: ActivityType?,
    val exerciseTypeRaw: Int,
    val start: Instant,
    val end: Instant,
    val durationMinutes: Long,
    val avgHeartRate: Long?,
    val dataOrigin: String,
    val recordingMethod: RecordingMethod,
    val trustTier: TrustTier,
)

/** 운동 세션 읽기 (#8) — ExerciseSession 3축 매핑 + 세션 범위 심박 연계. */
class ExerciseRepository(private val client: HealthConnectClient) {
    suspend fun readRecentSessions(days: Int = 7): List<ExerciseSummary> {
        require(days >= 1) { "days must be >= 1" }
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(days.toLong()))
        // 세션 읽기도 페이지네이션 — 페이지(기본 1000건) 초과분이 로그 없이 잘리는 것을 방지(#140 감사)
        val records = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null
        do {
            val page =
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken,
                    ),
                )
            records += page.records
            pageToken = page.pageToken
        } while (pageToken != null)
        val sessions = records.sortedByDescending { it.startTime }
        val heartRates = avgHeartRateBySession(sessions)
        return sessions.map { session ->
            val hr = heartRates[session.metadata.id]
            val method = session.metadata.recordingMethod.toRecordingMethod()
            val origin = session.metadata.dataOrigin.packageName
            ExerciseSummary(
                id = session.metadata.id,
                type = mapType(session.exerciseType),
                exerciseTypeRaw = session.exerciseType,
                start = session.startTime,
                end = session.endTime,
                durationMinutes = Duration.between(session.startTime, session.endTime).toMinutes(),
                avgHeartRate = hr,
                dataOrigin = origin,
                recordingMethod = method,
                trustTier = TrustPolicy.classify(method, origin, hasHeartRate = hr != null),
            )
        }
    }

    /**
     * 세션별 평균 심박 — 창 전체 HeartRateRecord를 **상수 회**(페이지네이션) 읽고 세션 시간대로
     * 인메모리 버킷팅한다 (#140: 세션마다 aggregate 하던 N+1 제거 → 레이트리밋 증폭·실패 지점 축소).
     * kotlin.md의 'readRecords 금지'는 걸음 이중집계 방지용 — 심박 버킷팅은 해당 없음(#140 확인).
     *
     * 샘플 없는 세션 = 맵에 없음(null → Tier B 후보). 조회 "실패"는 삼키지 않고 전파한다(#130) —
     * 실패를 null로 합치면 일시 오류가 Tier 강등으로 굳는다. 의도된 blast radius는 그대로:
     * 읽기 실패 = 배치 전체 실패(부분 성공으로 잘못된 티어를 만들지 않음). 모든 호출 경로
     * (활동·성장 화면)가 #130 catch 계약으로 에러 표시 처리한다(코루틴 취소는 자연 전파).
     *
     * 평균은 샘플 bpm의 산술 평균을 [roundToLong] — HC BPM_AVG와 반올림 단위가 다를 수 있으나
     * 용도가 Tier A 판정(심박 유무)과 표시라 ±1 오차는 무영향.
     */
    private suspend fun avgHeartRateBySession(sessions: List<ExerciseSessionRecord>): Map<String, Long> {
        if (sessions.isEmpty()) return emptyMap()
        val windowStart = sessions.minOf { it.startTime }
        val windowEnd = sessions.maxOf { it.endTime }
        // (id, start, end) — 세션 수는 수십 규모라 샘플당 선형 매칭으로 충분. 겹치는 세션은
        // 기존 aggregate 의미론과 동일하게 양쪽 모두에 집계된다.
        val intervals = sessions.map { Triple(it.metadata.id, it.startTime, it.endTime) }
        val sums = HashMap<String, LongArray>() // id -> [합, 개수]
        var pageToken: String? = null
        do {
            val page =
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd),
                        pageToken = pageToken,
                    ),
                )
            for (record in page.records) {
                for (sample in record.samples) {
                    bucketSample(sample.time, sample.beatsPerMinute, intervals, sums)
                }
            }
            pageToken = page.pageToken
        } while (pageToken != null)
        return sums.mapValues { (_, acc) -> (acc[0].toDouble() / acc[1]).roundToLong() }
    }

    private fun bucketSample(
        time: Instant,
        bpm: Long,
        intervals: List<Triple<String, Instant, Instant>>,
        sums: MutableMap<String, LongArray>,
    ) {
        for ((id, start, end) in intervals) {
            // 경계는 기존 aggregate(TimeRangeFilter.between)와 동일하게 시작 포함·끝 제외
            if (time < start || time >= end) continue
            val acc = sums.getOrPut(id) { LongArray(2) }
            acc[0] += bpm
            acc[1]++
        }
    }

    private fun mapType(raw: Int): ActivityType? = when (raw) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ActivityType.WALKING

        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        -> ActivityType.RUNNING

        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> ActivityType.STRENGTH

        else -> null
    }
}
