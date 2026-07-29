package com.nexus.app.health

import android.content.Context
import com.nexus.app.notify.NotificationSettings
import com.nexus.app.notify.ReminderWorker
import com.nexus.core.SyncFreshness

/**
 * 백그라운드 작업 자가복구 (#237, E15-9) — 포그라운드 복귀마다 배관을 다시 확인한다.
 *
 * 주기 워커는 온보딩 연결 성공 **한 지점**에서만 등록됐다. `KEEP`이라 멱등이긴 하지만, 작업이
 * 사라지는 경로는 여럿이다: 앱 데이터 지우기, WorkManager DB 가지치기, 배터리 최적화의 강제 종료,
 * 재부팅 실패. 그러면 재등록할 지점이 없어 **동기화가 무흔적 사망**한다.
 *
 * 이게 특히 위험한 이유는 사용자에게 신호가 없다는 것이다 — 화면은 그냥 옛 숫자를 계속 보여주고,
 * 14일 알파 내내 데이터가 멈춘 채로 게이트 판정이 내려간다. 오프디바이스 검증이 불가능한 구조라
 * (로컬 온리) 우리가 알아챌 방법도 없다.
 *
 * 그래서 복구를 **조용히** 한다: UI에 아무 말도 하지 않고(실시간 약속 금지, 불변식 ⑤) 배관만 세운다.
 * 사용자에게 보이는 신선도 표시는 #221이 담당한다.
 */
object SyncSelfHeal {
    /**
     * 신선도 워치독 임계(분) — 이보다 오래됐으면 1회성 동기화를 넣는다.
     *
     * 주기 워커가 15분이라 90분은 **6주기를 연속으로 놓친** 상태다. 도즈로 한두 번 밀리는 건 정상이니
     * 그 정도는 건드리지 않고, 확실히 이상한 구간에서만 개입한다.
     */
    const val STALE_THRESHOLD_MINUTES = 90

    /**
     * 연결된 상태에서 매 포그라운드 진입 시 호출한다.
     *
     * @param connected Health Connect 연결 여부. 미연결이면 워커가 할 일이 없어 아무것도 하지 않는다.
     */
    fun onForeground(context: Context, connected: Boolean) {
        if (!connected) return

        // ① 주기 워커 재등록 — KEEP이라 살아 있으면 그대로, 사라졌으면 되살아난다
        HealthSyncWorker.enqueuePeriodic(context)

        // ② 알림도 같은 취약점을 갖는다 — 켜 둔 사용자의 리마인더가 조용히 죽어 있으면 안 된다
        if (NotificationSettings(context).enabled) {
            ReminderWorker.enqueuePeriodic(context)
        }

        // ③ 신선도 워치독 — 주기 워커를 되살려도 다음 주기까지 최대 15분이 남는다.
        //    이미 한참 멈춰 있었다면 그 15분을 더 기다릴 이유가 없다.
        if (isStale(TokenStore(context).lastSyncEpochMillis, System.currentTimeMillis())) {
            HealthSyncWorker.enqueueNow(context)
        }
    }

    /**
     * 워치독 발동 여부 — **한 번도 동기화된 적 없으면(0) 발동한다**(연결됐는데 비어 있는 상태).
     * 그 외에는 경과가 임계를 넘겼을 때만.
     */
    fun isStale(lastSyncEpochMillis: Long, nowEpochMillis: Long): Boolean =
        when (val freshness = SyncFreshness.evaluate(lastSyncEpochMillis, nowEpochMillis)) {
            SyncFreshness.Never -> true
            is SyncFreshness.Synced -> freshness.minutesAgo >= STALE_THRESHOLD_MINUTES
        }
}
