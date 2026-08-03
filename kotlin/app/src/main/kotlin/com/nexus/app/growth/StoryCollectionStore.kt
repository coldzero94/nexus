package com.nexus.app.growth

import android.content.Context

/**
 * 모은 이야기 조각 (#112, E5-14) — id 집합의 영속.
 *
 * **집합이라 두 번 넣어도 같다.** 드롭 자체가 세션 id의 순수 함수라(core `StoryDropPicker`)
 * 재동기화로 같은 조각이 다시 나와도 여기서 흡수된다 — "이미 굴린 세션" 목록 같은 걸 따로
 * 둘 필요가 없고, 그래서 세션 수만큼 자라는 저장소가 생기지 않는다.
 *
 * `commit()`으로 쓰는 이유: 획득 직후 프로세스가 죽으면 사용자는 봤는데 앱은 모르는 상태가 된다
 * (`IntegrityMarkerStore`·`DeviceSourceStore`와 같은 판단 — 내구성이 중요한 쓰기).
 */
class StoryCollectionStore(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    val collected: Set<String>
        get() = prefs.getStringSet(KEY_COLLECTED, emptySet())?.toSet().orEmpty()

    /** @return 이번에 **새로** 들어온 조각들(축하 대상). 이미 있던 건 제외된다. */
    fun collect(ids: Set<String>): Set<String> {
        val newly = ids - collected
        if (newly.isEmpty()) return emptySet()
        prefs.edit().putStringSet(KEY_COLLECTED, collected + newly).commit()
        return newly
    }

    private companion object {
        const val PREFS = "nexus_story_collection"
        const val KEY_COLLECTED = "collected"
    }
}
