(() => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

  function headers() {
    return {
      'Content-Type': 'application/json',
      [csrfHeader]: csrfToken,
    };
  }

  function getPayload(prefix = '') {
    const getValue = (id) => document.getElementById(id)?.value;
    const getChecked = (id) => Boolean(document.getElementById(id)?.checked);

    return {
      boardId: Number(getValue(`${prefix}BoardId`) || 0) || null,
      boardName: (getValue(`${prefix}BoardName`) || '').trim(),
      boardType: getValue(`${prefix}BoardType`),
      deptId: Number(getValue(`${prefix}DeptId`)),
      positionId: Number(getValue(`${prefix}PositionId`)),
      viewType: getValue(`${prefix}ViewType`) || 'LIST',
      readScope: getValue(`${prefix}ReadScope`) || 'ALL',
      writeScope: getValue(`${prefix}WriteScope`) || 'ALL',
      commentScope: getValue(`${prefix}CommentScope`) || 'ALL',
      anonymousType: getValue(`${prefix}AnonymousType`) || 'NAME',
      isActive: getChecked(`${prefix}IsActive`),
      isAiUse: getChecked(`${prefix}IsAiUse`),
    };
  }

  async function onCreateSubmit(e) {
    e.preventDefault();
    const payload = getPayload('');

    try {
      const response = await fetch('/api/admin/board/create', {
        method: 'POST',
        headers: headers(),
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error('게시판 생성에 실패했습니다.');
      }

      alert('게시판이 생성되었습니다.');
      location.reload();
    } catch (error) {
      alert(error?.message || '요청에 실패했습니다.');
    }
  }

  function openEditModal(button) {
    const d = button?.dataset || {};
    document.getElementById('editBoardId').value = d.boardId || '';
    document.getElementById('editBoardName').value = d.boardName || '';
    document.getElementById('editBoardType').value = d.boardType || 'NOTICE';
    document.getElementById('editDeptId').value = d.deptId || '';
    document.getElementById('editPositionId').value = d.positionId || '';
    document.getElementById('editIsActive').checked = d.isActive === 'true';
    document.getElementById('editIsAiUse').checked = d.isAiUse === 'true';

    const modal = document.getElementById('editModal');
    modal.classList.remove('hidden');
    modal.classList.add('flex');
  }

  function closeEditModal() {
    const modal = document.getElementById('editModal');
    modal.classList.add('hidden');
    modal.classList.remove('flex');
  }

  async function onEditSubmit(e) {
    e.preventDefault();

    const payload = {
      ...getPayload('edit'),
      viewType: 'LIST',
      readScope: 'ALL',
      writeScope: 'ALL',
      commentScope: 'ALL',
      anonymousType: 'NAME',
    };

    try {
      const response = await fetch('/api/admin/board/update', {
        method: 'PUT',
        headers: headers(),
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error('게시판 수정에 실패했습니다.');
      }

      alert('게시판이 수정되었습니다.');
      location.reload();
    } catch {
      alert('수정 API가 아직 구현되지 않았거나 요청에 실패했습니다.');
    }
  }

  async function deleteBoard(boardId) {
    if (!confirm('정말 삭제하시겠습니까?')) {
      return;
    }

    try {
      const response = await fetch(`/api/admin/board/delete/${boardId}`, {
        method: 'DELETE',
        headers: headers(),
      });

      if (!response.ok) {
        throw new Error('삭제 실패');
      }

      alert('게시판이 삭제되었습니다.');
      location.reload();
    } catch {
      alert('삭제 API가 아직 구현되지 않았거나 요청에 실패했습니다.');
    }
  }

  // expose for inline onclick + thymeleaf attr usage
  window.openEditModal = openEditModal;
  window.closeEditModal = closeEditModal;
  window.deleteBoard = deleteBoard;

  document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('createBoardForm')?.addEventListener('submit', onCreateSubmit);
    document.getElementById('editBoardForm')?.addEventListener('submit', onEditSubmit);
  });
})();

