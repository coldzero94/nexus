package com.nexus.app.growth

import android.content.Context

/**
 * 축하 대기 중인 배지 (#218, E14-8) — **획득 감지와 축하 소비를 분리**한다.
 *
 * ## 왜 별도 저장소인가
 *
 * `BadgeState.newlyUnlocked`는 `currently - store.earned`로 계산되는데, 바로 다음 줄에서
 * `store.addEarned(currently)`가 돌아 **다음 로드에는 빈 집합**이 된다. 즉 그 신호는 한 번의
 * 컴포지션 동안만 산다 — 회전하거나 프로세스가 죽으면 축하가 영영 사라진다. #61 리뷰가 레벨업
 * 카드에서 정확히 같은 함정을 지적했고("기준점 소비를 감지 시점에 하면 카드가 영영 소실된다"),
 * 여기서도 같은 답을 쓴다: **소비는 '확인'을 눌렀을 때**.
 *
 * ## 묶어서 한 번만
 *
 * 여러 배지가 동시에 열려도 대기 집합에 합쳐 담고 카드 하나로 축하한다. 배지마다 카드를 띄우면
 * 첫 동기화에서 다섯 장이 연달아 뜨는 폭주가 된다(완료 기준의 '동시 다수는 묶음 1회').
 */
internal class BadgeCelebrationStore(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** 아직 축하하지 않은 배지 id. */
    val pending: Set<String>
        get() = prefs.getStringSet(KEY_PENDING, emptySet())?.toSet() ?: emptySet()

    /** 새로 열린 배지를 대기 집합에 더한다(합집합). 빈 입력은 무시. */
    fun record(ids: Set<String>) {
        if (ids.isEmpty()) return
        // 여기만 commit()이다: 이 값을 잃으면 축하가 영영 사라지고, 호출부가 이미 IO 컨텍스트다.
        prefs.edit().putStringSet(KEY_PENDING, pending + ids).commit()
    }

    /**
     * 확인 — 같은 배지를 다시 축하하지 않는다.
     *
     * 여기는 `apply()`다. [record]와 달리 **메인 스레드의 버튼 핸들러**에서 불리고, 유실 비용도
     * 다르다: 잃으면 축하가 한 번 더 뜰 뿐이지 영구 손실이 아니다. 동기 쓰기로 exit 연출을
     * 끊을 이유가 없다(형제인 `GrowthStateStore.recordSeen`도 `apply()`다).
     */
    fun clear() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    internal companion object {
        const val PREFS = "nexus_badge_celebration"
        private const val KEY_PENDING = "pending_badge_ids"
    }
}
