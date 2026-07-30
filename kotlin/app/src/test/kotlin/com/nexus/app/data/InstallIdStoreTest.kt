package com.nexus.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 승계 앵커 (#240) — 계정 연결 시 **기존 원장을 누구에게 귀속할지**의 유일한 근거.
 *
 * BACKEND §1이 경고하는 "나중에 추가하면 소급 보강 불가"의 사례다: 앵커 없이 알파 테스터가 재설치·
 * 기기 이전을 하면, 이후 계정을 연결할 때 이전 원장의 소유자를 알 수 없다. 그래서 값의 **안정성**과
 * **복원 우선순위**가 계약이다.
 */
@RunWith(RobolectricTestRunner::class)
class InstallIdStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun store() = InstallIdStore(context)

    @Test
    fun `첫 호출에 발급되고 v4 UUID 형식이다`() {
        val id = store().installId

        assertTrue(id.isNotBlank())
        // 형식이 어긋나면 서버 연결 시 파싱·인덱싱이 깨진다
        assertEquals(id, UUID.fromString(id).toString())
    }

    @Test
    fun `재실행에도 같은 값 — 새 인스턴스가 prefs에서 읽는다`() {
        val first = store().installId
        val second = InstallIdStore(context).installId

        assertEquals(first, second, "앵커가 흔들리면 원장 귀속이 깨진다")
    }

    @Test
    fun `반복 조회가 값을 바꾸지 않는다`() {
        val s = store()
        assertEquals(s.installId, s.installId)
    }

    @Test
    fun `백업 복원은 로컬 값을 대체한다`() {
        val local = store().installId
        val restored = UUID.randomUUID().toString()

        store().adoptFromBackup(restored)

        assertEquals(restored, store().installId, "복원의 목적은 이전 설치의 연속 — 로컬 값을 지키면 승계가 깨진다")
        assertNotEquals(local, store().installId)
    }

    @Test
    fun `구버전 백업은 앵커를 건드리지 않는다`() {
        // v1 파일엔 필드가 없다(null) — 이때 로컬 값을 지워버리면 앵커를 잃는다
        val local = store().installId

        store().adoptFromBackup(null)
        assertEquals(local, store().installId)

        store().adoptFromBackup("")
        assertEquals(local, store().installId)
    }
}
