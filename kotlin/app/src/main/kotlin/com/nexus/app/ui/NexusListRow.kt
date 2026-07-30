package com.nexus.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * 재사용 리스트 행 (#260, E16-10) — **라벨 + 우측 정렬 값**의 단일 규격.
 *
 * ## 왜 필요했나
 *
 * 활동 탭의 세션 목록이 카드도 정렬도 구분선도 없는 맨 `Column { Text; Text; Row }`였다. 4탭 중
 * 유일하게 그래서 "텍스트 덤프"로 읽혔고, 값이 어디에 정렬되는지가 행마다 달랐다. 행 규격을
 * 컴포넌트로 만들면 다음에 목록이 생길 때 같은 결정을 다시 하지 않는다.
 *
 * ## 라벨·보조·값을 한 노드로 묶는다 (`docs/A11Y-TALKBACK.md`)
 *
 * 값을 오른쪽 열로 옮기면 눈으로 비교하기는 좋아지지만, 시맨틱 트리에서는 **라벨과 값이 별개
 * 노드로 흩어진다** — TalkBack이 `"11월 15일 09:33"`과 `"42분"`을 따로 읽고, 배치 순서 때문에
 * 값이 맨 뒤에 맥락 없이 나온다. A11Y 문서가 명시적으로 금지하는 형태다.
 *
 * 그래서 라벨 행만 `mergeDescendants`로 묶는다. [content]는 **묶음 밖**에 둔다 — 신뢰 등급 칩처럼
 * 탭 가능한 요소가 들어오는 슬롯이라(#222) 함께 묶으면 그 동작이 흡수된다.
 *
 * @param label 행의 주 라벨. 목록에서 눈이 먼저 닿는 곳이라 SemiBold로 앵커를 준다.
 * @param supporting 라벨 아래 보조 설명(없으면 한 줄 행).
 * @param value 우측 정렬 짧은 값 — 스타일은 여기서 고정한다. 라벨보다 낮은 위계로 둬야
 *   둘이 같은 무게로 보이지 않는다.
 * @param content 라벨 묶음 **아래**에 붙는 추가 본문 — 등급 칩 줄처럼 자체 시맨틱이 필요할 때만.
 */
@Composable
fun NexusListRow(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    value: String? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = NexusSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
    ) {
        Row(
            // 한 문장으로 들리게 묶는다 — 라벨과 값이 끊겨 들리면 값이 무엇의 값인지 알 수 없다
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(NexusSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                // weight로 라벨 열이 남는 폭을 먹어야 값이 진짜 오른쪽 끝에 붙는다.
                // SpaceBetween으로 하면 라벨이 짧을 때 값이 가운데로 떠서 열이 안 맞는다.
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NexusSpacing.xs),
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (value != null) {
                // 값은 폭을 주장하지 않는다 — 긴 값이 와도 라벨 열을 0으로 짜부시지 않게 한 줄로 자른다
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content?.invoke(this)
    }
}

/** 구분선 테스트 태그 — "마지막 뒤에는 없다"를 개수로 셀 수 있게 (구분선은 시맨틱이 없다). */
const val LIST_DIVIDER_TAG = "nexus_list_divider"

/**
 * 행 사이에만 얇은 구분선을 넣는 목록 (#260).
 *
 * 마지막 행 뒤에는 넣지 않는다 — 컨테이너 아래 여백과 겹쳐 두 겹 경계로 보인다. 그 판단을
 * 호출부마다 반복하면 어딘가는 틀리므로(그리고 눈에 잘 안 띄므로) 여기서 한 번에 정한다.
 *
 * @param items 그릴 항목. 비어 있으면 아무것도 그리지 않는다 — 빈 상태 문구는 호출부 책임이다
 *   (목록 컴포넌트가 "없음"까지 정하면 화면마다 다른 문구를 못 쓴다).
 */
@Composable
fun <T> NexusDividedList(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            row(item)
            if (index != items.lastIndex) HorizontalDivider(Modifier.testTag(LIST_DIVIDER_TAG))
        }
    }
}
