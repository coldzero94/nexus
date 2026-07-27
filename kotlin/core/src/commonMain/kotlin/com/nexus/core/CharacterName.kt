package com.nexus.core

/**
 * 캐릭터 이름 규칙 (#216, E14-6) — 순수. 사용자가 지어준 이름을 앱 카피가 호명할 수 있게
 * 정규화·검증한다. 이름은 **로컬 표시 전용**(텔레메트리·크래시 페이로드·서버 전송 금지, PII 위생).
 */
object CharacterName {
    const val MAX_LENGTH = 12

    /**
     * 입력 정규화 — 앞뒤 공백 제거 + 내부 연속 공백 1칸으로. 제어문자는 제거한다(줄바꿈으로 카피가
     * 깨지지 않게). 길이 제한은 [isValid]가 판정하고 여기선 자르지 않는다(사용자 입력 보존).
     */
    fun normalize(raw: String): String = raw
        .filter { !it.isISOControl() }
        .trim()
        .replace(WHITESPACE_RUN, " ")

    /** 저장 가능한 이름인가 — 정규화 후 1~[MAX_LENGTH]자. 공백만/빈값은 거부. */
    fun isValid(raw: String): Boolean = normalize(raw).length in 1..MAX_LENGTH

    /**
     * 표시용 이름 — 유효하면 정규화된 이름, 아니면 null(호출측이 무명 폴백 카피를 쓴다).
     * 저장값이 과거 규칙으로 들어왔거나 손상된 경우에도 여기서 걸러진다.
     */
    fun displayOrNull(raw: String?): String? {
        val name = normalize(raw ?: return null)
        return name.takeIf { it.length in 1..MAX_LENGTH }
    }
}

private val WHITESPACE_RUN = Regex("\\s+")
