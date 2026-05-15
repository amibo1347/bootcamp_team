/**
 * 동적 본문 양식 (B안).
 *
 * - 회사 사본 + fieldSchema(JSON) 가 있는 양식이면 fixed 폼(VACATION/GENERIC/EXPENSE) 대신 이 모듈이 폼을 그린다.
 * - schema 항목: { key, label, type, required, options?, rows?, computed?, row, col, span }
 *   row/col/span: 12 컬럼 그리드 좌표 (admin 캔버스 그대로 재현). 옛 width('full'|'half') 도 fallback 으로 처리.
 *   type: text | textarea | number | date | select | radio | checkbox | multi-select | computed
 *   computed: { fn: 'diff_days_plus_1'|'sum_numbers', args: {...} } — 의존 필드 변경 시 자동 재계산
 * - serialize 결과는 wizard-controller 가 dynamicFields 로 백엔드에 전송 → ApprovalService 가 ApprovalFieldValue 로 저장.
 *   computed 필드도 그 시점 결과 그대로 함께 저장됨.
 *
 * grid 는 approval.html 의 CSS 가 처리 — section 의 visibility 는 form-registry 의 hidden 클래스 토글에 맡긴다.
 */

const TYPES_WITH_OPTIONS = new Set(['select', 'radio', 'multi-select']);
const SECTION_SELECTOR = '[data-approval-form-section="DYNAMIC"]';
const COL_COUNT = 12;
const DEFAULT_TEXTAREA_ROWS = 4;

/** @type {Array<Record<string, any>>} 가장 최근에 렌더한 schema — computed 재계산에 사용. */
let currentSchema = [];

function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

/** 동적 양식 슬롯이 없으면 wizard-step-draft 의 슬롯 안에 한 번 inject. */
export function mountDynamicFormSection(root = document) {
  if (root.querySelector(SECTION_SELECTOR)) return;
  const slot = root.querySelector('#approval-dynamic-form-slot');
  if (!slot) return;
  const section = document.createElement('div');
  section.setAttribute('data-approval-form-section', 'DYNAMIC');
  // visibility 는 form-registry 의 hidden 클래스 토글로. grid 표시는 CSS (approval.html) 에서.
  section.className = 'hidden';
  // input/change 위임 — computed 필드 자동 재계산
  section.addEventListener('input', () => recomputeAll(section));
  section.addEventListener('change', () => recomputeAll(section));
  slot.appendChild(section);
}

/** 양식 선택 시 호출. fieldSchema 파싱 후 입력 필드들을 12 컬럼 그리드에 그린다. */
export function renderDynamicForm(template, root = document) {
  mountDynamicFormSection(root);
  const section = root.querySelector(SECTION_SELECTOR);
  if (!section) return;

  let schema = [];
  const raw = template?.fieldSchema;
  if (raw) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) schema = parsed;
    } catch (err) {
      console.warn('[dynamic-form] fieldSchema 파싱 실패', err);
    }
  }
  currentSchema = schema;

  if (schema.length === 0) {
    section.innerHTML = `
      <div style="grid-column: 1 / -1;" class="rounded-xl border border-dashed border-gray-300 bg-gray-50 p-4 text-sm text-gray-500 dark:border-gray-700 dark:bg-white/[0.02] dark:text-gray-400">
        이 양식은 본문 입력이 없습니다. 제목·결재선만 작성해 제출하세요.
      </div>`;
    return;
  }

  section.innerHTML = schema.map((field) => renderFieldHtml(field)).join('');
  // 첫 렌더 직후 한 번 — 초깃값으로 computed 시도
  recomputeAll(section);
}

