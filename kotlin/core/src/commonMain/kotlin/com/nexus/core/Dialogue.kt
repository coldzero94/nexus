package com.nexus.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 상태별 대사 풀 (#29, E4-5) — 앱 assets JSON에서 로드. 대사 추가·수정 = JSON만(코드 무수정).
 * 상태 키는 캐릭터 상태(idle·walk, 이후 기분 E4-4)와 같은 어휘를 쓴다.
 */
@Serializable
data class DialoguePool(val version: Int, val defaultState: String, val lines: Map<String, List<String>>) {
    fun linesOrDefault(state: String): List<String> = lines[state] ?: lines.getValue(defaultState)
}

/**
 * 대사 선택기 — **반복 회피**(Pokémon Sleep 이탈 원인 = 같은 반응 소진, MVP §1)가 계약.
 * 최근 노출 목록을 피해서 뽑고, 전부 최근이면 가장 오래된 것을 허용한다(빈 결과 없음).
 * 무작위성은 호출자가 주입([pick]의 randomIndex) — core는 플랫폼 난수를 갖지 않는다(테스트 결정성).
 */
object DialogueSelector {

    /** [recent]에 없는 후보 중 randomIndex(후보 수 미만)번째. 전부 최근이면 recent에서 가장 오래된 것. */
    fun pick(pool: List<String>, recent: List<String>, randomIndex: Int): String {
        require(pool.isNotEmpty()) { "pool must not be empty" }
        require(randomIndex >= 0) { "randomIndex must be >= 0" }
        val fresh = pool.filterNot { it in recent }
        if (fresh.isNotEmpty()) return fresh[randomIndex % fresh.size]
        // 풀 전체가 최근 노출 — 그중 가장 오래 전에 나온 대사로 (recent는 오래된 순)
        return recent.first { it in pool }
    }

    /** 노출 기록 갱신 — 최근 [capacity]개만 유지(오래된 순 유지). */
    fun remember(recent: List<String>, shown: String, capacity: Int): List<String> {
        require(capacity >= 1) { "capacity must be >= 1" }
        return (recent.filterNot { it == shown } + shown).takeLast(capacity)
    }
}

/** 대사 풀 파서 — 애니메이션 메타(#25)와 같은 fail-fast 계약. */
object DialogueTable {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String): DialoguePool {
        val pool = json.decodeFromString(DialoguePool.serializer(), jsonText)
        require(pool.lines.isNotEmpty()) { "lines must not be empty" }
        require(pool.defaultState in pool.lines) { "defaultState '${pool.defaultState}' not in lines" }
        pool.lines.forEach { (state, list) ->
            require(list.isNotEmpty()) { "state '$state': line list must not be empty" }
        }
        return pool
    }
}
