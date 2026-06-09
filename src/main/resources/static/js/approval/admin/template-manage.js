/**
 * 결재 양식 관리 페이지 (admin 전용).
 *
 * - 목록: 회사 사본 + (fork 안 한) 시스템 디폴트
 * - 시스템 디폴트 행: "복사" 버튼 (fork)
 * - 회사 사본 행: "수정" / "삭제" 버튼
 *
 * [B안 모달] 필드 등록 + 12컬럼 그리드 캔버스 (Interact.js 드래그/리사이즈).
 *  - 등록 폼: 화면 이름 / 입력 형식 / 키(자동) / 옵션·rows·계산식(타입별) / 필수
 *  - 캔버스: 등록 시 빈 자리 자동 배치 → 마우스로 드래그(셀 스냅) / 우측 핸들로 폭 리사이즈
 *  - 카드 클릭 → 등록 폼이 수정 모드 (변경 후 같은 등록 버튼)
 *  - 저장: row/col/span 포함된 schema JSON 으로 백엔드 전송
 *
 * 백엔드: /api/approval/admin/form-templates (GET 목록·단건, POST 생성·수정·fork·delete)
 */

import {
  listAdminFormTemplates,
  forkFormTemplate,
  createFormTemplate,
  updateFormTemplate,
  deleteFormTemplate,
} from '../api/approval-client.js';
import { mountApprovalCombobox } from '../common/approval-combobox.js';

// ===== 양식 목록 상태 =====

/** @type {Array<Record<string, unknown>>} */
let templates = [];

/** @type {'create'|'edit'} */
let modalMode = 'create';

// ===== 필드 캔버스 상태 (B안) =====

/**
 * 모달의 필드 정의 상태. 카드 단위.
 * id 는 클라이언트 임시 식별자(저장 시 제외). 좌표는 row/col/span(12 컬럼 그리드).
 * @typedef {Object} FieldRow
 * @property {string} id
 * @property {string} key
 * @property {string} label
 * @property {string} type
 * @property {boolean} required
 * @property {string} options    콤마 구분 string (UI 편의)
 * @property {number} rows       textarea 줄 수
 * @property {Object|null} computed  {fn: string, args: Object} or null
 * @property {number} row        1부터
 * @property {number} col        1~12
 * @property {number} span       1~12
 */

/** @type {FieldRow[]} */
let fields = [];

/** @type {string|null} 등록 폼이 어느 카드의 수정 모드인지 (null = 새 등록) */
let selectedFieldId = null;

let interactInitedCanvas = null;

const FIELD_TYPES = ['text','textarea','number','date','select','radio','checkbox','multi-select','computed'];
const TYPES_WITH_OPTIONS = new Set(['select','radio','multi-select']);
const FIELD_TYPE_LABELS = {
  text: '짧은 텍스트',
  textarea: '긴 텍스트 (여러 줄)',
  number: '숫자',
  date: '날짜',
  select: '선택 (드롭다운)',
  radio: '선택 (라디오 버튼)',
  checkbox: '체크박스 (예/아니오)',
  'multi-select': '여러 개 선택',
  computed: '자동 계산 (값 자동 채움)',
};

const COL_COUNT = 12;
const ROW_HEIGHT = 76;     // 카드 한 행 높이(px)
const ROW_GAP = 8;         // 행 사이 간격
const DEFAULT_SPAN = 6;
const DEFAULT_TEXTAREA_ROWS = 4;

/**
 * 시스템 제공 계산식 카탈로그.
 * args 는 등록 폼에서 사용자가 골라야 할 인자들 — 각 인자는 fields 중 어떤 key 를 참조할지 select 로.
 * 백엔드는 schema 만 저장하고 실제 계산은 신청 화면(dynamic-form.js)에서 수행.
 */
const COMPUTED_FUNCTIONS = [
  {
    value: 'diff_days_plus_1',
    label: '날짜 차이 + 1 (예: 휴가 일수)',
    args: [
      { key: 'start', label: '시작일 필드', kind: 'single', filterTypes: ['date'] },
      { key: 'end',   label: '종료일 필드', kind: 'single', filterTypes: ['date'] },
    ],
  },
  {
    value: 'sum_numbers',
    label: '숫자 필드 합계',
    args: [
      { key: 'keys', label: '합칠 숫자 필드들', kind: 'multi', filterTypes: ['number'] },
    ],
  },
];

