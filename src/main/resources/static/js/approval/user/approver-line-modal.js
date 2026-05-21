/**
 * 결재선 선택 모달
 * - 1~4 단계의 결재선을 순서대로 선택한다.
 * - 회원 후보 목록(candidates)은 호출자가 미리 받아 전달 (GET /api/approval/approver-candidates).
 * - 검색은 클라이언트 측 이름 LIKE 필터.
 * - 디자인: 캘린더 일정 모달(calendar-event-modal.html)의 색·여백·라운드 톤을 그대로 따른다.
 */

/** @typedef {{ memberId:number, name:string, deptName?:string|null, role?:string, positionLevel?:number|null }} Candidate */

/** 모달 DOM 1회 주입 가드 */
let modalInjected = false;

/** 현재 진행 중 모달 상태 */
const state = {
  /** @type {number} */
  maxCount: 1,
  /** @type {Candidate[]} */
  candidates: [],
  /** @type {Candidate[]} */
  selected: [],
  /** @type {((line: number[])=>void) | null} */
  onConfirm: null,
};

/** HTML 이스케이프 */
function esc(value) {
  const div = document.createElement('div');
  div.textContent = value == null ? '' : String(value);
  return div.innerHTML;
}

/**
 * 모달 HTML을 body 에 1회 주입한다.
 */
function ensureModal() {
  if (modalInjected) return;
  modalInjected = true;

  const wrap = document.createElement('div');
  wrap.innerHTML = `
    <div id="approverLineModal"
         class="fixed inset-0 z-99999 items-center justify-center overflow-y-auto p-4 sm:p-6"
         style="display:none"
         role="dialog" aria-modal="true" aria-labelledby="approverLineModalLabel">
      <div data-approver-modal-dim class="fixed inset-0 z-0 bg-gray-900/40 backdrop-blur-sm dark:bg-gray-950/60"></div>
      <div class="relative z-10 flex w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-stroke bg-white shadow-2xl ring-1 ring-black/5 dark:border-strokedark dark:bg-boxdark dark:ring-white/10"
           style="max-height: min(92vh, 720px)">

        <header class="flex shrink-0 items-start justify-between border-b border-gray-100 px-5 py-4 sm:px-6 sm:py-5 dark:border-gray-800">
          <div class="min-w-0">
            <h2 id="approverLineModalLabel" class="text-lg font-semibold text-gray-900 dark:text-white sm:text-xl">
              결재선 설정
            </h2>
            <p class="mt-1 text-sm leading-relaxed text-gray-500 dark:text-gray-400">
              <span id="approver-line-stage-text">N단계</span> 결재선을 순서대로 선택하세요. 마지막 단계가 최종 결재자입니다.
            </p>
          </div>
          <button type="button" data-approver-modal-close
                  class="rounded-full p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-white/5"
                  aria-label="닫기">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="h-5 w-5" aria-hidden="true">
              <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
            </svg>
          </button>
        </header>

        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6 custom-scrollbar">
          <section class="space-y-4">
            <div class="relative z-20 space-y-3">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">결재자 검색</label>
              <div class="flex items-end gap-3">
                <div class="flex-1">
                  <input type="text" id="approver-line-search" placeholder="이름을 입력하세요"
                         class="w-full rounded border border-stroke bg-gray py-2 px-4 text-black focus:border-primary focus-visible:outline-none dark:border-strokedark dark:bg-meta-4 dark:text-white" />
                </div>
                <button type="button" id="approver-line-search-clear"
                        aria-label="검색어 지우기"
                        class="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-xl border border-gray-300 bg-white text-gray-500 transition hover:bg-gray-50 hover:text-gray-700 dark:border-strokedark dark:bg-meta-4 dark:text-gray-300 dark:hover:bg-white/5">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="h-5 w-5" aria-hidden="true">
                    <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                  </svg>
                </button>
              </div>

              <!-- 선택 현황 -->
              <div class="flex items-center justify-between rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 dark:border-strokedark dark:bg-meta-4">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-200">
                  현재 선택: <span id="approver-line-count">0</span> / <span id="approver-line-max">1</span>명
                </span>
                <button type="button" id="approver-line-reset"
                        class="text-xs font-medium text-rose-500 hover:text-rose-600">
                  초기화
                </button>
              </div>

              <!-- 선택된 결재선 (단계별) -->
              <ul id="approver-line-selected"
                  class="space-y-1 rounded-lg border border-gray-200 bg-white p-2 dark:border-strokedark dark:bg-meta-4">
              </ul>

              <!-- 검색 결과 -->
              <div id="approver-line-results"
                   class="max-h-56 overflow-y-auto rounded-lg border border-gray-300 bg-white p-2 dark:border-strokedark dark:bg-meta-4 dark:text-gray-300">
              </div>
            </div>
          </section>
        </div>

        <footer class="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-gray-100 bg-gray-50/90 px-5 py-4 backdrop-blur-sm dark:border-gray-800 dark:bg-gray-900/80 sm:px-6">
          <button type="button" data-approver-modal-close
                  class="rounded-xl bg-gray-200 px-5 py-2.5 text-sm font-medium text-gray-700 shadow-sm transition hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-800">
            취소
          </button>
          <button type="button" id="approver-line-confirm" disabled
                  class="rounded-xl bg-indigo-400 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-300 focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-gray-300 dark:focus:ring-offset-gray-900">
            확인
          </button>
        </footer>

      </div>
    </div>
  `;
  document.body.appendChild(wrap.firstElementChild);

  bindModalEvents();
}

