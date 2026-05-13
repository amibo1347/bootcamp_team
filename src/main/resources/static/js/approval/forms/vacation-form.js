/**
 * 휴가 신청 양식 유틸
 * - 위저드 3단계에서 입력값 검증과 제출 payload 직렬화를 담당한다.
 */

/**
 * 문자열 입력값을 안전하게 가져온다.
 * @param {Document|HTMLElement} root
 * @param {string} selector
 * @returns {string}
 */
function getValue(root, selector) {
  const el = root.querySelector(selector);
  if (!(el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement)) {
    return '';
  }
  return el.value.trim();
}

/**
 * 휴가 입력값을 제출용 객체로 변환한다.
 * @param {Document|HTMLElement} root
 * @returns {{ vacationType: string, days: number, startDate: string, endDate: string }}
 */
export function serializeVacationForm(root = document) {
  return {
    vacationType: getValue(root, '#vacation-type'),
    days: Number(getValue(root, '#vacation-days') || 0),
    startDate: getValue(root, '#vacation-start'),
    endDate: getValue(root, '#vacation-end'),
  };
}

/**
 * 휴가 입력값을 검증한다.
 * @param {Document|HTMLElement} root
 * @returns {{ valid: boolean, message: string, data: ReturnType<typeof serializeVacationForm> }}
 */
export function validateVacationForm(root = document) {
  const data = serializeVacationForm(root);

  if (!data.vacationType) {
    return { valid: false, message: '휴가 유형을 입력하세요.', data };
  }
  if (!Number.isFinite(data.days) || data.days <= 0) {
    return { valid: false, message: '휴가 일수를 입력하세요.', data };
  }
  if (!data.startDate || !data.endDate) {
    return { valid: false, message: '휴가 시작일과 종료일을 입력하세요.', data };
  }
  if (data.startDate > data.endDate) {
    return { valid: false, message: '종료일은 시작일 이후여야 합니다.', data };
  }

  return { valid: true, message: '', data };
}

/**
 * 휴가 입력 필드를 초기 상태로 되돌린다.
 * @param {Document|HTMLElement} root
 */
export function resetVacationForm(root = document) {
  const type = root.querySelector('#vacation-type');
  const days = root.querySelector('#vacation-days');
  const start = root.querySelector('#vacation-start');
  const end = root.querySelector('#vacation-end');

  if (type instanceof HTMLInputElement) type.value = '';
  if (days instanceof HTMLInputElement) days.value = '1';
  if (start instanceof HTMLInputElement) start.value = '';
  if (end instanceof HTMLInputElement) end.value = '';
}
