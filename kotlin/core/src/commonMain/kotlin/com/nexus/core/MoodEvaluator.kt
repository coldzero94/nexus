package com.nexus.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 기분 트리거 규칙 1행 (#28, E4-4). [whenExpr]는 불리언 식(문자열) — [BoolExpr]가 해석하므로
 * 임계값·조건을 표에서 바꿔도 코드 수정이 없다. [face]는 표정 5종(기분 enum과 1:1, CHARACTER.md §1).
 */
@Serializable
data class MoodRule(
    val priority: Int,
    val mood: String,
    @SerialName("when") val whenExpr: String,
    val face: String,
    val conditionEffect: String,
    val lines: List<String> = emptyList(),
)

/**
 * 기분 5종 트리거 표 (#65 산출물, #28 입력) — 앱 assets JSON에서 로드. 규칙은 [MoodEvaluator]가
 * priority 오름차순으로 평가하고 첫 매치를 채택한다. **표만 고치면 코드 무수정 반영**(E4-4 완료 기준).
 */
@Serializable
data class MoodTable(
    val version: String,
    val moods: List<String> = emptyList(),
    val rules: List<MoodRule>,
)

/** 평가 결과 — 채택된 규칙의 표시 정보(표정·컨디션 영향·대사 풀). 매치 없으면 호출자가 폴백. */
data class MoodResult(
    val mood: String,
    val face: String,
    val conditionEffect: String,
    val lines: List<String>,
)

/**
 * 기분 평가 입력 — 표의 `inputs` 어휘와 1:1. [toVars]가 식 변수 맵을 만든다.
 * 새 입력 '신호'를 추가할 때만 여기에 필드+매핑이 필요하고, 그 외(임계값·우선순위·문구·표정)
 * 편집은 JSON만으로 반영된다. [returning]은 규칙이 아니라 별도 환영 씬(E4-6) 트리거라 표 밖이다.
 */
data class MoodContext(
    val todayActiveMin: Int = 0,
    val personalCoef: Double = 1.0,
    val highIntensity: Boolean = false,
    val restMode: Boolean = false,
    val plannedRest: Boolean = false,
    val leveledUp: Boolean = false,
    val newBadge: Boolean = false,
    val weeklyGoalMet: Boolean = false,
    val newRecord: Boolean = false,
    val daysSinceActivity: Int = 0,
    val condition: Int = MAX_CONDITION,
    val returning: Boolean = false,
) {
    fun toVars(): Map<String, Double> = mapOf(
        "todayActiveMin" to todayActiveMin.toDouble(),
        "personalCoef" to personalCoef,
        "highIntensity" to highIntensity.toFlag(),
        "restMode" to restMode.toFlag(),
        "plannedRest" to plannedRest.toFlag(),
        "leveledUp" to leveledUp.toFlag(),
        "newBadge" to newBadge.toFlag(),
        "weeklyGoalMet" to weeklyGoalMet.toFlag(),
        "newRecord" to newRecord.toFlag(),
        "daysSinceActivity" to daysSinceActivity.toDouble(),
        "condition" to condition.toDouble(),
        "returning" to returning.toFlag(),
    )

    companion object {
        const val MAX_CONDITION = 100

        /** 표의 `when`에서 참조 가능한 변수 어휘 — 파서가 이 집합으로 오탈자/미지원 변수를 걸러낸다. */
        val VARS: Set<String> = setOf(
            "todayActiveMin", "personalCoef", "highIntensity", "restMode", "plannedRest",
            "leveledUp", "newBadge", "weeklyGoalMet", "newRecord", "daysSinceActivity",
            "condition", "returning",
        )
    }
}

/** 기분 평가기 (#28, E4-4) — priority 오름차순 첫 매치. 매치 없으면 null. */
object MoodEvaluator {
    fun evaluate(table: MoodTable, vars: Map<String, Double>): MoodResult? =
        table.rules
            .sortedBy { it.priority }
            .firstOrNull { BoolExpr.eval(it.whenExpr, vars) }
            ?.let { MoodResult(it.mood, it.face, it.conditionEffect, it.lines) }

    fun evaluate(table: MoodTable, context: MoodContext): MoodResult? = evaluate(table, context.toVars())
}

/** 기분 표 파서 — 애니메이션(#25)·대사(#29)와 같은 fail-fast 계약. */
object MoodTriggerTable {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String): MoodTable {
        val table = json.decodeFromString(MoodTable.serializer(), jsonText)
        require(table.rules.isNotEmpty()) { "rules must not be empty" }
        val seenPriorities = HashSet<Int>()
        table.rules.forEach { rule ->
            require(rule.whenExpr.isNotBlank()) { "rule '${rule.mood}': when must not be blank" }
            require(seenPriorities.add(rule.priority)) { "duplicate priority ${rule.priority} ('${rule.mood}')" }
            // 오탈자·미지원 변수는 로드 시점에 실패시킨다 — 런타임에 조용히 매치 실패/크래시하는 것 방지.
            val unknown = BoolExpr.identifiers(rule.whenExpr) - MoodContext.VARS
            require(unknown.isEmpty()) { "rule '${rule.mood}': unknown vars $unknown" }
        }
        return table
    }
}

/**
 * 불리언 식 미니 평가기 (#28) — 표의 `when` 문자열을 코드 수정 없이 해석한다.
 * 문법(낮은→높은 우선순위):
 * ```
 * or   := and ('||' and)*
 * and  := not ('&&' not)*
 * not  := '!' not | cmp
 * cmp  := atom (('>=' | '<=' | '==' | '!=' | '>' | '<') atom)?
 * atom := '(' or ')' | number | ident | true | false
 * ```
 * 식별자는 [vars]에서 수치로 해석(불리언은 1/0)하고, 비교 없이 단독으로 쓰이면 0이 아니면 참이다.
 * 미지의 식별자는 예외 — 표의 오탈자를 조기에 드러낸다([MoodTriggerTable]가 로드 시점에 검증).
 */