function getComputedFn(value) {
  return COMPUTED_FUNCTIONS.find((f) => f.value === value) || null;
}

// ===== 공용 헬퍼 =====

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

function setRegMessage(message) {
  const el = document.getElementById('reg-message');
  if (!el) return;
  el.textContent = message;
  el.classList.remove('hidden');
}

function clearRegMessage() {
  const el = document.getElementById('reg-message');
  if (!el) return;
  el.textContent = '';
  el.classList.add('hidden');
}

function extractErrorMessage(error) {
  if (error instanceof Error && error.message) return error.message;
  return '요청 처리에 실패했습니다.';
}

// ===== 모달 표시 =====

function openModal() {
  const modal = document.getElementById('approval-template-modal');
  if (!modal) return;
  modal.classList.remove('hidden');
  // 모달이 보일 때 캔버스 폭이 정해지므로 그때 다시 렌더 — 카드 픽셀 좌표 정확화
  setTimeout(() => renderCanvas(), 50);
}

function closeModal() {
  const modal = document.getElementById('approval-template-modal');
  if (!modal) return;
  modal.classList.add('hidden');
  clearMessage();
  clearRegMessage();
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

  loadFieldsFromSchema(null);
  resetRegisterForm();

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
    codeEl.disabled = true;
  }
  if (nameEl) nameEl.value = String(template.name ?? '');
  if (contentEl) contentEl.value = String(template.content ?? '');
  if (activeEl) { activeEl.checked = template.active !== false; activeEl.disabled = false; }

  loadFieldsFromSchema(/** @type {string|null} */ (template.fieldSchema ?? null));
  resetRegisterForm();

  clearMessage();
  openModal();
  setTimeout(() => nameEl?.focus(), 50);
}

// ===== 등록 폼 =====

function populateRegisterTypeOptions() {
  const sel = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-type'));
  if (!sel) return;
  sel.innerHTML = FIELD_TYPES.map((t) => `<option value="${t}">${escapeHtml(FIELD_TYPE_LABELS[t] || t)}</option>`).join('');
  mountApprovalCombobox(sel);
}

/**
 * 입력 형식 select 값을 바꾸고 커스텀 콤보박스 UI·연동 로직을 동기화한다.
 * @param {string} value
 */
function setRegTypeValue(value) {
  const typeEl = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-type'));
  if (!typeEl) return;
  typeEl.value = value;
  typeEl.dispatchEvent(new Event('change', { bubbles: true }));
}

function populateComputedFnOptions() {
  const sel = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-computed-fn'));
  if (!sel) return;
  sel.innerHTML = COMPUTED_FUNCTIONS.map((f) => `<option value="${f.value}">${escapeHtml(f.label)}</option>`).join('');
}

function nextAutoFieldKey() {
  const used = new Set(fields.map((f) => String(f.key || '')));
  let n = 1;
  while (used.has(`field_${n}`)) n += 1;
  return `field_${n}`;
}

function resetRegisterForm() {
  selectedFieldId = null;
  const labelEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-label'));
  const keyEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-key'));
  const optsEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-options'));
  const rowsEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-rows'));
  const reqEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-required'));
  const submitBtn = document.getElementById('reg-submit');
  const cancelBtn = document.getElementById('reg-register-cancel') || document.getElementById('template-register-cancel');
  const titleEl = document.getElementById('template-register-title');
  const compFnEl = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-computed-fn'));

  if (labelEl) labelEl.value = '';
  setRegTypeValue('text');
  if (keyEl) keyEl.value = nextAutoFieldKey();
  if (optsEl) optsEl.value = '';
  if (rowsEl) rowsEl.value = String(DEFAULT_TEXTAREA_ROWS);
  if (reqEl) reqEl.checked = false;
  if (compFnEl && COMPUTED_FUNCTIONS[0]) compFnEl.value = COMPUTED_FUNCTIONS[0].value;
  if (submitBtn) submitBtn.textContent = '필드 등록';
  if (cancelBtn) cancelBtn.classList.add('hidden');
  if (titleEl) titleEl.textContent = '새 필드 등록';

  syncRegisterTypeSpecificUi();
  clearRegMessage();
  clearCanvasSelection();
}

