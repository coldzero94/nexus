package com.nexus.app

import android.app.Application
import com.nexus.app.crash.CrashReporting
import com.nexus.app.telemetry.Telemetry

/**
 * 앱 진입점 (#46) — 계측·크래시 수집 초기화(설정값 없으면 각각 no-op)만 담당한다.
 * 여기서 이벤트를 발화하면 안 된다: 워커·위젯 기동 콜드스타트도 이 onCreate를 지나므로
 * "앱 열림"류 신호가 백그라운드마다 집계된다 (#46 리뷰 F1 — 발화는 MainActivity에서).
 */
class NexusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this)
        CrashReporting.init(this)
    }
}
