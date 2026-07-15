package com.nexus.core

/**
 * 오늘 XP 근거 한 줄 — 세션 하나가 XP에 어떻게 반영됐는지 (#24, E3-12).
 * [countsForXp]=false인 줄도 목록엔 남긴다 — "왜 안 쳐줬는지"가 투명성의 핵심(트랭글 반면교사).
 */
data class XpLine(
    val type: ActivityType?,
    val minutes: Int,
    val basePoints: Int,
    val tier: TrustTier,
    val countsForXp: Boolean,
)

/** 하루 XP 환산 분해 — 세션 줄 + 상한 적용 내역. */
data class DayXpExplanation(
    val lines: List<XpLine>,
    /** 상한 적용 전 신뢰 반영 합 (개인 계수 기준). */
    val rawPoints: Int,
    /** 일일 상한 적용 후 — 실제 오늘 XP. */
    val cappedXp: Int,
    /** raw가 200 니를 넘어 초과분 절반 체감이 적용됐는가. */
    val kneeApplied: Boolean,
    /** 니 체감으로 깎인 포인트(하드캡 클리핑 제외) — UI가 감소 사유를 분리 표기(#24 리뷰). */
    val kneeReducedPoints: Int,
    /** 하드캡 300 도달 여부. */
    val hardCapped: Boolean,
)

/**
 * XP 환산 설명 (#24): 특정 날짜의 세션들을 "왜 이 XP인가"로 분해한다.
 * 계산 규칙은 [GrowthCalculator]와 동일한 원천([XpEngine]·[TrustPolicy])을 쓰므로
 * 성장 탭 합계와 설명 화면 분해가 항상 일치한다 — 별도 산식 금지.
 */
object XpExplainer {

    fun explainDay(sessions: List<SessionInput>, epochDay: Long): DayXpExplanation {
        val lines = sessions.filter { it.epochDay == epochDay }.map { s ->
            val base = s.type?.let { XpEngine.baseScore(it, s.minutes) } ?: 0
            XpLine(
                type = s.type,
                minutes = s.minutes,
                basePoints = base,
                tier = s.tier,
                countsForXp = s.type != null && TrustPolicy.isXpEligible(s.tier),
            )
        }
        val raw = lines.filter { it.countsForXp }
            .sumOf { it.basePoints * it.tier.personalXpMultiplier }
        val softened = XpEngine.applyKnee(raw)
        val capped = XpEngine.applyDailyCap(raw)
        return DayXpExplanation(
            lines = lines,
            rawPoints = raw.toInt(),
            cappedXp = capped,
            kneeApplied = raw > XpEngine.DAILY_KNEE,
            kneeReducedPoints = (raw - softened).toInt(),
            hardCapped = capped >= XpEngine.DAILY_HARD_CAP.toInt(),
        )
    }
}
