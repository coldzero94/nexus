package com.nexus.core

/**
 * 걸음 수 → 보행 기본점수 환산 (#170). 걸음은 '수(count)'라 분당 산식([XpEngine.baseScore])과 별개.
 * 가정: **100걸음 ≈ 1분 보행 ≈ 1pt** ([STEPS_PER_POINT]) — 밸런스 튜닝 대상(#62 라운드2).
 */
object StepConversion {
    const val STEPS_PER_POINT = 100.0

    /** 걸음 수 → 보행 기본점수(걷기 축). */
    fun walkingBase(steps: Long): Double {
        require(steps >= 0) { "steps must be >= 0" }
        return steps / STEPS_PER_POINT
    }
}
