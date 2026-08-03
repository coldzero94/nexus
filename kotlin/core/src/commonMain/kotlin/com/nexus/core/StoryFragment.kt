package com.nexus.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 이야기 조각 하나 (#112, E5-14).
 *
 * @property id 안정 식별자 — 표를 고쳐도 이미 모은 조각을 추적할 수 있게(도감 영속의 키).
 * @property title 도감 목록에 보이는 한 줄.
 * @property body 조각 본문. 세계관을 조금씩 흘리는 게 이 텍스트의 일이다.
 * @property weight 뽑기 가중치(양수). 낮을수록 희귀하다.
 */
@Serializable
data class StoryFragment(val id: String, val title: String, val body: String, val weight: Int = 1)

/** 조각 표 — 앱 assets JSON. 조각 추가·수정 = JSON만(코드 무수정). */
@Serializable
data class StoryFragmentTable(val version: String, val fragments: List<StoryFragment>)

/**
 * 이야기 조각 드롭 (#112, E5-14) — 운동 세션에 붙는 확률 보상.
 *
 * ## 왜 세션 id의 순수 함수인가
 *
 * 동기화 워커는 **15분마다 최근 7일 세션을 다시 읽는다**(홈 로드는 28일). 드롭을 그때마다 새로
 * 굴리면 같은 운동 하나가 하루에 수십 개의 조각을 뱉는다. "이미 굴린 세션" 목록을 따로 저장해
 * 막을 수도 있지만, 그러면 세션 수만큼 자라는 저장소가 하나 더 생기고 원장·수집 목록과 함께
 * 세 곳이 어긋날 수 있게 된다.
 *
 * 대신 **결과를 세션 id에서 결정론적으로 유도한다.** 같은 세션은 몇 번을 읽어도 같은 답을 주므로
 * 멱등성이 계산 자체에 들어 있다 — 원장이 `idempotencyKey`로 지급 중복을 막는 것과 같은 규율이고,
 * 저장할 것은 "모은 조각 집합"뿐이다(집합이라 두 번 넣어도 같다).
 *
 * ## 왜 XP가 아닌가
 *
 * 조각은 **이야기**지 성장치가 아니다. XP를 주면 활동에서 파생되지 않은 지급이 원장에 섞이고,
 * 확률 요소가 성장 곡선에 들어온다([ExpeditionReward]와 같은 판단).
 */
object StoryDropPicker {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 표 파싱 + 검증. 잘못된 표는 즉시 실패한다 — 런타임에 조용히 "아무것도 안 나오는 드롭"이 되면
     * 확률이 낮아서인지 표가 깨져서인지 구분할 수 없다.
     */
    fun parse(jsonText: String): StoryFragmentTable {
        val table = json.decodeFromString(StoryFragmentTable.serializer(), jsonText)
        require(table.fragments.isNotEmpty()) { "story fragments must not be empty" }
        table.fragments.forEach {
            require(it.id.isNotBlank()) { "fragment id must not be blank" }
            require(it.weight > 0) { "fragment '${it.id}' weight must be > 0" }
        }
        require(table.fragments.distinctBy { it.id }.size == table.fragments.size) { "duplicate fragment id" }
        return table
    }

    /**
     * 세션 하나의 드롭 결과.
     *
     * @param sessionKey 세션의 멱등성 키(= Health Connect 레코드 id). 이 값이 결과를 결정한다.
     * @param table 조각 표.
     * @param chancePercent 드롭 확률(0~100). 0이면 절대, 100이면 항상.
     * @return 뽑힌 조각, 또는 드롭 실패 시 null.
     */
    fun drop(sessionKey: String, table: StoryFragmentTable, chancePercent: Int): StoryFragment? {
        require(chancePercent in 0..PERCENT_MAX) { "chancePercent must be 0..$PERCENT_MAX" }
        val hash = stableHash(sessionKey)
        // 확률 판정과 조각 선택에 **서로 다른 자리**를 쓴다 — 같은 값을 쓰면 드롭된 세션의
        // 해시가 항상 낮은 구간이라 가중치 낮은 조각만 계속 나온다.
        if (hash % PERCENT_MAX >= chancePercent) return null
        val total = table.fragments.sumOf { it.weight }
        var roll = ((hash / PERCENT_MAX) % total).toInt()
        table.fragments.forEach { fragment ->
            roll -= fragment.weight
            if (roll < 0) return fragment
        }
        return table.fragments.last()
    }

    /**
     * 안정 해시 — 플랫폼·실행에 무관하게 같은 문자열에 같은 값.
     *
     * `String.hashCode()`를 쓰지 않는 이유: JVM 규격상 안정적이지만 **KMP 전체에 대한 보장은 아니고**,
     * 이 값이 바뀌면 사용자가 이미 모은 조각과 앞으로 나올 조각이 통째로 달라진다. 직접 정의해 고정한다.
     */
    private fun stableHash(key: String): Long {
        var hash = FNV_OFFSET
        key.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and BYTE_MASK)
            hash *= FNV_PRIME
        }
        return hash and Long.MAX_VALUE
    }

    private const val PERCENT_MAX = 100L
    private const val FNV_OFFSET = -3_750_763_034_362_895_579L // 14695981039346656037 as signed
    private const val FNV_PRIME = 1_099_511_628_211L
    private const val BYTE_MASK = 0xFFL
}
