package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 밸런스 CSV 픽스처 하네스 (#185, E3-21) — `resources/balance/` 아래 CSV가 밸런스 표의
 * **단일 원천**이다. 치완의 스프레드시트(#2) export를 그대로 커밋하면 CI(:core:jvmTest)가
 * 엔진과 대조한다: **표 수정 = CSV 수정만, 코드 무수정**.
 *
 * 파일 기반이라 jvmTest 전용(공용 인라인 하네스는 XpParityHarnessTest가 iOS 측 최소 유지).
 * 새 표(파일) 추가 시 이 클래스에 디스패처 테스트 1개를 추가한다 — [fixturesArePresent]가
 * 누락·미배선 파일을 잡는다.
 */
class BalanceCsvHarnessTest {

    private val wiredTables = setOf("daily_xp.csv", "level_curve.csv", "steps.csv", "condition.csv", "energy.csv")

    @Test
    fun fixturesArePresent_andAllWired() {
        // 리소스 패키징 회귀·미배선 표를 소리나게 잡는다 — 없는데 통과하는 조용한 스킵 금지
        wiredTables.forEach { name ->
            assertTrue(rows(name).isNotEmpty(), "$name: 픽스처가 없거나 비어 있음")
        }
    }

    @Test
    fun dailyXp_matchesCsv() {
        rows("daily_xp.csv").forEach { (base, coef, trust, balance, expected) ->
            assertEquals(
                expected.toInt(),
                XpEngine.dailyXp(base.toDouble(), coef.toDouble(), trust.toDouble(), balance.toDouble()),
                "daily_xp.csv row: $base,$coef,$trust,$balance",
            )
        }
    }

    @Test
    fun levelCurve_matchesCsv() {
        rows("level_curve.csv").forEach { (level, xp) ->
            assertEquals(xp.toInt(), LevelCurve.xpForLevel(level.toInt()), "level_curve.csv level=$level")
        }
    }

    @Test
    fun stepsConversion_matchesCsv() {
        rows("steps.csv").forEach { (steps, expected) ->
            assertEquals(
                expected.toDouble(),
                StepConversion.walkingBase(steps.toLong()),
                1e-9,
                "steps.csv steps=$steps",
            )
        }
    }

    @Test
    fun condition_matchesCsv() {
        rows("condition.csv").forEach { (current, points, restMode, restedYesterday, expected) ->
            assertEquals(
                expected.toDouble(),
                ConditionEngine.nextDay(
                    current.toDouble(),
                    points.toDouble(),
                    restMode = restMode.toBooleanStrict(),
                    restedYesterday = restedYesterday.toBooleanStrict(),
                ),
                1e-9,
                "condition.csv row: $current,$points,$restMode,$restedYesterday",
            )
        }
    }

    @Test
    fun energy_matchesCsv() {
        rows("energy.csv").forEach { (totalXp, spent, expected) ->
            assertEquals(
                expected.toInt(),
                EnergyEngine.balance(totalXp.toInt(), spent.toInt()),
                "energy.csv row: $totalXp,$spent",
            )
        }
    }

    /** 리소스 CSV → 행 리스트(헤더 스킵). 파일 없으면 빈 리스트 — presence 테스트가 잡는다. */
    private fun rows(name: String): List<List<String>> {
        val stream = javaClass.classLoader.getResourceAsStream("balance/$name") ?: return emptyList()
        return stream.bufferedReader().readLines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> line.split(",").map { it.trim() } }
    }

    private operator fun List<String>.component4(): String = this[3]

    private operator fun List<String>.component5(): String = this[4]
}
