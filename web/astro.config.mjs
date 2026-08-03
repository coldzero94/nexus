// @ts-check
import { defineConfig } from 'astro/config'

/**
 * 랜딩·정책 페이지는 **완전 정적**이다 (docs/ARCHITECTURE.md §3, .claude/rules/web.md).
 *
 * 어댑터를 아직 안 붙인 이유: SSR이 필요한 건 공유 스냅샷 라우트(#103, S10)뿐이고, 지금 붙이면
 * 쓰지도 않는 런타임을 정책 페이지가 짊어진다. 정책 URL은 심사 전제라 가장 단순하게 뜨는 게 낫다.
 *
 * `site`는 배포 도메인이 확정되면 채운다(#101) — 절대 URL(og:url·sitemap)이 여기서 나온다.
 *
 * 출력은 기본값인 디렉터리 형식(`/privacy/index.html`)을 쓴다. 파일 형식(`/privacy.html`)은
 * 호스트가 확장자 없는 경로를 어떻게 다루는지에 기대는데, 정책 URL은 심사에 제출하는 주소라
 * 호스트 설정에 기대는 부분이 없는 쪽이 낫다.
 */
export default defineConfig({
  output: 'static',
})
