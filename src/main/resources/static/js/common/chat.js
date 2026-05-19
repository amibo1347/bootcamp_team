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

    const closeBtn = document.getElementById('chat-close');
    const backBtn = document.getElementById('chat-back');
    const titleEl = document.getElementById('chat-title');
    const badgeEl = document.getElementById('chat-badge');

    const screenList = document.getElementById('chat-screen-list');
    const screenThread = document.getElementById('chat-screen-thread');
    const screenPick = document.getElementById('chat-screen-pick');

    const listEl = document.getElementById('chat-list');
    const newBtn = document.getElementById('chat-new');

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
    let activeConvId = null;
    let activePeerName = '';
    let peersCache = [];              // 회원 목록 캐시
    let convsCache = [];              // 대화방 목록 캐시
    let pendingFiles = [];            // 전송 대기 파일

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

    // ─── 화면 전환 ─────────────────────────────────────────────────
    const showList = () => {
      view = 'list';
      activeConvId = null;
      screenList.classList.remove('hidden');
      screenThread.classList.add('hidden');
      screenPick.classList.add('hidden');
      backBtn.classList.add('hidden');
      titleEl.textContent = '채팅';
      loadConversations();
    };
    const showThread = (convId, peerName) => {
      view = 'thread';
      activeConvId = convId;
      activePeerName = peerName || '';
      screenList.classList.add('hidden');
      screenThread.classList.remove('hidden');
      screenPick.classList.add('hidden');
      backBtn.classList.remove('hidden');
      titleEl.textContent = activePeerName || '대화';
      pendingFiles = [];
      renderFilePreview();
      inputEl.value = '';
      loadMessages(convId);
    };
    const showPick = () => {
      view = 'pick';
      activeConvId = null;
      screenList.classList.add('hidden');
      screenThread.classList.add('hidden');
      screenPick.classList.remove('hidden');
      backBtn.classList.remove('hidden');
      titleEl.textContent = '새 채팅';
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
      if (view === 'thread' || view === 'pick') showList();
    });
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
      badgeEl.textContent = String(convsCache.length);
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
        return ''
          + '<button type="button" data-conv-id="' + c.conversationId + '" data-peer-name="' + esc(peer.name || '') + '" '
          + '  class="w-full flex items-start gap-3 px-4 py-3 text-left hover:bg-gray-50 dark:hover:bg-white/5">'
          +   avatar
          +   '<div class="min-w-0 flex-1">'
          +     '<div class="flex items-center justify-between gap-2">'
          +       '<span class="truncate text-sm font-semibold text-gray-900 dark:text-white">' + esc(peer.name || '?') + '<span class="ml-1 text-xs font-normal text-gray-500">·' + esc(peer.deptName || '미지정') + '</span></span>'
          +       '<span class="shrink-0 text-[11px] text-gray-400">' + time + '</span>'
          +     '</div>'
          +     '<div class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">' + preview + '</div>'
          +   '</div>'
          + '</button>';
      }).join('');
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
    async function loadMessages(convId) {
      messagesEl.innerHTML = '<div class="text-center text-xs text-gray-400 py-6">불러오는 중...</div>';
      try {
        const res = await fetch('/api/chat/conversations/' + convId + '/messages');
        if (!res.ok) throw new Error('load messages failed');
        const msgs = await res.json();
        renderMessages(msgs);
        scrollMessagesToBottom();
      } catch (e) {
        messagesEl.innerHTML = '<div class="text-center text-xs text-rose-500 py-6">메시지를 불러올 수 없습니다.</div>';
      }
    }
    function renderMessages(msgs) {
      const me = currentMemberIdGuess();
      if (!msgs || msgs.length === 0) {
        messagesEl.innerHTML = '<div class="text-center text-xs text-gray-400 py-6">첫 메시지를 보내보세요.</div>';
        return;
      }
      messagesEl.innerHTML = msgs.map(m => messageBubbleHtml(m, me)).join('');
    }
    function appendMessage(m) {
      const me = currentMemberIdGuess();
      // 첫 메시지인 경우 안내 텍스트 제거
      if (messagesEl.querySelector('.text-center')) messagesEl.innerHTML = '';
      messagesEl.insertAdjacentHTML('beforeend', messageBubbleHtml(m, me));
      scrollMessagesToBottom();
    }
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
      return ''
        + '<div class="mb-2 flex ' + align + '">'
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
      if (!hasText && !hasFiles) return;
      sendBtn.disabled = true;
      const form = new FormData();
      if (hasText) form.append('text', text);
      pendingFiles.forEach(f => form.append('files', f));
      try {
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
      } catch (e) {
        alert('메시지 전송에 실패했습니다.');
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
    // 서버: GET /api/chat/stream  → 이벤트 "ready" (핸드셰이크) / "message" ({conversationId, message}).
    // EventSource 는 끊겨도 브라우저가 자동 재연결한다. 페이지당 한 번만 연결.
    function connectChatStream() {
      if (typeof EventSource === 'undefined') return;  // 구형 브라우저 폴백 없음 (MVP)
      let es;
      try { es = new EventSource('/api/chat/stream'); }
      catch (err) { return; }
      es.addEventListener('message', (evt) => {
        let payload;
        try { payload = JSON.parse(evt.data); } catch { return; }
        if (!payload || !payload.message) return;
        const incomingConvId = Number(payload.conversationId);
        const msg = payload.message;
        // 현재 같은 채팅방 보고 있으면 즉시 말풍선 추가.
        if (view === 'thread' && Number(activeConvId) === incomingConvId) {
          appendMessage(msg);
        }
        // 목록 화면이면 미리보기/시간 갱신 위해 다시 로드.
        if (view === 'list') {
          loadConversations();
        }
      });
      // 에러 시 EventSource 가 자동 재연결 — 별도 처리 불필요.
      window.addEventListener('beforeunload', () => { try { es.close(); } catch {} });
    }
    connectChatStream();
  });
})();
