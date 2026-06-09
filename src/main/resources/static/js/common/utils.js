/**
 * 프론트 공용 유틸 — 여러 페이지에서 중복으로 정의되던 함수를 한 곳에 모은 모듈.
 *
 * 모든 함수를 window 글로벌로 등록한다. 각 페이지 JS(IIFE 안)에서는
 * 동일한 이름으로 그대로 호출하면 글로벌 스코프로 fallback 되어 호출된다.
 *
 * 로드 위치: 사용 페이지의 다른 페이지 JS 보다 먼저 <script src="/js/common/utils.js">.
 */
(() => {
  if (window.__intranetCommonUtilsInstalled) return;
  window.__intranetCommonUtilsInstalled = true;

  /**
   * 날짜 값을 'YYYY-MM-DD HH:mm' 형식으로 변환한다.
   * 빈 값이면 '-'. 파싱 실패면 String(value).
   * @param {string|number|Date} value
   * @returns {string}
   */
  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}`;
  }

  /**
   * 사용자 입력 문자열을 HTML 안전 형태로 이스케이프 (단순 치환 방식).
   * null/undefined → '' 처리.
   * @param {unknown} value
   * @returns {string}
   */
  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  /** 페이지 메타에서 CSRF 토큰 값을 꺼낸다 (없으면 빈 문자열). */
  function getCsrfToken() {
    return document.querySelector('meta[name="_csrf"]')?.content || '';
  }

  /** 페이지 메타에서 CSRF 헤더 이름을 꺼낸다 (없으면 'X-CSRF-TOKEN'). */
  function getCsrfHeaderName() {
    return document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  }

  /**
   * Accept + CSRF 헤더를 만든다. jsonBody=true면 Content-Type:application/json 추가.
   * @param {boolean} [jsonBody=false]
   * @returns {Record<string, string>}
   */
  function buildHeaders(jsonBody = false) {
    const headers = {
      Accept: 'application/json',
      [getCsrfHeaderName()]: getCsrfToken(),
    };
    if (jsonBody) {
      headers['Content-Type'] = 'application/json';
    }
    return headers;
  }

  /**
   * POST/PUT/DELETE 등 본문 없이 보내는 요청용 헤더.
   * 기존 board/trash.js, subAdmin/unifiedTrash.js 의 getPostHeaders() 와 동일.
   * @returns {Record<string, string>}
   */
  function getPostHeaders() {
    return {
      [getCsrfHeaderName()]: getCsrfToken(),
      Accept: 'application/json, text/plain, */*',
    };
  }

  window.formatDate = formatDate;
  window.escapeHtml = escapeHtml;
  window.buildHeaders = buildHeaders;
  window.getPostHeaders = getPostHeaders;
  window.getCsrfToken = getCsrfToken;
  window.getCsrfHeaderName = getCsrfHeaderName;
})();
