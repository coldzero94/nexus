package com.nexus.app.health

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 관측된 현재 기기 온디바이스 소스의 영속 집합 (#205).
 *
 * ## 왜 영속화가 필요한가
 *
 * 관측을 **읽은 배치 안에서만** 모으면 신뢰 등급이 읽기 창에 따라 달라진다. 호출부의 창이 서로 다르다:
 * 활동 탭 7일, 동기화 워커 7일, 홈 28일, 성장 28일 — 그리고 그중 셋이 원장에 지급한다.
 *
 * 자기 메타로 스스로를 증명하는 세션은 어느 창에서나 같지만, **같은 패키지의 형제 세션이 근거를
 * 대주는 경우**는 갈린다. 28일 창에서는 열흘 전 형제가 있어 Tier B로 지급되고, 7일 창에서는 같은
 * 세션이 "Tier C · XP 제외"로 보인다. 7일 경로만 도는 사용자는 영구히 지급받지 못하고, 28일이
 * 지나면 어느 창에도 안 들어와 **복구 창이 닫힌다**.
 *
 * 그래서 관측을 누적한다 — 한 번 본 소스는 다음 배치에도 유효하다.
 *
 * ## 기기 신원이 바뀌면 버린다
 *
 * 백업 복원·기기 이전으로 다른 폰에서 열리면 이전 기기의 관측은 근거가 아니다. 제조사·모델을 함께
 * 저장하고 달라지면 비운다 — 관측 오염이 기기를 넘어 따라다니지 않게 하는 안전장치다.
 */
class DeviceSourceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 누적 관측. 기기 신원이 바뀌었으면 빈 집합(그리고 다음 기록에서 새로 쌓인다). */
    val sources: Set<String>
        get() = if (prefs.getString(KEY_DEVICE, null) == deviceKey()) {
            prefs.getStringSet(KEY_SOURCES, emptySet()).orEmpty()
        } else {
            emptySet()
        }

    /**
     * 관측을 누적한다(합집합). 새로 늘어난 게 있으면 로그를 남긴다 — 이 기능이 **실기기에서 실제로
     * 동작하는지** 확인할 수단이 그것뿐이다(패키지명은 건강 파생 값이 아니라 로그에 남겨도 된다).
     */
    fun record(observed: Set<String>) {
        if (observed.isEmpty()) return
        val merged = sources + observed
        if (merged == sources && prefs.getString(KEY_DEVICE, null) == deviceKey()) return

        Log.i(TAG, "on-device sources: ${merged.joinToString()}")
        // apply()가 아니라 commit()이다. 이 값은 **다음 배치의 신뢰 등급을 결정**하고, 유실되면
        // 등급이 다시 읽기 창에 따라 달라진다(이 저장소의 존재 이유가 그것이다). 호출부가 이미
        // IO 컨텍스트의 suspend 경로이고 쓰는 양이 패키지명 몇 개라, 동기 쓰기의 비용이 무의미하다.
        prefs.edit()
            .putStringSet(KEY_SOURCES, merged)
            .putString(KEY_DEVICE, deviceKey())
            .commit()
    }

    private fun deviceKey() = "${Build.MANUFACTURER}/${Build.MODEL}"

    companion object {
        private const val TAG = "DeviceSource"
        const val PREFS = "nexus_device_sources"
        private const val KEY_SOURCES = "observed_sources"
        private const val KEY_DEVICE = "device_key"
    }
}
