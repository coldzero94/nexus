package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 산식 버전별 동결 벡터 (#243, E15-15) — **출하된 `FORMULA_VERSION`의 출력을 못 바꾸게 잠근다.**
 *
 * `balance` 디렉터리의 CSV(#185)는 밸런스 튜닝을 위해 **살아있는** 표라 "표 수정 = CSV 수정"이 장려된다.
 * 그런데 알파 중 엔진과 CSV를 함께 고치면서 버전을 안 올리면, 이미 테스터 원장에 쌓인 v1 이벤트의
 * **의미가 조용히 달라진다** — S9 서버 임포트가 정직한 사용자를 오검증하게 된다(BACKEND §1: 원장의
 * 최대 자산은 "각 이벤트를 당시 산식으로 재검산할 수 있다"는 것).
 *
 * 그래서 `balance/frozen/v{N}/`은 **불변 픽스처**다: 엔진 출력이 달라지면 이 테스트가 실패하고,
 * 해결책은 파일 수정이 아니라 **[XpEngine.FORMULA_VERSION] bump + 새 v{N+1} 디렉터리 추가**다.
 * 출하된 버전의 출력은 소급 수정이 불가능하므로, 출하 중인 지금 동결하는 것이 가장 싸다.
 */
class FrozenFormulaVectorTest {
    private val currentVersion = XpEngine.FORMULA_VERSION
    private val tables = listOf("daily_xp", "level_curve", "steps", "condition", "energy")

    @Test
    fun currentVersion_hasFrozenVectors() {
        // 새 버전을 올렸는데 동결 파일을 안 만들면 여기서 잡힌다(출하 전 강제)
        tables.forEach { table ->
            assertTrue(
                rows(currentVersion, table).isNotEmpty(),
                "v$currentVersion/$table.csv 없음 — FORMULA_VERSION을 올렸다면 동결 벡터를 함께 추가하세요",
            )
        }
    }

    @Test
    fun allShippedVersions_remainFrozen() {
        // 과거 버전 파일 삭제 금지 — 원장에 남은 옛 이벤트의 재검산 근거다
        (1..currentVersion).forEach { version ->
            tables.forEach { table ->
                assertTrue(
                    rows(version, table).isNotEmpty(),
                    "v$version/$table.csv 없음 — 출하된 버전의 동결 벡터는 삭제할 수 없습니다",
                )
            }
        }
    }

    @Test
    fun dailyXp_matchesFrozen() {
        rows(currentVersion, "daily_xp").forEach { (base, coef, trust, balance, expected) ->
            assertEquals(
                expected.toInt(),
                XpEngine.dailyXp(base.toDouble(), coef.toDouble(), trust.toDouble(), balance.toDouble()),
                failMessage("daily_xp", "$base,$coef,$trust,$balance"),
            )
        }
    }

    @Test
    fun levelCurve_matchesFrozen() {
        rows(currentVersion, "level_curve").forEach { (level, xp) ->
            assertEquals(
                xp.toInt(),
                LevelCurve.xpForLevel(level.toInt()),
                failMessage("level_curve", "level=$level"),
            )
        }
    }

    @Test
    fun steps_matchesFrozen() {
        rows(currentVersion, "steps").forEach { (steps, expected) ->
            assertEquals(
                expected.toDouble(),
                StepConversion.walkingBase(steps.toLong()),
                TOLERANCE,
                failMessage("steps", "steps=$steps"),
            )
        }
    }

    @Test
    fun condition_matchesFrozen() {
        rows(currentVersion, "condition").forEach { (current, points, restMode, restedYesterday, expected) ->
            assertEquals(
                expected.toDouble(),
                ConditionEngine.nextDay(
                    current.toDouble(),
                    points.toDouble(),
                    restMode = restMode.toBooleanStrict(),
                    restedYesterday = restedYesterday.toBooleanStrict(),
                ),
                TOLERANCE,
                failMessage("condition", "$current,$points,$restMode,$restedYesterday"),
            )
        }
    }

    @Test
    fun energy_matchesFrozen() {
        rows(currentVersion, "energy").forEach { (totalXp, spent, expected) ->
            assertEquals(
                expected.toInt(),
                EnergyEngine.balance(totalXp.toInt(), spent.toInt()),
                failMessage("energy", "$totalXp,$spent"),
            )
        }
    }

    /** 실패 시 무엇을 해야 하는지 오류 메시지가 직접 알려준다 — CSV를 고치려는 반사를 막는다. */
    private fun failMessage(table: String, row: String): String = "v$currentVersion/$table.csv 불일치 (행: $row)\n" +
        "  이 파일을 고치지 마세요. 출하된 산식의 출력을 바꾸려면 XpEngine.FORMULA_VERSION을 올리고\n" +
        "  balance/frozen/v" + (currentVersion + 1) + " 디렉터리를 새로 만드세요 (docs/MVP.md §5)."

    /** 동결 리소스 → 행 리스트(주석·헤더 스킵). 파일 없으면 빈 리스트 — presence 테스트가 잡는다. */
    private fun rows(version: Int, table: String): List<List<String>> {
        val stream = javaClass.classLoader.getResourceAsStream("balance/frozen/v$version/$table.csv")
            ?: return emptyList()
        return stream.bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .drop(1)
            .map { line -> line.split(",").map { it.trim() } }
    }

    private operator fun List<String>.component4(): String = this[3]

    private operator fun List<String>.component5(): String = this[4]

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
