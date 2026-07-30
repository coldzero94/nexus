package com.nexus.app.growth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.app.R
import com.nexus.app.character.CharacterAssets
import com.nexus.app.ui.NexusSpacing

/**
 * 배지 한 줄 (#266, E16-16) — 글리프 + 이름 + 설명, 획득/미획득 시각 상태.
 *
 * ## 왜 아이콘인가
 *
 * 배지가 이름+설명 텍스트로만 렌더돼 성취가 '따는 맛'이 없었다. 목록을 훑을 때 **무엇을 땄고 무엇이
 * 남았는지**가 한눈에 안 들어오는 게 실질 문제다 — 텍스트 두 줄씩 다섯 개는 다 읽어야 알 수 있다.
 *
 * ## 획득/미획득을 세 채널로 말한다
 *
 * 1. **형태** — 채운 원반 vs 빈 원반. 색을 못 봐도 "안이 비었다"는 보인다.
 * 2. **색** — 획득은 브랜드 컨테이너, 미획득은 윤곽·저채도.
 * 3. **텍스트** — 이름 뒤 '잠김', 낭독에 '획득함'/'미획득'.
 *
 * 형태 채널이 실제로 존재하려면 **테두리가 보여야** 한다. 처음엔 `outlineVariant`로 그렸는데 카드
 * 서피스 대비 1.53:1이라(라이트) 링이 사실상 안 보였고, 채운 쪽도 `primaryContainer`가 카드와
 * 1.16:1이어서 원반 경계가 없었다 — 즉 "채움 vs 윤곽"이라는 안전장치가 라이트 테마에 **없었다**.
 * 지금은 양쪽 다 링을 두르고(미획득 `outline`, 획득 `primary`) 3:1을 넘긴다. `BadgeGlyphContrastTest`가
 * 고정한다 — `docs/DESIGN.md §5`의 비텍스트 3:1 기준이 이 컴포넌트에도 적용된다.
 *
 * ## 접근성
 *
 * 행을 한 노드로 묶어 "첫걸음 · 획득함. 함께한 첫 활동을 기록했어요"로 읽게 한다. 묶지 않으면
 * 글리프(장식)·이름·설명이 흩어져 훑을 때 상태와 이름이 이어지지 않는다(#224에서 정한 규칙).
 */
@Composable
internal fun BadgeGlyphRow(
    name: String,
    description: String,
    icon: String?,
    earned: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (earned) R.string.a11y_badge_earned else R.string.a11y_badge_locked,
        name,
        description,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(NexusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeGlyph(icon = icon, earned = earned)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs)) {
            Text(
                text = if (earned) name else stringResource(R.string.growth_badge_locked, name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (earned) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 글리프 원반.
 *
 * `border`/`background`를 쓰고 직접 `drawCircle`하지 않는다 — 처음엔 `drawBehind`로 그렸는데
 * `DrawScope` 단위가 px이라 `Stroke(3f)`가 밀도 3배 기기에서 1dp가 됐다. 형태 채널이 되어야 할
 * 링이 헤어라인으로 사라진 것이고, 저장소가 이미 같은 값을 치른 함정이다
 * (`docs/DESIGN.md §5`: "갭은 dp — raw px은 3배 밀도에서 서브픽셀로 사라진다").
 *
 * 미획득 글리프에만 알파를 건다. 링에는 걸지 않는다 — 링이 흐려지면 "빈 원반"이라는 형태 신호가
 * 약해져, 색을 못 보는 경우에 남는 채널이 사라진다.
 */
@Composable
private fun BadgeGlyph(icon: String?, earned: Boolean) {
    val context = LocalContext.current
    val resId = remember(icon) { CharacterAssets(context).badgeIconResIdOrNull(icon) }
    val scheme = MaterialTheme.colorScheme

    val disc = if (earned) {
        Modifier
            .background(scheme.primaryContainer, CircleShape)
            // 채운 원반도 링을 두른다 — primaryContainer는 카드 서피스와 1.16:1이라
            // 링이 없으면 '채움'과 '윤곽'이 같은 모양으로 보인다
            .border(RING.dp, scheme.primary, CircleShape)
    } else {
        Modifier.border(RING.dp, scheme.outline, CircleShape)
    }

    Box(Modifier.size(DISC.dp).then(disc), contentAlignment = Alignment.Center) {
        // 드로어블을 못 찾아도 Box는 남긴다 — 원반만 빠지면 그 행의 텍스트가 왼쪽으로 밀려 열이 깨진다
        if (resId != null) {
            Icon(
                painter = painterResource(resId),
                // 장식이다 — 상태·이름은 행 전체 contentDescription이 갖고 있다
                contentDescription = null,
                tint = if (earned) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(GLYPH.dp).alpha(if (earned) 1f else LOCKED_GLYPH_ALPHA),
            )
        }
    }
}

/** 원반 지름 — 이름 두 줄 높이 안에 들어가면서 글리프가 알아볼 만한 크기. */
private const val DISC = 40

/** 글리프 크기 — 원반 안에 여백이 남아 '메달'로 읽히게. */
private const val GLYPH = 22

/** 원반 링 두께(dp). 얇으면 형태 신호가 죽고 굵으면 채움처럼 보인다. */
private const val RING = 2

/**
 * 미획득 글리프 감쇠 — '아직'이지 '없음'이 아니라서 완전히 지우지 않는다(무처벌 톤).
 *
 * 0.45로 시작했는데 카드 서피스 대비 2.21:1이라 비텍스트 3:1에 못 미쳤다. 0.7이면 3.85:1로
 * 기준을 넘으면서도 획득 글리프(13:1)와 확실히 구분된다.
 */
internal const val LOCKED_GLYPH_ALPHA = 0.7f
