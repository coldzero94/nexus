package com.nexus.app.growth

import android.content.Context

/**
 * 모은 이야기 조각 (#112, E5-14) — id 집합의 영속.
 *
 * **집합이라 두 번 넣어도 같다.** 드롭 자체가 세션 id의 순수 함수라(core `StoryDropPicker`)
 * 재동기화로 같은 조각이 다시 나와도 여기서 흡수된다 — "이미 굴린 세션" 목록 같은 걸 따로
 * 둘 필요가 없고, 그래서 세션 수만큼 자라는 저장소가 생기지 않는다.
 *
 * ## 왜 대기 집합이 따로 있는가
 *
 * [collect]가 돌려주는 "이번에 새로 들어온 것"은 **한 번의 로드 동안만 산다** — 다음 로드에선
 * 이미 모은 조각이라 빈 집합이 된다. 그 신호로만 축하하면 회전·프로세스 사망으로 축하가 영영
 * 사라진다(#61·#218이 레벨업·배지에서 같은 함정을 밟았다). 그래서 획득은 [collected]에,
 * 아직 안 보여준 축하는 [pending]에 따로 적고, 소비는 **사용자가 확인을 눌렀을 때**([acknowledge]).
 *
 * `commit()`으로 쓰는 이유: 획득 직후 프로세스가 죽으면 사용자는 봤는데 앱은 모르는 상태가 된다
 * (`IntegrityMarkerStore`·`DeviceSourceStore`와 같은 판단 — 내구성이 중요한 쓰기).
 */
class StoryCollectionStore(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    val collected: Set<String>
        get() = prefs.getStringSet(KEY_COLLECTED, emptySet())?.toSet().orEmpty()

    /** 아직 축하하지 않은 조각 id — 확인 전까지 살아남는다. */
    val pending: Set<String>
        get() = prefs.getStringSet(KEY_PENDING, emptySet())?.toSet().orEmpty()

    /** @return 이번에 **새로** 들어온 조각들. 이미 있던 건 제외된다. */
    fun collect(ids: Set<String>): Set<String> {
        val newly = ids - collected
        if (newly.isEmpty()) return emptySet()
        prefs.edit()
            .putStringSet(KEY_COLLECTED, collected + newly)
            .putStringSet(KEY_PENDING, pending + newly)
            .commit()
        return newly
    }

    /**
     * 축하 확인 — 같은 조각을 다시 축하하지 않는다.
     *
     * 여기는 `apply()`다. [collect]와 달리 메인 스레드의 버튼 핸들러에서 불리고, 유실 비용도
     * 다르다: 잃으면 축하가 한 번 더 뜰 뿐 획득 자체는 [collected]에 남아 있다.
     */
    fun acknowledge() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    internal companion object {
        const val PREFS = "nexus_story_collection"
        private const val KEY_COLLECTED = "collected"
        private const val KEY_PENDING = "pending"
    }
}
