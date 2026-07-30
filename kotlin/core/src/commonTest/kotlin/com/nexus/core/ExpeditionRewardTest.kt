package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * #68 — 원정 보상 추첨. 순수 함수라 seed가 같으면 결과가 같다(재시작 정합, [ExpeditionEngine]과 같은 규율).
 */
class ExpeditionRewardTest {

    private fun table(vararg weights: Int) = ExpeditionRewardTable(
        version = "test",
        rewards = weights.mapIndexed { i, w -> ExpeditionReward(id = "r$i", title = "t$i", body = "b$i", weight = w) },
    )

    @Test
    fun `같은 seed면 같은 보상`() {
        val t = table(1, 2, 3)

        assertEquals(ExpeditionRewardPicker.pick(t, 42L), ExpeditionRewardPicker.pick(t, 42L))
    }

    @Test
    fun `가중치 구간을 순서대로 나눈다`() {
        // 1/2/3 → 합 6. roll 0 → r0, 1~2 → r1, 3~5 → r2
        val t = table(1, 2, 3)

        assertEquals("r0", ExpeditionRewardPicker.pick(t, 0L).id)
        assertEquals("r1", ExpeditionRewardPicker.pick(t, 1L).id)
        assertEquals("r1", ExpeditionRewardPicker.pick(t, 2L).id)
        assertEquals("r2", ExpeditionRewardPicker.pick(t, 3L).id)
        assertEquals("r2", ExpeditionRewardPicker.pick(t, 5L).id)
    }

    @Test
    fun `seed가 합을 넘으면 순환한다`() {
        val t = table(1, 2, 3)

        assertEquals(ExpeditionRewardPicker.pick(t, 0L).id, ExpeditionRewardPicker.pick(t, 6L).id)
    }

    /**
     * 음수 seed가 항상 첫 보상을 뽑으면 분포가 한쪽으로 쏠린다 — `%`가 음수를 내기 때문에
     * 절댓값을 먼저 취한다. 시각 기반 seed가 음수가 될 일은 드물지만, 그 드묾이 보장은 아니다.
     */
    @Test
    fun `음수 seed도 분포에 참여한다`() {
        val t = table(1, 2, 3)

        assertEquals("r2", ExpeditionRewardPicker.pick(t, -5L).id)
        assertEquals("r1", ExpeditionRewardPicker.pick(t, -1L).id)
    }

    @Test
    fun `모든 보상이 뽑힐 수 있다`() {
        // 가중치가 있는데 절대 안 뽑히는 보상이 있으면 표가 거짓말을 하는 것이다
        val t = table(1, 1, 1, 1, 1)
        val picked = (0L until 5L).map { ExpeditionRewardPicker.pick(t, it).id }.toSet()

        assertEquals(setOf("r0", "r1", "r2", "r3", "r4"), picked)
    }

    @Test
    fun `가중치가 클수록 자주 뽑힌다`() {
        val t = table(1, 9)
        val counts = (0L until 100L).map { ExpeditionRewardPicker.pick(t, it).id }.groupingBy { it }.eachCount()

        assertTrue(counts.getValue("r1") > counts.getValue("r0"), "가중치가 분포에 반영되지 않는다")
    }

    // ── 표 검증: 조용한 실패를 막는다 ──

    @Test
    fun `정상 표를 파싱한다`() {
        val parsed = ExpeditionRewardPicker.parse(
            """{"version":"v1","rewards":[{"id":"a","title":"t","body":"b","weight":2}]}""",
        )

        assertEquals(1, parsed.rewards.size)
        assertEquals(2, parsed.rewards.single().weight)
    }

    @Test
    fun `가중치를 생략하면 1이다`() {
        val parsed = ExpeditionRewardPicker.parse("""{"version":"v1","rewards":[{"id":"a","title":"t","body":"b"}]}""")

        assertEquals(1, parsed.rewards.single().weight)
    }

    @Test
    fun `빈 표는 거부한다`() {
        // 조용히 통과시키면 개봉이 보상 없이 끝나고 원인을 찾기 어렵다
        assertFailsWith<IllegalArgumentException> {
            ExpeditionRewardPicker.parse("""{"version":"v1","rewards":[]}""")
        }
    }

    @Test
    fun `0 이하 가중치는 거부한다`() {
        // 0이면 영원히 안 뽑히는 보상이 표에 남는다
        assertFailsWith<IllegalArgumentException> {
            ExpeditionRewardPicker.parse(
                """{"version":"v1","rewards":[{"id":"a","title":"t","body":"b","weight":0}]}""",
            )
        }
    }

    @Test
    fun `중복 id는 거부한다`() {
        // 후속 도감이 id로 추적하므로 중복이면 무엇을 받았는지 말할 수 없다
        assertFailsWith<IllegalArgumentException> {
            ExpeditionRewardPicker.parse(
                """{"version":"v1","rewards":[
                    {"id":"a","title":"t","body":"b"},{"id":"a","title":"t2","body":"b2"}
                ]}""",
            )
        }
    }

    @Test
    fun `빈 id는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            ExpeditionRewardPicker.parse("""{"version":"v1","rewards":[{"id":"","title":"t","body":"b"}]}""")
        }
    }

    /** 보상에 XP·수치 필드가 없다 — 원정이 활동 무관 XP 경로가 되지 않게 하는 구조적 보장. */
    @Test
    fun `보상에 수치 필드가 없다`() {
        val reward = ExpeditionReward(id = "a", title = "t", body = "b")

        // weight는 뽑기용이지 사용자에게 주는 값이 아니다 — 그 외 수치 필드가 생기면 여기서 걸린다
        assertEquals("a", reward.id)
        assertEquals("t", reward.title)
        assertEquals("b", reward.body)
        assertEquals(1, reward.weight)
    }
}
