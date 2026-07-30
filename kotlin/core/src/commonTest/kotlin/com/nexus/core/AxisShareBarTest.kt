package com.nexus.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #263 — 성향 비중바. 독립 프로그레스바 3개를 한 스택 바로 합치면 **합이 1이 아닌 순간이 눈에
 * 보이게** 되므로, 정규화가 이 계산의 존재 이유다.
 */
class AxisShareBarTest {
    private fun shares(walk: Double, run: Double, strength: Double) = mapOf(
        ActivityType.WALKING to walk,
        ActivityType.RUNNING to run,
        ActivityType.STRENGTH to strength,
    )

    private fun assertSumsToOne(segments: List<AxisSegment>) {
        val sum = segments.sumOf { it.fraction }
        assertTrue(abs(sum - 1.0) < 1e-9, "세그먼트 합이 1이 아니다: $sum — 바가 덜 차거나 넘친다")
    }

    @Test
    fun `순서는 항상 걷기 달리기 근력이다`() {
        // 비중 순으로 정렬하면 어제와 오늘의 바를 눈으로 비교할 수 없다
        val segments = AxisShareBar.segments(shares(walk = 0.1, run = 0.8, strength = 0.1))

        assertEquals(
            listOf(ActivityType.WALKING, ActivityType.RUNNING, ActivityType.STRENGTH),
            segments.map { it.type },
        )
    }

    @Test
    fun `합이 이미 1이면 그대로 둔다`() {
        val segments = AxisShareBar.segments(shares(0.5, 0.3, 0.2))

        assertSumsToOne(segments)
        assertEquals(0.5, segments[0].fraction)
        assertEquals(0.3, segments[1].fraction)
        assertEquals(0.2, segments[2].fraction)
    }

    @Test
    fun `합이 1보다 작으면 정규화한다`() {
        // 이게 없으면 바가 덜 차서 "아직 뭔가 로딩 중"처럼 보인다
        val segments = AxisShareBar.segments(shares(0.2, 0.2, 0.1))

        assertSumsToOne(segments)
        assertEquals(0.4, segments[0].fraction, absoluteTolerance = 1e-9)
    }

    @Test
    fun `합이 1보다 크면 정규화한다`() {
        // 넘치면 마지막 세그먼트가 잘려 "근력이 아무리 해도 안 늘어난다"처럼 보인다
        val segments = AxisShareBar.segments(shares(0.6, 0.6, 0.3))

        assertSumsToOne(segments)
        assertEquals(0.4, segments[0].fraction, absoluteTolerance = 1e-9)
        assertEquals(0.2, segments[2].fraction, absoluteTolerance = 1e-9)
    }

    @Test
    fun `부동소수 누적 오차도 흡수한다`() {
        val third = 1.0 / 3.0
        val segments = AxisShareBar.segments(shares(third, third, third))

        assertSumsToOne(segments)
    }

    @Test
    fun `음수는 0으로 보고 정규화 분모에서 뺀다`() {
        // 음수를 그대로 두면 분모가 줄어 다른 축이 부풀려진다
        val segments = AxisShareBar.segments(shares(walk = 0.5, run = -0.2, strength = 0.5))

        assertSumsToOne(segments)
        assertEquals(0.0, segments[1].fraction)
        assertEquals(0.5, segments[0].fraction, absoluteTolerance = 1e-9)
    }

    @Test
    fun `최대 축이 지배 성향으로 표시된다`() {
        val segments = AxisShareBar.segments(shares(0.2, 0.7, 0.1))

        assertEquals(listOf(false, true, false), segments.map { it.dominant })
    }

    @Test
    fun `동률이면 앞선 축 하나만 지배로 본다`() {
        // 둘을 다 강조하면 강조가 아니게 된다
        val segments = AxisShareBar.segments(shares(0.5, 0.5, 0.0))

        assertEquals(1, segments.count { it.dominant })
        assertEquals(ActivityType.WALKING, segments.first { it.dominant }.type)
    }

    @Test
    fun `데이터가 없으면 지배 축도 없다`() {
        // 전부 0인데 어느 축을 지배로 강조하면 거짓말이 된다 (#213 첫 데이터 대기와도 정합)
        val segments = AxisShareBar.segments(shares(0.0, 0.0, 0.0))

        assertEquals(3, segments.size)
        assertTrue(segments.none { it.dominant })
        assertTrue(segments.all { it.fraction == 0.0 })
    }

