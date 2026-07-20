package com.nexus.app

import android.app.Application
import com.nexus.app.telemetry.Telemetry
import com.nexus.app.telemetry.TelemetryEvent

/** 앱 진입점 (#46) — 계측 초기화(앱 ID 없으면 no-op)만 담당한다. */
class NexusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this)
        // 프로세스 시작 = 앱 열림 1회 — Activity 재생성(회전)으로 중복 집계되지 않는 위치
        Telemetry.record(TelemetryEvent.APP_OPENED)
    }
}
