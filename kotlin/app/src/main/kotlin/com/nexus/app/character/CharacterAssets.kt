package com.nexus.app.character

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.DrawableRes
import com.nexus.core.CharacterAnimationSet
import com.nexus.core.CharacterAssetConvention

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
    }
}
