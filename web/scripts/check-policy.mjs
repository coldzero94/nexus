/**
 * 정책 페이지 정합 검사 (#52, E8-7).
 *
 * ## 왜 스크립트인가
 *
 * Play 심사는 이 페이지를 **Data safety 폼·Health Connect 권한 화면과 대조**한다(STACK.md §2).
 * 셋 중 하나라도 어긋나면 Health apps declaration이 반려되고, 거절 루프는 1회당 최대 7일이다.
 * 그런데 어긋나는 순간은 조용하다 — 앱에 권한 한 줄을 더한 사람이 웹 페이지를 고칠 이유가 없고,
 * 페이지는 여전히 잘 뜬다. 그래서 **매니페스트를 진실로 삼아** 페이지가 그걸 다 설명하는지 센다.
 *
 * ## 무엇을 못 잡는가
 *
 * 문장이 옳은지는 못 잡는다 — "심박을 읽습니다"라고 적혀만 있으면 통과한다. 잡는 건 **누락**이다:
 * 새 권한을 선언하고 페이지에 안 적은 경우, 그리고 페이지에만 있고 앱엔 없는 유령 권한.
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const manifestPath = resolve(here, '../../kotlin/app/src/main/AndroidManifest.xml')
const policyPath = resolve(here, '../dist/privacy/index.html')

/** 매니페스트 권한 → 정책 페이지에 반드시 등장해야 하는 한국어 표현. */
const REQUIRED_PHRASE = {
  READ_STEPS: '걸음 수',
  READ_EXERCISE: '운동 기록',
  READ_HEART_RATE: '심박',
  READ_SLEEP: '수면',
  READ_HEALTH_DATA_IN_BACKGROUND: '백그라운드',
  READ_HEALTH_DATA_HISTORY: '과거 기록',
}

const manifest = readFileSync(manifestPath, 'utf8')
const policy = readFileSync(policyPath, 'utf8')

const declared = [...manifest.matchAll(/android\.permission\.health\.([A-Z_]+)/g)].map((m) => m[1])
const failures = []

for (const permission of new Set(declared)) {
  const phrase = REQUIRED_PHRASE[permission]
  if (!phrase) {
    failures.push(
      `매니페스트에 '${permission}' 권한이 있는데 이 스크립트가 모른다 — ` +
        `REQUIRED_PHRASE에 추가하고 정책 페이지에도 설명을 넣을 것`,
    )
    continue
  }
  if (!policy.includes(phrase)) {
    failures.push(`'${permission}' 권한을 선언했는데 정책 페이지에 '${phrase}' 설명이 없다`)
  }
}

for (const [permission, phrase] of Object.entries(REQUIRED_PHRASE)) {
  if (!declared.includes(permission) && policy.includes(phrase)) {
    failures.push(`정책 페이지가 '${phrase}'를 설명하는데 앱은 '${permission}'를 선언하지 않는다 (과다 고지)`)
  }
}

/**
 * 게시 전용 검사 — 자리표시자가 남은 채로 **배포되는 것**만 막는다.
 *
 * 평소 CI에서까지 막지 않는 이유: 도메인·문의 창구는 #101에서 정해지고, 그때까지 이 페이지의
 * 나머지(권한 정합)는 계속 검사돼야 한다. 여기서 항상 실패하면 팀은 이 스크립트를 통째로 끄게 된다.
 * 배포 파이프라인이 `NEXUS_POLICY_PUBLISH=1`을 세워 이 게이트를 켠다.
 */
if (process.env.NEXUS_POLICY_PUBLISH === '1') {
  for (const placeholder of ['TODO@example.com']) {
    if (policy.includes(placeholder)) {
      failures.push(`자리표시자 '${placeholder}'가 남았다 — 게시 전 확정할 것 (#101)`)
    }
  }
} else if (policy.includes('TODO@example.com')) {
  console.warn('경고: 문의 이메일이 아직 자리표시자다 (#101에서 확정). 배포는 이 상태로 통과하지 못한다.')
}

/** OG는 초기 HTML에 있어야 한다 (.claude/rules/web.md — 카카오 스크래퍼는 JS를 안 돌린다). */
for (const tag of ['og:title', 'og:description', 'og:type']) {
  if (!policy.includes(`property="${tag}"`)) failures.push(`정책 페이지에 ${tag}가 없다`)
}

if (failures.length > 0) {
  console.error('정책 페이지 정합 실패:')
  failures.forEach((f) => console.error(`  - ${f}`))
  process.exit(1)
}

console.log(`정책 페이지 정합 OK — 건강 권한 ${new Set(declared).size}종 모두 설명됨`)
