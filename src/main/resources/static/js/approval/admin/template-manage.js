/**
 * 결재 양식 관리 페이지 (admin 전용).
 *
 * - 목록: 회사 사본 + (fork 안 한) 시스템 디폴트
 * - 시스템 디폴트 행: "복사하여 커스터마이즈" 버튼 (fork)
 * - 회사 사본 행: "수정" / "삭제" 버튼
 * - 상단 "새 양식 만들기" 버튼: 모달에서 formCode/name/content 입력 후 생성
 *
 * 백엔드: /api/approval/admin/form-templates (GET 목록·단건, POST 생성·수정·fork·delete)
 *
 * 행/버튼 스타일은 admin/managingDept.html, managingPosition.html 의 패턴을 따른다.
 */

import {
  listAdminFormTemplates,
  forkFormTemplate,
  createFormTemplate,
  updateFormTemplate,
  deleteFormTemplate,
} from '../api/approval-client.js';

/** @type {Array<Record<string, unknown>>} */
let templates = [];

/** @type {'create'|'edit'} */
let modalMode = 'create';

function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

function setMessage(message, tone = 'error') {
  const el = document.getElementById('approval-template-message');
  if (!el) return;
  el.textContent = message;
  el.classList.remove('hidden', 'text-rose-500', 'text-emerald-500');
  el.classList.add(tone === 'success' ? 'text-emerald-500' : 'text-rose-500');
}

function clearMessage() {
  const el = document.getElementById('approval-template-message');
  if (!el) return;
  el.textContent = '';
  el.classList.add('hidden');
}

function extractErrorMessage(error) {
  if (error instanceof Error && error.message) return error.message;
  return '요청 처리에 실패했습니다.';
}

function openModal() {
  const modal = document.getElementById('approval-template-modal');
  if (!modal) return;
  // 외부 컨테이너는 `flex hidden` 둘 다 갖고 있음 (캘린더 모달 패턴) — hidden 토글만으로 표시 제어
  modal.classList.remove('hidden');
}

function closeModal() {
  const modal = document.getElementById('approval-template-modal');
  if (!modal) return;
  modal.classList.add('hidden');
  clearMessage();
}

function openCreateModal() {
  modalMode = 'create';
  const title = document.getElementById('approval-template-modal-title');
  if (title) title.textContent = '새 양식 만들기';

  const idEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-id'));
  const codeEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-form-code'));
  const nameEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-name'));
  const contentEl = /** @type {HTMLTextAreaElement|null} */ (document.getElementById('template-content'));
  const activeEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-is-active'));

  if (idEl) idEl.value = '';
  if (codeEl) { codeEl.value = ''; codeEl.disabled = false; }
  if (nameEl) nameEl.value = '';
  if (contentEl) contentEl.value = '';
  if (activeEl) { activeEl.checked = true; activeEl.disabled = true; }

  clearMessage();
  openModal();
  setTimeout(() => codeEl?.focus(), 50);
}

function openEditModal(template) {
  modalMode = 'edit';
  const title = document.getElementById('approval-template-modal-title');
  if (title) title.textContent = '양식 수정';

  const idEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-id'));
  const codeEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-form-code'));
  const nameEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-name'));
  const contentEl = /** @type {HTMLTextAreaElement|null} */ (document.getElementById('template-content'));
  const activeEl = /** @type {HTMLInputElement|null} */ (document.getElementById('template-is-active'));

  if (idEl) idEl.value = String(template.id ?? '');
  if (codeEl) {
    codeEl.value = String(template.formCode ?? '');
    codeEl.disabled = true; // 양식 코드는 수정 불가 (충돌·결재 문서 참조 보호)
  }
  if (nameEl) nameEl.value = String(template.name ?? '');
  if (contentEl) contentEl.value = String(template.content ?? '');
  if (activeEl) { activeEl.checked = template.isActive !== false; activeEl.disabled = false; }

  clearMessage();
  openModal();
  setTimeout(() => nameEl?.focus(), 50);
}

/**
 * 양식 행 HTML 한 줄. 부서/직급 페이지의 row 패턴을 따른다.
 * @param {Record<string, unknown>} t
 */
