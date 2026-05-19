/**
 * [홈 대시보드] index.html 전용 — 출퇴근·시계·오늘 일정·주간·공지
 * - 플로팅 채팅은 floating-chat.js + partials/floating-chat 참고
 * - 서버 연동 시: fetch는 GET/POST 규약에 맞춰 각 핸들러만 교체하면 됩니다.
 */

<<<<<<< HEAD
/** 목표 근무 시간(밀리초) — 출근 시점부터 이 시간이 채우면 100% */
const WORKDAY_MS = 8 * 60 * 60 * 1000;
=======
/** @type {string} 홈 플로팅 채팅 대화 목록(Mock) localStorage 키 — 기존 쪽지 키와 분리 */
const HOME_CHAT_STORAGE_KEY = 'homeChatConversationsV1';

/**
 * 목표 근무 시간(밀리초) — 회사 정책이 없는 경우의 폴백.
 * 실제 진행바는 setupAttendanceCard 에 전달된 standardWorkMin 값을 우선 사용한다.
 */
const WORKDAY_FALLBACK_MS = 8 * 60 * 60 * 1000;

/** CSRF 토큰 fetch 헬퍼 — 메타에서 읽어 헤더에 실어준다. */
function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content;
  const headers = { 'Content-Type': 'application/json' };
  if (token && header) headers[header] = token;
  return headers;
}
>>>>>>> e3ce3ad4399f10a1e163a8f5fdc6e2fd7a1ed32c

/**
 * 두 자리 숫자로 포맷합니다.
 * @param {number} n
 * @returns {string}
 */
function pad2(n) {
  return String(n).padStart(2, '0');
}

/**
 * Date를 HH:mm:ss 문자열로 반환합니다.
 * @param {Date} d
 * @returns {string}
 */
function formatTime(d) {
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
}

/**
 * Date를 YYYY-MM-DD로 반환합니다.
 * @param {Date} d
 * @returns {string}
 */
function formatDateYmd(d) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

/**
 * 상단 시계 및 날짜 라벨을 갱신합니다.
 * @param {HTMLElement} clockEl
 * @param {HTMLElement | null} dateEl
 * @returns {number}
 */
function startClock(clockEl, dateEl) {
  const tick = () => {
    const now = new Date();
    clockEl.textContent = formatTime(now);
    if (dateEl) {
      dateEl.textContent = `${now.getFullYear()}.${pad2(now.getMonth() + 1)}.${pad2(now.getDate())}`;
    }
  };
  tick();
  return window.setInterval(tick, 1000);
}

/**
 * 근무 진행률 UI 갱신 — 진행바와 라벨/초과 판정의 기준을 통일.
 *  - 진행바 fill: "출근 시각 ~ 정책 work_end" 시계 구간을 0~100% 로. 정책 work_end 도달 시 정확히 100%.
 *  - 초과(overtime): 정책 work_end 이후의 시계 시간. fill 은 100% 캡, 우→좌 빨강 overlay 가 채워짐.
 *  - 라벨: 정규 구간은 "{n}%", 정책 work_end 초과 시 "100% · 초과 +{m}분".
 *  ※ 이전 구현은 fill 의 분모가 휴게 차감된 standardWorkMs 라서, 정책 work_end 도달 시 fill 이 100% 미만이고
 *    라벨만 100% 인 불일치가 있었음 → 분모를 시계 기준(checkin ~ workEnd) 으로 통일.
 *  ※ overtime overlay 의 분모는 standardWorkMs 그대로 — "정규 근무 시간만큼 더 일하면 트랙 전체 빨강" 의도 보존.
 * @param {Date | null} checkinAt
 * @param {Date | null} checkoutAt
 * @param {HTMLElement | null} fillEl
 * @param {HTMLElement | null} overtimeEl
 * @param {HTMLElement | null} trackEl
 * @param {HTMLElement | null} labelEl
 * @param {number} standardWorkMs
 * @param {Date | null} dailyWorkEndAt 오늘 날짜에 정책 work_end 시각을 박은 Date — 100% 기준점
 */
function updateWorkProgress(checkinAt, checkoutAt, fillEl, overtimeEl, trackEl, labelEl, standardWorkMs, dailyWorkEndAt) {
  if (!fillEl || !labelEl || !trackEl) return;
  if (!checkinAt) {
    fillEl.style.width = '0%';
    if (overtimeEl) overtimeEl.style.width = '0%';
    labelEl.textContent = '0%';
    trackEl.setAttribute('aria-valuenow', '0');
    return;
  }
  const endMs = checkoutAt ? checkoutAt.getTime() : Date.now();
  const std = standardWorkMs > 0 ? standardWorkMs : WORKDAY_FALLBACK_MS;

  // fill 분모: 출근 시점 ~ 정책 workEnd. 정책 정보가 없거나 출근이 workEnd 이후면 standardWorkMs 폴백.
  let denomMs;
  if (dailyWorkEndAt instanceof Date && dailyWorkEndAt.getTime() > checkinAt.getTime()) {
    denomMs = dailyWorkEndAt.getTime() - checkinAt.getTime();
  } else {
    denomMs = std;
  }

  const elapsed = Math.max(0, endMs - checkinAt.getTime());
  const regularPct = Math.min(100, (elapsed / denomMs) * 100);
  fillEl.style.width = `${regularPct}%`;

  // 초과: 정책 workEnd 이후의 시계 시간.
  let overtimeMs = 0;
  if (dailyWorkEndAt instanceof Date) {
    overtimeMs = Math.max(0, endMs - dailyWorkEndAt.getTime());
  } else {
    overtimeMs = Math.max(0, elapsed - denomMs);
  }
  const overtimePct = Math.min(100, (overtimeMs / std) * 100);
  if (overtimeEl) overtimeEl.style.width = `${overtimePct}%`;

  if (overtimeMs > 0) {
    const overMin = Math.floor(overtimeMs / 60000);
    labelEl.textContent = `100% · 초과 +${overMin}분`;
    labelEl.classList.add('text-rose-600', 'dark:text-rose-400', 'font-semibold');
  } else {
    labelEl.textContent = `${Math.round(regularPct)}%`;
    labelEl.classList.remove('text-rose-600', 'dark:text-rose-400', 'font-semibold');
  }
  trackEl.setAttribute('aria-valuenow', String(Math.round(regularPct)));
}

