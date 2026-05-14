/**
 * [홈 대시보드] index.html 전용 스크립트
 * - 출퇴근·근무 진행 바, 오늘 일정(3건), 주간·공지, 플로팅 쪽지함
 * - 서버 연동 시: fetch는 GET/POST 규약에 맞춰 각 핸들러만 교체하면 됩니다.
 */

/** @type {string} 쪽지 목록 Mock 로컬 저장 키 */
const INBOX_MESSAGES_KEY = 'homeInboxMockMessages';

/** 목표 근무 시간(밀리초) — 출근 시점부터 이 시간이 채우면 100% */
const WORKDAY_MS = 8 * 60 * 60 * 1000;

/**
 * 두 자리 숫자로 포맷합니다.
 * @param {number} n
 * @returns {string}
 */
function pad2(n) {
  return String(n).padStart(2, '0');
}

/**
 * Date를 HH:mm:ss 문자열로 반환합니다.
 * @param {Date} d
 * @returns {string}
 */
function formatTime(d) {
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
}

/**
 * Date를 YYYY-MM-DD로 반환합니다.
 * @param {Date} d
 * @returns {string}
 */
function formatDateYmd(d) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/**
 * 상대 시각 라벨(쪽지함 목록용)
 * @param {string} iso
 * @returns {string}
 */
