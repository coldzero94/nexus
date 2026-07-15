package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RewardLedgerTest {
    private fun ledger() = RewardLedger()

    @Test
    fun grant_appendsAndAccumulates() {
        val l = ledger()
        val e =
            l.grant(
                "hc-1",
                xp = 50,
                dataOrigin = "com.sec.android.app.shealth",
                recordingMethod = RecordingMethod.AUTO_RECORDED,
                epochMillis = 1000,
            )
        assertNotNull(e)
        assertEquals(0L, e.sequence)
        assertEquals(RewardEventType.GRANT, e.type)
        assertEquals(50, l.totalXp())
        assertEquals(1, l.events().size)
    }

    @Test
    fun grant_isIdempotentByKey() {
        val l = ledger()
        l.grant("hc-1", 50, "o", RecordingMethod.AUTO_RECORDED, epochMillis = 1)
        val dup = l.grant("hc-1", 50, "o", RecordingMethod.AUTO_RECORDED, epochMillis = 2)
        assertNull(dup, "같은 키 재지급은 무시")
        assertEquals(50, l.totalXp())
        assertEquals(1, l.events().size)
    }

    @Test
    fun sequence_isMonotonic() {
        val l = ledger()
        val a = l.grant("hc-1", 10, "o", RecordingMethod.AUTO_RECORDED, epochMillis = 1)!!
        val b = l.grant("hc-2", 20, "o", RecordingMethod.AUTO_RECORDED, epochMillis = 2)!!
        assertEquals(0L, a.sequence)
        assertEquals(1L, b.sequence)
        assertEquals(30, l.totalXp())
    }

    @Test
    fun cancel_appendsCompensatingEvent_notMutation() {
        val l = ledger()
        val grant = l.grant("hc-1", 50, "o", RecordingMethod.ACTIVELY_RECORDED, formulaVersion = 1, epochMillis = 100)!!
        val cancel = l.cancel("hc-1", epochMillis = 200)
        assertNotNull(cancel)
        assertEquals(RewardEventType.CANCELLATION, cancel.type)
        assertEquals(-50, cancel.xp)
        assertEquals(1L, cancel.sequence)
        // provenance·산식버전 박제 유지
        assertEquals(grant.dataOrigin, cancel.dataOrigin)
        assertEquals(grant.formulaVersion, cancel.formulaVersion)
        // 원장은 두 이벤트(수정 아님), 누적 0
        assertEquals(2, l.events().size)
        assertEquals(0, l.totalXp())
        // 원본 지급 이벤트는 그대로
        assertEquals(50, l.events()[0].xp)
    }

    @Test
    fun cancel_unknownOrDoubleCancel_returnsNull() {
        val l = ledger()
        assertNull(l.cancel("missing", epochMillis = 1))
        l.grant("hc-1", 50, "o", RecordingMethod.AUTO_RECORDED, epochMillis = 1)
        l.cancel("hc-1", epochMillis = 2)
        assertNull(l.cancel("hc-1", epochMillis = 3), "이중 취소 무시")
        assertEquals(0, l.totalXp())
        assertEquals(2, l.events().size)
    }
}