function loadFieldIntoRegisterForm(field) {
  selectedFieldId = field.id;
  const labelEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-label'));
  const keyEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-key'));
  const optsEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-options'));
  const rowsEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-rows'));
  const reqEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-required'));
  const submitBtn = document.getElementById('reg-submit');
  const cancelBtn = document.getElementById('template-register-cancel');
  const titleEl = document.getElementById('template-register-title');
  const compFnEl = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-computed-fn'));

  if (labelEl) labelEl.value = field.label || '';
  setRegTypeValue(FIELD_TYPES.includes(field.type) ? field.type : 'text');
  if (keyEl) keyEl.value = field.key || '';
  if (optsEl) optsEl.value = field.options || '';
  if (rowsEl) rowsEl.value = String(field.rows || DEFAULT_TEXTAREA_ROWS);
  if (reqEl) reqEl.checked = field.required === true;
  if (compFnEl && field.computed?.fn) compFnEl.value = field.computed.fn;
  if (submitBtn) submitBtn.textContent = '변경 사항 적용';
  if (cancelBtn) cancelBtn.classList.remove('hidden');
  if (titleEl) titleEl.textContent = `필드 수정: ${field.label || field.key}`;

  syncRegisterTypeSpecificUi(field.computed || null);
  clearRegMessage();
}

function syncRegisterTypeSpecificUi(currentComputed = null) {
  const typeEl = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-type'));
  const type = typeEl ? typeEl.value : 'text';
  const optsWrap = document.getElementById('reg-options-wrap');
  const rowsWrap = document.getElementById('reg-rows-wrap');
  const compWrap = document.getElementById('reg-computed-wrap');
  const reqEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-required'));

  optsWrap?.classList.toggle('hidden', !TYPES_WITH_OPTIONS.has(type));
  rowsWrap?.classList.toggle('hidden', type !== 'textarea');
  const isComputed = type === 'computed';
  compWrap?.classList.toggle('hidden', !isComputed);

  // 계산 필드는 read-only 라 "필수" 의미가 없음 — 자동으로 끄고 비활성
  if (reqEl) {
    if (isComputed) {
      reqEl.checked = false;
      reqEl.disabled = true;
    } else {
      reqEl.disabled = false;
    }
  }

  if (isComputed) {
    renderComputedArgs(currentComputed);
  }
}

function renderComputedArgs(currentComputed) {
  const root = document.getElementById('reg-computed-args');
  const fnEl = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-computed-fn'));
  if (!root || !fnEl) return;
  const fn = getComputedFn(fnEl.value);
  if (!fn) { root.innerHTML = ''; return; }

  // 본인(수정 중)이 아닌 다른 필드 중 type 매칭. computed 필드 자신은 제외.
  const candidates = (filterTypes) =>
    fields.filter((f) => f.id !== selectedFieldId && filterTypes.includes(f.type));

  const currentArgs = currentComputed?.fn === fn.value ? (currentComputed.args || {}) : {};

  root.innerHTML = fn.args.map((arg) => {
    const cands = candidates(arg.filterTypes);
    if (cands.length === 0) {
      return `<div class="text-xs text-rose-500">[${escapeHtml(arg.label)}] 인자로 쓸 ${arg.filterTypes.join('/')} 타입 필드를 먼저 만드세요.</div>`;
    }
    if (arg.kind === 'multi') {
      const selected = Array.isArray(currentArgs[arg.key]) ? currentArgs[arg.key] : [];
      return `
        <label class="block text-xs text-purple-700 dark:text-purple-300">
          ${escapeHtml(arg.label)}
        </label>
        <select data-arg-key="${escapeHtml(arg.key)}" data-arg-kind="multi" multiple
          class="h-20 w-full rounded border border-purple-300 bg-white px-2 text-sm dark:border-purple-500/50 dark:bg-meta-4 dark:text-white">
          ${cands.map((c) => `<option value="${escapeHtml(c.key)}" ${selected.includes(c.key) ? 'selected' : ''}>${escapeHtml(c.label)} (${escapeHtml(c.key)})</option>`).join('')}
        </select>`;
    }
    const cur = currentArgs[arg.key] || '';
    return `
      <label class="block text-xs text-purple-700 dark:text-purple-300">
        ${escapeHtml(arg.label)}
      </label>
      <select data-arg-key="${escapeHtml(arg.key)}" data-arg-kind="single"
        class="h-9 w-full rounded border border-purple-300 bg-white px-2 text-sm dark:border-purple-500/50 dark:bg-meta-4 dark:text-white">
        <option value="">선택하세요</option>
        ${cands.map((c) => `<option value="${escapeHtml(c.key)}" ${cur === c.key ? 'selected' : ''}>${escapeHtml(c.label)} (${escapeHtml(c.key)})</option>`).join('')}
      </select>`;
  }).join('');
}