function renderFieldHtml(field) {
  const key = String(field?.key || '');
  const label = String(field?.label || key);
  const type = String(field?.type || 'text');
  const required = field?.required === true;
  const options = Array.isArray(field?.options) ? field.options : [];

  // 좌표 — row/col/span 우선, 없으면 옛 width(full/half) 호환
  const span = Number.isFinite(field?.span)
    ? Math.max(1, Math.min(COL_COUNT, Number(field.span)))
    : (field?.width === 'half' ? 6 : COL_COUNT);
  const col = Number.isFinite(field?.col)
    ? Math.max(1, Math.min(COL_COUNT, Number(field.col)))
    : 1;
  const rowStyle = Number.isFinite(field?.row)
    ? `grid-row: ${Number(field.row)};`
    : '';
  const gridStyle = `grid-column: ${col} / span ${span}; ${rowStyle}`;

  const rows = (Number.isFinite(field?.rows) && field.rows >= 2)
    ? Math.min(Number(field.rows), 20)
    : DEFAULT_TEXTAREA_ROWS;

  const labelHtml = `
    <label class="mb-2 block text-xs font-medium text-gray-700 dark:text-gray-300">
      ${escapeHtml(label)}${required ? ' <span class="text-rose-500">*</span>' : ''}
    </label>`;

  const wrap = (inner) => `
    <div data-field-key="${escapeHtml(key)}" data-field-type="${escapeHtml(type)}"
         style="${gridStyle}"
         class="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-white/[0.03]">
      ${labelHtml}
      ${inner}
    </div>`;

  const inputCls = 'w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-900 dark:text-white';
  const readonlyCls = inputCls + ' bg-gray-100 cursor-not-allowed text-gray-700 dark:bg-gray-800';

  switch (type) {
    case 'textarea':
      return wrap(`<textarea data-input rows="${rows}" class="${inputCls}"></textarea>`);
    case 'number':
      return wrap(`<input data-input type="number" class="${inputCls}" />`);
    case 'date':
      return wrap(`<input data-input type="date" class="${inputCls}" />`);
    case 'select':
      return wrap(`
        <select data-input class="${inputCls}">
          <option value="">선택하세요</option>
          ${options.map((o) => `<option value="${escapeHtml(o)}">${escapeHtml(o)}</option>`).join('')}
        </select>`);
    case 'multi-select':
      return wrap(`
        <select data-input multiple size="${Math.min(Math.max(options.length, 3), 6)}" class="${inputCls}">
          ${options.map((o) => `<option value="${escapeHtml(o)}">${escapeHtml(o)}</option>`).join('')}
        </select>
        <p class="mt-1 text-xs text-gray-400">Ctrl(또는 Cmd) 로 여러 개 선택</p>`);
    case 'radio':
      return wrap(`
        <div class="flex flex-wrap gap-3 text-sm text-gray-700 dark:text-gray-300">
          ${options.map((o) => `
            <label class="flex items-center gap-2">
              <input data-input type="radio" name="dyn_${escapeHtml(key)}" value="${escapeHtml(o)}"
                class="h-4 w-4 text-brand-600" />
              ${escapeHtml(o)}
            </label>`).join('')}
        </div>`);
    case 'checkbox':
      return wrap(`
        <label class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
          <input data-input type="checkbox" class="h-4 w-4 cursor-pointer" />
          ${escapeHtml(label)}
        </label>`);
    case 'computed':
      return wrap(`
        <input data-input data-computed type="text" readonly placeholder="자동 계산"
          class="${readonlyCls}" />
        <p class="mt-1 text-xs text-purple-500 dark:text-purple-300">자동 계산되는 값입니다.</p>`);
    case 'text':
    default:
      return wrap(`<input data-input type="text" class="${inputCls}" />`);
  }
}

// ===== computed 자동 계산 =====

function recomputeAll(section) {
  if (!section || !Array.isArray(currentSchema)) return;
  for (const field of currentSchema) {
    if (!field || field.type !== 'computed' || !field.computed?.fn) continue;
    const value = computeFieldValue(field, section);
    const wrap = section.querySelector(`[data-field-key="${cssAttr(field.key)}"]`);
    const input = wrap?.querySelector('[data-input]');
    if (input instanceof HTMLInputElement) {
      const next = value == null ? '' : String(value);
      if (input.value !== next) input.value = next;
    }
  }
}

/** schema 의 computed 정의에 따라 의존 필드 값을 읽어 계산 결과 반환. 실패 시 ''. */
function computeFieldValue(field, section) {
  const fn = field.computed?.fn;
  const args = field.computed?.args || {};
  if (fn === 'diff_days_plus_1') {
    const s = readFieldText(args.start, section);
    const e = readFieldText(args.end, section);
    if (!s || !e) return '';
    const sd = new Date(s);
    const ed = new Date(e);
    if (Number.isNaN(sd.getTime()) || Number.isNaN(ed.getTime())) return '';
    const days = Math.floor((ed.getTime() - sd.getTime()) / (1000 * 60 * 60 * 24)) + 1;
    return days >= 0 ? days : '';
  }
  if (fn === 'sum_numbers') {
    const keys = Array.isArray(args.keys) ? args.keys : [];
    let sum = 0;
    let any = false;
    for (const k of keys) {
      const v = Number(readFieldText(k, section));
      if (Number.isFinite(v)) { sum += v; any = true; }
    }
    return any ? sum : '';
  }
  return '';
}