function renderRowHtml(t) {
  const isSystem = t.isSystemDefault === true;
  const isActive = t.isActive !== false;

  const sourceBadge = isSystem
    ? '<span class="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300">시스템 디폴트</span>'
    : '<span class="rounded-full bg-indigo-100 px-3 py-1 text-xs font-medium text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300">회사 사본</span>';

  const activeBadge = isActive
    ? '<span class="rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">노출</span>'
    : '<span class="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-500 dark:bg-gray-800 dark:text-gray-400">숨김</span>';

  // 액션 영역 — 부서/직급의 relative+invisible 폭 고정 트릭 그대로 채용
  const actions = isSystem
    ? `
      <div class="relative">
        <span aria-hidden="true" class="invisible block px-8 py-2.5 font-medium rounded-xl shadow-sm">복사하여 커스터마이즈</span>
        <button type="button" data-act="fork"
          class="absolute inset-0 px-8 py-2.5 bg-indigo-400 text-white font-medium rounded-xl hover:bg-indigo-500 transition-all active:scale-95 shadow-sm">
          복사하여 커스터마이즈
        </button>
      </div>`
    : `
      <div class="relative">
        <span aria-hidden="true" class="invisible block px-8 py-2.5 font-medium rounded-xl shadow-sm">수정</span>
        <button type="button" data-act="edit"
          class="absolute inset-0 px-8 py-2.5 bg-indigo-200 text-indigo-700 font-medium rounded-xl hover:bg-indigo-300 transition-all shadow-sm">
          수정
        </button>
      </div>
      <div class="relative">
        <span aria-hidden="true" class="invisible block px-8 py-2.5 font-medium rounded-xl shadow-sm">삭제</span>
        <button type="button" data-act="delete"
          class="btn-delete-hover absolute inset-0 px-8 py-2.5 bg-rose-200 text-rose-500 font-medium rounded-xl transition-all shadow-sm">
          삭제
        </button>
      </div>`;

  const content = t.content ? escapeHtml(t.content) : '<span class="text-gray-400">-</span>';

  return `
    <div data-id="${escapeHtml(t.id)}" data-form-code="${escapeHtml(t.formCode)}" data-system="${isSystem ? '1' : '0'}"
         class="flex items-center justify-between border-b border-gray-400 p-5 transition-colors last:border-b-0 hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-white/5">
      <div class="ml-4 flex min-w-0 flex-1 items-center gap-5">
        <div class="w-72 min-w-0 flex-shrink-0">
          <div class="text-lg font-semibold text-gray-900 dark:text-white">${escapeHtml(t.name)}</div>
          <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">${escapeHtml(t.formCode)}</div>
        </div>
        <div class="hidden min-w-0 flex-1 text-sm text-gray-500 dark:text-gray-300 md:block truncate">${content}</div>
        <div class="flex flex-shrink-0 items-center gap-2">
          ${sourceBadge}
          ${activeBadge}
        </div>
      </div>
      <div class="mr-4 flex items-center gap-3">
        ${actions}
      </div>
    </div>`;
}

function renderRows() {
  const listRoot = document.getElementById('approval-template-list');
  const loading = document.getElementById('approval-template-loading');
  const empty = document.getElementById('approval-template-empty');
  if (!listRoot) return;

  loading?.classList.add('hidden');

  // empty/loading 외의 행만 제거
  Array.from(listRoot.children).forEach((child) => {
    if (child.id !== 'approval-template-loading' && child.id !== 'approval-template-empty') {
      child.remove();
    }
  });

  if (templates.length === 0) {
    empty?.classList.remove('hidden');
    return;
  }
  empty?.classList.add('hidden');

  const html = templates.map(renderRowHtml).join('');
  // empty 노드 앞에 삽입
  const wrapper = document.createElement('div');
  wrapper.innerHTML = html;
  Array.from(wrapper.children).forEach((row) => {
    listRoot.insertBefore(row, empty || null);
  });
}

async function refresh() {
  const loading = document.getElementById('approval-template-loading');
  loading?.classList.remove('hidden');
  try {
    const data = await listAdminFormTemplates();
    templates = Array.isArray(data) ? data : [];
    renderRows();
  } catch (error) {
    console.error('[template-manage] 목록 로드 실패', error);
    templates = [];
    renderRows();
    window.alert('양식 목록을 불러오지 못했습니다.');
  }
}