function formatRelativeTime(iso) {
  const d = new Date(iso);
  const diffMs = Date.now() - d.getTime();
  const sec = Math.floor(diffMs / 1000);
  if (sec < 60) return '방금';
  if (sec < 3600) return `${Math.floor(sec / 60)}분 전`;
  if (sec < 86400) return `${Math.floor(sec / 3600)}시간 전`;
  const dayStart = (/** @type {Date} */ x) => new Date(x.getFullYear(), x.getMonth(), x.getDate());
  const days = Math.round((dayStart(new Date()).getTime() - dayStart(d).getTime()) / 86400000);
  if (days === 1) return '어제';
  if (days > 1 && days < 7) return `${days}일 전`;
  return `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/**
 * 상단 시계 및 날짜 라벨을 갱신합니다.
 * @param {HTMLElement} clockEl
 * @param {HTMLElement | null} dateEl
 * @returns {number}
 */
function startClock(clockEl, dateEl) {
  const tick = () => {
    const now = new Date();
    clockEl.textContent = formatTime(now);
    if (dateEl) {
      dateEl.textContent = `${now.getFullYear()}.${pad2(now.getMonth() + 1)}.${pad2(now.getDate())}`;
    }
  };
  tick();
  return window.setInterval(tick, 1000);
}

/**
 * 근무 진행률 UI 갱신
 * @param {Date | null} checkinAt
 * @param {Date | null} checkoutAt
 * @param {HTMLElement | null} fillEl
 * @param {HTMLElement | null} trackEl
 * @param {HTMLElement | null} labelEl
 */
function updateWorkProgress(checkinAt, checkoutAt, fillEl, trackEl, labelEl) {
  if (!fillEl || !labelEl || !trackEl) return;
  const endMs = checkoutAt ? checkoutAt.getTime() : Date.now();
  if (!checkinAt) {
    fillEl.style.width = '0%';
    labelEl.textContent = '0%';
    trackEl.setAttribute('aria-valuenow', '0');
    return;
  }
  const elapsed = endMs - checkinAt.getTime();
  const pct = Math.min(100, Math.max(0, (elapsed / WORKDAY_MS) * 100));
  fillEl.style.width = `${pct}%`;
  labelEl.textContent = `${Math.round(pct)}%`;
  trackEl.setAttribute('aria-valuenow', String(Math.round(pct)));
}

/**
 * 출퇴근 Mock + 근무 시간 프로그레스 바
 * @param {{
 *   checkinBtn: HTMLButtonElement;
 *   checkoutBtn: HTMLButtonElement;
 *   checkinDisplay: HTMLElement;
 *   checkoutDisplay: HTMLElement;
 *   fillEl: HTMLElement | null;
 *   trackEl: HTMLElement | null;
 *   labelEl: HTMLElement | null;
 * }} els
 */
function setupAttendanceCard(els) {
  let checkinAt = /** @type {Date | null} */ (null);
  let checkoutAt = /** @type {Date | null} */ (null);
  let progressTimer = /** @type {number | null} */ (null);

  const tickProgress = () => {
    updateWorkProgress(checkinAt, checkoutAt, els.fillEl, els.trackEl, els.labelEl);
  };

  const refreshButtons = () => {
    els.checkinBtn.disabled = checkinAt !== null;
    els.checkoutBtn.disabled = checkoutAt !== null || checkinAt === null;
  };

  els.checkinBtn.addEventListener('click', () => {
    if (checkinAt) return;
    checkinAt = new Date();
    els.checkinDisplay.textContent = formatTime(checkinAt);
    if (progressTimer) window.clearInterval(progressTimer);
    progressTimer = window.setInterval(tickProgress, 1000);
    tickProgress();
    refreshButtons();
  });

  els.checkoutBtn.addEventListener('click', () => {
    if (!checkinAt || checkoutAt) return;
    checkoutAt = new Date();
    els.checkoutDisplay.textContent = formatTime(checkoutAt);
    if (progressTimer) {
      window.clearInterval(progressTimer);
      progressTimer = null;
    }
    tickProgress();
    refreshButtons();
  });

  refreshButtons();
  tickProgress();
}

/**
 * 오늘 일정: 가까운 시간순 최대 3건만 표시 (점선 이하 제거)
 * @param {HTMLElement} container
 */
function renderTodaySchedule(container) {
  const ymd = formatDateYmd(new Date());

  /** @type {{ title: string; time: string; room?: string }[]} */
  const raw = [
    { title: '팀 스탠드업', time: `${ymd}T09:30:00`, room: '3층 A' },
    { title: '인트라넷 UI 리뷰', time: `${ymd}T11:00:00`, room: '화상' },
    { title: '점심 약속', time: `${ymd}T12:30:00` },
    { title: '교육: 보안 인식', time: `${ymd}T15:00:00`, room: '교육실' },
    { title: '주간 정리', time: `${ymd}T17:30:00` },
  ];

  raw.sort((a, b) => new Date(a.time) - new Date(b.time));
  const top = raw.slice(0, 3);

  const rowHtml = (item) => {
    const t = new Date(item.time);
    const timeLabel = `${pad2(t.getHours())}:${pad2(t.getMinutes())}`;
    const sub = item.room ? ` · ${item.room}` : '';
    return `
      <article class="rounded-xl border border-gray-100 bg-gray-50/90 px-3 py-2.5 dark:border-gray-600 dark:bg-gray-900/40">
        <p class="text-xs font-medium text-brand-600 dark:text-brand-400">${timeLabel}</p>
        <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-white">${item.title}</p>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">${sub || '\u00A0'}</p>
      </article>
    `;
  };

  container.innerHTML = `<div class="space-y-2">${top.map(rowHtml).join('')}</div>`;
}

/**
 * 이번 주 월~금
 * @param {Date} base
 * @returns {Date[]}
 */
function getMonToFri(base) {
  const d = new Date(base);
  const day = d.getDay();
  const diffToMon = day === 0 ? -6 : 1 - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + diffToMon);
  monday.setHours(0, 0, 0, 0);
  return Array.from({ length: 5 }, (_, i) => {
    const x = new Date(monday);
    x.setDate(monday.getDate() + i);
    return x;
  });
}

/**
 * 주간 캘린더 Mock
 * @param {HTMLElement} container
 * @param {HTMLElement | null} rangeLabel
 */
function renderWeekCalendar(container, rangeLabel) {
  const days = getMonToFri(new Date());
  const dayNames = ['월', '화', '수', '목', '금'];

  /** @type {Record<string, string[]>} */
  const eventsByYmd = {
    [formatDateYmd(days[0])]: ['주간 계획', '배포 점검'],
    [formatDateYmd(days[1])]: ['1:1 미팅'],
    [formatDateYmd(days[2])]: ['디자인 핸드오프', 'DB 백업'],
    [formatDateYmd(days[3])]: ['All-hands'],
    [formatDateYmd(days[4])]: ['주간 마무리'],
  };

  if (rangeLabel) {
    const a = days[0];
    const b = days[4];
    rangeLabel.textContent = `${a.getMonth() + 1}/${a.getDate()} – ${b.getMonth() + 1}/${b.getDate()}`;
  }

  container.innerHTML = days
    .map((date, idx) => {
      const ymd = formatDateYmd(date);
      const list = eventsByYmd[ymd] || [];
      const isToday = formatDateYmd(new Date()) === ymd;
      const ring = isToday ? 'ring-2 ring-brand-400 dark:ring-brand-500' : '';
      const shown = list.slice(0, 2);
      const more = list.length > 2 ? `<li class="truncate text-[10px] text-gray-400">+${list.length - 2}</li>` : '';
      const items = shown
        .map(
          (t) =>
            `<li class="truncate rounded-lg bg-white/90 px-1.5 py-1 text-[11px] font-medium text-gray-800 shadow-sm dark:bg-gray-900 dark:text-gray-100">${t}</li>`,
        )
        .join('');
      return `
        <div role="gridcell" class="flex min-h-0 flex-col rounded-xl border border-gray-100 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-900/30 sm:p-2.5 ${ring}">
          <div class="mb-1.5 shrink-0 text-center">
            <div class="text-[10px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">${dayNames[idx]}</div>
            <div class="text-sm font-bold text-gray-900 dark:text-white">${date.getDate()}</div>
          </div>
          <ul class="min-h-0 flex-1 space-y-1 overflow-y-auto">${items || '<li class="text-[10px] text-gray-400">일정 없음</li>'}${more}</ul>
        </div>
      `;
    })
    .join('');
}

/**
 * 공지 3건
 * @param {HTMLElement} listEl
 */
function renderNotices(listEl) {
  const items = [
    { title: '5월 정기 보안 점검 안내', date: '05-12' },
    { title: '사내 헬스케어 신청 마감', date: '05-10' },
    { title: '주차장 리모델링 구간 통제', date: '05-08' },
  ];
  listEl.innerHTML = items
    .map(
      (n) => `
      <li>
        <a href="#" class="block rounded-xl border border-transparent px-2 py-2.5 hover:border-gray-200 hover:bg-gray-50 dark:hover:border-gray-600 dark:hover:bg-gray-900/50">
          <span class="line-clamp-2 font-medium text-gray-900 dark:text-white">${n.title}</span>
          <span class="mt-1 block text-xs text-gray-500 dark:text-gray-400">${n.date}</span>
        </a>
      </li>
    `,
    )
    .join('');
}

/**
 * @typedef {{ from: string; text: string; at: string }} InboxMessage
 */

/**
 * 로컬에서 쪽지 목록 로드 (없으면 기본 Mock)
 * @returns {InboxMessage[]}
 */
function loadInboxMessages() {
  const defaults = /** @type {InboxMessage[]} */ ([
    {
      from: '박과장',
      text: '자료 검토 부탁드립니다. 첨부 링크 확인 후 회신 부탁드려요.',
      at: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
    },
    {
      from: '김부장',
      text: '내일 오전 잠깐 뵙고 싶습니다. 가능한 시간대 알려주세요.',
      at: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    },
    {
      from: '이대리',
      text: '회의실 예약 변경되었습니다. 공유 드립니다.',
      at: new Date(Date.now() - 26 * 60 * 60 * 1000).toISOString(),
    },
  ]);
  try {
    const raw = localStorage.getItem(INBOX_MESSAGES_KEY);
    if (!raw) return defaults;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.length === 0) return defaults;
    return /** @type {InboxMessage[]} */ (parsed);
  } catch {
    return defaults;
  }
}

/**
 * @param {InboxMessage[]} list
 */
function saveInboxMessages(list) {
  try {
    localStorage.setItem(INBOX_MESSAGES_KEY, JSON.stringify(list));
  } catch {
    /* ignore */
  }
}

/**
 * 쪽지 목록 DOM 반영 (첫 줄 강조)
 * @param {HTMLElement} listEl
 * @param {InboxMessage[]} messages
 * @param {HTMLElement | null} badgeEl
 */
function renderInboxList(listEl, messages, badgeEl) {
  if (badgeEl) badgeEl.textContent = String(messages.length);

  const preview = (t) => (t.length > 96 ? `${t.slice(0, 96)}…` : t);

  listEl.innerHTML = messages
    .map((m, i) => {
      const strong = i === 0;
      const border = strong ? 'border-l-4 border-brand-600 pl-3' : 'border-l-4 border-transparent pl-3';
      const titleCls = strong
        ? 'font-semibold text-gray-900 dark:text-white'
        : 'font-medium text-gray-700 dark:text-gray-200';
      return `
        <div class="px-4 py-3.5 sm:px-5 ${border}">
          <div class="flex items-start justify-between gap-3">
            <span class="text-sm ${titleCls}">${m.from}</span>
            <span class="shrink-0 text-xs text-gray-400 dark:text-gray-500">${formatRelativeTime(m.at)}</span>
          </div>
          <p class="mt-1.5 line-clamp-3 text-sm leading-relaxed ${strong ? 'text-gray-800 dark:text-gray-100' : 'text-gray-500 dark:text-gray-400'}">${preview(m.text)}</p>
        </div>
      `;
    })
    .join('');
}

/**
 * 플로팅 쪽지함: FAB 위 패널 + 백드롭
 * @param {{
 *   fab: HTMLElement;
 *   backdrop: HTMLElement;
 *   panel: HTMLElement;
 *   listEl: HTMLElement;
 *   input: HTMLInputElement;
 *   sendBtn: HTMLButtonElement;
 *   closeBtn: HTMLButtonElement;
 *   badgeEl: HTMLElement | null;
 * }} els
 */
function setupNoteInbox(els) {
  /** @type {InboxMessage[]} */
  let messages = loadInboxMessages();

  const open = () => {
    els.backdrop.classList.remove('hidden');
    els.panel.classList.remove('hidden');
    els.fab.setAttribute('aria-expanded', 'true');
    renderInboxList(els.listEl, messages, els.badgeEl);
    els.input.focus();
  };

  const close = () => {
    els.backdrop.classList.add('hidden');
    els.panel.classList.add('hidden');
    els.fab.setAttribute('aria-expanded', 'false');
  };

  const send = () => {
    const text = els.input.value.trim();
    if (!text) return;
    messages = [{ from: '나', text, at: new Date().toISOString() }, ...messages];
    saveInboxMessages(messages);
    els.input.value = '';
    renderInboxList(els.listEl, messages, els.badgeEl);
  };

  els.fab.addEventListener('click', (e) => {
    e.stopPropagation();
    if (els.panel.classList.contains('hidden')) open();
    else close();
  });

  els.closeBtn.addEventListener('click', close);
  els.backdrop.addEventListener('click', close);
  els.sendBtn.addEventListener('click', send);
  els.input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      send();
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !els.panel.classList.contains('hidden')) close();
  });

  if (els.badgeEl) els.badgeEl.textContent = String(messages.length);
}

/**
 * DOM 준비 후 대시보드 위젯 초기화
 */
function initHomeDashboard() {
  const root = document.getElementById('home-dashboard-root');
  if (!root) return;

  const clockEl = document.getElementById('home-dashboard-time');
  const dateEl = document.getElementById('home-dashboard-date');
  if (clockEl) startClock(clockEl, dateEl);

  const checkinBtn = document.getElementById('home-btn-checkin');
  const checkoutBtn = document.getElementById('home-btn-checkout');
  const checkinDisplay = document.getElementById('home-checkin-display');
  const checkoutDisplay = document.getElementById('home-checkout-display');
  const fillEl = document.getElementById('home-work-progress-fill');
  const trackEl = document.getElementById('home-work-progress-track');
  const labelEl = document.getElementById('home-work-progress-label');
  if (
    checkinBtn instanceof HTMLButtonElement &&
    checkoutBtn instanceof HTMLButtonElement &&
    checkinDisplay &&
    checkoutDisplay
  ) {
    setupAttendanceCard({
      checkinBtn,
      checkoutBtn,
      checkinDisplay,
      checkoutDisplay,
      fillEl,
      trackEl,
      labelEl,
    });
  }

  const todayList = document.getElementById('home-today-list');
  if (todayList) renderTodaySchedule(todayList);

  const weekEl = document.getElementById('home-week-calendar');
  const weekLabel = document.getElementById('home-week-range-label');
  if (weekEl) renderWeekCalendar(weekEl, weekLabel);

  const noticeList = document.getElementById('home-notice-list');
  if (noticeList) renderNotices(noticeList);

  const fab = document.getElementById('home-note-fab');
  const backdrop = document.getElementById('home-note-backdrop');
  const panel = document.getElementById('home-note-panel');
  const listEl = document.getElementById('home-note-list');
  const input = document.getElementById('home-note-input');
  const sendBtn = document.getElementById('home-note-send');
  const closeBtn = document.getElementById('home-note-close');
  const badgeEl = document.getElementById('home-note-badge');
  if (
    fab &&
    backdrop &&
    panel &&
    listEl &&
    input instanceof HTMLInputElement &&
    sendBtn instanceof HTMLButtonElement &&
    closeBtn instanceof HTMLButtonElement
  ) {
    setupNoteInbox({ fab, backdrop, panel, listEl, input, sendBtn, closeBtn, badgeEl });
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initHomeDashboard);
} else {
  initHomeDashboard();
}
