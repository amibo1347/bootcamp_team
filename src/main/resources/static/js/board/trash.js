(() => {
  const PAGE_SIZE = 10;
  /** 마지막으로 로드된 휴지통 페이지(0-base), 복구/삭제 후 동일 페이지 재조회에 사용 */
  let trashCurrentPage = 0;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

  /**
   * POST 요청에 사용할 CSRF 헤더를 포함한 객체를 반환한다.
   * @returns {Record<string, string>}
   */
  function getPostHeaders() {
    return {
      [csrfHeader]: csrfToken,
      Accept: 'application/json, text/plain, */*',
    };
  }

  /**
   * 날짜 값을 YYYY-MM-DD HH:mm 형식으로 변환한다.
   * @param {string|number|Date} value 원본
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
   * HTML 이스케이프(표시용 텍스트)
   * @param {string} value
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
   * Spring Data Page JSON을 프론트에서 쓰기 좋은 형태로 정규화한다.
   * @param {unknown} payload
   * @returns {{ items: Array, currentPage: number, totalPages: number }}
   */
  function normalizePage(payload) {
    const items = Array.isArray(payload?.content)
      ? payload.content
      : Array.isArray(payload?.items)
        ? payload.items
        : [];
    const currentPage = Number.isFinite(payload?.number) ? Number(payload.number) : 0;
    const totalPages = Number.isFinite(payload?.totalPages)
      ? Number(payload.totalPages)
      : Math.max(items.length ? 1 : 0, Math.ceil(items.length / PAGE_SIZE));

    return {
      items,
      currentPage: currentPage >= 0 ? currentPage : 0,
      totalPages: totalPages >= 0 ? totalPages : 0,
    };
  }

  /**
   * 휴지통 목록 API를 호출한다.
   * @param {number} boardId
   * @param {number} page 0-base
   */
  async function fetchTrashArticles(boardId, page) {
    const response = await fetch(
      `/api/board/${boardId}/articles/trash?page=${page}&size=${PAGE_SIZE}`,
      {
        method: 'GET',
        headers: { Accept: 'application/json' },
        credentials: 'same-origin',
      }
    );
    if (!response.ok) {
      const msg = await window.getApiErrorMessage(response, '휴지통 목록을 불러오지 못했습니다.');
      throw new Error(msg);
    }
    const payload = await response.json();
    return normalizePage(payload);
  }

  /**
   * 복구 API
   * @param {number} boardId
   * @param {number} articleId
   */
  async function postRestore(boardId, articleId) {
    const response = await fetch(`/api/board/${boardId}/articles/trash/${articleId}/restore`, {
      method: 'POST',
      headers: getPostHeaders(),
      credentials: 'same-origin',
    });
    if (!response.ok) {
      throw new Error(await window.getApiErrorMessage(response, '복구에 실패했습니다.'));
    }
  }

  /**
   * 영구 삭제 API
   * @param {number} boardId
   * @param {number} articleId
   */
  async function postPermanentDelete(boardId, articleId) {
    const response = await fetch(`/api/board/${boardId}/articles/trash/${articleId}/delete`, {
      method: 'POST',
      headers: getPostHeaders(),
      credentials: 'same-origin',
    });
    if (!response.ok) {
      throw new Error(await window.getApiErrorMessage(response, '영구 삭제에 실패했습니다.'));
    }
  }

  /**
   * 상단 에러 영역 표시
   * @param {string} message
   */
  function showPageMessage(message) {
    const el = document.getElementById('trashPageMessage');
    if (!el) return;
    el.textContent = message;
    el.classList.remove('hidden');
  }

  /**
   * 상단 에러 영역 숨김
   */
  function hidePageMessage() {
    const el = document.getElementById('trashPageMessage');
    if (!el) return;
    el.textContent = '';
    el.classList.add('hidden');
  }

  /**
   * 테이블 본문 렌더링
   * @param {Array<object>} articles
   * @param {number} boardId
   * @param {number} currentPage
   */
  function renderRows(articles, boardId, currentPage) {
    const body = document.getElementById('trashListBody');
    const empty = document.getElementById('trashListEmpty');
    if (!body || !empty) return;

    if (!articles.length) {
      empty.classList.remove('hidden');
      body.innerHTML = '';
      return;
    }
    empty.classList.add('hidden');

    const baseNo = currentPage * PAGE_SIZE;
    body.innerHTML = articles
      .map((post, index) => {
        const articleId = Number(post.articleId || 0);
        const rowNo = baseNo + index + 1;
        const title = escapeHtml(post.title || '(제목 없음)');
        const author = escapeHtml(post.authorName || '-');

        return `
          <tr class="border-t border-gray-100 text-gray-700 dark:border-strokedark dark:text-gray-200 dark:hover:bg-meta-4/40">
            <td class="whitespace-nowrap px-5 py-3">${rowNo}</td>
            <td class="px-5 py-3">
              <span class="line-clamp-2" title="${title}">${title}</span>
            </td>
            <td class="whitespace-nowrap px-5 py-3">${author}</td>
            <td class="whitespace-nowrap px-5 py-3">${formatDate(post.createdAt)}</td>
            <td class="whitespace-nowrap px-5 py-3">
              <div class="flex flex-wrap gap-2">
                <!-- 복구: DESIGN_RULES 4-2 Secondary -->
                <button type="button" data-action="restore" data-article-id="${articleId}"
                  class="rounded-lg bg-indigo-200 px-3 py-1.5 text-xs font-medium text-indigo-700 transition hover:bg-indigo-300">
                  복구
                </button>
                <button type="button" data-action="permanent" data-article-id="${articleId}"
                  class="btn-delete-hover rounded-lg bg-rose-200 px-3 py-1.5 text-xs font-medium text-rose-500 transition dark:bg-rose-600/35 dark:text-rose-100">
                  영구 삭제
                </button>
              </div>
            </td>
          </tr>
        `;
      })
      .join('');
  }

  /**
   * 페이지네이션 UI (board/view.js와 동일 패턴)
   * @param {number} currentPage
   * @param {number} totalPages
   * @param {(p:number)=>void} onMove
   */
  function renderPagination(currentPage, totalPages, onMove) {
    const pagination = document.getElementById('trashPagination');
    if (!pagination) return;
    if (totalPages <= 1) {
      pagination.innerHTML = '';
      return;
    }

    const createPageButton = (label, targetPage, disabled = false, active = false) => `
      <button
        type="button"
        data-page="${targetPage}"
        ${disabled ? 'disabled' : ''}
        class="rounded-md border px-3 py-1.5 text-sm transition
          ${active
        ? 'border-indigo-300 bg-indigo-200 font-semibold text-indigo-700 dark:border-indigo-500/60 dark:bg-indigo-500/20 dark:text-indigo-200'
        : 'border-gray-300 bg-white text-gray-700 hover:border-indigo-300 hover:text-indigo-600 disabled:cursor-not-allowed disabled:opacity-50 dark:border-strokedark dark:bg-boxdark dark:text-gray-200 dark:hover:border-indigo-500/60 dark:hover:text-indigo-300'
      }">
        ${label}
      </button>
    `;

    let buttons = createPageButton('이전', currentPage - 1, currentPage <= 0);
    for (let page = 0; page < totalPages; page += 1) {
      buttons += createPageButton(String(page + 1), page, false, page === currentPage);
    }
    buttons += createPageButton('다음', currentPage + 1, currentPage >= totalPages - 1);

    pagination.innerHTML = `<div class="flex flex-wrap items-center justify-center gap-2">${buttons}</div>`;
    pagination.querySelectorAll('button[data-page]').forEach((button) => {
      if (button.disabled) return;
      button.addEventListener('click', () => {
        const targetPage = Number(button.dataset.page);
        if (!Number.isFinite(targetPage) || targetPage < 0 || targetPage >= totalPages) return;
        onMove(targetPage);
      });
    });
  }

  /**
   * 목록 + 페이지네이션 갱신
   * @param {number} boardId
   * @param {number} page
   */
  async function loadTrash(boardId, page) {
    hidePageMessage();
    const requestPage = Math.max(0, page);
    let { items, currentPage, totalPages } = await fetchTrashArticles(boardId, requestPage);

    // 마지막 줄만 남은 페이지에서 영구 삭제 등으로 해당 페이지가 비었을 때 이전 페이지로 맞춤
    if (items.length === 0 && totalPages > 0) {
      const maxIndex = totalPages - 1;
      if (requestPage > maxIndex) {
        return loadTrash(boardId, maxIndex);
      }
      if (requestPage > 0) {
        return loadTrash(boardId, requestPage - 1);
      }
    }

    trashCurrentPage = currentPage;
    renderRows(items, boardId, currentPage);
    renderPagination(currentPage, totalPages, (next) => loadTrash(boardId, next));
  }

  /**
   * 행의 복구/영구삭제 클릭 처리 (이벤트 위임)
   * @param {number} boardId
   */
  function attachRowActions(boardId) {
    const body = document.getElementById('trashListBody');
    if (!body) return;

    body.addEventListener('click', async (event) => {
      const restoreBtn = event.target.closest('button[data-action="restore"]');
      const permanentBtn = event.target.closest('button[data-action="permanent"]');
      const target = restoreBtn || permanentBtn;
      if (!target) return;

      const articleId = Number(target.dataset.articleId || 0);
      if (!articleId) return;

      if (restoreBtn) {
        const ok = window.confirm('이 게시글을 복구하시겠습니까?');
        if (!ok) return;
        restoreBtn.disabled = true;
        try {
          await postRestore(boardId, articleId);
          await loadTrash(boardId, trashCurrentPage);
        } catch (e) {
          window.alert(e?.message || '복구에 실패했습니다.');
        } finally {
          restoreBtn.disabled = false;
        }
        return;
      }

      if (permanentBtn) {
        const ok1 = window.confirm(
          '이 글을 영구 삭제하시겠습니까? 휴지통에서 제거되며 일반 사용자는 복구할 수 없습니다.'
        );
        if (!ok1) return;
        const ok2 = window.confirm(
          '정말 영구 삭제합니다. 첨부 파일을 포함한 데이터가 삭제되며 되돌릴 수 없습니다.'
        );
        if (!ok2) return;
        permanentBtn.disabled = true;
        try {
          await postPermanentDelete(boardId, articleId);
          await loadTrash(boardId, trashCurrentPage);
        } catch (e) {
          window.alert(e?.message || '영구 삭제에 실패했습니다.');
        } finally {
          permanentBtn.disabled = false;
        }
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const boardId = Number(document.body.dataset.boardId || 0);
    if (!boardId) return;

    attachRowActions(boardId);

    loadTrash(boardId, 0).catch(async (error) => {
      console.error(error);
      showPageMessage(error?.message || '휴지통 목록을 불러오지 못했습니다.');
      renderRows([], boardId, 0);
      renderPagination(0, 1, () => { });
    });
  });
})();
