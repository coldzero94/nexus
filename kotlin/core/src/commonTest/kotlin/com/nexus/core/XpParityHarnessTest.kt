package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 케이스 테이블 파리티 하네스 (#59, E3-13). 스프레드시트(#2)와 **같은 표 형식(CSV)**으로 엔진을 대조.
 * 산식이 스프레드시트에서 벗어나면(또는 그 반대) 이 테스트가 잡는다.
 * CSV는 스프레드시트 export로 교체 가능 — 지금은 인라인 텍스트(파일 IO 없는 KMP commonTest 유지).
 */
class XpParityHarnessTest {

    // columns: base,personalCoef,trust,balance,expectedDailyXp
    private val dailyXpCsv = """
        base,personalCoef,trust,balance,expected
        100,1.0,1.0,1.0,100
        200,1.0,1.0,1.0,200
        300,1.0,1.0,1.0,250
        400,1.0,1.0,1.0,300
        500,1.0,1.0,1.0,300
        100,1.0,0.0,1.0,0
        200,1.0,1.0,1.15,215
        100,2.0,1.0,1.0,150
        100,0.5,1.0,1.0,80
        120,1.0,0.85,1.0,102
    """.trimIndent()

    @Test
    fun dailyXp_matchesSpreadsheetTable() {
        parse(dailyXpCsv).forEach { row ->
            val (base, coef, trust, balance, expected) = row
            assertEquals(
                expected.toInt(),
                XpEngine.dailyXp(base.toDouble(), coef.toDouble(), trust.toDouble(), balance.toDouble()),
                "row: ${row.joinToString(",")}",
            )
        }
    }

    // columns: level,cumulativeXpForLevel
    private val levelCsv = """
        level,xp
        1,100
        2,283
        4,800
        5,1118
        9,2700
    """.trimIndent()

    @Test
    fun levelCurve_matchesSpreadsheetTable() {
        parse(levelCsv).forEach { (level, xp) ->
            assertEquals(xp.toInt(), LevelCurve.xpForLevel(level.toInt()), "level $level")
        }
    }

    /** 헤더 1줄 스킵 + 공백 줄 제거 → 각 행을 컬럼 리스트로. */
    private fun parse(csv: String): List<List<String>> = csv.lineSequence()
        .drop(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line -> line.split(",").map { it.trim() } }
        .toList()
}
