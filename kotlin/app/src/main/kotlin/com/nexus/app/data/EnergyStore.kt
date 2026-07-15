package com.nexus.app.data

import android.content.Context
import com.nexus.core.EnergyEngine

/**
 * 에너지 소모 카운터 (#67) — 획득은 원장 파생(EnergyEngine KDoc)이라 소모만 영속화.
 * 단조 증가 카운터 — 소모 취소 없음(원정 시작은 확정 행위).
 */
class EnergyStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val totalSpent: Int
        get() = prefs.getInt(KEY_SPENT, 0)

    /** [cost] 소모 시도 — 잔액 부족이면 false(차감 없음). */
    fun trySpend(cappedTotalXp: Int, cost: Int): Boolean {
        if (!EnergyEngine.canSpend(cappedTotalXp, totalSpent, cost)) return false
        prefs.edit().putInt(KEY_SPENT, totalSpent + cost).apply()
        return true
    }

    private companion object {
        const val PREFS = "nexus_energy"
        const val KEY_SPENT = "total_spent"
    }
}