function readRegisterForm() {
  const labelEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-label'));
  const typeEl = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-type'));
  const keyEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-key'));
  const optsEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-options'));
  const rowsEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-rows'));
  const reqEl = /** @type {HTMLInputElement|null} */ (document.getElementById('reg-required'));
  const compFnEl = /** @type {HTMLSelectElement|null} */ (document.getElementById('reg-computed-fn'));
  const argsRoot = document.getElementById('reg-computed-args');

  const type = typeEl ? typeEl.value : 'text';
  let computed = null;
  if (type === 'computed' && compFnEl) {
    const fn = getComputedFn(compFnEl.value);
    if (fn) {
      const args = {};
      if (argsRoot) {
        argsRoot.querySelectorAll('[data-arg-key]').forEach((el) => {
          const k = el.getAttribute('data-arg-key');
          const kind = el.getAttribute('data-arg-kind');
          if (kind === 'multi') {
            args[k] = Array.from(/** @type {HTMLSelectElement} */ (el).selectedOptions).map((o) => o.value);
          } else {
            args[k] = /** @type {HTMLSelectElement} */ (el).value || '';
          }
        });
      }
      computed = { fn: fn.value, args };
    }
  }

  return {
    label: (labelEl?.value || '').trim(),
    type,
    key: (keyEl?.value || '').trim(),
    options: optsEl?.value || '',
    rows: Number(rowsEl?.value) || DEFAULT_TEXTAREA_ROWS,
    required: !!reqEl?.checked,
    computed,
  };
}

function validateRegisterForm(form, ignoreFieldId = null) {
  if (!form.label) return '화면에 표시될 이름을 입력하세요.';
  if (!form.key) return '시스템 키가 비어 있습니다.';
  if (!/^[A-Za-z0-9_]+$/.test(form.key)) return '키는 알파벳·숫자·언더스코어만 가능합니다.';
  const dup = fields.some((f) => f.id !== ignoreFieldId && f.key === form.key);
  if (dup) return `키 "${form.key}"가 이미 다른 필드에 사용 중입니다.`;
  if (TYPES_WITH_OPTIONS.has(form.type)) {
    const opts = form.options.split(',').map((s) => s.trim()).filter(Boolean);
    if (opts.length === 0) return '선택지를 1개 이상 입력하세요.';
  }
  if (form.type === 'computed') {
    if (!form.computed) return '계산식을 선택하세요.';
    const fn = getComputedFn(form.computed.fn);
    if (!fn) return '계산식이 유효하지 않습니다.';
    for (const arg of fn.args) {
      const v = form.computed.args[arg.key];
      if (arg.kind === 'multi') {
        if (!Array.isArray(v) || v.length === 0) return `[${arg.label}] 1개 이상 선택하세요.`;
      } else {
        if (!v) return `[${arg.label}] 을 선택하세요.`;
      }
    }
  }
  return null;
}

// ===== 필드 추가/수정/제거/배치 =====

