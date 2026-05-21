/**
 * admin/managingBoard.html — 게시판 생성·수정·삭제용 스크립트
 *
 * - 생성 폼: 숨김 필드(readScope 등) + 라디오/체크박스 UI 동기화 후 POST /api/admin/board/create
 * - 수정 모달: 목록의 data-* 로 폼 채움 후 POST /api/admin/board/update/{id}
 * - 삭제: POST /api/admin/board/delete/{id}
 * - native select는 버튼+드롭다운 형태의 커스텀 셀렉트로 교체 (Tailwind 스타일용)
 */
(() => {
  /** 페이지 메타의 CSRF 토큰·헤더명 (Spring Security) */
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  /** select id → 트리거·메뉴 등 DOM 참조 (커스텀 셀렉트용) */
  const customSelectMap = new Map();

  /** JSON 요청 시 공통 헤더 (CSRF 포함) */
  function headers() {
    return {
      'Content-Type': 'application/json',
      [csrfHeader]: csrfToken,
    };
  }

  /**
   * 생성/수정 폼에서 서버로 보낼 본문 객체 생성
   * @param {string} prefix '' 이면 생성 폼(boardName 등), 'edit' 이면 수정 폼(editBoardName 등)
   */
  function getPayload(prefix = '') {
    const getValue = (id) => document.getElementById(id)?.value;
    const getChecked = (id) => Boolean(document.getElementById(id)?.checked);
    const idFor = (name) => prefix ? `${prefix}${name[0].toUpperCase()}${name.slice(1)}` : name;

    return {
      boardId: Number(getValue(idFor('boardId')) || 0) || null,
      boardName: (getValue(idFor('boardName')) || '').trim(),
      boardType: getValue(idFor('boardType')),
      viewType: getValue(idFor('viewType')) || 'LIST',
      readScope: getValue(idFor('readScope')) || 'ALL',
      writeScope: getValue(idFor('writeScope')) || 'ALL',
      commentScope: getValue(idFor('commentScope')) || 'ALL',
      anonymousType: getValue(idFor('anonymousType')) || 'NAME',
      isActive: getChecked(idFor('isActive')),
      isAiUse: getChecked(idFor('isAiUse')),
    };
  }

  /** 열려 있는 모든 커스텀 셀렉트 드롭다운 닫기 + z-index 복구 */
  function closeAllCustomSelects() {
    customSelectMap.forEach(({ menu, trigger, container }) => {
      menu.classList.add('hidden');
      trigger.setAttribute('aria-expanded', 'false');
      if (container) {
        container.style.zIndex = '';
      }
    });
  }

  /** 네이티브 select 값 변경 후 트리거 라벨·메뉴 항목 하이라이트 동기화 */
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
      itemButton.classList.toggle('dark:bg-indigo-950/40', isSelected);
      itemButton.classList.toggle('dark:text-indigo-300', isSelected);
      itemButton.classList.toggle('font-semibold', isSelected);
    });
  }

  /**
   * 단일 native select를 숨기고, 버튼 + 목록으로 동일 동작의 커스텀 UI 생성
   * (실제 값은 숨겨진 select에 유지 → 폼/겟Payload와 호환)
   */
  function createCustomSelect(select) {
    const container = select.parentElement;
    const wrapper = document.createElement('div');
    wrapper.className = 'relative';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className =
      'w-full rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-left text-gray-900 hover:border-indigo-300 focus:outline-none focus:ring-2 focus:ring-indigo-300 dark:border-strokedark dark:bg-meta-4 dark:text-gray-200 dark:hover:border-indigo-500/40 dark:focus:ring-indigo-400/40';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');

    const triggerInner = document.createElement('div');
    triggerInner.className = 'flex items-center justify-between';

    const triggerText = document.createElement('span');
    triggerText.className = 'truncate';
    const arrow = document.createElement('span');
    arrow.className = 'ml-3 shrink-0 text-gray-500 dark:text-gray-400';
    arrow.textContent = '▾';

    triggerInner.appendChild(triggerText);
    triggerInner.appendChild(arrow);
    trigger.appendChild(triggerInner);

    const menu = document.createElement('div');
    menu.className =
      'absolute left-0 top-full z-[10001] mt-1 hidden max-h-64 w-full overflow-auto rounded-lg border border-gray-200 bg-white shadow-lg dark:border-strokedark dark:bg-boxdark dark:text-gray-200 dark:shadow-black/40';
    menu.setAttribute('role', 'listbox');

    Array.from(select.options).forEach((option) => {
      const item = document.createElement('button');
      item.type = 'button';
      item.dataset.value = option.value;
      item.disabled = option.disabled;
      item.className =
        'block w-full px-4 py-2 text-left text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:text-gray-300 dark:text-gray-300 dark:hover:bg-white/10 dark:disabled:text-gray-500';
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
        // 형제 필드 위에 겹치도록 열 때만 부모 z-index 상승
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

  /** 생성·수정 폼 내 모든 단일 select에 커스텀 UI 적용; 문서 클릭 시 드롭다운 닫기 */
  function initCustomSelects() {
    document
      .querySelectorAll('#createBoardForm select:not([multiple]), #editBoardForm select:not([multiple])')
      .forEach((select) => createCustomSelect(select));

    document.addEventListener('click', () => closeAllCustomSelects());
  }

  function getCheckedRadioValue(name) {
    return document.querySelector(`input[name="${name}"]:checked`)?.value;
  }

  /** data-multi-group 체크박스 중 선택된 값들을 숫자 배열로 (부서/직급 다중 선택용) */
  function getSelectedNumberValues(id) {
    return Array.from(document.querySelectorAll(`input[data-multi-group="${id}"]:checked`))
      .map((checkbox) => Number(checkbox.value))
      .filter((value) => Number.isFinite(value) && value > 0);
  }

  /** 해당 그룹의 첫 체크박스 value — 제한 미선택 시 API용 기본 부서/직급 후보 */
  function getFirstAvailableGroupValue(id) {
    const firstCheckbox = document.querySelector(`input[data-multi-group="${id}"]`);
    const value = Number(firstCheckbox?.value || 0);
    return Number.isFinite(value) && value > 0 ? value : null;
  }

  function setSingleGroupSelection(id, rawValue) {
    const value = String(rawValue || '');
    document.querySelectorAll(`input[data-multi-group="${id}"]`).forEach((checkbox) => {
      checkbox.checked = value !== '' && checkbox.value === value;
    });
  }

  /**
   * "전체 선택" 체크박스 ↔ 같은 그룹의 개별 체크박스 연동 (캘린더 공유 부서 선택과 동일 동작)
   * - 전체 선택 체크: 개별 체크박스 모두 uncheck (disable 하지 않음 — 바로 다른 부서 클릭 가능)
   *   제출 시 개별이 빈 배열 → 백엔드 ALL 해석은 그대로 유지
   * - 사용자가 개별 체크박스를 켜면 → 전체 선택 자동 해제 + 해당 부서만 단독 선택 가능
   * - 모든 개별 체크박스가 비면 → 전체 선택 자동 복귀(체크)
   */
  function initSelectAllControls() {
    document.querySelectorAll('input[data-select-all]').forEach((selectAll) => {
      const group = selectAll.dataset.selectAll;
      const individuals = document.querySelectorAll(`input[data-multi-group="${group}"]`);

      selectAll.addEventListener('change', () => {
        if (selectAll.checked) {
          individuals.forEach((cb) => { cb.checked = false; });
        }
      });

      individuals.forEach((cb) => {
        cb.addEventListener('change', () => {
          if (cb.checked) {
            selectAll.checked = false;
          } else {
            const anyChecked = Array.from(individuals).some((x) => x.checked);
            if (!anyChecked) selectAll.checked = true;
          }
        });
      });
    });
  }

  /**
   * data-toggle-target 으로 연결된 패널 표시/숨김 (권한 설정·기타 설정·부서/직급 접기 등)
   * data-toggle-arrow 에 rotate-180 으로 열림 상태 표시
   */
  function initPermissionPanelToggles() {
    document.querySelectorAll('button[data-toggle-target]').forEach((button) => {
      const panelId = button.getAttribute('data-toggle-target');
      const panel = panelId ? document.getElementById(panelId) : null;
      const arrow = button.querySelector('[data-toggle-arrow]');
      if (!panel) return;

      button.addEventListener('click', () => {
        const willOpen = panel.classList.contains('hidden');
        panel.classList.toggle('hidden', !willOpen);
        button.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
        if (arrow) {
          arrow.classList.toggle('rotate-180', willOpen);
        }
      });
    });
  }

  /**
   * 생성 폼: 라디오 선택 ↔ 숨김 input(readScope / writeScope / commentScope) 및 제한 시 부서·직급 블록 표시
   * - 읽기/쓰기: RESTRICTED 선택 시 제한 UI 표시
   * - 댓글: RESTRICTED 일 때만 제한 UI 표시, NONE 은 댓글 비활성
   */
  function initCreatePermissionControls() {
    const readScope = document.getElementById('readScope');
    const writeScope = document.getElementById('writeScope');
    const commentScope = document.getElementById('commentScope');
    const readLimitedOptions = document.getElementById('readLimitedOptions');
    const writeLimitedOptions = document.getElementById('writeLimitedOptions');
    const commentLimitedOptions = document.getElementById('commentLimitedOptions');

    if (!readScope || !writeScope || !commentScope) {
      return;
    }

    const syncReadScope = () => {
      const option = getCheckedRadioValue('readScopeOption') || 'ALL';
      const isLimited = option === 'RESTRICTED';
      readScope.value = option;
      readLimitedOptions?.classList.toggle('hidden', !isLimited);
    };

    const syncWriteScope = () => {
      const option = getCheckedRadioValue('writeScopeOption') || 'ALL';
      const isLimited = option === 'RESTRICTED';
      writeLimitedOptions?.classList.toggle('hidden', !isLimited);
      writeScope.value = option;
    };

    const syncCommentScope = () => {
      const option = getCheckedRadioValue('commentScopeOption') || 'ALL';
      commentLimitedOptions?.classList.toggle('hidden', option !== 'RESTRICTED');
      // 현재 DB 체크 제약조건과의 호환을 위해 댓글 비활성(NONE)은 ALL로 저장
      commentScope.value = option === 'NONE' ? 'ALL' : option;
    };

    document
      .querySelectorAll('input[name="readScopeOption"]')
      .forEach((radio) => radio.addEventListener('change', syncReadScope));
    document
      .querySelectorAll('input[name="writeScopeOption"]')
      .forEach((radio) => radio.addEventListener('change', syncWriteScope));
    document
      .querySelectorAll('input[name="commentScopeOption"]')
      .forEach((radio) => radio.addEventListener('change', syncCommentScope));

    syncReadScope();
    syncWriteScope();
    syncCommentScope();
  }

  function initEditPermissionControls() {
    const readScope = document.getElementById('editReadScope');
    const writeScope = document.getElementById('editWriteScope');
    const commentScope = document.getElementById('editCommentScope');
    const readLimitedOptions = document.getElementById('editReadLimitedOptions');
    const writeLimitedOptions = document.getElementById('editWriteLimitedOptions');
    const commentLimitedOptions = document.getElementById('editCommentLimitedOptions');

    if (!readScope || !writeScope || !commentScope) {
      return;
    }

    const syncReadScope = () => {
      const option = getCheckedRadioValue('editReadScopeOption') || 'ALL';
      readScope.value = option;
      readLimitedOptions?.classList.toggle('hidden', option !== 'RESTRICTED');
    };

    const syncWriteScope = () => {
      const option = getCheckedRadioValue('editWriteScopeOption') || 'ALL';
      writeScope.value = option;
      writeLimitedOptions?.classList.toggle('hidden', option !== 'RESTRICTED');
    };

    const syncCommentScope = () => {
      const option = getCheckedRadioValue('editCommentScopeOption') || 'ALL';
      commentScope.value = option === 'NONE' ? 'ALL' : option;
      commentLimitedOptions?.classList.toggle('hidden', option !== 'RESTRICTED');
    };

    document
      .querySelectorAll('input[name="editReadScopeOption"]')
      .forEach((radio) => radio.addEventListener('change', syncReadScope));
    document
      .querySelectorAll('input[name="editWriteScopeOption"]')
      .forEach((radio) => radio.addEventListener('change', syncWriteScope));
    document
      .querySelectorAll('input[name="editCommentScopeOption"]')
      .forEach((radio) => radio.addEventListener('change', syncCommentScope));

    syncReadScope();
    syncWriteScope();
    syncCommentScope();
  }

  /**
   * 게시판 생성 제출
   * 권한별로 부서/직급을 다중 선택 가능. 0개 선택 = 전체 허용.
   * 권한 라디오가 "제한"이어도 부서/직급이 비어있으면 백엔드가 ALL로 저장한다.
   */
  async function onCreateSubmit(e) {
    e.preventDefault();
    const payload = getPayload('');
    if (!payload.boardName) {
      alert('게시판명을 입력해주세요.');
      document.getElementById('boardName')?.focus();
      return;
    }
    const readScopeOption = getCheckedRadioValue('readScopeOption') || 'ALL';
    const writeScopeOption = getCheckedRadioValue('writeScopeOption') || 'ALL';
    const commentScopeOption = getCheckedRadioValue('commentScopeOption') || 'ALL';

    // 라디오가 "제한"이 아닌 경우엔 빈 배열 → 백엔드는 ALL로 인식
    payload.readDeptIds = readScopeOption === 'DEPARTMENT' ? getSelectedNumberValues('deptId') : [];
    payload.readPositionIds = readScopeOption === 'DEPARTMENT' ? getSelectedNumberValues('positionId') : [];
    payload.writeDeptIds = writeScopeOption === 'LIMITED' ? getSelectedNumberValues('writeDeptId') : [];
    payload.writePositionIds = writeScopeOption === 'LIMITED' ? getSelectedNumberValues('writePositionId') : [];
    payload.commentDeptIds = commentScopeOption === 'DEPARTMENT' ? getSelectedNumberValues('commentDeptId') : [];
    payload.commentPositionIds = commentScopeOption === 'DEPARTMENT' ? getSelectedNumberValues('commentPositionId') : [];

    try {
      const response = await fetch('/api/admin/board/create', {
        method: 'POST',
        headers: headers(),
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(await window.getApiErrorMessage(response, '게시판 생성에 실패했습니다.'));
      }

      alert('게시판이 생성되었습니다.');
      location.reload();
    } catch (error) {
      alert(error?.message || '요청에 실패했습니다.');
    }
  }

  /** 목록 행 버튼의 data-* 로 수정 모달 필드를 채우고 표시 */
  function openEditModal(button) {
    const d = button?.dataset || {};
    document.getElementById('editBoardId').value = d.boardId || '';
    document.getElementById('editBoardName').value = d.boardName || '';
    document.getElementById('editBoardType').value = d.boardType || 'NOTICE';
    document.getElementById('editViewType').value = d.viewType || 'LIST';
    document.getElementById('editAnonymousType').value = d.anonymousType || 'NAME';

    const readScope = d.readScope || 'ALL';
    const writeScope = d.writeScope || 'ALL';
    const commentScope = d.commentScope || 'ALL';
    const readRadio = document.querySelector(`input[name="editReadScopeOption"][value="${readScope}"]`);
    const writeRadio = document.querySelector(`input[name="editWriteScopeOption"][value="${writeScope}"]`);
    const commentRadio = document.querySelector(`input[name="editCommentScopeOption"][value="${commentScope}"]`);
    if (readRadio) readRadio.checked = true;
    if (writeRadio) writeRadio.checked = true;
    if (commentRadio) commentRadio.checked = true;

    setSingleGroupSelection('editDeptId', d.deptId);
    setSingleGroupSelection('editPositionId', d.positionId);
    setSingleGroupSelection('editWriteDeptId', d.deptId);
    setSingleGroupSelection('editWritePositionId', d.positionId);
    setSingleGroupSelection('editCommentDeptId', d.deptId);
    setSingleGroupSelection('editCommentPositionId', d.positionId);

    document.getElementById('editBoardType').dispatchEvent(new Event('change', { bubbles: true }));
    document.getElementById('editViewType').dispatchEvent(new Event('change', { bubbles: true }));
    document.getElementById('editAnonymousType').dispatchEvent(new Event('change', { bubbles: true }));
    readRadio?.dispatchEvent(new Event('change', { bubbles: true }));
    writeRadio?.dispatchEvent(new Event('change', { bubbles: true }));
    commentRadio?.dispatchEvent(new Event('change', { bubbles: true }));
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

    const payload = getPayload('edit');
    const readScopeOption = getCheckedRadioValue('editReadScopeOption') || 'ALL';
    const writeScopeOption = getCheckedRadioValue('editWriteScopeOption') || 'ALL';
    const commentScopeOption = getCheckedRadioValue('editCommentScopeOption') || 'ALL';
    const readDeptIds = getSelectedNumberValues('editDeptId');
    const readPositionIds = getSelectedNumberValues('editPositionId');
    const writeDeptIds = getSelectedNumberValues('editWriteDeptId');
    const writePositionIds = getSelectedNumberValues('editWritePositionId');
    const commentDeptIds = getSelectedNumberValues('editCommentDeptId');
    const commentPositionIds = getSelectedNumberValues('editCommentPositionId');
    const limitedSelections = [];

    if (!payload.boardName) {
      alert('게시판명을 입력해주세요.');
      document.getElementById('editBoardName')?.focus();
      return;
    }

    if (readScopeOption === 'RESTRICTED') {
      if (!readDeptIds.length && !readPositionIds.length) {
        alert('읽기 권한 제한을 선택한 경우 부서 또는 직급을 선택해주세요.');
        return;
      }
      limitedSelections.push({ deptId: readDeptIds[0] || null, positionId: readPositionIds[0] || null });
    }

    if (writeScopeOption === 'RESTRICTED') {
      if (!writeDeptIds.length && !writePositionIds.length) {
        alert('쓰기 권한 제한을 선택한 경우 부서 또는 직급을 선택해주세요.');
        return;
      }
      limitedSelections.push({ deptId: writeDeptIds[0] || null, positionId: writePositionIds[0] || null });
    }

    if (commentScopeOption === 'RESTRICTED') {
      if (!commentDeptIds.length && !commentPositionIds.length) {
        alert('댓글 권한 제한을 선택한 경우 부서 또는 직급을 선택해주세요.');
        return;
      }
      limitedSelections.push({ deptId: commentDeptIds[0] || null, positionId: commentPositionIds[0] || null });
    }

    if (limitedSelections.length > 1) {
      const deptCandidates = limitedSelections.map((selection) => selection.deptId).filter(Boolean);
      const positionCandidates = limitedSelections.map((selection) => selection.positionId).filter(Boolean);
      const resolvedDeptId = deptCandidates[0] || null;
      const resolvedPositionId = positionCandidates[0] || null;
      const hasDeptMismatch = deptCandidates.some((deptId) => deptId !== resolvedDeptId);
      const hasPositionMismatch = positionCandidates.some((positionId) => positionId !== resolvedPositionId);

      if (hasDeptMismatch || hasPositionMismatch) {
        alert('현재 시스템은 권한별 부서/직급을 따로 저장하지 않습니다. 제한 권한들의 부서/직급을 동일하게 선택해주세요.');
        return;
      }
    }

    if (limitedSelections.length > 0) {
      const deptCandidates = limitedSelections.map((selection) => selection.deptId).filter(Boolean);
      const positionCandidates = limitedSelections.map((selection) => selection.positionId).filter(Boolean);
      payload.deptId = deptCandidates[0] || null;
      payload.positionId = positionCandidates[0] || null;
    } else {
      payload.deptId = getFirstAvailableGroupValue('editDeptId');
      payload.positionId = getFirstAvailableGroupValue('editPositionId');
    }

    if (!payload.deptId) {
      payload.deptId = getFirstAvailableGroupValue('editDeptId');
    }
    if (!payload.positionId) {
      payload.positionId = getFirstAvailableGroupValue('editPositionId');
    }

    if (!payload.deptId || !payload.positionId) {
      alert('부서와 직급 정보를 확인해주세요.');
      return;
    }

    try {
      const response = await fetch(`/api/admin/board/update/${payload.boardId}`, {
        method: 'POST',
        headers: headers(),
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        throw new Error(await window.getApiErrorMessage(response, '게시판 수정에 실패했습니다.'));
      }

      alert('게시판이 수정되었습니다.');
      location.reload();
    } catch (error) {
      alert(error?.message || '수정 API가 아직 구현되지 않았거나 요청에 실패했습니다.');
    }
  }

  async function deleteBoard(boardId) {
    if (!confirm('정말 삭제하시겠습니까?')) {
      return;
    }

    try {
      const response = await fetch(`/api/admin/board/delete/${boardId}`, {
        method: 'POST',
        headers: headers(),
      });

      if (!response.ok) {
        throw new Error(await window.getApiErrorMessage(response, '삭제 실패'));
      }

      alert('게시판이 삭제되었습니다.');
      location.reload();
    } catch (error) {
      alert(error?.message || '삭제 API가 아직 구현되지 않았거나 요청에 실패했습니다.');
    }
  }

  // Thymeleaf onclick / th:attr 로 호출
  window.openEditModal = openEditModal;
  window.closeEditModal = closeEditModal;
  window.deleteBoard = deleteBoard;

  document.addEventListener('DOMContentLoaded', () => {
    initCustomSelects();
    initPermissionPanelToggles();
    initCreatePermissionControls();
    initEditPermissionControls();
    initSelectAllControls();
    document.getElementById('createBoardForm')?.addEventListener('submit', onCreateSubmit);
    document.getElementById('editBoardForm')?.addEventListener('submit', onEditSubmit);

    // 수정 모달: 배경(딤) 클릭 시 닫기 — calendar 의 .modal-close-btn 과 동일
    document.querySelectorAll('#editModal .modal-close-btn').forEach((btn) => {
      btn.addEventListener('click', closeEditModal);
    });
  });
})();
