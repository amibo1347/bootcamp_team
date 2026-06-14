import { mountApprovalCombobox } from '../common/approval-combobox.js';
import { getValue, attachShowPickerOnClick } from './form-utils.js';

/**
 * 휴가 신청 양식 유틸
 * - 위저드 3단계에서 입력값 검증과 제출 payload 직렬화를 담당한다.
 * - 일수(days)는 시작일/종료일 선택 시 자동 계산되며 사용자가 직접 입력하지 못한다(readonly).
 */

/**
 * 휴가 유형 select 에 enum 옵션을 채운다.
 * - 이미 옵션이 채워졌으면 (기본 "선택하세요" 1개 초과) 재호출 시 그대로 둠.
 * - 옵션 value 는 enum name (서버 매칭 키), 라벨은 description.
 * @param {Document|HTMLElement} root
 * @param {Array<{ name: string, description: string }>} options
 */
export function populateVacationTypeOptions(root = document, options = []) {
  const select = root.querySelector('#vacation-type');
  if (!(select instanceof HTMLSelectElement)) return;
  if (!Array.isArray(options) || options.length === 0) return;
  if (select.dataset.optionsLoaded === '1') return;

  const fragments = options.map((opt) => {
    const value = String(opt?.name ?? '');
    const label = String(opt?.description ?? opt?.name ?? '');
    const o = document.createElement('option');
    o.value = value;
    o.textContent = label;
    return o;
  });
  fragments.forEach((o) => select.appendChild(o));
  select.dataset.optionsLoaded = '1';
  mountApprovalCombobox(select);
}

/**
 * 시작/종료일 선택 시 일수 자동 계산 + days 입력은 readonly 로 잠금.
 * idempotent — 이미 mount 된 요소는 다시 listener 를 붙이지 않는다.
 * @param {Document|HTMLElement} root
 */
export function mountVacationFormInputs(root = document) {
  const start = root.querySelector('#vacation-start');
  const end = root.querySelector('#vacation-end');
  const days = root.querySelector('#vacation-days');
  const half = root.querySelector('#vacation-half');
  if (!(start instanceof HTMLInputElement)
    || !(end instanceof HTMLInputElement)
    || !(days instanceof HTMLInputElement)) {
    return;
  }
  if (days.dataset.vacationMounted === '1') return;
  days.dataset.vacationMounted = '1';

  days.readOnly = true;
  days.classList.add('bg-gray-100', 'cursor-not-allowed', 'dark:bg-gray-800');
  days.value = '0';

  const isHalf = () => half instanceof HTMLSelectElement && half.value !== '';

  // 반차일 때: 종료일을 시작일로 강제하고 입력을 잠근다(반차는 하루만).
  function syncEndForHalf() {
    if (!(end instanceof HTMLInputElement)) return;
    if (isHalf()) {
      end.value = start.value;
      end.readOnly = true;
      end.classList.add('bg-gray-100', 'cursor-not-allowed', 'dark:bg-gray-800');
    } else {
      end.readOnly = false;
      end.classList.remove('bg-gray-100', 'cursor-not-allowed', 'dark:bg-gray-800');
    }
  }

  function recalc() {
    // 단위(종일/반차) 변경에 따라 종료일 잠금 상태를 항상 먼저 reconcile.
    syncEndForHalf();
    // 반차 — 시작일만 있으면 0.5일.
    if (isHalf()) {
      days.value = start.value ? '0.5' : '0';
      return;
    }
    if (!start.value || !end.value) {
      days.value = '0';
      return;
    }
    const s = new Date(start.value);
    const e = new Date(end.value);
    if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime()) || e < s) {
      days.value = '0';
      return;
    }
    const diff = Math.floor((e - s) / (1000 * 60 * 60 * 24)) + 1;
    days.value = String(diff);
  }

  start.addEventListener('change', recalc);
  end.addEventListener('change', recalc);
  start.addEventListener('input', recalc);
  end.addEventListener('input', recalc);
  if (half instanceof HTMLSelectElement) {
    half.addEventListener('change', recalc);
  }

  // input row 어디를 클릭해도 네이티브 캘린더 picker 가 뜨도록.
  // (브라우저 기본은 아이콘 클릭만 picker 를 연다)
  attachShowPickerOnClick(start);
  attachShowPickerOnClick(end);
}

/**
 * 휴가 입력값을 제출용 객체로 변환한다.
 * @param {Document|HTMLElement} root
 * @returns {{ vacationType: string, days: number, startDate: string, endDate: string }}
 */
export function serializeVacationForm(root = document) {
  const halfDayPeriod = getValue(root, '#vacation-half'); // '' | 'AM' | 'PM'
  const isHalf = halfDayPeriod === 'AM' || halfDayPeriod === 'PM';
  const startDate = getValue(root, '#vacation-start');
  return {
    vacationType: getValue(root, '#vacation-type'),
    // 반차면 0.5일·하루 고정. 서버에서도 동일하게 강제하지만 클라이언트 표시값도 맞춰 보낸다.
    days: isHalf ? 0.5 : Number(getValue(root, '#vacation-days') || 0),
    startDate,
    endDate: isHalf ? startDate : getValue(root, '#vacation-end'),
    halfDayPeriod: isHalf ? halfDayPeriod : null,
    reason: getValue(root, '#vacation-reason') || null,
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

  const isHalf = data.halfDayPeriod === 'AM' || data.halfDayPeriod === 'PM';
  if (isHalf) {
    // 반차 — 시작일만 있으면 충분(종료일=시작일, 0.5일 자동).
    if (!data.startDate) {
      return { valid: false, message: '반차 날짜를 선택하세요.', data };
    }
    return { valid: true, message: '', data };
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
  const half = root.querySelector('#vacation-half');
  const reason = root.querySelector('#vacation-reason');

  if (reason instanceof HTMLTextAreaElement) reason.value = '';
  if (type instanceof HTMLSelectElement) type.value = '';
  else if (type instanceof HTMLInputElement) type.value = '';
  if (days instanceof HTMLInputElement) days.value = '0';
  if (start instanceof HTMLInputElement) start.value = '';
  if (end instanceof HTMLInputElement) {
    end.value = '';
    end.readOnly = false;
    end.classList.remove('bg-gray-100', 'cursor-not-allowed', 'dark:bg-gray-800');
  }
  if (half instanceof HTMLSelectElement) half.value = '';
}
