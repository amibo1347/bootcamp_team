(() => {
  let contentViewer = null;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  let currentPost = null;

  /**
   * 공통 요청 헤더를 반환한다.
   * @returns {Record<string, string>}
   */
  function getHeaders() {
    return {
      [csrfHeader]: csrfToken,
    };
  }

  /**
   * 글에 연결된 첨부파일 목록 API를 호출한다.
   * @param {number} articleId 게시글 ID
   * @returns {Promise<Array<{id:number, filename:string, size:number|null, downloadUrl:string}>>}
   */
  async function fetchAttachments(articleId) {
    const response = await fetch(`/api/article-attachment?articleId=${articleId}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    });

    if (!response.ok) return [];
    const data = await response.json();
    return Array.isArray(data) ? data : [];
  }

  /**
   * 상세 화면의 첨부파일 영역을 렌더링한다.
   * - 응답 형태: [{ id, filename, size, downloadUrl }]
   * @param {Array<{id:number, filename:string, size:number|null, downloadUrl:string}>} attachments 첨부파일 목록
   */
  function renderAttachments(attachments) {
    const sectionElement = document.getElementById('attachmentSection');
    const listElement = document.getElementById('attachmentList');
    if (!sectionElement || !listElement) return;

    if (!attachments?.length) {
      sectionElement.classList.add('hidden');
      listElement.innerHTML = '';
      return;
    }

    sectionElement.classList.remove('hidden');
    listElement.innerHTML = attachments
      .map((file) => {
        const sizeText = Number.isFinite(Number(file.size))
          ? `<span class="text-xs text-gray-500 dark:text-gray-400">(${Math.ceil(Number(file.size) / 1024)} KB)</span>`
          : '<span class="text-xs text-gray-500 dark:text-gray-400"></span>';

        return `
          <li class="flex items-center justify-between gap-3">
            <a href="${file.downloadUrl}" class="truncate text-indigo-600 hover:underline dark:text-indigo-300">
              ${file.filename || '첨부파일'}
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
    const editButtonElement = document.getElementById('editPostButton');
    const deleteButtonElement = document.getElementById('deletePostButton');
    const boardId = Number(document.body.dataset.boardId || 0);
    const articleId = Number(document.body.dataset.articleId || 0);
    const currentMemberId = Number(document.body.dataset.currentMemberId || 0);

    if (!titleElement || !authorElement || !dateElement || !viewsElement || !contentElement) return;

    if (!post) {
      titleElement.textContent = '게시글을 찾을 수 없습니다.';
      authorElement.textContent = '-';
      dateElement.textContent = '-';
      viewsElement.textContent = '0';
      contentElement.textContent = '요청한 게시글이 없거나 열람 권한이 없습니다.';
      if (editButtonElement) editButtonElement.classList.add('hidden');
      if (deleteButtonElement) deleteButtonElement.classList.add('hidden');
      renderAttachments([]);
      return;
    }

    titleElement.textContent = post.title || '(제목 없음)';
    authorElement.textContent = post.authorName || '-';
    dateElement.textContent = formatDate(post.createdAt);
    viewsElement.textContent = String(Number(post.viewCount || 0));
    // 작성자 본인인 경우에만 수정 버튼을 노출한다.
    if (editButtonElement && boardId && articleId) {
      const isAuthor = Number(post.authorId || 0) === currentMemberId;
      if (isAuthor) {
        editButtonElement.href = `/board/${boardId}/articles/${articleId}/edit`;
        editButtonElement.classList.remove('hidden');
        if (deleteButtonElement) deleteButtonElement.classList.remove('hidden');
      } else {
        editButtonElement.classList.add('hidden');
        if (deleteButtonElement) deleteButtonElement.classList.add('hidden');
      }
    }
    contentElement.innerHTML = ''; // 플레이스홀더 텍스트 제거
    contentViewer = null;

    if (window.toastui?.Editor?.factory) {
      // Markdown 본문을 Viewer로 렌더링해 이미지/링크를 정상 표시한다.
      contentViewer = window.toastui.Editor.factory({
        el: contentElement,
        viewer: true,
        initialValue: post.content || '',
      });
      // Viewer 렌더링이 DOM에 반영된 뒤 유튜브 링크 변환을 적용한다.
      requestAnimationFrame(() => upgradeYouTubeLinks(contentElement));
      return;
    }

    // 라이브러리 로딩 실패 폴백
    contentElement.textContent = post.content || '';
    upgradeYouTubeLinks(contentElement);
  }

  /**
   * 게시글 삭제 API를 호출한다.
   * @param {number} boardId 게시판 ID
   * @param {number} articleId 게시글 ID
   * @returns {Promise<void>}
   */
  async function deletePost(boardId, articleId) {
    const response = await fetch(`/api/board/${boardId}/articles/${articleId}/delete`, {
      method: 'POST',
      headers: {
        ...getHeaders(),
        Accept: 'application/json, text/plain, */*',
      },
      credentials: 'same-origin',
    });

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        throw new Error('삭제 권한이 없습니다.');
      }
      throw new Error('게시글 삭제에 실패했습니다.');
    }
  }

  /**
   * 삭제 버튼 클릭 이벤트를 처리한다.
   */
  async function handleDeleteClick() {
    const boardId = Number(document.body.dataset.boardId || 0);
    const articleId = Number(document.body.dataset.articleId || 0);
    const deleteButtonElement = document.getElementById('deletePostButton');
    if (!boardId || !articleId || !deleteButtonElement || !currentPost) return;

    const ok = window.confirm('정말 이 게시글을 삭제하시겠습니까? 삭제 후 복구할 수 없습니다.');
    if (!ok) return;

    try {
      deleteButtonElement.disabled = true;
      await deletePost(boardId, articleId);
      alert('게시글이 삭제되었습니다.');
      window.location.href = `/board/${boardId}`;
    } catch (error) {
      alert(error?.message || '요청 처리 중 오류가 발생했습니다.');
    } finally {
      deleteButtonElement.disabled = false;
    }
  }

  /**
   * 유튜브 링크에서 영상 ID를 추출한다.
   * @param {string|null} url 링크 URL
   * @returns {string|null}
   */
  function getYouTubeId(url) {
    try {
      const m = String(url).match(
        /(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([\w-]{11})/
      );
      return m ? m[1] : null;
    } catch {
      return null;
    }
  }

  /**
   * 본문 내 유튜브 링크를 iframe 임베드로 교체한다.
   * @param {HTMLElement} root 본문 root 요소
   */
  function upgradeYouTubeLinks(root) {
    if (!root) return;
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
      currentPost = post;
      renderPost(post);
      const attachments = await fetchAttachments(articleId);
      renderAttachments(attachments);
    } catch (error) {
      console.error(error);
      currentPost = null;
      renderPost(null);
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('deletePostButton')?.addEventListener('click', handleDeleteClick);
    init();
  });
})();