/** 근태 상태별 배지 색상 (메인 카드 헤더용) */
const ATTENDANCE_STATUS_STYLE = {
  NORMAL:      { cls: 'bg-emerald-50 text-emerald-700', label: '정상' },
  LATE:        { cls: 'bg-amber-50 text-amber-700',     label: '지각' },
  EARLY_LEAVE: { cls: 'bg-orange-50 text-orange-700',   label: '조퇴' },
  ABSENT:      { cls: 'bg-rose-50 text-rose-700',       label: '결근' },
  VACATION:    { cls: 'bg-sky-50 text-sky-700',         label: '휴가' },
  HOLIDAY:     { cls: 'bg-gray-100 text-gray-600',      label: '휴일' },
  ON_LEAVE:    { cls: 'bg-violet-50 text-violet-700',   label: '휴직' },
};

/** 헤더 배지에 status 적용. status null/빈값이면 숨김. */
function applyAttendanceStatusBadge(badgeEl, status, label) {
  if (!badgeEl) return;
  // 모든 색상 클래스 제거 후 재적용
  Object.values(ATTENDANCE_STATUS_STYLE).forEach(({ cls }) => {
    cls.split(' ').forEach(c => badgeEl.classList.remove(c));
  });
  if (!status) {
    badgeEl.classList.add('hidden');
    badgeEl.textContent = '';
    return;
  }
  const style = ATTENDANCE_STATUS_STYLE[status] || { cls: 'bg-gray-100 text-gray-600', label: status };
  style.cls.split(' ').forEach(c => badgeEl.classList.add(c));
  badgeEl.textContent = label || style.label;
  badgeEl.classList.remove('hidden');
}

/**
 * 출퇴근 카드 + 근무 진행바 (정책 기반 + 백엔드 API 연동).
 * 3상태: 미출근 → 출근(퇴근 가능) → 퇴근 완료(퇴근 취소 가능)
 * @param {{
 *   checkinBtn: HTMLButtonElement;
 *   checkoutBtn: HTMLButtonElement;
 *   cancelBtn: HTMLButtonElement | null;
 *   checkinDisplay: HTMLElement;
 *   checkoutDisplay: HTMLElement;
 *   statusBadgeEl: HTMLElement | null;
 *   fillEl: HTMLElement | null;
 *   overtimeEl: HTMLElement | null;
 *   trackEl: HTMLElement | null;
 *   labelEl: HTMLElement | null;
 * }} els
 * @param {{
 *   initialClockInAt?: string;
 *   initialClockOutAt?: string;
 *   initialStatus?: string;
 *   initialStatusLabel?: string;
 *   workStart?: string;
 *   workEnd?: string;
 *   standardWorkMin?: number;
 * }} [options]
 */
