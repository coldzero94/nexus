package com.nexus.app.growth

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.CharacterComposer
import com.nexus.app.character.EquipStore
import com.nexus.core.EquipCatalog
import com.nexus.core.EquipItem
import com.nexus.core.EquipSlot
import com.nexus.core.Loadout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "EquipmentSection"

/**
 * 장비 꾸미기 (#37, E5-4) — 슬롯별 장착/해제가 상단 미리보기 캐릭터에 즉시 반영된다(완료 기준).
 * 카탈로그 로드 실패는 섹션을 숨긴다(성장 화면 유지, #130 catch 계약). 획득 경로는 후속(#68).
 */
@Composable
fun EquipmentCard(spriteState: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { EquipStore(context) }
    var catalog by remember { mutableStateOf<EquipCatalog?>(null) }
    var loadout by remember { mutableStateOf(Loadout()) }

    LaunchedEffect(Unit) {
        catalog = loadCatalogOrNull(context)
        loadout = withContext(Dispatchers.IO) { store.load() }
    }

    val loaded = catalog ?: return
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.equip_title), style = MaterialTheme.typography.titleMedium)
            CharacterComposer.CharacterSprite(
                state = spriteState,
                modifier = Modifier.size(120.dp).align(Alignment.CenterHorizontally),
                equipLayers = loadout.renderLayers(spriteState, loaded.items).drop(1),
            )
            EquipSlot.entries.forEach { slot ->
                val items = loaded.items.filter { it.slot == slot }
                if (items.isNotEmpty()) {
                    SlotRow(slot, items, loadout.equippedId(slot)) { item ->
                        val next = if (loadout.equippedId(slot) == item.id) {
                            loadout.unequip(slot).also { store.setEquipped(slot, null) }
                        } else {
                            loadout.equip(item).also { store.setEquipped(slot, item.id) }
                        }
                        loadout = next
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(slot: EquipSlot, items: List<EquipItem>, equippedId: String?, onToggle: (EquipItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(slotLabel(slot)), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                FilterChip(
                    selected = equippedId == item.id,
                    onClick = { onToggle(item) },
                    label = { Text(item.name) },
                )
            }
        }
    }
}

private fun slotLabel(slot: EquipSlot): Int = when (slot) {
    EquipSlot.HEAD -> R.string.equip_slot_head
    EquipSlot.ACCESSORY -> R.string.equip_slot_accessory
}

private suspend fun loadCatalogOrNull(context: android.content.Context): EquipCatalog? = try {
    withContext(Dispatchers.IO) { CharacterAssets(context).loadEquipCatalog() }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "equip catalog load failure", e)
    null
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "equip catalog invalid", e) // SerializationException 포함(하위 타입)
    null
}
