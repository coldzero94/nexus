package com.nexus.app.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.nexus.app.R
import com.nexus.app.character.CharacterComposer
import com.nexus.app.ui.NexusSpacing
import com.nexus.app.ui.reduceMotion
import kotlinx.coroutines.delay

/**
 * 첫 만남·부화 연출 (#110, E7-6) — 알이 흔들리고 금이 가다 깨어나는 1컷 서사.
 *
 * ## 왜 명명보다 앞인가
 *
 * 이름을 짓는 행위는 **이미 존재하는 대상**에게 하는 것이다. 아무것도 없는 화면에서 "이름을
 * 지어주세요"는 폼 작성이지만, 방금 눈앞에서 깨어난 생물에게 짓는 이름은 첫 유대다. 그래서
 * 환영 → **부화** → 명명(#42) 순서이고, 이 순서가 이 연출의 값어치 전부다.
 *
 * ## 왜 자동 진행이 아니라 탭인가
 *
 * 알을 **직접 두드려** 깨우게 한다. 가만히 보고 있으면 영상이고, 두드리면 참여다 — 애착의 시작은
 * "내가 했다"는 감각이다. 대신 기다리기만 해도 다음 단계로 넘어가게 해(자동 진행) 탭을 모르는
 * 사용자가 갇히지 않게 한다.
 *
 * ## 재생은 한 번뿐
 *
 * 완료 기준이 "재실행에선 반복 재생되지 않는다"이다. 부화는 **처음 만나는 순간**이라 두 번째부터는
 * 거짓이 된다. 별도 저장소를 두지 않는 이유: 이 씬은 온보딩 첫 스텝 안에만 있고 온보딩 자체가
 * [OnboardingStore]로 1회성이라, 저장소를 하나 더 두면 같은 사실의 진실이 둘이 된다.
 */
@Composable
internal fun ColumnScope.HatchSceneContent(onDone: () -> Unit) {
    val reduced = reduceMotion()
    // 모션 감축이면 부화 과정을 건너뛰고 **이미 깨어난** 상태로 시작한다 — 연출을 없애되
    // 서사(깨어난 동료를 만난다)는 남긴다. 단계별 흔들림도 함께 사라진다.
    var stage by remember { mutableIntStateOf(if (reduced) LAST_STAGE else 0) }

    // 기다리기만 해도 진행된다 — 탭을 모르는 사용자가 알 앞에서 갇히지 않게
    LaunchedEffect(stage, reduced) {
        if (reduced || stage >= LAST_STAGE) return@LaunchedEffect
        delay(AUTO_ADVANCE_MS)
        stage++
    }

    Text(
        text = stringResource(if (stage < LAST_STAGE) R.string.hatch_title else R.string.hatch_title_done),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        // 알 → 깨어남으로 문구가 바뀌는 순간이 서사의 전환점이라 낭독으로도 알려야 한다
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(NexusSpacing.sm))
    Text(
        text = stringResource(if (stage < LAST_STAGE) R.string.hatch_body else R.string.hatch_body_done),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(NexusSpacing.xl))

    val tapLabel = stringResource(R.string.hatch_tap_hint)
    CharacterComposer.CharacterSprite(
        state = "egg_$stage",
        modifier = Modifier
            .size(NexusSpacing.heroSprite)
            .align(Alignment.CenterHorizontally)
            // 두드릴 때마다 한 단계 — 마지막 컷에서는 더 진행하지 않는다
            .clickable(enabled = stage < LAST_STAGE) { stage++ }
            .semantics { if (stage < LAST_STAGE) contentDescription = tapLabel }
            .graphicsLayer {
                // 금이 갈수록 크게 흔들린다 — 진행이 그림 밖에서도 읽히게
                rotationZ = if (reduced) 0f else SHAKE_DEGREES * stage
            },
    )

    Spacer(Modifier.height(NexusSpacing.xl))
    if (stage >= LAST_STAGE) {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hatch_meet))
        }
    } else {
        Text(
            text = stringResource(R.string.hatch_tap_hint),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = HINT_ALPHA },
        )
    }
}

/** 알 → 금 → 갈라짐 → 깨어남. 인덱스가 곧 `character_egg_{n}_0` 이다. */
private const val LAST_STAGE = 3

/** 탭을 모르는 사용자를 위한 자동 진행 간격. 너무 짧으면 두드릴 기회가 없다. */
private const val AUTO_ADVANCE_MS = 1_400L

private const val SHAKE_DEGREES = 2.5f
private const val HINT_ALPHA = 0.7f
