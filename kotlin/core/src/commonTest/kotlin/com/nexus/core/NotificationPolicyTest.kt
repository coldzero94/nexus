package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPolicyTest {

    @Test
    fun quietHours_wrapMidnight() {
        // 조용 시간 [21, 09): 21시·자정·8시는 조용, 9시·20시는 발송 가능 시간대
        assertTrue(NotificationPolicy.isQuietHour(21))
        assertTrue(NotificationPolicy.isQuietHour(0))
        assertTrue(NotificationPolicy.isQuietHour(8))
        assertFalse(NotificationPolicy.isQuietHour(9))
        assertFalse(NotificationPolicy.isQuietHour(20))
    }

    @Test
    fun dailyCap_blocksThirdNotification() {
        assertTrue(NotificationPolicy.canNotify(hourOfDay = 12, sentToday = 0))
        assertTrue(NotificationPolicy.canNotify(hourOfDay = 12, sentToday = 1))
        assertFalse(NotificationPolicy.canNotify(hourOfDay = 12, sentToday = 2)) // 상한 2
    }

    @Test
    fun quietHour_blocksEvenUnderCap() {
        assertFalse(NotificationPolicy.canNotify(hourOfDay = 22, sentToday = 0))
    }

    @Test
    fun invalidInputs_rejected() {
        assertFailsWith<IllegalArgumentException> { NotificationPolicy.isQuietHour(24) }
        assertFailsWith<IllegalArgumentException> { NotificationPolicy.canNotify(12, -1) }
    }
}
