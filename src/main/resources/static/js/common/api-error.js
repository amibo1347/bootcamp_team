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
