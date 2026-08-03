package com.nexus.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 탭별 로딩 스켈레톤 (#268, E16-18) — 각 화면이 **실제로 그릴 것과 닮은 자리**.
 *
 * 형태가 닮았다는 건 "카드 몇 장이, 대략 어떤 높이로, 어떤 순서로" 온다는 뜻이다. 픽셀을 맞출
 * 필요는 없고 **덩어리 크기와 개수**가 맞으면 완료 순간의 레이아웃 점프가 사라진다. 반대로 형태를
 * 안 맞추면(중앙 스피너 하나) 로딩 중 화면 높이가 0이라 완료 순간 전부가 한꺼번에 밀려 들어온다.
 *
 * 카드 수를 실제와 맞추는 게 중요하다 — 스켈레톤이 두 장인데 콘텐츠가 네 장이면 점프가 그대로 남는다.
 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier) {
        // ① 히어로 — 캐릭터 + 대사 + 컨디션 게이지
        SkeletonCard {
            SkeletonBlock(height = SkeletonHeight.HERO)
            SkeletonBlock(widthFraction = 0.6f)
            SkeletonBlock(height = SkeletonHeight.GAUGE)
        }
        // ② 기세 ③ 주간 목표 ④ 오늘
        repeat(HOME_SUMMARY_CARDS) {
            SkeletonCard {
                SkeletonBlock(widthFraction = 0.45f, height = SkeletonHeight.TITLE)
                SkeletonBlock(widthFraction = 0.7f)
            }
        }
    }
}

@Composable
fun GrowthSkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier) {
        // ① 히어로 — 레벨 + 다음 레벨까지 게이지
        SkeletonCard {
            SkeletonBlock(widthFraction = 0.4f, height = SkeletonHeight.TITLE)
            SkeletonBlock(height = SkeletonHeight.GAUGE)
            SkeletonBlock(widthFraction = 0.55f)
        }
        // ② 능력치 ③ 장비 ④ 배지
        repeat(GROWTH_SECTION_CARDS) {
            SkeletonCard {
                SkeletonBlock(widthFraction = 0.35f, height = SkeletonHeight.TITLE)
                SkeletonBlock()
                SkeletonBlock(widthFraction = 0.8f)
            }
        }
    }
}

@Composable
fun ActivitySkeleton(modifier: Modifier = Modifier) {
    SkeletonScreen(modifier) {
        // ① 걸음 — 7일 막대 차트
        SkeletonCard {
            SkeletonBlock(widthFraction = 0.3f, height = SkeletonHeight.TITLE)
            SkeletonBlock(height = SkeletonHeight.CHART)
        }
        // ② 운동 세션 목록 — 행이 여러 개라 줄을 그만큼
        SkeletonCard {
            SkeletonBlock(widthFraction = 0.3f, height = SkeletonHeight.TITLE)
            // 세션 행은 실제 목록 행 높이로 — 얇은 선으로 두면 완료 순간 목록이 밀고 들어온다
            repeat(SESSION_ROWS) { SkeletonBlock(widthFraction = 0.85f, height = SkeletonHeight.ROW) }
        }
        // ③ 동기화
        SkeletonCard {
            SkeletonBlock(widthFraction = 0.3f, height = SkeletonHeight.TITLE)
            SkeletonBlock(widthFraction = 0.6f)
        }
    }
}

/** 히어로 아래 기세·주간목표·오늘·원정·다음목표·신선도 — 실제 스택과 맞춘다 (#268 AC ④). */
private const val HOME_SUMMARY_CARDS = 5

/** 히어로 아래 오늘XP·능력치·범위·장비·배지. */
private const val GROWTH_SECTION_CARDS = 5
private const val SESSION_ROWS = 3
