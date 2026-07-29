package com.nexus.core

/** 하루의 시간대 (#220) — 대사 변주 입력. 경계는 아래 [GreetingSelector.timeOfDay]가 정한다. */
enum class TimeOfDay { MORNING, DAY, EVENING }

/**
 * 인사 대사 변주 (#220) — 상태 풀 위에 얹는 **맥락 레이어**.
 *
 * [None]이면 기존 상태별 대사(#29·#212)를 그대로 쓴다. 변주가 잡히면 그 풀에서 먼저 고른다.
 */
enum class GreetingVariant {
    /** 맥락 없음 — 기존 상태 대사. */
    None,

    /** 아침 인사. */
    Morning,

    /** 저녁 인사. */
    Evening,

    /** 오늘 첫 활동을 알아봄 — "오늘 벌써 움직였네". */
    FirstActivityToday,

    /** 하루 이틀 만의 재개 — 반가움(공백은 그리움이지 잘못이 아니다). */
    BackAfterShortGap,
}

/**
 * 인사 변주 선택 (#220, E14-10) — 말풍선이 **나의 맥락을 알아보게** 한다.
 *
 * 지금 말풍선은 상태 풀에서 무작위로 뽑을 뿐이라 '살아있는 동료'보다 마스코트에 가깝다. 아침 인사,
 * "오늘 첫 활동", "이틀 만이야 반가워" 같은 반응은 Finch류 애착·재방문의 방식이다.
 *
 * ## 3일+ 공백은 여기서 다루지 않는다
 *
 * 복귀 환영 씬([ReturnWelcomePolicy], #30)이 이미 전용 화면으로 맞이한다. 말풍선까지 같은 말을 하면
 * **한 번의 복귀를 두 번 축하**하는 셈이라, 경계를 [ReturnWelcomePolicy.WELCOME_GAP_DAYS] 미만으로
 * 잘라 겹치지 않게 한다.
 *
 * 무처벌 원칙: 공백을 지적하지 않는다. "이틀이나 안 왔네"가 아니라 "이틀 만이야, 반가워"다.
 */
object GreetingSelector {
    /** 아침의 시작(포함) — 이 시각부터 [TimeOfDay.MORNING]. */
    const val MORNING_START_HOUR = 5

    /** 낮의 시작(포함). */
    const val DAY_START_HOUR = 11

    /** 저녁의 시작(포함) — 다음 날 [MORNING_START_HOUR] 전까지 [TimeOfDay.EVENING]. */
    const val EVENING_START_HOUR = 18

    /**
     * 시:분의 '시' 하나로 시간대를 가른다. 범위 밖(0~4시 등 심야)은 저녁의 연장으로 본다 —
     * 새벽 3시에 "좋은 아침"은 어색하고, 그 시간대 전용 대사를 따로 두는 건 과설계다.
     */
    fun timeOfDay(hour: Int): TimeOfDay = when {
        hour in MORNING_START_HOUR until DAY_START_HOUR -> TimeOfDay.MORNING
        hour in DAY_START_HOUR until EVENING_START_HOUR -> TimeOfDay.DAY
        else -> TimeOfDay.EVENING
    }

    /**
     * 변주 선택 — 우선순위가 곧 "지금 말할 가치가 가장 큰 것"이다.
     *
     * 1. **오늘 첫 활동**: 방금 일어난 일이라 가장 신선하다. 시간대 인사보다 앞선다.
     * 2. **짧은 공백 뒤 재개**: 1~2일 만의 재방문. 3일+는 [ReturnWelcomePolicy]가 맡아 제외한다.
     * 3. **아침·저녁 인사**: 낮은 특별할 게 없어 변주를 두지 않는다(기존 상태 대사로 충분).
     *
     * @param todayActiveMin 오늘 활동 분. 0보다 크면 오늘 이미 움직인 것.
     * @param daysSinceActivity 마지막 활동 이후 경과일. 0 = 오늘도 움직였음.
     */
    fun select(hour: Int, todayActiveMin: Int, daysSinceActivity: Int): GreetingVariant = when {
        todayActiveMin > 0 -> GreetingVariant.FirstActivityToday

        daysSinceActivity in 1 until ReturnWelcomePolicy.WELCOME_GAP_DAYS -> GreetingVariant.BackAfterShortGap

        else -> when (timeOfDay(hour)) {
            TimeOfDay.MORNING -> GreetingVariant.Morning
            TimeOfDay.EVENING -> GreetingVariant.Evening
            TimeOfDay.DAY -> GreetingVariant.None
        }
    }
}
