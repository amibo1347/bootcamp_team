// 휴가 관리 페이지 — 회사 기본정책(계층1) + 직원별 조정(계층2)
function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    return { token, header };
}

function currentYear() {
    const el = document.getElementById('currentYear');
    return el ? parseInt(el.value, 10) : null;
}

// 연도 변경 → 페이지 새로고침
function changeYear(year) {
    window.location.href = `/admin/leave/list?year=${year}`;
}

// ===== 정렬 / 부서·직급 필터 (클라이언트) =====
// 기본 정렬: 직급 레벨 내림차순 → 대표가 맨 위.
let leaveSortKey = 'level';
let leaveSortType = 'num';
let leaveSortDir = -1; // 1=오름차순, -1=내림차순
let leaveDeptFilter = '';
let leavePositionFilter = '';

function leaveRows() {
    return Array.from(document.querySelectorAll('#leaveListContainer tbody tr[data-member-id]'));
}

// 부서 필터 select 옵션을 행에서 추출해 채운다(현재 선택 유지).
function buildDeptOptions() {
    const sel = document.getElementById('deptFilter');
    if (!sel) return;
    const depts = [...new Set(leaveRows().map((r) => r.dataset.dept).filter((d) => d))]
        .sort((a, b) => a.localeCompare(b, 'ko'));
    sel.innerHTML = '<option value="">부서(전체)</option>'
        + depts.map((d) => `<option value="${d}">${d}</option>`).join('');
    sel.value = leaveDeptFilter;
}

// 직급 필터 select 옵션 — 직급 레벨 내림차순(대표 → 사원)으로 정렬.
function buildPositionOptions() {
    const sel = document.getElementById('positionFilter');
    if (!sel) return;
    const levelByName = new Map();
    leaveRows().forEach((r) => {
        const p = r.dataset.position;
        if (p && !levelByName.has(p)) levelByName.set(p, parseFloat(r.dataset.level));
    });
    const names = [...levelByName.keys()].sort((a, b) => {
        const la = isNaN(levelByName.get(a)) ? -Infinity : levelByName.get(a);
        const lb = isNaN(levelByName.get(b)) ? -Infinity : levelByName.get(b);
        if (la !== lb) return lb - la; // 레벨 내림차순
        return a.localeCompare(b, 'ko');
    });
    sel.innerHTML = '<option value="">직급(전체)</option>'
        + names.map((n) => `<option value="${n}">${n}</option>`).join('');
    sel.value = leavePositionFilter;
}

function filterByDept() {
    const sel = document.getElementById('deptFilter');
    leaveDeptFilter = sel ? sel.value : '';
    applyLeaveFilter();
}

function filterByPosition() {
    const sel = document.getElementById('positionFilter');
    leavePositionFilter = sel ? sel.value : '';
    applyLeaveFilter();
}

function applyLeaveFilter() {
    leaveRows().forEach((r) => {
        const okDept = (!leaveDeptFilter || r.dataset.dept === leaveDeptFilter);
        const okPos = (!leavePositionFilter || r.dataset.position === leavePositionFilter);
        const visible = okDept && okPos;
        r.style.display = visible ? '' : 'none';
        if (!visible) {
            const cb = r.querySelector('.leave-row-check');
            if (cb) cb.checked = false; // 숨겨지는 행은 선택 해제
        }
    });
    const all = document.getElementById('leaveSelectAll');
    if (all) all.checked = false; // 필터 변경 시 전체선택 초기화
}

function rowSortValue(r, key, type) {
    if (key === 'granted') {
        const inp = r.querySelector('input[id^="granted-"]');
        const n = parseFloat(inp ? inp.value : '');
        return isNaN(n) ? -Infinity : n;
    }
    const raw = r.dataset[key] != null ? r.dataset[key] : '';
    if (type === 'num') {
        const n = parseFloat(raw);
        return isNaN(n) ? -Infinity : n;
    }
    return String(raw);
}

function sortLeave(btn) {
    const key = btn.dataset.key;
    const type = btn.dataset.type || 'text';
    if (leaveSortKey === key) {
        leaveSortDir = -leaveSortDir;
    } else {
        leaveSortKey = key;
        leaveSortType = type;
        leaveSortDir = 1;
    }
    applyLeaveSort();
    updateSortIndicators();
}

function applyLeaveSort() {
    if (!leaveSortKey) return;
    const tbody = document.querySelector('#leaveListContainer tbody');
    if (!tbody) return;
    const rows = leaveRows();
    rows.sort((a, b) => {
        const va = rowSortValue(a, leaveSortKey, leaveSortType);
        const vb = rowSortValue(b, leaveSortKey, leaveSortType);
        if (leaveSortType === 'num') return (va - vb) * leaveSortDir;
        return String(va).localeCompare(String(vb), 'ko') * leaveSortDir;
    });
    rows.forEach((r) => tbody.appendChild(r));
}

