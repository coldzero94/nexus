package com.nexus.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.ui.NexusIcons
import com.nexus.app.ui.NexusSpacing

/**
 * 온보딩 진행 표시 + 뒤로 어포던스 (#225, E14-15).
 *
 * ## 왜 필요했나
 *
 * 4단계 흐름에 진행 인디케이터도 뒤로가기도 없었다(전진만). **얼마나 남았는지 모르는 불투명한 흐름**과
 * **권한 화면을 지나면 되돌아갈 수 없음**은 첫 실행 이탈의 흔한 원인이다. 설치→완료 퍼널을 지키는
 * 저비용 개선이고, 퍼널 계측(#226)이 이미 붙어 있어 효과도 관측된다.
 *
 * ## 점 + 숫자를 함께 준다
 *
 * 점만 그리면 개수를 세어야 하고, 숫자만 주면 "얼마나 남았나"가 한눈에 안 온다. 점은 위치를,
 * 숫자는 총량을 말한다. 점은 색·크기로만 현재를 표시하므로 스크린리더에는 무의미해서
 * **행 전체를 한 노드로 묶고** 사람이 읽는 문장을 준다("온보딩 4단계 중 2단계").
 *
 * ## 첫 스텝에는 뒤로가 없다
 *
 * [onBack]이 null이면 자리만 차지하는 투명 스페이서를 둔다. 버튼을 숨기면 진행 표시가 좌우로
 * 튀어서, 스텝을 넘길 때마다 인디케이터가 움직이는 것처럼 보인다.
 */
@Composable
internal fun OnboardingProgress(current: Int, total: Int, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.a11y_onboarding_progress, current, total)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(NexusIcons.back),
                    contentDescription = stringResource(R.string.onboarding_back),
                )
            }
        } else {
            // 버튼을 숨기면 진행 표시가 좌우로 튀어 인디케이터가 움직이는 것처럼 보인다
            Spacer(Modifier.size(BACK_SLOT.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = label },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dots(current = current, total = total)
            Spacer(Modifier.width(NexusSpacing.sm))
            Text(
                text = stringResource(R.string.onboarding_progress, current, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 뒤로 버튼과 대칭인 빈 슬롯 — 점 묶음이 화면 가운데에 오게 한다
        Spacer(Modifier.size(BACK_SLOT.dp))
    }
}

@Composable
private fun Dots(current: Int, total: Int) {
    val active = MaterialTheme.colorScheme.primary
    // outlineVariant는 배경 대비 1.62:1이라 라이트에서 안 보인다 — #266에서 같은 토큰이 같은 이유로
    // 실패 채널로 판정됐다. 안 보이면 '전체 몇 단계'가 숫자에만 걸려 점은 장식이 된다.
    val inactive = MaterialTheme.colorScheme.outline
    Row(horizontalArrangement = Arrangement.spacedBy(NexusSpacing.xs)) {
        for (index in 1..total) {
            Dot(color = if (index <= current) active else inactive, wide = index == current)
        }
    }
}

/** 현재 점은 길게 늘여 위치를 색 없이도 읽히게 한다 — 색만으로 구분하지 않는다. */
@Composable
private fun Dot(color: Color, wide: Boolean) {
    Box(
        Modifier
            .height(DOT.dp)
            .width(if (wide) DOT_WIDE.dp else DOT.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** 뒤로 버튼 슬롯 크기 — 좌우 대칭을 위해 빈 자리도 같은 크기를 차지한다. */
private const val BACK_SLOT = 48

private const val DOT = 8

/** 현재 점 길이 — 색을 못 봐도 위치가 읽히게 한다. */
private const val DOT_WIDE = 20
