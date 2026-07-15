package com.nexus.core

/**
 * 원장 표시 합산 (#162) — 원장은 세션 단위 **무상한 지급**을 박제하고(재검산 가능),
 * 표시 XP는 여기서 일 단위 상한(MVP §5)을 적용해 계산한다. 상한을 지급 시점에 박으면
 * 산식 버전이 바뀔 때 소급 재계산이 불가능해진다 — 상한은 언제나 합산 시점의 규칙.
 */
object LedgerMath {

    /**
     * 일자별 순 XP(지급−취소, [xpByDay]: epochDay → 합)를 일 상한 적용 후 총합.
     * 취소는 지급과 같은 날 키로 들어와야 정확히 상쇄된다(호출자 계약 — 엔티티가 지급일을 보존).
     * 음수 일합(과취소·방어)은 0으로 클램프 — 총합이 음수로 새지 않는다.
     */
    fun cappedTotalXp(xpByDay: Map<Long, Double>): Int =
        xpByDay.values.sumOf { dayXp -> XpEngine.applyDailyCap(dayXp.coerceAtLeast(0.0)) }
}
