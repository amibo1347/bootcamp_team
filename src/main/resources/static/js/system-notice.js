/**
 * 시스템 공지 배너 — 현재 노출 공지를 주기적으로 폴링해 실시간 반영한다.
 *  - 공지 시작 시각 도달 시 자동 노출, 종료/삭제 시 자동 제거 (새로고침 불필요).
 *  - 배너 컨테이너: #system-notice-banner (partials/header.html).
 */
(() => {
  const BANNER_ID = 'system-notice-banner';
  const POLL_MS = 5000; // 5초 주기 — MASTER 가 공지 등록/삭제 시 일반 사용자 화면에 ~5초 안에 반영.

  /** 폴링 응답으로 배너 DOM 을 다시 그린다. */
  const render = (data) => {
    const box = document.getElementById(BANNER_ID);
    if (!box) return;

    if (!data || !data.active) {
      box.replaceChildren(); // 노출 공지 없음 → 비움
      return;
    }

    const div = document.createElement('div');
    div.className = 'system-notice '
      + (data.type === 'MAINTENANCE' ? 'system-notice--maint' : 'system-notice--info');
    div.textContent = data.content || ''; // textContent — XSS 방지
    box.replaceChildren(div);
  };

  const poll = () => {
    fetch('/api/system-notice/current', { headers: { Accept: 'application/json' } })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => { if (data) render(data); })
      .catch(() => { /* 네트워크 오류는 무시하고 다음 주기 재시도 */ });
  };

  // defer 스크립트라 DOM 은 이미 준비됨. 즉시 1회 동기화 후 주기 폴링.
  poll();
  setInterval(poll, POLL_MS);

  // 탭 활성화 시 즉시 한 번 더 폴링 — 다른 탭에서 작업하다 돌아왔을 때 즉시 갱신.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') poll();
  });
})();