/**
 * 한 번만 호출되는 이벤트 바인딩.
 */
function bindModalEvents() {
  const modal = document.getElementById('approverLineModal');
  if (!modal) return;

  modal.querySelectorAll('[data-approver-modal-close], [data-approver-modal-dim]').forEach((el) => {
    el.addEventListener('click', closeModal);
  });

  document.getElementById('approver-line-search')?.addEventListener('input', renderResults);
  document.getElementById('approver-line-search-clear')?.addEventListener('click', () => {
    const input = document.getElementById('approver-line-search');
    if (input instanceof HTMLInputElement) input.value = '';
    renderResults();
  });
  document.getElementById('approver-line-reset')?.addEventListener('click', () => {
    state.selected = [];
    renderSelected();
    renderResults();
    syncConfirmState();
  });
  document.getElementById('approver-line-confirm')?.addEventListener('click', () => {
    if (state.selected.length !== state.maxCount) return;
    const ids = state.selected.map((c) => c.memberId);
    closeModal();
    state.onConfirm?.(ids);
  });
}

/**
 * 검색 결과 영역을 현재 검색어 기준으로 다시 렌더.
 */
function renderResults() {
  const box = document.getElementById('approver-line-results');
  if (!box) return;
  const q = (document.getElementById('approver-line-search')?.value || '').trim().toLowerCase();
  const selectedIds = new Set(state.selected.map((c) => c.memberId));

  const rows = state.candidates.filter((c) => {
    if (!q) return true;
    return String(c.name || '').toLowerCase().includes(q);
  });

  if (rows.length === 0) {
    box.innerHTML = '<p class="px-2 py-3 text-sm text-gray-400">검색 결과가 없습니다.</p>';
    return;
  }

  box.innerHTML = rows.map((c) => {
    const already = selectedIds.has(c.memberId);
    const meta = [c.deptName, c.positionName].filter(Boolean).join(' / ');
    const fullCapacity = state.selected.length >= state.maxCount;
    const disabled = already || fullCapacity;
    return `
      <div class="flex items-center justify-between rounded px-2 py-1.5 hover:bg-gray-50 dark:hover:bg-white/5">
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-gray-900 dark:text-white">${esc(c.name)}</p>
          <p class="truncate text-xs text-gray-400">${esc(meta)}</p>
        </div>
        <button type="button"
                data-approver-add="${esc(c.memberId)}"
                ${disabled ? 'disabled' : ''}
                class="shrink-0 rounded-lg px-3 py-1 text-xs font-medium transition ${already
                  ? 'bg-gray-200 text-gray-500 dark:bg-meta-4 dark:text-gray-400'
                  : fullCapacity
                    ? 'bg-gray-100 text-gray-400 cursor-not-allowed dark:bg-gray-700 dark:text-gray-500'
                    : 'bg-indigo-400 text-white hover:bg-indigo-500'}">
          ${already ? '추가됨' : '추가'}
        </button>
      </div>
    `;
  }).join('');

  box.querySelectorAll('[data-approver-add]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = Number(btn.getAttribute('data-approver-add'));
      const c = state.candidates.find((x) => x.memberId === id);
      if (!c) return;
      if (state.selected.length >= state.maxCount) return;
      if (state.selected.some((s) => s.memberId === id)) return;
      state.selected.push(c);
      renderSelected();
      renderResults();
      syncConfirmState();
    });
  });
}

