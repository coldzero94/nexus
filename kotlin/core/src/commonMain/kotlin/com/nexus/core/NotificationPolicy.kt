package com.nexus.core

/**
 * 알림 규율 (#33, E4-9) — 알림은 신뢰를 소모하는 자원이다. 하루 최대 [DAILY_CAP]건,
 * 조용 시간([QUIET_START_HOUR]~[QUIET_END_HOUR])에는 보내지 않는다.
 * 발송 여부 판정만 core에 — 실제 발송·카운트 저장은 앱 계층.
 */
object NotificationPolicy {
    /** 하루 최대 알림 수 — 팀 규율(백로그 E4-9), 늘리려면 백로그 합의 먼저. */
    const val DAILY_CAP = 2

    /** 조용 시간 시작(21시) — 저녁 이후 방해 금지. */
    const val QUIET_START_HOUR = 21

    /** 조용 시간 끝(9시) — 아침 전 방해 금지. */
    const val QUIET_END_HOUR = 9

    private const val LAST_HOUR_OF_DAY = 23

    /** [hourOfDay]가 조용 시간대인가 — 자정 걸침([21, 24)∪[0, 9)) 처리. */
    fun isQuietHour(hourOfDay: Int): Boolean {
        require(hourOfDay in 0..LAST_HOUR_OF_DAY) { "hourOfDay must be 0..23" }
        return hourOfDay >= QUIET_START_HOUR || hourOfDay < QUIET_END_HOUR
    }

    /** 발송 가능 판정 — 조용 시간이 아니고 오늘 발송 수가 상한 미만. */
    fun canNotify(hourOfDay: Int, sentToday: Int): Boolean {
        require(sentToday >= 0) { "sentToday must be >= 0" }
        return !isQuietHour(hourOfDay) && sentToday < DAILY_CAP
    }
}