function setupAttendanceCard(els, options = {}) {
  const { initialClockInAt, initialClockOutAt, initialStatus, initialStatusLabel,
          workStart, workEnd, standardWorkMin } = options;

  let checkinAt = parseServerDatetime(initialClockInAt);
  let checkoutAt = parseServerDatetime(initialClockOutAt);
  let progressTimer = /** @type {number | null} */ (null);

  const standardWorkMs = (standardWorkMin && standardWorkMin > 0) ? standardWorkMin * 60000 : WORKDAY_FALLBACK_MS;
  const dailyWorkEndAt = buildTodayAt(workEnd) || new Date(Date.now() + standardWorkMs);

  const tickProgress = () => {
    updateWorkProgress(checkinAt, checkoutAt, els.fillEl, els.overtimeEl, els.trackEl, els.labelEl, standardWorkMs, dailyWorkEndAt);
  };

  // 3상태 버튼 가시성: 출근(미출근만) / 퇴근(출근만) / 퇴근 취소(퇴근 완료)
  const refreshButtons = () => {
    const notIn = checkinAt === null;
    const inOnly = checkinAt !== null && checkoutAt === null;
    const done = checkinAt !== null && checkoutAt !== null;
    els.checkinBtn.hidden = !notIn;
    els.checkinBtn.disabled = !notIn;
    els.checkoutBtn.hidden = !inOnly;
    els.checkoutBtn.disabled = !inOnly;
    if (els.cancelBtn) {
      els.cancelBtn.hidden = !done;
      els.cancelBtn.disabled = !done;
    }
  };

  // SSR 초기 상태 적용
  if (checkinAt) els.checkinDisplay.textContent = formatTime(checkinAt);
  if (checkoutAt) {
    els.checkoutDisplay.textContent = formatTime(checkoutAt);
  } else if (checkinAt) {
    progressTimer = window.setInterval(tickProgress, 1000);
  }
  applyAttendanceStatusBadge(els.statusBadgeEl, initialStatus, initialStatusLabel);

  els.checkinBtn.addEventListener('click', async () => {
    if (checkinAt) return;
    els.checkinBtn.disabled = true;
    try {
      const res = await fetch('/api/attendance/clock-in', { method: 'POST', headers: csrfHeaders() });
      if (!res.ok) throw new Error(`clock-in failed: ${res.status}`);
      const dto = await res.json();
      checkinAt = parseServerDatetime(dto.clockInAt) || new Date();
      els.checkinDisplay.textContent = formatTime(checkinAt);
      checkoutAt = null;
      els.checkoutDisplay.textContent = '—';
      if (progressTimer) window.clearInterval(progressTimer);
      progressTimer = window.setInterval(tickProgress, 1000);
      applyAttendanceStatusBadge(els.statusBadgeEl, dto.status, dto.statusLabel);
      tickProgress();
      refreshButtons();
    } catch (e) {
      console.error(e);
      alert('출근 처리에 실패했습니다.');
      refreshButtons();
    }
  });

  els.checkoutBtn.addEventListener('click', async () => {
    if (!checkinAt || checkoutAt) return;
    els.checkoutBtn.disabled = true;
    try {
      const res = await fetch('/api/attendance/clock-out', { method: 'POST', headers: csrfHeaders() });
      if (!res.ok) throw new Error(`clock-out failed: ${res.status}`);
      const dto = await res.json();
      checkoutAt = parseServerDatetime(dto.clockOutAt) || new Date();
      els.checkoutDisplay.textContent = formatTime(checkoutAt);
      if (progressTimer) { window.clearInterval(progressTimer); progressTimer = null; }
      applyAttendanceStatusBadge(els.statusBadgeEl, dto.status, dto.statusLabel);
      tickProgress();
      refreshButtons();
    } catch (e) {
      console.error(e);
      alert('퇴근 처리에 실패했습니다.');
      refreshButtons();
    }
  });

  // 퇴근 취소: 실수로 퇴근 누른 경우 복구. confirm 으로 한 단계 확인.
  if (els.cancelBtn) {
    els.cancelBtn.addEventListener('click', async () => {
      if (!checkoutAt) return;
      if (!confirm('퇴근 처리를 취소하고 다시 근무 상태로 되돌릴까요?')) return;
      els.cancelBtn.disabled = true;
      try {
        const res = await fetch('/api/attendance/clock-out-cancel', { method: 'POST', headers: csrfHeaders() });
        if (!res.ok) throw new Error(`clock-out-cancel failed: ${res.status}`);
        const dto = await res.json();
        checkoutAt = null;
        els.checkoutDisplay.textContent = '—';
        if (progressTimer) window.clearInterval(progressTimer);
        progressTimer = window.setInterval(tickProgress, 1000);
        applyAttendanceStatusBadge(els.statusBadgeEl, dto.status, dto.statusLabel);
        tickProgress();
        refreshButtons();
      } catch (e) {
        console.error(e);
        alert('퇴근 취소에 실패했습니다.');
        refreshButtons();
      }
    });
  }

  refreshButtons();
  tickProgress();
}

/** "yyyy-MM-dd HH:mm:ss" → Date (실패 시 null) */
function parseServerDatetime(s) {
  if (!s) return null;
  // ISO 호환 포맷으로 변환
  const iso = s.replace(' ', 'T');
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** "HH:mm" → 오늘 날짜의 Date (실패 시 null) */
function buildTodayAt(hhmm) {
  if (!hhmm) return null;
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm);
  if (!m) return null;
  const d = new Date();
  d.setHours(parseInt(m[1], 10), parseInt(m[2], 10), 0, 0);
  return d;
}

/** GET /api/calendars — 로그인 회원에게 가시한 일정 전체. 401/실패는 빈 배열. */
async function fetchVisibleCalendars() {
  try {
    const res = await fetch('/api/calendars');
    if (!res.ok) return [];
    return await res.json();
  } catch (e) {
    console.warn('일정 로드 실패', e);
    return [];
  }
}

