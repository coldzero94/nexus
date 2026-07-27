package com.nexus.app.ui

import androidx.annotation.DrawableRes
import com.nexus.app.R

/** 탭 아이콘 쌍 (#255, E16-5) — 미선택은 [outline], 선택은 [filled]. */
data class TabIcon(@DrawableRes val outline: Int, @DrawableRes val filled: Int)

/**
 * 앱 아이콘 단일 접근점 (#255) — R.drawable 리터럴이 화면에 흩어지지 않게 한다. tint·색은
 * 컴포저블(NavigationBar 등)이 colorScheme 토큰으로 주입하므로 아이콘은 형태만 담는다.
 */
object NexusIcons {
    val home = TabIcon(R.drawable.ic_tab_home_outline, R.drawable.ic_tab_home_filled)
    val activity = TabIcon(R.drawable.ic_tab_activity_outline, R.drawable.ic_tab_activity_filled)
    val growth = TabIcon(R.drawable.ic_tab_growth_outline, R.drawable.ic_tab_growth_filled)
    val settings = TabIcon(R.drawable.ic_tab_settings_outline, R.drawable.ic_tab_settings_filled)

    // 개념 아이콘 (#265, E16-15) — 반복 도메인 개념의 라인 아이콘. 카피 속 이모지(⚡🔥☀️…)를 대체해
    // 시각 언어를 통일한다. tint는 소비 컴포저블이 colorScheme 토큰으로 주입(하드코딩 색 금지).
    @DrawableRes val steps = R.drawable.ic_concept_steps

    @DrawableRes val exercise = R.drawable.ic_concept_exercise

    @DrawableRes val condition = R.drawable.ic_concept_condition

    @DrawableRes val level = R.drawable.ic_concept_level

    @DrawableRes val xp = R.drawable.ic_concept_xp

    @DrawableRes val goal = R.drawable.ic_concept_goal

    @DrawableRes val streak = R.drawable.ic_concept_streak

    @DrawableRes val expedition = R.drawable.ic_concept_expedition

    @DrawableRes val energy = R.drawable.ic_concept_energy
}
