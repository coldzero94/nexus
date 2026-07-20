package com.nexus.app.widget

import android.content.Context

/**
 * 위젯 스냅샷 계약 (#39, E6-1) — 앱·워커가 **쓰고** 위젯이 **읽기만** 하는 얇은 상태.
 * 위젯 프로세스에서 HC 조회·DB 스캔 같은 무거운 로드를 하지 않기 위한 경계다(갱신은 E6-2).
 * 원시 건강 수치는 담지 않는다 — 계산된 표시값만(레벨·컨디션·오늘 XP).
 */
data class WidgetSnapshot(val level: Int, val condition: Int, val todayXp: Int, val spriteState: String)

class WidgetSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): WidgetSnapshot = WidgetSnapshot(
        level = prefs.getInt(KEY_LEVEL, 1),
        // 콜드 기본값은 core와 단일 원천 — 밸런스 튜닝 시 위젯만 어긋나지 않게 (#39 리뷰 N2)
        condition = prefs.getInt(KEY_CONDITION, com.nexus.core.ConditionEngine.DEFAULT.toInt()),
        todayXp = prefs.getInt(KEY_TODAY_XP, 0),
        spriteState = prefs.getString(KEY_SPRITE, "idle").orEmpty().ifEmpty { "idle" },
    )

    fun write(snapshot: WidgetSnapshot) {
        prefs.edit()
            .putInt(KEY_LEVEL, snapshot.level)
            .putInt(KEY_CONDITION, snapshot.condition)
            .putInt(KEY_TODAY_XP, snapshot.todayXp)
            .putString(KEY_SPRITE, snapshot.spriteState)
            .apply()
    }

    private companion object {
        const val PREFS = "nexus_widget_snapshot"
        const val KEY_LEVEL = "level"
        const val KEY_CONDITION = "condition"
        const val KEY_TODAY_XP = "today_xp"
        const val KEY_SPRITE = "sprite_state"
    }
}
