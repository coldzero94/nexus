package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #266 — 배지 글리프 리소스 규약. 이 규약이 있어야 **배지 추가가 JSON만**이라는 #69 계약이 유지된다
 * (정적 `R.drawable` 참조면 배지마다 코드 수정이 필요하다).
 */
class BadgeAssetConventionTest {

    @Test
    fun `접미사에 접두어를 붙여 리소스 이름을 만든다`() {
        assertEquals("badge_first_step", BadgeAssetConvention.iconName("first_step"))
        assertEquals("badge_steps", BadgeAssetConvention.iconName("steps"))
    }

    @Test
    fun `아이콘이 없으면 기본 글리프 이름`() {
        // 조건만 적어 배지를 추가하는 경로를 막지 않는다 — 아이콘은 나중에 붙일 수 있다
        assertEquals("badge_default", BadgeAssetConvention.iconName(null))
    }

    @Test
    fun `규약 위반 접미사는 기본 글리프로 접힌다`() {
        // 표의 오타가 크래시나 빈 자리가 아니라 기본 글리프로 끝나야 한다(배지는 부가 정보)
        listOf("First_Step", "first step", "1step", "first-step", "", "../secret").forEach { bad ->
            assertEquals("badge_default", BadgeAssetConvention.iconName(bad), "'$bad'가 통과했다")
        }
    }

    @Test
    fun `접미사 규칙은 소문자 숫자 언더스코어만 허용한다`() {
        assertTrue(BadgeAssetConvention.isValidIcon("streak"))
        assertTrue(BadgeAssetConvention.isValidIcon("level_10"))
        assertTrue(BadgeAssetConvention.isValidIcon("a1"))
        assertFalse(BadgeAssetConvention.isValidIcon("Streak"))
        assertFalse(BadgeAssetConvention.isValidIcon("10_level"), "숫자로 시작하면 리소스 이름이 될 수 없다")
        assertFalse(BadgeAssetConvention.isValidIcon("streak!"))
    }

    @Test
    fun `기본 글리프 접미사 자체도 규약을 만족한다`() {
        // 폴백이 규약 위반이면 무한 폴백이 된다
        assertTrue(BadgeAssetConvention.isValidIcon(BadgeAssetConvention.FALLBACK_ICON))
    }

    @Test
    fun `배지 표를 아이콘 없이도 파싱할 수 있다`() {
        // icon은 옵션 필드 — 기존 표(아이콘 없음)가 그대로 로드돼야 한다
        val json = """
            {"version":"v1","badges":[
              {"id":"a","name":"A","description":"d","when":"level >= 1"}
            ]}
        """.trimIndent()

        val table = BadgeTableReader.parse(json)

        assertEquals(null, table.badges.single().icon)
    }

    @Test
    fun `아이콘이 있으면 파싱해 담는다`() {
        val json = """
            {"version":"v1","badges":[
              {"id":"a","name":"A","description":"d","when":"level >= 1","icon":"streak"}
            ]}
        """.trimIndent()

        assertEquals("streak", BadgeTableReader.parse(json).badges.single().icon)
    }
}
