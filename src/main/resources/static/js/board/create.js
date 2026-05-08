(() => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  const MAX_ATTACHMENTS = 5;
  const MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024;
  let editor = null;

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
   * 입력 요소 값을 trim 해서 반환한다.
   * @param {string} id 입력 요소 ID
   * @returns {string}
   */
  function getTrimmedValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  /**
   * 에디터의 Markdown 본문을 숨김 textarea로 동기화한다.
   */
  function syncEditorContentToTextarea() {
    if (!editor) return;
    const contentElement = document.getElementById('content');
    if (!contentElement) return;
    contentElement.value = editor.getMarkdown().trim();
  }

  /**
   * 첨부파일 input에 선택된 파일 목록을 반환한다.
   * @returns {File[]}
   */
  function getSelectedAttachments() {
    const attachmentInput = document.getElementById('attachments');
    if (!attachmentInput?.files) return [];
    return Array.from(attachmentInput.files);
  }

  /**
   * 선택된 첨부파일 목록을 화면에 렌더링한다.
   * @param {File[]} files 선택된 첨부파일 배열
   */
  function renderSelectedAttachments(files) {
    const listElement = document.getElementById('selectedAttachmentList');
    if (!listElement) return;

    if (!files.length) {
      listElement.innerHTML = '<li class="text-gray-400">선택된 파일이 없습니다.</li>';
      return;
    }

    listElement.innerHTML = files
      .map((file) => `<li>${file.name} (${Math.ceil(file.size / 1024)} KB)</li>`)
      .join('');
  }

  /**
   * 첨부파일 선택 변경 시 개수/용량을 검증하고 목록을 갱신한다.
   * @param {Event} event 파일 input change 이벤트
   */
  function handleAttachmentChange(event) {
    const input = event.currentTarget;
    const files = Array.from(input?.files || []);

    if (files.length > MAX_ATTACHMENTS) {
      alert(`첨부파일은 최대 ${MAX_ATTACHMENTS}개까지 선택할 수 있습니다.`);
      input.value = '';
      renderSelectedAttachments([]);
      return;
    }

    const hasOversizedFile = files.some((file) => file.size > MAX_ATTACHMENT_SIZE);
    if (hasOversizedFile) {
      alert('파일당 최대 10MB까지 업로드할 수 있습니다.');
      input.value = '';
      renderSelectedAttachments([]);
      return;
    }

    renderSelectedAttachments(files);
  }

  /**
   * 현재 선택된 첨부파일 유효성을 검증한다.
   * @returns {boolean}
   */
  function validateAttachments() {
    const files = getSelectedAttachments();
    if (files.length > MAX_ATTACHMENTS) {
      alert(`첨부파일은 최대 ${MAX_ATTACHMENTS}개까지 선택할 수 있습니다.`);
      return false;
    }
    if (files.some((file) => file.size > MAX_ATTACHMENT_SIZE)) {
      alert('파일당 최대 10MB까지 업로드할 수 있습니다.');
      return false;
    }
    return true;
  }

  /**
   * 에디터 이미지 Blob을 백엔드 업로드 API로 전송한 뒤 URL을 삽입한다.
   * @param {Blob} blob 업로드할 이미지 파일/블롭
   * @param {(url: string, altText?: string) => void} callback 에디터 삽입 콜백
   * @returns {Promise<boolean|void>}
   */
  async function handleImage(blob, callback) {
    const formData = new FormData();
    formData.append('file', blob);

    try {
      const response = await fetch('/api/article-image', {
        method: 'POST',
        headers: getHeaders(), // FormData 사용 시 Content-Type은 브라우저가 자동 설정
        body: formData,
        credentials: 'same-origin',
      });
      if (!response.ok) throw new Error('이미지 업로드에 실패했습니다.');
      const data = await response.json();
      if (!data?.url) throw new Error('업로드 URL을 받지 못했습니다.');
      callback(data.url, blob.name || 'image');
      return false; // 기본 base64 삽입 동작 차단
    } catch (err) {
      alert(err?.message || '이미지 업로드 중 오류가 발생했습니다.');
      return false;
    }
  }

  /**
   * TOAST UI Editor를 초기화한다.
   */
  function initEditor() {
    const editorRoot = document.getElementById('contentEditor');
    if (!editorRoot || !window.toastui?.Editor) return;

    editor = new window.toastui.Editor({
      el: editorRoot,
      height: '420px',
      initialEditType: 'wysiwyg',
      previewStyle: 'vertical',
      language: 'ko-KR',
      placeholder: '게시글 내용을 입력하세요',
      hideModeSwitch: true,
      hooks: { addImageBlobHook: handleImage }
    });

    editor.on('change', syncEditorContentToTextarea);
    syncEditorContentToTextarea();
  }

  /**
   * 등록 API 요청용 payload를 생성한다.
   * @returns {{boardId: number|null, title: string, content: string}}
   */
  function buildPayload() {
    syncEditorContentToTextarea();
    const boardId = Number(document.getElementById('boardId')?.value || 0);
    return {
      boardId: Number.isFinite(boardId) && boardId > 0 ? boardId : null,
      title: getTrimmedValue('title'),
      content: getTrimmedValue('content'),
    };
  }

  /**
   * 등록 전 입력값 유효성을 검증한다.
   * @param {{boardId: number|null, title: string, content: string}} payload
   * @returns {boolean}
   */
  function validate(payload) {
    if (!payload.boardId) {
      alert('게시판 정보가 없습니다. 올바른 경로로 다시 접근해주세요.');
      return false;
    }
    if (!payload.title) {
      alert('제목을 입력해주세요.');
      document.getElementById('title')?.focus();
      return false;
    }
    if (!payload.content) {
      alert('본문을 입력해주세요.');
      document.getElementById('content')?.focus();
      return false;
    }
    if (!validateAttachments()) {
      document.getElementById('attachments')?.focus();
      return false;
    }
    return true;
  }

  /**
   * 게시글 생성 요청을 전송한다.
   * @param {SubmitEvent} event 폼 submit 이벤트
   */
  async function submitPost(event) {
    event.preventDefault();

    const payload = buildPayload();
    if (!validate(payload)) return;

    const endpoint = `/api/board/${payload.boardId}/articles/new`;
    const submitButton = event.currentTarget.querySelector('button[type="submit"]');

    try {
      if (submitButton) submitButton.disabled = true;
      // 현재 백엔드 시그니처에 맞춰 form-urlencoded 로 전송한다.
      const body = new URLSearchParams({
        title: payload.title,
        content: payload.content,
        boardId: String(payload.boardId)
      });
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          ...getHeaders(),
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        },
        body,
      });

      if (!response.ok) {
        throw new Error('게시글 생성 요청에 실패했습니다.');
      }

      alert('게시글이 등록되었습니다.');
      window.location.href = `/board/${payload.boardId}`;
    } catch (error) {
      alert(error?.message || '요청 처리 중 오류가 발생했습니다.');
    } finally {
      if (submitButton) submitButton.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    initEditor();
    renderSelectedAttachments([]);
    document.getElementById('attachments')?.addEventListener('change', handleAttachmentChange);
    document.getElementById('postCreateForm')?.addEventListener('submit', submitPost);
  });
})();
