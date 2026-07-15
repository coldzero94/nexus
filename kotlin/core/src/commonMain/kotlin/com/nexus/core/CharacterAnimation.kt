package com.nexus.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 상태 하나의 애니메이션 메타 (#25, E4-1). 프레임 에셋은 [CharacterAssetConvention.frameName] 규약. */
@Serializable
data class AnimationState(
    val frames: Int,
    @SerialName("frameDurationMs") val frameDurationMs: Long,
    val loop: Boolean = true,
)

/** 캐릭터 애니메이션 세트 — 앱 assets의 JSON에서 로드. 상태 추가 = JSON+드로어블 추가(코드 무수정). */
@Serializable
data class CharacterAnimationSet(val version: Int, val defaultState: String, val states: Map<String, AnimationState>) {
    /** 알 수 없는 상태는 기본 상태로 — 표(JSON)와 코드(기분 enum 등)의 버전 차이를 흡수. */
    fun stateOrDefault(name: String): AnimationState = states[name] ?: states.getValue(defaultState)
}

/**
 * 에셋 이름 규약 + 메타 파서 (#25, E4-1). 규약: `character_{state}_{frame}` —
 * 예) character_idle_0. 컴포저(E4-2)는 이 이름으로 드로어블을 조회하므로
 * **에셋 추가 = 드로어블 파일 + JSON 항목 추가**로 끝난다(코드 수정 없음).
 */
object CharacterAssetConvention {

    /** 상태 이름 규칙: 소문자·숫자·언더스코어 — 리소스 이름에 그대로 들어간다. */
    private val STATE_NAME = Regex("[a-z][a-z0-9_]*")

    private val json = Json { ignoreUnknownKeys = true } // 메타 확장(후속 필드)에 관대

    fun frameName(state: String, frame: Int): String {
        require(frame >= 0) { "frame must be >= 0" }
        require(STATE_NAME.matches(state)) { "invalid state name: $state" }
        return "character_${state}_$frame"
    }

    /**
     * JSON 메타 파싱 + 검증. 잘못된 표는 여기서 즉시 실패한다(런타임 조용한 무애니메이션 방지) —
     * 실패는 [kotlinx.serialization.SerializationException] 또는 [IllegalArgumentException].
     */
    fun parse(jsonText: String): CharacterAnimationSet {
        val set = json.decodeFromString(CharacterAnimationSet.serializer(), jsonText)
        require(set.states.isNotEmpty()) { "states must not be empty" }
        require(set.defaultState in set.states) { "defaultState '${set.defaultState}' not in states" }
        set.states.forEach { (name, state) ->
            require(STATE_NAME.matches(name)) { "invalid state name: $name" }
            require(state.frames >= 1) { "state '$name': frames must be >= 1" }
            require(state.frameDurationMs > 0) { "state '$name': frameDurationMs must be > 0" }
        }
        return set
    }
}
