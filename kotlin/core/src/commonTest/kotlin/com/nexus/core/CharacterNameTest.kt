package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #216 — 이름 정규화·검증. 공백/빈값 방지와 1~12자 경계가 핵심. */
class CharacterNameTest {
    @Test
    fun `앞뒤 공백 제거하고 내부 연속 공백은 한 칸으로`() {
        assertEquals("모모", CharacterName.normalize("  모모  "))
        assertEquals("모 모", CharacterName.normalize("모   모"))
    }

    @Test
    fun `제어문자(줄바꿈·탭)는 제거된다`() {
        assertEquals("모모", CharacterName.normalize("모\n모"))
        assertEquals("모 모", CharacterName.normalize("모\t 모"))
    }

    @Test
    fun `빈값·공백만은 무효`() {
        assertFalse(CharacterName.isValid(""))
        assertFalse(CharacterName.isValid("   "))
        assertFalse(CharacterName.isValid("\n\t"))
    }

    @Test
    fun `보이지 않는 문자만으로는 무효 - 투명 이름 방지`() {
        assertFalse(CharacterName.isValid("  ")) // NBSP
        assertFalse(CharacterName.isValid("​")) // ZERO WIDTH SPACE
        assertFalse(CharacterName.isValid("﻿")) // BOM
        assertFalse(CharacterName.isValid("⁠")) // WORD JOINER
        assertFalse(CharacterName.isValid("　")) // 전각 공백
    }

    @Test
    fun `전각·비단절 공백도 내부에서 한 칸으로 접힌다`() {
        assertEquals("모 모", CharacterName.normalize("모　　모"))
        assertEquals("모 모", CharacterName.normalize("모  모"))
    }

    @Test
    fun `길이 경계 - 1자 유효, 12자 유효, 13자 무효`() {
        assertTrue(CharacterName.isValid("모"))
        assertTrue(CharacterName.isValid("가".repeat(CharacterName.MAX_LENGTH)))
        assertFalse(CharacterName.isValid("가".repeat(CharacterName.MAX_LENGTH + 1)))
    }

    @Test
    fun `공백 포함 이름도 정규화 후 길이로 판정`() {
        // "가"*12 + 공백 → 정규화하면 12자라 유효
        assertTrue(CharacterName.isValid("가".repeat(CharacterName.MAX_LENGTH) + "  "))
    }

    @Test
    fun `표시용 - 유효하면 정규화 이름, 무효·null이면 null`() {
        assertEquals("모모", CharacterName.displayOrNull(" 모모 "))
        assertNull(CharacterName.displayOrNull(null))
        assertNull(CharacterName.displayOrNull("   "))
        assertNull(CharacterName.displayOrNull("가".repeat(CharacterName.MAX_LENGTH + 1)))
    }
}
