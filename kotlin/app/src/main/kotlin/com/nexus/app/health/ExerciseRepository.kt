package com.nexus.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nexus.core.ActivityType
import com.nexus.core.DataOriginAllowlist
import com.nexus.core.DeviceSourceResolver
import com.nexus.core.RecordingMethod
import com.nexus.core.TrustExplainer
import com.nexus.core.TrustReason
import com.nexus.core.TrustTier
import com.nexus.core.tier
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
    /**
     * 등급 근거 (#222·#205) — **등급과 같은 allowlist로 한 번만** 계산한다.
     *
     * 화면이 다시 계산하게 두면 안 된다. 병합된 allowlist는 리포지토리에만 있어서, 컴포저블이
     * `reasonFor`를 부르면 기본 allowlist로 떨어져 **"Tier B"인데 근거는 "미등록 소스"**가 된다 —
     * 행은 XP에 반영된다고 하고 근거는 제외 대상이라고 말하는 모순이다. `core/Trust.kt`가 등급과
     * 근거를 한 분기에 모아 둔 것과 같은 이유다.
     */
    val trustReason: TrustReason,
)

/**
 * 운동 세션 읽기 (#8) — ExerciseSession 3축 매핑 + 세션 범위 심박 연계.
 *
 * @param deviceSources 관측된 현재 기기 온디바이스 소스의 누적 저장소 (#205). null이면 배치 관측만
 *   쓴다 — 그러면 등급이 읽기 창에 따라 달라지므로 **프로덕션은 반드시 넘긴다**
 *   ([DeviceSourceStore] KDoc). 순수 단위 테스트만 생략한다.
 */
class ExerciseRepository(
    private val client: HealthConnectClient,
    private val deviceSources: DeviceSourceStore? = null,
) {
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
        // 신뢰 등급 판정 전에 **현재 기기 온디바이스 소스를 관측해 allowlist에 병합**한다 (#205).
        // 하드코딩 기본값만 쓰면 2026-06 SPN 변경으로 소스 패키지가 달라진 순간, 사용자가 자기 폰으로
        // 자동 기록한 진짜 운동이 Tier C로 떨어져 XP에서 제외되고, 읽기 창을 지나면 복구도 불가능하다.
        val observed = sessions.map {
            DeviceIdentity.observe(it.metadata, it.metadata.recordingMethod.toRecordingMethod())
        }
        val batch = DeviceSourceResolver.onDeviceSources(observed)
        // 관측을 누적 저장한다 — 배치 안에서만 모으면 같은 세션이 7일 창에서 C, 28일 창에서 B가 되고
        // 그중 셋이 원장에 지급한다(창이 지나면 복구 기회도 닫힌다). [DeviceSourceStore] KDoc.
        deviceSources?.record(batch)
        val allowlist = DataOriginAllowlist.DEFAULT
            .withCurrentDeviceSources(batch + deviceSources?.sources.orEmpty())
        return sessions.map { session ->
            val hr = heartRates[session.metadata.id]
            val method = session.metadata.recordingMethod.toRecordingMethod()
            val origin = session.metadata.dataOrigin.packageName
            // 등급과 근거를 같은 allowlist로 한 번에 — 둘이 다른 allowlist를 보면 화면이 모순된 설명을 낸다
            val reason = TrustExplainer.reasonFor(method, origin, hasHeartRate = hr != null, allowlist = allowlist)
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
                trustTier = reason.tier,
                trustReason = reason,
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
