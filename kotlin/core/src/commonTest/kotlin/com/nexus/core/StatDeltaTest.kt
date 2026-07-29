package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #219 — 능력치 상승분. 축하 카드에 들어갈 값이라 **없는 성장을 만들어내지 않는 것**이 계약이다.
 */
class StatDeltaTest {
    private val unlocked = Stat.entries.filterNot { it.locked }

    @Test
    fun `오른 스탯만 담는다`() {
        val prev = unlocked.associateWith { 10 }
        val cur = prev.toMutableMap().apply {
            this[unlocked[0]] = 14
            this[unlocked[1]] = 11
        }
        assertEquals(mapOf(unlocked[0] to 4, unlocked[1] to 1), StatDelta.risen(prev, cur))
    }

    @Test
    fun `하락은 담지 않는다 — 축하 자리에서 손실을 말하지 않는다`() {
        // 표시 능력치는 28일 창이라 세션이 창을 빠져나가면 내려갈 수 있다(사용자 잘못이 아니다)
        val prev = unlocked.associateWith { 10 }
        val cur = prev.toMutableMap().apply { this[unlocked[0]] = 3 }
        assertTrue(StatDelta.risen(prev, cur).isEmpty())
    }

    @Test
    fun `변화 없으면 빈 결과`() {
        val same = unlocked.associateWith { 7 }
        assertTrue(StatDelta.risen(same, same).isEmpty())
    }

    @Test
    fun `기준점이 없으면 상승으로 보지 않는다`() {
        // 첫 방문에 "+12 지구력"을 띄우면 하지도 않은 성장을 축하하는 셈이다
        assertTrue(StatDelta.risen(emptyMap(), unlocked.associateWith { 12 }).isEmpty())
    }

    @Test
    fun `잠긴 스탯은 제외한다`() {
        val locked = Stat.entries.filter { it.locked }
        if (locked.isEmpty()) return // 전부 해금된 빌드에서는 검증할 게 없다
        val prev = Stat.entries.associateWith { 0 }
        val cur = Stat.entries.associateWith { 5 }
        val risen = StatDelta.risen(prev, cur)
        locked.forEach { assertTrue(it !in risen, "잠긴 스탯 $it 이 상승 목록에 있다") }
    }

    @Test
    fun `표시 순서는 선언 순서를 따른다`() {
        val prev = unlocked.associateWith { 0 }
        val cur = unlocked.associateWith { 3 }
        assertEquals(unlocked, StatDelta.risen(prev, cur).keys.toList())
    }
}
