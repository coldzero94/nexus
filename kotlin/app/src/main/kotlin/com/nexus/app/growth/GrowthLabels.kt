package com.nexus.app.growth

import com.nexus.app.R
import com.nexus.core.ActivityType
import com.nexus.core.ClassAffinity
import com.nexus.core.Stat

/** core enum → 표시 문자열 리소스 매핑. 성장 계열 화면(#23·#24·#61)이 공유. */
internal fun ClassAffinity.labelRes(): Int = when (this) {
    ClassAffinity.ENDURANCE -> R.string.affinity_endurance
    ClassAffinity.AGILITY -> R.string.affinity_agility
    ClassAffinity.STRENGTH -> R.string.affinity_strength
    ClassAffinity.BALANCED -> R.string.affinity_balanced
}

internal fun ActivityType.labelRes(): Int = when (this) {
    ActivityType.WALKING -> R.string.activity_walking
    ActivityType.RUNNING -> R.string.activity_running
    ActivityType.STRENGTH -> R.string.activity_strength
}

internal fun Stat.labelRes(): Int = when (this) {
    Stat.ENDURANCE -> R.string.stat_endurance
    Stat.AGILITY -> R.string.stat_agility
    Stat.STRENGTH -> R.string.stat_strength
    Stat.RECOVERY -> R.string.stat_recovery
    Stat.FOCUS -> R.string.stat_focus
    Stat.AFFINITY -> R.string.stat_affinity
}
