package com.nexus.app.character

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "EquipLayers"

/**
 * 저장된 로드아웃 → 렌더 레이어 (#37) — [baseState] 본체 위에 쌓을 장비 레이어 상태들.
 * 카탈로그·프리퍼런스 로드는 디스크 IO라 [Dispatchers.IO]에서. 부가 정보라 실패는 빈 목록
 * (본체만 그린다 — #130 catch 계약). 홈·기타 렌더 지점이 공유하는 단일 진입점.
 */
suspend fun equipRenderLayers(context: Context, baseState: String): List<String> = try {
    withContext(Dispatchers.IO) {
        val catalog = CharacterAssets(context).loadEquipCatalog()
        EquipStore(context).load().renderLayers(baseState, catalog.items).drop(1)
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    Log.w(TAG, "equip layers load failure", e)
    emptyList()
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "equip catalog invalid", e) // SerializationException 포함(하위 타입)
    emptyList()
}
