(() => {
  const PAGE_SIZE = 10;

  /**
   * 날짜 문자열을 YYYY-MM-DD HH:mm 형식으로 반환한다.
   * @param {string|number|Date} value 날짜 원본 값
   * @returns {string}
   */
  function formatDate(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}`;
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
   * 게시글 목록 API 응답을 프론트 공통 구조로 정규화한다.
   * @param {unknown} payload API 응답 원본
   * @returns {{ posts: Array, currentPage: number, totalPages: number }}
   */
  function normalizeResponse(payload) {
    if (Array.isArray(payload)) {
      return {
        posts: payload,
        currentPage: 0,
        totalPages: 1,
      };
    }

    const posts = Array.isArray(payload?.content)
      ? payload.content
      : Array.isArray(payload?.items)
      ? payload.items
      : [];
    const currentPage = Number.isFinite(payload?.number) ? Number(payload.number) : Number(payload?.page || 0);
    const totalPages = Number.isFinite(payload?.totalPages)
      ? Number(payload.totalPages)
      : Number.isFinite(payload?.pages)
      ? Number(payload.pages)
      : Math.max(1, Math.ceil(posts.length / PAGE_SIZE));

    return {
      posts,
      currentPage: currentPage >= 0 ? currentPage : 0,
      totalPages: totalPages > 0 ? totalPages : 1,
    };
  }

  /**
   * 게시판 ID와 페이지 번호를 기준으로 게시글 목록을 조회한다.
   * @param {number} boardId 게시판 ID
   * @param {number} page 페이지 번호(0-base)
   * @returns {Promise<{ posts: Array, currentPage: number, totalPages: number }>}
   */
  async function fetchPosts(boardId, page) {
    const response = await fetch(`/api/board/${boardId}/articles?page=${page}&size=${PAGE_SIZE}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
      credentials: 'same-origin',
    });

    if (!response.ok) {
      throw new Error('게시글 목록을 불러오지 못했습니다.');
    }

    const payload = await response.json();
    return normalizeResponse(payload);
  }

  /**
   * 게시글 1건의 첨부파일 목록을 조회한다.
   * @param {number} articleId 게시글 ID
   * @returns {Promise<Array>}
   */
  async function fetchArticleAttachments(articleId) {
    const response = await fetch(`/api/article-attachment?articleId=${articleId}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    });
    if (!response.ok) return [];
    const payload = await response.json();
    return Array.isArray(payload) ? payload : [];
  }

  /**
   * 게시글 데이터에 첨부파일 존재 여부를 추가한다.
   * - 응답에 첨부 여부 필드가 이미 있으면 그대로 사용
   * - 없으면 글별 첨부파일 목록 API를 조회해 hasAttachment를 계산
   * @param {Array} posts 게시글 배열
   * @returns {Promise<Array>}
   */
  async function enrichPostsWithAttachmentFlag(posts) {
    const enrichedPosts = await Promise.all(
      posts.map(async (post) => {
        const hasKnownAttachmentFlag =
          Boolean(post?.hasAttachment) ||
          (Array.isArray(post?.attachmentIds) && post.attachmentIds.length > 0) ||
          Number(post?.attachmentCount || 0) > 0 ||
          Boolean(post?.attachmentUrl);

        if (hasKnownAttachmentFlag) {
          return { ...post, hasAttachment: true };
        }

        const articleId = Number(post?.articleId || 0);
        if (!articleId) return { ...post, hasAttachment: false };

        const attachments = await fetchArticleAttachments(articleId);
        return { ...post, hasAttachment: attachments.length > 0 };
      })
    );

    return enrichedPosts;
  }

  /**
   * 목록형 뷰를 렌더링한다.
   * @param {Array} posts 게시글 배열
   * @param {number} boardId 게시판 ID
   */
  function renderList(posts, boardId) {
    const body = document.getElementById('postListBody');
    const empty = document.getElementById('postListEmpty');
    if (!body || !empty) return;
    if (!posts.length) {
      empty.classList.remove('hidden');
      body.innerHTML = '';
      return;
    }
    empty.classList.add('hidden');
    body.innerHTML = posts
      .map(
        (post, index) => {
          const detailUrl = `/board/${boardId}/articles/${post.articleId}`;
          const attachmentIcon = post?.hasAttachment
            ? `
              <span class="inline-flex items-center text-gray-500 dark:text-gray-300" title="첨부파일 있음" aria-label="첨부파일 있음">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path d="M8.5 3a3.5 3.5 0 0 0-3.5 3.5v7a5 5 0 0 0 10 0V7a2.5 2.5 0 0 0-5 0v6.5a1 1 0 1 0 2 0V8a.75.75 0 0 1 1.5 0v5.5a2.5 2.5 0 0 1-5 0V7a4 4 0 0 1 8 0v6.5a6.5 6.5 0 0 1-13 0v-7a5 5 0 0 1 10 0v7a3.5 3.5 0 1 1-7 0V7a2 2 0 1 1 4 0v6.5a.5.5 0 0 1-1 0V8a.75.75 0 0 0-1.5 0v5.5a2 2 0 1 0 4 0V7a3.5 3.5 0 0 0-3.5-3.5Z" />
                </svg>
              </span>
            `
            : '';
         return `
          <tr class="border-t border-gray-100 text-gray-700 dark:border-strokedark dark:text-gray-200">
            <td class="whitespace-nowrap px-5 py-3">${index + 1}</td>
            <td class="px-5 py-3">
              <a href="${detailUrl}" class="flex items-center gap-1 truncate hover:text-indigo-500">
                <span class="truncate">${escapeHtml(post.title)}</span>
                ${attachmentIcon}
              </a>
            </td>
            <td class="whitespace-nowrap px-5 py-3">${escapeHtml(post.authorName || '-')}</td>
            <td class="whitespace-nowrap px-5 py-3">${formatDate(post.createdAt)}</td>
            <td class="whitespace-nowrap px-5 py-3">${Number(post.viewCount || 0)}</td>
          </tr>
        `;
  })
      .join('');
  }

  /**
   * 앨범형 뷰를 렌더링한다.
   * @param {Array} posts 게시글 배열
   * @param {number} boardId 게시판 ID
   */
  function renderAlbum(posts, boardId) {
    const grid = document.getElementById('postAlbumGrid');
    const empty = document.getElementById('postAlbumEmpty');
    if (!grid || !empty) return;
    if (!posts.length) {
      empty.classList.remove('hidden');
      grid.innerHTML = '';
      return;
    }
    empty.classList.add('hidden');
    grid.innerHTML = posts
      .map(
        (post) => {
          const detailUrl = `/board/${boardId}/articles/${post.articleId}`;
        return  `
          <article class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-strokedark dark:bg-boxdark">
            <div class="flex h-48 items-center justify-center bg-gray-100 text-sm text-gray-400 dark:bg-meta-4/60">미리보기 없음</div>
            <div class="space-y-2 p-4">
              <h3 class="line-clamp-1 text-base font-semibold text-gray-900 dark:text-white">${escapeHtml(post.title)}</h3>
              <p class="line-clamp-2 text-sm text-gray-600 dark:text-gray-300">${escapeHtml(post.content || '')}</p>
              <div class="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                <span>${escapeHtml(post.authorName || '-')}</span>
                <span>${formatDate(post.createdAt)}</span>
              </div>
            </div>
          </article>
        `;
  })
      .join('');
  }

  /**
   * 카드형 뷰를 렌더링한다.
   * @param {Array} posts 게시글 배열
   * @param {number} boardId 게시판 ID
   */
  function renderCard(posts, boarId) {
    const grid = document.getElementById('postCardGrid');
    const empty = document.getElementById('postCardEmpty');
    if (!grid || !empty) return;
    if (!posts.length) {
      empty.classList.remove('hidden');
      grid.innerHTML = '';
      return;
    }
    empty.classList.add('hidden');
    grid.innerHTML = posts
      .map(
        (post) => {
          const detailUrl = `/board/${boardId}/articles/${post.articleId}`;
         return `
          <article class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-strokedark dark:bg-boxdark">
            <div class="mb-3 flex items-start justify-between gap-3">
              <h3 class="line-clamp-2 text-lg font-semibold text-gray-900 dark:text-white">${escapeHtml(post.title)}</h3>
              <span class="shrink-0 rounded-full bg-indigo-100 px-2.5 py-1 text-xs font-medium text-indigo-700">${Number(
                post.viewCount || 0
              )} views</span>
            </div>
            <p class="line-clamp-3 text-sm leading-6 text-gray-600 dark:text-gray-300">${escapeHtml(post.content || '')}</p>
            <div class="mt-5 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
              <span>${escapeHtml(post.authorName || '-')}</span>
              <span>${formatDate(post.createdAt)}</span>
            </div>
          </article>
        `;
  })
      .join('');
  }

  /**
   * 공통 페이지네이션 UI를 렌더링한다.
   * @param {number} currentPage 현재 페이지(0-base)
   * @param {number} totalPages 전체 페이지 수
   * @param {(page:number)=>void} onMove 페이지 이동 콜백
   */
  function renderPagination(currentPage, totalPages, onMove) {
    const pagination = document.getElementById('postPagination');
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

  /**
   * 현재 페이지에 맞는 게시글 목록과 페이지네이션을 갱신한다.
   * @param {number} boardId 게시판 ID
   * @param {number} page 페이지 번호(0-base)
   */
  async function updateBoardPosts(boardId, page) {
    const { posts, currentPage, totalPages } = await fetchPosts(boardId, page);
    const postsWithAttachment = await enrichPostsWithAttachmentFlag(posts);
    renderList(postsWithAttachment, boardId);
    renderAlbum(postsWithAttachment, boardId);
    renderCard(postsWithAttachment, boardId);
    renderPagination(currentPage, totalPages, (nextPage) => {
      updateBoardPosts(boardId, nextPage);
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const boardId = Number(document.body.dataset.boardId || 0);
    if (!boardId) return;
    updateBoardPosts(boardId, 0).catch((error) => {
      console.error(error);
      renderList([], boardId);
      renderAlbum([], boardId);
      renderCard([], boardId);
      renderPagination(0, 1, () => {});
    });
  });
})();
