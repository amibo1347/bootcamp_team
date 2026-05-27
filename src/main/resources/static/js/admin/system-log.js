/*
 * ADMIN 시스템 로그 페이지.
 *  - 모드: raw / daily / monthly / quarterly — 상단 탭으로 전환.
 *  - API: GET /admin/system-log/api?mode=...&page=...
 *  - 권한: 컨트롤러·Security 가 ADMIN 만 통과시킴. JS 는 정상 응답 가정.
 */
(() => {
  let currentMode = 'raw';
  let currentPage = 0;

  function escapeHtml(s) {
    return String(s || '')
      .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
  }

  /** 액션 코드 → 색상 클래스 (배지). */
  function actionBadgeClass(code) {
    switch (code) {
      case 'CREATE':  return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-200';
      case 'UPDATE':  return 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-200';
      case 'DELETE':  return 'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-200';
      case 'APPROVE': return 'bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-200';
      case 'REJECT':  return 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-200';
      case 'RESET':   return 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-200';
      case 'LOGIN':   return 'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200';
      default:        return 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200';
    }
  }

  /** target_type 코드 → 한국어 라벨. */
  function targetTypeLabel(code) {
    switch (code) {
      case 'MEMBER':        return '회원';
      case 'BOARD':         return '게시판';
      case 'ARTICLE':       return '게시글';
      case 'FORM_TEMPLATE': return '결재 양식';
      case 'DEPT':          return '부서';
      case 'POSITION':      return '직급';
      default: return code || '-';
    }
  }

  /** 액션 코드 → 텍스트 색상 클래스 (자연어 문장 내 강조용). */
  function actionTextColor(code) {
    switch (code) {
      case 'CREATE':  return 'text-emerald-600 dark:text-emerald-300';
      case 'UPDATE':  return 'text-blue-600 dark:text-blue-300';
      case 'DELETE':  return 'text-rose-600 dark:text-rose-300';
      case 'APPROVE': return 'text-teal-600 dark:text-teal-300';
      case 'REJECT':  return 'text-orange-600 dark:text-orange-300';
      case 'RESET':   return 'text-amber-600 dark:text-amber-300';
      default:        return 'text-gray-700 dark:text-gray-200';
    }
  }

  /** 한글 종성 유무에 따라 "을/를" 결정. 받침 있으면 "을", 없으면 "를". 영문/숫자/기타는 "을". */
  function objectParticle(text) {
    if (!text) return '을';
    const last = text.replace(/[\s()\[\]]+$/, '').slice(-1);
    const code = last.charCodeAt(0);
    if (code < 0xAC00 || code > 0xD7A3) return '을';
    return ((code - 0xAC00) % 28) === 0 ? '를' : '을';
  }

  function showMessage(text) {
    const el = document.getElementById('systemLogMessage');
    if (!el) return;
    el.textContent = text;
    el.classList.remove('hidden');
  }
  function clearMessage() {
    const el = document.getElementById('systemLogMessage');
    if (!el) return;
    el.textContent = '';
    el.classList.add('hidden');
  }

  /**
   * raw 모드 한 줄 — "누가 → 무엇을 → 어떻게" 자연어 카드.
   *
   * 레이아웃:
   *   ┌──────────────────────────────────────────────────────┐
   *   │ [시각]                                  [액션 배지]   │
   *   │ <강조>홍길동(인사팀/부장)</강조> 님이                 │
   *   │ [회원] <강조>김철수(개발팀/사원)</강조> 을(를)        │
   *   │ <색강조>수정</색강조>했습니다.                        │
   *   │ ↳ 변경 내용: …                                       │
   *   └──────────────────────────────────────────────────────┘
   */
  function rawRowHtml(row) {
    const actionLabel = row.actionLabel || row.actionCode || '-';
    const badgeCls = actionBadgeClass(row.actionCode);
    const actionColor = actionTextColor(row.actionCode);
    const actor = escapeHtml(row.actorName || '(알 수 없음)');
    const targetLabel = row.targetLabel || '-';
    const targetTypeLab = targetTypeLabel(row.targetType);
    const particle = objectParticle(targetLabel);

    const detailBlock = row.detail ? `
      <div class="mt-2.5 rounded-md border-l-2 border-indigo-300 bg-gray-50 px-3 py-2 text-xs leading-relaxed text-gray-700 dark:border-indigo-500/60 dark:bg-white/5 dark:text-gray-300">
        <span class="font-semibold text-gray-500 dark:text-gray-400">↳ 변경 내용 </span>
        <span>${escapeHtml(row.detail)}</span>
      </div>` : '';

    return `
      <div class="px-5 py-4">
        <div class="mb-2 flex items-center justify-between gap-3">
          <span class="text-[11px] text-gray-400 dark:text-gray-500">${escapeHtml(row.createdAt || '')}</span>
          <span class="inline-flex shrink-0 items-center justify-center rounded-full px-2.5 py-1 text-[11px] font-semibold ${badgeCls}">${escapeHtml(actionLabel)}</span>
        </div>
        <p class="text-sm leading-relaxed text-gray-700 dark:text-gray-200">
          <span class="font-bold text-gray-900 dark:text-white">${actor}</span>
          <span class="text-gray-500 dark:text-gray-400">님이 </span>
          <span class="ml-0.5 inline-flex items-center rounded bg-gray-100 px-1.5 py-0.5 text-[11px] font-semibold text-gray-600 dark:bg-gray-700 dark:text-gray-300">${escapeHtml(targetTypeLab)}</span>
          <span class="ml-1 font-bold text-gray-900 dark:text-white">${escapeHtml(targetLabel)}</span>
          <span class="text-gray-500 dark:text-gray-400">${particle} </span>
          <span class="font-bold ${actionColor}">${escapeHtml(actionLabel)}</span>
          <span class="text-gray-500 dark:text-gray-400">했습니다.</span>
        </p>
        ${detailBlock}
      </div>
    `;
  }

  /** summary 모드 한 줄. */
  function summaryRowHtml(row) {
    const periodLabel = (() => {
      const start = row.periodStart || '';
      const end = row.periodEnd || '';
      if (row.periodType === 'DAY')   return start;
      if (row.periodType === 'MONTH') return `${start} ~ ${end}`;
      if (row.periodType === 'QUARTER') return `${start} ~ ${end}`;
      return `${start} ~ ${end}`;
    })();
    const count = row.rawCount != null ? `${row.rawCount}건 압축` : '';
    return `
      <div class="px-5 py-4">
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <div class="text-sm font-semibold text-gray-900 dark:text-white">${escapeHtml(periodLabel)}</div>
          <div class="shrink-0 text-xs text-gray-400">${escapeHtml(count)}</div>
        </div>
        <pre class="mt-2 whitespace-pre-wrap break-words text-xs leading-relaxed text-gray-700 dark:text-gray-300" style="font-family: inherit;">${escapeHtml(row.summary || '')}</pre>
      </div>
    `;
  }

  function renderList(items) {
    const list = document.getElementById('systemLogList');
    const empty = document.getElementById('systemLogEmpty');
    if (!list || !empty) return;
    if (!items.length) {
      list.innerHTML = '';
      empty.classList.remove('hidden');
      return;
    }
    empty.classList.add('hidden');
    list.innerHTML = items.map((it) => (it.kind === 'SUMMARY' ? summaryRowHtml(it) : rawRowHtml(it))).join('');
  }

  function renderPagination(currentPageZero, totalPages) {
    const root = document.getElementById('systemLogPagination');
    if (!root) return;
    if (totalPages <= 1) { root.innerHTML = ''; return; }
    const btn = (label, target, disabled, active) => `
      <button type="button" data-page="${target}" ${disabled ? 'disabled' : ''}
        class="rounded-md border px-3 py-1.5 text-sm transition
          ${active
            ? 'border-indigo-300 bg-indigo-200 font-semibold text-indigo-700 dark:border-indigo-500/60 dark:bg-indigo-500/20 dark:text-indigo-200'
            : 'border-gray-300 bg-white text-gray-700 hover:border-indigo-300 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-50 dark:border-strokedark dark:bg-boxdark dark:text-gray-200 dark:hover:border-indigo-500/60 dark:hover:text-indigo-300'}">${label}</button>`;
    let html = btn('이전', currentPageZero - 1, currentPageZero <= 0, false);
    for (let i = 0; i < totalPages; i += 1) {
      html += btn(String(i + 1), i, false, i === currentPageZero);
    }
    html += btn('다음', currentPageZero + 1, currentPageZero >= totalPages - 1, false);
    root.innerHTML = `<div class="flex flex-wrap items-center justify-center gap-2">${html}</div>`;
    root.querySelectorAll('button[data-page]').forEach((b) => {
      if (b.disabled) return;
      b.addEventListener('click', () => load(currentMode, Number(b.dataset.page) || 0));
    });
  }

  async function load(mode, page) {
    currentMode = mode;
    currentPage = Math.max(0, page || 0);
    clearMessage();
    const list = document.getElementById('systemLogList');
    if (list) list.innerHTML = '<div class="p-10 text-center text-sm text-gray-400">불러오는 중...</div>';
    try {
      const res = await fetch(`/admin/system-log/api?mode=${encodeURIComponent(mode)}&page=${currentPage}`, {
        method: 'GET',
        headers: { Accept: 'application/json' },
        credentials: 'same-origin',
      });
      if (!res.ok) {
        const msg = await window.getApiErrorMessage(res, '시스템 로그를 불러오지 못했습니다.');
        showMessage(msg);
        renderList([]);
        renderPagination(0, 1);
        return;
      }
      const payload = await res.json();
      const items = Array.isArray(payload?.content) ? payload.content : [];
      const totalPages = Number.isFinite(payload?.totalPages) ? Number(payload.totalPages) : 1;
      const pageNumber = Number.isFinite(payload?.number) ? Number(payload.number) : 0;
      renderList(items);
      renderPagination(pageNumber, totalPages);
    } catch (e) {
      showMessage('시스템 로그를 불러오지 못했습니다.');
      renderList([]);
      renderPagination(0, 1);
    }
  }

  function syncTabUi(mode) {
    document.querySelectorAll('.js-log-mode').forEach((btn) => {
      const isActive = btn.dataset.logMode === mode;
      btn.classList.toggle('border-indigo-500', isActive);
      btn.classList.toggle('text-indigo-600', isActive);
      btn.classList.toggle('dark:text-indigo-300', isActive);
      btn.classList.toggle('border-transparent', !isActive);
      btn.classList.toggle('text-gray-500', !isActive);
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.js-log-mode').forEach((btn) => {
      btn.addEventListener('click', () => {
        const mode = btn.dataset.logMode || 'raw';
        syncTabUi(mode);
        load(mode, 0);
      });
    });
    syncTabUi('raw');
    load('raw', 0);
  });
})();
