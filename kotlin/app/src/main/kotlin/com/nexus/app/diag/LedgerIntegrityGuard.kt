package com.nexus.app.diag

import android.content.Context
import android.util.Log
import com.nexus.app.BuildConfig
import com.nexus.app.crash.CrashReporting
import com.nexus.core.FailureCategory
import com.nexus.core.LedgerIntegrity
import com.nexus.core.LedgerRow
import com.nexus.core.LedgerViolation

/**
 * 원장 무결성 런타임 가드 (#245, E15-12) — 검사는 순수하고, **대응만 호출 지점에 따라 다르다**.
 *
 * ## 왜 두 진입점인가
 *
 * [report]는 절대 던지지 않고, [verifyOrCrash]는 디버그에서 던진다. **자동 경로는 전부 [report]를
 * 쓴다** — 동기화(매 15분)와 앱 시작(매 실행)이 그렇다. 던지는 건 개발자가 개발자 도구에서
 * 명시적으로 요청할 때만이다.
 *
 * 자동 경로에서 던지지 않는 이유가 경로마다 다르다:
 * - **동기화**: `doWork`가 `IllegalStateException`을 잡아 `Result.retry()`로 바꾼다(설계대로).
 *   거기서 던지면 크래시가 아니라 **조용한 무한 재시도**가 되어 의도가 정확히 반대로 뒤집힌다.
 * - **앱 시작**: 검사가 비동기라 예외가 임의 시점에 프로세스를 죽인다. 게다가 디버그 도구에는
 *   위반을 일부러 심는 시드 버튼이 있어, 시작에서 죽으면 되돌릴 '원장 삭제' 버튼에 닿을 수 없다.
 *
 * ## 신호는 전용 슬롯에, 원격은 변화가 있을 때만
 *
 * 무결성 위반은 [IntegrityMarkerStore]에 남긴다 — `TokenStore`의 동기화 실패 슬롯을 쓰지 않는다.
 * 그 슬롯은 하나뿐이라 무결성으로 덮으면 `SYNC_PERMISSION`(사용자가 고칠 수 있는 유일한 분류)이
 * 가려지고, 동기화 성공 시 `clearFailure()`가 방금 쓴 신호까지 지운다. 원격 보고도 **위반 집합이
 * 바뀔 때만** 한다 — 무결성 위반은 회복되지 않으므로 매번 보내면 무료 티어를 혼자 태운다.
 * 자세한 근거는 [IntegrityMarkerStore] KDoc.
 *
 * ## 릴리스는 사용자를 벌주지 않는다
 *
 * 릴리스에서는 어느 진입점도 던지지 않는다. 원장이 깨진 건 사용자 잘못이 아니고 크래시가 원장을
 * 고쳐주지도 않는다 — 앱은 계속 돌고 [FailureCategory.LEDGER_INTEGRITY] **발생 사실만** 남는다.
 * 위반 종류·개수·시퀀스는 원격으로 나가지 않는다(불변식 ②).
 *
 * ## 고치지 않는다
 *
 * 위반을 발견해도 행을 지우거나 보정 이벤트를 넣지 않는다. append-only가 이 원장의 유일한 안전
 * 장치이고, "자동으로 고치는 코드"가 곧 다음 오염의 경로다. 발견과 보고까지만 한다.
 */
object LedgerIntegrityGuard {
    private const val TAG = "LedgerIntegrity"

    /**
     * 검사하고 위반을 기록한다 — **던지지 않는다**. 동기화·앱 시작처럼 자동으로 도는 경로용.
     *
     * @return 위반 종류(빈 집합 = 정상). 디버그 화면이 그대로 표시한다.
     */
    fun report(context: Context, rows: List<LedgerRow>): Set<LedgerViolation> {
        val violations = LedgerIntegrity.check(rows)
        val shouldNotifyRemote = IntegrityMarkerStore(context).record(violations)
        if (violations.isEmpty()) return violations

        // 위반 이름은 enum 상수 — 수치가 없어 로그에 남겨도 안전하다(불변식 ②).
        // 로그는 매번 남긴다(로컬 비용 0). 원격만 변화가 있을 때로 제한한다.
        Log.e(TAG, "원장 불변식 위반: ${violations.joinToString { it.name }}")
        if (shouldNotifyRemote) CrashReporting.recordHandledFailure(FailureCategory.LEDGER_INTEGRITY)
        return violations
    }

    /**
     * [report] + **디버그 빌드에서는 즉시 크래시** — 개발자 도구의 명시적 검사 버튼 전용.
     *
     * 원장 오염은 append-only라 되돌릴 수 없다. 위반을 눈으로 확인하고 스택까지 받고 싶은 순간이
     * 있고, 그때 던지는 건 개발자가 요청한 동작이므로 안전하다. 자동 경로는 [report]를 쓴다.
     *
     * @throws IllegalStateException 디버그 빌드에서 위반이 있을 때.
     */
    fun verifyOrCrash(context: Context, rows: List<LedgerRow>): Set<LedgerViolation> {
        val violations = report(context, rows)
        check(violations.isEmpty() || !BuildConfig.DEBUG) {
            "원장 불변식 위반(${violations.joinToString { it.name }}) — 원장은 되돌릴 수 없다"
        }
        return violations
    }
}