/** 가벼운 HTML escape (innerHTML 주입용) */
function escapeHtmlMini(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ─── 반복 일정 확장 ───────────────────────────────────────────────────
// CalendarDto.repeatType (DAILY/WEEKLY/MONTHLY/YEARLY) + repeatWeekdays + repeatMonthDays + repeatEndAt
// 클라이언트에서 target 일자에 해당하는 인스턴스를 생성한다.

/** JS Date.getDay() (0=일~6=토) → 백엔드 비트마스크 (MON=1, TUE=2, ..., SUN=64). */
function jsDayToWeekdayMask(jsDay) {
  return jsDay === 0 ? 64 : (1 << (jsDay - 1));
}

/**
 * 반복 일정 여부 — Lombok @Data + `boolean isRepeat` 필드는 Jackson 직렬화 시 JSON 키가
 * `repeat` 으로 떨어지므로 양쪽 키를 모두 확인해야 한다. (`isAlert`/`allDay` 도 동일 패턴이지만
 * 메인 위젯에서는 사용하지 않음)
 */
function isRepeatEv(ev) {
  return Boolean(ev && (ev.isRepeat != null ? ev.isRepeat : ev.repeat));
}

/** target 일자(0시 기준)에 해당 일정이 발생하는지. 단일/반복 모두 처리. */
function occursOnDate(ev, target) {
  if (!ev || !ev.startAt) return false;
  const start = new Date(ev.startAt);
  const targetDay0 = new Date(target);
  targetDay0.setHours(0, 0, 0, 0);

  // 단일 일정: startAt 의 날짜가 target 과 같은지
  if (!isRepeatEv(ev)) {
    const startDay0 = new Date(start);
    startDay0.setHours(0, 0, 0, 0);
    return startDay0.getTime() === targetDay0.getTime();
  }

  // 반복 시작 전이면 발생 안 함
  const startDay0 = new Date(start);
  startDay0.setHours(0, 0, 0, 0);
  if (targetDay0.getTime() < startDay0.getTime()) return false;

  // 반복 종료일 지나면 발생 안 함
  if (ev.repeatEndAt) {
    const repeatEnd = new Date(ev.repeatEndAt);
    repeatEnd.setHours(23, 59, 59, 999);
    if (targetDay0.getTime() > repeatEnd.getTime()) return false;
  }

  switch (ev.repeatType) {
    case 'DAILY':
      return true;
    case 'WEEKLY': {
      const mask = ev.repeatWeekdays || 0;
      if (mask !== 0) {
        return (mask & jsDayToWeekdayMask(targetDay0.getDay())) !== 0;
      }
      // null/0 → 시작일 요일만
      return start.getDay() === targetDay0.getDay();
    }
    case 'MONTHLY': {
      const mask = ev.repeatMonthDays || 0;
      if (mask !== 0) {
        return (mask & (1 << (targetDay0.getDate() - 1))) !== 0;
      }
      return start.getDate() === targetDay0.getDate();
    }
    case 'YEARLY':
      return start.getMonth() === targetDay0.getMonth()
          && start.getDate() === targetDay0.getDate();
    default:
      return false;
  }
}

/** target 일자에 발생하는 인스턴스 1개 생성 — startAt/endAt 의 시각은 보존, 날짜는 target 로 옮김. */
function instanceForDate(ev, target) {
  const start = new Date(ev.startAt);
  const targetStart = new Date(target);
  targetStart.setHours(start.getHours(), start.getMinutes(), start.getSeconds(), 0);
  let targetEndIso = null;
  if (ev.endAt) {
    const dur = new Date(ev.endAt).getTime() - start.getTime();
    if (isFinite(dur) && dur >= 0) {
      targetEndIso = new Date(targetStart.getTime() + dur).toISOString();
    }
  }
  return Object.assign({}, ev, {
    startAt: targetStart.toISOString(),
    endAt: targetEndIso,
  });
}

/** events 전체에서 target 일자에 해당하는 인스턴스 배열. (반복 일정 확장 포함) */
function expandEventsForDate(events, target) {
  return (events || [])
    .filter((ev) => occursOnDate(ev, target))
    .map((ev) => instanceForDate(ev, target))
    .sort((a, b) => new Date(a.startAt) - new Date(b.startAt));
}

/**
 * 오늘 일정 위젯 — 지금 시각 이후의 일정만 가까운 순 최대 3건.
 * - 반복 일정(매일/매주/매월/매년)도 오늘 인스턴스로 확장해서 포함.
 * - 카테고리 이름/색 + 일정 장소 표시.
 * - 1분 주기로 재계산해서 끝난 일정은 자동 사라지고 다음이 올라옴.
 */
function renderTodayScheduleFromEvents(container, events) {
  const render = () => {
    const now = new Date();
    // 오늘 발생하는 모든 인스턴스(단일 + 반복) 중 아직 끝나지 않은 것
    const list = expandEventsForDate(events, now)
      .filter((ev) => {
        const start = new Date(ev.startAt);
        const refEnd = ev.endAt ? new Date(ev.endAt) : start;
        return refEnd.getTime() > now.getTime();
      })
      .slice(0, 3);

    if (list.length === 0) {
      container.innerHTML = '<div class="px-2 py-3 text-center text-xs text-gray-400">오늘 남은 일정이 없습니다.</div>';
      return;
    }

    container.innerHTML = '<div class="space-y-2">' + list.map(scheduleCardHtml).join('') + '</div>';
  };
  render();
  return window.setInterval(render, 60_000);
}

/**
 * 오늘/내일 일정 카드 HTML — 시간 (+ 반복 배지) / 제목 / 카테고리·장소 메타.
 * 반복 일정이면 시간 옆에 보라색 배지(반복 아이콘 + "매주 월·수·금" 같은 라벨) 표시.
 */
function scheduleCardHtml(item) {
  const t = new Date(item.startAt);
  const timeLabel = pad2(t.getHours()) + ':' + pad2(t.getMinutes());
  const repeatBadge = buildRepeatBadgeHtml(item);
  const meta = buildScheduleMetaHtml(item);
  return ''
    + '<article class="rounded-xl border border-gray-100 bg-gray-50/90 px-3 py-2.5 dark:border-gray-600 dark:bg-gray-900/40">'
    +   '<div class="flex flex-wrap items-center gap-1.5">'
    +     '<p class="text-xs font-medium text-brand-600 dark:text-brand-400">' + timeLabel + '</p>'
    +     repeatBadge
    +   '</div>'
    +   '<p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-white">' + escapeHtmlMini(item.title || '') + '</p>'
    +   '<p class="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">' + (meta || ' ') + '</p>'
    + '</article>';
}

/** 카테고리(색 점 + 이름) + 장소 메타 정보 HTML. 둘 다 없으면 빈 문자열. */
function buildScheduleMetaHtml(item) {
  const parts = [];
  if (item.categoryName) {
    const dot = item.categoryColor
      ? '<span class="inline-block h-2 w-2 rounded-full" style="background-color:' + escapeHtmlMini(item.categoryColor) + ';"></span>'
      : '';
    parts.push('<span class="inline-flex items-center gap-1">' + dot + escapeHtmlMini(item.categoryName) + '</span>');
  }
  if (item.location) {
    parts.push('<span class="inline-flex items-center gap-1"><span class="text-gray-300">·</span>' + escapeHtmlMini(item.location) + '</span>');
  }
  return parts.join('');
}

// ─── 반복 일정 라벨/배지 ──────────────────────────────────────────────

const WEEKDAY_LABELS_KO = ['월', '화', '수', '목', '금', '토', '일'];

/** repeatWeekdays bitmask (MON=1, TUE=2, ..., SUN=64) → "월·수·금" */
function decodeWeekdayMask(mask) {
  const out = [];
  for (let i = 0; i < 7; i++) {
    if (mask & (1 << i)) out.push(WEEKDAY_LABELS_KO[i]);
  }
  return out.join('·');
}

/** repeatMonthDays bitmask (1일=1<<0 .. 31일=1<<30) → "1·15·28일" */
function decodeMonthDayMask(mask) {
  const out = [];
  for (let i = 0; i < 31; i++) {
    if (mask & (1 << i)) out.push((i + 1));
  }
  return out.length > 0 ? (out.join('·') + '일') : '';
}

/**
 * "매일 반복" / "매주 월·수·금" / "매월 15일" / "매년 5월 18일" 같은 사람용 라벨.
 * - WEEKLY: mask 없으면 시작일의 요일로 단일 표시 ("매주 월").
 * - MONTHLY: mask 없으면 시작일의 일자로 단일 표시 ("매월 18일").
 * - YEARLY: 시작일의 월·일.
 */
function buildRepeatLabel(item) {
  if (!isRepeatEv(item)) return '';
  const type = String(item.repeatType || '').toUpperCase();
  const start = item.startAt ? new Date(item.startAt) : null;
  switch (type) {
    case 'DAILY':
      return '매일 반복';
    case 'WEEKLY': {
      const mask = item.repeatWeekdays || 0;
      if (mask !== 0) {
        const days = decodeWeekdayMask(mask);
        return days ? '매주 ' + days : '매주 반복';
      }
      if (start) {
        // JS Date.getDay(): 0=일~6=토. 한국 라벨 인덱스: 0=월..6=일.
        const jsDay = start.getDay();
        const koIdx = jsDay === 0 ? 6 : jsDay - 1;
        return '매주 ' + WEEKDAY_LABELS_KO[koIdx];
      }
      return '매주 반복';
    }
    case 'MONTHLY': {
      const mask = item.repeatMonthDays || 0;
      if (mask !== 0) {
        const days = decodeMonthDayMask(mask);
        return days ? '매월 ' + days : '매월 반복';
      }
      if (start) return '매월 ' + start.getDate() + '일';
      return '매월 반복';
    }
    case 'YEARLY':
      if (start) return '매년 ' + (start.getMonth() + 1) + '월 ' + start.getDate() + '일';
      return '매년 반복';
    default:
      return '반복';
  }
}

/** 반복 일정 배지 HTML (반복 아이콘 + 라벨). 반복 아니면 빈 문자열. */
function buildRepeatBadgeHtml(item) {
  const label = buildRepeatLabel(item);
  if (!label) return '';
  return ''
    + '<span class="inline-flex items-center gap-1 rounded-full bg-violet-50 px-1.5 py-0.5 text-[10px] font-medium text-violet-700 dark:bg-violet-500/15 dark:text-violet-300" title="' + escapeHtmlMini(label) + '">'
    +   '<svg viewBox="0 0 16 16" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'
    +     '<path d="M3 8a5 5 0 0 1 8.5-3.5L13 3M13 8a5 5 0 0 1-8.5 3.5L3 13M13 3v3h-3M3 13v-3h3"/>'
    +   '</svg>'
    +   escapeHtmlMini(label)
    + '</span>';
}

/**
 * (구) 오늘 일정 mock — 사용 안 함. 호환을 위한 빈 함수.
 */
function renderTodaySchedule(container) {
  const ymd = formatDateYmd(new Date());

  /** @type {{ title: string; time: string; room?: string }[]} */
  const raw = [
    { title: '팀 스탠드업', time: `${ymd}T09:30:00`, room: '3층 A' },
    { title: '인트라넷 UI 리뷰', time: `${ymd}T11:00:00`, room: '화상' },
    { title: '점심 약속', time: `${ymd}T12:30:00` },
    { title: '교육: 보안 인식', time: `${ymd}T15:00:00`, room: '교육실' },
    { title: '주간 정리', time: `${ymd}T17:30:00` },
  ];

  raw.sort((a, b) => new Date(a.time) - new Date(b.time));
  const top = raw.slice(0, 3);

  const rowHtml = (item) => {
    const t = new Date(item.time);
    const timeLabel = `${pad2(t.getHours())}:${pad2(t.getMinutes())}`;
    const sub = item.room ? ` · ${item.room}` : '';
    return `
      <article class="rounded-xl border border-gray-100 bg-gray-50/90 px-3 py-2.5 dark:border-gray-600 dark:bg-gray-900/40">
        <p class="text-xs font-medium text-brand-600 dark:text-brand-400">${timeLabel}</p>
        <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-white">${item.title}</p>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">${sub || '\u00A0'}</p>
      </article>
    `;
  };

  container.innerHTML = `<div class="space-y-2">${top.map(rowHtml).join('')}</div>`;
}

/**
 * 이번 주 월~금
 * @param {Date} base
 * @returns {Date[]}
 */
function getMonToFri(base) {
  const d = new Date(base);
  const day = d.getDay();
  const diffToMon = day === 0 ? -6 : 1 - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + diffToMon);
  monday.setHours(0, 0, 0, 0);
  return Array.from({ length: 5 }, (_, i) => {
    const x = new Date(monday);
    x.setDate(monday.getDate() + i);
    return x;
  });
}

