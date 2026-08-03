package com.nexus.app.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.nexus.core.Ambience
import com.nexus.core.AmbienceSlot
import com.nexus.core.Season

/**
 * 시간대·계절 앰비언스 색 (#115, E16-19) — 홈 배경의 **아주 옅은** 워시.
 *
 * ## 왜 옅어야 하는가
 *
 * 이건 배경이지 콘텐츠가 아니다. 진하게 넣으면 그 위 본문·게이지·캐릭터 대비가 흔들리고,
 * 라이트/다크 × 시간대 4 × 계절 4 = **32조합**의 대비를 각각 지켜야 한다. 알파는 매 조합을 손으로
 * 검증할 여력이 없으므로 표면색에 얹는 **알파를 상한으로 묶어** 대비가 애초에 크게 흔들릴 수 없게
 * 만든다(`AmbienceContrastTest`가 그 상한을 고정).
 *
 * ## 왜 색을 표면 위에 얹는가
 *
 * 시간대 색을 그대로 배경으로 쓰면 다크 테마에서 뒤집힌다 — #268에서 shimmer 하이라이트가 정확히
 * 그렇게 깨졌다(라이트에선 밝고 다크에선 더 어두웠다). 여기서는 스킴의 표면색 위에 틴트를 알파로
 * 합성해, 두 스킴 모두에서 "표면에서 살짝 기운 색"이 되게 한다.
 */
object AmbienceColors {

    /** 시간대 틴트 — 색상만 정하고 세기는 [WASH_ALPHA]가 정한다. */
    fun tint(slot: AmbienceSlot): Color = when (slot) {
        AmbienceSlot.MORNING -> MorningTint
        AmbienceSlot.DAY -> DayTint
        AmbienceSlot.EVENING -> EveningTint
        AmbienceSlot.NIGHT -> NightTint
    }

    /** 계절 틴트 — 시간대 위에 더 옅게 얹혀 "같은 저녁이라도 겨울 저녁"이 되게 한다. */
    fun seasonTint(season: Season): Color = when (season) {
        Season.SPRING -> SpringTint
        Season.SUMMER -> SummerTint
        Season.AUTUMN -> AutumnTint
        Season.WINTER -> WinterTint
    }

    /**
     * 표면 위에 얹은 최종 배경색.
     *
     * 순수 함수라 대비 테스트가 스킴별로 값을 직접 검사할 수 있다 — 컴포즈 배경은 픽셀로도
     * 시맨틱으로도 관측할 수 없다는 걸 #338에서 확인했다.
     */
    fun wash(surface: Color, slot: Color, season: Color): Color = season.copy(alpha = SEASON_ALPHA)
        .compositeOver(slot.copy(alpha = WASH_ALPHA).compositeOver(surface))

    /**
     * 아침 — 노란 금빛.
     *
     * 저녁과 **색상을 벌려야** 한다. 처음엔 둘 다 주황이라 실기기에서 페이지 배경 RGB가 5 차이밖에
     * 안 났다(#115 실측). 세기를 올리면 대비가 흔들리므로, 구분은 채도·명도가 아니라 **색상**으로 낸다.
     */
    private val MorningTint = Color(0xFFFFD84D)

    /** 낮 — 거의 무채. 가장 오래 머무는 시간이라 조용해야 한다. */
    private val DayTint = Color(0xFFFFF3E0)

    /** 저녁 — 붉은 노을. 아침(노랑)과 색상을 벌린 쪽. 저녁 일지가 열리는 18시에 같이 바뀐다. */
    private val EveningTint = Color(0xFFFF6B4D)

    /** 밤 — 유일한 한색 계열이라 낮과 확실히 구분된다. */
    private val NightTint = Color(0xFF5470D6)

    private val SpringTint = Color(0xFF9FD08C)
    private val SummerTint = Color(0xFF6FC7B6)
    private val AutumnTint = Color(0xFFE0A05C)
    private val WinterTint = Color(0xFF9FB8D8)

    /** 시간대 워시 상한 — 이 값을 넘기면 본문 대비가 흔들린다. */
    const val WASH_ALPHA = 0.10f

    /** 계절은 시간대보다 더 약하게 — 계절이 시간대를 덮으면 하루 리듬이 안 읽힌다. */
    const val SEASON_ALPHA = 0.05f
}

/**
 * 홈 배경에 앰비언스를 얹는다 (#115).
 *
 * 위→아래 그라디언트인 이유: 균일하게 칠하면 색 필터를 씌운 것처럼 보이고, 위쪽만 물들이면
 * 하늘빛이 든 것처럼 읽힌다. 하단은 표면색 그대로라 내비게이션 바와 이어진다.
 */
@Composable
fun Modifier.ambienceBackground(hour: Int, month: Int, surface: Color): Modifier {
    val top = AmbienceColors.wash(
        surface = surface,
        slot = AmbienceColors.tint(Ambience.slotAt(hour)),
        season = AmbienceColors.seasonTint(Ambience.seasonOf(month)),
    )
    return background(Brush.verticalGradient(listOf(top, surface)))
}