async function handleRowAction(rowEl, action) {
  const id = Number(rowEl.dataset.id);
  const formCode = String(rowEl.dataset.formCode || '');
  const template = templates.find((t) => Number(t.id) === id);

  try {
    if (action === 'fork') {
      if (!formCode) return;
      const ok = window.confirm(`"${formCode}" 양식을 회사 사본으로 복사할까요?\n복사 후에는 자유롭게 수정할 수 있습니다.`);
      if (!ok) return;
      await forkFormTemplate(formCode);
      await refresh();
      return;
    }

    if (action === 'edit') {
      if (!template) return;
      openEditModal(template);
      return;
    }

    if (action === 'delete') {
      if (!template) return;
      const ok = window.confirm(`"${template.name}" 양식을 삭제할까요?\n같은 코드의 시스템 디폴트가 있으면 자동으로 다시 노출됩니다.`);
      if (!ok) return;
      await deleteFormTemplate(id);
      await refresh();
      return;
    }
  } catch (error) {
    console.error(`[template-manage] ${action} 실패`, error);
    window.alert(extractErrorMessage(error));
  }
}

async function handleSubmit(event) {
  event.preventDefault();

  const idVal = /** @type {HTMLInputElement|null} */ (document.getElementById('template-id'))?.value || '';
  const codeVal = /** @type {HTMLInputElement|null} */ (document.getElementById('template-form-code'))?.value || '';
  const nameVal = /** @type {HTMLInputElement|null} */ (document.getElementById('template-name'))?.value || '';
  const contentVal = /** @type {HTMLTextAreaElement|null} */ (document.getElementById('template-content'))?.value || '';
  const activeVal = /** @type {HTMLInputElement|null} */ (document.getElementById('template-is-active'))?.checked ?? true;

  const formCode = codeVal.trim();
  const name = nameVal.trim();

  if (!name) {
    setMessage('양식명을 입력하세요.');
    return;
  }

  try {
    if (modalMode === 'create') {
      if (!formCode) {
        setMessage('양식 코드를 입력하세요.');
        return;
      }
      if (!/^[A-Za-z0-9_]+$/.test(formCode)) {
        setMessage('양식 코드는 알파벳·숫자·언더스코어만 사용할 수 있습니다.');
        return;
      }
      await createFormTemplate({
        formCode: formCode.toUpperCase(),
        name,
        content: contentVal,
        fieldSchema: null,
      });
    } else {
      const id = Number(idVal);
      if (!Number.isFinite(id)) {
        setMessage('잘못된 양식 ID 입니다.');
        return;
      }
      await updateFormTemplate(id, {
        name,
        content: contentVal,
        isActive: activeVal,
      });
    }
    closeModal();
    await refresh();
  } catch (error) {
    console.error('[template-manage] 저장 실패', error);
    setMessage(extractErrorMessage(error));
  }
}

function bindEvents() {
  document.getElementById('approval-template-create')?.addEventListener('click', () => {
    openCreateModal();
  });

  document.getElementById('approval-template-list')?.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const btn = target.closest('button[data-act]');
    if (!(btn instanceof HTMLElement)) return;
    const rowEl = btn.closest('[data-id]');
    if (!(rowEl instanceof HTMLElement)) return;
    const action = btn.getAttribute('data-act') || '';
    void handleRowAction(rowEl, action);
  });

  document.getElementById('approval-template-modal-close')?.addEventListener('click', closeModal);
  document.getElementById('approval-template-modal-cancel')?.addEventListener('click', closeModal);
  // 백드롭 클릭 시 닫기 (모달 외부 컨테이너는 flex 라서 패널 클릭도 잡혀버리므로 backdrop 만 바인딩)
  document.getElementById('approval-template-modal-backdrop')?.addEventListener('click', closeModal);

  document.getElementById('approval-template-form')?.addEventListener('submit', (event) => {
    void handleSubmit(/** @type {SubmitEvent} */ (event));
  });
  // 저장 버튼이 form 바깥(푸터)에 있으므로 click 으로 form 의 submit 을 트리거
  document.getElementById('approval-template-submit')?.addEventListener('click', () => {
    const form = /** @type {HTMLFormElement|null} */ (document.getElementById('approval-template-form'));
    form?.requestSubmit();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  bindEvents();
  void refresh();
});
