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

    // ─── 패널 열기/닫기 ────────────────────────────────────────────
    const openPanel = () => {
      panel.classList.remove('hidden');
      fab.setAttribute('aria-expanded', 'true');
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
    function messageBubbleHtml(m, meId) {
      const mine = meId != null && m.senderId === meId;
      const align = mine ? 'justify-end' : 'justify-start';
      const bubbleCls = mine
        ? 'rounded-2xl rounded-br-sm px-3 py-2 text-white'
        : 'rounded-2xl rounded-bl-sm px-3 py-2 bg-gray-100 text-gray-900 dark:bg-gray-700 dark:text-gray-100';
      const bubbleStyle = mine ? 'background-color:#4f46e5;' : '';
      const time = fmtTime(m.createdAt);
      const text = m.text ? '<div class="whitespace-pre-wrap break-words">' + esc(m.text) + '</div>' : '';
      const atts = (m.attachments || []).map(att => attachmentHtml(att, mine)).join('');
      return ''
        + '<div class="mb-2 flex ' + align + '">'
        +   '<div class="max-w-[78%]">'
        +     '<div class="' + bubbleCls + ' text-sm" style="' + bubbleStyle + '">'
        +       text
        +       atts
        +     '</div>'
        +     '<div class="mt-0.5 text-[10px] text-gray-400 ' + (mine ? 'text-right' : 'text-left') + '">' + time + '</div>'
        +   '</div>'
        + '</div>';
    }
    function attachmentHtml(att, mineBubble) {
      if (att.isImage) {
        return '<a href="' + esc(att.url) + '" target="_blank" rel="noopener" class="block mt-1">'
          + '<img src="' + esc(att.url) + '" alt="' + esc(att.fileName) + '" class="max-h-48 rounded-lg" />'
          + '</a>';
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
  });
})();
