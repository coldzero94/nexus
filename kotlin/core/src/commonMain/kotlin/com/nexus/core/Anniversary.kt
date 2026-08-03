package com.nexus.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 기념일 한 줄 (#111, E5-16) — [days]일째 되는 날 띄울 축하.
 *
 * 카피가 표에 있는 이유는 배지·기분 표와 같다: 기념일 추가는 JSON만 고치는 일이어야 한다.
 */
@Serializable
data class Anniversary(val days: Int, val title: String, val body: String)

@Serializable
data class AnniversaryTable(val version: String, val anniversaries: List<Anniversary>)

/**
 * 함께한 N일 기념일 (#111, E5-16).
 *
 * ## 마일스톤(#113)과 무엇이 다른가
 *
 * 마일스톤은 **활동일** 축이다("함께 움직인 100일" — 쉰 날은 안 센다). 여기는 **달력일** 축이다
 * ("만난 지 100일" — 쉰 날도 함께한 날이다). 둘을 하나로 합치면 축이 흐려진다: 활동일 100일은
 * 성취고, 만난 지 100일은 **그냥 시간이 흘렀다는 사실**이라 노력과 무관하게 온다. 후자가 애착
 * 자산이 되는 건 정확히 그 무조건성 때문이다(RESEARCH.md — 장기 리텐션 = 애착).
 *
 * ## 놓친 기념일은 뒤늦게라도 띄운다
 *
 * 100일째에 앱을 안 열었다고 100일 축하가 사라지면 안 된다 — 그날 안 연 사람이야말로 붙잡을
 * 대상이다. [pendingAt]은 "아직 축하 안 한 것 중 가장 큰 것"을 고른다. 여러 개가 밀렸어도
 * 하나만 띄운다: 7일과 30일을 한 화면에 겹쳐 띄우면 둘 다 값이 깎인다.
 */
object Anniversaries {
    private val json = Json { ignoreUnknownKeys = true }

    /** 표 파서 — 배지·기분 표와 같은 fail-fast 계약. */
    fun parse(jsonText: String): AnniversaryTable {
        val table = json.decodeFromString(AnniversaryTable.serializer(), jsonText)
        require(table.anniversaries.isNotEmpty()) { "anniversaries must not be empty" }
        val seen = HashSet<Int>()
        table.anniversaries.forEach {
            require(it.days > 0) { "anniversary ${it.days}: days must be positive" }
            require(seen.add(it.days)) { "duplicate anniversary day ${it.days}" }
            require(it.title.isNotBlank() && it.body.isNotBlank()) { "anniversary ${it.days}: blank copy" }
        }
        return table
    }

    /**
     * 함께한 일수 — 만난 날이 1일째다.
     *
     * 0일째가 아닌 이유: 사람은 "오늘부터 함께"를 1일로 센다(한국어 'N일째'). 0으로 세면
     * 7일 기념일이 실제로는 8일째에 뜬다.
     */
    fun daysTogether(firstMetEpochDay: Long, todayEpochDay: Long): Int =
        (todayEpochDay - firstMetEpochDay + 1).coerceAtLeast(0L).toInt()

    /**
     * 지금 띄울 기념일 — 아직 축하하지 않은 것 중 **가장 큰** 것. 없으면 null.
     *
     * @param celebratedDays 마지막으로 축하한 기념일의 [Anniversary.days]. 아직 없으면 0.
     */
    fun pendingAt(table: AnniversaryTable, daysTogether: Int, celebratedDays: Int): Anniversary? = table
        .anniversaries
        .filter { it.days in (celebratedDays + 1)..daysTogether }
        .maxByOrNull { it.days }
}
