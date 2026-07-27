package com.nexus.core

/**
 * 앱 오픈 날짜 집계 (#286, E15-19) — 순수 함수. D14 게이트("주 3회+ = 서로 다른 오픈 날짜 ≥ 3",
 * `docs/ALPHA.md`)를 테스터의 기억이 아니라 **기기가 센 사실값**으로 답하게 한다.
 *
 * 날짜는 **앱이 전송하지 않는다** — 계측·수동 백업 페이로드·서버 없음. 화면에 숫자를 띄우고 테스터가
 * 읽어 알려주는 것이 전부다. 단 SharedPreferences라 Android 자동 백업(사용자 본인 계정) 표면에는
 * 포함된다 — 건강 파생값이 아닌 정수 날짜라 허용(#216 선례, `docs/BACKEND.md`).
 */
object OpenDays {
    /** 최근 며칠을 세는가 — 이동창 7일(그 주가 아니라 "최근 7일", 화면 문구도 그렇게 쓴다). */
    const val WINDOW_DAYS = 7

    /** 보관 한도 — 창보다 넉넉히 두되 무한 증가는 막는다(3주). */
    const val RETENTION_DAYS = 21

    /**
     * 최근 [WINDOW_DAYS]일(오늘 포함) 중 앱을 연 서로 다른 날짜 수. 같은 날 여러 번 열어도 1회,
     * 미래 날짜·창 밖은 제외.
     */
    fun countInWindow(days: Collection<Long>, todayEpochDay: Long, window: Int = WINDOW_DAYS): Int {
        if (window <= 0) return 0
        val oldest = todayEpochDay - (window - 1)
        return days.toSet().count { it in oldest..todayEpochDay }
    }

    /**
     * 오늘을 더한 보관 집합 — 중복 없이 **최근 [retention]개 날짜만** 남긴다.
     *
     * 날짜(오늘)를 기준으로 잘라내지 않는 이유(#286 리뷰): 기기 시계가 크게 앞으로 튀면 그 기준이
     * 미래로 가 **기존 기록이 통째로 지워지고** 시계를 되돌려도 복구되지 않는다. 개수 기준은 시계
     * 변화에 영향받지 않으면서 저장값 무한 증가도 막는다. 미래 날짜는 남더라도 창 계산에서 걸러진다.
     */
    fun record(days: Collection<Long>, todayEpochDay: Long, retention: Int = RETENTION_DAYS): Set<Long> =
        (days + todayEpochDay)
            .distinct()
            .sortedDescending()
            .take(retention.coerceAtLeast(1))
            .toSet()
}
