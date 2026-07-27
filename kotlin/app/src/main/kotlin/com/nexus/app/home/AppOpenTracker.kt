package com.nexus.app.home

import android.content.Context
import com.nexus.core.OpenDays
import java.time.LocalDate

/**
 * 앱 실행일 마커 (#30, E4-6) — 복귀 감지의 기준점. 0 = 최초 실행.
 * 판정과 마커 갱신을 분리해, 환영 씬이 뜨기 전에 기준점이 지워지지 않게 한다.
 *
 * 오픈 날짜 집합(#286)도 여기서 함께 관리한다 — 매 실행마다 호출되는 유일한 지점이라 자연스럽고,
 * 값은 **앱이 전송하지 않는다**(계측·수동 백업 페이로드·서버 없음, 자동 백업은 본인 계정 표면).
 * 게이트 판정(`docs/ALPHA.md`)이 테스터의 기억 대신 이 숫자를 읽는다.
 */
class AppOpenTracker(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val lastOpenEpochDay: Long
        get() = prefs.getLong(KEY_LAST_OPEN, 0L)

    /** 최근 7일 중 앱을 연 서로 다른 날짜 수 (#286) — 설정 화면이 그대로 보여준다. */
    fun openDaysInWindow(todayEpochDay: Long = LocalDate.now().toEpochDay()): Int =
        OpenDays.countInWindow(storedDays(), todayEpochDay)

    /**
     * 복귀 판정 기준일 갱신 (#30) — 갭 판정 1회와 짝이라 세션당 한 번만 불린다.
     * 날짜 집합은 여기서 건드리지 않는다(그 가드 안에 있으면 날이 누락된다, #286 리뷰).
     */
    fun recordOpen(todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        prefs.edit().putLong(KEY_LAST_OPEN, todayEpochDay).apply()
    }

    /**
     * 오픈 날짜 기록 (#286) — **앱이 전면에 올 때마다** 부른다. 같은 날 여러 번은 집합이라 1회,
     * 보관 한도를 넘은 날짜는 record가 걸러내 저장값이 무한 증가하지 않는다.
     */
    fun recordOpenDay(todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        val days = OpenDays.record(storedDays(), todayEpochDay)
        prefs
            .edit()
            .putStringSet(KEY_OPEN_DAYS, days.mapTo(mutableSetOf()) { it.toString() })
            .apply()
    }

    /** 손상된 항목(숫자 아님)은 조용히 버린다 — 카운터는 부가 정보라 실패해도 앱이 멈추지 않는다. */
    private fun storedDays(): Set<Long> = prefs
        .getStringSet(KEY_OPEN_DAYS, emptySet())
        .orEmpty()
        .mapNotNullTo(mutableSetOf()) { it.toLongOrNull() }

    private companion object {
        const val PREFS = "nexus_app_open"
        const val KEY_LAST_OPEN = "last_open_epoch_day"
        const val KEY_OPEN_DAYS = "open_epoch_days"
    }
}
