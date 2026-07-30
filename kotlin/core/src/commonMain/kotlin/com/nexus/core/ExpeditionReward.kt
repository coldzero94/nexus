package com.nexus.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 원정 보상 한 건 (#68, E5-7).
 *
 * @property id 안정 식별자 — 표를 고쳐도 이미 받은 보상을 추적할 수 있게(후속 도감 대비).
 * @property title 개봉 카드의 한 줄. 캐릭터가 **가져온 것**을 말한다.
 * @property body 이야기 한 조각. 동기화 지연을 "모험 중"으로 흡수하는 게 이 텍스트의 일이다.
 * @property weight 뽑기 가중치(양수). 밸런스는 #77(P-5)이 이 표를 입력으로 잡는다.
 */
@Serializable
data class ExpeditionReward(val id: String, val title: String, val body: String, val weight: Int = 1)

/** 원정 보상 표 — 앱 assets JSON에서 로드. 보상 추가·수정 = JSON만(코드 무수정). */
@Serializable
data class ExpeditionRewardTable(val version: String, val rewards: List<ExpeditionReward>)

/**
 * 원정 보상 추첨 (#68, E5-7) — **순수 함수**. 시계도 난수원도 갖지 않는다.
 *
 * ## 왜 XP가 아닌가
 *
 * 보상은 **이야기 조각**이지 성장치가 아니다. XP를 주면 원정이 활동과 무관한 XP 경로가 되어
 * "움직이면 자란다"는 이 앱의 전제가 흐려지고, 원장(crown jewel)에 활동에서 파생되지 않은 지급이
 * 섞인다. MVP §5의 XP는 활동 파생이고, 원정의 목적은 `docs/MVP.md`가 적은 대로 **하루 2~3회
 * 자연 재방문과 동기화 지연 흡수**다 — 그건 연출이 하는 일이지 숫자가 하는 일이 아니다.
 *
 * 환금 가능한 보상이 아니라는 점도 여기서 자동으로 지켜진다(제품 불변식 ①).
 *
 * ## 왜 난수를 주입받는가
 *
 * core는 `Math.random`을 쓰지 않는다 — 같은 입력에 같은 출력이어야 케이스 테이블 테스트가 서고,
 * 재시작 정합을 검사할 수 있다([ExpeditionEngine]과 같은 규율). 호출측이 seed를 넘긴다.
 */
object ExpeditionRewardPicker {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 표 파싱 + 검증. 잘못된 표는 여기서 즉시 실패한다 — 런타임에 조용히 보상 없는 개봉이 되면
     * 원인을 찾기 어렵다([CharacterAssetConvention.parse]와 같은 태도).
     *
     * @throws IllegalArgumentException 보상이 없거나 가중치가 0 이하일 때.
     */
    fun parse(jsonText: String): ExpeditionRewardTable {
        val table = json.decodeFromString(ExpeditionRewardTable.serializer(), jsonText)
        require(table.rewards.isNotEmpty()) { "expedition rewards must not be empty" }
        table.rewards.forEach { reward ->
            require(reward.weight > 0) { "reward '${reward.id}' weight must be > 0" }
            require(reward.id.isNotBlank()) { "reward id must not be blank" }
        }
        require(table.rewards.distinctBy { it.id }.size == table.rewards.size) { "duplicate reward id" }
        return table
    }

    /**
     * 가중치 추첨.
     *
     * @param seed 호출측이 주입하는 난수 원본(예: 개봉 시각). 음수도 받는다 — 나머지 연산 전에
     *   절댓값을 취한다(음수 seed가 항상 첫 보상을 뽑는 편향을 막는다).
     * @return 뽑힌 보상. 표가 비어 있을 수 없으므로([parse] 검증) 항상 값이 있다.
     */
    fun pick(table: ExpeditionRewardTable, seed: Long): ExpeditionReward {
        val total = table.rewards.sumOf { it.weight }
        // seed % total은 음수 seed에서 음수가 된다 — 절댓값을 먼저 취해 분포가 한쪽으로 쏠리지 않게
        var roll = (if (seed < 0) -seed else seed) % total
        table.rewards.forEach { reward ->
            roll -= reward.weight
            if (roll < 0) return reward
        }
        // 가중치 합 계산과 순회가 어긋날 수 없지만, 표가 비지 않음이 보장되므로 마지막을 돌려준다
        return table.rewards.last()
    }
}
