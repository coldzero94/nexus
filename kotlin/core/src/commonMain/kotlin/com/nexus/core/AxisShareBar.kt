package com.nexus.core

/**
 * 스택 비중바의 한 세그먼트 (#263, E16-13).
 *
 * @property type 활동 축. 그리는 순서는 [AxisShareBar.segments]가 고정한다.
 * @property fraction 정규화된 비중(합 = 1). 0인 축도 세그먼트로 남는다 — 라벨을 함께 보여줘야
 *   "이 축은 0이다"와 "이 축이 목록에 없다"가 구분된다.
 * @property percent 표시용 정수 퍼센트. **합이 정확히 100**이고(데이터가 있으면), 비중이 0이 아니면
 *   최소 1이다 — 단순 절삭으로는 `33+33+33=99`가 되고, 그려진 얇은 세그먼트가 "0%"로 읽혀
 *   [fraction] KDoc이 막으려던 그 혼동이 되살아난다.
 * @property dominant 최대 비중 축인가 — 지배 성향 강조에 쓴다. 동률이면 [AxisShareBar]의 순서상
 *   앞선 축 하나만 true다(둘을 다 강조하면 강조가 아니게 된다).
 */
data class AxisSegment(val type: ActivityType, val fraction: Double, val percent: Int, val dominant: Boolean)

/**
 * 성향 비중바 계산 (#263, E16-13) — 스톡 프로그레스 3줄을 **하나의 스택 바**로 대체하기 위한 순수 로직.
 *
 * ## 왜 정규화가 필요한가
 *
 * `axisShares`는 상류에서 비율로 만들어지지만 **합이 정확히 1이라는 보장이 없다**. 부동소수 누적
 * 오차가 있고, 축이 일부만 채워질 수도 있다. 3개의 독립 프로그레스바로 그릴 때는 각자 0~1이라
 * 문제가 안 보였는데, **한 바에 이어 붙이면 합이 1이 아닌 순간 바가 덜 차거나 넘친다** — 넘치면
 * 마지막 세그먼트가 잘려서 "근력을 아무리 해도 안 늘어난다"처럼 보인다.
 *
 * ## 순서를 고정하는 이유
 *
 * `walk → run → strength`로 **항상** 같은 순서다. 비중 순으로 정렬하면 하루 활동에 따라 색 순서가
 * 바뀌어, 어제와 오늘의 바를 눈으로 비교할 수 없다. 스택 바의 가치가 "구성의 변화를 본다"는 데
 * 있으므로 순서는 데이터에 의존해선 안 된다.
 */
object AxisShareBar {
    /** 그리는 순서 — 데이터에 의존하지 않는다. */
    val ORDER = listOf(ActivityType.WALKING, ActivityType.RUNNING, ActivityType.STRENGTH)

    /**
     * 정규화된 세그먼트 목록. 항상 [ORDER] 크기이고 순서도 그대로다.
     *
     * @param axisShares 축별 비중(합이 1이 아니어도 된다). 음수는 0으로 본다 — 비중에 음수는
     *   의미가 없고, 그대로 두면 정규화 분모가 줄어 다른 축이 부풀려진다.
     * @return 합이 1인 세그먼트. 입력이 전부 0/음수/빈 맵이면 **전부 fraction 0**이고 [AxisSegment.dominant]도
     *   없다 — 데이터가 없는데 어느 축을 지배로 강조하면 거짓말이 된다.
     */
    fun segments(axisShares: Map<ActivityType, Double>): List<AxisSegment> {
        val raw = ORDER.map { type -> axisShares[type]?.coerceAtLeast(0.0) ?: 0.0 }
        val total = raw.sum()
        val dominantIndex = if (total <= 0.0) -1 else raw.indexOf(raw.max())
        val fractions = if (total <= 0.0) raw.map { 0.0 } else raw.map { it / total }
        val percents = wholePercents(fractions)

        return ORDER.mapIndexed { index, type ->
            AxisSegment(
                type = type,
                fraction = fractions[index],
                percent = percents[index],
                dominant = index == dominantIndex,
            )
        }
    }

    /**
     * 비중 → 정수 퍼센트. **합이 정확히 [PERCENT_TOTAL]**이 되게 최대잔여법으로 배분한다.
     *
     * 절삭만 하면 `1/3, 1/3, 1/3`이 `33, 33, 33 = 99`가 된다. 합이 1이라고 문서에 적어둔 바 아래에
     * 99%가 적혀 있으면 그 문서가 거짓이 되고, 사용자는 1%가 어디로 갔는지 묻게 된다.
     *
     * 0이 아닌 비중은 **최소 1%**를 보장한다 — 그려진 세그먼트가 "0%"로 읽히면 "이 축은 0"과
     * "이 축이 아주 작다"가 구분되지 않는다. 그 1%는 최댓값 축에서 빌린다(가장 덜 티는 곳).
     *
     * `core`의 기존 관행과 같다 — `StatMapping`·`GrowthCalculator`도 나머지를 최상위 버킷으로 밀어
     * 합을 보존한다.
     */
    private fun wholePercents(fractions: List<Double>): List<Int> {
        if (fractions.all { it <= 0.0 }) return fractions.map { 0 }

        val scaled = fractions.map { it * PERCENT_TOTAL }
        val floors = scaled.map { it.toInt() }.toMutableList()
        // 0이 아닌 축은 먼저 1%를 확보한다 — 이후 잔여 배분이 그만큼 줄어든다
        fractions.indices.forEach { i -> if (fractions[i] > 0.0 && floors[i] == 0) floors[i] = 1 }

        var remainder = PERCENT_TOTAL - floors.sum()
        // 잔여가 양수면 소수부가 큰 순서로 +1, 음수면(최소 1% 보장으로 초과) 가장 큰 축에서 뺀다
        val byFraction = scaled.indices.sortedByDescending { scaled[it] - scaled[it].toInt() }
        var cursor = 0
        while (remainder > 0 && byFraction.isNotEmpty()) {
            floors[byFraction[cursor % byFraction.size]] += 1
            cursor++
            remainder--
        }
        val byValue = scaled.indices.sortedByDescending { scaled[it] }
        cursor = 0
        while (remainder < 0) {
            val target = byValue[cursor % byValue.size]
            if (floors[target] > 1) {
                floors[target] -= 1
                remainder++
            }
            cursor++
        }
        return floors
    }

    /**
     * 비중이 0이 아닌 세그먼트만 — 바를 실제로 그릴 대상.
     *
     * 0폭 세그먼트를 그리면 세그먼트 사이 갭만 남아 **없는 구획선이 생긴다**. 라벨 목록은 [segments]
     * 전체를 쓰고(0인 축도 "0%"로 보여줘야 한다), 바는 이걸 쓴다.
     */
    fun drawable(axisShares: Map<ActivityType, Double>): List<AxisSegment> =
        segments(axisShares).filter { it.fraction > 0.0 }

    /** 퍼센트 합. 표시 계약이 100이라 상수로 둔다. */
    const val PERCENT_TOTAL = 100
}
