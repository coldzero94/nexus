package com.nexus.app.character

import android.content.Context
import com.nexus.core.EquipSlot
import com.nexus.core.Loadout

/**
 * 로드아웃 영속 (#37, E5-4) — 슬롯별 장착 아이템 id를 프리퍼런스에 저장. 순수 상태 로직은
 * core [Loadout]이고 여기선 읽기/쓰기만 담당(#39 위젯 스냅샷 스토어와 같은 얇은 계층).
 *
 * MVP는 카탈로그 전체가 보유 상태다 — 획득 경로(원정 보상)는 #68·#178 결정 뒤에 붙는다.
 */
class EquipStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Loadout {
        val equipped = EquipSlot.entries.mapNotNull { slot ->
            prefs.getString(key(slot), null)?.let { slot to it }
        }.toMap()
        return Loadout(equipped)
    }

    fun setEquipped(slot: EquipSlot, itemId: String?) {
        prefs.edit().apply {
            if (itemId == null) remove(key(slot)) else putString(key(slot), itemId)
        }.apply()
    }

    private fun key(slot: EquipSlot) = "slot_${slot.name}"

    private companion object {
        const val PREFS = "nexus_loadout"
    }
}
