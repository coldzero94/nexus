package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 시간대·계절 판정 (#115, E16-19).
 *
 * 경계값이 전부다. "저녁인데 낮 배경"은 그 시각에만 재현되므로, 실기기로 쫓지 않으려면
 * 여기서 표로 고정해야 한다.
 */
class AmbienceTest {

    @Test
    fun `하루 경계가 정확하다`() {
        listOf(
            0 to AmbienceSlot.NIGHT,
            4 to AmbienceSlot.NIGHT,
            5 to AmbienceSlot.MORNING,
            10 to AmbienceSlot.MORNING,
            11 to AmbienceSlot.DAY,
            17 to AmbienceSlot.DAY,
            18 to AmbienceSlot.EVENING,
            20 to AmbienceSlot.EVENING,
            21 to AmbienceSlot.NIGHT,
            23 to AmbienceSlot.NIGHT,
        ).forEach { (hour, expected) ->
            assertEquals(expected, Ambience.slotAt(hour), "${hour}시")
        }
    }

    /**
     * 저녁 배경과 저녁 일지가 **같은 시각**에 열려야 한다. 어긋나면 "카드는 떴는데 배경은 낮"인
     * 한 시간이 생겨 화면이 스스로와 모순된다.
     */
    @Test
    fun `저녁 시작이 저녁 일지 개방 시각과 같다`() {
        assertEquals(AmbienceSlot.DAY, Ambience.slotAt(EVENING_JOURNAL_OPEN_HOUR - 1))
        assertEquals(AmbienceSlot.EVENING, Ambience.slotAt(EVENING_JOURNAL_OPEN_HOUR))
    }

    @Test
    fun `모든 시가 한 구간에 속한다`() {
        val slots = (0..23).map { Ambience.slotAt(it) }

        assertEquals(24, slots.size)
        assertEquals(AmbienceSlot.entries.toSet(), slots.toSet(), "쓰이지 않는 구간이 있다")
    }

    @Test
    fun `범위 밖 시는 거부한다`() {
        assertFailsWith<IllegalArgumentException> { Ambience.slotAt(-1) }
        assertFailsWith<IllegalArgumentException> { Ambience.slotAt(24) }
    }

    // ── 계절 ──

    @Test
    fun `계절 경계가 정확하다`() {
        listOf(
            12 to Season.WINTER, 1 to Season.WINTER, 2 to Season.WINTER,
            3 to Season.SPRING, 5 to Season.SPRING,
            6 to Season.SUMMER, 8 to Season.SUMMER,
            9 to Season.AUTUMN, 11 to Season.AUTUMN,
        ).forEach { (month, expected) ->
            assertEquals(expected, Ambience.seasonOf(month), "${month}월")
        }
    }

    @Test
    fun `모든 달이 한 계절에 속한다`() {
        val seasons = (1..12).map { Ambience.seasonOf(it) }

        assertEquals(Season.entries.toSet(), seasons.toSet())
        Season.entries.forEach { season ->
            assertEquals(3, seasons.count { it == season }, "$season 이 3개월이 아니다")
        }
    }

    @Test
    fun `범위 밖 월은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { Ambience.seasonOf(0) }
        assertFailsWith<IllegalArgumentException> { Ambience.seasonOf(13) }
    }

    private companion object {
        /** `EveningJournalStore.OPEN_HOUR` — core에서 app을 참조할 수 없어 값으로 고정한다. */
        const val EVENING_JOURNAL_OPEN_HOUR = 18
    }
}
