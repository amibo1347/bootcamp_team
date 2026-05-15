/**
 * [홈 대시보드] index.html 전용 스크립트
 * - 출퇴근·근무 진행 바, 오늘 일정(3건), 주간·공지, 플로팅 채팅(Mock·localStorage)
 * - 서버 연동 시: fetch는 GET/POST 규약에 맞춰 각 핸들러만 교체하면 됩니다.
 */

/** @type {string} 홈 플로팅 채팅 대화 목록(Mock) localStorage 키 — 기존 쪽지 키와 분리 */
const HOME_CHAT_STORAGE_KEY = 'homeChatConversationsV1';

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
 * 상대 시각 라벨(대화 목록 미리보기용)
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
 * - 버튼 노출: 출근 전에는 출근만, 출근 후에는 퇴근만(Thymeleaf SSR + 클라이언트에서 hidden 동기화)
 * @param {{
 *   checkinBtn: HTMLButtonElement;
 *   checkoutBtn: HTMLButtonElement;
 *   checkinDisplay: HTMLElement;
 *   checkoutDisplay: HTMLElement;
 *   fillEl: HTMLElement | null;
 *   trackEl: HTMLElement | null;
 *   labelEl: HTMLElement | null;
 * }} els
 * @param {{ serverHasCheckIn?: boolean }} [options] serverHasCheckIn: 조각 data-home-server-check-in 과 동일(이미 출근 처리된 SSR)
 */
