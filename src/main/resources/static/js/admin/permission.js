// 권한 관리 페이지 (ADMIN 전용)
//  - 탭 1: 직급별 권한 부여. 한 직급에 대해 권한 체크박스를 모아 POST /api/admin/permission/{positionId}
//          백엔드가 권한 유무에 따라 Role(SUB_ADMIN/USER) 을 자동 전환. 대표(ADMIN) 직급은 컨트롤러에서 제외.
//  - 탭 2: 회원별 예외 권한 부여. 인사이동과 동일한 다중 선택 패턴.
//          부서/직급/이름 클라이언트 사이드 필터 적용.
//          POST /api/admin/permission/members 로 일괄 교체.
//  - 두 영역 모두 ADMIN 외에는 백엔드 PreAuthorize 로 403 차단됨.

// ============================================================================
// 탭 전환 (직급별 / 회원별)
// ============================================================================

window.switchPermissionTab = (tab) => {
    // 패널 표시/숨김
    document.getElementById('permTabPosition').classList.toggle('hidden', tab !== 'position');
    document.getElementById('permTabMember').classList.toggle('hidden', tab !== 'member');

    // 탭 버튼 활성 스타일 토글
    document.querySelectorAll('.js-perm-tab').forEach((btn) => {
        const active = btn.dataset.permTab === tab;
        btn.classList.toggle('border-indigo-500', active);
        btn.classList.toggle('text-indigo-600', active);
        btn.classList.toggle('dark:text-indigo-300', active);
        btn.classList.toggle('border-transparent', !active);
        btn.classList.toggle('text-gray-500', !active);
        btn.classList.toggle('dark:text-gray-400', !active);
    });
};

// ============================================================================
// 좌측: 직급별 권한 저장
// ============================================================================