function updateSortIndicators() {
    document.querySelectorAll('#leaveListContainer .leave-sort').forEach((btn) => {
        const ind = btn.querySelector('.sort-ind');
        if (!ind) return;
        ind.textContent = (btn.dataset.key === leaveSortKey)
            ? (leaveSortDir === 1 ? ' ▲' : ' ▼') : '';
    });
}

// 컨테이너 부분 교체 후 항상 호출 — 부서/직급 옵션 재생성 + 필터/정렬 재적용.
function setLeaveContainer(html) {
    document.getElementById('leaveListContainer').innerHTML = html;
    buildDeptOptions();
    buildPositionOptions();
    applyLeaveFilter();
    applyLeaveSort();
    updateSortIndicators();
}

document.addEventListener('DOMContentLoaded', () => {
    buildDeptOptions();
    buildPositionOptions();
    applyLeaveSort();        // 기본: 직급 레벨 내림차순(대표 최상단)
    updateSortIndicators();
});

// 계층1: 회사 기본 부여일수 저장
async function saveCompanyDefault() {
    const input = document.getElementById('companyDefaultInput');
    const days = parseFloat(input.value);

    if (isNaN(days) || days < 0) {
        alert('기본 부여일수는 0 이상 숫자여야 합니다.');
        return;
    }

    const { token, header } = getCsrfToken();
    try {
        const response = await fetch('/api/admin/leave/default', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', [header]: token },
            body: JSON.stringify({ days: days, year: currentYear() })
        });

        if (response.ok) {
            setLeaveContainer(await response.text());
            alert('회사 기본 연차가 저장되었습니다.');
        } else {
            alert(await window.getApiErrorMessage(response, '저장 실패'));
        }
    } catch (error) {
        console.error(error);
    }
}

// 선택된 직원 memberId 목록
// 행이 부서 필터로 숨겨졌는지
function isRowHidden(cb) {
    const tr = cb.closest('tr');
    return tr && tr.style.display === 'none';
}

function getCheckedMemberIds() {
    return Array.from(document.querySelectorAll('.leave-row-check:checked:not(:disabled)'))
        .filter((cb) => !isRowHidden(cb)) // 필터로 가려진 행은 제외
        .map((cb) => parseInt(cb.value, 10))
        .filter((id) => !isNaN(id));
}

// 전체 선택 토글 (컨테이너가 부분 교체되므로 위임으로 처리).
// 수정 불가(비활성) 행 + 부서 필터로 숨겨진 행은 제외 → 현재 보이는 직원만 선택.
document.addEventListener('change', (e) => {
    if (e.target && e.target.id === 'leaveSelectAll') {
        const checked = e.target.checked;
        document.querySelectorAll('.leave-row-check:not(:disabled)').forEach((cb) => {
            if (isRowHidden(cb)) return;
            cb.checked = checked;
        });
    }
});

// 선택 직원에게 부여 연차 일괄 적용
async function applyBulkGranted() {
    const memberIds = getCheckedMemberIds();
    if (memberIds.length === 0) {
        alert('적용할 직원을 한 명 이상 선택하세요.');
        return;
    }
    const granted = parseFloat(document.getElementById('bulkGrantedInput').value);
    if (isNaN(granted) || granted < 0) {
        alert('부여 연차는 0 이상 숫자여야 합니다.');
        return;
    }
    if (!confirm(`선택한 ${memberIds.length}명에게 부여 연차 ${granted}일을 일괄 적용할까요?`)) return;

    const { token, header } = getCsrfToken();
    try {
        const response = await fetch('/api/admin/leave/bulk', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', [header]: token },
            body: JSON.stringify({ memberIds, granted, year: currentYear() })
        });
        if (response.ok) {
            setLeaveContainer(await response.text());
            alert('일괄 적용되었습니다.');
        } else {
            alert(await window.getApiErrorMessage(response, '일괄 적용 실패'));
        }
    } catch (error) {
        console.error(error);
    }
}

// 계층2: 직원 1명 부여 연차 저장
async function saveMemberLeave(memberId) {
    const granted = parseFloat(document.getElementById(`granted-${memberId}`).value);
    const note = document.getElementById(`note-${memberId}`).value;

    if (isNaN(granted) || granted < 0) {
        alert('부여 연차는 0 이상 숫자여야 합니다.');
        return;
    }

    const { token, header } = getCsrfToken();
    try {
        const response = await fetch(`/api/admin/leave/member/${memberId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', [header]: token },
            body: JSON.stringify({ granted, note, year: currentYear() })
        });

        if (response.ok) {
            setLeaveContainer(await response.text());
            alert('저장되었습니다.');
        } else {
            alert(await window.getApiErrorMessage(response, '저장 실패'));
        }
    } catch (error) {
        console.error(error);
    }
}
