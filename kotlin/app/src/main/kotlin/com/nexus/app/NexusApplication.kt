package com.nexus.app

import android.app.Application
import android.util.Log
import com.nexus.app.crash.CrashReporting
import com.nexus.app.diag.DiagnosticsCollector
import com.nexus.app.diag.LedgerIntegrityGuard
import com.nexus.app.telemetry.Telemetry
import com.nexus.core.FailureCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 앱 진입점 (#46) — 계측·크래시 수집 초기화(설정값 없으면 각각 no-op)만 담당한다.
 * 여기서 이벤트를 발화하면 안 된다: 워커·위젯 기동 콜드스타트도 이 onCreate를 지나므로
 * "앱 열림"류 신호가 백그라운드마다 집계된다 (#46 리뷰 F1 — 발화는 MainActivity에서).
 *
 * 원장 무결성 검사(#245)도 여기서 돈다 — 매 실행 1회. 시작 경로인 이유는 워커·화면과 달리
 * **모든 실행이 반드시 지나는 유일한 지점**이라 위반을 놓칠 창이 없다는 것이다.
 */
class NexusApplication : Application() {
    private companion object {
        const val TAG = "NexusApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this)
        CrashReporting.init(this)
        verifyLedgerIntegrity()
    }

    /**
     * 원장 불변식 검사 (#245) — **보고만 한다. 여기서는 던지지 않는다.**
     *
     * 던지지 않는 이유가 두 가지다. ① 이 검사는 비동기라 예외가 임의의 시점에 프로세스를 죽인다 —
     * 어느 화면에서 죽었는지가 매번 달라 디버깅에 오히려 방해가 된다. ② 디버그 도구(#245)에는
     * 위반을 **일부러 심는** 시드 버튼이 있는데, 시작에서 크래시하면 그 버튼을 누른 뒤 앱이
     * 켜지지 않아 되돌릴 '원장 삭제' 버튼에도 닿을 수 없다. 도구가 스스로를 막는 셈이다.
     *
     * 그래서 크래시는 개발자가 명시적으로 요청할 때만 한다(개발자 도구 카드의 무결성 검사 버튼).
     * 시작 경로는 매 실행 E-레벨 로그 + 실패 카운터 + 처리된-실패 신호를 남긴다 — 조용하지 않다.
     *
     * 시작을 막지 않도록 별 스코프에서 비동기로 돈다. 이미 박제된 행을 보는 일이라 결과가 몇백 ms
     * 늦어도 판정은 같고, `onCreate`를 Room 읽기로 붙잡으면 콜드스타트가 그만큼 밀린다(#261).
     */
    private fun verifyLedgerIntegrity() {
        CoroutineScope(Dispatchers.IO).launch {
            // report까지 runCatching 안에 둔다 — 이 스코프엔 예외 핸들러가 없어서, 밖에 두면
            // prefs·리포터 쪽 예외 하나가 시작 직후 프로세스를 죽인다("던지지 않는다"의 정반대).
            runCatching {
                LedgerIntegrityGuard.report(
                    this@NexusApplication,
                    DiagnosticsCollector.ledgerRows(this@NexusApplication),
                )
            }.onFailure { e ->
                // 원장을 못 읽은 것 자체가 신호다. 여기서 조용해지면 시작 경로 — 유일하게 항상
                // 도는 경로 — 가 손상된 DB를 아무 흔적 없이 지나친다.
                Log.w(TAG, "startup ledger integrity check failed", e)
                CrashReporting.recordHandledFailure(FailureCategory.LEDGER_DB)
            }
        }
    }
}
