package com.nexus.app.home

import android.content.Context

/**
 * 첫 만남 날짜와 마지막으로 축하한 기념일 (#111, E5-16).
 *
 * ## 왜 별도 저장소인가
 *
 * "언제 만났는가"의 진실이 지금 어디에도 없다. `InstallIdStore`는 **첫 호출 시점**에 UUID를
 * 만들 뿐 날짜를 안 남기고, 온보딩 저장소는 완료 여부만 갖는다. 원장의 첫 이벤트는 첫 **활동**이지
 * 첫 만남이 아니다 — 설치하고 사흘 뒤 처음 걸었다면 그 사흘이 사라진다.
 *
 * ## 이미 쓰던 사람에게는 원장 첫날을 준다
 *
 * 알파 테스터는 이 코드가 없던 때 이미 설치했다. 그들에게 "오늘 만났다"고 하면 기념일 시계가
 * 리셋되는데, 그건 이 기능이 만들려는 것(누적된 시간 = 애착 자산)의 정반대다. 그래서 최초 해석 때
 * **원장의 가장 이른 활동일**을 만난 날로 삼는다 — 실제보다 늦을 수는 있어도 앞서지는 않는다.
 * 원장이 비어 있으면(정말 신규) 오늘이다.
 *
 * 한 번 정해지면 안 바뀐다. 매번 다시 계산하면 원장이 정리되거나 백업이 복원될 때 만난 날이
 * 흔들리고, 기념일이 뒤로 갔다 앞으로 갔다 한다.
 */
class TogetherStore(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /**
     * 첫 만남 일자(epochDay) — 처음 물어볼 때 확정하고 그 뒤로 고정.
     *
     * @param ledgerFirstEpochDay 원장의 가장 이른 활동일. 없으면 null.
     * @param todayEpochDay 오늘.
     */
    fun firstMetEpochDay(ledgerFirstEpochDay: Long?, todayEpochDay: Long): Long {
        val stored = prefs.getLong(KEY_FIRST_MET, NOT_SET)
        if (stored != NOT_SET) return stored
        // 원장 첫날이 오늘보다 뒤일 수는 없지만(과거 기록이다) 시계 조작을 배제하지 않는다
        val resolved = minOf(ledgerFirstEpochDay ?: todayEpochDay, todayEpochDay)
        prefs.edit().putLong(KEY_FIRST_MET, resolved).apply()
        return resolved
    }

    /** 마지막으로 축하한 기념일의 일수. 아직 없으면 0. */
    var celebratedDays: Int
        get() = prefs.getInt(KEY_CELEBRATED, 0)
        set(value) {
            // 뒤로 가지 않는다 — 낮은 값이 들어오면 이미 축하한 기념일이 다시 뜬다
            if (value > celebratedDays) prefs.edit().putInt(KEY_CELEBRATED, value).apply()
        }

    internal companion object {
        const val PREFS = "nexus_together"
        private const val KEY_FIRST_MET = "first_met_epoch_day"
        private const val KEY_CELEBRATED = "celebrated_days"
        private const val NOT_SET = -1L
    }
}
