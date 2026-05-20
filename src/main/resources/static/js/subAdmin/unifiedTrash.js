(() => {
  const PAGE_SIZE = 10;
  /** 복구/삭제 후 동일 페이지 재조회용 */
  let unifiedTrashCurrentPage = 0;

  /**
   * CSRF 메타 또는 빈 문자열.
   */
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

  /**
   * POST용 헤더
   */
  function getPostHeaders() {
    return {
      [csrfHeader]: csrfToken,
      Accept: 'application/json, text/plain, */*',
    };
  }

  /** @param {string|number|Date} value */
  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const h = String(date.getHours()).padStart(2, '0');
    const min = String(date.getMinutes()).padStart(2, '0');
    return `${y}-${m}-${d} ${h}:${min}`;
  }

  /** @param {string} value */
  function escapeHtml(value) {
    return String(value || '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  /**
   * @param {unknown} payload
   * @returns {{ items: Array<object>, currentPage: number, totalPages: number }}
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

  /** @param {number} page 0-base */
  async function fetchUnifiedTrash(page) {
    const response = await fetch(
      `/api/subAdmin/articles/trash?page=${page}&size=${PAGE_SIZE}`,
      { method: 'GET', headers: { Accept: 'application/json' }, credentials: 'same-origin' }
    );
    if (!response.ok) throw new Error(await window.getApiErrorMessage(response, '통합 휴지통 목록을 불러오지 못했습니다.'));
    return normalizePage(await response.json());
  }

  /** 게시판별 휴지통 기존 API 재사용 — 복구 */
  async function postRestore(boardId, articleId) {
    const res = await fetch(`/api/board/${boardId}/articles/trash/${articleId}/restore`, {
      method: 'POST',
      headers: getPostHeaders(),
      credentials: 'same-origin',
    });
    if (!res.ok) throw new Error(await window.getApiErrorMessage(res, '복구에 실패했습니다.'));
  }

  /** 영구 삭제 */
  async function postPermanentDelete(boardId, articleId) {
    const res = await fetch(`/api/board/${boardId}/articles/trash/${articleId}/delete`, {
      method: 'POST',
      headers: getPostHeaders(),
      credentials: 'same-origin',
    });
    if (!res.ok) throw new Error(await window.getApiErrorMessage(res, '영구 삭제에 실패했습니다.'));
  }

  function showMsg(message) {
    const el = document.getElementById('unifiedTrashMessage');
    if (!el) return;
    el.textContent = message;
    el.classList.remove('hidden');
  }

  function hideMsg() {
    const el = document.getElementById('unifiedTrashMessage');
    if (!el) return;
    el.textContent = '';
    el.classList.add('hidden');
  }

  /** @param {Array<object>} articles @param {number} currentPage */
  function renderRows(articles, currentPage) {
    const body = document.getElementById('unifiedTrashBody');
    const empty = document.getElementById('unifiedTrashEmpty');
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
        const boardId = Number(post.boardId || 0);
        const rowNo = baseNo + index + 1;
        const boardLabel = escapeHtml(post.boardName || (boardId ? `게시판 #${boardId}` : '') || '알 수 없음');
        const title = escapeHtml(post.title || '(제목 없음)');
        const author = escapeHtml(post.authorName || '-');
        const created = formatDate(post.createdAt);
        return `
          <tr class="border-t border-gray-100 text-gray-700 dark:border-strokedark dark:text-gray-200 dark:hover:bg-meta-4/40">
            <td class="whitespace-nowrap px-5 py-3">${rowNo}</td>
            <td class="px-5 py-3">
              <span class="line-clamp-2" title="${boardLabel}">${boardLabel}</span>
            </td>
            <td class="px-5 py-3">
              <span class="line-clamp-2" title="${title}">${title}</span>
            </td>
            <td class="whitespace-nowrap px-5 py-3">${author}</td>
            <td class="whitespace-nowrap px-5 py-3">${created}</td>
            <td class="whitespace-nowrap px-5 py-3">
              <div class="flex flex-wrap gap-2">
                <button type="button" data-action="restore" data-board-id="${boardId}" data-article-id="${articleId}"
                  class="rounded-xl bg-indigo-200 px-3 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-300 dark:bg-indigo-600/35 dark:text-indigo-100 dark:hover:bg-indigo-600/50">
                  복구
                </button>
                <button type="button" data-action="permanent" data-board-id="${boardId}" data-article-id="${articleId}"
                  class="btn-delete-hover rounded-xl bg-rose-200 px-3 py-1.5 text-xs font-medium text-rose-500 transition dark:bg-rose-600/35 dark:text-rose-100">
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
   * @param {number} currentPage @param {number} totalPages
   * @param {(p: number)=>void} onMove
   */
  function renderPagination(currentPage, totalPages, onMove) {
    const pagination = document.getElementById('unifiedTrashPagination');
    if (!pagination) return;
    if (totalPages <= 1) {
      pagination.innerHTML = '';
      return;
    }
    const createPageButton = (label, targetPage, disabled = false, active = false) => `
      <button type="button" data-page="${targetPage}"
        ${disabled ? 'disabled' : ''}
        class="rounded-md border px-3 py-1.5 text-sm transition
          ${
            active
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

  /** @param {number} page */
  async function loadUnifiedTrash(page) {
    hideMsg();
    const requestPage = Math.max(0, page);
    let { items, currentPage, totalPages } = await fetchUnifiedTrash(requestPage);

    if (items.length === 0 && totalPages > 0) {
      const maxIndex = totalPages - 1;
      if (requestPage > maxIndex) return loadUnifiedTrash(maxIndex);
      if (requestPage > 0) return loadUnifiedTrash(requestPage - 1);
    }

    unifiedTrashCurrentPage = currentPage;
    renderRows(items, currentPage);
    renderPagination(currentPage, totalPages, (next) => loadUnifiedTrash(next));
  }

  function attachRowDelegation() {
    const body = document.getElementById('unifiedTrashBody');
    if (!body) return;

    body.addEventListener('click', async (event) => {
      const restoreBtn = event.target.closest('button[data-action="restore"]');
      const permanentBtn = event.target.closest('button[data-action="permanent"]');
      const target = restoreBtn || permanentBtn;
      if (!target) return;

      const articleId = Number(target.dataset.articleId || 0);
      const boardId = Number(target.dataset.boardId || 0);
      if (!articleId || !boardId) return;

      if (restoreBtn) {
        if (!window.confirm('이 게시글을 복구하시겠습니까?')) return;
        restoreBtn.disabled = true;
        try {
          await postRestore(boardId, articleId);
          await loadUnifiedTrash(unifiedTrashCurrentPage);
        } catch (e) {
          window.alert(e?.message || '복구에 실패했습니다.');
        } finally {
          restoreBtn.disabled = false;
        }
        return;
      }

      if (!window.confirm('이 글을 영구 삭제하시겠습니까? 되돌릴 수 없습니다.')) return;
      if (!window.confirm('정말 영구 삭제합니다. 첨부를 포함해 삭제되며 되돌릴 수 없습니다.')) return;
      permanentBtn.disabled = true;
      try {
        await postPermanentDelete(boardId, articleId);
        await loadUnifiedTrash(unifiedTrashCurrentPage);
      } catch (e) {
        window.alert(e?.message || '영구 삭제에 실패했습니다.');
      } finally {
        permanentBtn.disabled = false;
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    if (!document.getElementById('unifiedTrashBody')) return;

    attachRowDelegation();
    loadUnifiedTrash(0).catch(async (error) => {
      console.error(error);
      showMsg(error?.message || '통합 휴지통 목록을 불러오지 못했습니다.');
      renderRows([], 0);
      renderPagination(0, 1, () => {});
    });
  });
})();
