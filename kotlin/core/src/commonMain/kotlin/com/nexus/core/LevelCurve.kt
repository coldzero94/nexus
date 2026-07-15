package com.nexus.core

import kotlin.math.pow
import kotlin.math.roundToInt

/** 레벨업 전이 이벤트 (#18). from < to. */
data class LevelUp(
    val from: Int,
    val to: Int,
)

/**
 * 레벨 곡선 (#18, MVP §5): **누적 XP 기준** `레벨 N 도달 = 100 × N^1.5` (#2 확정).
 * 순수 함수 — E3 원장·성장 탭·레벨업 연출이 이 위에 쌓인다.
 */
object LevelCurve {
    const val BASE = 100.0
    const val EXPONENT = 1.5
    private const val EPS = 1e-9 // 부동소수 경계(예: 8^(2/3)=3.9999…) 보정

    /** 레벨 [level] 도달에 필요한 누적 XP. */
    fun xpForLevel(level: Int): Int {
        require(level >= 0) { "level must be >= 0" }
        return (BASE * level.toDouble().pow(EXPONENT)).roundToInt()
    }

    /** 누적 XP로 도달한 정수 레벨. 0 XP → 0. */
    fun levelForXp(cumulativeXp: Int): Int {
        require(cumulativeXp >= 0) { "cumulativeXp must be >= 0" }
        return ((cumulativeXp / BASE).pow(1.0 / EXPONENT) + EPS).toInt()
    }

    /** 현재 레벨에서 다음 레벨까지 진행률 0.0~1.0 (성장 탭 진행바). */
    fun progressToNextLevel(cumulativeXp: Int): Double {
        val level = levelForXp(cumulativeXp)
        val cur = xpForLevel(level)
        val next = xpForLevel(level + 1)
        if (next <= cur) return 0.0
        return ((cumulativeXp - cur).toDouble() / (next - cur)).coerceIn(0.0, 1.0)
    }

    /** 누적 XP 변화로 레벨업 감지. 레벨 증가 없으면 null. */
    fun levelUp(
        prevCumulativeXp: Int,
        newCumulativeXp: Int,
    ): LevelUp? {
        val from = levelForXp(prevCumulativeXp)
        val to = levelForXp(newCumulativeXp)
        return if (to > from) LevelUp(from, to) else null
    }
}
