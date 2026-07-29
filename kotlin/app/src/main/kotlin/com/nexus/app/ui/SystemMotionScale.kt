package com.nexus.app.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * 시스템 '애니메이션 제거' 반영 (#217) — `Settings.Global.ANIMATOR_DURATION_SCALE`를 읽는다.
 *
 * 개발자 옵션에서 애니메이션을 끄거나 접근성 설정에서 모션을 줄이면 이 값이 0이 된다. 전정기관
 * 장애가 있는 사용자에게 상시 미동은 불편을 넘어 증상을 유발할 수 있어, **끄면 정말 멈춰야** 한다.
 *
 * 설정 변경은 액티비티를 재생성하지 않으므로 **포그라운드 복귀마다 다시 읽는다** — 한 번 캐시하면
 * "앱을 잠시 나가서 애니메이션을 끄고 돌아오는" 가장 현실적인 경로에서 계속 움직인다.
 *
 * 반환값은 [LocalMotionScale]에 그대로 공급된다(앱 루트). 0.5배 같은 부분 감속도 살린다 —
 * 이진값으로 뭉개면 "느리게"를 고른 사용자에게 전속력이 나간다.
 */
@Composable
fun rememberSystemMotionScale(): Float {
    val context = LocalContext.current
    var scale by remember(context) { mutableFloatStateOf(readAnimatorScale(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scale = readAnimatorScale(context)
    }
    return scale
}

/** 3-인자 오버로드는 예외를 던지지 않고 기본값을 돌려준다 — 설정이 없으면 정상(1f)으로 본다. */
private fun readAnimatorScale(context: android.content.Context): Float = Settings.Global
    .getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    .coerceAtLeast(0f)
