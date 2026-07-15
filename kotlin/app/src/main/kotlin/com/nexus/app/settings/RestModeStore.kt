package com.nexus.app.settings

import android.content.Context
import java.time.LocalDate

/**
 * 휴식 모드 상태 (#31, E4-7) — 켜져 있는 동안 컨디션이 하락하지 않는다(회복은 그대로).
 * 시작일을 함께 저장해 컨디션 폴드가 "휴식 시작 이후의 날"에만 하락을 면제한다 —
 * 켜기 전의 공백까지 소급 면제되면 게이지가 거짓말을 하게 된다.
 */
class RestModeStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    /** 휴식 시작일(epochDay), 0 = 미설정. */
    val sinceEpochDay: Long
        get() = prefs.getLong(KEY_SINCE, 0L)

    fun setEnabled(value: Boolean, todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, value)
            .putLong(KEY_SINCE, if (value) todayEpochDay else 0L)
            .apply()
    }

    /** [epochDay]가 휴식 면제 대상인가 — 켜져 있고 시작일 이후. */
    fun isRestDay(epochDay: Long): Boolean = enabled && sinceEpochDay != 0L && epochDay >= sinceEpochDay

    private companion object {
        const val PREFS = "nexus_rest_mode"
        const val KEY_ENABLED = "enabled"
        const val KEY_SINCE = "since_epoch_day"
    }
}
