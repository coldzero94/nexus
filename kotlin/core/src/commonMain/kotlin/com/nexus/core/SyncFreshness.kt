package com.nexus.core

/**
 * 데이터 신선도 (#221, E14-11) — 마지막 반영 시각을 표시 등급으로 바꾼다.
 *
 * Health Connect는 준실시간이 아니다(삼성헬스 → HC 전파에 30~60분). 워커가 실패·지연돼도 화면은
 * 경고 없이 오래된 값을 보여주므로, 사용자가 "새 기록이 왜 안 보이지"를 스스로 진단할 근거가 없다.
 * 그래서 경과 시간을 드러내되 **꾸짖지 않는 톤**으로 안내한다 — 지연은 사용자 잘못이 아니다.
 */
sealed interface SyncFreshness {
    /** 한 번도 반영된 적 없음 — 신선도 대신 첫 연결 안내가 맞다(#213에 위임). */
    data object Never : SyncFreshness

    /**
     * 마지막 반영 이후 [minutesAgo]분 경과. [delayed]면 지연 안내 한 줄을 함께 띄운다.
     * 음수는 만들지 않는다 — 시간대 변경·시계 보정으로 미래 시각이 저장될 수 있어 0으로 바닥을 친다.
     */
    data class Synced(val minutesAgo: Int) : SyncFreshness {
        init {
            // 타입이 불변식을 지고 있게 — evaluate만 클램프하면 다른 호출부가 음수를 만들 수 있다
            require(minutesAgo >= 0) { "minutesAgo must not be negative: $minutesAgo" }
        }

        val delayed: Boolean get() = minutesAgo >= DELAY_NOTICE_MINUTES

        /** 시간 단위 표기용 — 60분 미만이면 0. */
        val hoursAgo: Int get() = minutesAgo / MINUTES_PER_HOUR
    }

    companion object {
        /** 지연 안내 임계(분) = 3시간. 15분 주기 워커가 12회 연속 밀린 셈이라 '평소'로 보기 어렵다. */
        const val DELAY_NOTICE_MINUTES: Int = 180

        private const val MINUTES_PER_HOUR = 60
        private const val MILLIS_PER_MINUTE = 60_000L

        /**
         * [lastSyncEpochMillis] == 0(미동기화)은 [Never], 그 외는 경과 분을 담은 [Synced].
         * 경과가 Int 범위를 넘는 값(손상된 prefs 등)도 클램프해 표시가 깨지지 않게 한다.
         */
        fun evaluate(lastSyncEpochMillis: Long, nowEpochMillis: Long): SyncFreshness {
            if (lastSyncEpochMillis <= 0L) return Never
            val elapsedMillis = (nowEpochMillis - lastSyncEpochMillis).coerceAtLeast(0L)
            val minutes = (elapsedMillis / MILLIS_PER_MINUTE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return Synced(minutes)
        }
    }
}
