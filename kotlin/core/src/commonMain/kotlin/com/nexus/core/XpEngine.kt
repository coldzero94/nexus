package com.nexus.core

enum class ActivityType(val pointsPerMinute: Double) {
    WALKING(1.0),
    RUNNING(2.0),
    STRENGTH(1.5),
}

object XpEngine {
    /** 산식 버전 태그 — RewardEvent 원장과 케이스 테이블이 이 값으로 버전링된다 (BACKEND.md §1). */
    const val FORMULA_VERSION = 1

    /**
     * MVP.md §5 기본 점수: 활동 유형별 분당 포인트 × 시간.
     * 개인 계수·신뢰 계수·상한은 E3에서 이 함수 위에 쌓인다.
     */
    fun baseScore(type: ActivityType, minutes: Int): Int {
        require(minutes >= 0) { "minutes must be >= 0" }
        return (type.pointsPerMinute * minutes).toInt()
    }
}
