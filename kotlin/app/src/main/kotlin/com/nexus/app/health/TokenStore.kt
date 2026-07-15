package com.nexus.app.health

import android.content.Context

/**
 * Changes 토큰 + 마지막 동기화 상태 영속화 (#8). 단순 문자열/롱이라 SharedPreferences로 충분.
 * 로컬 온리 MVP — 서버 없음.
 */
class TokenStore(
    context: Context,
) {
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

    private companion object {
        const val PREFS = "nexus_sync"
        const val KEY_TOKEN = "changes_token"
        const val KEY_LAST_SYNC = "last_sync_millis"
        const val KEY_LAST_COUNT = "last_change_count"
    }
}
