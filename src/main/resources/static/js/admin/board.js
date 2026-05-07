/**
 * admin/managingBoard.html — 게시판 생성·수정·삭제용 스크립트
 *
 * - 생성 폼: 숨김 필드(readScope 등) + 라디오/체크박스 UI 동기화 후 POST /api/admin/board/create
 * - 수정 모달: 목록의 data-* 로 폼 채움 후 PUT /api/admin/board/update
 * - 삭제: DELETE /api/admin/board/delete/{id}
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
    const fieldId = (name) => (prefix ? `${prefix}${name}` : `${name.charAt(0).toLowerCase()}${name.slice(1)}`);

    return {
      boardId: Number(getValue(fieldId('BoardId')) || 0) || null,
      boardName: (getValue(fieldId('BoardName')) || '').trim(),
      boardType: getValue(fieldId('BoardType')),
      deptId: Number(getValue(fieldId('DeptId'))),
      positionId: Number(getValue(fieldId('PositionId'))),
      viewType: getValue(fieldId('ViewType')) || 'LIST',
      readScope: getValue(fieldId('ReadScope')) || 'ALL',
      writeScope: getValue(fieldId('WriteScope')) || 'ALL',
      commentScope: getValue(fieldId('CommentScope')) || 'ALL',
      anonymousType: getValue(fieldId('AnonymousType')) || 'NAME',
      isActive: getChecked(fieldId('IsActive')),
      isAiUse: getChecked(fieldId('IsAiUse')),
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

  /**
   * 게시판 생성 제출
   * 백엔드는 Board 한 건에 deptId·positionId 하나만 저장하므로,
   * 여러 권한을 제한으로 켠 경우 부서/직급이 모두 동일한지 검사 후 첫 쌍만 전송
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
    const readDeptIds = getSelectedNumberValues('deptId');
    const readPositionIds = getSelectedNumberValues('positionId');
    const writeDeptIds = getSelectedNumberValues('writeDeptId');
    const writePositionIds = getSelectedNumberValues('writePositionId');
    const commentDeptIds = getSelectedNumberValues('commentDeptId');
    const commentPositionIds = getSelectedNumberValues('commentPositionId');
    const limitedSelections = [];

    if (readScopeOption === 'RESTRICTED') {
      if (!readDeptIds.length || !readPositionIds.length) {
        alert('읽기 권한 제한을 선택한 경우 부서와 직급을 모두 선택해주세요.');
        return;
      }
      limitedSelections.push({ deptId: readDeptIds[0], positionId: readPositionIds[0], source: '읽기 권한' });
    }

    if (writeScopeOption === 'RESTRICTED') {
      if (!writeDeptIds.length || !writePositionIds.length) {
        alert('쓰기 권한 제한을 선택한 경우 부서와 직급을 모두 선택해주세요.');
        return;
      }
      limitedSelections.push({ deptId: writeDeptIds[0], positionId: writePositionIds[0], source: '쓰기 권한' });
    }

    if (commentScopeOption === 'RESTRICTED') {
      if (!commentDeptIds.length || !commentPositionIds.length) {
        alert('댓글 권한 제한을 선택한 경우 부서와 직급을 모두 선택해주세요.');
        return;
      }
      limitedSelections.push({ deptId: commentDeptIds[0], positionId: commentPositionIds[0], source: '댓글 권한' });
    }

    if (limitedSelections.length > 1) {
      const first = limitedSelections[0];
      const hasMismatch = limitedSelections.slice(1).some(
        (selection) => selection.deptId !== first.deptId || selection.positionId !== first.positionId,
      );
      if (hasMismatch) {
        alert('현재 시스템은 권한별 부서/직급을 따로 저장하지 않습니다. 제한 권한들의 부서/직급을 동일하게 선택해주세요.');
        return;
      }
    }

    if (limitedSelections.length > 0) {
      payload.deptId = limitedSelections[0].deptId;
      payload.positionId = limitedSelections[0].positionId;
    } else {
      payload.deptId = getFirstAvailableGroupValue('deptId');
      payload.positionId = getFirstAvailableGroupValue('positionId');
    }

    if (!payload.deptId || !payload.positionId) {
      alert('부서와 직급 정보를 확인해주세요.');
      return;
    }

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

  /** 목록 행 버튼의 data-* 로 수정 모달 필드를 채우고 표시 */
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

  /**
   * 수정 저장 — 현재 폼에서 가져온 값 외에 뷰/권한/익명은 코드상 고정값으로 덮어 API 스펙에 맞춤
   * (백엔드 구현 상태에 따라 일부 필드는 서버에서 무시될 수 있음)
   */
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
      const response = await fetch(`/api/admin/board/update/${payload.boardId}`, {
        method: 'POST',
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
        method: 'POST',
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

  // Thymeleaf onclick / th:attr 로 호출
  window.openEditModal = openEditModal;
  window.closeEditModal = closeEditModal;
  window.deleteBoard = deleteBoard;

  document.addEventListener('DOMContentLoaded', () => {
    initCustomSelects();
    initPermissionPanelToggles();
    initCreatePermissionControls();
    document.getElementById('createBoardForm')?.addEventListener('submit', onCreateSubmit);
    document.getElementById('editBoardForm')?.addEventListener('submit', onEditSubmit);
  });
})();
