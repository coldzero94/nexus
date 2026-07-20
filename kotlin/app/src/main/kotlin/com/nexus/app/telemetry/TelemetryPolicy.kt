package com.nexus.app.telemetry

/**
 * 이벤트 파라미터 정책 (#46, E8-1) — 순수 검증 로직이라 단위 테스트로 고정된다.
 *
 * 3중 방어: ① 키 allowlist(MVP는 발생 사실만 기록하므로 **비어 있다**) ② 건강 파생
 * 의심 용어 차단 ③ 숫자 포함 값 전면 차단(수치가 실릴 통로 자체를 막는다).
 * ②·③은 미래에 ①에 키가 추가되어도 그대로 작동하는 안전망이다.
 */
object TelemetryPolicy {

    /** 위반 사유 — 테스트가 메시지 문자열이 아니라 이 타입으로 고정한다. */
    enum class Kind { KEY_NOT_ALLOWED, HEALTH_TERM_IN_KEY, NUMERIC_VALUE }

    data class Violation(val kind: Kind, val key: String)

    /** 파라미터 키 allowlist — MVP는 발생 사실만 기록: 의도적으로 빈 집합. */
    val allowedParamKeys: Set<String> = emptySet()

    private val healthTerms = listOf(
        "step", "walk", "run", "workout", "exercise", "duration", "minute",
        "heart", "bpm", "hr", "sleep", "calorie", "distance",
        "xp", "condition", "level", "energy",
    )
    private val digits = Regex("""\d""")

    fun violations(params: Map<String, String>): List<Violation> = violationsFor(params, allowedParamKeys)

    internal fun violationsFor(params: Map<String, String>, allowedKeys: Set<String>): List<Violation> = buildList {
        params.forEach { (key, value) ->
            if (key !in allowedKeys) add(Violation(Kind.KEY_NOT_ALLOWED, key))
            val lower = key.lowercase()
            if (healthTerms.any { lower.contains(it) }) add(Violation(Kind.HEALTH_TERM_IN_KEY, key))
            if (digits.containsMatchIn(value)) add(Violation(Kind.NUMERIC_VALUE, key))
        }
    }
}
