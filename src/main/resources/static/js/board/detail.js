(() => {

   async function fetchPost(boardId, articleId) {
      const response = await fetch(`/api/board/${boardId}/articles/${articleId}`, {
          method: 'GET',
          headers: { Accept: 'application/json' },
          credentials: 'same-origin',
      });
      if (!response.ok) {
          throw new Error('게시글 조회에 실패했습니다.');
      }
      return response.json();
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
    contentElement.innerHTML = '';                       // 플레이스홀더 텍스트 제거
      if (window.toastui?.Editor?.factory) {
          window.toastui.Editor.factory({
              el: contentElement,
              viewer: true,
              initialValue: post.content || '',
          });
      } else {
          // 라이브러리 로딩 실패 폴백
          contentElement.textContent = post.content || '';
      }
       upgradeYouTubeLinks(contentElement);
  }

  function getYouTubeId(url) {
      try {
          const m = String(url).match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([\w-]{11})/);
          return m ? m[1] : null;
      } catch { return null; }
  }

  function upgradeYouTubeLinks(root) {
      root.querySelectorAll('a').forEach((a) => {
          const id = getYouTubeId(a.getAttribute('href'));
          if (!id) return;

          const wrapper = document.createElement('div');
          wrapper.className = 'my-4 w-full max-w-2xl';
          wrapper.style.aspectRatio = '16 / 9';
          wrapper.innerHTML = `
              <iframe
                  src="https://www.youtube.com/embed/${id}"
                  class="h-full w-full rounded-lg border border-gray-200 dark:border-strokedark"
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  allowfullscreen
                  referrerpolicy="strict-origin-when-cross-origin"
                  loading="lazy">
              </iframe>
          `;
          a.replaceWith(wrapper);
      });
  }
  
  /**
   * 상세 페이지 초기 로딩 로직을 실행한다.
   */
  async function init() {
      const boardId = Number(document.body.dataset.boardId || 0);
      const articleId = Number(document.body.dataset.articleId || 0);
      if (!boardId || !articleId) return;
      try {
          const post = await fetchPost(boardId, articleId);
          renderPost(post);
      } catch (error) {
          console.error(error);
          renderPost(null);
      }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
