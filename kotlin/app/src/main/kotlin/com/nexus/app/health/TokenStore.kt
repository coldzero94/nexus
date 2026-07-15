package com.nexus.app.health

import android.content.Context

/**
 * Changes 토큰 + 마지막 동기화 상태 영속화 (#8). 단순 문자열/롱이라 SharedPreferences로 충분.
 * 로컬 온리 MVP — 서버 없음.
 */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var changesToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var lastSyncEpochMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SYNC, value).apply()
        }

    /** 마지막 동기화에서 감지한 변경(업서트+삭제) 개수. */
    var lastChangeCount: Int
        get() = prefs.getInt(KEY_LAST_COUNT, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_COUNT, value).apply()
        }

    /**
     * 마지막 Changes 토큰 리셋(30일 만료) 시각, 0 = 없음 (#141).
     * 리셋 = 그 시점까지의 델타 유실 — E3 소급 재계산이 "이 시각까지 재조회"의 입력으로 쓴다.
     */
    var lastTokenResetEpochMillis: Long
        get() = prefs.getLong(KEY_LAST_TOKEN_RESET, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_TOKEN_RESET, value).apply()
        }

    /**
     * 리셋으로 유실된 델타 구간의 시작(= 리셋 감지 시점의 lastSync), 0 = 미상 (#141).
     * 리셋 지점에서 함께 저장해야 한다 — Worker가 sync() 직후 lastSync를 덮어써서
     * 시작점이 즉시 파괴되므로, 이 마커 없이는 E3이 최대 30일을 보수적으로 재조회해야 한다.
     */
    var lostDeltaWindowStartEpochMillis: Long
        get() = prefs.getLong(KEY_LOST_WINDOW_START, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LOST_WINDOW_START, value).apply()
        }

    private companion object {
        const val PREFS = "nexus_sync"
        const val KEY_TOKEN = "changes_token"
        const val KEY_LAST_SYNC = "last_sync_millis"
        const val KEY_LAST_COUNT = "last_change_count"
        const val KEY_LAST_TOKEN_RESET = "last_token_reset_millis"
        const val KEY_LOST_WINDOW_START = "lost_delta_window_start_millis"
    }
}
