package com.nexus.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 월 한정 배지 1종 (#38, E5-5). [period]("YYYY-MM") 달에만 획득 가능하고, 한 번 얻으면 캐릭터에
 * 영구히 남는 수집 자산이다(BENCHMARK.md: Apple/Garmin 검증 패턴). [whenExpr]는 그 달 지표에 대한
 * 불리언 식(문자열) — [BoolExpr]가 해석하므로 새 달 배지 추가·조건 변경이 데이터 수정만으로 된다.
 */
@Serializable
data class MonthlyBadge(
    val id: String,
    val name: String,
    val description: String,
    val period: String,
    @SerialName("when") val whenExpr: String,
)

/** 월 한정 배지 표 — 앱 assets JSON에서 로드. 배지 추가 = 항목만 추가(코드 무수정). */
@Serializable
data class MonthlyBadgeTable(val version: String, val badges: List<MonthlyBadge>)

/**
 * 월 한정 평가 입력 — 이번 달로 스코프된 지표. [toVars]가 식 변수 맵을 만든다. 신호는 월 단위 집계
 * (걸음·활동일·원정·XP)로, 정산/앱 진입 시 호출자가 채운다. 새 신호 추가 시에만 코드 변경이 필요하다.
 */
data class MonthlyBadgeContext(
    val monthActiveDays: Int = 0,
    val monthSteps: Int = 0,
    val monthExpeditions: Int = 0,
    val monthXp: Int = 0,
) {
    fun toVars(): Map<String, Double> = mapOf(
        "monthActiveDays" to monthActiveDays.toDouble(),
        "monthSteps" to monthSteps.toDouble(),
        "monthExpeditions" to monthExpeditions.toDouble(),
        "monthXp" to monthXp.toDouble(),
    )

    companion object {
        /** 월 한정 배지 `when`에서 참조 가능한 변수 어휘 — 파서가 오탈자/미지원 변수를 걸러낸다. */
        val VARS: Set<String> = setOf("monthActiveDays", "monthSteps", "monthExpeditions", "monthXp")
    }
}

/**
 * 월 한정 배지 캘린더 (#38, E5-5) — [period]에 활성인 배지만 평가한다. 지난 달 배지는 그 달이
 * 지나면 획득 불가(캘린더). 이미 얻은 배지는 영구 보유라 [newlyUnlocked]의 차집합으로만 늘어난다.
 */
object MonthlyBadgeCalendar {
    /** [period]("YYYY-MM")에 활성인(그 달에만 획득 가능한) 배지. */
    fun activeBadges(table: MonthlyBadgeTable, period: String): List<MonthlyBadge> =
        table.badges.filter { it.period == period }

    /** 현재 달에 활성이고 조건 충족한 배지 id. 지난/다음 달 배지는 제외. */
    fun unlocked(table: MonthlyBadgeTable, period: String, vars: Map<String, Double>): Set<String> =
        activeBadges(table, period).filter { BoolExpr.eval(it.whenExpr, vars) }.map { it.id }.toSet()

    fun unlocked(table: MonthlyBadgeTable, period: String, context: MonthlyBadgeContext): Set<String> =
        unlocked(table, period, context.toVars())

    /** 이전 획득([alreadyEarned]) 대비 이번 달 새로 열린 배지. 배지는 영구 수집 — 차집합만. */
    fun newlyUnlocked(
        table: MonthlyBadgeTable,
        period: String,
        context: MonthlyBadgeContext,
        alreadyEarned: Set<String>,
    ): Set<String> = unlocked(table, period, context) - alreadyEarned
}

/** 월 한정 배지 표 파서 — 기분(#28)·배지(#69)와 같은 fail-fast 계약. period 형식도 검증. */
object MonthlyBadgeTableReader {
    private val json = Json { ignoreUnknownKeys = true }
    private val periodPattern = Regex("""\d{4}-(0[1-9]|1[0-2])""")

    fun parse(jsonText: String): MonthlyBadgeTable {
        val table = json.decodeFromString(MonthlyBadgeTable.serializer(), jsonText)
        require(table.badges.isNotEmpty()) { "badges must not be empty" }
        val seenIds = HashSet<String>()
        table.badges.forEach { badge ->
            require(badge.id.isNotBlank()) { "badge id must not be blank" }
            require(seenIds.add(badge.id)) { "duplicate badge id '${badge.id}'" }
            require(periodPattern.matches(badge.period)) { "badge '${badge.id}': bad period '${badge.period}'" }
            require(badge.whenExpr.isNotBlank()) { "badge '${badge.id}': when must not be blank" }
            val unknown = BoolExpr.identifiers(badge.whenExpr) - MonthlyBadgeContext.VARS
            require(unknown.isEmpty()) { "badge '${badge.id}': unknown vars $unknown" }
        }
        return table
    }
}
