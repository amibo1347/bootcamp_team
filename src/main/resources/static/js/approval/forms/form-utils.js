/**
 * 결재 양식 공용 유틸 — generic / expense / vacation 폼에서 동일 패턴으로 중복 정의되던 헬퍼.
 *
 *  - getValue: form 필드의 trim 된 문자열 값을 안전하게 꺼낸다.
 *  - attachShowPickerOnClick: 네이티브 date/time picker 를 click/focus 시 강제 표시.
 *
 *  ※ dynamic-form.js 는 schema 기반이라 이 헬퍼를 사용하지 않으므로 통합 대상 아님.
 */

/**
 * 문자열 입력값 안전 추출. input / textarea / select 만 대상.
 * @param {Document|HTMLElement} root
 * @param {string} selector
 * @returns {string}
 */
export function getValue(root, selector) {
  const el = root.querySelector(selector);
  if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement)) {
    return '';
  }
  return el.value.trim();
}

/**
 * input row 전체 클릭(또는 포커스) 시 네이티브 date picker 호출.
 *  - showPicker 미지원 브라우저 / user-gesture 외부 호출은 조용히 패스.
 *  - idempotent — 같은 input 에 두 번 부착되지 않는다 (dataset 가드).
 * @param {HTMLInputElement} input
 */
export function attachShowPickerOnClick(input) {
  if (input.dataset.showPickerBound === '1') return;
  input.dataset.showPickerBound = '1';
  const openPicker = () => {
    if (typeof input.showPicker === 'function') {
      try { input.showPicker(); } catch (_ignored) { /* user gesture 밖 호출 등 */ }
    }
  };
  input.addEventListener('click', openPicker);
  input.addEventListener('focus', openPicker);
}
