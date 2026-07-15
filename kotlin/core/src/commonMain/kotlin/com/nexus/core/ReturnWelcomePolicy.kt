package com.nexus.core

/**
 * 복귀 환영 정책 (#30, E4-6) — 3일+ 공백 후 첫 실행에서 환영 씬(1급 기능).
 * 무처벌 원칙: 공백은 죄책감이 아니라 환영의 계기다(Duolingo 용서 장치, MVP §1).
 */
object ReturnWelcomePolicy {
    /** 이 일수 이상 공백이면 복귀로 본다. */
    const val WELCOME_GAP_DAYS = 3

    /**
     * [lastOpenEpochDay]는 마지막 실행일(0 = 최초 실행 — 복귀가 아니므로 환영 없음).
     * 시계 역행(음수 공백)은 복귀가 아니다.
     */
    fun shouldWelcome(lastOpenEpochDay: Long, todayEpochDay: Long): Boolean =
        lastOpenEpochDay != 0L && todayEpochDay - lastOpenEpochDay >= WELCOME_GAP_DAYS

    /** 공백 일수(표시용) — 최초 실행·역행은 0. */
    fun gapDays(lastOpenEpochDay: Long, todayEpochDay: Long): Long =
        if (lastOpenEpochDay == 0L) 0L else (todayEpochDay - lastOpenEpochDay).coerceAtLeast(0L)
}
