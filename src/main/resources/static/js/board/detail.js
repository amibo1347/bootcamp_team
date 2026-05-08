(() => {
  const PAGE_SIZE = 100;

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
   * 게시글 목록 API 응답 형식을 배열로 정규화한다.
   * @param {unknown} payload API 응답 원본
   * @returns {Array}
   */
  function normalizePosts(payload) {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.content)) return payload.content;
    if (Array.isArray(payload?.items)) return payload.items;
    return [];
  }

  /**
   * 게시글 목록을 조회한다.
   * @param {number} boardId 게시판 ID
   * @returns {Promise<Array>}
   */
  async function fetchPosts(boardId) {
    const response = await fetch(`api/board/${boardId}/articles?page=0&size=${PAGE_SIZE}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
      credentials: 'same-origin',
    });

    if (!response.ok) {
      throw new Error('게시글 조회에 실패했습니다.');
    }

    const payload = await response.json();
    return normalizePosts(payload);
  }

  /**
   * 게시글 상세 영역을 렌더링한다.
   * @param {Object|null} post 게시글 정보
   */
  function renderPost(post) {
    const titleElement = document.getElementById('postTitle');
    const authorElement = document.getElementById('postAuthor');
    const dateElement = document.getElementById('postDate');
    const viewsElement = document.getElementById('postViews');
    const contentElement = document.getElementById('postContent');

    if (!titleElement || !authorElement || !dateElement || !viewsElement || !contentElement) return;

    if (!post) {
      titleElement.textContent = '게시글을 찾을 수 없습니다.';
      authorElement.textContent = '-';
      dateElement.textContent = '-';
      viewsElement.textContent = '0';
      contentElement.textContent = '요청한 게시글이 없거나 열람 권한이 없습니다.';
      return;
    }

    titleElement.textContent = post.title || '(제목 없음)';
    authorElement.textContent = post.authorName || '-';
    dateElement.textContent = formatDate(post.createdAt);
    viewsElement.textContent = String(Number(post.viewCount || 0));
    contentElement.textContent = post.content || '';
  }

  /**
   * 상세 페이지 초기 로딩 로직을 실행한다.
   */
  async function init() {
    const boardId = Number(document.body.dataset.boardId || 0);
    const articleId = Number(document.body.dataset.articleId || 0);
    if (!boardId || !articleId) return;

    try {
      const posts = await fetchPosts(boardId);
      const post = posts.find((item) => Number(item.articleId) === articleId) || null;
      renderPost(post);
    } catch (error) {
      console.error(error);
      renderPost(null);
    }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
