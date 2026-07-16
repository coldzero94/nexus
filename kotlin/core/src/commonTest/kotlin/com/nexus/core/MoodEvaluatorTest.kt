package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoodEvaluatorTest {

    // 런타임 자산(app/src/main/assets/character/mood_triggers.json)과 동일한 v1 표.
    private val v1Table = """
        {
          "version": "v1",
          "moods": ["신남", "평온", "뿌듯", "심심", "휴식중"],
          "rules": [
            { "priority": 1, "mood": "뿌듯", "when": "leveledUp || newBadge || weeklyGoalMet || newRecord",
              "face": "proud_sparkle", "conditionEffect": "neutral", "lines": ["봤어 봤어?!"] },
            { "priority": 2, "mood": "신남",
              "when": "todayActiveMin > 0 && (personalCoef >= 1.3 || highIntensity)",
              "face": "jump_hyped", "conditionEffect": "recover_small", "lines": ["오늘 좀 미쳤는데?!"] },
            { "priority": 3, "mood": "평온", "when": "todayActiveMin > 0",
              "face": "calm_smile", "conditionEffect": "hold_or_recover_small", "lines": ["무난하고 좋았어"] },
            { "priority": 4, "mood": "휴식중", "when": "todayActiveMin == 0 && (restMode || plannedRest)",
              "face": "cozy_roll", "conditionEffect": "no_decay", "lines": ["뒹굴데이"] },
            { "priority": 5, "mood": "심심", "when": "todayActiveMin == 0 && !restMode",
              "face": "bored_lookaround", "conditionEffect": "decay_soft", "lines": ["심심해~"] }
          ]
        }
    """.trimIndent()

    private val table = MoodTriggerTable.parse(v1Table)

    private fun moodOf(context: MoodContext): String? = MoodEvaluator.evaluate(table, context)?.mood

    @Test
    fun achievement_wins_over_activity() {
        // 성취(1)는 활동(2·3)보다 우선 — 운동을 많이 했어도 레벨업이면 뿌듯.
        val result = MoodEvaluator.evaluate(
            table,
            MoodContext(todayActiveMin = 60, highIntensity = true, leveledUp = true),
        )
        assertEquals("뿌듯", result?.mood)
        assertEquals("proud_sparkle", result?.face)
    }

    @Test
    fun highIntensity_or_highCoef_is_excited() {
        assertEquals("신남", moodOf(MoodContext(todayActiveMin = 30, highIntensity = true)))
        assertEquals("신남", moodOf(MoodContext(todayActiveMin = 30, personalCoef = 1.3)))
        // 임계값 바로 아래(1.29)이고 고강도 아니면 평온으로 강등.
        assertEquals("평온", moodOf(MoodContext(todayActiveMin = 30, personalCoef = 1.29)))
    }

    @Test
    fun plain_activity_is_calm() {
        assertEquals("평온", moodOf(MoodContext(todayActiveMin = 20)))
    }

    @Test
    fun rest_beats_bored_when_idle() {
        // 활동 0분 + 휴식 모드 → 휴식중. restMode 없으면 심심.
        assertEquals("휴식중", moodOf(MoodContext(todayActiveMin = 0, restMode = true)))
        assertEquals("휴식중", moodOf(MoodContext(todayActiveMin = 0, plannedRest = true)))
        assertEquals("심심", moodOf(MoodContext(todayActiveMin = 0, restMode = false)))
    }

    @Test
    fun activity_overrides_rest_mode() {
        // 핵심 규칙(CHARACTER.md §2): 휴식 모드여도 운동하면 활동 기분이 우선.
        assertEquals("평온", moodOf(MoodContext(todayActiveMin = 15, restMode = true)))
    }

    @Test
    fun default_idle_context_is_bored() {
        assertEquals("심심", moodOf(MoodContext()))
    }

    @Test
    fun evaluate_returns_null_when_no_rule_matches() {
        val single = MoodTriggerTable.parse(
            """{ "version": "t", "rules": [
                 { "priority": 1, "mood": "x", "when": "leveledUp", "face": "f", "conditionEffect": "n" } ] }""",
        )
        assertNull(MoodEvaluator.evaluate(single, MoodContext()))
    }

    @Test
    fun priority_order_is_independent_of_json_order() {
        // JSON에 역순으로 넣어도 priority로 정렬 — 활동+고강도는 신남(2)이 평온(3)보다 우선.
        val reversed = MoodTriggerTable.parse(
            """{ "version": "t", "rules": [
                 { "priority": 3, "mood": "평온", "when": "todayActiveMin > 0",
                   "face": "f", "conditionEffect": "n" },
                 { "priority": 2, "mood": "신남", "when": "todayActiveMin > 0 && highIntensity",
                   "face": "f", "conditionEffect": "n" } ] }""",
        )
        val result = MoodEvaluator.evaluate(reversed, MoodContext(todayActiveMin = 10, highIntensity = true))
        assertEquals("신남", result?.mood)
    }

    @Test
    fun parse_rejects_unknown_variable() {
        // 오탈자·미지원 변수는 로드 시점에 실패 — 런타임에 조용히 매치 실패하지 않게.
        val ex = assertFailsWith<IllegalArgumentException> {
            MoodTriggerTable.parse(
                """{ "version": "t", "rules": [
                     { "priority": 1, "mood": "x", "when": "todayActiveMni > 0",
                       "face": "f", "conditionEffect": "n" } ] }""",
            )
        }
        assertTrue(ex.message!!.contains("unknown vars"))
    }

    @Test
    fun parse_rejects_duplicate_priority() {
        assertFailsWith<IllegalArgumentException> {
            MoodTriggerTable.parse(
                """{ "version": "t", "rules": [
                     { "priority": 1, "mood": "a", "when": "restMode", "face": "f", "conditionEffect": "n" },
                     { "priority": 1, "mood": "b", "when": "returning", "face": "f", "conditionEffect": "n" } ] }""",
            )
        }
    }

    @Test
    fun parse_rejects_empty_rules() {
        assertFailsWith<IllegalArgumentException> {
            MoodTriggerTable.parse("""{ "version": "t", "rules": [] }""")
        }
    }
}

