package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RecalculationPipelineTest {
    private fun sampleEvents(): List<RewardEvent> {
        val ledger = RewardLedger()
        ledger.grant("a", 100, "origin", RecordingMethod.AUTO_RECORDED, formulaVersion = 1, epochMillis = 1)
        ledger.grant("b", 200, "origin", RecordingMethod.AUTO_RECORDED, formulaVersion = 1, epochMillis = 2)
        return ledger.events()
    }

    @Test
    fun identityRecompute_resultMatches() {
        // 완료 기준: 산식 변경 후 재계산 결과 일치 — 동일 산식이면 총합·레벨 불변
        val r = RecalculationPipeline.recompute(sampleEvents(), toFormulaVersion = 1) { it.xp }
        assertEquals(300, r.oldTotalXp)
        assertEquals(300, r.newTotalXp)
        assertEquals(LevelCurve.levelForXp(300), r.displayLevel)
    }

    @Test
    fun upwardRecompute_raisesTotalAndLevel() {
        val r = RecalculationPipeline.recompute(sampleEvents(), toFormulaVersion = 2) { it.xp * 2 }
        assertEquals(600, r.newTotalXp)
        assertEquals(LevelCurve.levelForXp(600), r.displayLevel) // 상승은 반영
        assertEquals(2, r.toFormulaVersion)
    }

    @Test
    fun downwardRecompute_neverLowersDisplayLevel() {
        // 재계산이 XP를 낮춰도(150) 표시 레벨은 기존(300 기준) 유지 — 하향 미반영
        val r = RecalculationPipeline.recompute(sampleEvents(), toFormulaVersion = 2) { it.xp / 2 }
        assertEquals(150, r.newTotalXp)
        assertEquals(LevelCurve.levelForXp(300), r.displayLevel) // 레벨다운 금지
    }

    @Test
    fun nullRecompute_freezesOriginalValue() {
        // 원본 없는 이벤트(동결) → 기존 xp 유지
        val r = RecalculationPipeline.recompute(sampleEvents(), toFormulaVersion = 2) { null }
        assertEquals(300, r.newTotalXp)
    }
}
