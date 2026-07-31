package com.nexus.app.widget

import android.content.Context

/**
 * 위젯 스냅샷 계약 (#39, E6-1) — 앱·워커가 **쓰고** 위젯이 **읽기만** 하는 얇은 상태.
 * 위젯 프로세스에서 HC 조회·DB 스캔 같은 무거운 로드를 하지 않기 위한 경계다(갱신은 E6-2).
 * 원시 건강 수치는 담지 않는다 — 계산된 표시값만(레벨·컨디션·오늘 XP).
 */
data class WidgetSnapshot(
    val level: Int,
    val condition: Int,
    val todayXp: Int,
    val spriteState: String,
    /** 원정 시작 시각(0=미진행) — 남은 시간은 렌더 시점에 core로 계산(#72, 장치 ②). */
    val expeditionStartedAt: Long = 0L,
    /** 아침 카드 미확인(#72, 장치 ④). 갱신 주기만큼 지연 가능(≤15분). */
    val morningPending: Boolean = false,
    /** 저녁 일지 미확인(#72, 장치 ④). */
    val journalPending: Boolean = false,
)

class WidgetSnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): WidgetSnapshot = WidgetSnapshot(
        level = prefs.getInt(KEY_LEVEL, 1),
        // 콜드 기본값은 core와 단일 원천 — 밸런스 튜닝 시 위젯만 어긋나지 않게 (#39 리뷰 N2)
        condition = prefs.getInt(KEY_CONDITION, com.nexus.core.ConditionEngine.DEFAULT.toInt()),
        todayXp = prefs.getInt(KEY_TODAY_XP, 0),
        spriteState = prefs.getString(KEY_SPRITE, "idle").orEmpty().ifEmpty { "idle" },
        expeditionStartedAt = prefs.getLong(KEY_EXPEDITION_AT, 0L),
        morningPending = prefs.getBoolean(KEY_MORNING, false),
        journalPending = prefs.getBoolean(KEY_JOURNAL, false),
    )

    /**
     * 마지막으로 **푸시를 시도하고 돌아온** 렌더 키 (#246) — 스냅샷 + 시각 의존 표시를 합친 값.
     *
     * 위젯은 이걸 읽지 않는다(스냅샷 계약 밖). 무변화 갱신 스킵 판정만을 위한 메모다.
     *
     * "실제로 그려졌다"까지는 보장하지 못한다 — `updateAll`은 Glance 세션 워커에 일을 넘기고
     * 즉시 반환하고, 그 뒤 합성이 실패하면 Glance가 자체 에러 레이아웃을 그린 뒤 삼킨다
     * (`onCompositionError` 기본 구현). 그래서 이 키만으로 스킵하면 안 되고
     * [WidgetUpdater]가 시간 상한을 함께 본다.
     */
    var lastRenderKey: String
        get() = prefs.getString(KEY_RENDER, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_RENDER, value).apply()
        }

    /** 마지막 푸시 시도 시각 — 스킵이 무한히 이어지지 않게 하는 상한의 기준점 (#246). */
    var lastPushedAtMillis: Long
        get() = prefs.getLong(KEY_PUSHED_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_PUSHED_AT, value).apply()
        }

    /**
     * 스킵 메모를 지운다 — 다음 갱신이 반드시 푸시하게.
     *
     * 위젯이 처음 배치될 때 부른다. 이 메모는 **이 기기에서 위젯에 무엇을 밀어 넣었는가**라는
     * 기기 지역 사실인데 프리퍼런스가 클라우드 백업을 타고 복원되면(#238이 `nexus_sync`·
     * `nexus_telemetry_firsts`에서 같은 문제를 막았다) 위젯을 한 번도 그린 적 없는 새 기기에
     * "이미 그렸음"이 실린다.
     */
    fun clearPushMemo() {
        prefs.edit().remove(KEY_RENDER).remove(KEY_PUSHED_AT).apply()
    }

    fun write(snapshot: WidgetSnapshot) {
        prefs.edit()
            .putInt(KEY_LEVEL, snapshot.level)
            .putInt(KEY_CONDITION, snapshot.condition)
            .putInt(KEY_TODAY_XP, snapshot.todayXp)
            .putString(KEY_SPRITE, snapshot.spriteState)
            .putLong(KEY_EXPEDITION_AT, snapshot.expeditionStartedAt)
            .putBoolean(KEY_MORNING, snapshot.morningPending)
            .putBoolean(KEY_JOURNAL, snapshot.journalPending)
            .apply()
    }

    private companion object {
        const val PREFS = "nexus_widget_snapshot"
        const val KEY_LEVEL = "level"
        const val KEY_CONDITION = "condition"
        const val KEY_TODAY_XP = "today_xp"
        const val KEY_SPRITE = "sprite_state"
        const val KEY_EXPEDITION_AT = "expedition_started_at"
        const val KEY_MORNING = "morning_pending"
        const val KEY_JOURNAL = "journal_pending"
        const val KEY_RENDER = "last_render_key"
        const val KEY_PUSHED_AT = "last_pushed_at"
    }
}
