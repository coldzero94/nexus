package com.nexus.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.character.CharacterComposer
import com.nexus.app.character.EquipStore
import com.nexus.app.settings.IdentityStore
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.CharacterName
import com.nexus.core.EquipItem
import com.nexus.core.EquipSlot
import com.nexus.core.Loadout

/**
 * 캐릭터 만들기 (#42, E7-1) — **권한 요청 전**에 자기 캐릭터를 갖게 하는 단계.
 *
 * ## 왜 권한보다 먼저인가
 *
 * 이 순서가 이 스텝의 존재 이유다. 아직 아무것도 아닌 앱이 건강 데이터부터 요구하면 "왜 줘야 하지"가
 * 되지만, 이름을 지어주고 모자를 씌운 뒤라면 "얘를 키우려면 필요하구나"가 된다. 온보딩 이탈이
 * 가장 크게 나는 지점이 권한 화면이고, 그 앞에 애착의 씨앗을 심는 게 값싸고 효과가 크다.
 *
 * ## 왜 건너뛸 수 있는가
 *
 * 이름도 장비도 **없어도 앱이 완전히 동작한다** — 이름은 무명 카피로 폴백하고(#216), 장비는
 * 안 쓰면 그만이다. 필수로 만들면 "빨리 쓰고 싶은" 사용자에게 관문이 되고, 그건 애착이 아니라
 * 마찰이다. 나중에 설정에서 언제든 지을 수 있다.
 *
 * ## 저장 시점
 *
 * 이름은 **다음으로 넘어갈 때** 저장한다(입력 중 매 글자 저장하면 중간 상태가 새고, 무효 입력이
 * 저장될 여지가 생긴다). 장비는 고르는 즉시 저장한다 — 미리보기가 곧 결과라 지연시킬 이유가 없고,
 * 뒤로 갔다 와도 선택이 남아야 한다.
 */
@Composable
internal fun ColumnScope.CreateCharacterContent(onDone: () -> Unit) {
    val context = LocalContext.current
    val identity = remember { IdentityStore(context) }
    val equipStore = remember { EquipStore(context) }
    val catalog =
        remember { runCatching { CharacterAssets(context).loadEquipCatalog().items }.getOrDefault(emptyList()) }

    var name by rememberSaveable { mutableStateOf(identity.name.orEmpty()) }
    var loadout by remember { mutableStateOf(equipStore.load()) }

    Text(
        text = stringResource(R.string.onboarding_create_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.sm))
    Text(
        text = stringResource(R.string.onboarding_create_body),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.lg))

    // 고르는 즉시 반영되는 미리보기 — 이 스텝의 보상이 이 그림이다
    CharacterComposer.CharacterSprite(
        state = "idle",
        modifier = Modifier.size(NexusSpacing.heroSprite),
        equipLayers = loadout.renderLayers("idle", catalog).drop(1),
    )

    Spacer(Modifier.height(NexusSpacing.lg))
    OutlinedTextField(
        value = name,
        onValueChange = { if (it.length <= CharacterName.MAX_LENGTH) name = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.onboarding_create_name_label)) },
        supportingText = { Text(stringResource(R.string.onboarding_create_name_hint, CharacterName.MAX_LENGTH)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )

    EquipSlot.entries.forEach { slot ->
        val items = catalog.filter { it.slot == slot }
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(NexusSpacing.md))
            SlotChooser(slot, items, loadout) { next ->
                loadout = next
                equipStore.setEquipped(slot, next.equippedId(slot))
            }
        }
    }

    Spacer(Modifier.height(NexusSpacing.xl))
    Button(
        onClick = {
            // 유효할 때만 저장 — 빈 입력은 '안 지음'이지 '지우기'가 아니다
            if (CharacterName.isValid(name)) identity.setName(name)
            onDone()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.onboarding_next))
    }
    TextButton(onClick = onDone) {
        Text(stringResource(R.string.onboarding_create_skip))
    }
}

/** 슬롯 하나의 선택지 — 같은 칩을 다시 누르면 해제(벗기)다. */
@Composable
private fun SlotChooser(slot: EquipSlot, items: List<EquipItem>, loadout: Loadout, onChange: (Loadout) -> Unit) {
    Text(
        text = stringResource(slotLabel(slot)),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(NexusSpacing.xs))
    Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs)) {
        items.chunked(CHIPS_PER_ROW).forEach { row ->
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(NexusSpacing.sm),
            ) {
                row.forEach { item ->
                    val equipped = loadout.equippedId(slot) == item.id
                    FilterChip(
                        selected = equipped,
                        onClick = { onChange(if (equipped) loadout.unequip(slot) else loadout.equip(item)) },
                        label = { Text(item.name) },
                    )
                }
            }
        }
    }
}

private fun slotLabel(slot: EquipSlot) = when (slot) {
    EquipSlot.HEAD -> R.string.equip_slot_head
    EquipSlot.ACCESSORY -> R.string.equip_slot_accessory
}

private const val CHIPS_PER_ROW = 2
