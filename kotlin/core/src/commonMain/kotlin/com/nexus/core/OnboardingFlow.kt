package com.nexus.core

/** 온보딩 스텝 (#6·#225). 순서는 [OnboardingFlow.steps]가 정한다 — 선언 순서에 기대지 않는다. */
enum class OnboardingStage { WELCOME, CREATE, RATIONALE, SAMSUNG_HEALTH, WEEKLY_GOAL }

/**
 * 온보딩 경로 (#225, E14-15) — 진행 표시와 뒤로가기가 **실제로 지나는 스텝**만 세게 한다.
 *
 * ## 왜 경로를 계산하는가
 *
 * [OnboardingStage.CREATE]는 **권한 요청 전**에 온다(#42). 이름을 짓고 꾸민 캐릭터가 생긴 뒤에
 * 권한을 물어야 "내 것을 키우기 위해 허락한다"가 되고, 순서가 뒤집히면 아직 아무것도 아닌 앱이
 * 건강 데이터부터 요구하는 모양이 된다. 가용성과 무관하므로 두 경로 모두에 있다.
 *
 * 흐름이 모든 사용자에게 같지 않다. Health Connect를 쓸 수 없거나 업데이트가 필요한 기기는 권한
 * 설명(RATIONALE)을 건너뛴다(#236 — 거기서 권한을 요청하면 실패한다). 그래서 스텝을 enum 선언
 * 순서로 세면 **"1/4"였다가 갑자기 3번으로 뛰는** 인디케이터가 된다 — 얼마나 남았는지 알려주려고
 * 만든 장치가 오히려 사용자를 헷갈리게 한다. 뒤로가기도 같은 문제를 겪는다: 건너뛴 스텝으로
 * 되돌아가면 권한 요청이 실패하는 화면에 갇힌다.
 *
 * 그래서 가용성으로 경로를 먼저 정하고, 진행 표시·뒤로가기 모두 **그 경로 위에서만** 움직인다.
 */
object OnboardingFlow {

    /**
     * 이 기기가 실제로 지나는 스텝 순서.
     *
     * @param healthAvailable Health Connect를 **지금 쓸 수 있는가**([HealthAvailability.Available]).
     *   업데이트 필요·미가용은 false — 권한 설명을 보여줘도 요청이 실패한다(#236).
     */
    fun steps(healthAvailable: Boolean): List<OnboardingStage> = if (healthAvailable) {
        listOf(
            OnboardingStage.WELCOME,
            OnboardingStage.CREATE,
            OnboardingStage.RATIONALE,
            OnboardingStage.SAMSUNG_HEALTH,
            OnboardingStage.WEEKLY_GOAL,
        )
    } else {
        listOf(
            OnboardingStage.WELCOME,
            OnboardingStage.CREATE,
            OnboardingStage.SAMSUNG_HEALTH,
            OnboardingStage.WEEKLY_GOAL,
        )
    }

    /**
     * 1부터 세는 현재 위치. 경로에 없는 스텝이면 null — 인디케이터를 그리지 않는다.
     *
     * 경로 밖 스텝에 0이나 1을 주지 않는 이유: 가용성이 바뀌는 순간(사용자가 HC를 설치하고 돌아옴)
     * 잠깐 경로와 스텝이 어긋날 수 있고, 그때 **틀린 숫자를 보여주는 것보다 안 보여주는 게 낫다**.
     */
    fun positionOf(stage: OnboardingStage, healthAvailable: Boolean): Int? =
        steps(healthAvailable).indexOf(stage).takeIf { it >= 0 }?.plus(1)

    /**
     * 뒤로 갈 스텝. 첫 스텝이거나 경로 밖이면 null — 호출부는 그때 뒤로 어포던스를 숨긴다.
     *
     * 건너뛴 스텝으로는 절대 돌아가지 않는다(경로에서 뽑으므로). HC 미가용 기기가 SAMSUNG_HEALTH에서
     * 뒤로 가면 RATIONALE이 아니라 WELCOME이다 — 권한 요청이 실패하는 화면에 갇히지 않게.
     */
    fun previousOf(stage: OnboardingStage, healthAvailable: Boolean): OnboardingStage? {
        val path = steps(healthAvailable)
        val index = path.indexOf(stage)
        return if (index >= 1) path[index - 1] else null
    }
}