/**
 * 주간 일정 (월~금) + 내일 일정 풋터.
 *  - 각 일자에 대해 반복 일정 확장(expandEventsForDate)으로 인스턴스 채움.
 *  - 셀마다 최대 2건 + 초과 시 +N (카드 크기 안에 맞춤).
 *  - 풋터: 내일 일정 표시. 2건 이상이면 ←/→ 페이지네이션.
 */
function renderWeekCalendarFromEvents(container, rangeLabel, footerEl, events) {
  const days = getMonToFri(new Date());
  const dayNames = ['월', '화', '수', '목', '금'];

  if (rangeLabel) {
    const a = days[0];
    const b = days[4];
    rangeLabel.textContent = (a.getMonth() + 1) + '/' + a.getDate() + ' – ' + (b.getMonth() + 1) + '/' + b.getDate();
  }

  // 각 일자별 인스턴스 (반복 일정 포함)
  const byDayInstances = days.map((d) => expandEventsForDate(events, d));

  const todayYmd = formatDateYmd(new Date());
  container.innerHTML = days
    .map((date, idx) => {
      const list = byDayInstances[idx];
      const isToday = todayYmd === formatDateYmd(date);
      const ring = isToday ? 'ring-2 ring-brand-400 dark:ring-brand-500' : '';
      const shown = list.slice(0, 2);
      const more = list.length > 2 ? '<li class="truncate text-[10px] text-gray-400">+' + (list.length - 2) + '</li>' : '';
      const items = shown
        .map((t) => '<li class="truncate rounded-lg bg-white/90 px-1.5 py-1 text-[11px] font-medium text-gray-800 shadow-sm dark:bg-gray-900 dark:text-gray-100" title="' + escapeHtmlMini(t.title || '') + '">' + escapeHtmlMini(t.title || '') + '</li>')
        .join('');
      return ''
        + '<div role="gridcell" class="flex min-h-0 flex-col overflow-hidden rounded-xl border border-gray-100 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-900/30 sm:p-2.5 ' + ring + '">'
        +   '<div class="mb-1.5 shrink-0 text-center">'
        +     '<div class="text-[10px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">' + dayNames[idx] + '</div>'
        +     '<div class="text-sm font-bold text-gray-900 dark:text-white">' + date.getDate() + '</div>'
        +   '</div>'
        +   '<ul class="min-h-0 flex-1 space-y-1 overflow-hidden">' + (items || '<li class="text-[10px] text-gray-400">일정 없음</li>') + more + '</ul>'
        + '</div>';
    })
    .join('');

  // 풋터: 내일 일정 (반복 확장 포함). 2건 이상이면 ←/→ 페이지네이션.
  if (footerEl) {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowEvents = expandEventsForDate(events, tomorrow);
    renderTomorrowFooter(footerEl, tomorrowEvents);
  }
}

