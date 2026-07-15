package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DialogueTest {

    private val pool = listOf("가", "나", "다")

    @Test
    fun pick_avoidsRecentLines() {
        // 반복 회피 계약: 최근 노출("가")을 피해서 뽑는다
        val picked = DialogueSelector.pick(pool, recent = listOf("가"), randomIndex = 0)
        assertTrue(picked in listOf("나", "다"))
    }

    @Test
    fun pick_allRecent_returnsOldestShown() {
        // 풀 전체가 최근이면 침묵 대신 가장 오래된 대사 재사용 (빈 결과 없음)
        assertEquals("나", DialogueSelector.pick(pool, recent = listOf("나", "다", "가"), randomIndex = 0))
    }

    @Test
    fun pick_randomIndexWrapsSafely() {
        // randomIndex가 후보 수를 넘어도 순환 — 호출자 난수 범위 실수에 강건
        val picked = DialogueSelector.pick(pool, recent = emptyList(), randomIndex = 7)
        assertEquals("나", picked) // 7 % 3 = 1
    }

    @Test
    fun remember_keepsCapacity_dropsOldest() {
        var recent = emptyList<String>()
        listOf("가", "나", "다").forEach { recent = DialogueSelector.remember(recent, it, capacity = 2) }
        assertEquals(listOf("나", "다"), recent) // 가장 오래된 "가" 탈락
    }

    @Test
    fun remember_reshowMovesToNewest() {
        // 재노출된 대사는 목록 끝(최신)으로 — 회피 순서가 실제 노출 순서를 따른다
        val recent = DialogueSelector.remember(listOf("가", "나"), shown = "가", capacity = 3)
        assertEquals(listOf("나", "가"), recent)
    }

    @Test
    fun parse_validPool_andStateFallback() {
        val pool = DialogueTable.parse(
            """{"version":1,"defaultState":"idle","lines":{"idle":["안녕!"],"walk":["오늘도 걷자!"]}}""",
        )
        assertEquals(listOf("오늘도 걷자!"), pool.linesOrDefault("walk"))
        assertEquals(listOf("안녕!"), pool.linesOrDefault("brand_new_mood")) // 미지 상태 폴백
    }

    @Test
    fun parse_rejectsBrokenTables() {
        assertFailsWith<IllegalArgumentException> {
            DialogueTable.parse("""{"version":1,"defaultState":"walk","lines":{"idle":["안녕"]}}""")
        } // defaultState 미존재
        assertFailsWith<IllegalArgumentException> {
            DialogueTable.parse("""{"version":1,"defaultState":"idle","lines":{"idle":[]}}""")
        } // 빈 대사 목록
    }
}
