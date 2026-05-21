/**
 * 백엔드 에러 응답에서 사용자에게 보여줄 메시지를 추출하는 공통 헬퍼.
 *
 * GlobalExceptionHandler 는 BusinessException 을 { errorCode, message } 형식으로 응답하며,
 * message 에는 ErrorCode 에 정의된 한국어 안내 문구가 담긴다.
 *
 * 사용 예:
 *   const res = await fetch(url, ...);
 *   if (!res.ok) { alert(await getApiErrorMessage(res)); return; }
 *
 * @param {Response} res    fetch 응답 객체
 * @param {string}  [fallback] body 파싱 실패/네트워크 오류 시 보여줄 기본 문구
 * @returns {Promise<string>} 사용자에게 표시할 에러 메시지
 */
window.getApiErrorMessage = async function (res, fallback) {
  const def = fallback || '요청 처리 중 오류가 발생했습니다.';
  try {
    const body = await res.json();
    return (body && body.message) ? body.message : def;
  } catch (e) {
    return def;
  }
};

/**
 * 세션 강제 종료 가드 (회사 비활성화 등).
 *  - 서버(MemberCompanyGuardFilter)가 API 요청에 401 + 'X-Logout-Reason' 헤더를 주면,
 *    fetch 응답을 가로채 즉시 로그인 화면으로 보낸다.
 *  - 일반 페이지 요청은 서버가 직접 302 리다이렉트하므로 여기서 다룰 필요 없음.
 *  - 헤더가 없는 일반 401(미인증 API 등)은 건드리지 않는다 — 기존 호출부 처리 유지.
 */
(function () {
  if (window.__memberLogoutGuardInstalled || typeof window.fetch !== 'function') return;
  window.__memberLogoutGuardInstalled = true;

  const originalFetch = window.fetch.bind(window);
  window.fetch = async function (...args) {
    const res = await originalFetch(...args);
    try {
      if (res.status === 401 && res.headers.get('X-Logout-Reason')) {
        window.location.href = '/company-login';
      }
    } catch (e) {
      // 헤더 접근 실패 등은 무시 — 원래 응답은 그대로 반환.
    }
    return res;
  };
})();
