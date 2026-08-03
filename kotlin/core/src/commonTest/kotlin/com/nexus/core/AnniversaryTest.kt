package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AnniversaryTest {

    private val table = AnniversaryTable(
        version = "test",
        anniversaries = listOf(
            Anniversary(7, "일주일", "일주일"),
            Anniversary(30, "한 달", "한 달"),
            Anniversary(100, "백일", "백일"),
        ),
    )

    /** 만난 날이 1일째 — 0으로 세면 7일 기념일이 8일째에 뜬다. */
    @Test
    fun `만난 날이 1일째다`() {
        assertEquals(1, Anniversaries.daysTogether(firstMetEpochDay = 100, todayEpochDay = 100))
        assertEquals(7, Anniversaries.daysTogether(firstMetEpochDay = 100, todayEpochDay = 106))
    }

    /** 기기 시계가 뒤로 가도 음수 일수가 나오면 안 된다 — 표 조회가 이상하게 돈다. */
    @Test
    fun `시계가 뒤로 가도 음수가 아니다`() {
        assertEquals(0, Anniversaries.daysTogether(firstMetEpochDay = 100, todayEpochDay = 50))
    }

    @Test
    fun `도달한 기념일을 띄운다`() {
        assertEquals(7, Anniversaries.pendingAt(table, daysTogether = 7, celebratedDays = 0)?.days)
    }

    @Test
    fun `아직 안 왔으면 없다`() {
        assertNull(Anniversaries.pendingAt(table, daysTogether = 6, celebratedDays = 0))
    }

    /** 한 번 축하한 기념일은 다음 기념일까지 다시 안 뜬다 (완료 기준). */
    @Test
    fun `축하한 기념일은 다시 안 뜬다`() {
        assertNull(Anniversaries.pendingAt(table, daysTogether = 29, celebratedDays = 7))
        assertEquals(30, Anniversaries.pendingAt(table, daysTogether = 30, celebratedDays = 7)?.days)
    }

    /**
     * 100일째에 앱을 안 열었다면 101일째에라도 띄운다 — 그날 안 연 사람이야말로 붙잡을 대상이다.
     * 단 밀린 게 여럿이면 **가장 큰 것 하나만**: 7일과 30일을 겹쳐 띄우면 둘 다 값이 깎인다.
     */
    @Test
    fun `놓친 기념일은 가장 큰 것 하나만 띄운다`() {
        assertEquals(100, Anniversaries.pendingAt(table, daysTogether = 150, celebratedDays = 0)?.days)
    }

    @Test
    fun `표를 다 지나면 없다`() {
        assertNull(Anniversaries.pendingAt(table, daysTogether = 500, celebratedDays = 100))
    }

    @Test
    fun `빈 표는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            Anniversaries.parse("""{"version":"v1","anniversaries":[]}""")
        }
    }

    @Test
    fun `중복 일수는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            Anniversaries.parse(
                """{"version":"v1","anniversaries":[
                   {"days":7,"title":"a","body":"a"},{"days":7,"title":"b","body":"b"}]}""",
            )
        }
    }

    @Test
    fun `빈 카피는 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            Anniversaries.parse("""{"version":"v1","anniversaries":[{"days":7,"title":"","body":"a"}]}""")
        }
    }

    @Test
    fun `정상 표는 파싱된다`() {
        val parsed = Anniversaries.parse(
            """{"version":"v1","anniversaries":[{"days":7,"title":"일주일","body":"함께한 일주일"}]}""",
        )

        assertEquals(1, parsed.anniversaries.size)
        assertEquals("함께한 일주일", parsed.anniversaries.first().body)
    }
}
