/**
 * 전자결재 3스텝 위저드 컨트롤러
 * - 양식 선택, 결재자 선택, 동적 양식 작성, Mock POST 제출 흐름을 담당한다.
 */

import { submitApproval } from '../api/approval-client.js';
import {
  activateApprovalFormSection,
  getApprovalFormDefinition,
  resetRegisteredApprovalForms,
} from '../forms/form-registry.js';

/** @type {number} */
let wizardStep = 1;

/** @type {Array<Record<string, unknown>>} */
let formTemplates = [];

/**
 * HTML 이스케이프
 * @param {unknown} value
 * @returns {string}
 */
function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

/**
 * 위저드 안내 메시지를 표시한다.
 * @param {string} message
 * @param {'success'|'error'} tone
 */
function setWizardMessage(message, tone = 'success') {
  const msg = document.getElementById('approval-wizard-message');
  if (!msg) return;
  msg.textContent = message;
  msg.classList.remove('hidden', 'text-green-600', 'dark:text-green-400', 'text-red-600', 'dark:text-red-400');
  if (tone === 'error') {
    msg.classList.add('text-red-600', 'dark:text-red-400');
  } else {
    msg.classList.add('text-green-600', 'dark:text-green-400');
  }
}

/**
 * 위저드 안내 메시지를 숨긴다.
 */
function clearWizardMessage() {
  const msg = document.getElementById('approval-wizard-message');
  if (!msg) return;
  msg.textContent = '';
  msg.classList.add('hidden');
}

/**
 * 선택한 양식 템플릿을 조회한다.
 * @returns {Record<string, unknown>|null}
 */
function getSelectedTemplate() {
  const el = document.querySelector('input[name="approval-form-template"]:checked');
  if (!(el instanceof HTMLInputElement)) return null;
  const id = Number(el.value);
  return formTemplates.find((template) => Number(template.id) === id) || null;
}

/**
 * 현재 선택된 양식의 formCode를 반환한다.
 * @returns {string}
 */
function getSelectedFormCode() {
  const selected = getSelectedTemplate();
  return selected?.formCode ? String(selected.formCode).toUpperCase() : '';
}

/**
 * 현재 스텝 화면과 버튼 상태를 갱신한다.
 * @param {number} nextStep
 */
function showWizardStep(nextStep) {
  wizardStep = nextStep;
  for (let i = 1; i <= 3; i += 1) {
    const step = document.getElementById(`approval-step-${i}`);
    const label = document.getElementById(`approval-step-label-${i}`);
    step?.classList.toggle('hidden', i !== nextStep);
    if (label) {
      label.classList.toggle('font-semibold', i === nextStep);
      label.classList.toggle('text-brand-600', i === nextStep);
      label.classList.toggle('dark:text-brand-400', i === nextStep);
    }
  }

  const back = document.getElementById('approval-wizard-back');
  const next = document.getElementById('approval-wizard-next');
  const submit = document.getElementById('approval-wizard-submit');
  if (back instanceof HTMLButtonElement) back.disabled = nextStep === 1;
  next?.classList.toggle('hidden', nextStep === 3);
  submit?.classList.toggle('hidden', nextStep !== 3);
}

/**
 * 양식 라디오 카드 목록을 렌더링한다.
 * @param {Array<Record<string, unknown>>} templates
 */
function renderFormTemplates(templates) {
  const root = document.getElementById('approval-form-template-list');
  if (!root) return;
  root.innerHTML = '';

  const activeTemplates = templates.filter(
    (template) => template.isActive !== false && getApprovalFormDefinition(String(template.formCode || '')),
  );
  activeTemplates.forEach((template, index) => {
    const label = document.createElement('label');
    const checked = index === 0 ? 'checked' : '';
    label.className =
      'flex cursor-pointer items-start gap-3 rounded-xl border border-gray-200 p-4 hover:border-brand-400 dark:border-gray-700 dark:hover:border-brand-500';
    label.innerHTML = `
      <input type="radio" name="approval-form-template" value="${escapeHtml(template.id)}" class="mt-1 h-4 w-4 border-gray-300 text-brand-600 focus:ring-brand-500" ${checked} />
      <span>
        <span class="block font-medium text-gray-900 dark:text-white">${escapeHtml(template.name)}</span>
        <span class="text-xs text-gray-500 dark:text-gray-400">${escapeHtml(template.formCode)}</span>
      </span>`;
    root.appendChild(label);
  });

  activateApprovalFormSection(getSelectedFormCode());
}

/**
 * 결재자 후보 select를 렌더링한다.
 * @param {Array<Record<string, unknown>>} candidates
 */
