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
 * ## 원장 첫날은 **소급 창보다 오래됐을 때만** 쓴다
 *
 * 알파 테스터는 이 코드가 없던 때 이미 설치했으니 그들에게 "오늘 만났다"고 하면 시계가 리셋된다.
 * 그래서 원장의 가장 이른 활동일을 보되, **그대로 쓰면 안 된다**: 홈 로드와 소급 지급(#44)이
 * 설치 직후 **최근 28일치 세션을 원장에 적는다**. 삼성헬스 이력이 있는 신규 사용자는 설치 당일에
 * 이미 28일 전 원장 행을 갖고, 그 값을 만난 날로 삼으면 **설치 3일 만에 "만난 지 한 달"**이 뜬다 —
 * 이 기능에서 가장 감정이 실린 카피가 거짓말이 되고, 알파 안에 닿는 기념일 둘이 그렇게 소진된다.
 *
 * 그래서 판정은 이렇다: 원장 첫날이 **소급 창보다 오래됐으면** 그건 이전 설치의 증거다(원장은
 * append-only라 28일보다 오래된 행은 그때부터 앱을 쓰고 있었다는 뜻이다). 창 안이면 설치 시점
 * 소급과 구분이 안 되므로 오늘로 본다. 창 안에 있던 기존 테스터는 시계를 잃지만, 그 손실이
 * 거짓 기념일보다 싸다.
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
        val backfillFloor = todayEpochDay - BACKFILL_WINDOW_DAYS
        val evidence = ledgerFirstEpochDay?.takeIf { it < backfillFloor } ?: todayEpochDay
        // 시계를 앞당겨 놓고 되돌린 기기에서도 "만난 지 -3일"이 나오면 안 된다
        val resolved = minOf(evidence, todayEpochDay)
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

        /**
         * 설치 직후 원장에 적히는 소급 창 (`HomeLoader.CONDITION_WINDOW_DAYS` ·
         * `ClassAffinity.WINDOW_DAYS`). 이 안의 원장 행은 이전 설치의 증거가 못 된다.
         */
        private const val BACKFILL_WINDOW_DAYS = 28L
    }
}
