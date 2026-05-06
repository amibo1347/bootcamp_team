(() => {
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  const customSelectMap = new Map();

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

  function closeAllCustomSelects() {
    customSelectMap.forEach(({ menu, trigger, container }) => {
      menu.classList.add('hidden');
      trigger.setAttribute('aria-expanded', 'false');
      if (container) {
        container.style.zIndex = '';
      }
    });
  }

  function syncCustomSelect(selectId) {
    const ui = customSelectMap.get(selectId);
    if (!ui) return;

    const { select, triggerText, menu } = ui;
    const selectedOption = select.options[select.selectedIndex];
    triggerText.textContent = selectedOption ? selectedOption.textContent : '선택하세요';

    menu.querySelectorAll('button[data-value]').forEach((itemButton) => {
      const isSelected = itemButton.dataset.value === select.value;
      itemButton.classList.toggle('bg-indigo-50', isSelected);
      itemButton.classList.toggle('text-indigo-700', isSelected);
      itemButton.classList.toggle('font-semibold', isSelected);
    });
  }

  function createCustomSelect(select) {
    const container = select.parentElement;
    const wrapper = document.createElement('div');
    wrapper.className = 'relative';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className =
      'w-full rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-left text-gray-700 hover:border-indigo-300 focus:outline-none focus:ring-2 focus:ring-indigo-300';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');

    const triggerInner = document.createElement('div');
    triggerInner.className = 'flex items-center justify-between';

    const triggerText = document.createElement('span');
    triggerText.className = 'truncate';
    const arrow = document.createElement('span');
    arrow.className = 'ml-3 text-gray-500';
    arrow.textContent = '▾';

    triggerInner.appendChild(triggerText);
    triggerInner.appendChild(arrow);
    trigger.appendChild(triggerInner);

    const menu = document.createElement('div');
    menu.className =
      'absolute left-0 top-full z-[10001] mt-1 hidden max-h-64 w-full overflow-auto rounded-lg border border-gray-200 bg-white shadow-lg';
    menu.setAttribute('role', 'listbox');

    Array.from(select.options).forEach((option) => {
      const item = document.createElement('button');
      item.type = 'button';
      item.dataset.value = option.value;
      item.disabled = option.disabled;
      item.className =
        'block w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300';
      item.textContent = option.textContent;
      item.addEventListener('click', () => {
        select.value = option.value;
        select.dispatchEvent(new Event('change', { bubbles: true }));
        closeAllCustomSelects();
      });
      menu.appendChild(item);
    });

    trigger.addEventListener('click', (e) => {
      e.stopPropagation();
      const isOpen = !menu.classList.contains('hidden');
      closeAllCustomSelects();
      if (!isOpen) {
        // Bring this field above its siblings while open
        if (container) {
          container.style.zIndex = '9999';
        }
        menu.classList.remove('hidden');
        trigger.setAttribute('aria-expanded', 'true');
      }
    });

    select.classList.add('hidden');
    select.insertAdjacentElement('afterend', wrapper);
    wrapper.appendChild(trigger);
    wrapper.appendChild(menu);

    customSelectMap.set(select.id, { select, trigger, triggerText, menu, container });
    select.addEventListener('change', () => syncCustomSelect(select.id));
    syncCustomSelect(select.id);
  }

  function initCustomSelects() {
    document
      .querySelectorAll('#createBoardForm select, #editBoardForm select')
      .forEach((select) => createCustomSelect(select));

    document.addEventListener('click', () => closeAllCustomSelects());
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
    document.getElementById('editBoardType').dispatchEvent(new Event('change', { bubbles: true }));
    document.getElementById('editDeptId').dispatchEvent(new Event('change', { bubbles: true }));
    document.getElementById('editPositionId').dispatchEvent(new Event('change', { bubbles: true }));
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
    initCustomSelects();
    document.getElementById('createBoardForm')?.addEventListener('submit', onCreateSubmit);
    document.getElementById('editBoardForm')?.addEventListener('submit', onEditSubmit);
  });
})();

