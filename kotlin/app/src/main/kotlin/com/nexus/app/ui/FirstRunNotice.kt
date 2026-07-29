package com.nexus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer

/** 빈 상태 렌더 테스트가 잡는 태그 (#213) — 3분기 중 이 분기만 이 노드를 그린다. */
const val FIRST_RUN_NOTICE_TAG = "first_run_notice"

/**
 * 첫 연결·첫 동기화 대기 빈 상태 (#213, E14-3) — 홈·활동·성장이 공유한다.
 *
 * 첫날 0 데이터는 헬스 연동 앱의 최대 이탈 트리거다. 오늘 5천 보를 걸은 사람도 첫 진입에선
 * '0보·0 XP·최근 세션 없음'만 보게 되는데(삼성헬스 → HC 전파 30~60분), 0을 나열하면 고장으로
 * 읽힌다. 그래서 세 화면 모두 이 상태에선 **수치 대신** 캐릭터와 "준비 중"을 보여준다.
 *
 * 카피 규칙: 실시간·즉시 반영을 약속하지 않는다(불변식 ⑤). 걸리는 시간을 먼저 밝혀 두면 30분 뒤
 * 데이터가 나타났을 때 "말한 대로"가 되고, 약속했다 어기는 것보다 신뢰가 남는다.
 *
 * 노출 조건은 [com.nexus.core.FirstRun.isAwaitingFirstData] — 평생 원장 XP 0 **그리고** 보여줄
 * 건강 데이터 0. 기존 사용자의 휴식일에는 절대 뜨지 않는다.
 *
 * @param onSyncFinished '지금 확인'이 끝났을 때. 화면이 다시 읽지 않으면 데이터가 도착했는데도
 *   "곧 시작해요"에 머물러, 유일한 액션이 막다른 길이 된다.
 */
@Composable
fun FirstRunNotice(onSyncFinished: () -> Unit = {}, modifier: Modifier = Modifier) {
    val sync = rememberManualSync(onSyncFinished)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FIRST_RUN_NOTICE_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.md),
    ) {
        // 숫자가 없는 화면에서 캐릭터가 유일한 앵커 — idle로 "기다리는 중"을 보인다
        CharacterComposer.CharacterSprite(state = "idle", modifier = Modifier.size(NexusSpacing.heroSprite))
        Text(
            stringResource(R.string.first_run_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.first_run_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = sync.checkNow, enabled = !sync.running) {
            Text(stringResource(if (sync.running) R.string.freshness_checking else R.string.freshness_check_now))
        }
        if (sync.failed) {
            // 실패를 삼키면 "왜 안 오지"에 답을 못 한다 — 권한 회수가 가장 흔한 원인 (#130 계약)
            Text(
                stringResource(R.string.freshness_check_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
