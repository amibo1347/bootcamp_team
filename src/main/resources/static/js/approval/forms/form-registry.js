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
import {
  mountDynamicFormSection,
  renderDynamicForm,
  resetDynamicForm,
  serializeDynamicForm,
  validateDynamicForm,
} from './dynamic-form.js';

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
  // B안: 회사 사본 + fieldSchema 가 있는 양식은 모두 이 정의 사용. 양식별 모양은 template.fieldSchema 로
  // 매번 다시 그린다 — wizard-controller 가 양식 선택 시 renderDynamicForm(template) 을 호출해야 한다.
  DYNAMIC: {
    formCode: 'DYNAMIC',
    sectionSelector: '[data-approval-form-section="DYNAMIC"]',
    validate: validateDynamicForm,
    serialize: serializeDynamicForm,
    reset: resetDynamicForm,
  },
};

/** 모든 양식의 mount 작업(서버 렌더 fragment 의 listener 부착 + 동적 inject) 을 한 번에 수행. */
function mountDynamicSections(root = document) {
  mountVacationFormInputs(root);
  mountGenericFormFields(root);
  mountExpenseFormFields(root);
  mountDynamicFormSection(root);
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
 * 선택된 양식(template) 에 맞는 정의를 결정한다.
 * - fieldSchema 가 있는 양식 → DYNAMIC (B안)
 * - 그 외엔 formCode 기반 (시스템 디폴트 VACATION/GENERIC/EXPENSE)
 * - 둘 다 매칭 안 되면 null (본문 없는 양식)
 * @param {Record<string, unknown>|null|undefined} template
 * @returns {ApprovalFormDefinition|null}
 */
export function getApprovalFormDefinitionForTemplate(template) {
  if (!template) return null;
  const schema = typeof template.fieldSchema === 'string' ? template.fieldSchema.trim() : '';
  if (schema) return FORM_REGISTRY.DYNAMIC;
  return getApprovalFormDefinition(String(template.formCode || ''));
}

/**
 * 선택된 양식에 맞는 입력 섹션만 표시한다.
 * 동적(DYNAMIC) 양식의 경우 template 정보가 필요 — 두 번째 인자로 받는다.
 * @param {string|null|undefined} sectionCode 섹션 식별자 (formCode 또는 'DYNAMIC')
 * @param {Document|HTMLElement} root
 * @param {Record<string, unknown>|null} [template] sectionCode === 'DYNAMIC' 일 때 fieldSchema 로 폼 렌더
 */
export function activateApprovalFormSection(sectionCode, root = document, template = null) {
  const normalized = sectionCode ? String(sectionCode).toUpperCase() : '';
  // mount 가 늦었을 수 있으므로 호출 시점에 한 번 더 보장
  mountDynamicSections(root);

  if (normalized === 'DYNAMIC' && template) {
    renderDynamicForm(template, root);
  }

  const sections = root.querySelectorAll('[data-approval-form-section]');
  sections.forEach((section) => {
    const code = section.getAttribute('data-approval-form-section');
    section.classList.toggle('hidden', code !== normalized);
  });
}

/**
 * 등록된 모든 양식 입력값을 초기화한다.
 * @param {Document|HTMLElement} root
 */
export function resetRegisteredApprovalForms(root = document) {
  Object.values(FORM_REGISTRY).forEach((definition) => definition.reset(root));
}
