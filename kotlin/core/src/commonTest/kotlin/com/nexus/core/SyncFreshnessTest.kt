package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** #221 — 신선도 등급. 경계(임계 3시간)와 "음수 경과를 만들지 않는다"가 핵심. */
class SyncFreshnessTest {
    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun `한 번도 동기화 안 했으면 Never`() {
        assertIs<SyncFreshness.Never>(SyncFreshness.evaluate(0L, now))
        // 음수 저장값(손상)도 미동기화로 — 표시할 근거가 없다
        assertIs<SyncFreshness.Never>(SyncFreshness.evaluate(-1L, now))
    }

    @Test
    fun `경과 분을 그대로 담는다`() {
        val f = SyncFreshness.evaluate(now - 42 * minute, now)
        assertEquals(SyncFreshness.Synced(42), f)
    }

    @Test
    fun `임계 직전은 지연 아님, 임계부터 지연`() {
        assertFalse(SyncFreshness.Synced(SyncFreshness.DELAY_NOTICE_MINUTES - 1).delayed)
        assertTrue(SyncFreshness.Synced(SyncFreshness.DELAY_NOTICE_MINUTES).delayed)
    }

    @Test
    fun `미래 시각이 저장돼도 음수가 되지 않는다`() {
        // 시간대 변경·시계 보정으로 lastSync가 now보다 앞설 수 있다 — "-3분 전"은 표시할 수 없다
        val f = SyncFreshness.evaluate(now + 10 * minute, now)
        assertEquals(SyncFreshness.Synced(0), f)
    }

    @Test
    fun `시간 단위 표기는 60분 단위로 내림`() {
        assertEquals(0, SyncFreshness.Synced(59).hoursAgo)
        assertEquals(1, SyncFreshness.Synced(60).hoursAgo)
        assertEquals(3, SyncFreshness.Synced(200).hoursAgo)
    }

    @Test
    fun `아주 오래된 값도 Int로 클램프돼 표시가 깨지지 않는다`() {
        val f = SyncFreshness.evaluate(1L, Long.MAX_VALUE)
        assertEquals(SyncFreshness.Synced(Int.MAX_VALUE), f)
    }
}
