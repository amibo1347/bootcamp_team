/**
 * 전자결재 3스텝 위저드 컨트롤러
 * - 1: 양식 선택, 2: 결재선 선택(드롭다운 → 모달), 3: 양식 본문 작성·제출
 * - 결재선은 1~4인 옵션. 선택 즉시 모달이 열려 후보 중 N명을 단계 순서로 고른다.
 */

import { submitApproval } from '../api/approval-client.js';
import {
  activateApprovalFormSection,
  getApprovalFormDefinitionForTemplate,
  resetRegisteredApprovalForms,
} from '../forms/form-registry.js';
import { openApproverLineModal } from './approver-line-modal.js';
import { mountApprovalCombobox } from '../common/approval-combobox.js';

/** @type {number} */
let wizardStep = 1;

/** @type {Array<Record<string, unknown>>} */
let formTemplates = [];

/** @type {Array<Record<string, unknown>>} */
let approverCandidates = [];

/** @type {number[]} 선택된 결재선 (memberId 순서 = 단계) */
let currentApprovalLine = [];

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
 * 현재 선택된 양식에 맞는 본문 섹션을 활성화한다 (B안 동적 분기 포함).
 */
function activateSectionForSelected() {
  const selected = getSelectedTemplate();
  const def = getApprovalFormDefinitionForTemplate(selected);
  if (def) {
    activateApprovalFormSection(def.formCode, document, selected);
  } else {
    // 본문 없는 양식 — 모든 섹션 숨김
    activateApprovalFormSection('', document);
  }
}

/**
 * 현재 스텝 화면과 버튼 상태를 갱신한다.
 * @param {number} nextStep
 */