window.savePermissions = async (button) => {
    const positionId = button.dataset.positionId;
    if (!positionId) return;

    const row = document.getElementById(`perm-row-${positionId}`);
    if (!row) return;

    const checked = Array.from(row.querySelectorAll(`input[name="perm-${positionId}"]:checked`))
        .map((c) => c.value);

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    const params = new URLSearchParams();
    checked.forEach((p) => params.append('permissions', p));

    try {
        const response = await fetch(`/api/admin/permission/${positionId}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                [header]: token
            },
            body: params.toString()
        });

        if (response.ok) {
            // 권한 부여/해제에 따라 Role 이 자동 전환되었으므로 뱃지를 새로 그리려면 새로고침이 필요.
            alert('직급 권한이 저장되었습니다. 페이지를 새로고침합니다.');
            window.location.reload();
        } else {
            // ErrorCode.message 를 그대로 노출 — 공통 헬퍼로 통일.
            alert(await window.getApiErrorMessage(response, '권한 저장에 실패했습니다.'));
        }
    } catch (e) {
        console.error('Permission save error:', e);
        alert('서버와 통신 중 오류가 발생했습니다.');
    }
};

// ============================================================================
// 우측: 회원별 예외 권한 일괄 부여 (인사이동과 동일한 패턴)
// ============================================================================

/**
 * 보이는 회원 행만 반환 (검색 필터로 hidden 된 것 제외).
 * 전체선택/체크 정합성 계산에 사용.
 */
function getVisibleMemberChecks() {
    return Array.from(document.querySelectorAll('.js-member-check'))
        .filter((c) => {
            const row = c.closest('.js-member-row');
            return row && !row.classList.contains('hidden');
        });
}

/** 전체 선택 체크박스 클릭: 검색 결과로 보이는 회원만 동기화 (필터 OFF 된 행은 손대지 않음). */
window.onMemberSelectAll = (master) => {
    const checked = !!master.checked;
    getVisibleMemberChecks().forEach((c) => { c.checked = checked; });
    updateMemberSelectedCount();
};

/** 개별 회원 체크박스 클릭: 전체선택 상태 + 카운트 동기화. */
window.onMemberCheck = () => {
    const visible = getVisibleMemberChecks();
    const checkedCount = visible.filter((c) => c.checked).length;
    const master = document.querySelector('#memberSelectAll');
    if (master) master.checked = visible.length > 0 && checkedCount === visible.length;
    updateMemberSelectedCount();
};

function updateMemberSelectedCount() {
    const label = document.querySelector('#memberSelectedCount');
    if (!label) return;
    // 검색 필터와 무관하게 전체 체크된 회원 수를 표시 — 필터 변경 시 사용자가 이전 선택을 잃었다고 오해하지 않도록.
    const count = document.querySelectorAll('.js-member-check:checked').length;
    label.textContent = `선택 ${count}명`;
}

// ============================================================================
// 회원 검색 필터 (부서 / 직급 / 이름)
//  - 모두 클라이언트 사이드. 회원 행에 박힌 data-* 속성 기준으로 show/hide.
//  - select 옵션은 회원 데이터에서 unique 값으로 동적 생성 (컨트롤러 변경 없이 동작).
// ============================================================================

/** 회원 행 data-* 에서 unique 부서/직급 옵션을 추출해 select 에 채운다. */
function initMemberSearchOptions() {
    const deptSelect = document.getElementById('memberSearchDept');
    const posSelect = document.getElementById('memberSearchPosition');
    if (!deptSelect || !posSelect) return;

    const rows = document.querySelectorAll('.js-member-row');
    const depts = new Set();
    const positions = new Set();
    rows.forEach((row) => {
        const d = (row.dataset.dept || '').trim();
        const p = (row.dataset.position || '').trim();
        if (d) depts.add(d);
        if (p) positions.add(p);
    });

    // 정렬 후 옵션 추가 (기존 "전체" 옵션은 유지).
    Array.from(depts).sort((a, b) => a.localeCompare(b, 'ko')).forEach((name) => {
        const opt = document.createElement('option');
        opt.value = name;
        opt.textContent = name;
        deptSelect.appendChild(opt);
    });
    Array.from(positions).sort((a, b) => a.localeCompare(b, 'ko')).forEach((name) => {
        const opt = document.createElement('option');
        opt.value = name;
        opt.textContent = name;
        posSelect.appendChild(opt);
    });
}

/** 검색 조건 변경 시 호출 — 행을 show/hide 만 한다 (체크 상태는 보존). */
window.filterMemberList = () => {
    const deptVal = (document.getElementById('memberSearchDept')?.value || '').trim();
    const posVal = (document.getElementById('memberSearchPosition')?.value || '').trim();
    const nameVal = (document.getElementById('memberSearchName')?.value || '').trim().toLowerCase();

    const rows = document.querySelectorAll('.js-member-row');
    let visibleCount = 0;
    rows.forEach((row) => {
        const d = (row.dataset.dept || '').trim();
        const p = (row.dataset.position || '').trim();
        const n = (row.dataset.name || '').trim().toLowerCase();

        const match =
            (!deptVal || d === deptVal) &&
            (!posVal || p === posVal) &&
            (!nameVal || n.includes(nameVal));

        row.classList.toggle('hidden', !match);
        if (match) visibleCount++;
    });

    // "검색 결과 없음" 메시지 토글
    const empty = document.getElementById('memberListEmptySearch');
    if (empty) empty.classList.toggle('hidden', visibleCount > 0);

    // 보이는 행 기준으로 전체선택 master 상태 재계산
    const visible = getVisibleMemberChecks();
    const checkedCount = visible.filter((c) => c.checked).length;
    const master = document.querySelector('#memberSelectAll');
    if (master) master.checked = visible.length > 0 && checkedCount === visible.length;
};

/** 검색 조건 초기화 (선택은 보존). */
window.resetMemberSearch = () => {
    const dept = document.getElementById('memberSearchDept');
    const pos = document.getElementById('memberSearchPosition');
    const name = document.getElementById('memberSearchName');
    if (dept) {
        dept.value = '';
        syncMemberFilterSelect('memberSearchDept');
    }
    if (pos) {
        pos.value = '';
        syncMemberFilterSelect('memberSearchPosition');
    }
    if (name) name.value = '';
    window.filterMemberList();
};

// ============================================================================
// 회원 검색 — DESIGN_RULES 5-2 커스텀 콤보박스 (managingBoard 패턴, 값은 숨긴 select 유지)
// ============================================================================

/** @type {Map<string, { select: HTMLSelectElement, trigger: HTMLButtonElement, triggerText: HTMLElement, menu: HTMLElement, container: HTMLElement | null }>} */
const memberFilterSelectMap = new Map();

/** 열린 회원 검색 콤보박스 모두 닫기 */
function closeMemberFilterSelects() {
    memberFilterSelectMap.forEach(({ menu, trigger, container }) => {
        menu.classList.add('hidden');
        trigger.setAttribute('aria-expanded', 'false');
        if (container) container.style.zIndex = '';
    });
}

/**
 * 숨긴 select 값 → 트리거 라벨·메뉴 선택 상태 동기화
 * @param {string} selectId
 */
function syncMemberFilterSelect(selectId) {
    const ui = memberFilterSelectMap.get(selectId);
    if (!ui) return;
    const { select, triggerText, menu } = ui;
    const selectedOption = select.options[select.selectedIndex];
    triggerText.textContent = selectedOption ? selectedOption.textContent : '';
    menu.querySelectorAll('[data-value]').forEach((btn) => {
        const isSelected = btn.getAttribute('data-value') === select.value;
        btn.classList.toggle('is-selected', isSelected);
    });
}

/**
 * 단일 native select → 커스텀 버튼+패널 (filterMemberList 는 select change 로 유지)
 * @param {HTMLSelectElement} select
 */
function createMemberFilterCustomSelect(select) {
    if (!select?.id || select.dataset.permCombobox === 'true') return;

    const container = select.parentElement;
    const wrapper = document.createElement('div');
    wrapper.className = 'relative';

    const trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'perm-filter-combobox-trigger';
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');

    const triggerText = document.createElement('span');
    triggerText.className = 'truncate';
    const arrow = document.createElement('span');
    arrow.className = 'form-combobox-arrow ml-2 shrink-0';
    arrow.setAttribute('aria-hidden', 'true');
    arrow.textContent = '▾';

    trigger.appendChild(triggerText);
    trigger.appendChild(arrow);

    const menu = document.createElement('div');
    menu.className = 'perm-filter-combobox-panel hidden';
    menu.setAttribute('role', 'listbox');

    Array.from(select.options).forEach((option) => {
        const item = document.createElement('button');
        item.type = 'button';
        item.dataset.value = option.value;
        item.className = 'perm-filter-combobox-option';
        item.textContent = option.textContent;
        item.addEventListener('click', () => {
            select.value = option.value;
            select.dispatchEvent(new Event('change', { bubbles: true }));
            closeMemberFilterSelects();
        });
        menu.appendChild(item);
    });

    trigger.addEventListener('click', (e) => {
        e.stopPropagation();
        const isOpen = !menu.classList.contains('hidden');
        closeMemberFilterSelects();
        if (!isOpen) {
            if (container) container.style.zIndex = '9999';
            menu.classList.remove('hidden');
            trigger.setAttribute('aria-expanded', 'true');
        }
    });

    select.insertAdjacentElement('afterend', wrapper);
    wrapper.appendChild(trigger);
    wrapper.appendChild(menu);

    select.dataset.permCombobox = 'true';
    memberFilterSelectMap.set(select.id, { select, trigger, triggerText, menu, container });
    select.addEventListener('change', () => syncMemberFilterSelect(select.id));
    syncMemberFilterSelect(select.id);
}

/** 부서·직급 검색 select 에 커스텀 UI 적용 (옵션 채운 뒤 호출) */
function initMemberFilterCustomSelects() {
    ['memberSearchDept', 'memberSearchPosition'].forEach((id) => {
        const el = document.getElementById(id);
        if (el instanceof HTMLSelectElement) createMemberFilterCustomSelect(el);
    });
    if (!window.__permFilterSelectClickBound) {
        document.addEventListener('click', closeMemberFilterSelects);
        window.__permFilterSelectClickBound = true;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    initMemberSearchOptions();
    initMemberFilterCustomSelects();
});

/**
 * [등록] 버튼: 선택 회원들의 예외 권한 일괄 교체.
 *  - 권한 체크 0개 + 회원 선택 1명 이상 → 해당 회원들의 예외 권한 모두 해제 (확인 후 진행)
 *  - 권한 체크 1개 이상 + 회원 선택 0명 → 진행 불가
 */
window.submitMemberPermissions = async () => {
    const selectedMembers = Array.from(document.querySelectorAll('.js-member-check:checked'))
        .map((c) => c.value)
        .filter((v) => v);
    if (selectedMembers.length === 0) {
        alert('권한을 부여할 회원을 선택하세요.');
        return;
    }

    const checkedPerms = Array.from(document.querySelectorAll('.js-member-perm:checked'))
        .map((c) => c.value);

    const message = checkedPerms.length === 0
        ? `선택된 ${selectedMembers.length}명의 모든 예외 권한을 해제합니다. 계속하시겠습니까?`
        : `선택된 ${selectedMembers.length}명의 예외 권한을 [${checkedPerms.length}개]로 설정합니다. 계속하시겠습니까?`;
    if (!confirm(message)) return;

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;

    const params = new URLSearchParams();
    selectedMembers.forEach((id) => params.append('memberIds', id));
    checkedPerms.forEach((p) => params.append('permissions', p));

    try {
        const response = await fetch('/api/admin/permission/members', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                [header]: token
            },
            body: params.toString()
        });

        if (response.ok) {
            alert('회원 예외 권한이 저장되었습니다. (변경 사항은 해당 회원이 다시 로그인하면 반영됩니다.)\n페이지를 새로고침합니다.');
            window.location.reload();
        } else {
            // ErrorCode.message 를 그대로 노출 — 공통 헬퍼로 통일.
            alert(await window.getApiErrorMessage(response, '권한 저장에 실패했습니다.'));
        }
    } catch (e) {
        console.error('Member permission save error:', e);
        alert('서버와 통신 중 오류가 발생했습니다.');
    }
};
