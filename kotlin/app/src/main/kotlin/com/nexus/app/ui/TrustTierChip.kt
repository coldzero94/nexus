package com.nexus.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import com.nexus.app.R
import com.nexus.core.TrustReason
import com.nexus.core.TrustTier

/**
 * 신뢰 등급 칩 (#222, E14-12) — 활동 세션 행과 성장 환산 줄이 **공유**하는 등급 라벨. 탭하면
 * "왜 이 등급인지"를 설명한다: A=워치+심박이라 강도를 더 정확히, B=폰 기록이어도 **개인 성장 100%**,
 * C=수기·미상이라 XP 제외(기록은 그대로 보임).
 *
 * [reason]이 있으면(활동 화면처럼 판정 근거를 아는 경우) 실제 근거 한 줄을 덧붙인다 — 문구는
 * 리소스지만 어떤 근거인지는 core [com.nexus.core.TrustExplainer]가 판정한다(하드코딩 아님).
 * '조작 불가·인증' 같은 단정 표현은 쓰지 않는다(RESEARCH §7.3).
 */
@Composable
fun TrustTierChip(
    tier: TrustTier,
    modifier: Modifier = Modifier,
    reason: TrustReason? = null,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    // 회전·테마 전환에도 열린 설명이 닫히지 않게(#222 리뷰)
    var explaining by rememberSaveable { mutableStateOf(false) }
    val openLabel = stringResource(R.string.trust_explain_more)
    Text(
        text = stringResource(tier.labelRes()),
        style = style,
        // 탭 가능함을 색·밑줄로 알린다(아이콘 추가 없이 줄 레이아웃 유지)
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            // 13sp 텍스트라 그대로면 터치 타깃이 ~19dp — 최소 크기 확보 + 버튼 역할 낭독(#222 리뷰)
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = openLabel, role = Role.Button) { explaining = true },
    )
    if (explaining) {
        TierExplainDialog(tier = tier, reason = reason, onDismiss = { explaining = false })
    }
}

@Composable
private fun TierExplainDialog(tier: TrustTier, reason: TrustReason?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trust_explain_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm)) {
                Text(stringResource(tier.meaningRes()), style = MaterialTheme.typography.bodyMedium)
                reason?.let {
                    Text(
                        stringResource(it.reasonRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.trust_explain_close)) }
        },
    )
}

private fun TrustTier.labelRes(): Int = when (this) {
    TrustTier.A -> R.string.trust_tier_a
    TrustTier.B -> R.string.trust_tier_b
    TrustTier.C -> R.string.trust_tier_c
}

private fun TrustTier.meaningRes(): Int = when (this) {
    TrustTier.A -> R.string.trust_meaning_a
    TrustTier.B -> R.string.trust_meaning_b
    TrustTier.C -> R.string.trust_meaning_c
}

private fun TrustReason.reasonRes(): Int = when (this) {
    TrustReason.WATCH_WITH_HEART_RATE -> R.string.trust_reason_watch_hr
    TrustReason.WATCH_WITHOUT_HEART_RATE -> R.string.trust_reason_watch_no_hr
    TrustReason.PHONE_RECORDED -> R.string.trust_reason_phone
    TrustReason.MANUAL_ENTRY -> R.string.trust_reason_manual
    TrustReason.UNKNOWN_SOURCE -> R.string.trust_reason_unknown
}