function nextFieldId() {
  return `f_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
}

/**
 * 12 컬럼 그리드에서 새 카드가 들어갈 빈 자리 찾기.
 * 같은 row 에 합쳐진 span 이 12 - newSpan 이하인 첫 행에 우측 끝에 배치.
 * 모든 행이 다 차면 새 행에 col=1.
 */
function autoPlace(span) {
  const safeSpan = Math.max(1, Math.min(COL_COUNT, span));
  const occupied = {}; // row -> 사용된 col 집합
  fields.forEach((f) => {
    const r = f.row;
    if (!occupied[r]) occupied[r] = new Set();
    for (let c = f.col; c < f.col + f.span; c += 1) occupied[r].add(c);
  });
  const rows = Object.keys(occupied).map(Number).sort((a, b) => a - b);
  for (const r of rows) {
    // 가장 왼쪽부터 연속된 빈 칸 찾기
    for (let start = 1; start <= COL_COUNT - safeSpan + 1; start += 1) {
      let ok = true;
      for (let c = start; c < start + safeSpan; c += 1) {
        if (occupied[r].has(c)) { ok = false; break; }
      }
      if (ok) return { row: r, col: start, span: safeSpan };
    }
  }
  const nextRow = rows.length === 0 ? 1 : Math.max(...rows) + 1;
  return { row: nextRow, col: 1, span: safeSpan };
}

function registerOrUpdateField() {
  const form = readRegisterForm();
  const err = validateRegisterForm(form, selectedFieldId);
  if (err) { setRegMessage(err); return; }

  if (selectedFieldId) {
    // 수정 모드
    const idx = fields.findIndex((f) => f.id === selectedFieldId);
    if (idx >= 0) {
      const old = fields[idx];
      fields[idx] = {
        ...old,
        key: form.key,
        label: form.label,
        type: form.type,
        required: form.required,
        options: form.options,
        rows: form.rows,
        computed: form.computed,
      };
    }
    resetRegisterForm();
  } else {
    // 새 등록 — 빈 자리 자동 배치
    const placed = autoPlace(DEFAULT_SPAN);
    fields.push({
      id: nextFieldId(),
      key: form.key,
      label: form.label,
      type: form.type,
      required: form.required,
      options: form.options,
      rows: form.rows,
      computed: form.computed,
      ...placed,
    });
    resetRegisterForm();
  }
  renderCanvas();
}

function removeField(id) {
  fields = fields.filter((f) => f.id !== id);
  if (selectedFieldId === id) resetRegisterForm();
  renderCanvas();
}

function selectFieldById(id) {
  const f = fields.find((x) => x.id === id);
  if (!f) return;
  loadFieldIntoRegisterForm(f);
  // 카드 시각 강조
  document.querySelectorAll('.canvas-card').forEach((el) => el.classList.remove('is-selected'));
  document.querySelector(`.canvas-card[data-field-id="${id}"]`)?.classList.add('is-selected');
}

function clearCanvasSelection() {
  document.querySelectorAll('.canvas-card').forEach((el) => el.classList.remove('is-selected'));
}

// ===== 캔버스 렌더 + Interact.js =====

function getCanvasMetrics() {
  const canvas = document.getElementById('template-canvas');
  if (!canvas) return null;
  const width = canvas.clientWidth || 600;
  const colWidth = width / COL_COUNT;
  return { width, colWidth };
}

function gridToPx(field) {
  const m = getCanvasMetrics();
  if (!m) return { left: 0, top: 0, width: 100, height: ROW_HEIGHT };
  return {
    left: (field.col - 1) * m.colWidth,
    top: (field.row - 1) * (ROW_HEIGHT + ROW_GAP),
    width: field.span * m.colWidth,
    height: ROW_HEIGHT,
  };
}

function snapPxToGrid(left, top, width) {
  const m = getCanvasMetrics();
  if (!m) return { col: 1, row: 1, span: DEFAULT_SPAN };
  const col = Math.max(1, Math.min(COL_COUNT, Math.round(left / m.colWidth) + 1));
  const row = Math.max(1, Math.round(top / (ROW_HEIGHT + ROW_GAP)) + 1);
  let span = Math.max(1, Math.min(COL_COUNT, Math.round(width / m.colWidth)));
  // col + span 이 12 초과면 우측에 맞춤
  if (col + span - 1 > COL_COUNT) span = COL_COUNT - col + 1;
  return { col, row, span };
}

function renderCanvas() {
  const canvas = document.getElementById('template-canvas');
  const empty = document.getElementById('template-canvas-empty');
  if (!canvas) return;

  // 캔버스 높이는 가장 아래 행 + 1 만큼
  const maxRow = fields.reduce((acc, f) => Math.max(acc, f.row + 1), 4); // 최소 4행 보이게
  canvas.style.minHeight = `${maxRow * (ROW_HEIGHT + ROW_GAP)}px`;

  // 카드 노드 정리 — 비교를 단순화하기 위해 통째 다시 그림
  canvas.innerHTML = fields.map((f) => renderCardHtml(f)).join('');

  // empty 상태
  if (fields.length === 0) {
    empty?.classList.remove('hidden');
  } else {
    empty?.classList.add('hidden');
  }

  bindCardInteractions();
  applyCardPositions();
}

function renderCardHtml(field) {
  const isComputed = field.type === 'computed';
  const typeLabel = FIELD_TYPE_LABELS[field.type] || field.type;
  const meta = isComputed
    ? `🧮 ${typeLabel}`
    : `${typeLabel}${field.required ? ' · 필수' : ''}`;
  return `
    <div class="canvas-card${isComputed ? ' is-computed' : ''}"
         data-field-id="${escapeHtml(field.id)}">
      <div class="card-label" title="${escapeHtml(field.label)}">${escapeHtml(field.label)}</div>
      <div class="card-meta" title="${escapeHtml(field.key)}">${escapeHtml(meta)} · ${escapeHtml(field.key)}</div>
      <div class="card-actions">
        <button type="button" data-act="remove" aria-label="필드 삭제">×</button>
      </div>
      <div class="resize-handle-right"></div>
    </div>`;
}

function applyCardPositions() {
  document.querySelectorAll('.canvas-card').forEach((el) => {
    const id = el.getAttribute('data-field-id');
    const field = fields.find((f) => f.id === id);
    if (!field) return;
    const px = gridToPx(field);
    el.style.left = `${px.left}px`;
    el.style.top = `${px.top}px`;
    el.style.width = `${px.width}px`;
    el.style.height = `${px.height}px`;
    // Interact.js 가 inline transform 으로 임시 이동 처리하니 초기화
    el.style.transform = '';
    el.removeAttribute('data-x');
    el.removeAttribute('data-y');
  });
}

function bindCardInteractions() {
  if (typeof window.interact !== 'function') {
    console.warn('[template-manage] interact.js 가 로드되지 않았습니다.');
    return;
  }
  if (interactInitedCanvas) return; // 위임 방식이라 한 번만 바인딩하면 됨 — selector 가 새 카드도 잡음
  interactInitedCanvas = true;

  // 드래그 ----
  window.interact('.canvas-card').draggable({
    listeners: {
      start(event) {
        const id = event.target.getAttribute('data-field-id');
        if (id) selectFieldById(id);
      },
      move(event) {
        const t = event.target;
        const x = (parseFloat(t.getAttribute('data-x')) || 0) + event.dx;
        const y = (parseFloat(t.getAttribute('data-y')) || 0) + event.dy;
        t.style.transform = `translate(${x}px, ${y}px)`;
        t.setAttribute('data-x', x);
        t.setAttribute('data-y', y);
      },
      end(event) {
        const t = event.target;
        const id = t.getAttribute('data-field-id');
        const field = fields.find((f) => f.id === id);
        if (!field) { t.style.transform = ''; return; }
        const dx = parseFloat(t.getAttribute('data-x')) || 0;
        const dy = parseFloat(t.getAttribute('data-y')) || 0;
        const startPx = gridToPx(field);
        const newLeft = startPx.left + dx;
        const newTop = startPx.top + dy;
        const newWidth = startPx.width;
        const snapped = snapPxToGrid(newLeft, newTop, newWidth);
        field.col = snapped.col;
        field.row = snapped.row;
        field.span = snapped.span;
        renderCanvas();
        // 선택 상태 유지
        selectFieldById(id);
      },
    },
    inertia: false,
    modifiers: [
      window.interact.modifiers.restrictRect({ restriction: 'parent' }),
    ],
  });

  // 리사이즈 (가로만, 우측 핸들) ----
  window.interact('.canvas-card').resizable({
    edges: { right: '.resize-handle-right' },
    listeners: {
      start(event) {
        const id = event.target.getAttribute('data-field-id');
        if (id) selectFieldById(id);
      },
      move(event) {
        const t = event.target;
        const w = event.rect.width;
        t.style.width = `${w}px`;
      },
      end(event) {
        const t = event.target;
        const id = t.getAttribute('data-field-id');
        const field = fields.find((f) => f.id === id);
        if (!field) return;
        const startPx = gridToPx(field);
        const newWidth = parseFloat(t.style.width) || startPx.width;
        const snapped = snapPxToGrid(startPx.left, startPx.top, newWidth);
        field.span = snapped.span; // 폭만 변경
        renderCanvas();
        selectFieldById(id);
      },
    },
    modifiers: [
      window.interact.modifiers.restrictSize({ min: { width: 60, height: ROW_HEIGHT } }),
    ],
  });
}

// 카드 클릭/액션 — 위임
function bindCanvasClick() {
  const canvas = document.getElementById('template-canvas');
  if (!canvas) return;
  canvas.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const removeBtn = target.closest('button[data-act="remove"]');
    if (removeBtn instanceof HTMLElement) {
      const card = removeBtn.closest('.canvas-card');
      const id = card?.getAttribute('data-field-id');
      if (id) {
        if (window.confirm('이 필드를 삭제할까요?')) removeField(id);
      }
      event.stopPropagation();
      return;
    }
    const card = target.closest('.canvas-card');
    if (card instanceof HTMLElement) {
      const id = card.getAttribute('data-field-id');
      if (id) selectFieldById(id);
    }
  });
}

// ===== schema 직렬화/로드 =====

function loadFieldsFromSchema(schemaJson) {
  fields = [];
  if (schemaJson) {
    try {
      const arr = JSON.parse(schemaJson);
      if (Array.isArray(arr)) {
        let nextRow = 1;
        arr.forEach((field, i) => {
          const type = FIELD_TYPES.includes(field?.type) ? field.type : 'text';
          const span = Number.isFinite(field?.span) ? Math.max(1, Math.min(COL_COUNT, Number(field.span)))
                       : (field?.width === 'half' ? 6 : 12); // 호환: 옛 width 도 받음
          const row = Number.isFinite(field?.row) ? Number(field.row) : (++nextRow, nextRow);
          const col = Number.isFinite(field?.col) ? Math.max(1, Math.min(COL_COUNT, Number(field.col))) : 1;
          fields.push({
            id: nextFieldId(),
            key: String(field?.key ?? `field_${i + 1}`),
            label: String(field?.label ?? ''),
            type,
            required: field?.required === true,
            options: Array.isArray(field?.options) ? field.options.join(', ') : '',
            rows: Number.isFinite(field?.rows) ? Number(field.rows) : DEFAULT_TEXTAREA_ROWS,
            computed: field?.computed && field.computed.fn ? field.computed : null,
            row,
            col,
            span,
          });
        });
      }
    } catch (err) {
      console.warn('[template-manage] fieldSchema 파싱 실패', err);
    }
  }
  renderCanvas();
}

function buildFieldSchemaJson() {
  if (fields.length === 0) return null;
  const out = fields.map((f) => {
    const item = {
      key: f.key,
      label: f.label,
      type: f.type,
      required: !!f.required,
      row: f.row,
      col: f.col,
      span: f.span,
    };
    if (f.type === 'textarea') {
      item.rows = Number.isFinite(f.rows) && f.rows >= 2 ? Math.min(f.rows, 20) : DEFAULT_TEXTAREA_ROWS;
    }
    if (TYPES_WITH_OPTIONS.has(f.type)) {
      const opts = (f.options || '').split(',').map((s) => s.trim()).filter(Boolean);
      item.options = opts;
    }
    if (f.type === 'computed' && f.computed) {
      item.computed = f.computed;
    }
    return item;
  });
  return JSON.stringify(out);
}

// ===== 양식 목록 (변경 없음) =====

function renderRowHtml(t) {
  const isSystem = t.systemDefault === true;
  const isActive = t.active !== false;

  const sourceBadge = isSystem
    ? '<span class="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300">시스템 디폴트</span>'
    : '<span class="rounded-full bg-indigo-100 px-3 py-1 text-xs font-medium text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300">회사 사본</span>';

  const activeBadge = isActive
    ? '<span class="rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">노출</span>'
    : '<span class="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-500 dark:bg-gray-800 dark:text-gray-400">숨김</span>';

  const actions = isSystem
    ? `
      <div class="relative">
        <span aria-hidden="true" class="invisible block whitespace-nowrap rounded-xl px-6 py-2 text-sm font-medium shadow-sm">복사</span>
        <button type="button" data-act="fork"
          class="absolute inset-0 whitespace-nowrap rounded-xl bg-indigo-400 px-6 py-2 text-sm font-medium text-white shadow-sm transition-all hover:bg-indigo-500 active:scale-[0.98]">
          복사
        </button>
      </div>`
    : `
      <div class="relative">
        <span aria-hidden="true" class="invisible block whitespace-nowrap rounded-xl px-6 py-2 text-sm font-medium shadow-sm">수정</span>
        <button type="button" data-act="edit"
          class="absolute inset-0 whitespace-nowrap rounded-xl bg-indigo-200 px-6 py-2 text-sm font-medium text-indigo-700 shadow-sm transition-all hover:bg-indigo-300">
          수정
        </button>
      </div>
      <div class="relative">
        <span aria-hidden="true" class="invisible block whitespace-nowrap rounded-xl px-6 py-2 text-sm font-medium shadow-sm">삭제</span>
        <button type="button" data-act="delete"
          class="btn-delete-hover absolute inset-0 whitespace-nowrap rounded-xl bg-rose-200 px-6 py-2 text-sm font-medium text-rose-500 shadow-sm transition-all">
          삭제
        </button>
      </div>`;

  const content = t.content ? escapeHtml(t.content) : '<span class="text-gray-400">-</span>';

  return `
    <div data-id="${escapeHtml(t.id)}" data-form-code="${escapeHtml(t.formCode)}" data-system="${isSystem ? '1' : '0'}"
         class="flex items-center justify-between gap-6 border-b border-gray-400 px-5 py-3.5 transition-colors last:border-b-0 hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-white/5">
      <div class="ml-4 flex min-w-0 flex-1 items-center gap-8 lg:gap-10">
        <div class="min-w-0 flex-1">
          <div class="text-lg font-semibold text-gray-900 dark:text-white">${escapeHtml(t.name)}</div>
          <div class="mt-0.5 text-sm text-gray-500 dark:text-gray-300 truncate">${content}</div>
          <div class="mt-0.5 text-xs text-gray-400 dark:text-gray-500">${escapeHtml(t.formCode)}</div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          ${sourceBadge}
          ${activeBadge}
        </div>
      </div>
      <div class="mr-4 flex shrink-0 items-center gap-3">
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
  listRoot.replaceChildren();

  if (templates.length === 0) {
    empty?.classList.remove('hidden');
    return;
  }
  empty?.classList.add('hidden');

  const html = templates.map(renderRowHtml).join('');
  const wrapper = document.createElement('div');
  wrapper.innerHTML = html;
  Array.from(wrapper.children).forEach((row) => {
    listRoot.appendChild(row);
  });
}

