/**
 * 전자결재 양식 레지스트리
 * - formCode별로 화면 섹션 표시, 검증, payload 직렬화를 연결한다.
 * - GENERIC / EXPENSE 는 HTML fragment 가 없으므로 mount* 가 페이지 진입 시 slot 에 inject.
 */

import {
  mountVacationFormInputs,
  resetVacationForm,
  serializeVacationForm,
  validateVacationForm,
} from './vacation-form.js';
import {
  mountGenericFormFields,
  resetGenericForm,
  serializeGenericForm,
  validateGenericForm,
} from './generic-form.js';
import {
  mountExpenseFormFields,
  resetExpenseForm,
  serializeExpenseForm,
  validateExpenseForm,
} from './expense-form.js';

/**
 * @typedef {Object} ApprovalFormDefinition
 * @property {string} formCode
 * @property {string} sectionSelector
 * @property {(root?: Document|HTMLElement) => { valid: boolean, message: string, data: Record<string, unknown> }} validate
 * @property {(root?: Document|HTMLElement) => Record<string, unknown>} serialize
 * @property {(root?: Document|HTMLElement) => void} reset
 */

/** @type {Record<string, ApprovalFormDefinition>} */
const FORM_REGISTRY = {
  VACATION: {
    formCode: 'VACATION',
    sectionSelector: '[data-approval-form-section="VACATION"]',
    validate: validateVacationForm,
    serialize: serializeVacationForm,
    reset: resetVacationForm,
  },
  GENERIC: {
    formCode: 'GENERIC',
    sectionSelector: '[data-approval-form-section="GENERIC"]',
    validate: validateGenericForm,
    serialize: serializeGenericForm,
    reset: resetGenericForm,
  },
  EXPENSE: {
    formCode: 'EXPENSE',
    sectionSelector: '[data-approval-form-section="EXPENSE"]',
    validate: validateExpenseForm,
    serialize: serializeExpenseForm,
    reset: resetExpenseForm,
  },
};

/** 모든 양식의 mount 작업(서버 렌더 fragment 의 listener 부착 + 동적 inject) 을 한 번에 수행. */
function mountDynamicSections(root = document) {
  mountVacationFormInputs(root);
  mountGenericFormFields(root);
  mountExpenseFormFields(root);
}

// 모듈 로드 시 즉시 mount 시도. DOM 이 아직 안 만들어졌으면 DOMContentLoaded 에서 재시도.
if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => mountDynamicSections(document));
  } else {
    mountDynamicSections(document);
  }
}

/**
 * formCode로 등록된 양식 정의를 조회한다.
 * @param {string|null|undefined} formCode
 * @returns {ApprovalFormDefinition|null}
 */
export function getApprovalFormDefinition(formCode) {
  if (!formCode) return null;
  return FORM_REGISTRY[String(formCode).toUpperCase()] || null;
}

/**
 * 선택된 양식에 맞는 입력 섹션만 표시한다.
 * @param {string|null|undefined} formCode
 * @param {Document|HTMLElement} root
 */
export function activateApprovalFormSection(formCode, root = document) {
  const normalized = formCode ? String(formCode).toUpperCase() : '';
  // mount 가 늦었을 수 있으므로 호출 시점에 한 번 더 보장
  mountDynamicSections(root);
  const sections = root.querySelectorAll('[data-approval-form-section]');
  sections.forEach((section) => {
    const sectionCode = section.getAttribute('data-approval-form-section');
    section.classList.toggle('hidden', sectionCode !== normalized);
  });
}

/**
 * 등록된 모든 양식 입력값을 초기화한다.
 * @param {Document|HTMLElement} root
 */
export function resetRegisteredApprovalForms(root = document) {
  Object.values(FORM_REGISTRY).forEach((definition) => definition.reset(root));
}
