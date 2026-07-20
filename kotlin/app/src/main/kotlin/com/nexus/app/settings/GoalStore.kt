package com.nexus.app.settings

import android.content.Context

/**
 * 주간 활동 목표 (#73, E7-5) — 온보딩에서 수집, 설정에서 변경. 개인 계수 시스템의 입력으로
 * 수집만 하고, 산식 반영(콜드스타트 시드·균형 보너스 기준 개인화)은 밸런스 라운드(#77·#62)에서 —
 * 산식 변경은 버전 태그 원자 세트 대상이라 여기서 건드리지 않는다.
 */
class GoalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var weeklyGoalDays: Int
        get() = prefs.getInt(KEY_WEEKLY_DAYS, DEFAULT_WEEKLY_DAYS)
        set(value) {
            prefs.edit().putInt(KEY_WEEKLY_DAYS, value.coerceIn(MIN_DAYS, MAX_DAYS)).apply()
        }

    companion object {
        /** 기본 주 4일 — 균형 보너스 기준(주 4일+ 활동, MVP §5)과 정합. */
        const val DEFAULT_WEEKLY_DAYS = 4
        const val MIN_DAYS = 2
        const val MAX_DAYS = 6

        private const val PREFS = "nexus_goal"
        private const val KEY_WEEKLY_DAYS = "weekly_goal_days"
    }
}
