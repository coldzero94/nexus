package com.nexus.app.home

import android.content.Context

/**
 * 정산 기준점 (#35, E5-2) — 마지막으로 "개봉"한 누적 XP. 홈 진입 시 원장 합산이 이보다
 * 크면 그 차액이 "새로 도착한 성장"이고, 정산 카드를 띄운다. 기준점 소비는 사용자가
 * 카드를 확인한 시점(#61 패턴 — 감지 시점 소비는 회전·프로세스 사망 시 정산을 유실한다).
 * -1 = 최초(카드 없이 기준점만 설정). 원장 취소로 합산이 줄면 조용히 하향 동기화.
 */
class SettlementStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val lastSeenXp: Int
        get() = prefs.getInt(KEY_LAST_SEEN, UNSET)

    fun markSeen(totalXp: Int) {
        prefs.edit().putInt(KEY_LAST_SEEN, totalXp).apply()
    }

    companion object {
        const val UNSET = -1

        private const val PREFS = "nexus_settlement"
        private const val KEY_LAST_SEEN = "last_seen_xp"
    }
}
