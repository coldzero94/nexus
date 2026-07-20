package com.nexus.app.character

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import com.nexus.core.BadgeTable
import com.nexus.core.BadgeTableReader
import com.nexus.core.CharacterAnimationSet
import com.nexus.core.CharacterAssetConvention
import com.nexus.core.DialoguePool
import com.nexus.core.DialogueTable
import com.nexus.core.EquipCatalog
import com.nexus.core.EquipCatalogReader
import com.nexus.core.MonthlyBadgeTable
import com.nexus.core.MonthlyBadgeTableReader
import com.nexus.core.MoodTable
import com.nexus.core.MoodTriggerTable

/**
 * 캐릭터 에셋 로더 (#25, E4-1). 컴포저(E4-2)의 유일한 에셋 진입점.
 *
 * 에셋 추가 절차(코드 무수정): ① drawable에 `character_{state}_{frame}` 파일 추가
 * ② assets/character/animations.json에 상태 항목 추가 — 끝.
 */
class CharacterAssets(private val context: Context) {

    /** 애니메이션 메타 로드 — 잘못된 표는 여기서 즉시 실패(조용한 무애니메이션 방지, core 검증). */
    fun loadAnimationSet(): CharacterAnimationSet = context.assets.open(META_PATH).bufferedReader().use {
        CharacterAssetConvention.parse(it.readText())
    }

    /** 대사 풀 로드 (#29) — 같은 fail-fast 계약. 대사 수정 = JSON만(코드 무수정). */
    fun loadDialoguePool(): DialoguePool = context.assets.open(DIALOGUE_PATH).bufferedReader().use {
        DialogueTable.parse(it.readText())
    }

    /** 기분 트리거 표 로드 (#28) — 같은 fail-fast 계약. 임계값·규칙 수정 = JSON만(코드 무수정). */
    fun loadMoodTable(): MoodTable = context.assets.open(MOOD_PATH).bufferedReader().use {
        MoodTriggerTable.parse(it.readText())
    }

    /** 배지 해금 표 로드 (#69, 게임 데이터) — 같은 fail-fast 계약. 배지 추가·조건 수정 = JSON만. */
    fun loadBadgeTable(): BadgeTable = context.assets.open(BADGE_PATH).bufferedReader().use {
        BadgeTableReader.parse(it.readText())
    }

    /** 월 한정 배지 캘린더 로드 (#38) — 같은 fail-fast 계약. 새 달 배지 추가 = JSON만. */
    fun loadMonthlyBadgeTable(): MonthlyBadgeTable = context.assets.open(MONTHLY_BADGE_PATH).bufferedReader().use {
        MonthlyBadgeTableReader.parse(it.readText())
    }

    /** 장비 카탈로그 로드 (#37) — 같은 fail-fast 계약. 장비 추가 = JSON + 드로어블만(코드 무수정). */
    fun loadEquipCatalog(): EquipCatalog = context.assets.open(EQUIPMENT_PATH).bufferedReader().use {
        EquipCatalogReader.parse(it.readText())
    }

    /**
     * 규약 이름 → 드로어블 id. 없으면 null(호출자가 기본 상태 프레임으로 폴백).
     * getIdentifier는 규약 기반 동적 조회가 목적이라 의도적 사용 — 에셋 추가에 코드 수정이
     * 없어야 한다는 E4-1 완료 기준 때문(정적 R 참조는 상태 추가마다 코드 수정 필요).
     */
    @SuppressLint("DiscouragedApi")
    @DrawableRes
    fun frameResIdOrNull(state: String, frame: Int): Int? {
        val name = CharacterAssetConvention.frameName(state, frame)
        return context.resources
            .getIdentifier(name, "drawable", context.packageName)
            .takeIf { it != 0 }
    }

    private companion object {
        const val META_PATH = "character/animations.json"
        const val DIALOGUE_PATH = "character/dialogue.json"
        const val MOOD_PATH = "character/mood_triggers.json"
        const val BADGE_PATH = "character/badges.json"
        const val MONTHLY_BADGE_PATH = "character/monthly_badges.json"
        const val EQUIPMENT_PATH = "character/equipment.json"
    }
}
