/**
 * 전자결재 양식 레지스트리
 * - formCode별로 화면 섹션 표시, 검증, payload 직렬화를 연결한다.
 */

import { resetVacationForm, serializeVacationForm, validateVacationForm } from './vacation-form.js';

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
};

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
