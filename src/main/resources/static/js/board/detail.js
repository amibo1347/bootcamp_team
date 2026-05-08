(() => {
  let contentViewer = null;

  /**
   * 상세 화면의 첨부파일 영역을 렌더링한다.
   * - 백엔드 응답 형태가 확정되지 않았을 수 있어, 아래 형태를 모두 지원한다.
   *   1) post.attachmentUrl: string (단일 첨부파일 URL)
   *   2) post.attachments: [{ name, url, size }] (다중 첨부파일 배열)
   * @param {Object|null} post 게시글 정보
   */
  function renderAttachments(post) {
    const sectionElement = document.getElementById('attachmentSection');
    const listElement = document.getElementById('attachmentList');
    if (!sectionElement || !listElement) return;

    // ----- 첨부파일 데이터 정규화 시작 -----
    const normalized = [];

    // (1) 단일 attachmentUrl 지원
    if (post?.attachmentUrl && typeof post.attachmentUrl === 'string') {
      normalized.push({
        name: post.attachmentName || '첨부파일',
        url: post.attachmentUrl,
        size: post.attachmentSize || null,
      });
    }

    // (2) 다중 attachments[] 지원
    if (Array.isArray(post?.attachments)) {
      post.attachments.forEach((item, index) => {
        if (!item?.url) return;
        normalized.push({
          name: item.name || `첨부파일 ${index + 1}`,
          url: item.url,
          size: item.size ?? null,
        });
      });
    }
    // ----- 첨부파일 데이터 정규화 끝 -----

    if (!normalized.length) {
      sectionElement.classList.add('hidden');
      listElement.innerHTML = '';
      return;
    }

    sectionElement.classList.remove('hidden');
    listElement.innerHTML = normalized
      .map((file) => {
        const sizeText = Number.isFinite(Number(file.size))
          ? ` <span class="text-xs text-gray-500 dark:text-gray-400">(${Math.ceil(Number(file.size) / 1024)} KB)</span>`
          : '';

        // 다운로드 UX: 새 탭/다운로드 모두 가능하도록 기본 링크 제공
        return `
          <li class="flex items-center justify-between gap-3">
            <a href="${file.url}" class="truncate text-indigo-600 hover:underline dark:text-indigo-300" target="_blank" rel="noopener noreferrer">
              ${file.name}
            </a>
            <span class="shrink-0">${sizeText}</span>
          </li>
        `;
      })
      .join('');
  }

  /**
   * 게시글 상세 API를 호출해 단건 게시글 데이터를 가져온다.
   * @param {number} boardId 게시판 ID
   * @param {number} articleId 게시글 ID
   * @returns {Promise<Object>}
   */
  async function fetchPost(boardId, articleId) {
    const response = await fetch(`/api/board/${boardId}/articles/${articleId}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    });
    if (!response.ok) {
      if (response.status === 404) {
        throw new Error('게시글을 찾을 수 없습니다.');
      }
      throw new Error('게시글 상세 조회에 실패했습니다.');
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
      renderAttachments(null);
      return;
    }

    titleElement.textContent = post.title || '(제목 없음)';
    authorElement.textContent = post.authorName || '-';
    dateElement.textContent = formatDate(post.createdAt);
    viewsElement.textContent = String(Number(post.viewCount || 0));
<<<<<<< HEAD
    contentElement.innerHTML = ''; // 플레이스홀더 텍스트 제거
    contentViewer = null;
    renderAttachments(post);

    if (window.toastui?.Editor?.factory) {
      // Markdown 본문을 Viewer로 렌더링해 이미지/링크를 정상 표시한다.
      contentViewer = window.toastui.Editor.factory({
        el: contentElement,
        viewer: true,
        initialValue: post.content || '',
      });
      return;
    }

    // 라이브러리 로딩 실패 폴백
    contentElement.textContent = post.content || '';
=======
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
>>>>>>> 9f5cc350b995142c2d698a099d21bc9d00a39a14
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
