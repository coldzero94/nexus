package com.nexus.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 배지 1종 (#69, E5-8). [whenExpr]는 해금 조건 불리언 식(문자열) — [BoolExpr]가 해석하므로
 * 조건·임계값을 표에서 바꿔도 코드 수정이 없다(완료 기준: 조건 데이터화). 배지는 캐릭터에
 * 영구히 남는 수집 자산이라(BENCHMARK.md) 한 번 해금되면 회수 기제가 없다.
 */
@Serializable
data class Badge(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("when") val whenExpr: String,
)

/** 배지 표 — 앱 assets JSON에서 로드. 배지 추가·조건 수정 = JSON만(코드 무수정). */
@Serializable
data class BadgeTable(val version: String, val badges: List<Badge>)

/**
 * 해금 평가 입력 — 플레이어 상태 신호. [toVars]가 식 변수 맵을 만든다. 신호는 기존 엔진에서 파생:
 * [level]/[cumulativeXp]=원장 합산(#163)·레벨커브, [streakDays]/[activeDaysTotal]=베이스라인·연속성,
 * [expeditionsCompleted]=원정(#34), [bestDaySteps]=걸음(#7). 카테고리(클래스 성향 등)는 후속(문자열 식 필요).
 */
data class BadgeContext(
    val level: Int = 1,
    val cumulativeXp: Int = 0,
    val activeDaysTotal: Int = 0,
    val streakDays: Int = 0,
    val expeditionsCompleted: Int = 0,
    val bestDaySteps: Int = 0,
) {
    fun toVars(): Map<String, Double> = mapOf(
        "level" to level.toDouble(),
        "cumulativeXp" to cumulativeXp.toDouble(),
        "activeDaysTotal" to activeDaysTotal.toDouble(),
        "streakDays" to streakDays.toDouble(),
        "expeditionsCompleted" to expeditionsCompleted.toDouble(),
        "bestDaySteps" to bestDaySteps.toDouble(),
    )

    companion object {
        /** 배지 `when`에서 참조 가능한 변수 어휘 — 파서가 이 집합으로 오탈자/미지원 변수를 걸러낸다. */
        val VARS: Set<String> = setOf(
            "level",
            "cumulativeXp",
            "activeDaysTotal",
            "streakDays",
            "expeditionsCompleted",
            "bestDaySteps",
        )
    }
}

/**
 * 해금 조건 평가기 (#69, E5-8) — 조건이 참인 배지 집합을 낸다. [newlyUnlocked]는 이전 해금 대비
 * 이번에 새로 열린 배지로, #28 기분 트리거의 `newBadge`(뿌듯) 신호원이 된다.
 */
object BadgeEvaluator {
    fun unlocked(table: BadgeTable, vars: Map<String, Double>): Set<String> = table.badges
        .filter { BoolExpr.eval(it.whenExpr, vars) }
        .map { it.id }
        .toSet()

    fun unlocked(table: BadgeTable, context: BadgeContext): Set<String> = unlocked(table, context.toVars())

    /** 이전 해금 집합([alreadyUnlocked]) 대비 이번 평가에서 새로 해금된 배지. 배지는 회수 없음 — 차집합만. */
    fun newlyUnlocked(
        table: BadgeTable,
        context: BadgeContext,
        alreadyUnlocked: Set<String>,
    ): Set<String> = unlocked(table, context) - alreadyUnlocked
}

/** 배지 표 파서 — 기분(#28)·대사(#29)와 같은 fail-fast 계약. */
object BadgeTableReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String): BadgeTable {
        val table = json.decodeFromString(BadgeTable.serializer(), jsonText)
        require(table.badges.isNotEmpty()) { "badges must not be empty" }
        val seenIds = HashSet<String>()
        table.badges.forEach { badge ->
            require(badge.id.isNotBlank()) { "badge id must not be blank" }
            require(seenIds.add(badge.id)) { "duplicate badge id '${badge.id}'" }
            require(badge.whenExpr.isNotBlank()) { "badge '${badge.id}': when must not be blank" }
            // 오탈자·미지원 변수는 로드 시점에 실패시킨다 — 런타임에 조용히 미해금되는 것 방지.
            val unknown = BoolExpr.identifiers(badge.whenExpr) - BadgeContext.VARS
            require(unknown.isEmpty()) { "badge '${badge.id}': unknown vars $unknown" }
        }
        return table
    }
}
