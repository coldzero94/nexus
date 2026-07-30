package com.nexus.app.data

import android.content.Context
import java.util.UUID

/**
 * 설치 식별자 (#240, E15-12) — **계정 연결 시 기존 원장을 귀속시킬 앵커**.
 *
 * BACKEND §1이 "로컬 UUID지만 나중에 연결 가능한 구조"를 지금 박을 계약으로 명시하는데 발급 코드가
 * 어디에도 없었다. 앵커가 없으면 #80(계정 연결)의 "기존 캐릭터·원장 무손실 승계"가 성립하지 않는다:
 * 알파 테스터가 재설치·기기 이전을 하면 새 UUID를 받고, 이후 계정을 연결할 때 **이전 원장을 누구에게
 * 귀속시킬지 알 수 없다.** §1이 경고하는 "나중에 추가하면 소급 보강 불가"의 정확한 사례다.
 *
 * ## 서버로 나가지 않는다
 *
 * 이건 **로컬 앵커**다. 텔레메트리·크래시 페이로드에 싣지 않는다(불변식 ② — allowlist가 비어 있어
 * 애초에 통과 못 한다). 백업에는 포함한다: 백업의 목적이 승계이므로 앵커가 함께 넘어가야 의미가 있고,
 * 본인 통제 표면이다(#51 결정과 같은 근거).
 */
class InstallIdStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 이 설치의 식별자 — 첫 호출에 1회 생성해 영속하고, 이후 항상 같은 값을 돌려준다.
     *
     * 생성 시점을 첫 호출로 미루는 이유: 앱 시작 경로에 쓰기를 넣지 않으려는 것이다. 어차피 백업·연결
     * 어느 쪽도 첫 실행 직후에 일어나지 않는다.
     */
    val installId: String
        get() = prefs.getString(KEY_INSTALL_ID, null) ?: newId().also {
            prefs.edit().putString(KEY_INSTALL_ID, it).apply()
        }

    /**
     * 백업 복원 시 앵커 승계 (#240) — 스냅샷의 UUID가 **갓 생성된 로컬 UUID를 대체한다**.
     *
     * 우선순위가 이 방향인 이유: 복원의 목적은 "이전 설치의 연속"이다. 로컬에서 방금 만든 값을 지키면
     * 복원된 원장과 앵커가 어긋나 승계가 깨진다. 빈 값은 무시한다(구버전 백업엔 필드가 없다).
     */
    fun adoptFromBackup(restored: String?) {
        if (restored.isNullOrBlank()) return
        prefs.edit().putString(KEY_INSTALL_ID, restored).apply()
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private companion object {
        const val PREFS = "nexus_install"
        const val KEY_INSTALL_ID = "install_id"
    }
}