/**
 * 단계별 선택 결과 렌더.
 */
function renderSelected() {
  const ul = document.getElementById('approver-line-selected');
  const cnt = document.getElementById('approver-line-count');
  if (cnt) cnt.textContent = String(state.selected.length);
  if (!ul) return;

  if (state.selected.length === 0) {
    ul.innerHTML = '<li class="px-2 py-2 text-xs text-gray-400">아직 선택된 결재자가 없습니다.</li>';
    return;
  }

  ul.innerHTML = state.selected.map((c, idx) => {
    const isFinal = idx === state.maxCount - 1;
    const stageLabel = isFinal ? `${idx + 1}단계 (최종)` : `${idx + 1}단계`;
    return `
      <li class="flex items-center justify-between rounded px-2 py-1.5">
        <div class="flex items-center gap-2 min-w-0">
          <span class="rounded bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">
            ${esc(stageLabel)}
          </span>
          <span class="truncate text-sm text-gray-800 dark:text-gray-100">${esc(c.name)}</span>
          <span class="truncate text-xs text-gray-400">${esc(c.deptName || '')}</span>
        </div>
        <button type="button"
                data-approver-remove="${esc(c.memberId)}"
                aria-label="제거"
                class="rounded-full p-1 text-gray-400 hover:bg-gray-100 hover:text-rose-500 dark:hover:bg-white/5">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="h-4 w-4" aria-hidden="true">
            <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
          </svg>
        </button>
      </li>
    `;
  }).join('');

  ul.querySelectorAll('[data-approver-remove]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = Number(btn.getAttribute('data-approver-remove'));
      state.selected = state.selected.filter((c) => c.memberId !== id);
      renderSelected();
      renderResults();
      syncConfirmState();
    });
  });
}

/**
 * 확정 버튼 활성/비활성 갱신.
 */
function syncConfirmState() {
  const btn = document.getElementById('approver-line-confirm');
  if (!(btn instanceof HTMLButtonElement)) return;
  btn.disabled = state.selected.length !== state.maxCount;
}

/**
 * 모달 닫기.
 * (tailwind flex/hidden 충돌 회피 위해 inline style 로 토글)
 */
function closeModal() {
  const modal = document.getElementById('approverLineModal');
  if (modal instanceof HTMLElement) modal.style.display = 'none';
}

/**
 * 모달 열기.
 * @param {{ maxCount:number, candidates:Candidate[], onConfirm:(line:number[])=>void }} opts
 */
export function openApproverLineModal(opts) {
  ensureModal();

  state.maxCount = Math.max(1, Math.min(4, Number(opts.maxCount) || 1));
  state.candidates = Array.isArray(opts.candidates) ? opts.candidates : [];
  state.selected = [];
  state.onConfirm = typeof opts.onConfirm === 'function' ? opts.onConfirm : null;

  const stageText = document.getElementById('approver-line-stage-text');
  if (stageText) stageText.textContent = `${state.maxCount}단계`;
  const max = document.getElementById('approver-line-max');
  if (max) max.textContent = String(state.maxCount);
  const search = document.getElementById('approver-line-search');
  if (search instanceof HTMLInputElement) search.value = '';

  renderSelected();
  renderResults();
  syncConfirmState();

  const modal = document.getElementById('approverLineModal');
  if (modal instanceof HTMLElement) modal.style.display = 'flex';
}
