package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이야기 조각 드롭 (#112, E5-14).
 *
 * **멱등성이 전부다.** 동기화 워커는 15분마다 최근 세션을 다시 읽는데, 드롭이 그때마다 새로
 * 굴려지면 운동 하나가 하루에 수십 개의 조각을 뱉는다. 결과가 세션 id의 함수라는 게 그걸 막는
 * 유일한 장치라, 여기가 깨지면 기능이 아니라 사고다.
 */
class StoryDropTest {

    private val table = StoryFragmentTable(
        version = "test",
        fragments = listOf(
            StoryFragment("a", "A", "본문 A", weight = 3),
            StoryFragment("b", "B", "본문 B", weight = 2),
            StoryFragment("c", "C", "본문 C", weight = 1),
        ),
    )

    // ── 멱등성 ──

    @Test
    fun `같은 세션은 몇 번을 굴려도 같은 결과다`() {
        val key = "hc-record-42"

        val results = (1..100).map { StoryDropPicker.drop(key, table, chancePercent = 50) }

        assertEquals(1, results.distinct().size, "같은 세션이 다른 조각을 뱉는다 — 재동기화마다 늘어난다")
    }

    @Test
    fun `세션이 다르면 결과도 갈린다`() {
        // 전부 같은 답이면 확률이 아니라 상수다
        val outcomes = (1..200).map { StoryDropPicker.drop("session-$it", table, chancePercent = 50) }

        assertTrue(outcomes.any { it != null }, "아무것도 안 나온다")
        assertTrue(outcomes.any { it == null }, "전부 나온다 — 확률이 아니다")
    }

    // ── 확률 경계 ──

    @Test
    fun `확률 0이면 절대 나오지 않는다`() {
        (1..200).forEach { assertNull(StoryDropPicker.drop("s-$it", table, chancePercent = 0)) }
    }

    @Test
    fun `확률 100이면 항상 나온다`() {
        (1..200).forEach { assertNotNull(StoryDropPicker.drop("s-$it", table, chancePercent = 100)) }
    }

    @Test
    fun `범위 밖 확률은 거부한다`() {
        assertFailsWith<IllegalArgumentException> { StoryDropPicker.drop("s", table, chancePercent = -1) }
        assertFailsWith<IllegalArgumentException> { StoryDropPicker.drop("s", table, chancePercent = 101) }
    }

    /** 실제 확률이 지정값 근처여야 한다 — 크게 벗어나면 해시 분포가 한쪽으로 쏠린 것이다. */
    @Test
    fun `실제 드롭률이 지정 확률에 가깝다`() {
        val samples = 4_000
        val hits = (1..samples).count { StoryDropPicker.drop("session-$it", table, chancePercent = 25) != null }

        val rate = hits * PERCENT / samples
        assertTrue(rate in 20..30, "드롭률 $rate% — 25% 근처여야 한다")
    }

    // ── 가중치 ──

    /**
     * 확률 판정과 조각 선택이 **같은 자리**를 쓰면, 드롭된 세션은 해시가 낮은 구간이라
     * 가중치 낮은 조각만 계속 나온다. 세 조각이 모두 등장하는지로 그걸 잡는다.
     */
    @Test
    fun `가중치가 다른 조각이 모두 등장한다`() {
        val drops = (1..2_000).mapNotNull { StoryDropPicker.drop("session-$it", table, chancePercent = 100) }

        assertEquals(table.fragments.map { it.id }.toSet(), drops.map { it.id }.toSet(), "안 나오는 조각이 있다")
    }

    @Test
    fun `가중치가 높을수록 자주 나온다`() {
        val drops = (1..4_000).mapNotNull { StoryDropPicker.drop("session-$it", table, chancePercent = 100) }
        val counts = drops.groupingBy { it.id }.eachCount()

        assertTrue(counts.getValue("a") > counts.getValue("c"), "가중치 3이 1보다 드물다: $counts")
    }

    // ── 표 검증 ──

    @Test
    fun `빈 표는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            StoryDropPicker.parse("""{"version":"v1","fragments":[]}""")
        }
    }

    @Test
    fun `가중치 0 이하는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            StoryDropPicker.parse("""{"version":"v1","fragments":[{"id":"a","title":"A","body":"b","weight":0}]}""")
        }
    }

    @Test
    fun `중복 id는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            StoryDropPicker.parse(
                """{"version":"v1","fragments":[
                   {"id":"a","title":"A","body":"b"},{"id":"a","title":"A2","body":"b2"}]}""",
            )
        }
    }

    private companion object {
        const val PERCENT = 100
    }
}