class BoolExprTest {
    private fun eval(expr: String, vars: Map<String, Double> = emptyMap()) = BoolExpr.eval(expr, vars)

    @Test
    fun comparisons() {
        assertTrue(eval("x > 0", mapOf("x" to 5.0)))
        assertFalse(eval("x > 0", mapOf("x" to 0.0)))
        assertTrue(eval("x >= 1.3", mapOf("x" to 1.3)))
        assertFalse(eval("x >= 1.3", mapOf("x" to 1.29)))
        assertTrue(eval("x == 0", mapOf("x" to 0.0)))
        assertTrue(eval("x != 0", mapOf("x" to 2.0)))
        assertTrue(eval("x <= 10 && x > 3", mapOf("x" to 7.0)))
    }

    @Test
    fun boolean_atoms_and_operators() {
        assertTrue(eval("a || b", mapOf("a" to 0.0, "b" to 1.0)))
        assertFalse(eval("a && b", mapOf("a" to 1.0, "b" to 0.0)))
        assertTrue(eval("!a", mapOf("a" to 0.0)))
        assertFalse(eval("!a", mapOf("a" to 1.0)))
        assertTrue(eval("true"))
        assertFalse(eval("false"))
    }

    @Test
    fun precedence_and_parentheses() {
        // && 가 || 보다 강하게 결합: false || (true && true) == true
        assertTrue(eval("a || b && c", mapOf("a" to 0.0, "b" to 1.0, "c" to 1.0)))
        // 괄호로 묶으면 (a || b) && c == (true) && false == false
        assertFalse(eval("(a || b) && c", mapOf("a" to 1.0, "b" to 0.0, "c" to 0.0)))
        assertTrue(eval("n > 0 && (p >= 1.3 || h)", mapOf("n" to 5.0, "p" to 1.0, "h" to 1.0)))
        assertFalse(eval("n > 0 && (p >= 1.3 || h)", mapOf("n" to 5.0, "p" to 1.0, "h" to 0.0)))
    }

    @Test
    fun unknown_variable_throws() {
        assertFailsWith<IllegalArgumentException> { eval("missing > 0") }
    }

    @Test
    fun malformed_expression_throws() {
        assertFailsWith<IllegalArgumentException> { eval("x >", mapOf("x" to 1.0)) }
        assertFailsWith<IllegalArgumentException> { eval("x 3", mapOf("x" to 1.0)) }
    }

    @Test
    fun identifiers_excludes_literals_and_numbers() {
        assertEquals(
            setOf("todayActiveMin", "personalCoef", "highIntensity"),
            BoolExpr.identifiers("todayActiveMin > 0 && (personalCoef >= 1.3 || highIntensity) || true"),
        )
    }
}
