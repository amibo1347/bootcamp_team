// 근태 관리 — 회사 정책 CRUD + 일/주/월 보기.
//  - 정책: GET/POST /api/attendance/policy
//  - 일: GET /api/attendance/company/day?date=YYYY-MM-DD → 24시간 간트
//  - 주: GET /api/attendance/company/range?from&to → 직원×7일 셀
//  - 월: GET /api/attendance/company/range?from&to → 직원×N일 셀
//  - 부서/이름 필터는 클라이언트 사이드 (행 show/hide)
//  - 근무 일정 추가 UI 는 없음 — 일정 조회(/calendar) 에서 처리.

let viewMode = 'day'; // 'day' | 'week' | 'month'

/**
 * 상태별 색 (hex) — Tailwind output.css 에 모든 색 유틸리티가 빌드 안 되어 있을 수 있어
 * inline style 의 background-color 로 직접 적용한다.
 */
const STATUS_COLOR_HEX = {
    NORMAL: '#4f46e5',       // indigo-600
    LATE: '#f59e0b',         // amber-500
    EARLY_LEAVE: '#f97316',  // orange-500
    ABSENT: '#f43f5e',       // rose-500
    VACATION: '#0ea5e9',     // sky-500
    HOLIDAY: '#9ca3af',      // gray-400
    ON_LEAVE: '#8b5cf6',     // violet-500
};
const OVERTIME_COLOR_HEX = '#f43f5e'; // rose-500

let cachedRows = [];
let policyCache = null;

function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = { 'Content-Type': 'application/json' };
    if (token && header) headers[header] = token;
    return headers;
}

function todayIso() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function formatDateLabel(iso) {
    const d = new Date(iso + 'T00:00:00');
    const days = ['일', '월', '화', '수', '목', '금', '토'];
    return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${days[d.getDay()]})`;
}

function formatRangeLabel(fromIso, toIso) {
    const f = new Date(fromIso + 'T00:00:00');
    const t = new Date(toIso + 'T00:00:00');
    if (f.getFullYear() === t.getFullYear() && f.getMonth() === t.getMonth()) {
        return `${f.getFullYear()}.${pad2(f.getMonth() + 1)}.${pad2(f.getDate())} ~ ${pad2(t.getDate())}`;
    }
    return `${f.getFullYear()}.${pad2(f.getMonth() + 1)}.${pad2(f.getDate())} ~ ${t.getFullYear()}.${pad2(t.getMonth() + 1)}.${pad2(t.getDate())}`;
}

function formatMonthLabel(iso) {
    const d = new Date(iso + 'T00:00:00');
    return `${d.getFullYear()}년 ${d.getMonth() + 1}월`;
}

function pad2(n) { return String(n).padStart(2, '0'); }

function shiftDate(iso, delta) {
    const d = new Date(iso + 'T00:00:00');
    d.setDate(d.getDate() + delta);
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

function shiftMonth(iso, delta) {
    const d = new Date(iso + 'T00:00:00');
    d.setMonth(d.getMonth() + delta);
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** 해당 일자가 속한 주의 월요일 ISO. */
function mondayOf(iso) {
    const d = new Date(iso + 'T00:00:00');
    const day = d.getDay(); // 0=일
    const diffToMon = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diffToMon);
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/** 모드별 [from, to] ISO 쌍. */
function rangeFor(mode, iso) {
    if (mode === 'day') return [iso, iso];
    if (mode === 'week') {
        const from = mondayOf(iso);
        const to = shiftDate(from, 6);
        return [from, to];
    }
    // month
    const d = new Date(iso + 'T00:00:00');
    const from = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-01`;
    const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    const to = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(last)}`;
    return [from, to];
}

function makeDateRangeList(fromIso, toIso) {
    const list = [];
    let cur = fromIso;
    while (cur <= toIso) {
        list.push(cur);
        cur = shiftDate(cur, 1);
    }
    return list;
}

// ─── 정책 CRUD ───────────────────────────────────────────────────────

async function loadPolicy() {
    const res = await fetch('/api/attendance/policy');
    if (!res.ok) throw new Error('policy load failed');
    policyCache = await res.json();
    document.getElementById('policyWorkStart').value = policyCache.workStart;
    document.getElementById('policyWorkEnd').value = policyCache.workEnd;
    document.getElementById('policyBreakMin').value = policyCache.breakMinutes;
    document.getElementById('policyLateMin').value = policyCache.lateThresholdMin;
}

async function savePolicy() {
    const body = {
        workStart: document.getElementById('policyWorkStart').value,
        workEnd: document.getElementById('policyWorkEnd').value,
        breakMinutes: parseInt(document.getElementById('policyBreakMin').value, 10) || 0,
        lateThresholdMin: parseInt(document.getElementById('policyLateMin').value, 10) || 0,
    };
    try {
        const res = await fetch('/api/attendance/policy', {
            method: 'POST',
            headers: csrfHeaders(),
            body: JSON.stringify(body),
        });
        if (!res.ok) throw new Error(`policy save failed: ${res.status}`);
        policyCache = await res.json();
        const msg = document.getElementById('policySaveMsg');
        if (msg) {
            msg.classList.remove('hidden');
            setTimeout(() => msg.classList.add('hidden'), 2000);
        }
        // 정책 변경 시 간트 배경 가이드 재계산
        renderGantt(applyFilters(cachedRows));
    } catch (e) {
        console.error(e);
        alert('정책 저장에 실패했습니다.');
    }
}

// ─── 일 근태 로드 + 간트 렌더 ────────────────────────────────────────

async function loadCurrent() {
    const dateInput = document.getElementById('searchDate');
    const iso = dateInput?.value || todayIso();
    const [from, to] = rangeFor(viewMode, iso);
    try {
        let rows;
        if (viewMode === 'day') {
            const res = await fetch(`/api/attendance/company/day?date=${encodeURIComponent(iso)}`);
            if (!res.ok) throw new Error(`day load failed: ${res.status}`);
            rows = await res.json();
        } else {
            const res = await fetch(`/api/attendance/company/range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
            if (!res.ok) throw new Error(`range load failed: ${res.status}`);
            rows = await res.json();
        }
        cachedRows = rows;
        rebuildDeptOptions(cachedRows);
        renderCurrentView(applyFilters(cachedRows));
        updateDateLabel();
    } catch (e) {
        console.error(e);
        alert('근태 데이터를 불러오지 못했습니다.');
    }
}

