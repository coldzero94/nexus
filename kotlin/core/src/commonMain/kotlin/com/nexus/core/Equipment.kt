package com.nexus.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 장착 슬롯 (#37, E5-4) — 2슬롯. **선언 순서 = z-순서**(뒤→앞): ACCESSORY가 아래, HEAD가 위에
 * 그려진다. 슬롯 추가 시 원하는 z-위치에 상수를 넣으면 렌더가 그대로 따른다.
 */
enum class EquipSlot { ACCESSORY, HEAD }

/**
 * 장비 1종 (#37) — [layerState]는 캐릭터 컴포저의 레이어 상태 키(에셋 규약
 * [CharacterAssetConvention.frameName], frames 1장). 카탈로그는 앱 에셋(equipment.json)에서 로드.
 */
@Serializable
data class EquipItem(val id: String, val slot: EquipSlot, val layerState: String, val name: String)

/** 장비 카탈로그 (#37) — 앱 번들 저작물. */
@Serializable
data class EquipCatalog(val version: String, val items: List<EquipItem>)

/**
 * 로드아웃 (#37) — 슬롯별 장착 아이템 id. 순수·불변 상태.
 * **캐릭터는 퇴행하지 않는다**(제품 불변식): 장착/해제는 표시만 바꾸고 성장치·컨디션에 영향이 없다.
 */
@Serializable
data class Loadout(val equipped: Map<EquipSlot, String> = emptyMap()) {

    fun equip(item: EquipItem): Loadout = copy(equipped = equipped + (item.slot to item.id))

    fun unequip(slot: EquipSlot): Loadout = copy(equipped = equipped - slot)

    fun equippedId(slot: EquipSlot): String? = equipped[slot]

    /**
     * [baseState](본체)를 맨 아래에 두고 장착 레이어를 z-순서([EquipSlot] 선언 순서)대로 쌓은
     * 상태 키 목록. 카탈로그에 없는 id는 건너뛴다(에셋 삭제·구버전 저장 안전). 렌더러는 이 순서로
     * 아래→위 합성한다([com.nexus.core] 계약 — 앱·위젯 동일).
     */
    fun renderLayers(baseState: String, catalog: List<EquipItem>): List<String> {
        val byId = catalog.associateBy { it.id }
        val equipLayers = EquipSlot.entries.mapNotNull { slot -> equipped[slot]?.let { byId[it]?.layerState } }
        return listOf(baseState) + equipLayers
    }
}

/** 장비 카탈로그 파서 (#37) — 배지(#69)와 같은 fail-fast 계약. layerState는 에셋 규약을 따라야 한다. */
object EquipCatalogReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonText: String): EquipCatalog {
        val catalog = json.decodeFromString(EquipCatalog.serializer(), jsonText)
        val seenIds = HashSet<String>()
        catalog.items.forEach { item ->
            require(item.id.isNotBlank()) { "equip id must not be blank" }
            require(seenIds.add(item.id)) { "duplicate equip id '${item.id}'" }
            // layerState가 에셋 규약을 어기면 로드 시점에 실패시킨다 — 런타임 조용한 미렌더 방지.
            require(CharacterAssetConvention.isValidState(item.layerState)) {
                "equip '${item.id}': invalid layerState '${item.layerState}'"
            }
        }
        return catalog
    }
}