    @Test
    fun `빈 맵도 세 축을 모두 돌려준다`() {
        // 축이 목록에서 사라지면 "이 축은 0"과 "이 축이 없다"가 구분되지 않는다
        val segments = AxisShareBar.segments(emptyMap())

        assertEquals(AxisShareBar.ORDER, segments.map { it.type })
        assertTrue(segments.none { it.dominant })
    }

    @Test
    fun `누락된 축은 0으로 채운다`() {
        val segments = AxisShareBar.segments(mapOf(ActivityType.RUNNING to 1.0))

        assertSumsToOne(segments)
        assertEquals(0.0, segments[0].fraction)
        assertEquals(1.0, segments[1].fraction)
        assertEquals(0.0, segments[2].fraction)
    }

    // ── 정수 퍼센트: 합 100 보존 + 0이 아닌 축의 최소 1% (#263 리뷰) ──

    private fun assertPercentsSumTo100(segments: List<AxisSegment>) {
        assertEquals(
            AxisShareBar.PERCENT_TOTAL,
            segments.sumOf { it.percent },
            "퍼센트 합이 100이 아니다: ${segments.map { it.percent }} — 합이 1이라고 적은 바 아래 99%가 적힌다",
        )
    }

    @Test
    fun `삼등분도 퍼센트 합이 100이다`() {
        // 절삭만 하면 33 + 33 + 33 = 99가 된다
        val segments = AxisShareBar.segments(shares(1.0, 1.0, 1.0))

        assertPercentsSumTo100(segments)
    }

    @Test
    fun `두 축 반반도 퍼센트 합이 100이다`() {
        assertPercentsSumTo100(AxisShareBar.segments(shares(1.0, 1.0, 0.0)))
    }

    @Test
    fun `한 축만 있으면 100이다`() {
        val segments = AxisShareBar.segments(shares(3.0, 0.0, 0.0))

        assertEquals(listOf(100, 0, 0), segments.map { it.percent })
    }

    @Test
    fun `데이터가 없으면 퍼센트도 전부 0이다`() {
        // 여기서 합 100을 강제하면 없는 활동에 퍼센트를 배정하게 된다
        assertEquals(listOf(0, 0, 0), AxisShareBar.segments(shares(0.0, 0.0, 0.0)).map { it.percent })
    }

    @Test
    fun `아주 작은 비중도 최소 1퍼센트로 표시된다`() {
        // 그려진 세그먼트가 "0%"로 읽히면 '이 축은 0'과 '아주 작다'가 구분되지 않는다
        val segments = AxisShareBar.segments(shares(walk = 0.998, run = 0.001, strength = 0.001))

        assertPercentsSumTo100(segments)
        assertTrue(segments[1].percent >= 1, "그려지는데 0%로 표시된다")
        assertTrue(segments[2].percent >= 1)
    }

    @Test
    fun `0인 축은 최소 1퍼센트를 받지 않는다`() {
        val segments = AxisShareBar.segments(shares(walk = 0.5, run = 0.5, strength = 0.0))

        assertEquals(0, segments[2].percent, "활동이 없는 축에 퍼센트를 주면 거짓말이다")
    }

    @Test
    fun `퍼센트가 있는 축은 fraction도 0이 아니다`() {
        // 두 표현이 어긋나면 바와 범례가 서로 다른 말을 한다
        AxisShareBar.segments(shares(0.7, 0.29, 0.01)).forEach { segment ->
            assertEquals(segment.fraction > 0.0, segment.percent > 0, "축 ${segment.type}에서 바와 범례가 어긋난다")
        }
    }

    @Test
    fun `그릴 세그먼트는 0폭을 제외한다`() {
        // 0폭을 그리면 세그먼트 갭만 남아 없는 구획선이 생긴다
        val drawable = AxisShareBar.drawable(shares(0.5, 0.0, 0.5))

        assertEquals(listOf(ActivityType.WALKING, ActivityType.STRENGTH), drawable.map { it.type })
    }

    @Test
    fun `데이터가 없으면 그릴 세그먼트도 없다`() {
        assertTrue(AxisShareBar.drawable(shares(0.0, 0.0, 0.0)).isEmpty())
    }

    @Test
    fun `라벨 목록은 0인 축도 유지한다`() {
        // 바는 drawable, 라벨은 segments — 라벨에서 0% 축이 사라지면 안 된다
        assertEquals(3, AxisShareBar.segments(shares(1.0, 0.0, 0.0)).size)
        assertEquals(1, AxisShareBar.drawable(shares(1.0, 0.0, 0.0)).size)
    }
}
