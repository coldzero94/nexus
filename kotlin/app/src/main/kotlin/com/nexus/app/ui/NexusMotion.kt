package com.nexus.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/**
 * 모션 토큰 (#262, E16-12) — 앱 전역 '움직임 언어'의 단일 원천. 하드코딩 spring·즉시 값 대입을
 * 걷어내고 전환·보간이 공유할 Duration·Easing·Spring을 정의한다. #217(스프라이트)·#218·#219·#228이
 * 이 토큰을 참조한다.
 *
 * **리듀스드모션 훅**: 모든 duration은 [motionDuration]을 경유한다. [LocalMotionScale]=0f를 주입하면
 * 0ms(즉시)가 돼 애니메이션이 사라진다. 시스템 설정 감지·정책은 #228 소유 — 여기선 훅과 기본 스케일(1f)만 제공.
 */
object NexusMotion {
    /** 마이크로 상태 변화(칩·토글). */
    const val DURATION_SHORT = 120

    /** 표준 전환(탭 크로스페이드·작은 컴포넌트). */
    const val DURATION_MEDIUM = 240

    /** 큰 전환·게이지 보간. */
    const val DURATION_LONG = 360

    /** 강조 등장·축하. */
    const val DURATION_XLONG = 520

    /** 감속 강조 — 들어오고 멈추는 값(게이지 상승·하향 완만 감속). */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 가속 강조 — 빠져나가는 값. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 표준 — 대칭 전환(크로스페이드). */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 등장 연출 표준 스프링(레벨업 축하 등) — 살짝 통통 튀는 감쇠. */
    val CelebrationSpring: FiniteAnimationSpec<Float> =
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /**
     * duration에 모션 스케일 적용 — [scale] 0(리듀스드모션)이면 0ms(즉시). 음수 방어 클램프.
     * 순수 함수라 스케일링 계약을 유닛 테스트로 고정한다.
     */
    fun scaledDuration(baseMs: Int, scale: Float): Int = (baseMs * scale).roundToInt().coerceAtLeast(0)
}

/** 모션 스케일 — #228이 리듀스드모션 시 0f 주입. 기본 1f(정상). */
val LocalMotionScale = staticCompositionLocalOf { 1f }

/** 현 모션 스케일이 적용된 duration(ms). 전환·보간 스펙은 이 값을 쓴다. */
@Composable
@ReadOnlyComposable
fun motionDuration(baseMs: Int): Int = NexusMotion.scaledDuration(baseMs, LocalMotionScale.current)

/**
 * 게이지 진행값 보간 (#262 AC④) — 목표로 완만 감속 접근. 리듀스드모션(스케일 0) 시 즉시 반영.
 *
 * [upwardOnly]=true(레벨·XP): 값이 **줄어드는** 순간(레벨업 리셋·일일 경계)엔 애니메이션 없이 즉시
 * 반영해 게이지가 '뒤로 빠지는' 연출을 막는다(불퇴행 체감, #262 리뷰). 증가는 감속 보간.
 * false(컨디션): 양방향 감속 보간 — 하향도 툭 떨어지지 않고 부드럽게.
 */
@Composable
fun animatedGaugeProgress(target: Float, upwardOnly: Boolean = false, label: String = "gauge"): Float {
    var committedTarget by remember { mutableFloatStateOf(target) }
    val spec: FiniteAnimationSpec<Float> = if (upwardOnly && target < committedTarget) {
        snap()
    } else {
        tween(motionDuration(NexusMotion.DURATION_LONG), easing = NexusMotion.EmphasizedDecelerate)
    }
    val value by animateFloatAsState(targetValue = target, animationSpec = spec, label = label)
    // 방향 판정 기준을 커밋된 이전 목표로 갱신(컴포지션 중 상태 쓰기 회피).
    SideEffect { committedTarget = target }
    return value
}