/**
 * 내일 일정 풋터.
 *  - 0건: 안내 문구
 *  - 1건: 단일 표시
 *  - 2건 이상: 현재 인덱스 표시 + 좌/우 화살표 + "n / total" 인디케이터
 */
function renderTomorrowFooter(footerEl, list) {
  if (!footerEl) return;
  if (!list || list.length === 0) {
    footerEl.innerHTML = '<div class="text-[11px] text-gray-400">내일 등록된 일정이 없습니다.</div>';
    return;
  }
  let idx = 0;
  const total = list.length;
  const draw = () => {
    const item = list[idx];
    const start = new Date(item.startAt);
    const timeLabel = pad2(start.getHours()) + ':' + pad2(start.getMinutes());
    const meta = buildScheduleMetaHtml(item);
    const repeatBadge = buildRepeatBadgeHtml(item);
    const pagerHtml = total > 1
      ? '<div class="ml-auto flex items-center gap-1.5 shrink-0">'
        + '<button type="button" data-tomorrow-prev aria-label="이전 일정" class="rounded p-0.5 text-gray-500 hover:bg-gray-200 disabled:opacity-30 dark:hover:bg-gray-700">'
        +   '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 4 L6 8 L10 12" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        + '</button>'
        + '<span class="text-[10px] tabular-nums text-gray-500">' + (idx + 1) + ' / ' + total + '</span>'
        + '<button type="button" data-tomorrow-next aria-label="다음 일정" class="rounded p-0.5 text-gray-500 hover:bg-gray-200 disabled:opacity-30 dark:hover:bg-gray-700">'
        +   '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 4 L10 8 L6 12" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        + '</button>'
        + '</div>'
      : '';
    footerEl.innerHTML = ''
      + '<div class="flex items-center gap-2 text-[11px]">'
      +   '<span class="shrink-0 rounded-full bg-brand-50 px-2 py-0.5 font-semibold text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">내일 ' + timeLabel + '</span>'
      +   repeatBadge
      +   '<span class="min-w-0 flex-1 truncate text-gray-700 dark:text-gray-200" title="' + escapeHtmlMini(item.title || '') + '">' + escapeHtmlMini(item.title || '') + '</span>'
      +   (meta ? '<span class="hidden sm:inline-flex shrink-0 items-center gap-1.5 text-gray-500 dark:text-gray-400">' + meta + '</span>' : '')
      +   pagerHtml
      + '</div>';

    if (total > 1) {
      const prev = footerEl.querySelector('[data-tomorrow-prev]');
      const next = footerEl.querySelector('[data-tomorrow-next]');
      if (prev) {
        prev.disabled = idx === 0;
        prev.addEventListener('click', () => { if (idx > 0) { idx--; draw(); } });
      }
      if (next) {
        next.disabled = idx >= total - 1;
        next.addEventListener('click', () => { if (idx < total - 1) { idx++; draw(); } });
      }
    }
  };
  draw();
}