function updateDateLabel() {
    const dateInput = document.getElementById('searchDate');
    const iso = dateInput?.value || todayIso();
    const labelEl = document.getElementById('dateLabel');
    if (!labelEl) return;
    if (viewMode === 'day') labelEl.textContent = formatDateLabel(iso);
    else if (viewMode === 'week') {
        const [from, to] = rangeFor('week', iso);
        labelEl.textContent = formatRangeLabel(from, to);
    } else {
        labelEl.textContent = formatMonthLabel(iso);
    }
}

function renderCurrentView(rows) {
    const dayPanel = document.getElementById('viewDay');
    const matrixPanel = document.getElementById('viewMatrix');
    if (viewMode === 'day') {
        dayPanel?.classList.remove('hidden');
        matrixPanel?.classList.add('hidden');
        renderGantt(rows);
    } else {
        dayPanel?.classList.add('hidden');
        matrixPanel?.classList.remove('hidden');
        const iso = document.getElementById('searchDate')?.value || todayIso();
        const [from, to] = rangeFor(viewMode, iso);
        renderMatrix(rows, from, to, viewMode);
    }
}

function rebuildDeptOptions(rows) {
    const select = document.getElementById('filterDept');
    if (!select) return;
    const current = select.value;
    const depts = Array.from(new Set(rows.map(r => (r.deptName || '').trim()).filter(Boolean)))
        .sort((a, b) => a.localeCompare(b, 'ko'));
    select.innerHTML = '<option value="">전체 부서</option>' +
        depts.map(d => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join('');
    if (current) select.value = current;
}

function applyFilters(rows) {
    const dept = (document.getElementById('filterDept')?.value || '').trim();
    const name = (document.getElementById('filterName')?.value || '').trim().toLowerCase();
    return rows.filter(r => {
        if (dept && (r.deptName || '') !== dept) return false;
        if (name && !((r.memberName || '').toLowerCase().includes(name))) return false;
        return true;
    });
}

/**
 * 간트 차트 렌더.
 *  - 각 행: [직원 정보 56w] [24시간 그리드 + 막대 absolute]
 *  - 막대: clock_in 분 → clock_out (또는 정책 work_end + overtime) 분
 *  - 초과 근무 부분은 별도 rose-500 막대로 덧대 표시
 *  - 정책 시간대는 행 배경에 연한 음영(gantt-bar-policy)
 */
function renderGantt(rows) {
    const body = document.getElementById('ganttBody');
    const empty = document.getElementById('ganttEmpty');
    if (!body || !empty) return;

    if (!rows.length) {
        body.innerHTML = '';
        empty.classList.remove('hidden');
        return;
    }
    empty.classList.add('hidden');

    // 정책 시간대 (분 단위)
    const policyStartMin = policyCache ? hhmmToMin(policyCache.workStart) : 9 * 60;
    const policyEndMin = policyCache ? hhmmToMin(policyCache.workEnd) : 18 * 60;
    const totalDayMin = 24 * 60;
    const policyLeftPct = (policyStartMin / totalDayMin) * 100;
    const policyWidthPct = ((policyEndMin - policyStartMin) / totalDayMin) * 100;

    body.innerHTML = rows.map((r) => {
        const inMin = timeToMin(r.clockInTime);
        const outMin = timeToMin(r.clockOutTime);

        // 막대 영역 (출근~퇴근 또는 출근~정책 종료)
        const startMin = inMin != null ? inMin : null;
        // 퇴근 안 했으면 오늘이라면 현재 시각, 과거 일자는 null (표시 안 함)
        let endMin = outMin;
        if (startMin != null && endMin == null) {
            endMin = nowMinIfTodayElseNull(r.workDate);
        }

        let baseBar = '';
        let overtimeBar = '';
        if (startMin != null && endMin != null && endMin > startMin) {
            const regularEnd = Math.min(endMin, policyEndMin);
            const overStart = Math.max(startMin, policyEndMin);
            const overEnd = endMin;

            // 정규 근무 막대 (출근 ~ min(퇴근, 정책 종료))
            if (regularEnd > startMin) {
                const left = (startMin / totalDayMin) * 100;
                const width = ((regularEnd - startMin) / totalDayMin) * 100;
                const color = STATUS_COLOR_HEX[r.status] || STATUS_COLOR_HEX.NORMAL;
                const label = `${r.clockInTime ?? '-'} ~ ${r.clockOutTime ?? '진행중'}`;
                baseBar = `<div class="gantt-bar" style="left:${left}%; width:${width}%; background-color:${color};" title="${escapeHtml(label)}">${escapeHtml(label)}</div>`;
            }

            // 초과 근무 막대 (정책 종료 ~ 실제 퇴근)
            // ※ .gantt-bar-overtime 클래스가 min-width 보장 → 짧은 초과(예: +13분) 도 라벨이 잘리지 않음.
            if (overEnd > overStart) {
                const left = (overStart / totalDayMin) * 100;
                const width = ((overEnd - overStart) / totalDayMin) * 100;
                const overMin = overEnd - overStart;
                overtimeBar = `<div class="gantt-bar gantt-bar-overtime" style="left:${left}%; width:${width}%; background-color:${OVERTIME_COLOR_HEX};" title="초과 +${minToHm(overMin)}">+${minToHm(overMin)}</div>`;
            }
        }

        const policyBg = `<div class="gantt-bar-policy" style="left:${policyLeftPct}%; width:${policyWidthPct}%;"></div>`;
        const totalMin = (startMin != null && endMin != null) ? Math.max(0, endMin - startMin) : 0;
        const totalLabel = totalMin > 0 ? minToHm(totalMin) : '-';

        return `
            <div class="flex hover:bg-gray-50 dark:hover:bg-white/5" data-dept="${escapeAttr(r.deptName || '')}" data-name="${escapeAttr(r.memberName || '')}">
                <div class="w-56 shrink-0 border-r border-gray-100 px-4 py-2 dark:border-gray-700 flex flex-col justify-center">
                    <div class="flex items-center gap-2">
                        <span class="font-medium text-gray-900 dark:text-white">${escapeHtml(r.memberName ?? '-')}</span>
                        <span class="text-xs text-gray-400">/ ${escapeHtml(totalLabel)}</span>
                    </div>
                    <div class="mt-0.5 text-xs text-gray-500 dark:text-gray-300">
                        ${escapeHtml(r.deptName ?? '미지정')} · ${escapeHtml(r.positionName ?? '미지정')}
                    </div>
                </div>
                <div class="flex-1 gantt-track">
                    <div class="gantt-grid absolute inset-0">
                        ${Array.from({length: 24}, () => '<div class="gantt-hour-cell"></div>').join('')}
                    </div>
                    ${policyBg}
                    ${baseBar}
                    ${overtimeBar}
                </div>
            </div>
        `;
    }).join('');
}

// ─── 유틸 ────────────────────────────────────────────────────────────

function hhmmToMin(s) {
    if (!s) return 0;
    const [h, m] = s.split(':').map(n => parseInt(n, 10));
    return h * 60 + (m || 0);
}

/** "HH:mm:ss" → 분 (null/빈값이면 null) */
function timeToMin(s) {
    if (!s) return null;
    const m = /^(\d{1,2}):(\d{2})(?::(\d{2}))?$/.exec(s);
    if (!m) return null;
    return parseInt(m[1], 10) * 60 + parseInt(m[2], 10);
}

function nowMinIfTodayElseNull(workDate) {
    if (!workDate) return null;
    const today = todayIso();
    if (workDate !== today) return null;
    const d = new Date();
    return d.getHours() * 60 + d.getMinutes();
}

function minToHm(m) {
    const h = Math.floor(m / 60);
    const min = m % 60;
    if (h === 0) return `${min}분`;
    if (min === 0) return `${h}시간`;
    return `${h}시간 ${min}분`;
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function escapeAttr(s) { return escapeHtml(s); }

// ─── 매트릭스(주/월) 렌더 ────────────────────────────────────────────

const STATUS_BG = {
    NORMAL: 'bg-indigo-100 text-indigo-700',
    LATE: 'bg-amber-100 text-amber-700',
    EARLY_LEAVE: 'bg-orange-100 text-orange-700',
    ABSENT: 'bg-rose-100 text-rose-700',
    VACATION: 'bg-sky-100 text-sky-700',
    HOLIDAY: 'bg-gray-100 text-gray-600',
    ON_LEAVE: 'bg-violet-100 text-violet-700',
};
const DAY_KO = ['일', '월', '화', '수', '목', '금', '토'];

/**
 * 직원 × 일자 셀 그리드.
 *  - 주 보기: 한 셀에 출퇴근 시각(HH:MM) + 상태 배지 + 실근무 (cell 큼)
 *  - 월 보기: 한 셀에 상태 배지만 (cell 작음)
 *  - 사람 별 정렬 + 비어 있는 셀도 표시.
 */
function renderMatrix(rows, fromIso, toIso, mode) {
    const container = document.getElementById('matrixContainer');
    const empty = document.getElementById('matrixEmpty');
    if (!container || !empty) return;

    if (!rows.length) {
        container.innerHTML = '';
        empty.classList.remove('hidden');
        return;
    }
    empty.classList.add('hidden');

    const dates = makeDateRangeList(fromIso, toIso);
    // memberId 별 그룹핑 — 첫 등장 순서 보존
    const memberMap = new Map();
    for (const r of rows) {
        if (!memberMap.has(r.memberId)) {
            memberMap.set(r.memberId, { meta: r, byDate: new Map() });
        }
        memberMap.get(r.memberId).byDate.set(r.workDate, r);
    }
    // 회원 이름 순 정렬
    const members = Array.from(memberMap.values()).sort((a, b) =>
        (a.meta.memberName || '').localeCompare(b.meta.memberName || '', 'ko')
    );

    const cellW = mode === 'week' ? 130 : 56;  // 주는 넓게, 월은 좁게
    const cellMinH = mode === 'week' ? 64 : 40;

    // 헤더 행
    const headerCells = dates.map(d => {
        const dt = new Date(d + 'T00:00:00');
        const w = dt.getDay();
        const dowColor = w === 0 ? 'text-rose-500' : (w === 6 ? 'text-sky-500' : 'text-gray-500 dark:text-gray-300');
        const label = mode === 'week'
            ? `<div class="text-[10px] ${dowColor}">${DAY_KO[w]}</div><div class="text-sm font-semibold text-gray-900 dark:text-white">${dt.getMonth() + 1}/${dt.getDate()}</div>`
            : `<div class="text-[10px] ${dowColor}">${DAY_KO[w]}</div><div class="text-xs font-semibold text-gray-700 dark:text-gray-200">${dt.getDate()}</div>`;
        return `<div class="shrink-0 border-l border-gray-200 px-1 py-2 text-center dark:border-gray-700" style="width:${cellW}px;">${label}</div>`;
    }).join('');

    // 본문 행
    const bodyRows = members.map(({ meta, byDate }) => {
        const cells = dates.map(d => {
            const r = byDate.get(d);
            return `<div class="shrink-0 border-l border-gray-100 p-1 dark:border-gray-700" style="width:${cellW}px; min-height:${cellMinH}px;">${cellHtml(r, mode)}</div>`;
        }).join('');

        return `
            <div class="flex border-b border-gray-100 hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-white/5"
                 data-dept="${escapeAttr(meta.deptName || '')}" data-name="${escapeAttr(meta.memberName || '')}">
                <div class="w-56 shrink-0 border-r border-gray-200 px-4 py-2 dark:border-gray-700 flex flex-col justify-center">
                    <span class="font-medium text-gray-900 dark:text-white">${escapeHtml(meta.memberName || '-')}</span>
                    <span class="text-xs text-gray-500 dark:text-gray-300">${escapeHtml(meta.deptName || '미지정')} · ${escapeHtml(meta.positionName || '미지정')}</span>
                </div>
                <div class="flex">${cells}</div>
            </div>
        `;
    }).join('');

    container.innerHTML = `
        <div class="min-w-fit">
            <div class="flex border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-meta-4">
                <div class="w-56 shrink-0 border-r border-gray-200 px-4 py-2 text-xs font-semibold text-gray-500 dark:border-gray-700 dark:text-gray-300">직원</div>
                <div class="flex">${headerCells}</div>
            </div>
            ${bodyRows}
        </div>
    `;
}

function cellHtml(r, mode) {
    if (!r) return '<div class="h-full"></div>';
    const cls = STATUS_BG[r.status] || 'bg-gray-100 text-gray-600';
    const label = r.statusLabel || r.status || '';
    if (mode === 'week') {
        const inT = r.clockInTime ? r.clockInTime.slice(0, 5) : '--:--';
        const outT = r.clockOutTime ? r.clockOutTime.slice(0, 5) : '--:--';
        const actual = r.actualWorkMin != null ? minToHm(r.actualWorkMin) : '';
        const over = r.overtimeMin != null && r.overtimeMin > 0 ? `<span class="text-[10px] text-rose-600">+${minToHm(r.overtimeMin)}</span>` : '';
        return `
            <div class="flex h-full flex-col gap-0.5">
                <span class="inline-flex w-fit items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium ${cls}">${escapeHtml(label)}</span>
                <div class="font-mono text-[11px] text-gray-700 dark:text-gray-200">${inT} ~ ${outT}</div>
                <div class="flex items-center gap-1 text-[10px] text-gray-500">${actual}${over ? ' / ' + over : ''}</div>
            </div>
        `;
    }
    // month 모드: 색만 작게 + 툴팁
    const title = `${r.workDate} ${r.clockInTime ?? '--'} ~ ${r.clockOutTime ?? '--'} (${label})`;
    return `<div class="flex h-full items-center justify-center" title="${escapeAttr(title)}">
        <span class="inline-block h-5 w-full rounded ${cls}"></span>
    </div>`;
}

// ─── 부트 ────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', async () => {
    const dateInput = document.getElementById('searchDate');

    const setDate = (iso) => {
        dateInput.value = iso;
        loadCurrent();
    };

    // 모드별 이동 단위 — 일 ±1일, 주 ±7일, 월 ±1개월
    const shiftCurrent = (delta) => {
        const base = dateInput.value || todayIso();
        if (viewMode === 'month') return shiftMonth(base, delta);
        if (viewMode === 'week') return shiftDate(base, delta * 7);
        return shiftDate(base, delta);
    };

    if (dateInput) {
        dateInput.value = todayIso();
        dateInput.addEventListener('change', () => setDate(dateInput.value || todayIso()));
    }

    document.getElementById('prevDayBtn')?.addEventListener('click', (e) => { e.stopPropagation(); setDate(shiftCurrent(-1)); });
    document.getElementById('nextDayBtn')?.addEventListener('click', (e) => { e.stopPropagation(); setDate(shiftCurrent(1)); });
    document.getElementById('todayBtn')?.addEventListener('click', (e) => { e.stopPropagation(); setDate(todayIso()); });

    // 날짜 그룹 어느 빈 영역이든 클릭 → date input picker 열기 (내부 버튼/input 은 자기 동작 유지)
    document.getElementById('dateGroup')?.addEventListener('click', (e) => {
        const t = e.target;
        if (t instanceof HTMLButtonElement || t instanceof HTMLInputElement) return;
        try { dateInput?.showPicker?.(); } catch (_) { dateInput?.focus(); }
    });

    // 일/주/월 토글
    document.querySelectorAll('.js-view-mode').forEach((btn) => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            const mode = btn.getAttribute('data-view-mode');
            if (!mode || mode === viewMode) return;
            viewMode = mode;
            // 버튼 활성 스타일 토글
            document.querySelectorAll('.js-view-mode').forEach((b) => {
                const on = b.getAttribute('data-view-mode') === viewMode;
                b.classList.toggle('bg-gray-900', on);
                b.classList.toggle('text-white', on);
                b.classList.toggle('dark:bg-white', on);
                b.classList.toggle('dark:text-gray-900', on);
                b.classList.toggle('bg-white', !on);
                b.classList.toggle('text-gray-700', !on);
                b.classList.toggle('dark:bg-meta-4', !on);
                b.classList.toggle('dark:text-gray-200', !on);
            });
            loadCurrent();
        });
    });

    document.getElementById('filterDept')?.addEventListener('change', () => renderCurrentView(applyFilters(cachedRows)));
    document.getElementById('filterName')?.addEventListener('input', () => renderCurrentView(applyFilters(cachedRows)));

    document.getElementById('policySaveBtn')?.addEventListener('click', savePolicy);

    // 정책 관리 토글: 버튼 자체 + 그룹 div 어디든 클릭 가능.
    const policyToggleBtn = document.getElementById('policyToggleBtn');
    const policyToggleGroup = document.getElementById('policyToggleGroup');
    const policyCard = document.getElementById('policyCard');
    const togglePolicy = (e) => {
        e?.stopPropagation();
        const willShow = policyCard?.classList.contains('hidden');
        policyCard?.classList.toggle('hidden');
        policyToggleBtn?.setAttribute('aria-expanded', String(!!willShow));
        if (willShow) {
            policyToggleBtn?.classList.add('bg-indigo-400', 'text-white', 'hover:bg-indigo-500');
            policyToggleBtn?.classList.remove('bg-indigo-200', 'text-indigo-700', 'hover:bg-indigo-300');
        } else {
            policyToggleBtn?.classList.add('bg-indigo-200', 'text-indigo-700', 'hover:bg-indigo-300');
            policyToggleBtn?.classList.remove('bg-indigo-400', 'text-white', 'hover:bg-indigo-500');
        }
    };
    policyToggleGroup?.addEventListener('click', togglePolicy);

    // 정책 먼저 로드 → 간트 배경 가이드 정확하게
    try { await loadPolicy(); } catch (e) { console.error(e); }
    loadCurrent();
});
