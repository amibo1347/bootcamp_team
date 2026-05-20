/*
 * 공통 채팅 패널 클라이언트.
 *  - DOM id 는 partials/chat.html 의 #chat-* 와 1:1.
 *  - 백드롭 없음 → 외부 클릭으로 닫히지 않음. X 버튼만으로 닫기. 페이지 인터랙션은 보존.
 *  - 화면: 목록 / 스레드 / 회원선택
 *  - API: /api/chat/*  (서버 검증은 ChatService 에서)
 */
(function () {
  // 한 페이지에 두 번 로드돼도 init 한 번만.
  if (window.__chatPanelInit) return;
  window.__chatPanelInit = true;

  document.addEventListener('DOMContentLoaded', () => {
    const fab = document.getElementById('chat-fab');
    const panel = document.getElementById('chat-panel');
    if (!fab || !panel) return;  // 채팅 partial 이 include 안 된 페이지 — 그냥 종료

    /** FAB·패널이 본문(캘린더 등) 스크롤 레이어 위에 오도록 고정 z-index */
    const CHAT_Z_INDEX = '10080';

    // #chat-stack 을 body 직속으로 — flex/overflow 래퍼 안에 있으면 캘린더 페이지 등에서 겹침·클릭 가로채기 발생
    const chatStack = document.getElementById('chat-stack');
    if (chatStack && chatStack.parentElement !== document.body) {
      document.body.appendChild(chatStack);
      chatStack.style.zIndex = CHAT_Z_INDEX;
    }

    const closeBtn = document.getElementById('chat-close');
    const backBtn = document.getElementById('chat-back');
    const titleEl = document.getElementById('chat-title');
    const badgeEl = document.getElementById('chat-badge');
    const headerTabs = document.getElementById('chat-header-tabs');
    const tabChatBtn = document.getElementById('chat-tab-employee');
    const tabAiBtn = document.getElementById('chat-tab-ai');

    const screenList = document.getElementById('chat-screen-list');
    const screenAi = document.getElementById('chat-screen-ai');
    const screenThread = document.getElementById('chat-screen-thread');
    const screenPick = document.getElementById('chat-screen-pick');

    const listEl = document.getElementById('chat-list');
    const aiBodyEl = document.getElementById('chat-ai-body');
    const newBtn = document.getElementById('chat-new');
    const aiNewBtn = document.getElementById('chat-ai-new');

    const pickListEl = document.getElementById('chat-pick-list');
    const pickName = document.getElementById('chat-pick-name');
    const pickDept = document.getElementById('chat-pick-dept');
    const pickPos = document.getElementById('chat-pick-position');

    const messagesEl = document.getElementById('chat-messages');
    const inputEl = document.getElementById('chat-input');
    const sendBtn = document.getElementById('chat-send');
    const fileInput = document.getElementById('chat-file-input');
    const filePreview = document.getElementById('chat-file-preview');

    let view = 'list';                // 'list' | 'thread' | 'pick'
    let panelTab = 'chat';            // 'chat' | 'ai' — 헤더 탭 (목록 화면)
    let activeMode = 'chat';          // 'chat' | 'ai' — 현재 thread 가 사람 vs AI
    let activeConvId = null;
    let activePeerName = '';
    let peersCache = [];              // 회원 목록 캐시
    let convsCache = [];              // 대화방 목록 캐시
    let pendingFiles = [];            // 전송 대기 파일
    let messagesLoadGen = 0;          // loadMessages 경쟁 방지 (늦게 도착한 응답이 SSE 말풍선을 덮어쓰지 않게)

    // ─── CSRF ─────────────────────────────────────────────────────
    const csrfHeader = () => {
      const t = document.querySelector('meta[name="_csrf"]')?.content;
      const h = document.querySelector('meta[name="_csrf_header"]')?.content;
      const out = {};
      if (t && h) out[h] = t;
      return out;
    };

    // ─── 공통 유틸 ─────────────────────────────────────────────────
    const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    /**
     * 회원 아바타 HTML.
     *  - profileImageUrl 이 있으면 img 시도 → 로드 실패(404 등) 시 글로벌 폴백 함수가 이니셜 div 로 교체.
     *  - 없거나 이름이 비면 바로 이니셜 div.
     */
    window.__chatAvatarErr = window.__chatAvatarErr || function (img) {
      const initial = (img.dataset.initial || '?');
      const px = img.style.width || '36px';
      const fontSize = Math.max(11, Math.floor(parseInt(px, 10) * 0.4)) + 'px';
      const div = document.createElement('div');
      div.className = 'rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center font-bold';
      div.style.width = px;
      div.style.height = px;
      div.style.fontSize = fontSize;
      div.textContent = initial;
      img.replaceWith(div);
    };

    function avatarHtml(peer, size) {
      const px = size || 36;
      const initial = (peer.name || '?').charAt(0);
      const fontSize = Math.max(11, Math.floor(px * 0.4));
      if (!peer.profileImageUrl) {
        return '<div class="rounded-full bg-indigo-100 text-indigo-600 flex items-center justify-center font-bold" '
          + 'style="width:' + px + 'px;height:' + px + 'px;font-size:' + fontSize + 'px;">'
          + esc(initial) + '</div>';
      }
      return '<img src="' + esc(peer.profileImageUrl) + '" alt="" '
        + 'class="rounded-full object-cover" '
        + 'style="width:' + px + 'px;height:' + px + 'px;" '
        + 'data-initial="' + esc(initial) + '" '
        + 'onerror="__chatAvatarErr(this)">';
    }

    const pad2 = (n) => String(n).padStart(2, '0');
    const fmtTime = (iso) => {
      if (!iso) return '';
      const d = new Date(iso.replace(' ', 'T'));
      if (Number.isNaN(d.getTime())) return '';
      return pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    };
    const fmtRelative = (iso) => {
      if (!iso) return '';
      const d = new Date(iso.replace(' ', 'T'));
      if (Number.isNaN(d.getTime())) return '';
      const now = new Date();
      const diffMin = Math.round((now - d) / 60000);
      if (diffMin < 1) return '방금';
      if (diffMin < 60) return diffMin + '분 전';
      if (diffMin < 60 * 24) return Math.floor(diffMin / 60) + '시간 전';
      if (diffMin < 60 * 24 * 2) return '어제';
      return d.getMonth() + 1 + '/' + d.getDate();
    };

    // ─── 헤더 탭 UI (채팅 | AI 비서) ───────────────────────────────
    // 활성 탭: 굵은 흰색 / 비활성: 은은한 white/55
    const TAB_BTN_CHAT_ACTIVE =
      'inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 text-sm font-bold text-white transition sm:text-base';
    const TAB_BTN_CHAT_INACTIVE =
      'inline-flex items-center gap-1.5 rounded-md px-1 py-0.5 text-sm font-medium text-white/55 transition hover:text-white/80 sm:text-base';
    const TAB_BTN_AI_ACTIVE =
      'rounded-md px-1 py-0.5 text-sm font-bold text-white transition sm:text-base';
    const TAB_BTN_AI_INACTIVE =
      'rounded-md px-1 py-0.5 text-sm font-medium text-white/55 transition hover:text-white/80 sm:text-base';

    /**
     * 헤더 탭 버튼 활성/비활성 스타일·aria-selected 동기화.
     */
    function updateTabUi() {
      if (!tabChatBtn || !tabAiBtn) return;
      const isChat = panelTab === 'chat';
      tabChatBtn.className = isChat ? TAB_BTN_CHAT_ACTIVE : TAB_BTN_CHAT_INACTIVE;
      tabAiBtn.className = isChat ? TAB_BTN_AI_INACTIVE : TAB_BTN_AI_ACTIVE;
      tabChatBtn.setAttribute('aria-selected', isChat ? 'true' : 'false');
      tabAiBtn.setAttribute('aria-selected', isChat ? 'false' : 'true');
    }

    /**
     * 목록 탭 vs 스레드/회원선택: 헤더에 탭 nav 또는 제목(h2) 표시 전환.
     */
    function syncHeader() {
      const inSubView = view === 'thread' || view === 'pick';
      if (headerTabs) headerTabs.classList.toggle('hidden', inSubView);
      if (titleEl) titleEl.classList.toggle('hidden', !inSubView);
    }

    /** GET /api/ai/conversations — AI 대화 목록 */
    const AI_LIST_ENDPOINT = '/api/ai/conversations';

    /** AI 비서 목록 행 왼쪽 아이콘 (원형 이니셜) */
    function aiAvatarHtml() {
      return '<div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-violet-100 text-xs font-bold text-violet-700 dark:bg-violet-900/40 dark:text-violet-200">AI</div>';
    }

    /**
     * AI 비서 대화 목록 AJAX 로드 (GET /api/ai/conversations).
     */
    async function loadAiList() {
      if (!aiBodyEl) return;
      aiBodyEl.innerHTML = '<div class="px-4 py-6 text-center text-xs text-gray-400">불러오는 중...</div>';
      try {
        const res = await fetch(AI_LIST_ENDPOINT);
        if (!res.ok) throw new Error('ai list failed');
        const items = await res.json();
        renderAiList(Array.isArray(items) ? items : []);
      } catch (e) {
        aiBodyEl.innerHTML =
          '<div class="px-4 py-6 text-center text-xs text-gray-400">AI 대화 목록을 불러올 수 없습니다.</div>';
      }
    }

    /**
     * 새 AI 대화 시작 (POST /api/ai/conversations) → 곧바로 thread 진입.
     */
    async function createAiConversation() {
      if (aiNewBtn) aiNewBtn.disabled = true;
      try {
        const res = await fetch(AI_LIST_ENDPOINT, {
          method: 'POST',
          headers: Object.assign({ 'Content-Type': 'application/json' }, csrfHeader()),
          body: JSON.stringify({}),
        });
        if (!res.ok) throw new Error('create ai failed');
        const created = await res.json();
        if (created && created.sessionId) {
          showThread(created.sessionId, created.title || 'AI 비서', 'ai');
        } else {
          loadAiList();
        }
      } catch (e) {
        alert('새 AI 대화를 시작할 수 없습니다.');
      } finally {
        if (aiNewBtn) aiNewBtn.disabled = false;
      }
    }

    /**
     * AI 비서 목록 HTML 렌더 (#chat-ai-body).
     * @param {Array<{sessionId:string,title:string,lastMessagePreview?:string,updatedAt?:string}>} items
     */
    function renderAiList(items) {
      if (!aiBodyEl) return;
      if (!items.length) {
        aiBodyEl.innerHTML =
          '<div class="px-4 py-10 text-center text-xs text-gray-400">AI 대화가 없습니다. + 버튼으로 새 대화를 시작하세요.</div>';
        return;
      }
      aiBodyEl.innerHTML = items.map((item) => {
        const preview = item.lastMessagePreview
          ? esc(item.lastMessagePreview)
          : '<span class="text-gray-300">대화를 시작해 보세요</span>';
        const time = fmtRelative(item.updatedAt);
        return ''
          + '<button type="button" data-ai-session-id="' + esc(item.sessionId || '') + '" '
          + '  data-ai-title="' + esc(item.title || 'AI 비서') + '" '
          + '  class="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-gray-50 dark:hover:bg-white/5">'
          +   aiAvatarHtml()
          +   '<div class="min-w-0 flex-1">'
          +     '<div class="flex items-center justify-between gap-2">'
          +       '<span class="truncate text-sm font-semibold text-gray-900 dark:text-white">' + esc(item.title || 'AI 비서') + '</span>'
          +       '<span class="shrink-0 text-[11px] text-gray-400">' + time + '</span>'
          +     '</div>'
          +     '<div class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">' + preview + '</div>'
          +   '</div>'
          + '</button>';
      }).join('');
    }

    /**
     * 헤더 탭 전환: 'chat' 직원 목록 / 'ai' AI 목록(AJAX).
     * @param {'chat'|'ai'} tab
     */
    function switchPanelTab(tab) {
      if (panelTab === tab && view === 'list') {
        if (tab === 'chat') loadConversations();
        else loadAiList();
        return;
      }
      panelTab = tab;
      updateTabUi();
      if (tab === 'ai') {
        view = 'list';
        activeConvId = null;
        screenList.classList.add('hidden');
        screenThread.classList.add('hidden');
        screenPick.classList.add('hidden');
        if (screenAi) screenAi.classList.remove('hidden');
        backBtn.classList.add('hidden');
        syncHeader();
        loadAiList();
        return;
      }
      showList();
    }

    // ─── 화면 전환 ─────────────────────────────────────────────────
    const showList = () => {
      view = 'list';
      panelTab = 'chat';
      activeConvId = null;
      screenList.classList.remove('hidden');
      if (screenAi) screenAi.classList.add('hidden');
      screenThread.classList.add('hidden');
      screenPick.classList.add('hidden');
      backBtn.classList.add('hidden');
      syncHeader();
      updateTabUi();
      loadConversations();
    };
    /**
     * 스레드 화면 진입. mode 로 사람 채팅 vs AI 비서 분기.
     *  - 'chat' : id = conversationId, 파일 첨부 가능, markRead 호출
     *  - 'ai'   : id = aiSessionId,   파일 첨부 숨김,  markRead 호출 X
     */
    const showThread = (id, peerName, mode) => {
      activeMode = (mode === 'ai') ? 'ai' : 'chat';
      view = 'thread';
      activeConvId = id;
      activePeerName = peerName || '';
      screenList.classList.add('hidden');
      if (screenAi) screenAi.classList.add('hidden');
      screenThread.classList.remove('hidden');
      screenPick.classList.add('hidden');
      backBtn.classList.remove('hidden');
      titleEl.textContent = activePeerName || (activeMode === 'ai' ? 'AI 비서' : '대화');
      syncHeader();
      pendingFiles = [];
      renderFilePreview();
      inputEl.value = '';
      setComposerForMode(activeMode);
      loadMessages(id);
      if (activeMode === 'chat') markConversationRead(id);
    };

    /** AI 모드에선 파일 첨부 label 숨김 (텍스트 전용). 사람 채팅은 다시 표시. */
    function setComposerForMode(mode) {
      const fileLabel = document.querySelector('label[for="chat-file-input"]');
      const isAi = mode === 'ai';
      if (fileLabel) fileLabel.style.display = isAi ? 'none' : '';
      if (filePreview) filePreview.style.display = isAi ? 'none' : '';
      if (inputEl) inputEl.placeholder = isAi ? 'AI 비서에게 질문하기...' : '메시지 입력...';
    }
    const showPick = () => {
      view = 'pick';
      activeConvId = null;
      screenList.classList.add('hidden');
      if (screenAi) screenAi.classList.add('hidden');
      screenThread.classList.add('hidden');
      screenPick.classList.remove('hidden');
      backBtn.classList.remove('hidden');
      titleEl.textContent = '새 채팅';
      syncHeader();
      loadPeers();
    };

    // ─── 패널 드래그 + 8방향 리사이즈 ──────────────────────────────
    // 첫 오픈 시 한 번만 실행 — panel 을 body 로 떼고 fixed + 디폴트 크기/위치 박음.
    // 헤더 드래그 → 이동, 8개 핸들(n/s/e/w/ne/nw/se/sw) → 리사이즈. min 크기는 디폴트로 고정.
    const DEFAULT_W = 360;
    const DEFAULT_H = 480;

    function setupDraggableResizablePanel() {
      if (panel.dataset.dragResizeInit === '1') return;
      panel.dataset.dragResizeInit = '1';

      // panel 을 body 직속으로 이동 — 부모 stack 의 fixed/items-end 영향 제거.
      if (panel.parentElement !== document.body) document.body.appendChild(panel);

      // 핸들/커서용 스타일 한 번만 주입.
      injectChatStyle();

      const w = Math.min(DEFAULT_W, window.innerWidth - 64);
      const h = Math.min(DEFAULT_H, window.innerHeight - 64);
      const left = Math.max(16, window.innerWidth - w - 32);
      const top = Math.max(16, window.innerHeight - h - 32);

      panel.style.position = 'fixed';
      panel.style.zIndex = CHAT_Z_INDEX;
      panel.style.left = left + 'px';
      panel.style.top = top + 'px';
      panel.style.right = 'auto';
      panel.style.bottom = 'auto';
      panel.style.width = w + 'px';
      panel.style.height = h + 'px';
      panel.style.minWidth = w + 'px';   // 디폴트가 최소 — 더 작아질 수 없음
      panel.style.minHeight = h + 'px';
      panel.style.maxWidth = 'none';
      panel.style.maxHeight = 'none';
      panel.style.resize = 'none';        // CSS resize 끄고 JS 핸들로 대체

      // 미디어 절대 크기: 디폴트 너비 × 0.6. 패널 커져도 미디어는 그대로.
      panel.style.setProperty('--chat-media-max', Math.round(w * 0.6) + 'px');

      addResizeHandles(panel);
      enableHeaderDrag(panel);
    }

    function injectChatStyle() {
      if (document.getElementById('__chat-style')) return;
      // ※ panel 에 overflow-hidden 클래스가 있어서 핸들이 panel 바깥(음수 offset)에 있으면 잘림.
      //   → 핸들을 panel 안쪽으로 (offset 0~) 배치. 두께 8px 의 띠/14×14 코너로 잡힘.
      const css = `
        #chat-panel header { cursor: move; user-select: none; }
        #chat-panel header button { cursor: pointer; }
        .__cr { position: absolute; z-index: 9999; background: transparent; }
        .__cr-n  { top: 0; left: 14px; right: 14px; height: 6px; cursor: n-resize; }
        .__cr-s  { bottom: 0; left: 14px; right: 14px; height: 6px; cursor: s-resize; }
        .__cr-e  { right: 0; top: 14px; bottom: 14px; width: 6px; cursor: e-resize; }
        .__cr-w  { left: 0; top: 14px; bottom: 14px; width: 6px; cursor: w-resize; }
        .__cr-ne { top: 0; right: 0; width: 14px; height: 14px; cursor: ne-resize; }
        .__cr-nw { top: 0; left: 0; width: 14px; height: 14px; cursor: nw-resize; }
        .__cr-se { bottom: 0; right: 0; width: 14px; height: 14px; cursor: se-resize; }
        .__cr-sw { bottom: 0; left: 0; width: 14px; height: 14px; cursor: sw-resize; }
      `;
      const s = document.createElement('style');
      s.id = '__chat-style';
      s.textContent = css;
      document.head.appendChild(s);
    }

    function addResizeHandles(p) {
      ['n','s','e','w','ne','nw','se','sw'].forEach(dir => {
        const h = document.createElement('div');
        h.className = '__cr __cr-' + dir;
        p.appendChild(h);
        h.addEventListener('mousedown', (e) => startResize(e, dir));
      });
    }

    function startResize(e, dir) {
      e.preventDefault();
      e.stopPropagation();
      const sx = e.clientX, sy = e.clientY;
      const r = panel.getBoundingClientRect();
      const sw = r.width, sh = r.height, sl = r.left, st = r.top;
      const minW = parseInt(panel.style.minWidth, 10) || 200;
      const minH = parseInt(panel.style.minHeight, 10) || 200;
      const move = (ev) => {
        const dx = ev.clientX - sx, dy = ev.clientY - sy;
        let nw = sw, nh = sh, nl = sl, nt = st;
        if (dir.includes('e')) nw = sw + dx;
        if (dir.includes('w')) { nw = sw - dx; nl = sl + dx; }
        if (dir.includes('s')) nh = sh + dy;
        if (dir.includes('n')) { nh = sh - dy; nt = st + dy; }
        if (nw < minW) { if (dir.includes('w')) nl = sl + (sw - minW); nw = minW; }
        if (nh < minH) { if (dir.includes('n')) nt = st + (sh - minH); nh = minH; }
        // viewport 밖으로 나가지 않게.
        if (nl < 0) { nw += nl; nl = 0; if (nw < minW) nw = minW; }
        if (nt < 0) { nh += nt; nt = 0; if (nh < minH) nh = minH; }
        if (nl + nw > window.innerWidth) nw = window.innerWidth - nl;
        if (nt + nh > window.innerHeight) nh = window.innerHeight - nt;
        panel.style.left = nl + 'px';
        panel.style.top = nt + 'px';
        panel.style.width = nw + 'px';
        panel.style.height = nh + 'px';
      };
      const up = () => {
        document.removeEventListener('mousemove', move);
        document.removeEventListener('mouseup', up);
      };
      document.addEventListener('mousemove', move);
      document.addEventListener('mouseup', up);
    }

    function enableHeaderDrag(p) {
      const header = p.querySelector('header');
      if (!header) return;
      header.addEventListener('mousedown', (e) => {
        // 헤더 내부의 버튼(뒤로/닫기)은 드래그 시작 X.
        if (e.target.closest('button')) return;
        e.preventDefault();
        const sx = e.clientX, sy = e.clientY;
        const r = panel.getBoundingClientRect();
        const sl = r.left, st = r.top;
        const move = (ev) => {
          const dx = ev.clientX - sx, dy = ev.clientY - sy;
          let nl = sl + dx, nt = st + dy;
          // 패널 일부는 viewport 안에 항상 보이도록 클램프 (최소 100px 안에 헤더가 보임).
          nl = Math.max(-r.width + 100, Math.min(window.innerWidth - 100, nl));
          nt = Math.max(0, Math.min(window.innerHeight - 50, nt));
          panel.style.left = nl + 'px';
          panel.style.top = nt + 'px';
        };
        const up = () => {
          document.removeEventListener('mousemove', move);
          document.removeEventListener('mouseup', up);
        };
        document.addEventListener('mousemove', move);
        document.addEventListener('mouseup', up);
      });
    }

    // ─── 패널 열기/닫기 ────────────────────────────────────────────
    const openPanel = () => {
      panel.classList.remove('hidden');
      fab.setAttribute('aria-expanded', 'true');
      setupDraggableResizablePanel();  // 첫 오픈에만 실제 실행됨 (idempotent)
      showList();
    };
    const closePanel = () => {
      panel.classList.add('hidden');
      fab.setAttribute('aria-expanded', 'false');
    };
    fab.addEventListener('click', () => {
      if (panel.classList.contains('hidden')) openPanel();
      else closePanel();
    });
    closeBtn.addEventListener('click', closePanel);
    backBtn.addEventListener('click', () => {
      if (view !== 'thread' && view !== 'pick') return;
      // AI thread 에서 뒤로 → AI 목록 복귀. 사람 채팅·pick → 직원 목록.
      if (view === 'thread' && activeMode === 'ai') {
        switchPanelTab('ai');
      } else {
        showList();
      }
    });
    if (tabChatBtn) {
      tabChatBtn.addEventListener('click', () => switchPanelTab('chat'));
    }
    if (tabAiBtn) {
      tabAiBtn.addEventListener('click', () => switchPanelTab('ai'));
    }
    if (aiNewBtn) {
      aiNewBtn.addEventListener('click', createAiConversation);
    }
    if (aiBodyEl) {
      aiBodyEl.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-ai-session-id]');
        if (!btn) return;
        const sessionId = Number(btn.dataset.aiSessionId);
        if (!sessionId) return;
        showThread(sessionId, btn.dataset.aiTitle || 'AI 비서', 'ai');
      });
    }
    // Esc 로도 닫음 — 단 입력 포커스 중일 땐 무시 (한글 IME 충돌 방지)
    document.addEventListener('keydown', (e) => {
      if (e.key !== 'Escape') return;
      if (panel.classList.contains('hidden')) return;
      const active = document.activeElement;
      if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) return;
      closePanel();
    });

    // ─── 대화 목록 ──────────────────────────────────────────────────
    async function loadConversations() {
      try {
        const res = await fetch('/api/chat/conversations');
        if (!res.ok) throw new Error('list failed');
        convsCache = await res.json();
        renderConversations();
      } catch (e) {
        listEl.innerHTML = '<div class="px-4 py-6 text-center text-xs text-gray-400">대화 목록을 불러올 수 없습니다.</div>';
      }
    }
    function renderConversations() {
      // 총합 안 읽음 = 헤더 badge + FAB badge 공통 데이터. convsCache 의 unreadCount 합산.
      const totalUnread = convsCache.reduce((sum, c) => sum + (c.unreadCount || 0), 0);
      updateUnreadBadges(totalUnread);

      if (convsCache.length === 0) {
        listEl.innerHTML = '<div class="px-4 py-10 text-center text-xs text-gray-400">대화가 없습니다. + 버튼으로 새 채팅을 시작하세요.</div>';
        return;
      }
      listEl.innerHTML = convsCache.map(c => {
        const peer = c.peer || {};
        const preview = c.lastMessagePreview
          ? (c.lastSenderIsMe ? '나: ' : '') + esc(c.lastMessagePreview)
          : '<span class="text-gray-300">메시지 없음</span>';
        const time = fmtRelative(c.updatedAt);
        const avatar = avatarHtml(peer, 36);
        // 행별 안 읽음 배지 — 헤더 알림 종 배지(#header-alert-badge) 스타일과 통일 (orange-500 + 흰 글씨).
        const unread = c.unreadCount || 0;
        const unreadBadge = unread > 0
          ? '<span class="inline-flex h-5 min-w-5 max-w-[2.75rem] shrink-0 items-center justify-center overflow-hidden rounded-full bg-orange-500 px-1 text-[10px] font-bold tabular-nums leading-none text-white shadow-sm">' + (unread > 99 ? '99+' : unread) + '</span>'
          : '';
        return ''
          + '<button type="button" data-conv-id="' + c.conversationId + '" data-peer-name="' + esc(peer.name || '') + '" '
          + '  class="w-full flex items-start gap-3 px-4 py-3 text-left hover:bg-gray-50 dark:hover:bg-white/5">'
          +   avatar
          +   '<div class="min-w-0 flex-1">'
          +     '<div class="flex items-center justify-between gap-2">'
          +       '<span class="truncate text-sm font-semibold text-gray-900 dark:text-white">' + esc(peer.name || '?') + '<span class="ml-1 text-xs font-normal text-gray-500">·' + esc(peer.deptName || '미지정') + '</span></span>'
          +       '<span class="shrink-0 flex items-center gap-1.5 text-[11px] text-gray-400">' + time + unreadBadge + '</span>'
          +     '</div>'
          +     '<div class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">' + preview + '</div>'
          +   '</div>'
          + '</button>';
      }).join('');
    }

    /** 헤더 배지 (#chat-badge) + FAB 배지 (#chat-fab-badge) 를 총합 unread 로 동기화. */
    function updateUnreadBadges(total) {
      const display = total > 99 ? '99+' : String(total);
      if (badgeEl) badgeEl.textContent = display;
      const fabBadge = document.getElementById('chat-fab-badge');
      if (fabBadge) {
        if (total > 0) {
          fabBadge.textContent = display;
          fabBadge.classList.remove('hidden');
          fabBadge.classList.add('inline-flex');
        } else {
          fabBadge.classList.add('hidden');
          fabBadge.classList.remove('inline-flex');
        }
      }
    }

    /** 페이지 로드 직후 / SSE 수신 시: 패널 닫힌 상태에서도 FAB 배지를 위해 총합 1회 fetch. */
    async function refreshUnreadTotal() {
      try {
        const res = await fetch('/api/chat/unread');
        if (!res.ok) return;
        const total = await res.json();
        updateUnreadBadges(typeof total === 'number' ? total : 0);
      } catch (e) { /* 무시 */ }
    }

    /** 채팅방 진입 = 그 대화방의 본인 알림 일괄 삭제 후 배지 즉시 갱신. */
    async function markConversationRead(convId) {
      try {
        await fetch('/api/chat/conversations/' + convId + '/read', {
          method: 'POST',
          headers: csrfHeader(),
        });
        const found = convsCache.find(c => c.conversationId === convId);
        if (found) found.unreadCount = 0;
        const total = convsCache.reduce((sum, c) => sum + (c.unreadCount || 0), 0);
        updateUnreadBadges(total);
      } catch (e) { /* 무시 */ }
    }
    listEl.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-conv-id]');
      if (!btn) return;
      showThread(Number(btn.dataset.convId), btn.dataset.peerName || '');
    });
    newBtn.addEventListener('click', showPick);

    // ─── 회원 선택 ──────────────────────────────────────────────────
    async function loadPeers() {
      try {
        const res = await fetch('/api/chat/members');
        if (!res.ok) throw new Error('peers failed');
        peersCache = await res.json();
        rebuildPickFilters();
        renderPeers();
      } catch (e) {
        pickListEl.innerHTML = '<div class="px-4 py-6 text-center text-xs text-gray-400">직원 목록을 불러올 수 없습니다.</div>';
      }
    }
    function rebuildPickFilters() {
      const depts = Array.from(new Set(peersCache.map(p => (p.deptName || '').trim()).filter(Boolean))).sort((a, b) => a.localeCompare(b, 'ko'));
      const positions = Array.from(new Set(peersCache.map(p => (p.positionName || '').trim()).filter(Boolean))).sort((a, b) => a.localeCompare(b, 'ko'));
      pickDept.innerHTML = '<option value="">전체 부서</option>' + depts.map(d => '<option value="' + esc(d) + '">' + esc(d) + '</option>').join('');
      pickPos.innerHTML = '<option value="">전체 직급</option>' + positions.map(p => '<option value="' + esc(p) + '">' + esc(p) + '</option>').join('');
    }
    function renderPeers() {
      const dept = (pickDept.value || '').trim();
      const pos = (pickPos.value || '').trim();
      const name = (pickName.value || '').trim().toLowerCase();
      const list = peersCache.filter(p => {
        if (dept && (p.deptName || '') !== dept) return false;
        if (pos && (p.positionName || '') !== pos) return false;
        if (name && !((p.name || '').toLowerCase().includes(name))) return false;
        return true;
      });
      if (list.length === 0) {
        pickListEl.innerHTML = '<div class="px-4 py-10 text-center text-xs text-gray-400">조건에 맞는 직원이 없습니다.</div>';
        return;
      }
      pickListEl.innerHTML = list.map(p => {
        const avatar = avatarHtml(p, 40);
        return ''
          + '<button type="button" data-peer-id="' + p.memberId + '" data-peer-name="' + esc(p.name || '') + '" '
          + '  class="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-gray-50 dark:hover:bg-white/5">'
          +   avatar
          +   '<div class="min-w-0 flex-1">'
          +     '<div class="truncate text-sm font-semibold text-gray-900 dark:text-white">' + esc(p.name || '?') + '</div>'
          +     '<div class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">' + esc(p.deptName || '미지정') + ' · ' + esc(p.positionName || '미지정') + '</div>'
          +   '</div>'
          + '</button>';
      }).join('');
    }
    pickListEl.addEventListener('click', async (e) => {
      const btn = e.target.closest('[data-peer-id]');
      if (!btn) return;
      btn.disabled = true;
      try {
        const res = await fetch('/api/chat/conversations', {
          method: 'POST',
          headers: Object.assign({ 'Content-Type': 'application/json' }, csrfHeader()),
          body: JSON.stringify({ peerId: Number(btn.dataset.peerId) }),
        });
        if (!res.ok) throw new Error('open conv failed');
        const conv = await res.json();
        showThread(conv.conversationId, btn.dataset.peerName || (conv.peer && conv.peer.name) || '');
      } catch (err) {
        alert('대화방을 열 수 없습니다.');
      } finally {
        btn.disabled = false;
      }
    });
    [pickName, pickDept, pickPos].forEach(el => el && el.addEventListener('input', renderPeers));
    pickDept.addEventListener('change', renderPeers);
    pickPos.addEventListener('change', renderPeers);

    // ─── 메시지 스레드 ─────────────────────────────────────────────
    /** AI 응답 메시지 → 기존 messageBubbleHtml 이 기대하는 chat 메시지 형식으로 어댑팅. */
    function aiMsgToChatBubble(m, meId) {
      const isUser = m.role === 'USER';
      return {
        messageId: m.messageId,
        senderId: isUser ? meId : -1,         // AI 응답은 좌측 정렬용으로 음수 senderId
        senderName: isUser ? '나' : 'AI 비서',
        text: m.content,
        createdAt: m.createdAt,
        attachments: [],
        // AI 액션 제안 (일정/결재) 메타. 카드 렌더용.
        proposal: m.proposal || null,
        proposalApplied: m.proposalApplied === true,
      };
    }

    /** ISO datetime → "2026년 5월 20일 12:30" 형식. */
    function fmtKoreanDateTime(isoStr) {
      if (!isoStr) return '';
      const m = String(isoStr).match(/(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/);
      if (!m) return isoStr;
      return `${m[1]}년 ${parseInt(m[2],10)}월 ${parseInt(m[3],10)}일 ${m[4]}:${m[5]}`;
    }
    /** 같은 날이면 "HH:mm", 다른 날이면 전체 "년월일 HH:mm" 으로. */
    function fmtSameDayTime(isoStr) {
      if (!isoStr) return '';
      const m = String(isoStr).match(/[T ](\d{2}):(\d{2})/);
      return m ? `${m[1]}:${m[2]}` : isoStr;
    }
    function sameDate(a, b) {
      if (!a || !b) return false;
      return String(a).slice(0, 10) === String(b).slice(0, 10);
    }

    /** 일정 제안 카드 HTML. type=calendar|calendar_update|calendar_delete. */
    function calendarProposalCardHtml(m) {
      const p = m.proposal || {};
      const isUpdate = p.type === 'calendar_update';
      const isDelete = p.type === 'calendar_delete';
      const head = isDelete ? '🗑️ 일정 삭제 제안'
                 : isUpdate ? '✏️ 일정 수정 제안'
                            : '📅 일정 등록 제안';
      const actionLabel = isDelete ? '삭제' : (isUpdate ? '수정' : '등록');
      const actionAttr  = isDelete ? 'data-ai-confirm-delete'
                       : isUpdate ? 'data-ai-confirm-update'
                                  : 'data-ai-confirm-calendar';
      const doneLabel = actionLabel + '됨';

      const title = esc(p.title || (isDelete ? '일정' : 'AI 일정'));
      const startH = fmtKoreanDateTime(p.startAt);
      const endH   = sameDate(p.startAt, p.endAt) ? fmtSameDayTime(p.endAt) : fmtKoreanDateTime(p.endAt);
      const loc   = p.location ? '<div>📍 ' + esc(p.location) + '</div>' : '';
      const desc  = p.description ? '<div class="text-gray-500 mt-1">' + esc(p.description) + '</div>' : '';
      const attendees = (Array.isArray(p.attendeeNames) && p.attendeeNames.length > 0)
        ? '<div>👥 함께: ' + p.attendeeNames.map(esc).join(', ') + '</div>'
        : '';
      const applied = m.proposalApplied;

      // 액션별 버튼 색 — Tailwind 캐시 / output.css 미빌드 영향 없이 inline 으로 강제.
      let btnBg = '#4f46e5';                              // 등록 — indigo-600
      if (isUpdate) btnBg = '#f97316';                    // 수정 — orange-500
      if (isDelete) btnBg = '#ef4444';                    // 삭제 — red-500
      const btnStyle = 'background:' + btnBg + ';color:#fff;border:none;padding:6px 16px;border-radius:6px;font-size:12px;font-weight:700;cursor:pointer;box-shadow:0 1px 3px rgba(0,0,0,0.2);';
      const doneStyle = 'background:#d1d5db;color:#4b5563;border:none;padding:6px 16px;border-radius:6px;font-size:12px;font-weight:700;';
      const btn = applied
        ? '<button type="button" disabled style="' + doneStyle + '">' + esc(doneLabel) + '</button>'
        : '<button type="button" ' + actionAttr + '="' + m.messageId + '" style="' + btnStyle + '">' + esc(actionLabel) + '</button>';

      return ''
        + '<div class="mb-2 flex justify-start">'
        +   '<div class="max-w-[85%] rounded-2xl border border-indigo-200 bg-indigo-50/50 p-3 text-xs text-gray-700 dark:border-indigo-900/50 dark:bg-indigo-900/20 dark:text-gray-200">'
        +     '<div class="mb-1 text-[11px] font-semibold text-indigo-600 dark:text-indigo-300">' + head + '</div>'
        +     '<div class="font-semibold text-gray-900 dark:text-white">' + title + '</div>'
        +     '<div class="mt-1 space-y-0.5">'
        +       (startH ? '<div>🕒 ' + startH + (endH ? ' ~ ' + endH : '') + '</div>' : '')
        +       loc + attendees + desc
        +     '</div>'
        +     '<div class="mt-2 flex justify-end gap-2">' + btn + '</div>'
        +   '</div>'
        + '</div>';
    }

    // 휴가 종류 — VacationType.java 와 동기화. [enum 코드, 한글 라벨].
    const VACATION_TYPES = [
      ['ANNUAL_PAID_LEAVE',   '연차유급휴가'],
      ['SICK_LEAVE',          '병가'],
      ['MATERNITY_LEAVE',     '출산전후휴가'],
      ['PATERNITY_LEAVE',     '배우자출산휴가'],
      ['MENSTRUAL_LEAVE',     '생리휴가'],
      ['FAMILY_CARE_LEAVE',   '가족돌봄휴가'],
      ['SPECIAL_LEAVE',       '경조사휴가'],
      ['REFRESH_LEAVE',       '리프레시휴가'],
      ['SUMMER_VACATION',     '하계휴가'],
      ['REPLACEMENT_HOLIDAY', '대체휴일'],
      ['ETC',                 '기타'],
    ];
    function vacationTypeLabel(code) {
      const f = VACATION_TYPES.find(x => x[0] === code);
      return f ? f[1] : '기타';
    }

    /**
     * 결재선 표시 — "홍길동(인사팀/부장) → 김철수(대표)".
     * 보강된 approvers 배열(부서/직급 포함) 우선, 없으면 approverNames 로 이름만.
     * flex-wrap 이라 결재자가 많아 가로가 길어지면 자동으로 아래 줄로 내려간다.
     */
    function leaveApproverChips(p) {
      let items = [];
      if (Array.isArray(p.approvers) && p.approvers.length > 0) {
        items = p.approvers.map(a => {
          const meta = [a.dept, a.position].filter(Boolean).join('/');
          return { text: meta ? a.name + '(' + meta + ')' : a.name, ok: a.matched !== false };
        });
      } else if (Array.isArray(p.approverNames) && p.approverNames.length > 0) {
        items = p.approverNames.map(n => ({ text: n, ok: true }));
      }
      if (items.length === 0) return '';
      const chips = items.map((it, i) => {
        const bg = it.ok ? '#ccfbf1' : '#fee2e2';
        const fg = it.ok ? '#0f766e' : '#dc2626';
        return '<span style="white-space:nowrap;background:' + bg + ';color:' + fg + ';border-radius:4px;padding:1px 6px;">'
          + esc(it.text) + (it.ok ? '' : ' ⚠️') + '</span>'
          + (i < items.length - 1 ? '<span style="color:#0d9488;">→</span>' : '');
      }).join('');
      return '<div style="display:flex;flex-wrap:wrap;align-items:center;gap:3px;margin-top:2px;">'
        + '<span>🗂️ 결재선</span>' + chips + '</div>';
    }

    /** 휴가 신청 제안 카드 HTML. type=leave. 휴가 종류는 dropdown 으로 사용자가 확정. */
    function leaveProposalCardHtml(m) {
      const p = m.proposal || {};
      const applied = m.proposalApplied;
      const selType = (p.vacationType && VACATION_TYPES.some(x => x[0] === p.vacationType))
        ? p.vacationType : 'ANNUAL_PAID_LEAVE';
      const range = p.startDate
        ? ((p.endDate && p.endDate !== p.startDate)
            ? esc(p.startDate) + ' ~ ' + esc(p.endDate)
            : esc(p.startDate))
        : '';
      const days   = (p.totalDays != null) ? esc(String(p.totalDays)) + '일' : '';
      const reason = p.reason ? '<div>📝 ' + esc(p.reason) + '</div>' : '';
      const line   = leaveApproverChips(p);

      // 휴가 종류 — 신청 전엔 dropdown, 신청 후엔 고정 텍스트.
      let typeField;
      if (applied) {
        typeField = '<div>🏷️ ' + esc(vacationTypeLabel(selType)) + '</div>';
      } else {
        const opts = VACATION_TYPES.map(([code, label]) =>
          '<option value="' + code + '"' + (code === selType ? ' selected' : '') + '>' + esc(label) + '</option>'
        ).join('');
        typeField = '<div class="flex items-center gap-1">🏷️ <span>휴가 종류</span>'
          + '<select data-leave-type style="border:1px solid #99f6e4;border-radius:6px;padding:2px 6px;font-size:12px;background:#fff;color:#111;">'
          + opts + '</select></div>';
      }

      const btnStyle  = 'background:#0d9488;color:#fff;border:none;padding:6px 16px;border-radius:6px;font-size:12px;font-weight:700;cursor:pointer;box-shadow:0 1px 3px rgba(0,0,0,0.2);';
      const doneStyle = 'background:#d1d5db;color:#4b5563;border:none;padding:6px 16px;border-radius:6px;font-size:12px;font-weight:700;';
      // 보강 검증 — 회원 매칭 안 된 결재자가 있으면 신청 불가.
      const hasUnmatched = (Array.isArray(p.approvers) ? p.approvers : []).some(a => a.matched === false);
      const warn = hasUnmatched
        ? '<div style="color:#dc2626;margin-top:3px;">⚠️ 회원 명단에서 확인되지 않은 결재자가 있습니다. AI 에게 정확한 성함(또는 소속 부서)을 다시 알려주세요.</div>'
        : '';
      let btn;
      if (applied) {
        btn = '<button type="button" disabled style="' + doneStyle + '">신청됨</button>';
      } else if (hasUnmatched) {
        btn = '<button type="button" disabled style="' + doneStyle + '">신청 불가</button>';
      } else {
        btn = '<button type="button" data-ai-confirm-leave="' + m.messageId + '" style="' + btnStyle + '">신청</button>';
      }

      return ''
        + '<div class="mb-2 flex justify-start">'
        +   '<div data-leave-card class="max-w-[85%] rounded-2xl border border-teal-200 bg-teal-50/50 p-3 text-xs text-gray-700 dark:border-teal-900/50 dark:bg-teal-900/20 dark:text-gray-200">'
        +     '<div class="mb-1 text-[11px] font-semibold text-teal-600 dark:text-teal-300">🏖️ 휴가 신청 제안</div>'
        +     '<div class="mt-1 space-y-1">'
        +       typeField
        +       (range ? '<div>🗓️ ' + range + (days ? ' · ' + days : '') + '</div>'
                       : (days ? '<div>🗓️ ' + days + '</div>' : ''))
        +       reason + line + warn
        +     '</div>'
        +     '<div class="mt-2 flex justify-end gap-2">' + btn + '</div>'
        +   '</div>'
        + '</div>';
    }

    async function loadMessages(id) {
      const gen = ++messagesLoadGen;
      messagesEl.innerHTML = '<div class="text-center text-xs text-gray-400 py-6">불러오는 중...</div>';
      const url = (activeMode === 'ai')
        ? '/api/ai/conversations/' + id + '/messages'
        : '/api/chat/conversations/' + id + '/messages';
      try {
        const res = await fetch(url);
        if (!res.ok) throw new Error('load messages failed');
        let msgs = await res.json();
        if (gen !== messagesLoadGen || Number(activeConvId) !== Number(id)) return;
        if (activeMode === 'ai') {
          const me = currentMemberIdGuess();
          msgs = msgs.map(m => aiMsgToChatBubble(m, me));
        }
        renderMessages(msgs);
        scrollMessagesToBottom();
      } catch (e) {
        if (gen !== messagesLoadGen) return;
        messagesEl.innerHTML = '<div class="text-center text-xs text-rose-500 py-6">메시지를 불러올 수 없습니다.</div>';
      }
    }
    /** 메시지 1건 HTML + proposal 카드(있으면). */
    function fullMessageHtml(m, me) {
      let html = messageBubbleHtml(m, me);
      const t = m.proposal && m.proposal.type;
      if (t === 'calendar' || t === 'calendar_update' || t === 'calendar_delete') {
        html += calendarProposalCardHtml(m);
      } else if (t === 'leave') {
        html += leaveProposalCardHtml(m);
      }
      return html;
    }
    function renderMessages(msgs) {
      const me = currentMemberIdGuess();
      if (!msgs || msgs.length === 0) {
        messagesEl.innerHTML = '<div class="text-center text-xs text-gray-400 py-6">첫 메시지를 보내보세요.</div>';
        return;
      }
      messagesEl.innerHTML = msgs.map(m => fullMessageHtml(m, me)).join('');
    }
    function appendMessage(m) {
      if (!m) return;
      const me = currentMemberIdGuess();
      if (m.messageId != null && messagesEl.querySelector('[data-msg-id="' + m.messageId + '"]')) return;
      // 첫 메시지인 경우 안내 텍스트 제거
      if (messagesEl.querySelector('.text-center')) messagesEl.innerHTML = '';
      messagesEl.insertAdjacentHTML('beforeend', fullMessageHtml(m, me));
      scrollMessagesToBottom();
    }

    // 일정 제안 카드의 [등록/수정/삭제] 버튼 위임 처리. 한 endpoint 로 통합 (서버가 type 분기).
    messagesEl.addEventListener('click', async (e) => {
      const btn = e.target.closest(
        '[data-ai-confirm-calendar],[data-ai-confirm-update],[data-ai-confirm-delete]');
      if (!btn) return;
      const messageId = Number(
        btn.dataset.aiConfirmCalendar || btn.dataset.aiConfirmUpdate || btn.dataset.aiConfirmDelete);
      if (!messageId) return;
      const isDelete = !!btn.dataset.aiConfirmDelete;
      const isUpdate = !!btn.dataset.aiConfirmUpdate;
      const actionLabel = isDelete ? '삭제' : isUpdate ? '수정' : '등록';

      btn.disabled = true;
      btn.textContent = actionLabel + ' 중...';
      try {
        const res = await fetch('/api/ai/calendar/confirm', {
          method: 'POST',
          headers: Object.assign({ 'Content-Type': 'application/json' }, csrfHeader()),
          body: JSON.stringify({ messageId }),
        });
        if (!res.ok) throw new Error('confirm failed');
        const confirmMsg = await res.json();
        btn.textContent = actionLabel + '됨';
        btn.style.cssText = 'background:#d1d5db;color:#4b5563;border:none;padding:6px 16px;border-radius:6px;font-size:12px;font-weight:700;';
        const me = currentMemberIdGuess();
        appendMessage(aiMsgToChatBubble(confirmMsg, me));
      } catch (err) {
        alert(actionLabel + '에 실패했습니다.');
        btn.disabled = false;
        btn.textContent = actionLabel;
      }
    });

    // 휴가 신청 카드의 [신청] 버튼 위임 처리 → 전자결재 상신.
    messagesEl.addEventListener('click', async (e) => {
      const btn = e.target.closest('[data-ai-confirm-leave]');
      if (!btn) return;
      const messageId = Number(btn.dataset.aiConfirmLeave);
      if (!messageId) return;

      // 카드 dropdown 에서 사용자가 최종 확정한 휴가 종류.
      const card = btn.closest('[data-leave-card]');
      const sel = card ? card.querySelector('[data-leave-type]') : null;
      const vacationType = sel ? sel.value : null;

      btn.disabled = true;
      btn.textContent = '신청 중...';
      try {
        const res = await fetch('/api/ai/leave/confirm', {
          method: 'POST',
          headers: Object.assign({ 'Content-Type': 'application/json' }, csrfHeader()),
          body: JSON.stringify({ messageId, vacationType }),
        });
        if (!res.ok) throw new Error('confirm failed');
        const confirmMsg = await res.json();
        btn.textContent = '신청됨';
        btn.style.cssText = 'background:#d1d5db;color:#4b5563;border:none;padding:6px 16px;border-radius:6px;font-size:12px;font-weight:700;';
        const me = currentMemberIdGuess();
        appendMessage(aiMsgToChatBubble(confirmMsg, me));
      } catch (err) {
        alert('휴가 신청에 실패했습니다. 결재선 규정을 다시 확인해주세요.');
        btn.disabled = false;
        btn.textContent = '신청';
      }
    });
    // YouTube URL → videoId 추출. youtu.be/ID, youtube.com/watch?v=ID, youtube.com/shorts/ID, youtube.com/embed/ID 지원.
    const YOUTUBE_RE = /(?:youtube\.com\/(?:watch\?v=|shorts\/|embed\/)|youtu\.be\/)([A-Za-z0-9_-]{11})/;
    function extractYoutubeId(text) {
      if (!text) return null;
      const m = text.match(YOUTUBE_RE);
      return m ? m[1] : null;
    }
    function youtubeEmbedHtml(videoId) {
      // videoId 는 [A-Za-z0-9_-]{11} 로 정규식이 이미 보장 — XSS 안전.
      // 너비는 --chat-media-max (디폴트 패널 너비 × 0.6) — 이미지/비디오와 통일. 16:9 비율 유지.
      return '<div class="mt-1 aspect-video overflow-hidden rounded-lg" style="width:var(--chat-media-max, 200px);max-width:100%;">'
        + '<iframe src="https://www.youtube.com/embed/' + videoId + '" '
        + 'class="h-full w-full" frameborder="0" '
        + 'allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" '
        + 'allowfullscreen></iframe></div>';
    }

    function messageBubbleHtml(m, meId) {
      const mine = meId != null && m.senderId === meId;
      const align = mine ? 'justify-end' : 'justify-start';
      const hasText = !!m.text;
      const hasMedia = (m.attachments && m.attachments.length > 0) || extractYoutubeId(m.text);
      // 미디어 있는 말풍선은 padding 최소화 (둘레 두꺼운 테두리 X).
      const padCls = hasText ? (hasMedia ? 'p-1.5' : 'px-3 py-2') : 'p-1';
      const bubbleCls = mine
        ? 'rounded-2xl rounded-br-sm text-white ' + padCls
        : 'rounded-2xl rounded-bl-sm bg-gray-100 text-gray-900 dark:bg-gray-700 dark:text-gray-100 ' + padCls;
      const bubbleStyle = mine ? 'background-color:#4f46e5;' : '';
      const time = fmtTime(m.createdAt);
      // URL 같이 break 가 어려운 긴 문자열도 강제 분할 — wrap 의 width 강제가 min-content 에 의해 무시되는 것 방지.
      const text = hasText
        ? '<div class="whitespace-pre-wrap" style="overflow-wrap:anywhere;word-break:break-word;">' + esc(m.text) + '</div>'
        : '';
      const ytId = extractYoutubeId(m.text);
      const youtube = ytId ? youtubeEmbedHtml(ytId) : '';
      const atts = (m.attachments || []).map(att => attachmentHtml(att, mine)).join('');
      // 미디어가 있으면 말풍선 너비를 미디어 너비와 동일하게 강제. width + max-width 둘 다 박아 자식 min-content 영향 차단.
      const wrapCls = hasMedia ? 'inline-block align-top' : 'max-w-[78%]';
      let wrapStyle = '';
      if (hasMedia) {
        const mediaMaxPx = (panel.style.getPropertyValue('--chat-media-max') || '200px').trim();
        wrapStyle = 'width:' + mediaMaxPx + ';max-width:' + mediaMaxPx + ';';
      }
      const msgAttr = m.messageId != null ? ' data-msg-id="' + esc(String(m.messageId)) + '"' : '';
      return ''
        + '<div class="mb-2 flex ' + align + '"' + msgAttr + '>'
        +   '<div class="' + wrapCls + '" style="' + wrapStyle + '">'
        +     '<div class="' + bubbleCls + ' text-sm" style="' + bubbleStyle + '">'
        +       text
        +       youtube
        +       atts
        +     '</div>'
        +     '<div class="mt-0.5 text-[10px] text-gray-400 ' + (mine ? 'text-right' : 'text-left') + '">' + time + '</div>'
        +   '</div>'
        + '</div>';
    }
    function attachmentHtml(att, mineBubble) {
      // 이미지/비디오 크기는 --chat-media-max (디폴트 패널 너비 × 0.6 px) 로 절대 고정.
      // 패널이 resize 로 커져도 미디어 크기는 변하지 않음 (카톡 스타일).
      const mediaStyle = 'max-width:var(--chat-media-max, 200px);max-height:var(--chat-media-max, 200px);width:auto;height:auto;';
      if (att.kind === 'image') {
        return '<a href="' + esc(att.url) + '" target="_blank" rel="noopener" class="block">'
          + '<img src="' + esc(att.url) + '" alt="' + esc(att.fileName) + '" class="rounded-lg" style="' + mediaStyle + '" />'
          + '</a>';
      }
      if (att.kind === 'video') {
        return '<div>'
          + '<video src="' + esc(att.url) + '" controls preload="metadata" class="rounded-lg" style="' + mediaStyle + '" '
          + (att.mimeType ? 'data-mime="' + esc(att.mimeType) + '"' : '')
          + '></video></div>';
      }
      const fg = mineBubble ? 'text-white/90' : 'text-indigo-600 dark:text-indigo-300';
      return '<a href="' + esc(att.url) + '" target="_blank" rel="noopener" class="mt-1 inline-flex items-center gap-1.5 underline ' + fg + '">'
        + '<svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">'
        + '<path d="M9 2H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1V6L9 2zM9 2v4h4" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        + esc(att.fileName)
        + '</a>';
    }
    function scrollMessagesToBottom() {
      messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    // ─── 파일 첨부 ─────────────────────────────────────────────────
    fileInput.addEventListener('change', () => {
      const files = Array.from(fileInput.files || []);
      pendingFiles = pendingFiles.concat(files);
      fileInput.value = '';
      renderFilePreview();
    });
    function renderFilePreview() {
      if (!filePreview) return;
      if (pendingFiles.length === 0) {
        filePreview.classList.add('hidden');
        filePreview.innerHTML = '';
        return;
      }
      filePreview.classList.remove('hidden');
      filePreview.classList.add('flex');
      filePreview.innerHTML = pendingFiles.map((f, i) =>
        '<span class="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-700 dark:bg-gray-700 dark:text-gray-200">'
        + esc(f.name)
        + '<button type="button" data-rm="' + i + '" class="ml-0.5 text-gray-400 hover:text-rose-500" aria-label="제거">×</button>'
        + '</span>'
      ).join('');
    }
    filePreview.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-rm]');
      if (!btn) return;
      const idx = Number(btn.dataset.rm);
      pendingFiles.splice(idx, 1);
      renderFilePreview();
    });

    // ─── 전송 ──────────────────────────────────────────────────────
    async function sendMessage() {
      if (!activeConvId) return;
      const text = inputEl.value.trim();
      const hasText = text.length > 0;
      const hasFiles = pendingFiles.length > 0;
      // AI 모드는 텍스트만, 사람 채팅은 텍스트 또는 파일
      if (activeMode === 'ai' ? !hasText : (!hasText && !hasFiles)) return;
      sendBtn.disabled = true;
      // 사용자 말풍선을 먼저 그려두면 AI 응답 대기 시간 UX 가 자연스러움.
      const me = currentMemberIdGuess();
      try {
        if (activeMode === 'ai') {
          // 즉시 USER 말풍선 표시
          appendMessage({ messageId: 'tmp-' + Date.now(), senderId: me, text, createdAt: new Date().toISOString(), attachments: [] });
          inputEl.value = '';
          // 로딩 표시
          const loadingHtml = '<div id="chat-ai-loading" class="mb-2 flex justify-start"><div class="rounded-2xl rounded-bl-sm bg-gray-100 px-3 py-2 text-xs text-gray-500 dark:bg-gray-700 dark:text-gray-300">AI 응답 생성 중...</div></div>';
          messagesEl.insertAdjacentHTML('beforeend', loadingHtml);
          scrollMessagesToBottom();
          const res = await fetch('/api/ai/conversations/' + activeConvId + '/messages', {
            method: 'POST',
            headers: Object.assign({ 'Content-Type': 'application/json' }, csrfHeader()),
            body: JSON.stringify({ content: text }),
          });
          const loading = document.getElementById('chat-ai-loading');
          if (loading) loading.remove();
          if (!res.ok) {
            // 서버의 errorCode/message 를 사용자에게 그대로 노출 (특히 429 quota)
            let errMsg = 'AI 응답 생성에 실패했습니다.';
            try {
              const errBody = await res.json();
              if (errBody && errBody.message) errMsg = errBody.message;
            } catch {}
            throw new Error(errMsg);
          }
          const dto = await res.json();
          appendMessage(aiMsgToChatBubble(dto, me));
        } else {
          const form = new FormData();
          if (hasText) form.append('text', text);
          pendingFiles.forEach(f => form.append('files', f));
          const res = await fetch('/api/chat/conversations/' + activeConvId + '/messages', {
            method: 'POST',
            headers: csrfHeader(),
            body: form,
          });
          if (!res.ok) throw new Error('send failed');
          const dto = await res.json();
          appendMessage(dto);
          inputEl.value = '';
          pendingFiles = [];
          renderFilePreview();
        }
      } catch (e) {
        const loading = document.getElementById('chat-ai-loading');
        if (loading) loading.remove();
        const fallback = activeMode === 'ai' ? 'AI 응답 생성에 실패했습니다.' : '메시지 전송에 실패했습니다.';
        alert(e && e.message ? e.message : fallback);
      } finally {
        sendBtn.disabled = false;
        inputEl.focus();
      }
    }
    sendBtn.addEventListener('click', sendMessage);
    inputEl.addEventListener('keydown', (e) => {
      if (e.key !== 'Enter') return;
      if (e.isComposing || e.keyCode === 229) return;  // 한글 IME 조합 중 무시
      e.preventDefault();
      sendMessage();
    });

    // ─── 본인 ID 추정 ──────────────────────────────────────────────
    // MainController 가 currentMemberId 를 meta 로 노출하지는 않으므로,
    // 메시지 목록 응답에서 "내가 보낸 메시지" 여부 판단 시 사용. 헬퍼는 conv 호출 시 응답의 peer 정보로 역추적 가능.
    // MVP: 대화방 목록에 lastSenderIsMe 가 있으니, 그것으로 me 를 식별. 없으면 null → 좌측 정렬로 보임.
    function currentMemberIdGuess() {
      // chat-stack 의 data-me-id 가 SSR 로 박혀 있음 (partials/chat.html). 비로그인 시 빈 문자열.
      const raw = document.getElementById('chat-stack')?.dataset.meId;
      if (raw) {
        const n = Number(raw);
        if (!Number.isNaN(n)) return n;
      }
      // fallback: 일부 페이지 meta
      const meta = document.querySelector('meta[name="current-member-id"]');
      if (meta && meta.content) return Number(meta.content);
      return null;
    }

    // ─── 실시간 수신 (SSE) ─────────────────────────────────────────
    // 서버: GET /api/chat/stream → "ready"(핸드셰이크) / "chat-message"({ conversationId, message })
    function handleChatSsePayload(raw) {
      let payload = raw;
      if (typeof payload === 'string') {
        try { payload = JSON.parse(payload); } catch { return; }
      }
      if (!payload || payload.message == null) return;
      let msg = payload.message;
      if (typeof msg === 'string') {
        try { msg = JSON.parse(msg); } catch { return; }
      }
      const incomingConvId = Number(payload.conversationId);
      // 같은 채팅방 보고 있으면 즉시 말풍선 추가 + 그 즉시 읽음 처리.
      if (view === 'thread' && Number(activeConvId) === incomingConvId) {
        appendMessage(msg);
        markConversationRead(incomingConvId);
      } else {
        // 그 외엔 FAB/헤더 총합만 즉시 갱신 + 토스트 알림 (다른 채팅방 새 메시지).
        refreshUnreadTotal();
        showChatToast(msg, incomingConvId);
      }
      if (view === 'list' && panelTab === 'chat') {
        loadConversations();  // 행별 배지 + 미리보기 갱신
      }
    }

    /** 새 메시지 토스트 — FAB 위쪽에 잠깐 떴다 사라짐. 클릭하면 해당 채팅방 진입. */
    function showChatToast(msg, convId) {
      if (!msg) return;
      const senderName = msg.senderName || '새 메시지';
      const preview = msg.text
        ? (msg.text.length > 40 ? msg.text.slice(0, 40) + '…' : msg.text)
        : '[파일]';
      // 토스트 컨테이너는 한 번만 생성 (body 직속).
      let host = document.getElementById('chat-toast-host');
      if (!host) {
        host = document.createElement('div');
        host.id = 'chat-toast-host';
        host.style.cssText = 'position:fixed;right:24px;bottom:108px;z-index:10090;display:flex;flex-direction:column;gap:8px;pointer-events:none;';
        document.body.appendChild(host);
      }
      const toast = document.createElement('button');
      toast.type = 'button';
      toast.style.cssText = 'pointer-events:auto;max-width:280px;text-align:left;border-radius:12px;background:#4f46e5;color:white;padding:10px 14px;box-shadow:0 6px 20px rgba(0,0,0,.2);transition:opacity .3s ease, transform .3s ease;opacity:0;transform:translateY(8px);cursor:pointer;border:none;';
      toast.innerHTML =
        '<div style="font-weight:700;font-size:13px;margin-bottom:2px;">' + esc(senderName) + '</div>'
        + '<div style="font-size:12px;opacity:0.95;white-space:pre-wrap;overflow-wrap:anywhere;">' + esc(preview) + '</div>';
      toast.addEventListener('click', () => {
        // 패널 닫혀있으면 열고, 해당 채팅방 진입.
        if (panel.classList.contains('hidden')) openPanel();
        showThread(convId, senderName);
        toast.remove();
      });
      host.appendChild(toast);
      // 슬라이드 인
      requestAnimationFrame(() => {
        toast.style.opacity = '1';
        toast.style.transform = 'translateY(0)';
      });
      // 4초 후 자동 fade out + 제거
      setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(8px)';
        setTimeout(() => toast.remove(), 350);
      }, 4000);
    }

    function connectChatStream() {
      if (typeof EventSource === 'undefined') return;
      let es;
      try {
        es = new EventSource('/api/chat/stream');
      } catch {
        return;
      }
      const onChatEvent = (evt) => {
        try { handleChatSsePayload(JSON.parse(evt.data)); } catch {}
      };
      es.addEventListener('chat-message', onChatEvent);
      es.addEventListener('message', onChatEvent);
      window.addEventListener('beforeunload', () => { try { es.close(); } catch {} });
    }
    connectChatStream();
    // 페이지 로드 시 FAB 배지를 위해 안 읽음 총합 1회 fetch.
    refreshUnreadTotal();
  });
})();