function renderApproverCandidates(candidates) {
  const select = document.getElementById('approval-approver-select');
  if (!(select instanceof HTMLSelectElement)) return;

  select.innerHTML = '<option value="">결재자를 선택하세요</option>';
  candidates.forEach((candidate, index) => {
    const option = document.createElement('option');
    option.value = String(candidate.memberId || '');
    option.textContent = `${candidate.name || ''} (${candidate.deptName || ''})`;
    if (index === 0) option.selected = true;
    select.appendChild(option);
  });
}

/**
 * 현재 단계 입력값을 검증한다.
 * @returns {boolean}
 */
function validateCurrentStep() {
  if (wizardStep === 1) {
    if (!getSelectedTemplate()) {
      setWizardMessage('양식을 선택하세요.', 'error');
      return false;
    }
  }

  if (wizardStep === 2) {
    const select = document.getElementById('approval-approver-select');
    if (!(select instanceof HTMLSelectElement) || !select.value) {
      setWizardMessage('결재자를 선택하세요.', 'error');
      return false;
    }
  }

  clearWizardMessage();
  return true;
}

/**
 * 3단계 입력값을 검증하고 제출 payload를 만든다.
 * @param {number|null|undefined} memberId
 * @returns {{ valid: boolean, message: string, payload: Record<string, unknown> }}
 */
function buildSubmitPayload(memberId) {
  const selected = getSelectedTemplate();
  const definition = getApprovalFormDefinition(getSelectedFormCode());
  const approver = document.getElementById('approval-approver-select');
  const title = document.getElementById('approval-draft-title');

  const titleText = title instanceof HTMLInputElement ? title.value.trim() : '';
  if (!titleText) {
    return { valid: false, message: '제목을 입력하세요.', payload: {} };
  }

  if (!definition) {
    return { valid: false, message: '지원하지 않는 양식입니다.', payload: {} };
  }

  const formValidation = definition.validate(document);
  if (!formValidation.valid) {
    return { valid: false, message: formValidation.message, payload: {} };
  }

  return {
    valid: true,
    message: '',
    payload: {
      formTemplateId: selected ? Number(selected.id) : null,
      formCode: selected?.formCode || '',
      approverMemberId: approver instanceof HTMLSelectElement ? Number(approver.value) : null,
      title: titleText,
      drafterMemberId: memberId ?? null,
      [String(selected?.formCode || '').toLowerCase()]: definition.serialize(document),
    },
  };
}

/**
 * 위저드 입력값을 초기화한다.
 */
function resetWizard() {
  const title = document.getElementById('approval-draft-title');
  if (title instanceof HTMLInputElement) title.value = '';
  resetRegisteredApprovalForms();
  showWizardStep(1);
}

/**
 * 위저드 컨트롤러를 초기화한다.
 * @param {{ templates: Array<Record<string, unknown>>, candidates: Array<Record<string, unknown>>, memberId?: number|null, onSubmitted?: () => Promise<void>|void }} options
 * @returns {{ refreshTemplates: (templates: Array<Record<string, unknown>>) => void }}
 */
export function initApprovalWizard(options) {
  formTemplates = Array.isArray(options.templates) ? options.templates : [];
  renderFormTemplates(formTemplates);
  renderApproverCandidates(Array.isArray(options.candidates) ? options.candidates : []);
  showWizardStep(1);

  document.getElementById('approval-form-template-list')?.addEventListener('change', () => {
    activateApprovalFormSection(getSelectedFormCode());
  });

  document.getElementById('approval-wizard-next')?.addEventListener('click', () => {
    if (!validateCurrentStep()) return;
    if (wizardStep < 3) showWizardStep(wizardStep + 1);
  });

  document.getElementById('approval-wizard-back')?.addEventListener('click', () => {
    if (wizardStep > 1) showWizardStep(wizardStep - 1);
  });

  document.getElementById('approval-wizard-submit')?.addEventListener('click', async () => {
    const submitButton = document.getElementById('approval-wizard-submit');
    const built = buildSubmitPayload(options.memberId);
    if (!built.valid) {
      setWizardMessage(built.message, 'error');
      return;
    }

    try {
      if (submitButton instanceof HTMLButtonElement) submitButton.disabled = true;
      const result = await submitApproval(built.payload);
      setWizardMessage(result.message || '제출되었습니다.', 'success');
      resetWizard();
      await options.onSubmitted?.();
    } catch (error) {
      console.error('[approval-wizard] 제출 실패', error);
      setWizardMessage('제출 중 오류가 발생했습니다.', 'error');
    } finally {
      if (submitButton instanceof HTMLButtonElement) submitButton.disabled = false;
    }
  });

  return {
    refreshTemplates(nextTemplates) {
      formTemplates = Array.isArray(nextTemplates) ? nextTemplates : [];
      renderFormTemplates(formTemplates);
    },
  };
}
