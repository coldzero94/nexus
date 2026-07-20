package com.nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 로드아웃 순수 로직 고정 (#37, E5-4) — 장착이 렌더 레이어로 반영되는 계약. */
class EquipmentTest {

    private val hat = EquipItem("straw_hat", EquipSlot.HEAD, "hat_straw", "밀짚모자")
    private val hat2 = EquipItem("cap", EquipSlot.HEAD, "hat_cap", "야구모자")
    private val scarf = EquipItem("scarf", EquipSlot.ACCESSORY, "scarf_red", "빨간 목도리")
    private val catalog = listOf(hat, hat2, scarf)

    @Test
    fun equipReflectsInRenderLayers() {
        val layers = Loadout().equip(hat).renderLayers("idle", catalog)
        assertEquals(listOf("idle", "hat_straw"), layers)
    }

    @Test
    fun layersFollowSlotZOrder() {
        // ACCESSORY(아래) → HEAD(위): 본체 다음 ACCESSORY, 그 위 HEAD
        val layers = Loadout().equip(hat).equip(scarf).renderLayers("walk", catalog)
        assertEquals(listOf("walk", "scarf_red", "hat_straw"), layers)
    }

    @Test
    fun equipReplacesSameSlot() {
        val loadout = Loadout().equip(hat).equip(hat2)
        assertEquals("cap", loadout.equippedId(EquipSlot.HEAD))
        assertEquals(listOf("idle", "hat_cap"), loadout.renderLayers("idle", catalog))
    }

    @Test
    fun unequipClearsSlot() {
        val loadout = Loadout().equip(hat).equip(scarf).unequip(EquipSlot.HEAD)
        assertNull(loadout.equippedId(EquipSlot.HEAD))
        assertEquals(listOf("idle", "scarf_red"), loadout.renderLayers("idle", catalog))
    }

    @Test
    fun emptyLoadoutIsBodyOnly() {
        assertEquals(listOf("idle"), Loadout().renderLayers("idle", catalog))
    }

    @Test
    fun unknownIdIsSkipped() {
        // 카탈로그에서 사라진 장비(에셋 삭제·구버전 저장)는 조용히 건너뛴다 — 크래시 없이 본체만
        val stale = Loadout(mapOf(EquipSlot.HEAD to "deleted_item"))
        assertEquals(listOf("idle"), stale.renderLayers("idle", catalog))
    }
}
