package com.nexus.app.health

import android.os.RemoteException
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nexus.core.StreakCalculator
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
        val records = readSessions(end.minus(Duration.ofHours(lookbackHours)), end)
        if (records.isEmpty()) return null
        val latestEnd = records.maxOf { it.endTime }
        val nightStart = latestEnd.minus(Duration.ofHours(NIGHT_SPAN_HOURS))
        val nightMinutes = records
            .filter { it.endTime >= nightStart }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        return nightMinutes / MINUTES_PER_HOUR
    }

    /**
     * 연속 수면 기록일 (#359) — 오늘부터 거슬러, 밤별 기록 유무를 [StreakCalculator]로 센다.
     * 밤 날짜는 세션 종료 시각의 로컬 날짜(11시~7시 수면은 아침이 속한 날). 워치 미착용/미기록
     * 밤에서 끊긴다. 기록 없으면 0 — 이 신호가 수면 스트릭 배지를 연다.
     */
    suspend fun sleepStreakDays(days: Int = STREAK_WINDOW_DAYS): Int {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val end = Instant.now()
        val records = readSessions(end.minus(Duration.ofDays(days.toLong())), end)
        if (records.isEmpty()) return 0
        val nights = records.map { it.endTime.atZone(zone).toLocalDate() }.toSet()
        // 오래된→최신, 마지막이 오늘 — currentStreak가 끝(오늘)에서부터 이어진 밤을 센다
        val series = (days - 1 downTo 0).map { today.minusDays(it.toLong()) in nights }
        return StreakCalculator.currentStreak(series)
    }

    private suspend fun readSessions(start: Instant, end: Instant): List<SleepSessionRecord> {
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
        return records
    }

    private companion object {
        const val LOOKBACK_HOURS = 36L
        const val NIGHT_SPAN_HOURS = 18L
        const val MINUTES_PER_HOUR = 60.0
        const val STREAK_WINDOW_DAYS = 30
    }
}

/** 수면 읽기는 부가 정보 (#180) — 실패해도 컨디션은 활동 기반으로(무보정). 취소는 전파(#130). */
internal suspend fun sleepHoursOrNull(repo: SleepRepository?): Double? {
    repo ?: return null
    return try {
        repo.lastNightSleepHours()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.w(SLEEP_READ_TAG, "sleep read IO failure", e)
        null
    } catch (e: RemoteException) {
        Log.w(SLEEP_READ_TAG, "sleep read remote failure", e)
        null
    } catch (e: SecurityException) {
        Log.w(SLEEP_READ_TAG, "sleep read permission failure", e)
        null
    } catch (e: IllegalStateException) {
        Log.w(SLEEP_READ_TAG, "sleep read state failure", e)
        null
    }
}

/** 수면 스트릭 읽기 (#359) — 부가 정보라 실패는 0(배지 미해금, 화면 유지). 취소는 전파(#130). */
internal suspend fun sleepStreakOrZero(repo: SleepRepository?): Int {
    repo ?: return 0
    return try {
        repo.sleepStreakDays()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.w(SLEEP_READ_TAG, "sleep streak IO failure", e)
        0
    } catch (e: RemoteException) {
        Log.w(SLEEP_READ_TAG, "sleep streak remote failure", e)
        0
    } catch (e: SecurityException) {
        Log.w(SLEEP_READ_TAG, "sleep streak permission failure", e)
        0
    } catch (e: IllegalStateException) {
        Log.w(SLEEP_READ_TAG, "sleep streak state failure", e)
        0
    }
}

private const val SLEEP_READ_TAG = "SleepRepository"