/**
 * (구) 주간 캘린더 mock — 사용 안 함.
 */
function renderWeekCalendar(container, rangeLabel) {
  const days = getMonToFri(new Date());
  const dayNames = ['월', '화', '수', '목', '금'];

  /** @type {Record<string, string[]>} */
  const eventsByYmd = {
    [formatDateYmd(days[0])]: ['주간 계획', '배포 점검'],
    [formatDateYmd(days[1])]: ['1:1 미팅'],
    [formatDateYmd(days[2])]: ['디자인 핸드오프', 'DB 백업'],
    [formatDateYmd(days[3])]: ['All-hands'],
    [formatDateYmd(days[4])]: ['주간 마무리'],
  };

  if (rangeLabel) {
    const a = days[0];
    const b = days[4];
    rangeLabel.textContent = `${a.getMonth() + 1}/${a.getDate()} – ${b.getMonth() + 1}/${b.getDate()}`;
  }

  container.innerHTML = days
    .map((date, idx) => {
      const ymd = formatDateYmd(date);
      const list = eventsByYmd[ymd] || [];
      const isToday = formatDateYmd(new Date()) === ymd;
      const ring = isToday ? 'ring-2 ring-brand-400 dark:ring-brand-500' : '';
      const shown = list.slice(0, 2);
      const more = list.length > 2 ? `<li class="truncate text-[10px] text-gray-400">+${list.length - 2}</li>` : '';
      const items = shown
        .map(
          (t) =>
            `<li class="truncate rounded-lg bg-white/90 px-1.5 py-1 text-[11px] font-medium text-gray-800 shadow-sm dark:bg-gray-900 dark:text-gray-100">${t}</li>`,
        )
        .join('');
      return `
        <div role="gridcell" class="flex min-h-0 flex-col rounded-xl border border-gray-100 bg-gray-50 p-2 dark:border-gray-600 dark:bg-gray-900/30 sm:p-2.5 ${ring}">
          <div class="mb-1.5 shrink-0 text-center">
            <div class="text-[10px] font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">${dayNames[idx]}</div>
            <div class="text-sm font-bold text-gray-900 dark:text-white">${date.getDate()}</div>
          </div>
          <ul class="min-h-0 flex-1 space-y-1 overflow-y-auto">${items || '<li class="text-[10px] text-gray-400">일정 없음</li>'}${more}</ul>
        </div>
      `;
    })
    .join('');
}

/**
 * 공지 3건
 * @param {HTMLElement} listEl
 */
function renderNotices(listEl) {
  const items = [
    { title: '5월 정기 보안 점검 안내', date: '05-12' },
    { title: '사내 헬스케어 신청 마감', date: '05-10' },
    { title: '주차장 리모델링 구간 통제', date: '05-08' },
  ];
  listEl.innerHTML = items
    .map(
      (n) => `
      <li>
        <a href="#" class="block rounded-xl border border-transparent px-2 py-2.5 hover:border-gray-200 hover:bg-gray-50 dark:hover:border-gray-600 dark:hover:bg-gray-900/50">
          <span class="line-clamp-2 font-medium text-gray-900 dark:text-white">${n.title}</span>
          <span class="mt-1 block text-xs text-gray-500 dark:text-gray-400">${n.date}</span>
        </a>
      </li>
    `,
    )
    .join('');
}

/**
 * DOM 준비 후 홈 대시보드 위젯 초기화(#home-dashboard-root 있을 때만)
 */
