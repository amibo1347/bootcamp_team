(() => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  const MAX_ATTACHMENTS = 5;
  const MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024;
  let editor = null;
  let isUploadingAttachments = false;
  /**
   * 사용자가 누적 선택한 원본 파일 목록을 보관한다.
   * @type {File[]}
   */
  let selectedFiles = [];

  /**
   * 업로드 완료된 첨부파일 메타를 보관한다.
   * @type {Array<{id:number, filename:string, size:number|null, downloadUrl?:string}>}
   */
  let uploadedAttachments = [];

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
   * 업로드 완료된 첨부파일 id들을 hidden input으로 동기화한다.
   * - 서버의 @ModelAttribute List<Long> attachmentIds 바인딩을 위해
   *   `attachmentIds=1&attachmentIds=2...` 형태로 전송되도록 구성한다.
   */
  function syncAttachmentIdFields() {
    const container = document.getElementById('attachmentIdFields');
    if (!container) return;

    container.innerHTML = uploadedAttachments
      .map((att) => `<input type="hidden" name="attachmentIds" value="${att.id}" />`)
      .join('');
  }

  /**
   * 업로드된 첨부파일 목록을 화면에 렌더링한다.
   */
  function renderUploadedAttachments() {
    const listElement = document.getElementById('selectedAttachmentList');
    if (!listElement) return;

    if (!uploadedAttachments.length) {
      listElement.innerHTML = '<li class="text-gray-400">선택된 파일이 없습니다.</li>';
      return;
    }

    listElement.innerHTML = uploadedAttachments
      .map((att, index) => {
        const sizeText = Number.isFinite(Number(att.size))
          ? `${Math.ceil(Number(att.size) / 1024)} KB`
          : '-';

        return `
          <li class="flex items-center justify-between gap-3">
            <span class="min-w-0 truncate">${att.filename} (${sizeText})</span>
            <button
              type="button"
              data-attachment-index="${index}"
              class="shrink-0 rounded-md bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-700 hover:bg-gray-200 dark:bg-white/10 dark:text-gray-200 dark:hover:bg-white/15">
              제거
            </button>
          </li>
        `;
      })
      .join('');
  }

  /**
   * 업로드 진행 중 상태를 렌더링한다.
   * @param {string} message 상태 메시지
   */
  function renderUploadingState(message) {
    const listElement = document.getElementById('selectedAttachmentList');
    if (!listElement) return;
    listElement.innerHTML = `<li class="text-gray-400">${message}</li>`;
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
      headers: getHeaders(), // FormData 사용 시 Content-Type은 브라우저가 자동 설정
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
   * 첨부파일 선택 변경 시 개수/용량을 검증하고, 업로드까지 수행한다.
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

    if (mergedFiles.length > MAX_ATTACHMENTS) {
      alert(`첨부파일은 최대 ${MAX_ATTACHMENTS}개까지 선택할 수 있습니다.`);
      input.value = '';
      return;
    }

    try {
      isUploadingAttachments = true;
      input.disabled = true;
      renderUploadingState('첨부파일 업로드 중...');

      const uploadedKeySet = new Set(
        uploadedAttachments.map((att) => `${att.filename}__${Number(att.size || 0)}`)
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
      uploadedAttachments = [...uploadedAttachments, ...results];
      syncAttachmentIdFields();
      renderUploadedAttachments();
    } catch (error) {
      alert(error?.message || '첨부파일 업로드 중 오류가 발생했습니다.');
    } finally {
      isUploadingAttachments = false;
      input.disabled = false;
      input.value = '';
    }
  }

  /**
   * 현재 첨부파일 업로드/선택 상태를 검증한다.
   * @returns {boolean}
   */
  function validateAttachments() {
    // 업로드 중 submit 방지
    if (isUploadingAttachments) {
      alert('첨부파일 업로드가 완료될 때까지 잠시만 기다려주세요.');
      return false;
    }

    // 파일을 선택했는데 업로드 결과가 없는 케이스 방지(업로드 실패/차단)
    const selectedFiles = getSelectedAttachments();
    if (selectedFiles.length && !uploadedAttachments.length) {
      alert('첨부파일 업로드가 완료되지 않았습니다. 다시 선택해주세요.');
      return false;
    }

    return true;
  }

  /**
   * 첨부파일 제거 버튼 클릭을 처리한다(클라이언트에서 연결 대상에서 제외).
   * @param {MouseEvent} event 클릭 이벤트
   */
  function handleRemoveAttachmentClick(event) {
    const button = event.target?.closest?.('button[data-attachment-index]');
    if (!button) return;

    const index = Number(button.dataset.attachmentIndex);
    if (!Number.isFinite(index)) return;

    const removed = uploadedAttachments[index];
    uploadedAttachments = uploadedAttachments.filter((_, i) => i !== index);
    if (removed) {
      selectedFiles = selectedFiles.filter(
        (file) =>
          !(file.name === removed.filename && Number(file.size || 0) === Number(removed.size || 0))
      );
    }
    syncAttachmentIdFields();
    renderUploadedAttachments();
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
      const body = new URLSearchParams();
      body.set('title', payload.title);
      body.set('content', payload.content);
      body.set('boardId', String(payload.boardId));
      uploadedAttachments.forEach((att) => {
        body.append('attachmentIds', String(att.id));
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
    uploadedAttachments = [];
    selectedFiles = [];
    syncAttachmentIdFields();
    renderUploadedAttachments();
    document.getElementById('attachments')?.addEventListener('change', handleAttachmentChange);
    document.getElementById('selectedAttachmentList')?.addEventListener('click', handleRemoveAttachmentClick);
    document.getElementById('postCreateForm')?.addEventListener('submit', submitPost);
  });
})();
