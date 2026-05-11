(() => {
  /** 마지막으로 로드한 댓글 목록(수정 시 원문 조회용) */
  let cachedComments = [];

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

  /**
   * Accept·CSRF 및 선택적 JSON 본문용 헤더를 만든다.
   * @param {boolean} jsonBody Content-Type: application/json 포함 여부
   * @returns {Record<string, string>}
   */
  function buildHeaders(jsonBody = false) {
    const headers = {
      Accept: 'application/json',
      [csrfHeader]: csrfToken,
    };
    if (jsonBody) {
      headers['Content-Type'] = 'application/json';
    }
    return headers;
  }

  /**
   * 사용자 입력 텍스트를 안전한 HTML 문자열로 이스케이프한다.
   * @param {string} value 원본 문자열
   * @returns {string}
   */
  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  /**
   * 날짜 값을 YYYY-MM-DD HH:mm 형식으로 변환한다.
   * @param {string|number|Date} value 원본 날짜 값
   * @returns {string}
   */
  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}`;
  }

  /**
   * API 응답에서 댓글 ID를 추출한다(commentId 또는 id).
   * @param {Object} comment 댓글 객체
   * @returns {number}
   */
  function getCommentId(comment) {
    const raw = comment?.commentId ?? comment?.id;
    const n = Number(raw);
    return Number.isFinite(n) ? n : 0;
  }

  /**
   * 댓글 객체에서 부모 댓글 ID를 숫자로 추출한다.
   * @param {Object} comment 댓글 객체
   * @returns {number}
   */
  function getParentCommentId(comment) {
    const raw = comment?.parentCommentId ?? comment?.parentId ?? 0;
    const n = Number(raw);
    return Number.isFinite(n) ? n : 0;
  }

  /**
   * 목록 API 응답을 배열 형태로 정규화한다.
   * @param {unknown} data 응답 본문
   * @returns {Array}
   */
  function normalizeCommentsPayload(data) {
    if (Array.isArray(data)) return data;
    if (Array.isArray(data?.content)) return data.content;
    if (Array.isArray(data?.items)) return data.items;
    return [];
  }

  /**
   * body 데이터셋에서 게시판·글·회원·댓글 허용 범위를 읽는다.
   * @returns {{ boardId: number, articleId: number, currentMemberId: number, commentScope: string }}
   */
  function readPageContext() {
    const boardId = Number(document.body.dataset.boardId || 0);
    const articleId = Number(document.body.dataset.articleId || 0);
    const currentMemberId = Number(document.body.dataset.currentMemberId || 0);
    const commentScope = String(document.body.dataset.commentScope || 'ALL').toUpperCase();
    return { boardId, articleId, currentMemberId, commentScope };
  }

  /**
   * 게시판 설정(NONE) 및 비로그인 상태에 따라 작성 폼 UI를 조정한다.
   * @param {{ currentMemberId: number, commentScope: string }} ctx 페이지 컨텍스트
   */
  function applyCommentFormRules(ctx) {
    const formSection = document.getElementById('commentFormSection');
    const textarea = document.getElementById('commentContentInput');
    const submitBtn = document.getElementById('commentSubmitButton');
    const guestNotice = document.getElementById('commentGuestNotice');
    if (!formSection || !textarea || !submitBtn) return;

    const scopeNone = ctx.commentScope === 'NONE';
    if (scopeNone) {
      formSection.classList.add('hidden');
      return;
    }

    formSection.classList.remove('hidden');
    const loggedIn = ctx.currentMemberId > 0;
    textarea.disabled = !loggedIn;
    submitBtn.disabled = !loggedIn;
    if (guestNotice) {
      guestNotice.classList.toggle('hidden', loggedIn);
    }
    if (!loggedIn) {
      textarea.placeholder = '로그인이 필요합니다.';
    }
  }

  /**
   * 로딩·빈 목록·오류 메시지 표시 상태를 갱신한다.
   * @param {{ loading: boolean, empty: boolean, error: string }} state 표시 상태
   */
  function setCommentsStatus(state) {
    const loadingEl = document.getElementById('commentsLoading');
    const emptyEl = document.getElementById('commentsEmpty');
    const errorEl = document.getElementById('commentsLoadError');
    if (loadingEl) loadingEl.classList.toggle('hidden', !state.loading);
    if (emptyEl) emptyEl.classList.toggle('hidden', !state.empty);
    if (errorEl) {
      if (state.error) {
        errorEl.textContent = state.error;
        errorEl.classList.remove('hidden');
      } else {
        errorEl.textContent = '';
        errorEl.classList.add('hidden');
      }
    }
  }

  /**
   * 댓글 목록 GET 요청을 수행한다.
   * @param {number} boardId 게시판 ID
   * @param {number} articleId 게시글 ID
   * @returns {Promise<{ ok: boolean, comments: Array, errorMessage: string | null }>}
   */
  async function requestComments(boardId, articleId) {
    const response = await fetch(`/api/board/${boardId}/articles/${articleId}/comments`, {
      method: 'GET',
      headers: buildHeaders(false),
      credentials: 'same-origin',
    });

    if (!response.ok) {
      const msg =
        response.status === 404
          ? '댓글 목록을 찾을 수 없습니다. API가 아직 준비되지 않았을 수 있습니다.'
          : '댓글을 불러오지 못했습니다.';
      return { ok: false, comments: [], errorMessage: msg };
    }

    const data = await response.json();
    return { ok: true, comments: normalizeCommentsPayload(data), errorMessage: null };
  }

  /**
   * 댓글 한 줄 DOM 문자열을 생성한다. 본문은 escapeHtml 후 pre-wrap로 표시한다.
   * @param {Object} comment 댓글 객체
   * @param {number} currentMemberId 현재 로그인 회원 ID
   * @returns {string}
   */
  function renderCommentItem(comment, currentMemberId, commentScope) {
    const id = getCommentId(comment);
    const parentCommentId = getParentCommentId(comment);
    const isReply = parentCommentId > 0;
    const authorId = Number(comment.authorId || 0);
    const isAuthor = authorId > 0 && authorId === Number(currentMemberId);
    const canReply = Number(currentMemberId) > 0 && commentScope !== 'NONE' && !isReply;
    const authorName = escapeHtml(comment.authorName || '익명');
    const created = formatDate(comment.createdAt);
    const bodyEscaped = escapeHtml(comment.content || '');

    const replyButton = canReply
      ? `
      <button type="button" data-comment-action="reply" data-comment-id="${id}"
        class="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-300">답글</button>
    `
      : '';

    const actions = isAuthor
      ? `
      <div class="flex shrink-0 gap-2">
        <button type="button" data-comment-action="edit" data-comment-id="${id}"
          class="text-xs font-medium text-indigo-600 hover:underline dark:text-indigo-300">수정</button>
        <button type="button" data-comment-action="delete" data-comment-id="${id}"
          class="text-xs font-medium text-rose-600 hover:underline dark:text-rose-400">삭제</button>
      </div>
    `
      : '';

    const replyIndentStyle = isReply ? 'style="margin-left: 2rem;"' : '';

    return `
      <div ${replyIndentStyle}>
        <div class="comment-item rounded-lg ${isReply ? 'bg-transparent' : 'bg-gray-50/90'} px-4 py-3 dark:${isReply ? 'bg-transparent' : 'bg-meta-4/25'}" data-comment-id="${id}">
        <div class="comment-view">
          <div class="flex flex-wrap items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-medium text-gray-900 dark:text-gray-100">${authorName}</span>
                <span class="text-xs text-gray-500 dark:text-gray-400">${created}</span>
                ${replyButton}
              </div>
            </div>
            ${actions}
          </div>
          <p class="comment-text mt-2 whitespace-pre-wrap break-words text-gray-800 dark:text-gray-200">${bodyEscaped}</p>
        </div>
        <div class="comment-edit mt-2 hidden">
          <textarea rows="3"
            class="comment-edit-input w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-800 shadow-sm focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:border-strokedark dark:bg-boxdark dark:text-gray-100 dark:focus:border-indigo-500 dark:focus:ring-indigo-500/30"></textarea>
          <div class="mt-2 flex flex-wrap gap-2">
            <button type="button" data-comment-action="save" data-comment-id="${id}"
              class="rounded-lg bg-indigo-200 px-3 py-1.5 text-xs font-medium text-indigo-800 hover:bg-indigo-300 dark:bg-indigo-500/30 dark:text-indigo-100">저장</button>
            <button type="button" data-comment-action="cancel" data-comment-id="${id}"
              class="rounded-lg bg-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-300 dark:bg-white/10 dark:text-gray-200">취소</button>
          </div>
        </div>
        <div class="comment-reply mt-3 hidden">
          <label class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">대댓글 작성</label>
          <textarea rows="2"
            class="comment-reply-input w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-800 shadow-sm placeholder:text-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:border-strokedark dark:bg-boxdark dark:text-gray-100 dark:focus:border-indigo-500 dark:focus:ring-indigo-500/30"
            placeholder="답글 내용을 입력하세요."></textarea>
          <div class="mt-2 flex flex-wrap gap-2">
            <button type="button" data-comment-action="reply-save" data-comment-id="${id}"
              class="rounded-lg bg-indigo-200 px-3 py-1.5 text-xs font-medium text-indigo-800 hover:bg-indigo-300 dark:bg-indigo-500/30 dark:text-indigo-100">등록</button>
            <button type="button" data-comment-action="reply-cancel" data-comment-id="${id}"
              class="rounded-lg bg-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-300 dark:bg-white/10 dark:text-gray-200">취소</button>
          </div>
        </div>
        </div>
      </div>
    `;
  }

  /**
   * 평면 댓글 배열을 원댓글/대댓글 그룹으로 변환한다.
   * @param {Array} comments 댓글 배열
   * @returns {Array<{ root: Object, replies: Array }>}
   */
  function groupCommentsByRoot(comments) {
    const groups = [];
    const rootGroupMap = new Map();

    comments.forEach((comment) => {
      const commentId = getCommentId(comment);
      const parentId = getParentCommentId(comment);

      if (!parentId) {
        const group = { root: comment, replies: [] };
        groups.push(group);
        rootGroupMap.set(commentId, group);
        return;
      }

      const parentGroup = rootGroupMap.get(parentId);
      if (parentGroup) {
        parentGroup.replies.push(comment);
      } else {
        // 부모가 누락된 예외 케이스는 원댓글처럼 표시한다.
        const fallbackGroup = { root: comment, replies: [] };
        groups.push(fallbackGroup);
        rootGroupMap.set(commentId, fallbackGroup);
      }
    });

    return groups;
  }

  /**
   * 댓글 목록을 DOM에 반영한다.
   * @param {Array} comments 댓글 배열
   * @param {number} currentMemberId 현재 회원 ID
   */
  function renderCommentList(comments, currentMemberId) {
    const list = document.getElementById('commentsList');
    if (!list) return;
    const { commentScope } = readPageContext();
    const groups = groupCommentsByRoot(comments);

    list.innerHTML = groups
      .map((group) => {
        const rootHtml = renderCommentItem(group.root, currentMemberId, commentScope);
        const repliesHtml = group.replies
          .map((reply, index) => {
            const separatorClass = index === 0 ? 'border-t border-gray-200 pt-3 dark:border-strokedark' : 'mt-3 border-t border-gray-200 pt-3 dark:border-strokedark';
            return `<div class="${separatorClass}">${renderCommentItem(reply, currentMemberId, commentScope)}</div>`;
          })
          .join('');

        const replySection = repliesHtml ? `<div class="mt-3">${repliesHtml}</div>` : '';

        return `<li class="rounded-lg border border-gray-100 bg-gray-50/90 px-1 py-1 dark:border-strokedark dark:bg-meta-4/25">${rootHtml}${replySection}</li>`;
      })
      .join('');
  }

  /**
   * 목록을 다시 불러와 화면을 갱신한다.
   */
  async function reloadComments() {
    const { boardId, articleId, currentMemberId } = readPageContext();
    if (!boardId || !articleId) return;

    setCommentsStatus({ loading: true, empty: false, error: '' });
    const result = await requestComments(boardId, articleId);
    setCommentsStatus({ loading: false, empty: false, error: '' });

    if (!result.ok) {
      cachedComments = [];
      renderCommentList([], currentMemberId);
      setCommentsStatus({ loading: false, empty: false, error: result.errorMessage || '오류가 발생했습니다.' });
      return;
    }

    cachedComments = result.comments;
    renderCommentList(cachedComments, currentMemberId);
    const emptyVisible = cachedComments.length === 0;
    setCommentsStatus({ loading: false, empty: emptyVisible, error: '' });
  }

  /**
   * 댓글 등록 요청을 보낸다.
   * @param {number} boardId 게시판 ID
   * @param {number} articleId 게시글 ID
   * @param {string} content 본문
   */
  async function submitNewComment(boardId, articleId, content, parentCommentId = 0) {
    const body = new URLSearchParams();
    body.set('content', content);
    if (Number(parentCommentId) > 0) {
      body.set('parentCommentId', String(parentCommentId));
    }

    const response = await fetch(
      `/api/board/${boardId}/articles/${articleId}/comments/new`,
      {
        method: 'POST',
        headers: buildHeaders(false),
        credentials: 'same-origin',
        body,
      }
    );

    if (response.status === 401 || response.status === 403) {
      throw new Error('댓글 작성 권한이 없습니다.');
    }
    if (!response.ok) {
      throw new Error('댓글 등록에 실패했습니다.');
    }
  }

  /**
   * 댓글 수정 요청을 보낸다.
   * @param {number} boardId 게시판 ID
   * @param {number} articleId 게시글 ID
   * @param {number} commentId 댓글 ID
   * @param {string} content 본문
   */
  async function submitCommentUpdate(boardId, articleId, commentId, content) {
    const body = new URLSearchParams();
    body.set('content', content);

    const response = await fetch(
      `/api/board/${boardId}/articles/${articleId}/comments/${commentId}/edit`,
      {
        method: 'POST',
        headers: buildHeaders(false),
        credentials: 'same-origin',
        body,
      }
    );

    if (response.status === 401 || response.status === 403) {
      throw new Error('댓글 수정 권한이 없습니다.');
    }
    if (!response.ok) {
      throw new Error('댓글 수정에 실패했습니다.');
    }
  }

  /**
   * 댓글 삭제 요청을 보낸다.
   * @param {number} boardId 게시판 ID
   * @param {number} articleId 게시글 ID
   * @param {number} commentId 댓글 ID
   */
  async function submitCommentDelete(boardId, articleId, commentId) {
    const response = await fetch(
      `/api/board/${boardId}/articles/${articleId}/comments/${commentId}/delete`,
      {
        method: 'POST',
        headers: buildHeaders(false),
        credentials: 'same-origin',
      }
    );

    if (response.status === 401 || response.status === 403) {
      throw new Error('댓글 삭제 권한이 없습니다.');
    }
    if (!response.ok) {
      throw new Error('댓글 삭제에 실패했습니다.');
    }
  }

  /**
   * 수정 모드를 연다(텍스트에어리어에 원문 로드).
   * @param {number} commentId 댓글 ID
   */
  function openEditMode(commentId) {
    const item = document.querySelector(`#commentsList [data-comment-id="${commentId}"]`);
    if (!item) return;
    const view = item.querySelector('.comment-view');
    const edit = item.querySelector('.comment-edit');
    const input = item.querySelector('.comment-edit-input');
    if (!view || !edit || !input) return;

    const original = cachedComments.find((c) => getCommentId(c) === commentId);
    input.value = original?.content != null ? String(original.content) : '';

    view.classList.add('hidden');
    edit.classList.remove('hidden');
    input.focus();
  }

  /**
   * 수정 모드를 닫는다.
   * @param {number} commentId 댓글 ID
   */
  function closeEditMode(commentId) {
    const item = document.querySelector(`#commentsList [data-comment-id="${commentId}"]`);
    if (!item) return;
    const view = item.querySelector('.comment-view');
    const edit = item.querySelector('.comment-edit');
    if (!view || !edit) return;
    view.classList.remove('hidden');
    edit.classList.add('hidden');
  }

  /**
   * 현재 열린 대댓글 입력창을 모두 닫는다.
   */
  function closeAllReplyModes() {
    document.querySelectorAll('#commentsList .comment-reply').forEach((replyBox) => {
      replyBox.classList.add('hidden');
      const input = replyBox.querySelector('.comment-reply-input');
      if (input instanceof HTMLTextAreaElement) {
        input.value = '';
      }
    });
  }

  /**
   * 특정 댓글의 대댓글 입력창을 연다.
   * @param {number} commentId 부모 댓글 ID
   */
  function openReplyMode(commentId) {
    const item = document.querySelector(`#commentsList [data-comment-id="${commentId}"]`);
    if (!item) return;
    const replyBox = item.querySelector('.comment-reply');
    const input = item.querySelector('.comment-reply-input');
    if (!replyBox || !(input instanceof HTMLTextAreaElement)) return;

    closeAllReplyModes();
    replyBox.classList.remove('hidden');
    input.focus();
  }

  /**
   * 목록 영역 클릭(수정·삭제·저장·취소)을 처리한다.
   * @param {MouseEvent} event 클릭 이벤트
   */
  async function handleCommentsListClick(event) {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const btn = target.closest('[data-comment-action]');
    if (!btn) return;

    const action = btn.getAttribute('data-comment-action');
    const commentId = Number(btn.dataset.commentId || 0);
    const { boardId, articleId, currentMemberId } = readPageContext();
    if (!boardId || !articleId || !commentId) return;

    if (action === 'edit') {
      openEditMode(commentId);
      return;
    }

    if (action === 'cancel') {
      closeEditMode(commentId);
      return;
    }

    if (action === 'delete') {
      const ok = window.confirm('이 댓글을 삭제할까요?');
      if (!ok) return;
      try {
        await submitCommentDelete(boardId, articleId, commentId);
        await reloadComments();
      } catch (err) {
        alert(err?.message || '요청 처리 중 오류가 발생했습니다.');
      }
      return;
    }

    if (action === 'reply') {
      openReplyMode(commentId);
      return;
    }

    if (action === 'reply-cancel') {
      closeAllReplyModes();
      return;
    }

    if (action === 'reply-save') {
      const item = btn.closest('.comment-item[data-comment-id]');
      const input = item?.querySelector('.comment-reply-input');
      const text = input instanceof HTMLTextAreaElement ? input.value.trim() : '';
      if (!text) {
        alert('답글 내용을 입력해 주세요.');
        return;
      }
      try {
        await submitNewComment(boardId, articleId, text, commentId);
        closeAllReplyModes();
        await reloadComments();
      } catch (err) {
        alert(err?.message || '요청 처리 중 오류가 발생했습니다.');
      }
      return;
    }

    if (action === 'save') {
      const item = btn.closest('.comment-item[data-comment-id]');
      const input = item?.querySelector('.comment-edit-input');
      const text = input instanceof HTMLTextAreaElement ? input.value.trim() : '';
      if (!text) {
        alert('댓글 내용을 입력해 주세요.');
        return;
      }
      try {
        await submitCommentUpdate(boardId, articleId, commentId, text);
        closeEditMode(commentId);
        await reloadComments();
      } catch (err) {
        alert(err?.message || '요청 처리 중 오류가 발생했습니다.');
      }
    }
  }

  /**
   * 등록 버튼 클릭 시 새 댓글을 전송한다.
   */
  async function handleSubmitClick() {
    const { boardId, articleId, currentMemberId, commentScope } = readPageContext();
    if (commentScope === 'NONE' || currentMemberId <= 0) return;

    const textarea = document.getElementById('commentContentInput');
    const submitBtn = document.getElementById('commentSubmitButton');
    if (!textarea || !submitBtn) return;

    const text = textarea.value.trim();
    if (!text) {
      alert('댓글 내용을 입력해 주세요.');
      return;
    }

    try {
      submitBtn.disabled = true;
      await submitNewComment(boardId, articleId, text);
      textarea.value = '';
      closeAllReplyModes();
      await reloadComments();
    } catch (err) {
      alert(err?.message || '요청 처리 중 오류가 발생했습니다.');
    } finally {
      submitBtn.disabled = false;
      applyCommentFormRules(readPageContext());
    }
  }

  /**
   * 댓글 영역 초기화: 폼 규칙 적용, 목록 로드, 이벤트 바인딩.
   */
  function initCommentsSection() {
    const ctx = readPageContext();
    if (!ctx.boardId || !ctx.articleId) return;

    applyCommentFormRules(ctx);

    const list = document.getElementById('commentsList');
    list?.addEventListener('click', handleCommentsListClick);

    document.getElementById('commentSubmitButton')?.addEventListener('click', handleSubmitClick);

    reloadComments().catch((err) => {
      console.error(err);
      setCommentsStatus({
        loading: false,
        empty: false,
        error: '댓글을 불러오지 못했습니다.',
      });
    });
  }

  document.addEventListener('DOMContentLoaded', initCommentsSection);
})();
