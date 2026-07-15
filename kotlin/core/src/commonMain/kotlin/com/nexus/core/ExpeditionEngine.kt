package com.nexus.core

/** 원정 상태 (#34, E5-1). */
sealed interface ExpeditionState {
    /** 원정 미진행 — 에너지가 충분하면 출발 가능. */
    data object Idle : ExpeditionState

    /** 진행 중 — [remainingMillis]는 0 초과. */
    data class InProgress(val remainingMillis: Long) : ExpeditionState

    /** 완료 — 개봉 대기(보상·연출은 E5-7). */
    data object ReadyToOpen : ExpeditionState
}

/**
 * 원정 코어 (#34): 8시간 타이머의 순수 상태 산술 — 시각은 호출자 주입(core는 시계 비의존).
 * 정합 규칙(완료 기준 "재시작·시간 조작에도 정합"):
 * - 상태는 시작 시각 하나에서만 파생 — 재시작해도 같은 입력이면 같은 상태.
 * - 시계 후퇴(now < startedAt): 남은 시간을 전체 길이로 클램프 — 음수·과대 잔여 없음.
 *   후퇴로 완료가 진행 중으로 되돌아가는 건 허용(원정은 실시간 대기 자체가 콘텐츠).
 * - 시계 전진: 즉시 완료 — 개봉은 사용자의 명시 행위라 이득은 보상 1회분뿐(E5-7에서 상한).
 */
object ExpeditionEngine {
    const val DURATION_HOURS = 8
    const val DURATION_MILLIS: Long = DURATION_HOURS * 60L * 60L * 1000L

    /** [startedAtMillis]가 null이면 미진행. */
    fun stateAt(startedAtMillis: Long?, nowMillis: Long): ExpeditionState {
        if (startedAtMillis == null) return ExpeditionState.Idle
        val elapsed = nowMillis - startedAtMillis
        if (elapsed >= DURATION_MILLIS) return ExpeditionState.ReadyToOpen
        return ExpeditionState.InProgress(remainingMillis = (DURATION_MILLIS - elapsed).coerceAtMost(DURATION_MILLIS))
    }
}
