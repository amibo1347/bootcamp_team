(() => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

  function getHeaders() {
    return {
      'Content-Type': 'application/json',
      [csrfHeader]: csrfToken,
    };
  }

  function getTrimmedValue(id) {
    return document.getElementById(id)?.value?.trim() || '';
  }

  function parseTags(rawTags) {
    return rawTags
      .split(',')
      .map((tag) => tag.trim())
      .filter((tag) => tag.length > 0);
  }

  function buildPayload() {
    const boardId = Number(document.getElementById('boardId')?.value || 0);
    return {
      boardId: Number.isFinite(boardId) && boardId > 0 ? boardId : null,
      title: getTrimmedValue('title'),
      summary: getTrimmedValue('summary'),
      content: getTrimmedValue('content'),
      authorName: getTrimmedValue('authorName'),
      tags: parseTags(getTrimmedValue('tags')),
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

    const endpoint = getTrimmedValue('apiEndpoint') || '/api/board/post/create';
    const submitButton = event.currentTarget.querySelector('button[type="submit"]');

    try {
      if (submitButton) submitButton.disabled = true;
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error('게시글 생성 요청에 실패했습니다. API 경로를 확인해주세요.');
      }

      alert('게시글이 등록되었습니다.');
      event.currentTarget.reset();
    } catch (error) {
      alert(error?.message || '요청 처리 중 오류가 발생했습니다.');
    } finally {
      if (submitButton) submitButton.disabled = false;
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('postCreateForm')?.addEventListener('submit', submitPost);
  });
})();
