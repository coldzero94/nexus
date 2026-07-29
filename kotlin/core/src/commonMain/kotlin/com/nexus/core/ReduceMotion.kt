package com.nexus.core

/**
 * 모션 감축 판정 (#228, E14-18) — 시스템 애니메이션 스케일 하나로 결정한다.
 *
 * 전정계가 민감한 사용자에게 상시 움직임은 취향이 아니라 **실사용 장벽**이다(멀미·어지럼). 안드로이드는
 * 접근성 '애니메이션 제거'와 개발자 옵션의 애니메이션 배율을 같은 값(`ANIMATOR_DURATION_SCALE`)으로
 * 노출하므로, 그 값이 0이면 사용자가 "움직이지 마라"라고 명시한 것으로 본다.
 *
 * 판정 자체는 한 줄이지만 core에 두는 이유는 **경계값을 한 곳에 못 박기 위해서**다: 0.5배 같은 부분
 * 감축을 '감축'으로 오인해 애니메이션을 통째로 끄면, "느리게"를 고른 사용자에게서 연출을 빼앗는다.
 */
object ReduceMotion {
    /**
     * 모션을 없애야 하는가 — 스케일이 **정확히 0 이하**일 때만 true.
     *
     * @param animatorDurationScale 시스템 애니메이션 배율. 1 = 정상, 0 = 제거, 0.5 = 절반 속도.
     *   음수(손상된 설정)는 0과 같게 다룬다.
     */
    fun isReduced(animatorDurationScale: Float): Boolean = animatorDurationScale <= 0f
}
