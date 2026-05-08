(() => {
  // CSRF 토큰 정보는 Spring Security 메타 태그에서 읽는다.
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  const MAX_ATTACHMENTS = 5;
  const MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024;
  let editor = null;
  let isUploadingAttachments = false;
  let existingAttachments = [];
  /**
   * 수정 화면에서 사용자가 누적 선택한 새 첨부파일 목록을 보관한다.
   * @type {File[]}
   */
  let selectedFiles = [];

  /**
   * 새로 업로드가 완료된 첨부파일 메타를 보관한다.
   * @type {Array<{id:number, filename:string, size:number|null}>}
   */
  let newUploadedAttachments = [];

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
   * 첨부파일 input에 선택된 파일 목록을 반환한다.
   * @returns {File[]}
   */
  function getSelectedAttachments() {
    return selectedFiles;
  }

  /**
   * 파일 고유 식별 키를 생성한다.
   * @param {File} file 원본 파일
   * @returns {string}
   */
  function getFileKey(file) {
    return `${file.name}__${file.size}__${file.lastModified}`;
  }

  /**
   * 새 업로드 완료 첨부파일 id들을 hidden input으로 동기화한다.
   * - 서버 @ModelAttribute List<Long> attachmentIds 바인딩을 위해 사용한다.
   */
  function syncAttachmentIdFields() {
    const container = document.getElementById('attachmentIdFields');
    if (!container) return;

    container.innerHTML = newUploadedAttachments
      .map((att) => `<input type="hidden" name="attachmentIds" value="${att.id}" />`)
      .join('');
  }

  /**
   * 게시글에 연결된 첨부파일 목록을 조회한다.
   * @param {number} articleId 게시글 ID
   * @returns {Promise<Array<{id:number, filename:string, size:number|null, downloadUrl:string}>>}
   */
  async function fetchExistingAttachments(articleId) {
    const response = await fetch(`/api/article-attachment?articleId=${articleId}`, {
      method: 'GET',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    });

    if (!response.ok) {
      throw new Error('첨부파일 목록을 불러오지 못했습니다.');
    }

    const data = await response.json();
    return Array.isArray(data) ? data : [];
  }

  /**
   * 첨부파일 1건을 삭제한다.
   * @param {number} attachmentId 첨부파일 ID
   * @returns {Promise<void>}
   */
  async function deleteAttachment(attachmentId) {
    const response = await fetch(`/api/article-attachment/${attachmentId}`, {
      method: 'DELETE',
      headers: {
        ...getHeaders(),
        Accept: 'application/json, text/plain, */*',
      },
      credentials: 'same-origin',
    });

    if (!response.ok) {
      if (response.status === 401 || response.status === 403) {
        throw new Error('첨부파일 삭제 권한이 없습니다.');
      }
      if (response.status === 404) {
        throw new Error('첨부파일을 찾을 수 없습니다.');
      }
      throw new Error('첨부파일 삭제에 실패했습니다.');
    }
  }

  /**
   * 첨부파일 1개를 업로드한다.
   * @param {File} file 업로드 파일
   * @returns {Promise<{id:number, filename:string, size:number|null}>}
   */
  async function uploadAttachment(file) {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch('/api/article-attachment', {
      method: 'POST',
      headers: getHeaders(),
      body: formData,
      credentials: 'same-origin',
    });

    if (!response.ok) {
      if (response.status === 413) throw new Error('첨부파일 용량이 너무 큽니다.');
      throw new Error('첨부파일 업로드에 실패했습니다.');
    }

    const data = await response.json();
    if (!data?.id) throw new Error('첨부파일 ID를 받지 못했습니다.');
    return {
      id: Number(data.id),
      filename: data.filename || file.name,
      size: data.size ?? file.size,
    };
  }

  /**
   * 기존 첨부파일 목록을 렌더링한다.
   * @param {Array<{id:number, filename:string, size:number|null, downloadUrl:string}>} attachments 첨부파일 목록
   */
  function renderExistingAttachments(attachments) {
    const listElement = document.getElementById('existingAttachmentList');
    if (!listElement) return;

    if (!attachments?.length) {
      listElement.innerHTML = '<li class="text-gray-400">첨부파일이 없습니다.</li>';
      return;
    }

    listElement.innerHTML = attachments
      .map((file) => {
        const sizeText = Number.isFinite(Number(file.size))
          ? `${Math.ceil(Number(file.size) / 1024)} KB`
          : '-';

        return `
          <li class="flex items-center justify-between gap-3 rounded-lg border border-gray-200 px-3 py-2 dark:border-strokedark">
            <a href="${file.downloadUrl}" class="min-w-0 flex-1 truncate text-indigo-600 hover:underline dark:text-indigo-300">
              ${file.filename || '첨부파일'}
            </a>
            <span class="shrink-0 text-xs text-gray-500 dark:text-gray-400">${sizeText}</span>
            <button
              type="button"
              data-delete-attachment-id="${file.id}"
              class="shrink-0 rounded-md bg-rose-100 px-2.5 py-1 text-xs font-medium text-rose-600 hover:bg-rose-200 dark:bg-rose-400/20 dark:text-rose-200 dark:hover:bg-rose-400/30">
              삭제
            </button>
          </li>
        `;
      })
      .join('');
  }

  /**
   * 새로 추가한 첨부파일 목록을 렌더링한다.
   */
  function renderNewAttachments() {
    const listElement = document.getElementById('newAttachmentList');
    if (!listElement) return;

    if (!newUploadedAttachments.length) {
      listElement.innerHTML = '<li class="text-gray-400">추가된 파일이 없습니다.</li>';
      return;
    }

    listElement.innerHTML = newUploadedAttachments
      .map((att, index) => {
        const sizeText = Number.isFinite(Number(att.size))
          ? `${Math.ceil(Number(att.size) / 1024)} KB`
          : '-';

        return `
          <li class="flex items-center justify-between gap-3">
            <span class="min-w-0 truncate">${att.filename} (${sizeText})</span>
            <button
              type="button"
              data-remove-new-attachment-index="${index}"
              class="shrink-0 rounded-md bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-200 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15">
              제거
            </button>
          </li>
        `;
      })
      .join('');
  }

  /**
   * 첨부파일 목록을 조회해 화면에 렌더링한다.
   * @returns {Promise<void>}
   */
  async function loadExistingAttachments() {
    const articleId = Number(document.getElementById('articleId')?.value || 0);
    if (!articleId) return;

    try {
      const attachments = await fetchExistingAttachments(articleId);
      existingAttachments = attachments;
      renderExistingAttachments(attachments);
    } catch (error) {
      existingAttachments = [];
      renderExistingAttachments([]);
      alert(error?.message || '첨부파일 목록 조회 중 오류가 발생했습니다.');
    }
  }

  /**
   * 새 첨부파일 선택 변경 시 개수/용량을 검증하고 업로드를 수행한다.
   * @param {Event} event 파일 input change 이벤트
   */
  async function handleAttachmentChange(event) {
    const input = event.currentTarget;
    const files = Array.from(input?.files || []);

    if (!files.length) return;

    const hasOversizedFile = files.some((file) => file.size > MAX_ATTACHMENT_SIZE);
    if (hasOversizedFile) {
      alert('파일당 최대 10MB까지 업로드할 수 있습니다.');
      input.value = '';
      return;
    }

    const existingKeySet = new Set(selectedFiles.map((file) => getFileKey(file)));
    const uniqueNewFiles = files.filter((file) => !existingKeySet.has(getFileKey(file)));
    const mergedFiles = [...selectedFiles, ...uniqueNewFiles];

    const totalCount = existingAttachments.length + mergedFiles.length;
    if (totalCount > MAX_ATTACHMENTS) {
      alert(`첨부파일은 기존 포함 최대 ${MAX_ATTACHMENTS}개까지 업로드할 수 있습니다.`);
      input.value = '';
      return;
    }

    try {
      isUploadingAttachments = true;
      input.disabled = true;

      const uploadedKeySet = new Set(
        newUploadedAttachments.map((att) => `${att.filename}__${Number(att.size || 0)}`)
      );
      const results = [];
      for (const file of uniqueNewFiles) {
        // eslint-disable-next-line no-await-in-loop
        const uploaded = await uploadAttachment(file);
        const uploadedKey = `${uploaded.filename}__${Number(uploaded.size || 0)}`;
        if (uploadedKeySet.has(uploadedKey)) continue;
        uploadedKeySet.add(uploadedKey);
        results.push(uploaded);
      }

      selectedFiles = mergedFiles;
      newUploadedAttachments = [...newUploadedAttachments, ...results];
      syncAttachmentIdFields();
      renderNewAttachments();
    } catch (error) {
      alert(error?.message || '첨부파일 업로드 중 오류가 발생했습니다.');
    } finally {
      isUploadingAttachments = false;
      input.disabled = false;
      input.value = '';
    }
  }

  /**
   * 첨부파일 삭제 버튼 클릭 이벤트를 처리한다.
   * @param {MouseEvent} event 클릭 이벤트
   * @returns {Promise<void>}
   */
  async function handleDeleteAttachmentClick(event) {
    const button = event.target?.closest?.('button[data-delete-attachment-id]');
    if (!button) return;

    const attachmentId = Number(button.dataset.deleteAttachmentId || 0);
    if (!attachmentId) return;

    const ok = window.confirm('이 첨부파일을 삭제하시겠습니까? 삭제 후 복구할 수 없습니다.');
    if (!ok) return;

    try {
      button.disabled = true;
      await deleteAttachment(attachmentId);
      await loadExistingAttachments();
    } catch (error) {
      alert(error?.message || '첨부파일 삭제 중 오류가 발생했습니다.');
    } finally {
      button.disabled = false;
    }
  }

  /**
   * 새 첨부파일 제거 버튼 클릭 이벤트를 처리한다.
   * - 아직 글에 연결되지 않은 업로드 파일은 서버에서도 즉시 삭제한다.
   * @param {MouseEvent} event 클릭 이벤트
   */
  async function handleRemoveNewAttachmentClick(event) {
    const button = event.target?.closest?.('button[data-remove-new-attachment-index]');
    if (!button) return;

    const index = Number(button.dataset.removeNewAttachmentIndex);
    if (!Number.isFinite(index)) return;

    const target = newUploadedAttachments[index];
    if (!target?.id) return;

    try {
      button.disabled = true;
      await deleteAttachment(target.id);
      newUploadedAttachments = newUploadedAttachments.filter((_, i) => i !== index);
      selectedFiles = selectedFiles.filter(
        (file) =>
          !(file.name === target.filename && Number(file.size || 0) === Number(target.size || 0))
      );
      syncAttachmentIdFields();
      renderNewAttachments();
    } catch (error) {
      alert(error?.message || '첨부파일 제거 중 오류가 발생했습니다.');
    } finally {
      button.disabled = false;
    }
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
    const initialContent = document.getElementById('content')?.value || '';
    if (!editorRoot || !window.toastui?.Editor) return;

    editor = new window.toastui.Editor({
      el: editorRoot,
      height: '420px',
      initialEditType: 'wysiwyg',
      previewStyle: 'vertical',
      language: 'ko-KR',
      placeholder: '게시글 내용을 입력하세요',
      hideModeSwitch: true,
      initialValue: initialContent,
      hooks: { addImageBlobHook: handleImage },
    });

    editor.on('change', syncEditorContentToTextarea);
    syncEditorContentToTextarea();
  }

  /**
   * 수정 API 요청용 payload를 생성한다.
   * @returns {{boardId: number|null, articleId: number|null, title: string, content: string}}
   */
  function buildPayload() {
    syncEditorContentToTextarea();
    const boardId = Number(document.getElementById('boardId')?.value || 0);
    const articleId = Number(document.getElementById('articleId')?.value || 0);
    return {
      boardId: Number.isFinite(boardId) && boardId > 0 ? boardId : null,
      articleId: Number.isFinite(articleId) && articleId > 0 ? articleId : null,
      title: getTrimmedValue('title'),
      content: getTrimmedValue('content'),
    };
  }

  /**
   * 수정 전 입력값 유효성을 검증한다.
   * @param {{boardId: number|null, articleId: number|null, title: string, content: string}} payload
   * @returns {boolean}
   */
  function validate(payload) {
    if (!payload.boardId || !payload.articleId) {
      alert('게시글 정보가 없습니다. 올바른 경로로 다시 접근해주세요.');
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
    if (isUploadingAttachments) {
      alert('첨부파일 업로드가 완료될 때까지 잠시만 기다려주세요.');
      return false;
    }
    const selectedFiles = getSelectedAttachments();
    if (selectedFiles.length && !newUploadedAttachments.length) {
      alert('첨부파일 업로드가 완료되지 않았습니다. 다시 선택해주세요.');
      document.getElementById('attachments')?.focus();
      return false;
    }
    return true;
  }

  /**
   * 게시글 수정 요청을 전송한다.
   * @param {SubmitEvent} event 폼 submit 이벤트
   */
  async function submitEditPost(event) {
    event.preventDefault();

    const payload = buildPayload();
    if (!validate(payload)) return;

    const endpoint = `/api/board/${payload.boardId}/articles/${payload.articleId}/edit`;
    const submitButton = event.currentTarget.querySelector('button[type="submit"]');

    try {
      if (submitButton) submitButton.disabled = true;

      // 백엔드 @ModelAttribute 바인딩에 맞춰 form-urlencoded로 전송한다.
      const body = new URLSearchParams();
      body.set('title', payload.title);
      body.set('content', payload.content);
      body.set('boardId', String(payload.boardId));
      newUploadedAttachments.forEach((att) => {
        body.append('attachmentIds', String(att.id));
      });

      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          ...getHeaders(),
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
        },
        body,
        credentials: 'same-origin',
      });

      if (!response.ok) {
        throw new Error('게시글 수정 요청에 실패했습니다.');
      }

      alert('게시글이 수정되었습니다.');
      window.location.href = `/board/${payload.boardId}/articles/${payload.articleId}`;
    } catch (error) {
      alert(error?.message || '요청 처리 중 오류가 발생했습니다.');
    } finally {
      if (submitButton) submitButton.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    initEditor();
    newUploadedAttachments = [];
    selectedFiles = [];
    syncAttachmentIdFields();
    renderNewAttachments();
    loadExistingAttachments();
    document.getElementById('attachments')?.addEventListener('change', handleAttachmentChange);
    document.getElementById('existingAttachmentList')?.addEventListener('click', handleDeleteAttachmentClick);
    document.getElementById('newAttachmentList')?.addEventListener('click', handleRemoveNewAttachmentClick);
    document.getElementById('postEditForm')?.addEventListener('submit', submitEditPost);
  });
})();
