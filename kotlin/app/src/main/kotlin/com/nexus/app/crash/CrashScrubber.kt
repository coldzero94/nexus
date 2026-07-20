package com.nexus.app.crash

/**
 * 크래시 페이로드 수치 스크러버 (#48·#201) — 건강 파생 수치가 크래시 보고로 유출되는 것을 막는
 * 2차 방어의 순수 로직. 앱 로그가 수치를 안 담는 것이 1차 방어(#46 정책)이고, 이 함수는
 * 미래에 값 보간 예외 메시지("steps=8432")가 생겨도 숫자가 나가지 않게 마지막에 한 번 더 지운다.
 *
 * 순수 함수라 [CrashScrubberTest]가 동작을 고정한다 — 프라이버시 불변식이 리팩터로 조용히
 * 무력화되면 CI가 잡는다(#46 TelemetryPolicy와 같은 원칙).
 */
object CrashScrubber {

    private val DIGITS = Regex("""\d+""")

    /** 연속 숫자를 `#`으로 치환. null 안전(그대로 null 반환). */
    fun scrub(text: String?): String? = text?.replace(DIGITS, "#")
}
