package com.nexus.app.health

import android.content.Context

/**
 * 동기화 상태 저장 계약 (#146) — 프로덕션은 [TokenStore](SharedPreferences),
 * 테스트는 인메모리 페이크. [HealthConnectSync]는 이 인터페이스에만 의존한다.
 */
interface SyncStateStore {
    var changesToken: String?
    var lastSyncEpochMillis: Long
    var lastChangeCount: Int
    val lastTokenResetEpochMillis: Long
    val lostDeltaWindowStartEpochMillis: Long

    /** 토큰 리셋 마커 기록 — 계약은 [TokenStore.recordTokenReset] KDoc 참고. */
    fun recordTokenReset(resetAtEpochMillis: Long)
}

/**
 * Changes 토큰 + 마지막 동기화 상태 영속화 (#8). 단순 문자열/롱이라 SharedPreferences로 충분.
 * 로컬 온리 MVP — 서버 없음.
 */
class TokenStore(context: Context) : SyncStateStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var changesToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    override var lastSyncEpochMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SYNC, value).apply()
        }

    /** 마지막 동기화에서 감지한 변경(업서트+삭제) 개수. */
    override var lastChangeCount: Int
        get() = prefs.getInt(KEY_LAST_COUNT, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_COUNT, value).apply()
        }

    /** 마지막 Changes 토큰 리셋(30일 만료) 시각, 0 = 없음 (#141). [recordTokenReset]으로만 기록. */
    override val lastTokenResetEpochMillis: Long
        get() = prefs.getLong(KEY_LAST_TOKEN_RESET, 0L)

    /** 리셋으로 유실된 델타 구간의 시작(= 리셋 감지 시점의 lastSync), 0 = 미상 (#141). */
    override val lostDeltaWindowStartEpochMillis: Long
        get() = prefs.getLong(KEY_LOST_WINDOW_START, 0L)

    /**
     * 마지막 처리된-실패 분류와 연속 횟수 (#239) — 디버그 도구(#245)가 읽는 로컬 신호.
     *
     * 원격 관측만으로는 부족하다: DSN이 없으면(알파 초기) 아무것도 안 남고, 있어도 표본이 지연된다.
     * 기기에 "무엇이 몇 번 연속 실패했는가"가 있으면 테스터 폰을 손에 들고 즉석에서 판단할 수 있다.
     */
    val lastFailureCategory: String?
        get() = prefs.getString(KEY_LAST_FAILURE, null)

    val consecutiveFailures: Int
        get() = prefs.getInt(KEY_FAILURE_COUNT, 0)

    /** 실패 1건 — 같은 분류가 이어지면 횟수만 늘고, 분류가 바뀌면 1부터 다시 센다. */
    fun recordFailure(category: String) {
        val next = if (category == lastFailureCategory) consecutiveFailures + 1 else 1
        prefs.edit().putString(KEY_LAST_FAILURE, category).putInt(KEY_FAILURE_COUNT, next).apply()
    }

    /** 성공 — 카운터를 지운다. 회복 여부도 운영 판단에 필요하다. */
    fun clearFailure() {
        prefs.edit().remove(KEY_LAST_FAILURE).putInt(KEY_FAILURE_COUNT, 0).apply()
    }

    /**
     * 토큰 리셋 마커 기록 (#141) — E3 소급 재계산이 유실 구간 [시작, 리셋 시각]을 입력으로 쓴다.
     * 구간 시작은 호출 시점의 [lastSyncEpochMillis] — Worker가 sync() 직후 lastSync를 덮어써서
     * 리셋 지점에서 함께 보존하지 않으면 시작점이 즉시 파괴된다.
     * 두 값은 단일 edit로 원자 기록(반쪽 마커 방지). 마커는 최신 리셋 우선(latest-wins) —
     * E3 도착 전 리셋이 겹치면(≥30일 간격) 마지막 것만 남으며, 이는 상한이 뒤로 밀릴 뿐 안전하다.
     */
    override fun recordTokenReset(resetAtEpochMillis: Long) {
        prefs.edit()
            .putLong(KEY_LAST_TOKEN_RESET, resetAtEpochMillis)
            .putLong(KEY_LOST_WINDOW_START, lastSyncEpochMillis)
            .apply()
    }

    /** prefs 키 — 디버그 도구(#245)가 클린 리셋을 위해 이름으로 접근한다. */
    internal companion object {
        const val PREFS = "nexus_sync"
        const val KEY_TOKEN = "changes_token"
        const val KEY_LAST_SYNC = "last_sync_millis"
        const val KEY_LAST_COUNT = "last_change_count"
        const val KEY_LAST_TOKEN_RESET = "last_token_reset_millis"
        const val KEY_LOST_WINDOW_START = "lost_delta_window_start_millis"
        const val KEY_LAST_FAILURE = "last_failure_category"
        const val KEY_FAILURE_COUNT = "consecutive_failures"
    }
}
