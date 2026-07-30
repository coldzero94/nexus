package com.nexus.core

/**
 * 배지 글리프 리소스 규약 (#266, E16-16) — `CharacterAssetConvention`(E4-1)의 계승.
 *
 * ## 왜 규약인가
 *
 * 배지 표는 JSON이고 배지 추가는 **JSON만 고치는 것**이 #69의 계약이다. 그런데 아이콘을 정적
 * `R.drawable` 참조로 붙이면 배지를 추가할 때마다 코드를 고쳐야 해서 그 계약이 깨진다. 그래서
 * 이름 규약 + `getIdentifier` 동적 조회로 간다 — 캐릭터 프레임이 이미 같은 이유로 그렇게 한다.
 *
 * ## 표는 접미사만 말한다
 *
 * `badges.json`의 `icon`은 `"first_step"`처럼 **접미사**다. 리소스 이름 전체(`badge_first_step`)를
 * 적게 하면 리소스 접두어 규칙이 바뀔 때 표를 손대야 하고, 표가 리소스 네이밍을 알게 된다.
 */
object BadgeAssetConvention {
    /** 리소스 접두어 — 드로어블 이름은 `badge_<icon>`이다. */
    const val PREFIX = "badge_"

    /** 아이콘 없는 배지·조회 실패 시 쓰는 기본 글리프의 접미사. */
    const val FALLBACK_ICON = "default"

    /** 접미사 이름 규칙: 소문자·숫자·언더스코어 — 리소스 이름에 그대로 들어간다. */
    private val ICON_NAME = Regex("[a-z][a-z0-9_]*")

    /** 규약에 맞는 접미사인가. 안 맞으면 리소스 조회에 넣지 않는다(오타가 잘못된 조회로 안 가게). */
    fun isValidIcon(icon: String): Boolean = ICON_NAME.matches(icon)

    /**
     * 접미사 → 드로어블 리소스 이름.
     *
     * @param icon `Badge.icon`. null이거나 규약 위반이면 [FALLBACK_ICON]을 쓴다 — 표의 오타가
     *   크래시나 빈 자리가 아니라 **기본 글리프**로 끝나게 한다(배지는 부가 정보다).
     */
    fun iconName(icon: String?): String {
        val suffix = icon?.takeIf { isValidIcon(it) } ?: FALLBACK_ICON
        return "$PREFIX$suffix"
    }
}