function initHomeDashboard() {
  const root = document.getElementById('home-dashboard-root');
  if (!root) return;

  const clockEl = document.getElementById('home-dashboard-time');
  const dateEl = document.getElementById('home-dashboard-date');
  if (clockEl) startClock(clockEl, dateEl);

  const attendanceCard = document.getElementById('home-attendance-card');

  const checkinBtn = document.getElementById('home-btn-checkin');
  const checkoutBtn = document.getElementById('home-btn-checkout');
  const cancelBtn = document.getElementById('home-btn-checkout-cancel');
  const checkinDisplay = document.getElementById('home-checkin-display');
  const checkoutDisplay = document.getElementById('home-checkout-display');
  const statusBadgeEl = document.getElementById('home-attendance-status-badge');
  const fillEl = document.getElementById('home-work-progress-fill');
  const overtimeEl = document.getElementById('home-work-progress-overtime');
  const trackEl = document.getElementById('home-work-progress-track');
  const labelEl = document.getElementById('home-work-progress-label');
  if (
    checkinBtn instanceof HTMLButtonElement &&
    checkoutBtn instanceof HTMLButtonElement &&
    checkinDisplay &&
    checkoutDisplay
  ) {
    const standardMinAttr = attendanceCard?.getAttribute('data-policy-standard-min');
    setupAttendanceCard(
      {
        checkinBtn,
        checkoutBtn,
        cancelBtn: cancelBtn instanceof HTMLButtonElement ? cancelBtn : null,
        checkinDisplay,
        checkoutDisplay,
        statusBadgeEl,
        fillEl,
        overtimeEl,
        trackEl,
        labelEl,
      },
      {
        initialClockInAt: attendanceCard?.getAttribute('data-today-clock-in-at') || undefined,
        initialClockOutAt: attendanceCard?.getAttribute('data-today-clock-out-at') || undefined,
        initialStatus: statusBadgeEl?.getAttribute('data-initial-status') || undefined,
        initialStatusLabel: statusBadgeEl?.getAttribute('data-initial-label') || undefined,
        workStart: attendanceCard?.getAttribute('data-policy-work-start') || undefined,
        workEnd: attendanceCard?.getAttribute('data-policy-work-end') || undefined,
        standardWorkMin: standardMinAttr ? parseInt(standardMinAttr, 10) : undefined,
      },
    );
  }

  // 캘린더 API 한번 호출해서 오늘/주간 위젯 둘 다 채움. (실패 시 빈 배열)
  const todayList = document.getElementById('home-today-list');
  const weekEl = document.getElementById('home-week-calendar');
  const weekLabel = document.getElementById('home-week-range-label');
<<<<<<< HEAD
  if (weekEl) renderWeekCalendar(weekEl, weekLabel);

  const noticeList = document.getElementById('home-notice-list');
  if (noticeList) renderNotices(noticeList);
=======
  const tomorrowFooter = document.getElementById('home-week-tomorrow');
  if (todayList || weekEl) {
    fetchVisibleCalendars().then((events) => {
      if (todayList) renderTodayScheduleFromEvents(todayList, events);
      if (weekEl) renderWeekCalendarFromEvents(weekEl, weekLabel, tomorrowFooter, events);
    });
  }
  // 공지사항은 MainController 가 model 로 SSR 함 (fragments-notice.html). 클라이언트 mock 불필요.

  const fab = document.getElementById('home-chat-fab');
  const backdrop = document.getElementById('home-chat-backdrop');
  const panel = document.getElementById('home-chat-panel');
  const closeBtn = document.getElementById('home-chat-close');
  const backBtn = document.getElementById('home-chat-back');
  const titleEl = document.getElementById('home-chat-title');
  const badgeEl = document.getElementById('home-chat-badge');
  const screenList = document.getElementById('home-chat-screen-list');
  const screenThread = document.getElementById('home-chat-screen-thread');
  const screenPick = document.getElementById('home-chat-screen-pick');
  const listEl = document.getElementById('home-chat-list');
  const messagesEl = document.getElementById('home-chat-messages');
  const pickListEl = document.getElementById('home-chat-pick-list');
  const footer = document.getElementById('home-chat-footer');
  const input = document.getElementById('home-chat-input');
  const sendBtn = document.getElementById('home-chat-send');
  const newBtn = document.getElementById('home-chat-new');
  if (
    fab &&
    backdrop &&
    panel &&
    closeBtn instanceof HTMLButtonElement &&
    backBtn instanceof HTMLButtonElement &&
    titleEl &&
    badgeEl &&
    screenList &&
    screenThread &&
    screenPick &&
    listEl &&
    messagesEl &&
    pickListEl &&
    footer &&
    input instanceof HTMLInputElement &&
    sendBtn instanceof HTMLButtonElement &&
    newBtn instanceof HTMLButtonElement
  ) {
    setupFloatingChat({
      fab,
      backdrop,
      panel,
      closeBtn,
      backBtn,
      titleEl,
      badgeEl,
      screenList,
      screenThread,
      screenPick,
      listEl,
      messagesEl,
      pickListEl,
      footer,
      input,
      sendBtn,
      newBtn,
    });
  }
>>>>>>> e3ce3ad4399f10a1e163a8f5fdc6e2fd7a1ed32c
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initHomeDashboard);
} else {
  initHomeDashboard();
}
