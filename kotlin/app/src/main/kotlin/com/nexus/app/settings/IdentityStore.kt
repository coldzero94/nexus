package com.nexus.app.settings

import android.content.Context
import com.nexus.core.CharacterName

/**
 * 캐릭터 정체성 (#216, E14-6) — 사용자가 지어준 이름의 로컬 영속. 이름을 짓는 행위보다 매일 곳곳에서
 * 호명되는 반복이 애착을 만든다(Finch 패턴). 저장 시 [CharacterName] 규칙으로 정규화·검증한다.
 *
 * **PII 위생**: 이름은 사용자 자유 입력이라 **텔레메트리·크래시 페이로드에 절대 싣지 않고**, 앱이
 * 서버로 전송하지도 않는다(표시 전용). 단 SharedPreferences라 Android 자동 백업(사용자 본인 계정,
 * `backup_rules.xml`) 표면에는 포함된다 — 건강 파생값이 아니라 설정이므로 허용.
 */
class IdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 저장된 이름(표시 가능한 형태) — 미설정·손상 시 null이라 호출측이 무명 카피로 폴백. */
    val name: String?
        get() = CharacterName.displayOrNull(prefs.getString(KEY_NAME, null))

    /** 이름 저장 — 유효하면 정규화해 저장하고 true, 무효(빈값·공백·초과)면 저장하지 않고 false. */
    fun setName(raw: String): Boolean {
        if (!CharacterName.isValid(raw)) return false
        prefs.edit().putString(KEY_NAME, CharacterName.normalize(raw)).apply()
        return true
    }

    /** 이름 지우기 — 이후 카피는 무명 폴백으로 돌아간다. */
    fun clear() {
        prefs.edit().remove(KEY_NAME).apply()
    }

    companion object {
        private const val PREFS = "nexus_identity"
        private const val KEY_NAME = "character_name"
    }
}