function setupAttendanceCard(els, options = {}) {
  const { serverHasCheckIn = false } = options;
  let checkinAt = /** @type {Date | null} */ (null);
  let checkoutAt = /** @type {Date | null} */ (null);
  let progressTimer = /** @type {number | null} */ (null);

  const tickProgress = () => {
    updateWorkProgress(checkinAt, checkoutAt, els.fillEl, els.trackEl, els.labelEl);
  };

  /**
   * 출근/퇴근 버튼 표시·비활성 상태를 checkinAt/checkoutAt 에 맞춥니다.
   */
  const refreshButtons = () => {
    els.checkinBtn.hidden = checkinAt !== null;
    els.checkoutBtn.hidden = checkinAt === null || checkoutAt !== null;
    els.checkinBtn.disabled = checkinAt !== null;
    els.checkoutBtn.disabled = checkoutAt !== null || checkinAt === null;
  };

  // SSR 로 이미 출근한 경우: 목업 진행률용 시각(당일 09:00, 미래면 전일)으로만 채움 — 실제 연동 시 서버 시각으로 교체
  if (serverHasCheckIn) {
    const nine = new Date();
    nine.setHours(9, 0, 0, 0);
    if (nine.getTime() > Date.now()) {
      nine.setDate(nine.getDate() - 1);
    }
    checkinAt = nine;
    els.checkinDisplay.textContent = formatTime(checkinAt);
    progressTimer = window.setInterval(tickProgress, 1000);
  }

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
 * @typedef {'me' | 'them'} ChatRole
 */

/**
 * @typedef {{ role: ChatRole; text: string; at: string }} ChatMsg
 */

/**
 * @typedef {{
 *   id: string;
 *   peerId: string;
 *   peerName: string;
 *   peerDept?: string;
 *   messages: ChatMsg[];
 *   updatedAt: string;
 * }} Conversation
 */

/**
 * @typedef {{ id: string; name: string; dept: string }} MockPeer
 * 서버 연동 시: 직원 목록은 GET으로 조회한 결과로 MOCK_PEERS 대체
 */
const MOCK_PEERS = /** @type {MockPeer[]} */ ([
  { id: 'peer-001', name: '박과장', dept: '기획팀' },
  { id: 'peer-002', name: '김부장', dept: '경영지원팀' },
  { id: 'peer-003', name: '이대리', dept: '인사팀' },
  { id: 'peer-004', name: '최주임', dept: '개발팀' },
  { id: 'peer-005', name: '정대리', dept: '디자인팀' },
]);

/**
 * HTML 이스케이프(말풍선 본문 삽입 시)
 * @param {string} s
 * @returns {string}
 */
function escapeHtml(s) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * 새 대화 id 생성
 * @returns {string}
 */
function newConversationId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `conv-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

/**
 * 기본 시드 대화(구 쪽지 Mock 3건을 대화·메시지 형태로 변환)
 * @returns {Conversation[]}
 */
function defaultConversations() {
  const t1 = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const t1b = new Date(Date.now() - 8 * 60 * 1000).toISOString();
  const t2 = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  const t3 = new Date(Date.now() - 26 * 60 * 60 * 1000).toISOString();
  return [
    {
      id: 'conv-seed-1',
      peerId: 'peer-001',
      peerName: '박과장',
      peerDept: '기획팀',
      updatedAt: t1b,
      messages: [
        {
          role: 'them',
          text: '자료 검토 부탁드립니다. 첨부 링크 확인 후 회신 부탁드려요.',
          at: t1,
        },
        { role: 'me', text: '확인했습니다. 오늘 중으로 공유드릴게요.', at: t1b },
      ],
    },
    {
      id: 'conv-seed-2',
      peerId: 'peer-002',
      peerName: '김부장',
      peerDept: '경영지원팀',
      updatedAt: t2,
      messages: [
        {
          role: 'them',
          text: '내일 오전 잠깐 뵙고 싶습니다. 가능한 시간대 알려주세요.',
          at: t2,
        },
      ],
    },
    {
      id: 'conv-seed-3',
      peerId: 'peer-003',
      peerName: '이대리',
      peerDept: '인사팀',
      updatedAt: t3,
      messages: [
        { role: 'them', text: '회의실 예약 변경되었습니다. 공유 드립니다.', at: t3 },
        { role: 'me', text: '네, 반영하겠습니다.', at: new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString() },
      ],
    },
  ];
}

/**
 * localStorage에서 대화 목록 로드 (없거나 형식 오류 시 시드)
 * @returns {Conversation[]}
 */
function loadConversations() {
  const defaults = defaultConversations();
  try {
    const raw = localStorage.getItem(HOME_CHAT_STORAGE_KEY);
    if (!raw) return defaults;
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.length === 0) return defaults;
    const ok = parsed.every(
      (c) =>
        c &&
        typeof c.id === 'string' &&
        typeof c.peerId === 'string' &&
        typeof c.peerName === 'string' &&
        typeof c.updatedAt === 'string' &&
        Array.isArray(c.messages),
    );
    if (!ok) return defaults;
    return /** @type {Conversation[]} */ (parsed);
  } catch {
    return defaults;
  }
}

/**
 * 대화 목록을 localStorage에 저장
 * @param {Conversation[]} list
 */
function saveConversations(list) {
  try {
    localStorage.setItem(HOME_CHAT_STORAGE_KEY, JSON.stringify(list));
  } catch {
    /* ignore */
  }
}

/**
 * updatedAt 기준 내림차순 정렬된 복사본
 * @param {Conversation[]} list
 * @returns {Conversation[]}
 */
function sortConversations(list) {
  return [...list].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
}

/**
 * 마지막 메시지 미리보기 텍스트
 * @param {Conversation} c
 * @returns {string}
 */
function lastMessagePreview(c) {
  const last = c.messages[c.messages.length - 1];
  if (!last) return '';
  const prefix = last.role === 'me' ? '나: ' : '';
  return `${prefix}${last.text}`;
}

/**
 * 대화 목록 DOM 렌더
 * @param {HTMLElement} listEl
 * @param {Conversation[]} conversations
 */
function renderConversationList(listEl, conversations) {
  const sorted = sortConversations(conversations);
  const preview = (t) => (t.length > 80 ? `${t.slice(0, 80)}…` : t);

  listEl.innerHTML = sorted
    .map((c, i) => {
      const strong = i === 0;
      const border = strong ? 'border-l-4 border-brand-600 pl-3' : 'border-l-4 border-transparent pl-3';
      const nameCls = strong
        ? 'font-semibold text-gray-900 dark:text-white'
        : 'font-medium text-gray-700 dark:text-gray-200';
      const sub = c.peerDept ? ` · ${escapeHtml(c.peerDept)}` : '';
      const prev = preview(lastMessagePreview(c));
      const prevCls = strong ? 'text-gray-800 dark:text-gray-100' : 'text-gray-500 dark:text-gray-400';
      return `
        <button type="button" data-conversation-id="${escapeHtml(c.id)}" class="w-full border-0 bg-transparent px-4 py-3.5 text-left sm:px-5 ${border} hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-400 dark:hover:bg-gray-800/60">
          <div class="flex items-start justify-between gap-3">
            <span class="text-sm ${nameCls}">${escapeHtml(c.peerName)}${sub}</span>
            <span class="shrink-0 text-xs text-gray-400 dark:text-gray-500">${formatRelativeTime(c.updatedAt)}</span>
          </div>
          <p class="mt-1.5 line-clamp-2 text-sm leading-relaxed ${prevCls}">${escapeHtml(prev)}</p>
        </button>
      `;
    })
    .join('');
}

/**
 * 스레드 말풍선 렌더(시간 오름차순)
 * @param {HTMLElement} container
 * @param {Conversation} conv
 */
function renderMessages(container, conv) {
  const ordered = [...conv.messages].sort((a, b) => new Date(a.at).getTime() - new Date(b.at).getTime());
  if (ordered.length === 0) {
    container.innerHTML =
      '<p class="py-10 text-center text-sm text-gray-400 dark:text-gray-500">메시지를 입력해 보세요.</p>';
    return;
  }
  const timeShort = (iso) => {
    const d = new Date(iso);
    return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
  };
  container.innerHTML = ordered
    .map((m) => {
      if (m.role === 'them') {
        return `
          <div class="mb-3 flex justify-start">
            <div class="max-w-[min(100%,18rem)] rounded-2xl rounded-tl-md bg-gray-100 px-3 py-2 text-sm text-gray-900 shadow-sm dark:bg-gray-700 dark:text-gray-100 sm:max-w-[85%] sm:px-3.5 sm:py-2.5">
              <p class="whitespace-pre-wrap break-words">${escapeHtml(m.text)}</p>
              <p class="mt-1 text-[10px] text-gray-400 dark:text-gray-400">${timeShort(m.at)}</p>
            </div>
          </div>`;
      }
      return `
        <div class="mb-3 flex justify-end">
          <div class="max-w-[min(100%,18rem)] rounded-2xl rounded-tr-md bg-brand-600 px-3 py-2 text-sm text-white shadow-sm sm:max-w-[85%] sm:px-3.5 sm:py-2.5">
            <p class="whitespace-pre-wrap break-words">${escapeHtml(m.text)}</p>
            <p class="mt-1 text-[10px] text-brand-100">${timeShort(m.at)}</p>
          </div>
        </div>`;
    })
    .join('');
}

/**
 * 직원 선택 목록(Mock)
 * @param {HTMLElement} pickListEl
 */
function renderPickList(pickListEl) {
  pickListEl.innerHTML = MOCK_PEERS.map(
    (p) => `
    <button type="button" data-peer-id="${escapeHtml(p.id)}" class="flex w-full flex-col gap-0.5 border-0 bg-transparent px-4 py-3.5 text-left hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-brand-400 dark:hover:bg-gray-800/60 sm:px-5">
      <span class="text-sm font-medium text-gray-900 dark:text-white">${escapeHtml(p.name)}</span>
      <span class="text-xs text-gray-500 dark:text-gray-400">${escapeHtml(p.dept)}</span>
    </button>
  `,
  ).join('');
}

/**
 * 플로팅 채팅: 목록·스레드·직원 선택 3화면 + localStorage
 * 추후 서버 연동: GET으로 목록/스레드, POST로 전송·생성 엔드포인트로 치환 (프로젝트 GET/POST 규약)
 * @param {{
 *   fab: HTMLElement;
 *   backdrop: HTMLElement;
 *   panel: HTMLElement;
 *   closeBtn: HTMLButtonElement;
 *   backBtn: HTMLButtonElement;
 *   titleEl: HTMLElement;
 *   badgeEl: HTMLElement;
 *   screenList: HTMLElement;
 *   screenThread: HTMLElement;
 *   screenPick: HTMLElement;
 *   listEl: HTMLElement;
 *   messagesEl: HTMLElement;
 *   pickListEl: HTMLElement;
 *   footer: HTMLElement;
 *   input: HTMLInputElement;
 *   sendBtn: HTMLButtonElement;
 *   newBtn: HTMLButtonElement;
 * }} els
 */
function setupFloatingChat(els) {
  /** @type {'list' | 'thread' | 'pick'} */
  let view = 'list';
  /** @type {string | null} */
  let activeConversationId = null;
  /** @type {Conversation[]} */
  let conversations = loadConversations();

  const persist = () => saveConversations(conversations);

  const scrollMessagesToBottom = () => {
    els.messagesEl.scrollTop = els.messagesEl.scrollHeight;
  };

  /**
   * 목록 화면으로 전환(헤더·푸터·배지 동기화)
   */
  const showList = () => {
    view = 'list';
    activeConversationId = null;
    els.screenList.classList.remove('hidden');
    els.screenThread.classList.add('hidden');
    els.screenPick.classList.add('hidden');
    els.footer.classList.add('hidden');
    els.backBtn.classList.add('hidden');
    els.titleEl.textContent = '채팅';
    els.badgeEl.classList.remove('hidden');
    els.badgeEl.textContent = String(conversations.length);
    renderConversationList(els.listEl, conversations);
  };

  /**
   * 직원 선택 화면
   */
  const showPick = () => {
    view = 'pick';
    activeConversationId = null;
    els.screenList.classList.add('hidden');
    els.screenThread.classList.add('hidden');
    els.screenPick.classList.remove('hidden');
    els.footer.classList.add('hidden');
    els.backBtn.classList.remove('hidden');
    els.titleEl.textContent = '새 채팅';
    els.badgeEl.classList.add('hidden');
    renderPickList(els.pickListEl);
  };

  /**
   * 스레드 화면
   * @param {string} conversationId
   */
  const showThread = (conversationId) => {
    const conv = conversations.find((c) => c.id === conversationId);
    if (!conv) return;
    view = 'thread';
    activeConversationId = conversationId;
    els.screenList.classList.add('hidden');
    els.screenThread.classList.remove('hidden');
    els.screenPick.classList.add('hidden');
    els.footer.classList.remove('hidden');
    els.backBtn.classList.remove('hidden');
    els.titleEl.textContent = conv.peerName;
    els.badgeEl.classList.add('hidden');
    renderMessages(els.messagesEl, conv);
    window.requestAnimationFrame(() => scrollMessagesToBottom());
    els.input.focus();
  };

  const open = () => {
    els.backdrop.classList.remove('hidden');
    els.panel.classList.remove('hidden');
    els.fab.setAttribute('aria-expanded', 'true');
    conversations = loadConversations();
    showList();
  };

  const close = () => {
    els.backdrop.classList.add('hidden');
    els.panel.classList.add('hidden');
    els.fab.setAttribute('aria-expanded', 'false');
    els.input.value = '';
    conversations = loadConversations();
    showList();
  };

  const send = () => {
    if (view !== 'thread' || !activeConversationId) return;
    const text = els.input.value.trim();
    if (!text) return;
    const conv = conversations.find((c) => c.id === activeConversationId);
    if (!conv) return;
    const at = new Date().toISOString();
    conv.messages.push({ role: 'me', text, at });
    conv.updatedAt = at;
    persist();
    els.input.value = '';
    renderMessages(els.messagesEl, conv);
    scrollMessagesToBottom();
  };

  els.fab.addEventListener('click', (e) => {
    e.stopPropagation();
    if (els.panel.classList.contains('hidden')) open();
    else close();
  });

  els.closeBtn.addEventListener('click', close);
  els.backdrop.addEventListener('click', close);

  els.backBtn.addEventListener('click', () => {
    if (view === 'thread' || view === 'pick') showList();
  });

  els.newBtn.addEventListener('click', () => {
    if (!els.panel.classList.contains('hidden')) showPick();
  });

  els.listEl.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-conversation-id]');
    if (!(btn instanceof HTMLElement)) return;
    const id = btn.getAttribute('data-conversation-id');
    if (id) showThread(id);
  });

  els.pickListEl.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-peer-id]');
    if (!(btn instanceof HTMLElement)) return;
    const peerId = btn.getAttribute('data-peer-id');
    if (!peerId) return;
    const peer = MOCK_PEERS.find((p) => p.id === peerId);
    if (!peer) return;
    const existing = conversations.find((c) => c.peerId === peerId);
    if (existing) {
      showThread(existing.id);
      return;
    }
    const now = new Date().toISOString();
    const created = /** @type {Conversation} */ ({
      id: newConversationId(),
      peerId: peer.id,
      peerName: peer.name,
      peerDept: peer.dept,
      messages: [],
      updatedAt: now,
    });
    conversations.push(created);
    persist();
    showThread(created.id);
  });

  els.sendBtn.addEventListener('click', send);
  // Enter 전송: 한글 IME 조합 중(마지막 글자 확정 전)에는 무시 — 조합 종료 후 남은 '요' 등이 두 번째 메시지로 나가는 것을 방지
  els.input.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    if (e.isComposing || e.keyCode === 229) return;
    e.preventDefault();
    send();
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !els.panel.classList.contains('hidden')) close();
  });

  els.badgeEl.textContent = String(conversations.length);
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

  const attendanceCard = document.getElementById('home-attendance-card');
  const serverHasCheckIn = attendanceCard?.getAttribute('data-home-server-check-in') === 'true';

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
    setupAttendanceCard(
      {
        checkinBtn,
        checkoutBtn,
        checkinDisplay,
        checkoutDisplay,
        fillEl,
        trackEl,
        labelEl,
      },
      { serverHasCheckIn },
    );
  }

  const todayList = document.getElementById('home-today-list');
  if (todayList) renderTodaySchedule(todayList);

  const weekEl = document.getElementById('home-week-calendar');
  const weekLabel = document.getElementById('home-week-range-label');
  if (weekEl) renderWeekCalendar(weekEl, weekLabel);

  const noticeList = document.getElementById('home-notice-list');
  if (noticeList) renderNotices(noticeList);

  const fab = document.getElementById('home-chat-fab');
  const backdrop = document.getElementById('home-chat-backdrop');
  const panel = document.getElementById('home-chat-panel');
  const closeBtn = document.getElementById('home-chat-close');
  const backBtn = document.getElementById('home-chat-back');
  const titleEl = document.getElementById('home-chat-title');
  const badgeEl = document.getElementById('home-chat-badge');
  const screenList = document.getElementById('home-chat-screen-list');
  const screenThread = document.getElementById('home-chat-screen-thread');
  const screenPick = document.getElementById('home-chat-screen-pick');
  const listEl = document.getElementById('home-chat-list');
  const messagesEl = document.getElementById('home-chat-messages');
  const pickListEl = document.getElementById('home-chat-pick-list');
  const footer = document.getElementById('home-chat-footer');
  const input = document.getElementById('home-chat-input');
  const sendBtn = document.getElementById('home-chat-send');
  const newBtn = document.getElementById('home-chat-new');
  if (
    fab &&
    backdrop &&
    panel &&
    closeBtn instanceof HTMLButtonElement &&
    backBtn instanceof HTMLButtonElement &&
    titleEl &&
    badgeEl &&
    screenList &&
    screenThread &&
    screenPick &&
    listEl &&
    messagesEl &&
    pickListEl &&
    footer &&
    input instanceof HTMLInputElement &&
    sendBtn instanceof HTMLButtonElement &&
    newBtn instanceof HTMLButtonElement
  ) {
    setupFloatingChat({
      fab,
      backdrop,
      panel,
      closeBtn,
      backBtn,
      titleEl,
      badgeEl,
      screenList,
      screenThread,
      screenPick,
      listEl,
      messagesEl,
      pickListEl,
      footer,
      input,
      sendBtn,
      newBtn,
    });
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initHomeDashboard);
} else {
  initHomeDashboard();
}
