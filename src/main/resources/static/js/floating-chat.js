/**
 * [전역 플로팅 채팅] partials/floating-chat 마크업(#home-chat-*) 전용
 * - localStorage Mock, 서버 연동 시 GET/POST 엔드포인트로 교체 (프로젝트 HTTP 규약)
 */

/** @type {string} 대화 목록 Mock localStorage 키 */
const HOME_CHAT_STORAGE_KEY = 'homeChatConversationsV1';

/**
 * 두 자리 숫자(말풍선 시각·상대시각 라벨용)
 * @param {number} n
 * @returns {string}
 */
function pad2(n) {
  return String(n).padStart(2, '0');
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
 * 기본 시드 대화(Mock)
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
  // Enter 전송: 한글 IME 조합 중에는 무시
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
 * DOM에서 #home-chat-* 를 찾아 플로팅 채팅 바인딩
 */
function initFloatingChat() {
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
  document.addEventListener('DOMContentLoaded', initFloatingChat);
} else {
  initFloatingChat();
}