object BoolExpr {
    fun eval(expr: String, vars: Map<String, Double>): Boolean = Parser(tokenize(expr), vars).evaluate()

    /** 식이 참조하는 변수 식별자 집합(true/false 리터럴 제외) — 파서 검증용. */
    fun identifiers(expr: String): Set<String> =
        tokenize(expr)
            .filter { it.kind == TokenKind.IDENT && it.text != LIT_TRUE && it.text != LIT_FALSE }
            .map { it.text }
            .toSet()
}

private const val TRUE_FLAG = 1.0
private const val FALSE_FLAG = 0.0
private const val LIT_TRUE = "true"
private const val LIT_FALSE = "false"

private fun Boolean.toFlag(): Double = if (this) TRUE_FLAG else FALSE_FLAG

private enum class TokenKind { IDENT, NUMBER, OP, LPAREN, RPAREN }

private data class Token(val kind: TokenKind, val text: String)

private val TWO_CHAR_OPS = setOf("||", "&&", ">=", "<=", "==", "!=")
private val CMP_OPS = setOf(">", ">=", "<", "<=", "==", "!=")

private fun tokenize(expr: String): List<Token> {
    val out = ArrayList<Token>()
    var i = 0
    while (i < expr.length) {
        val c = expr[i]
        when {
            c.isWhitespace() -> i++
            c == '(' -> { out += Token(TokenKind.LPAREN, "("); i++ }
            c == ')' -> { out += Token(TokenKind.RPAREN, ")"); i++ }
            i + 1 < expr.length && expr.substring(i, i + 2) in TWO_CHAR_OPS -> {
                out += Token(TokenKind.OP, expr.substring(i, i + 2)); i += 2
            }
            c == '!' || c == '>' || c == '<' -> { out += Token(TokenKind.OP, c.toString()); i++ }
            isNumberChar(c) -> i = scanWhile(expr, i, out, TokenKind.NUMBER, ::isNumberChar)
            isIdentChar(c) -> i = scanWhile(expr, i, out, TokenKind.IDENT, ::isIdentChar)
            else -> throw IllegalArgumentException("unexpected char '$c' in \"$expr\"")
        }
    }
    return out
}

// 숫자는 앞선 분기에서 먼저 잡히므로, 식별자 첫 글자로 숫자가 들어와도 여기 오지 않는다.
private fun isNumberChar(c: Char): Boolean = c.isDigit() || c == '.'
private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

private fun scanWhile(
    expr: String,
    start: Int,
    out: MutableList<Token>,
    kind: TokenKind,
    accept: (Char) -> Boolean,
): Int {
    var i = start
    while (i < expr.length && accept(expr[i])) i++
    out += Token(kind, expr.substring(start, i))
    return i
}

private class Parser(private val tokens: List<Token>, private val vars: Map<String, Double>) {
    private var pos = 0

    fun evaluate(): Boolean {
        val result = parseOr()
        require(pos == tokens.size) { "unexpected trailing token '${tokens[pos].text}'" }
        return result
    }

    private fun parseOr(): Boolean {
        var acc = parseAnd()
        while (matchOp("||")) {
            val rhs = parseAnd() // 항상 파싱해 위치를 소비(부수효과 없어 단락 평가 불필요)
            acc = acc || rhs
        }
        return acc
    }

    private fun parseAnd(): Boolean {
        var acc = parseNot()
        while (matchOp("&&")) {
            val rhs = parseNot()
            acc = acc && rhs
        }
        return acc
    }

    private fun parseNot(): Boolean = if (matchOp("!")) !parseNot() else parseCmp()

    private fun parseCmp(): Boolean {
        val left = parseAtom()
        val op = peekCmpOp() ?: return left != FALSE_FLAG // 비교 없이 단독 → 참/거짓
        pos++
        val right = parseAtom()
        return compare(left, op, right)
    }

    private fun parseAtom(): Double {
        require(pos < tokens.size) { "unexpected end of expression" }
        val t = tokens[pos]
        return when (t.kind) {
            TokenKind.LPAREN -> {
                pos++
                val v = parseOr()
                expect(TokenKind.RPAREN)
                v.toFlag()
            }
            TokenKind.NUMBER -> { pos++; t.text.toDouble() }
            TokenKind.IDENT -> { pos++; atomValue(t.text) }
            else -> throw IllegalArgumentException("expected a value, got '${t.text}'")
        }
    }

    private fun atomValue(name: String): Double = when (name) {
        LIT_TRUE -> TRUE_FLAG
        LIT_FALSE -> FALSE_FLAG
        else -> vars[name] ?: throw IllegalArgumentException("unknown variable '$name'")
    }

    private fun compare(l: Double, op: String, r: Double): Boolean = when (op) {
        ">" -> l > r
        ">=" -> l >= r
        "<" -> l < r
        "<=" -> l <= r
        "==" -> l == r
        "!=" -> l != r
        else -> throw IllegalArgumentException("unknown operator '$op'")
    }

    private fun matchOp(op: String): Boolean {
        if (pos < tokens.size && tokens[pos].kind == TokenKind.OP && tokens[pos].text == op) {
            pos++
            return true
        }
        return false
    }

    private fun peekCmpOp(): String? =
        tokens.getOrNull(pos)?.takeIf { it.kind == TokenKind.OP && it.text in CMP_OPS }?.text

    private fun expect(kind: TokenKind) {
        require(pos < tokens.size && tokens[pos].kind == kind) { "expected $kind" }
        pos++
    }
}