async function refresh() {
  const loading = document.getElementById('approval-template-loading');
  const empty = document.getElementById('approval-template-empty');
  const listRoot = document.getElementById('approval-template-list');
  loading?.classList.remove('hidden');
  empty?.classList.add('hidden');
  listRoot?.replaceChildren([]);
  try {
    const data = await listAdminFormTemplates();
    templates = Array.isArray(data) ? data : [];
    renderRows();
  } catch (error) {
    console.error('[template-manage] 목록 로드 실패', error);
    templates = [];
    renderRows();
    window.alert(extractErrorMessage(error));
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

  const fieldSchema = buildFieldSchemaJson();

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
        fieldSchema,
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
        active: activeVal,
        fieldSchema,
      });
    }
    closeModal();
    await refresh();
  } catch (error) {
    console.error('[template-manage] 저장 실패', error);
    setMessage(extractErrorMessage(error));
  }
}

// ===== 이벤트 바인딩 =====

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
  document.getElementById('approval-template-modal-backdrop')?.addEventListener('click', closeModal);

  document.getElementById('approval-template-form')?.addEventListener('submit', (event) => {
    void handleSubmit(/** @type {SubmitEvent} */ (event));
  });
  document.getElementById('approval-template-submit')?.addEventListener('click', () => {
    const form = /** @type {HTMLFormElement|null} */ (document.getElementById('approval-template-form'));
    form?.requestSubmit();
  });

  // 등록 폼 이벤트
  populateRegisterTypeOptions();
  populateComputedFnOptions();

  document.getElementById('reg-type')?.addEventListener('change', () => {
    syncRegisterTypeSpecificUi();
  });
  document.getElementById('reg-computed-fn')?.addEventListener('change', () => {
    renderComputedArgs(null);
  });
  document.getElementById('reg-submit')?.addEventListener('click', () => {
    registerOrUpdateField();
  });
  document.getElementById('template-register-cancel')?.addEventListener('click', () => {
    resetRegisterForm();
  });

  // 캔버스
  bindCanvasClick();

  // 모달이 열려 있을 때 창 크기 변경되면 카드 위치 재조정
  window.addEventListener('resize', () => {
    if (!document.getElementById('approval-template-modal')?.classList.contains('hidden')) {
      applyCardPositions();
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  bindEvents();
  void refresh();
});
