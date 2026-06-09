/**
 * 헤더 알림(Alert) — /api/alerts 연동
 * - GET 전체 목록(백엔드 페이징 없음): 클라이언트에서 최신순 10건씩 확장
 * - POST /api/alerts/{id}/read 읽음, POST /api/alerts/{id} 단일 삭제
 * - GET /api/alerts/unread/count → 헤더 종 아이콘 숫자 배지(0이면 숨김, 100건 이상은 99+)
 * - 드롭다운 높이 고정(h-[480px]), 목록 영역만 스크롤
 */
(function () {
  'use strict';

  const POLL_MS = 45000;
  const PAGE_SIZE = 10;
  const API = '/api/alerts';
  const API_UNREAD_COUNT = `${API}/unread/count`;

  /** @type {ReturnType<typeof setInterval> | null} */
  let pollTimer = null;

  /** 서버에서 받은 최신순 전체(캐시). 백엔드 Pageable 없음 → 한 번에 수신 후 슬라이스 */
  /** @type {Record<string, unknown>[]} */
  let allRows = [];

  /** 현재 화면에 그릴 상한(최신부터 N개) */
  let visibleCount = PAGE_SIZE;

  /** DOM에 붙인 알림 행 개수(더 보기 시 append 범위 계산용) */
  let renderedInDom = 0;

  function getEls() {
    return {
      bell: document.querySelector('[data-header-alerts-bell]'),
      list: document.getElementById('header-alert-list'),
      badge: document.getElementById('header-alert-badge'),
      loadMore: document.getElementById('header-alert-load-more'),
    };
  }

  /**
   * @param {Response} res
   */
  async function parseJsonSafe(res) {
    const text = await res.text();
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch {
      return null;
    }
  }

  /**
   * GET /api/alerts/unread/count 응답의 count를 숫자로 정규화한다.
   * @param {unknown} raw
   * @returns {number} 0 이상 정수
   */
  function normalizeUnreadCount(raw) {
    if (raw == null) return 0;
    const n = Number(raw);
    if (!Number.isFinite(n) || n <= 0) return 0;
    return Math.min(Math.floor(n), 999999);
  }

  /**
   * 배지에 표시할 라벨(1~99 그대로, 100 이상은 99+).
   * @param {number} count
   * @returns {string}
   */
  function formatBadgeLabel(count) {
    if (count <= 0) return '';
    if (count > 99) return '99+';
    return String(count);
  }

  /**
   * 헤더 종 아이콘 배지: 미읽음 개수 API 반영. 0이면 숨김.
   */
  async function refreshUnreadBadge() {
    const { badge } = getEls();
    if (!badge) return;

    let count = 0;
    try {
      const res = await fetch(API_UNREAD_COUNT, { credentials: 'same-origin' });
      if (!res.ok) return;
      const data = await parseJsonSafe(res);
      count = normalizeUnreadCount(data && /** @type {{ count?: unknown }} */ (data).count);
    } catch {
      return;
    }

    const show = count > 0;
    const label = formatBadgeLabel(count);
    badge.textContent = show ? label : '';
    badge.classList.toggle('hidden', !show);
    badge.classList.toggle('inline-flex', show);
    if (show) {
      badge.setAttribute('aria-label', `읽지 않은 알림 ${count}건`);
    } else {
      badge.removeAttribute('aria-label');
    }
  }

  /**
   * @param {string | number[] | null | undefined} v
   */
  function formatCreatedAt(v) {
    if (v == null) return '';
    let d;
    if (Array.isArray(v)) {
      const [y, mo, day, h = 0, mi = 0, s = 0] = v;
      d = new Date(y, mo - 1, day, h, mi, s);
    } else {
      d = new Date(v);
    }
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' });
  }

  /**
   * @param {Record<string, unknown>} row
   */
  function isReadRow(row) {
    if (row.read === true) return true;
    if (row.isRead === true) return true;
    return false;
  }

  /**
   * @param {Record<string, unknown>} row
   */
  function displayTitle(row) {
    const t = row.title;
    if (typeof t === 'string' && t.trim()) return t;
    return '알림';
  }

  /**
   * @param {Record<string, unknown>} row
   */
  function displayContent(row) {
    const c = row.content;
    if (typeof c === 'string' && c.trim()) return c;
    return '';
  }

  /**
   * @param {Record<string, unknown>} row
   */
  function linkHref(row) {
    const link = row.link;
    if (typeof link === 'string' && link.trim()) return link;
    return '#';
  }

  /**
   * 알림 한 줄(<a>) 공통·읽음·미읽음 Tailwind 클래스.
   * - 저채도 slate/zinc, 배경은 호버 시에만 살짝.
   * - 미읽음 표시: 왼쪽 작은 점 — 헤더 배지와 동일 bg-orange-500 (세로선 없음)
   * - 제목·메타는 data-alert-part 로 읽음 여부에 따라 글자 농도만 달리 함.
   */
  const CLS_ROW_BASE =
    'group text-theme-sm relative flex items-start gap-2 rounded-lg py-2.5 pr-9 pl-3 transition-colors duration-150 ';
  const CLS_ROW_UNREAD =
    'bg-transparent hover:bg-zinc-100/40 dark:hover:bg-zinc-900/25 ';
  const CLS_ROW_READ =
    'bg-transparent hover:bg-zinc-50/70 dark:hover:bg-white/[0.03] ';

  /** 제목·메타를 감싸는 컬럼(점 옆 본문 영역) */
  const CLS_ALERT_INNER = 'flex min-w-0 flex-1 flex-col gap-0.5';

  /** 미읽음 점: 헤더 종 배지(#header-alert-badge)의 원 배경색(bg-orange-500)과 동일 톤 */
  const CLS_DOT_UNREAD =
    'pointer-events-none mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-orange-500 dark:bg-orange-500';
  const CLS_DOT_READ =
    'pointer-events-none mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-transparent';

  const CLS_TITLE_UNREAD =
    'line-clamp-2 text-left font-medium text-slate-900 dark:text-slate-100';
  const CLS_TITLE_READ =
    'line-clamp-2 text-left font-normal text-gray-500 dark:text-gray-400';
  const CLS_META_UNREAD =
    'text-theme-xs text-left text-slate-600/95 dark:text-slate-400';
  const CLS_META_READ =
    'text-theme-xs text-left text-gray-400 dark:text-gray-500';

  /**
   * 알림 링크 행의 읽음/미읽음 외형을 일괄 적용한다.
   * @param {HTMLAnchorElement} a
   * @param {boolean} read
   */
  function applyAlertRowAppearance(a, read) {
    a.className = CLS_ROW_BASE + (read ? CLS_ROW_READ : CLS_ROW_UNREAD);
    const dotSpan = a.querySelector('[data-alert-part="dot"]');
    if (dotSpan) {
      dotSpan.className = read ? CLS_DOT_READ : CLS_DOT_UNREAD;
    }
    const titleSpan = a.querySelector('[data-alert-part="title"]');
    const metaSpan = a.querySelector('[data-alert-part="meta"]');
    if (titleSpan) {
      titleSpan.className = read ? CLS_TITLE_READ : CLS_TITLE_UNREAD;
    }
    if (metaSpan) {
      metaSpan.className = read ? CLS_META_READ : CLS_META_UNREAD;
    }
  }

  /**
   * 한 알림 행(li) 생성 — 본문 링크 + 삭제 버튼
   * @param {Record<string, unknown>} row
   */
  function buildAlertLi(row) {
    const id = row.alertId;
    if (typeof id !== 'number' && typeof id !== 'string') {
      return null;
    }

    const read = isReadRow(row);
    const li = document.createElement('li');
    li.className = 'relative';

    const a = document.createElement('a');
    a.href = linkHref(row);
    a.dataset.alertId = String(id);
    a.dataset.unread = read ? 'false' : 'true';

    const dot = document.createElement('span');
    dot.setAttribute('data-alert-part', 'dot');
    dot.setAttribute('aria-hidden', 'true');

    const inner = document.createElement('span');
    inner.className = CLS_ALERT_INNER;

    const titleEl = document.createElement('span');
    titleEl.setAttribute('data-alert-part', 'title');
    titleEl.className = read ? CLS_TITLE_READ : CLS_TITLE_UNREAD;
    titleEl.textContent = displayTitle(row);

    const meta = document.createElement('span');
    meta.setAttribute('data-alert-part', 'meta');
    meta.className = read ? CLS_META_READ : CLS_META_UNREAD;
    const timeStr = formatCreatedAt(
      /** @type {string | number[] | undefined} */ (row.createdAt)
    );
    const contentStr = displayContent(row);
    meta.textContent = [timeStr, contentStr].filter(Boolean).join(' · ');

    inner.appendChild(titleEl);
    inner.appendChild(meta);
    a.appendChild(dot);
    a.appendChild(inner);
    applyAlertRowAppearance(a, read);

    const del = document.createElement('button');
    del.type = 'button';
    del.dataset.alertDelete = String(id);
    del.setAttribute('aria-label', '알림 삭제');
    del.className =
      'text-theme-xs absolute top-2 right-2 rounded-md p-1.5 text-gray-400 hover:bg-gray-200 hover:text-gray-700 dark:hover:bg-white/10 dark:hover:text-gray-200';

    const delSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    delSvg.setAttribute('class', 'pointer-events-none h-4 w-4');
    delSvg.setAttribute('viewBox', '0 0 24 24');
    delSvg.setAttribute('fill', 'none');
    delSvg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('stroke', 'currentColor');
    path.setAttribute('stroke-width', '1.5');
    path.setAttribute('stroke-linecap', 'round');
    path.setAttribute(
      'd',
      'M6 6l12 12M18 6L6 18'
    );
    delSvg.appendChild(path);
    del.appendChild(delSvg);

    li.appendChild(a);
    li.appendChild(del);
    return li;
  }

  /**
   * 목록에서 알림 행·빈 상태 메시지 li만 제거(이벤트 위임 대상 유지)
   * @param {HTMLUListElement} list
   */
  function clearAlertItems(list) {
    const toRemove = list.querySelectorAll('[data-alert-item]');
    toRemove.forEach((n) => n.remove());
  }

  function updateLoadMoreVisibility() {
    const { loadMore } = getEls();
    if (!loadMore) return;
    const more = visibleCount < allRows.length;
    loadMore.classList.toggle('hidden', !more);
  }

  /**
   * 빈 상태 한 줄 표시
   * @param {HTMLUListElement} list
   */
  function showEmptyState(list) {
    clearAlertItems(list);
    const li = document.createElement('li');
    li.dataset.alertItem = 'empty';
    li.className = 'text-theme-sm px-3 py-8 text-center text-gray-500 dark:text-gray-400';
    li.textContent = '알림이 없습니다';
    list.appendChild(li);
    renderedInDom = 0;
    updateLoadMoreVisibility();
  }

  /**
   * 캐시 기준 [0, visibleCount) 구간을 처음부터 다시 그림(폴링·삭제·동기화)
   * @param {HTMLUListElement} list
   */
  function redrawVisibleFromScratch(list) {
    clearAlertItems(list);
    const n = Math.min(visibleCount, allRows.length);
    for (let i = 0; i < n; i++) {
      const li = buildAlertLi(allRows[i]);
      if (li) {
        li.dataset.alertItem = '1';
        list.appendChild(li);
      }
    }
    renderedInDom = n;
    updateLoadMoreVisibility();
  }

  /**
   * 더 보기: 이미 그린 개수 이후부터 새 행만 하단에 append
   * @param {HTMLUListElement} list
   */
  function appendNextSlice(list) {
    const target = Math.min(visibleCount, allRows.length);
    for (let i = renderedInDom; i < target; i++) {
      const li = buildAlertLi(allRows[i]);
      if (li) {
        li.dataset.alertItem = '1';
        list.appendChild(li);
      }
    }
    renderedInDom = target;
    updateLoadMoreVisibility();
  }

  /**
   * 캐시(allRows)와 visibleCount를 반영해 DOM 갱신
   * @param {boolean} resetToFirstPage true면 최신 10개만 표시(초기 진입), false면 현재 표시 개수 유지·폴링 동기화
   */
  function applyCacheToDom(resetToFirstPage) {
    const { list } = getEls();
    if (!list) return;

    if (!Array.isArray(allRows) || allRows.length === 0) {
      visibleCount = 0;
      showEmptyState(list);
      return;
    }

    if (resetToFirstPage) {
      visibleCount = Math.min(PAGE_SIZE, allRows.length);
    } else {
      const cap = allRows.length;
      if (visibleCount <= 0) {
        visibleCount = Math.min(PAGE_SIZE, cap);
      } else {
        visibleCount = Math.min(visibleCount, cap);
      }
    }

    redrawVisibleFromScratch(list);
  }

  /**
   * GET 목록 후 캐시 갱신
   * @param {boolean} resetToFirstPage
   */
  async function fetchAlertsFromServer(resetToFirstPage) {
    const { list } = getEls();
    if (!list) return;

    try {
      const res = await fetch(API, { credentials: 'same-origin' });
      if (res.status === 401) {
        allRows = [];
        showEmptyState(list);
        return;
      }
      if (!res.ok) return;
      const data = await parseJsonSafe(res);
      allRows = Array.isArray(data) ? data : [];
      applyCacheToDom(resetToFirstPage);
    } catch {
      /* 유지 */
    }
  }

  /**
   * 단일 삭제 — POST /api/alerts/{id}
   * @param {string} alertId
   */
  async function postDeleteAlert(alertId) {
    const res = await fetch(`${API}/${encodeURIComponent(alertId)}`, {
      method: 'POST',
      credentials: 'same-origin',
    });
    return res.ok || res.status === 204;
  }

  /**
   * @param {string} alertId
   */
  async function postMarkRead(alertId) {
    const res = await fetch(`${API}/${encodeURIComponent(alertId)}/read`, {
      method: 'POST',
      credentials: 'same-origin',
    });
    return res.ok || res.status === 204;
  }

  /**
   * 링크 클릭: 읽음 후 이동
   * @param {MouseEvent} e
   */
  async function onListClick(e) {
    if (e.target && /** @type {HTMLElement} */ (e.target).closest('[data-alert-delete]')) {
      return;
    }

    const a = e.target && /** @type {HTMLElement} */ (e.target).closest('a[data-alert-id]');
    if (!a || !a.dataset.alertId) return;

    e.preventDefault();

    const id = a.dataset.alertId;
    const href = a.getAttribute('href') || '#';

    try {
      await postMarkRead(id);
    } catch {
      /* */
    }

    a.dataset.unread = 'false';
    applyAlertRowAppearance(a, true);

    const row = allRows.find(
      (r) => String(r.alertId) === id
    );
    if (row) {
      row.read = true;
      row.isRead = true;
    }

    await refreshUnreadBadge();

    if (href && href !== '#') {
      window.location.assign(href);
    }
  }

  /**
   * 삭제 버튼 클릭
   * @param {MouseEvent} e
   */
  async function onListClickDelete(e) {
    const btn = e.target && /** @type {HTMLElement} */ (e.target).closest('[data-alert-delete]');
    if (!btn || !btn.dataset.alertDelete) return;

    e.preventDefault();
    e.stopPropagation();

    const id = btn.dataset.alertDelete;
    const ok = await postDeleteAlert(id);
    if (!ok) return;

    allRows = allRows.filter((r) => String(r.alertId) !== id);
    visibleCount = Math.min(visibleCount, allRows.length);

    const { list } = getEls();
    if (list) {
      if (allRows.length === 0) {
        showEmptyState(list);
      } else {
        redrawVisibleFromScratch(list);
      }
    }

    await refreshUnreadBadge();
  }

  function onLoadMoreClick() {
    const { list } = getEls();
    if (!list) return;
    if (visibleCount >= allRows.length) return;

    visibleCount = Math.min(visibleCount + PAGE_SIZE, allRows.length);
    appendNextSlice(list);
  }

  function onBellClick() {
    fetchAlertsFromServer(false);
    refreshUnreadBadge();
  }

  function init() {
    const { bell, list, loadMore } = getEls();
    if (!bell || !list) return;

    bell.addEventListener('click', onBellClick);
    list.addEventListener('click', onListClick);
    list.addEventListener('click', onListClickDelete);
    if (loadMore) {
      loadMore.addEventListener('click', onLoadMoreClick);
    }

    refreshUnreadBadge();
    fetchAlertsFromServer(true);

    if (pollTimer) clearInterval(pollTimer);
    pollTimer = setInterval(() => {
      refreshUnreadBadge();
      fetchAlertsFromServer(false);
    }, POLL_MS);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
