// 내 근무표 — 월 캘린더형.
//  - GET /api/attendance/me?month=YYYY-MM
//  - 각 일자 셀: 날짜 + 출퇴근 시각 + 상태 배지 + 실근무
//  - 다른 달 잎/뒷부분 회색 표시

const STATUS_LABEL_COLOR = {
    NORMAL: 'text-emerald-700 bg-emerald-50',
    LATE: 'text-amber-700 bg-amber-50',
    EARLY_LEAVE: 'text-orange-700 bg-orange-50',
    ABSENT: 'text-rose-700 bg-rose-50',
    VACATION: 'text-sky-700 bg-sky-50',
    HOLIDAY: 'text-gray-600 bg-gray-100',
    ON_LEAVE: 'text-violet-700 bg-violet-50',
};
const STATUS_LABEL_KO = {
    NORMAL: '정상',
    LATE: '지각',
    EARLY_LEAVE: '조퇴',
    ABSENT: '결근',
    VACATION: '휴가',
    HOLIDAY: '휴일',
    ON_LEAVE: '휴직',
};

let currentYm = currentMonth();

function currentMonth() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function shiftMonth(ym, delta) {
    const [y, m] = ym.split('-').map(n => parseInt(n, 10));
    const d = new Date(y, m - 1 + delta, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function ymdToIso(y, m, d) {
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

async function load(ym) {
    document.getElementById('monthLabel').textContent = ym.replace('-', '년 ') + '월';
    try {
        const res = await fetch(`/api/attendance/me?month=${encodeURIComponent(ym)}`);
        if (!res.ok) throw new Error(`load failed: ${res.status}`);
        const rows = await res.json();
        renderCalendar(ym, rows);
        summarize(rows);
    } catch (e) {
        console.error(e);
        alert('근태 데이터를 불러오지 못했습니다.');
    }
}

function renderCalendar(ym, rows) {
    const grid = document.getElementById('calendarGrid');
    if (!grid) return;

    const byDate = new Map(rows.map(r => [r.workDate, r]));
    const [y, m] = ym.split('-').map(n => parseInt(n, 10));
    const firstOfMonth = new Date(y, m - 1, 1);
    const startWeekday = firstOfMonth.getDay(); // 0(일)~6(토)
    const daysInMonth = new Date(y, m, 0).getDate();
    const prevDays = new Date(y, m - 1, 0).getDate();
    const todayIsoStr = (() => {
        const d = new Date();
        return ymdToIso(d.getFullYear(), d.getMonth() + 1, d.getDate());
    })();

    const cells = [];
    // 앞쪽 채우기 (이전 달)
    for (let i = startWeekday - 1; i >= 0; i--) {
        cells.push({ y: m === 1 ? y - 1 : y, mo: m === 1 ? 12 : m - 1, d: prevDays - i, otherMonth: true });
    }
    // 이번 달
    for (let d = 1; d <= daysInMonth; d++) {
        cells.push({ y, mo: m, d, otherMonth: false });
    }
    // 뒤쪽 채우기 (다음 달) — 6주 = 42칸 채움
    while (cells.length < 42) {
        const last = cells[cells.length - 1];
        const nd = new Date(last.y, last.mo - 1, last.d + 1);
        cells.push({ y: nd.getFullYear(), mo: nd.getMonth() + 1, d: nd.getDate(), otherMonth: nd.getMonth() + 1 !== m });
    }
    // 마지막 주가 모두 다음 달이면 잘라내기 (5주만으로 충분한 달)
    while (cells.length > 35 && cells.slice(35).every(c => c.otherMonth)) {
        cells.length = 35;
    }

    grid.innerHTML = cells.map((c, idx) => {
        const iso = ymdToIso(c.y, c.mo, c.d);
        const rec = byDate.get(iso);
        const dayOfWeek = idx % 7;
        const dayColor = c.otherMonth
            ? 'text-gray-300 dark:text-gray-600'
            : (dayOfWeek === 0 ? 'text-rose-500' : (dayOfWeek === 6 ? 'text-sky-500' : 'text-gray-700 dark:text-gray-200'));
        const isToday = iso === todayIsoStr;
        const ring = isToday ? 'ring-2 ring-indigo-400 dark:ring-indigo-500' : '';
        const bg = c.otherMonth ? 'bg-gray-50/50 dark:bg-gray-900/30' : '';

        const content = rec ? buildRecordHtml(rec) : '<div class="mt-1 text-[10px] text-gray-300 dark:text-gray-600">-</div>';

        return `
            <div class="min-h-[110px] border-b border-r border-gray-100 p-2 dark:border-gray-700 ${bg} ${ring}">
                <div class="flex items-center justify-between">
                    <span class="text-sm font-semibold ${dayColor}">${c.d}</span>
                    ${rec ? statusBadgeHtml(rec.status, rec.statusLabel) : ''}
                </div>
                ${content}
            </div>
        `;
    }).join('');
}

function buildRecordHtml(r) {
    const inT = r.clockInTime ? r.clockInTime.slice(0, 5) : null;
    const outT = r.clockOutTime ? r.clockOutTime.slice(0, 5) : null;
    const actual = r.actualWorkMin != null ? minToHm(r.actualWorkMin) : null;
    const overtime = r.overtimeMin != null && r.overtimeMin > 0 ? `+${minToHm(r.overtimeMin)}` : null;

    const lines = [];
    if (inT || outT) {
        lines.push(`<div class="mt-1 font-mono text-[11px] text-gray-700 dark:text-gray-200">${inT ?? ''} ~ ${outT ?? ''}</div>`);
    }
    if (actual) {
        lines.push(`<div class="text-[10px] text-gray-500 dark:text-gray-400">실 ${actual}</div>`);
    }
    if (overtime) {
        lines.push(`<div class="text-[10px] font-semibold text-rose-600">초과 ${overtime}</div>`);
    }
    return lines.join('');
}

function statusBadgeHtml(status, label) {
    const cls = STATUS_LABEL_COLOR[status] || 'text-gray-600 bg-gray-100';
    const text = label || STATUS_LABEL_KO[status] || status || '';
    return `<span class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium ${cls}">${escapeHtml(text)}</span>`;
}

function summarize(rows) {
    const totalActual = rows.reduce((s, r) => s + (r.actualWorkMin || 0), 0);
    const totalOvertime = rows.reduce((s, r) => s + (r.overtimeMin || 0), 0);
    const lateCount = rows.filter(r => r.status === 'LATE').length;

    document.getElementById('summaryActual').textContent = minToHm(totalActual);
    document.getElementById('summaryOvertime').textContent = totalOvertime > 0 ? `+${minToHm(totalOvertime)}` : '0분';
    document.getElementById('summaryLate').textContent = `${lateCount}회`;
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

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('prevMonthBtn')?.addEventListener('click', () => {
        currentYm = shiftMonth(currentYm, -1);
        load(currentYm);
    });
    document.getElementById('nextMonthBtn')?.addEventListener('click', () => {
        currentYm = shiftMonth(currentYm, 1);
        load(currentYm);
    });
    document.getElementById('thisMonthBtn')?.addEventListener('click', () => {
        currentYm = currentMonth();
        load(currentYm);
    });

    load(currentYm);
});
