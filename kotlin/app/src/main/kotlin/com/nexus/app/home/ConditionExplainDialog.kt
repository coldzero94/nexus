package com.nexus.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nexus.app.R
import com.nexus.app.ui.NexusSpacing
import com.nexus.core.ConditionEngine
import kotlin.math.roundToInt

/**
 * 컨디션 설명 (#223, E14-13) — 앱의 **유일한 손실 표면**을 처벌이 아닌 리듬으로 읽히게 한다.
 *
 * 컨디션은 이 앱에서 유일하게 내려가는 숫자다. 이유 없이 내려가면 처벌로 읽혀 "무처벌·죄책감 제로"
 * 비전을 훼손하고, 나아가 **"캐릭터는 퇴행하지 않는다"는 불변식의 체감 신뢰를 깎는다** — 사용자는
 * 레벨도 같이 깎이는 줄 안다.
 *
 * 그래서 설명의 중심은 "왜 내려갔나"가 아니라 **"무엇은 절대 안 내려가나"**다. 레벨·XP 불변을 먼저
 * 못 박고, 컨디션의 오르내림을 그 위의 작은 리듬으로 위치시킨다. 하락 조건도 바닥([SOFT_FLOOR])과
 * 함께 말해 "오래 쉬어도 처음부터 다시 시작하지 않는다"를 같이 전한다.
 *
 * 톤 규칙: 사용자를 탓하지 않는다. "며칠 안 움직여서 떨어졌어요"가 아니라 "며칠 안 움직이면 조금씩
 * 내려가요 — 가볍게 걷기만 해도 다시 올라와요"처럼 원인과 회복을 한 호흡에 둔다.
 */
@Composable
internal fun ConditionExplainDialog(restMode: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.condition_explain_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NexusSpacing.sm)) {
                Text(stringResource(R.string.condition_explain_what), style = MaterialTheme.typography.bodyMedium)
                // 불변식 ④의 명시적 재확인 — 이 줄이 이 다이얼로그의 존재 이유라 굵게.
                // 문자열 리소스는 마크다운을 해석하지 않으므로 강조는 스타일로 준다.
                Text(
                    stringResource(R.string.condition_explain_never_down),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.condition_explain_down, ConditionEngine.SOFT_FLOOR.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.condition_explain_up),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 휴식 모드는 켜져 있으면 현재 상태를, 꺼져 있으면 선택지를 알린다(권유가 아니라 안내)
                Text(
                    stringResource(
                        if (restMode) R.string.condition_explain_rest_on else R.string.condition_explain_rest_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.condition_explain_close)) }
        },
    )
}