function showWizardStep(nextStep) {
  wizardStep = nextStep;
  const indicatorActive =
    'flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-500 text-sm font-semibold text-white';
  const indicatorInactive =
    'flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gray-100 text-sm font-semibold text-gray-400 dark:bg-gray-800 dark:text-gray-500';

  for (let i = 1; i <= 3; i += 1) {
    const step = document.getElementById(`approval-step-${i}`);
    const label = document.getElementById(`approval-step-label-${i}`);
    const indicator = document.getElementById(`approval-step-indicator-${i}`);
    const isActive = i === nextStep;

    step?.classList.toggle('hidden', i !== nextStep);

    if (label) {
      label.classList.toggle('font-semibold', isActive);
      label.classList.toggle('text-brand-600', isActive);
      label.classList.toggle('dark:text-brand-400', isActive);
      label.classList.toggle('text-gray-500', !isActive);
      label.classList.toggle('dark:text-gray-400', !isActive);
    }

    if (indicator) {
      indicator.className = isActive ? indicatorActive : indicatorInactive;
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

  // active 양식 모두 표시. 시스템 디폴트 + 회사 사본(B안 동적 포함) + 본문 없는 양식 전부.
  // Jackson 직렬화상 boolean isActive → JSON property "active" 라 t.active 로 판정.
  const activeTemplates = templates.filter((template) => template.active !== false);
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

  activateSectionForSelected();
}

/**
 * 결재자 선택 드롭다운을 결재선 타입 옵션으로 채운다.
 * (옵션 자체로 결재자를 고르지 않는다 — 선택 시 모달이 열린다.)
 */
function renderApprovalLineSelect() {
  const select = document.getElementById('approval-approver-select');
  if (!(select instanceof HTMLSelectElement)) return;
  select.innerHTML = `
    <option value="">결재선 선택</option>
    <option value="1">1인 결재선</option>
    <option value="2">2인 결재선</option>
    <option value="3">3인 결재선</option>
    <option value="4">4인 결재선</option>
  `;
  select.value = '';
  mountApprovalCombobox(select);
}

/**
 * 결재선 미리보기 영역을 콤보박스 아래에 동적 inject (1회만).
 */
function ensureApprovalLineSummary() {
  if (document.getElementById('approval-line-summary')) return;
  const select = document.getElementById('approval-approver-select');
  if (!select) return;
  const anchor =
    select.parentElement?.querySelector('[data-approval-combobox-root]') || select;
  const summary = document.createElement('div');
  summary.id = 'approval-line-summary';
  summary.className = 'mt-3 hidden rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 text-sm dark:border-strokedark dark:bg-meta-4';
  anchor.insertAdjacentElement('afterend', summary);
}

/**
 * 선택된 결재선을 화면 요약에 반영.
 */
function renderApprovalLineSummary() {
  ensureApprovalLineSummary();
  const box = document.getElementById('approval-line-summary');
  if (!box) return;

  if (currentApprovalLine.length === 0) {
    box.classList.add('hidden');
    box.innerHTML = '';
    return;
  }

  box.classList.remove('hidden');
  const items = currentApprovalLine.map((id, idx) => {
    const c = approverCandidates.find((x) => Number(x.memberId) === Number(id));
    const name = c ? String(c.name || '') : `#${id}`;
    const meta = c ? [c.deptName, c.positionName].filter(Boolean).join(' / ') : '';
    const isFinal = idx === currentApprovalLine.length - 1;
    const stage = isFinal ? `${idx + 1}단계 (최종)` : `${idx + 1}단계`;
    return `
      <div class="flex items-center gap-2 rounded-lg bg-white px-3 py-2 ring-1 ring-gray-200 dark:bg-boxdark dark:ring-gray-700">
        <span class="shrink-0 rounded bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">${escapeHtml(stage)}</span>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-gray-900 dark:text-white">${escapeHtml(name)}</p>
          ${meta ? `<p class="truncate text-xs text-gray-400">${escapeHtml(meta)}</p>` : ''}
        </div>
      </div>
    `;
  }).join('');
  box.innerHTML = `
    <div class="flex items-start justify-between gap-3">
      <span class="text-xs font-semibold uppercase tracking-wider text-gray-400">선택된 결재선</span>
      <button type="button" id="approval-line-cancel"
              class="shrink-0 text-xs font-medium text-rose-500 hover:text-rose-600">
        취소
      </button>
    </div>
    <div class="mt-2 flex flex-wrap gap-2">${items}</div>
  `;

  document.getElementById('approval-line-cancel')?.addEventListener('click', () => {
    currentApprovalLine = [];
    const select = document.getElementById('approval-approver-select');
    if (select instanceof HTMLSelectElement) {
      select.value = '';
      select.dispatchEvent(new Event('change', { bubbles: true }));
    }
    renderApprovalLineSummary();
  });
}

/**
 * 결재선 select change 이벤트: 1~4인 결재선 선택 시 모달 오픈.
 */
function bindApprovalLineSelect() {
  const select = document.getElementById('approval-approver-select');
  if (!(select instanceof HTMLSelectElement)) return;

  select.addEventListener('change', () => {
    const v = select.value;
    if (!v) {
      currentApprovalLine = [];
      renderApprovalLineSummary();
      return;
    }
    const n = Number(v);
    if (!Number.isFinite(n) || n < 1 || n > 4) return;

    openApproverLineModal({
      maxCount: n,
      candidates: approverCandidates,
      onConfirm: (line) => {
        currentApprovalLine = Array.isArray(line) ? line.slice() : [];
        renderApprovalLineSummary();
      },
    });
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
      setWizardMessage('결재선을 선택하세요.', 'error');
      return false;
    }
    const expected = Number(select.value);
    if (currentApprovalLine.length !== expected) {
      setWizardMessage(`${expected}인 결재선을 모두 지정하세요.`, 'error');
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
  const definition = getApprovalFormDefinitionForTemplate(selected);
  const title = document.getElementById('approval-draft-title');

  const titleText = title instanceof HTMLInputElement ? title.value.trim() : '';
  if (!titleText) {
    return { valid: false, message: '제목을 입력하세요.', payload: {} };
  }
  if (currentApprovalLine.length === 0) {
    return { valid: false, message: '결재선을 선택하세요.', payload: {} };
  }
  if (!selected) {
    return { valid: false, message: '양식을 선택하세요.', payload: {} };
  }

  // 본문 분기: definition === DYNAMIC → dynamicFields 키, fixed → vacation/generic/expense 키, 없음 → 본문 키 생략
  let bodyPayload = {};
  if (definition) {
    const formValidation = definition.validate(document);
    if (!formValidation.valid) {
      return { valid: false, message: formValidation.message, payload: {} };
    }
    const serialized = definition.serialize(document);
    if (definition.formCode === 'DYNAMIC') {
      // serializeDynamicForm 결과: { dynamicFields: {...} } — 그대로 spread
      bodyPayload = serialized;
    } else {
      bodyPayload = { [String(selected.formCode).toLowerCase()]: serialized };
    }
  }

  const formCode = String(selected.formCode || '');
  return {
    valid: true,
    message: '',
    payload: {
      formTemplateId: Number(selected.id),
      formCode,
      // 1인이면 백엔드 하위호환을 위해 approverMemberId 도 같이 전송.
      approverMemberId: currentApprovalLine[currentApprovalLine.length - 1],
      approvalLine: currentApprovalLine.slice(),
      title: titleText,
      drafterMemberId: memberId ?? null,
      ...bodyPayload,
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
  currentApprovalLine = [];
  const select = document.getElementById('approval-approver-select');
  if (select instanceof HTMLSelectElement) {
    select.value = '';
    mountApprovalCombobox(select);
  }
  renderApprovalLineSummary();
  showWizardStep(1);
}

/**
 * 위저드 컨트롤러를 초기화한다.
 * @param {{ templates: Array<Record<string, unknown>>, candidates: Array<Record<string, unknown>>, memberId?: number|null, onSubmitted?: () => Promise<void>|void }} options
 * @returns {{ refreshTemplates: (templates: Array<Record<string, unknown>>) => void }}
 */
export function initApprovalWizard(options) {
  formTemplates = Array.isArray(options.templates) ? options.templates : [];
  approverCandidates = Array.isArray(options.candidates) ? options.candidates : [];
  currentApprovalLine = [];

  renderFormTemplates(formTemplates);
  renderApprovalLineSelect();
  ensureApprovalLineSummary();
  renderApprovalLineSummary();
  bindApprovalLineSelect();
  showWizardStep(1);

  document.getElementById('approval-form-template-list')?.addEventListener('change', () => {
    activateSectionForSelected();
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
      // approval-client 가 getApiErrorMessage 로 만든 서버 메시지(ErrorCode.message)를 error.message 로 전달.
      setWizardMessage(error?.message || '제출 중 오류가 발생했습니다.', 'error');
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
