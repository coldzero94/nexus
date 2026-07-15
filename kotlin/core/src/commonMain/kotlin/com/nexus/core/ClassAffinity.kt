package com.nexus.core

/** 캐릭터 클래스 성향 (MVP §6). 활동 분포로 결정, 어느 축도 지배적이지 않으면 균형형. */
enum class ClassAffinity {
    ENDURANCE, // 지구력형 (걷기 중심)
    AGILITY, // 민첩형 (러닝 중심)
    STRENGTH, // 힘형 (근력 중심)
    BALANCED, // 균형형 (40% 규칙 미달)
}

/**
 * 클래스 성향 계산 (#20, E3-8): 최근 28일 활동 벡터(유형별 기본점수 합)로 성향 판정.
 * **40% 규칙**: 최대 비중 유형이 [THRESHOLD] 이상이면 그 성향, 아니면 [ClassAffinity.BALANCED].
 * 순수 함수 — 성장 탭·성향 변화 연출(S3)이 이 위에 쌓인다.
 */
object ClassAffinityCalculator {
    const val WINDOW_DAYS = 28
    const val THRESHOLD = 0.40

    fun affinity(walkBase: Double, runBase: Double, strengthBase: Double): ClassAffinity {
        require(walkBase >= 0 && runBase >= 0 && strengthBase >= 0) { "base must be >= 0" }
        val total = walkBase + runBase + strengthBase
        if (total <= 0.0) return ClassAffinity.BALANCED
        val fractions =
            listOf(
                ClassAffinity.ENDURANCE to walkBase / total,
                ClassAffinity.AGILITY to runBase / total,
                ClassAffinity.STRENGTH to strengthBase / total,
            )
        val (topClass, topFraction) = fractions.maxByOrNull { it.second }!!
        return if (topFraction >= THRESHOLD) topClass else ClassAffinity.BALANCED
    }
}
