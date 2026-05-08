(() => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  let editor = null;

  function getHeaders() {
    return {
      [csrfHeader]: csrfToken,
    };
  }

  function getTrimmedValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function syncEditorContentToTextarea() {
    if (!editor) return;
    const contentElement = document.getElementById('content');
    if (!contentElement) return;
    contentElement.value = editor.getMarkdown().trim();
  }

  function handleImage(blob, callback) {
      const reader = new FileReader();
      reader.onload = () => {
          callback(reader.result, blob.name || 'image');
      };
      reader.onerror = () => alert('이미지를 읽지 못했습니다.');
      reader.readAsDataURL(blob);
  }

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
      hooks : {addImageBlobHook: handleImage}
    });

    editor.on('change', syncEditorContentToTextarea);
    syncEditorContentToTextarea();
  }

  function buildPayload() {
    syncEditorContentToTextarea();
    const boardId = Number(document.getElementById('boardId')?.value || 0);
    return {
      boardId: Number.isFinite(boardId) && boardId > 0 ? boardId : null,
      title: getTrimmedValue('title'),
      content: getTrimmedValue('content'),
    };
  }

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
    return true;
  }

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
        boardId: payload.boardId
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
    document.getElementById('postCreateForm')?.addEventListener('submit', submitPost);
  });
})();
