package com.nexus.core

/**
 * 시간대 구간 (#115, E16-19) — 홈 배경 톤의 입력.
 *
 * 경계는 아침/저녁 카드(#35·#72)의 리듬과 맞춘다: 하루 2~3회 자연 재방문이 목표이고, 배경이
 * 그 방문마다 달라 보여야 "살아 있는 공간"이 된다. 구간을 더 잘게 쪼개면 차이를 못 느끼고,
 * 더 굵게 하면 하루 종일 같은 화면이 된다.
 */
enum class AmbienceSlot { MORNING, DAY, EVENING, NIGHT }

/** 계절 (#115) — 북반구 기준. 사용자 시간대의 월로 판정한다. */
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }

/**
 * 홈 앰비언스 판정 (#115, E16-19) — **순수 함수**. 시계도 시간대 DB도 갖지 않는다.
 *
 * 순수로 두는 이유는 [ReduceMotion]·[ExpeditionEngine]과 같다: 경계값(5시·11시·17시·21시,
 * 월 전환)이 정확히 어디서 넘어가는지를 케이스 표로 고정할 수 있어야 하고, 그게 안 되면
 * "저녁인데 낮 배경"을 실기기에서 그 시각에만 재현하며 쫓게 된다.
 */
object Ambience {

    /**
     * 시(0~23) → 구간.
     *
     * 경계는 **닫힌 앞·열린 뒤**다: 5시는 아침, 11시는 낮.
     *
     * 저녁 시작을 18시로 둔 건 **저녁 일지가 열리는 시각**(`EveningJournalStore.OPEN_HOUR`)과
     * 같기 때문이다. 둘이 어긋나면 "저녁 카드는 떴는데 배경은 낮"인 한 시간이 생겨, 화면이
     * 스스로와 모순되는 말을 한다.
     */
    fun slotAt(hour: Int): AmbienceSlot {
        require(hour in HOUR_RANGE) { "hour must be $HOUR_RANGE" }
        return when (hour) {
            in MORNING_START until DAY_START -> AmbienceSlot.MORNING
            in DAY_START until EVENING_START -> AmbienceSlot.DAY
            in EVENING_START until NIGHT_START -> AmbienceSlot.EVENING
            else -> AmbienceSlot.NIGHT
        }
    }

    /**
     * 월(1~12) → 계절. 3·4·5=봄, 6·7·8=여름, 9·10·11=가을, 12·1·2=겨울.
     *
     * 절기가 아니라 월로 나누는 이유: 사용자가 체감하는 계절은 절기보다 월에 가깝고, 절기는
     * 해마다 날짜가 달라 표로 고정할 수 없다.
     */
    fun seasonOf(month: Int): Season {
        require(month in MONTH_RANGE) { "month must be $MONTH_RANGE" }
        return when (month) {
            in SPRING_MONTHS -> Season.SPRING
            in SUMMER_MONTHS -> Season.SUMMER
            in AUTUMN_MONTHS -> Season.AUTUMN
            else -> Season.WINTER
        }
    }

    private val HOUR_RANGE = 0..23
    private val MONTH_RANGE = 1..12
    private val SPRING_MONTHS = 3..5
    private val SUMMER_MONTHS = 6..8
    private val AUTUMN_MONTHS = 9..11

    private const val MORNING_START = 5
    private const val DAY_START = 11
    private const val EVENING_START = 18
    private const val NIGHT_START = 21
}