function readFieldText(key, section) {
  if (!key) return '';
  const wrap = section.querySelector(`[data-field-key="${cssAttr(key)}"]`);
  if (!wrap) return '';
  const input = wrap.querySelector('[data-input]');
  if (!input) return '';
  return /** @type {HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement} */ (input).value || '';
}

/** key 가 알파벳/숫자/언더스코어만이라 단순 처리. 그래도 따옴표 같은 게 들어와도 안전하게 escape. */
function cssAttr(value) {
  return String(value || '').replace(/(["\\])/g, '\\$1');
}

// ===== 검증·직렬화 (wizard-controller 가 호출) =====

/**
 * 동적 양식 입력값 검증. required 만 체크 (computed 는 자동값이라 검증 X).
 * @returns {{ valid: boolean, message: string, data: Record<string, unknown> }}
 */
export function validateDynamicForm(root = document) {
  const section = root.querySelector(SECTION_SELECTOR);
  if (!section) return { valid: true, message: '', data: {} };

  const fields = section.querySelectorAll('[data-field-key]');
  for (const field of fields) {
    const type = field.getAttribute('data-field-type') || 'text';
    if (type === 'computed') continue;
    const labelEl = field.querySelector('label');
    const labelText = labelEl ? labelEl.textContent.trim().replace(/\s*\*\s*$/, '') : (field.getAttribute('data-field-key') || '');
    const required = !!labelEl?.querySelector('.text-rose-500');
    if (!required) continue;
    const values = readFieldValues(field, type);
    if (values.length === 0) {
      return { valid: false, message: `"${labelText}" 항목을 입력하세요.`, data: {} };
    }
  }
  return { valid: true, message: '', data: {} };
}

/**
 * 동적 양식 입력값을 백엔드 payload 형식으로 직렬화.
 * computed 필드는 input 의 자동 계산 결과 그대로 함께 보내짐 — 결재 시점 값 보존.
 * @returns {{ dynamicFields: Record<string, string[]> }}
 */
export function serializeDynamicForm(root = document) {
  const section = root.querySelector(SECTION_SELECTOR);
  if (!section) return { dynamicFields: {} };

  // 직렬화 직전 한 번 더 재계산 — 사용자가 마지막 입력 후 곧장 제출한 경우에도 최신값 보장
  recomputeAll(section);

  const out = {};
  section.querySelectorAll('[data-field-key]').forEach((field) => {
    const key = field.getAttribute('data-field-key') || '';
    const type = field.getAttribute('data-field-type') || 'text';
    out[key] = readFieldValues(field, type);
  });
  return { dynamicFields: out };
}

/** 동적 양식 입력값을 모두 비운다. (재렌더 시엔 renderDynamicForm 으로 덮어씀) */
export function resetDynamicForm(root = document) {
  const section = root.querySelector(SECTION_SELECTOR);
  if (!section) return;
  section.innerHTML = '';
  currentSchema = [];
}

function readFieldValues(field, type) {
  if (type === 'multi-select') {
    const sel = field.querySelector('select[data-input]');
    if (!(sel instanceof HTMLSelectElement)) return [];
    return Array.from(sel.selectedOptions).map((o) => o.value).filter(Boolean);
  }
  if (type === 'radio') {
    const chosen = field.querySelector('input[type="radio"][data-input]:checked');
    if (chosen instanceof HTMLInputElement && chosen.value) return [chosen.value];
    return [];
  }
  if (type === 'checkbox') {
    const cb = field.querySelector('input[type="checkbox"][data-input]');
    if (cb instanceof HTMLInputElement && cb.checked) return ['true'];
    return [];
  }
  // text, textarea, number, date, select 단일, computed (자동값)
  const input = field.querySelector('[data-input]');
  if (!input) return [];
  const v = /** @type {HTMLInputElement|HTMLTextAreaElement|HTMLSelectElement} */ (input).value || '';
  return v.trim() ? [v] : [];
}
