package com.nexus.app.character

/**
 * 쓰다듬기 반응의 연타 억제 (#217) — 순수 상태 기계라 단위 테스트로 고정한다.
 *
 * 연타를 그대로 받으면 바운스가 매 탭마다 처음부터 다시 시작해 캐릭터가 경련하듯 보이고, 대사도
 * 순식간에 소진돼 "반복 회피"(MVP §1, Pokémon Sleep 이탈 원인)가 무의미해진다. 그렇다고 너무 길게
 * 막으면 "만졌는데 반응이 없다"가 되어 애착 훅 자체가 죽는다 — 바운스 한 번이 끝날 만큼만 막는다.
 */
internal class PetReactionThrottle(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    private var lastAcceptedAtMillis = Long.MIN_VALUE

    /** 이번 탭을 반응으로 받아들일지 — 직전 수락으로부터 [windowMillis]가 지났을 때만. */
    fun accept(nowMillis: Long): Boolean {
        // 첫 탭은 무조건 수락(빼기 오버플로 방지 겸)
        if (lastAcceptedAtMillis == Long.MIN_VALUE) {
            lastAcceptedAtMillis = nowMillis
            return true
        }
        if (nowMillis - lastAcceptedAtMillis < windowMillis) return false
        lastAcceptedAtMillis = nowMillis
        return true
    }

    companion object {
        /** 바운스 왕복이 끝날 정도 — 이보다 짧으면 연출이 겹치고, 길면 무반응으로 느껴진다. */
        const val DEFAULT_WINDOW_MILLIS = 700L
    }
}
