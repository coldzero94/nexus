package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CharacterAnimationTest {

    private val valid = """
        {
          "version": 1,
          "defaultState": "idle",
          "states": {
            "idle": { "frames": 2, "frameDurationMs": 600 },
            "happy": { "frames": 4, "frameDurationMs": 250, "loop": false }
          }
        }
    """.trimIndent()

    @Test
    fun parse_validMeta_readsStatesAndDefaults() {
        val set = CharacterAssetConvention.parse(valid)
        assertEquals(2, set.states.getValue("idle").frames)
        assertTrue(set.states.getValue("idle").loop) // loop 기본값 true
        assertEquals(false, set.states.getValue("happy").loop)
    }

    @Test
    fun frameName_followsConvention() {
        // 규약 character_{state}_{frame} — 컴포저(E4-2)의 드로어블 조회 키
        assertEquals("character_idle_0", CharacterAssetConvention.frameName("idle", 0))
        assertEquals("character_happy_3", CharacterAssetConvention.frameName("happy", 3))
    }

    @Test
    fun frameName_rejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { CharacterAssetConvention.frameName("Idle", 0) } // 대문자
        assertFailsWith<IllegalArgumentException> { CharacterAssetConvention.frameName("idle", -1) }
    }

    @Test
    fun unknownState_fallsBackToDefault() {
        // 표(JSON)와 코드(기분 enum)의 버전 차이 흡수 — 미지 상태는 기본 상태로
        val set = CharacterAssetConvention.parse(valid)
        assertEquals(set.states.getValue("idle"), set.stateOrDefault("brand_new_mood"))
    }

    @Test
    fun parse_rejectsBrokenTables() {
        // 잘못된 표는 로드 시점에 즉시 실패 — 조용한 무애니메이션 방지
        assertFailsWith<IllegalArgumentException> {
            CharacterAssetConvention.parse(
                """{"version":1,"defaultState":"walk","states":{"idle":{"frames":2,"frameDurationMs":600}}}""",
            )
        } // defaultState 미존재
        assertFailsWith<IllegalArgumentException> {
            CharacterAssetConvention.parse(
                """{"version":1,"defaultState":"idle","states":{"idle":{"frames":0,"frameDurationMs":600}}}""",
            )
        } // 프레임 0
        assertFailsWith<IllegalArgumentException> {
            CharacterAssetConvention.parse(
                """{"version":1,"defaultState":"idle","states":{"idle":{"frames":2,"frameDurationMs":0}}}""",
            )
        } // 지속시간 0
        assertFailsWith<IllegalArgumentException> {
            CharacterAssetConvention.parse("""{"version":1,"defaultState":"idle","states":{}}""")
        } // 빈 표
    }

    @Test
    fun parse_toleratesUnknownKeys() {
        // 후속 필드 확장(예: sound)이 구버전 앱을 깨지 않는다
        val meta =
            """{"version":2,"defaultState":"idle",""" +
                """"states":{"idle":{"frames":1,"frameDurationMs":500,"sound":"chirp"}}}"""
        val set = CharacterAssetConvention.parse(meta)
        assertEquals(1, set.states.getValue("idle").frames)
    }
}
