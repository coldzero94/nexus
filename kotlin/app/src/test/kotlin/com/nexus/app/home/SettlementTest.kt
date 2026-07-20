package com.nexus.app.home

import kotlin.test.Test
import kotlin.test.assertEquals

class SettlementTest {

    @Test
    fun firstVisit_noCard_syncsBaseline() {
        val d = decideSettlement(lastSeenXp = SettlementStore.UNSET, currentXp = 100)
        assertEquals(SettlementDecision(deltaXp = null, syncBaseline = true), d)
    }

    @Test
    fun newXp_showsDelta() {
        assertEquals(
            SettlementDecision(deltaXp = 30, syncBaseline = false),
            decideSettlement(lastSeenXp = 100, currentXp = 130),
        )
    }

    @Test
    fun ledgerCancellation_downward_noCard_syncsBaseline() {
        // 원장 취소로 하향 — 카드 없이 기준점 동기화. 매 로드 "대입"이라 떠 있던 낡은 카드도 사라진다 (#35 리뷰)
        assertEquals(
            SettlementDecision(deltaXp = null, syncBaseline = true),
            decideSettlement(lastSeenXp = 100, currentXp = 90),
        )
    }

    @Test
    fun unchanged_noCard_noSync() {
        assertEquals(
            SettlementDecision(deltaXp = null, syncBaseline = false),
            decideSettlement(lastSeenXp = 100, currentXp = 100),
        )
    }
}
