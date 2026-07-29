package com.nexus.core

/**
 * 능력치 상승분 (#219, E14-9) — 레벨업 축하가 **"무엇이 올랐는지"**를 말하게 한다.
 *
 * 지금 축하는 '레벨 N 달성' 텍스트뿐이라 "데이터가 캐릭터에 새겨진다"는 핵심 약속의 증거가 빠져
 * 있다(BENCHMARK §2 '숫자만 오르는 성장 금지'). 어느 스탯이 얼마나 올랐는지 보여야 그 약속이
 * 픽셀로 증명된다.
 */
object StatDelta {
    /**
     * 직전 벡터 대비 **오른 스탯만** — 잠긴 스탯과 변화 없음·하락은 제외한다.
     *
     * 하락을 빼는 이유는 두 가지다. 표시 능력치는 28일 창 계산이라 세션이 창을 빠져나가면 내려갈 수
     * 있는데, 그건 사용자가 뭘 잘못해서가 아니다. 그리고 축하 카드에 마이너스를 적는 건 "캐릭터는
     * 퇴행하지 않는다"는 약속과 정면으로 어긋난다 — 축하하는 자리에서 손실을 말하지 않는다.
     *
     * 반환 순서는 [Stat] 선언 순서를 따른다(표시 일관성 — 매번 순서가 바뀌면 읽기 어렵다).
     *
     * @param previous 직전에 보여준 능력치 벡터. 비어 있으면(최초) 전부 상승으로 보지 않고 빈 결과를
     *   낸다 — 기준점이 없는 첫 방문에 "+12 지구력"을 띄우면 하지도 않은 성장을 축하하는 셈이다.
     */
    fun risen(previous: Map<Stat, Int>, current: Map<Stat, Int>): Map<Stat, Int> {
        if (previous.isEmpty()) return emptyMap()
        return Stat.entries
            .filterNot { it.locked }
            .mapNotNull { stat ->
                val delta = (current[stat] ?: 0) - (previous[stat] ?: 0)
                if (delta > 0) stat to delta else null
            }
            .toMap()
    }
}
