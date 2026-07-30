package com.nexus.app.diag

import android.content.Context
import com.nexus.app.BuildConfig
import com.nexus.core.LedgerViolation

/**
 * 원장 무결성 위반 마커 (#245) — 실패 카운터와 **별도 저장소**를 쓰는 이유가 두 가지다.
 *
 * ## ① 동기화 실패 슬롯을 덮지 않기 위해
 *
 * `TokenStore`의 마지막 실패 분류는 슬롯이 하나이고, `recordFailure`는 분류가 바뀌면 연속 횟수를
 * 1로 되돌린다. 무결성 위반을 거기 쓰면 매 실행마다 `SYNC_PERMISSION`을 지운다 — 진단 순서상
 * 가장 먼저 보는 값이고, **사용자가 직접 고칠 수 있는 유일한 분류**인데 그게 가려진다.
 * 게다가 동기화가 성공하면 `clearFailure()`가 불려서, 방금 기록한 무결성 신호까지 함께 지워진다.
 *
 * ## ② 같은 위반을 무한히 보고하지 않기 위해
 *
 * 다른 실패 분류는 전부 일시적이다(IO·원격·권한·DB) — 재시도로 회복한다. 무결성 위반은 **회복되지
 * 않는다**: 원장은 append-only이고 우리는 자동 수정을 하지 않기로 했다. 그래서 매 실행 + 15분마다
 * 같은 위반을 보고하면 한 대의 기기가 월 3,000건을 만들고, Sentry 무료 티어(월 5천) 하나를
 * 혼자 태운다 — 진짜 크래시가 묻힌다.
 *
 * `prefsName`을 받는 이유는 테스트 격리다. `NexusApplication.onCreate`의 시작 검사가 **모든 Robolectric
 * 앱 부팅마다** 비동기로 이 저장소에 쓰므로, 테스트가 프로덕션과 같은 이름을 쓰면 앱의 쓰기가 테스트의
 * `@Before` 정리 뒤에 도착해 간헐 실패한다(실제로 전체 스위트에서만 재현됐다). `BadgeProgressStore`가
 * 같은 이유로 같은 형태를 쓴다.
 *
 * 그래서 **(위반 집합, 빌드) 조합이 바뀔 때만** 원격으로 보낸다. 같은 위반이 그대로면 로컬 마커만
 * 유지된다(진단 스냅샷은 마커를 읽으므로 정보는 잃지 않는다). 빌드를 키에 넣는 이유는 우리가
 * 고친 버전을 올렸을 때 "아직 남아 있다"를 다시 듣고 싶기 때문이다.
 */
internal class IntegrityMarkerStore(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** 마지막으로 관측한 위반 집합. 빈 집합이면 정상(또는 아직 검사 전). */
    val lastViolations: Set<String>
        get() = prefs.getStringSet(KEY_VIOLATIONS, emptySet()).orEmpty()

    /**
     * 위반 집합을 기록하고 **원격 보고가 필요한지** 반환한다.
     *
     * @return (위반 집합, 빌드)가 직전과 다르면 true. 같으면 false — 로컬 마커만 갱신된다.
     */
    fun record(violations: Set<LedgerViolation>): Boolean {
        val names = violations.mapTo(mutableSetOf()) { it.name }
        val changed = names != lastViolations || prefs.getString(KEY_BUILD, null) != buildKey()

        // apply()가 아니라 commit()이다. 이 값이 **다음 호출의 원격 보고 여부**를 결정하므로 유실되면
        // 같은 위반을 반복 보고해(무료 티어 소진) 이 저장소의 존재 이유가 사라진다. 실제로 전체
        // 스위트에서만 재현되는 경합이 있었다 — apply()는 루퍼가 도는 시점에 반영된다.
        prefs.edit()
            .putStringSet(KEY_VIOLATIONS, names)
            .putString(KEY_BUILD, buildKey())
            .commit()
        return changed && names.isNotEmpty()
    }

    /** 마커 삭제 — 원장을 비운 뒤(디버그 리셋)처럼 과거 관측이 무의미해질 때. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun buildKey() = "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"

    internal companion object {
        const val PREFS = "nexus_ledger_integrity"
        private const val KEY_VIOLATIONS = "last_violations"
        private const val KEY_BUILD = "last_build"
    }
}
