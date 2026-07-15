package com.nexus.core

/**
 * 소급 재계산 결과 (#60). [displayLevel]은 **하향 미반영** — 재계산이 XP를 낮춰도 표시 레벨은 안 내려간다.
 */
data class RecalcResult(
    val oldTotalXp: Int,
    val newTotalXp: Int,
    val displayLevel: Int,
    val fromFormulaVersion: Int,
    val toFormulaVersion: Int,
)

/**
 * 소급 재계산 파이프라인 (#60, E3-14, BACKEND.md §1): 산식 버전이 바뀌면 원장 이벤트를 새 산식으로 재검산.
 * - [recompute]가 null을 반환하면 **동결**(원본 없는 과거 이벤트 = 기존 값 유지).
 * - 표시 레벨은 **하향 미반영**(레벨다운 노출 = 이탈 요인) — max(기존 레벨, 새 레벨).
 * 순수 함수 — 실제 원장 append(보정 이벤트)·트리거는 app/서버.
 */
object RecalculationPipeline {
    fun recompute(events: List<RewardEvent>, toFormulaVersion: Int, recompute: (RewardEvent) -> Int?): RecalcResult {
        val oldTotal = events.sumOf { it.xp }
        val newTotal = events.sumOf { recompute(it) ?: it.xp } // 동결: null이면 기존 xp 유지
        val oldLevel = LevelCurve.levelForXp(maxOf(0, oldTotal))
        val newLevel = LevelCurve.levelForXp(maxOf(0, newTotal))
        val fromVersion = events.maxOfOrNull { it.formulaVersion } ?: toFormulaVersion
        return RecalcResult(
            oldTotalXp = oldTotal,
            newTotalXp = newTotal,
            displayLevel = maxOf(oldLevel, newLevel),
            fromFormulaVersion = fromVersion,
            toFormulaVersion = toFormulaVersion,
        )
    }
}
