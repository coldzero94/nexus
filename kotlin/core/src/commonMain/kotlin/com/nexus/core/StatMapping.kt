package com.nexus.core

/**
 * 능력치 (MVP §2). 지구력·민첩·힘·회복은 해금, 집중·친화는 잠금(커뮤니티/후속).
 * 회복은 활동이 아니라 휴식 리듬으로 채워짐(S4 컨디션) — 활동 배분엔 안 나타남.
 */
enum class Stat(val locked: Boolean) {
    ENDURANCE(false), // 지구력
    AGILITY(false), // 민첩
    STRENGTH(false), // 힘
    RECOVERY(false), // 회복 (휴식 기반, S4)
    FOCUS(true), // 집중 — 잠금
    AFFINITY(true), // 친화 — 잠금
}

/**
 * 능력치 배분 매핑 (#17): 활동 유형의 XP를 능력치들에 배분. 잔여(반올림)는 최대 가중치 스탯에 → 합 보존.
 */
object StatMapping {

    private val weights: Map<ActivityType, Map<Stat, Double>> = mapOf(
        ActivityType.WALKING to mapOf(Stat.ENDURANCE to 1.0),
        ActivityType.RUNNING to mapOf(Stat.ENDURANCE to 0.5, Stat.AGILITY to 0.5),
        ActivityType.STRENGTH to mapOf(Stat.STRENGTH to 1.0),
    )

    /** [xp]를 [type]의 능력치들에 정수 배분. 배분 합 = xp(보존). xp 0이면 빈 맵. */
    fun distribute(type: ActivityType, xp: Int): Map<Stat, Int> {
        require(xp >= 0) { "xp must be >= 0" }
        if (xp == 0) return emptyMap()
        val w = weights.getValue(type)
        val result = LinkedHashMap<Stat, Int>()
        var allocated = 0
        for ((stat, weight) in w) {
            val pts = (xp * weight).toInt()
            result[stat] = pts
            allocated += pts
        }
        val remainder = xp - allocated
        if (remainder > 0) {
            val top = w.maxByOrNull { it.value }!!.key
            result[top] = (result[top] ?: 0) + remainder
        }
        return result
    }

    /** 잠긴 능력치(표시만). */
    val lockedStats: List<Stat> get() = Stat.entries.filter { it.locked }

    /** 해금된 능력치. */
    val unlockedStats: List<Stat> get() = Stat.entries.filter { !it.locked }
}
