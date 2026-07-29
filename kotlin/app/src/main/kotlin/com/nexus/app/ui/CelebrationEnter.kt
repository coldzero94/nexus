package com.nexus.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable

/**
 * 축하 등장 전환 (#228) — 감축 시 **튕김 없이 페이드만**. 콘텐츠는 동일하다.
 *
 * scaleIn 스프링은 카드가 튀어나오며 오버슈트하는 연출이라 전정계 민감 사용자에게 가장 부담이 큰
 * 종류다. 그렇다고 즉시 나타나게 하면 "언제 생겼는지" 모르게 되므로, 위치가 바뀌지 않는 페이드로
 * 대체한다 — 움직임 없이 등장만 알린다.
 */
@Composable
internal fun celebrationEnter(): EnterTransition =
    if (reduceMotion()) fadeIn() else scaleIn(animationSpec = NexusMotion.CelebrationSpring) + fadeIn()
