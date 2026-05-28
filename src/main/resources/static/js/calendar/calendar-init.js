import { Calendar } from "@fullcalendar/core";
import koLocale from "@fullcalendar/core/locales/ko";
import dayGridPlugin from "@fullcalendar/daygrid";
import listPlugin from "@fullcalendar/list";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import { getHolidaysForDate, ensureHolidaysForYear } from "./calendar-holidays.js";

/** @typedef {'create'|'edit'} EventModalMode */

document.addEventListener("DOMContentLoaded", async () => {
  const calendarEl = document.querySelector("#calendar");
  if (!calendarEl) return;

  // ---------------------------------------------------------------------------
  // [상수] API 경로 — CalendarApiController(@RequestMapping("/api/calendars"))·CategoryApiController 와 동일
  // 일정 생성·수정은 JSON이 아니라 application/x-www-form-urlencoded(@ModelAttribute CalendarDto)
  // ---------------------------------------------------------------------------
  const API_CALENDARS = "/api/calendars";
  /** CategoryApiController: `/api/categories` — 생성·수정은 @RequestBody CategoryDto(JSON) */
  const API_CATEGORIES = "/api/categories";
  const API_DEPTS = "/api/depts";
  const API_MEMBERS = "/api/members";

  /** 본인 회원 ID — calendar.html 의 <meta name="cal-current-member-id"> 에서 읽음 (비로그인이면 null) */
  const CURRENT_MEMBER_ID = (() => {
    const v = document.querySelector('meta[name="cal-current-member-id"]')?.getAttribute("content");
    return v && v !== "null" ? String(v) : null;
  })();

  /** @type {Calendar | null} */
  let calendarInstance = null;

  /** @type {EventModalMode} */
  let eventModalMode = "create";

  /** @type {Array<Record<string, unknown>>} */
  let categoriesCache = [];

  /** 일정 모달 콤보박스: document 전역 리스너 1회만 */
  let eventModalComboboxGlobalsBound = false;

  // ---------------------------------------------------------------------------
  // [유틸] 날짜·CSRF·문자열 이스케이프
  // ---------------------------------------------------------------------------

  /**
   * Date → datetime-local 문자열 (브라우저 로컬 기준)
   * @param {Date} date
   */
  function toDatetimeLocalValue(date) {
    if (!(date instanceof Date) || Number.isNaN(date.getTime())) return "";
    const pad = (n) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  /**
   * datetime-local 입력값을 Spring LocalDateTime 폼 바인딩용 문자열로 변환 (로컬 시각 유지)
   * @param {string} localStr
   * @returns {string|null}
   */
  function datetimeLocalToSpringParam(localStr) {
    if (!localStr || !String(localStr).trim()) return null;
    const s = String(localStr).trim();
    return s.length === 16 ? `${s}:00` : s;
  }

  /**
   * JSON의 startAt/endAt(문자열·배열 등)을 epoch ms로 변환 (뷰 범위 필터용)
   * @param {unknown} v
   */
  function calendarDtoTimeToMillis(v) {
    if (v == null) return NaN;
    if (typeof v === "string") {
      const t = new Date(v).getTime();
      return Number.isNaN(t) ? NaN : t;
    }
    if (Array.isArray(v) && v.length >= 3) {
      const y = Number(v[0]);
      const mo = Number(v[1]);
      const d = Number(v[2]);
      const h = v.length > 3 ? Number(v[3]) : 0;
      const mi = v.length > 4 ? Number(v[4]) : 0;
      const s = v.length > 5 ? Number(v[5]) : 0;
      if ([y, mo, d, h, mi, s].some((n) => Number.isNaN(n))) return NaN;
      return new Date(y, mo - 1, d, h, mi, s).getTime();
    }
    return NaN;
  }

  /** Spring Security CSRF 메타가 있으면 헤더 객체로 반환 */
  function getCsrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
    if (!token || !headerName) return {};
    return { [headerName]: token };
  }

  /**
   * 세션(로그인)이 필요한 API 호출 — `/calendar` 는 비로그인 허용이라 POST 가 302 → 로그인 HTML 로 이어지지 않도록 redirect 를 수동 처리
   * @param {string} url
   * @param {RequestInit} [init]
   */
  async function fetchSessionApi(url, init = {}) {
    const rest = { ...init };
    delete rest.redirect;
    const res = await fetch(url, {
      credentials: "same-origin",
      ...rest,
      redirect: "manual",
    });
    if (res.type === "opaqueredirect" || (res.status >= 300 && res.status < 400)) {
      throw new Error("로그인이 필요합니다. 카테고리 저장·조회는 로그인한 뒤 이용해 주세요.");
    }
    return res;
  }

  /**
   * API 실패 시 응답 본문에서 사용자 메시지 추출 (GlobalExceptionHandler JSON 의 message 등)
   * @param {Response} res fetch Response (본문은 한 번만 읽음)
   */
  async function parseApiErrorBody(res) {
    // 401(미인증)은 ErrorCode 가 아닌 Spring Security 단계라 안내 메시지를 별도로 둔다.
    if (res.status === 401) return "로그인이 필요합니다.";
    // 그 외에는 공통 헬퍼로 위임 — GlobalExceptionHandler JSON 의 message(=ErrorCode.message) 추출.
    return window.getApiErrorMessage(res, `요청 실패 (${res.status})`);
  }

  /**
   * CategoryDto JSON 본문 — CategoryApiController 의 @RequestBody CategoryDto(name, color) 와 동일 스키마
   * @param {{ name: string; color: string }} fields
   * @returns {string}
   */
  function buildCategoryRequestJson(fields) {
    return JSON.stringify({ name: fields.name, color: fields.color });
  }

  /**
   * 카테고리 hex 색 → 반투명 배경용 rgba (월 그리드에서 색 박스가 보이도록)
   * @param {string} hex #rgb 또는 #rrggbb
   * @param {number} alpha 0~1
   * @returns {string}
   */
  function hexToRgba(hex, alpha) {
    const h = String(hex ?? "").replace(/^#/, "").trim();
    if (h.length === 3) {
      const r = parseInt(h[0] + h[0], 16);
      const g = parseInt(h[1] + h[1], 16);
      const b = parseInt(h[2] + h[2], 16);
      return `rgba(${r},${g},${b},${alpha})`;
    }
    if (h.length === 6) {
      const r = parseInt(h.slice(0, 2), 16);
      const g = parseInt(h.slice(2, 4), 16);
      const b = parseInt(h.slice(4, 6), 16);
      if ([r, g, b].some((n) => Number.isNaN(n))) return `rgba(70,95,255,${alpha})`;
      return `rgba(${r},${g},${b},${alpha})`;
    }
    return `rgba(70,95,255,${alpha})`;
  }

  /** XSS 방지용 간단 이스케이프 (카테고리 이름 등) */
  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /**
   * 일정의 "공유자 목록" — owner + shareMemberIds 합집합에서 본인은 제외.
   * (A↔B↔C 공유 시 각 사용자 화면에서 자기는 빠지고 나머지만 보이도록)
   * @param {{ownerId?: string|null, shareMemberIds?: Array<string|number>}} xp
   * @returns {string[]} 회원 ID 배열 (문자열)
   */
  function computeEventViewerIds(xp) {
    const ids = new Set();
    if (xp?.ownerId) ids.add(String(xp.ownerId));
    if (Array.isArray(xp?.shareMemberIds)) {
      xp.shareMemberIds.forEach((id) => {
        if (id != null && String(id).trim() !== "") ids.add(String(id));
      });
    }
    if (CURRENT_MEMBER_ID) ids.delete(CURRENT_MEMBER_ID);
    return Array.from(ids);
  }

  /** 타원 배지 element 생성 헬퍼 — "모두" / 부서명 */
  function makeOvalBadge(text) {
    const el = document.createElement("span");
    el.className =
      "inline-flex shrink-0 items-center rounded-full bg-white/85 px-2 py-0.5 text-[10px] font-semibold text-gray-700 ring-1 ring-gray-300 dark:bg-gray-900/70 dark:text-gray-100 dark:ring-gray-600";
    el.textContent = text;
    return el;
  }

  /**
   * FullCalendar eventContent — 셀 안에 [모두/부서 배지] + [동그라미 최대 3] + [+N] + [제목] 가로 배치.
   *  - visibility = COMPANY → "모두" 배지
   *  - shareDeptIds 있음 → 부서명 배지 (여러 개면 첫 1 + "외 N")
   *  - viewerIds (owner + shareMember − 본인) → 동그라미 최대 3개 + +N
   *  - 위 모두 없음(개인/나만 보기)이면 기본 텍스트만
   * @param {{event: any}} arg
   */
  function renderCalendarEventCellContent(arg) {
    const ev = arg.event;
    const xp = ev?.extendedProps ?? {};

    const visibility = String(xp?.visibility ?? "PRIVATE");
    const shareDeptIds = Array.isArray(xp?.shareDeptIds) ? xp.shareDeptIds.map(String) : [];
    const viewerIds = computeEventViewerIds(xp);
    const title = String(ev?.title ?? "");

    const wrap = document.createElement("div");
    wrap.className = "flex w-full min-w-0 items-center gap-1.5";

    // 1) 모두 공유 → "모두" 배지
    if (visibility === "COMPANY") {
      wrap.appendChild(makeOvalBadge("모두"));
    }

    // 2) 부서 공유 → 부서명 배지(첫 1개) + 추가시 "외 N"
    if (visibility === "DEPARTMENT" && shareDeptIds.length > 0) {
      const firstName = deptDirectoryCache.get(shareDeptIds[0]) ?? "부서";
      wrap.appendChild(makeOvalBadge(firstName));
      if (shareDeptIds.length > 1) {
        wrap.appendChild(makeOvalBadge(`외 ${shareDeptIds.length - 1}`));
      }
    }

    // 3) 특정 인원 / owner 공유 → 동그라미 최대 3 + +N
    if (viewerIds.length > 0) {
      const stack = document.createElement("div");
      stack.className = "flex shrink-0 items-center";
      const VISIBLE = 3;
      const shown = viewerIds.slice(0, VISIBLE);
      const extra = Math.max(0, viewerIds.length - VISIBLE);
      shown.forEach((id, idx) => {
        const img = document.createElement("img");
        img.src = memberProfileUrl(id);
        img.alt = "";
        img.className = `h-5 w-5 ${idx === 0 ? "" : "-ml-1.5"} shrink-0 rounded-full border border-white bg-gray-200 object-cover dark:border-gray-800`;
        img.onerror = function () { this.onerror = null; this.src = "/images/user/default_user.jpg"; };
        stack.appendChild(img);
      });
      if (extra > 0) {
        const badge = document.createElement("span");
        badge.className = "-ml-1.5 inline-flex h-5 min-w-[1.25rem] shrink-0 items-center justify-center rounded-full border border-white bg-gray-500/90 px-1 text-[10px] font-semibold text-white dark:border-gray-800";
        badge.textContent = `+${extra}`;
        stack.appendChild(badge);
      }
      wrap.appendChild(stack);
    }

    const text = document.createElement("span");
    text.className = "fc-event-title min-w-0 truncate";
    text.textContent = title;
    wrap.appendChild(text);

    return { domNodes: [wrap] };
  }

  /**
   * 서버 DTO 한 건을 FullCalendar 이벤트 객체로 매핑
   * @param {Record<string, unknown>} raw
   */
  function mapCalendarDtoToFcEvent(raw) {
    const cat = raw.category && typeof raw.category === "object" ? raw.category : null;
    const color =
      (cat && typeof cat.color === "string" && cat.color) ||
      (typeof raw.categoryColor === "string" && raw.categoryColor) ||
      "#465fff";
    /** 월 뷰 블록 배경은 반투명, 테두리는 같은 색조로 조금 진하게 */
    const bgTint = hexToRgba(color, 0.22);
    const borderTint = hexToRgba(color, 0.55);
    const calendarId = raw.calendarId ?? raw.id;
    const shareMemberIds = Array.isArray(raw.shareMemberIds) ? raw.shareMemberIds : [];
    const shareDeptIds = Array.isArray(raw.shareDeptIds) ? raw.shareDeptIds : [];
    const ownerId = raw.memberId != null ? String(raw.memberId) : null;
    const ownerName = typeof raw.memberName === "string" ? raw.memberName : "";
    // 반복 일정 occurrence 는 같은 calendarId 라도 FullCalendar 상에서 별개 이벤트로 그려야 함
    // → fc id 에 occurrenceIndex 를 suffix 로 붙여 유니크화. 클릭 시 fetch 는 extendedProps.calendarId 사용.
    const occIdx = raw.__occurrenceIndex;
    const fcId = occIdx != null ? `${calendarId}#${occIdx}` : String(calendarId ?? "");

    return {
      id: fcId,
      title: typeof raw.title === "string" ? raw.title : "",
      start: raw.startAt ?? raw.start,
      end: raw.endAt ?? raw.end ?? raw.startAt ?? raw.start,
      allDay: Boolean(raw.allDay),
      backgroundColor: bgTint,
      borderColor: borderTint,
      extendedProps: {
        calendarId,
        description: raw.description ?? "",
        categoryId: cat?.categoryId ?? raw.categoryId ?? null,
        categoryColor: color,
        categoryBgTint: bgTint,
        categoryBorderTint: borderTint,
        location: raw.location ?? "",
        visibility: raw.visibility ?? "PRIVATE",
        allDay: Boolean(raw.allDay),
        isRepeat: Boolean(raw.isRepeat),
        repeatType: raw.repeatType ?? null,
        repeatEndAt: raw.repeatEndAt ?? null,
        repeatWeekdays: raw.repeatWeekdays ?? null,
        repeatMonthDays: raw.repeatMonthDays ?? null,
        isAlert: Boolean(raw.isAlert),
        alertMinutesBefore: raw.alertMinutesBefore ?? 15,
        shareMemberIds,
        shareDeptIds,
        ownerId,
        ownerName,
      },
    };
  }

  // ---------------------------------------------------------------------------
  // [DOM 참조] 일정 폼
  // ---------------------------------------------------------------------------
  const eventModal = document.getElementById("eventModal");
  const eventForm = document.getElementById("calendarEventForm");
  const eventModalLabel = document.getElementById("eventModalLabel");
  const formErrorEl = document.getElementById("calendar-event-form-error");

  const btnSubmit = document.getElementById("btn-calendar-submit");
  const btnUpdate = document.getElementById("btn-calendar-update");
  const btnDelete = document.getElementById("btn-calendar-delete");
  const btnCancel = document.getElementById("btn-calendar-cancel");

  const visibilityRadios = () =>
    eventForm ? Array.from(eventForm.querySelectorAll('input[name="visibility"]')) : [];

  // ---------------------------------------------------------------------------
  // [폼] 검증 · 수집 · 리셋 · 채우기
  // ---------------------------------------------------------------------------

  function showEventFormError(message) {
    if (!formErrorEl) return;
    formErrorEl.textContent = message;
    formErrorEl.classList.remove("hidden");
  }

  function clearEventFormError() {
    if (!formErrorEl) return;
    formErrorEl.textContent = "";
    formErrorEl.classList.add("hidden");
  }

  function validateEventForm() {
    const titleInput = document.getElementById("calendar-title");
    const startInput = document.getElementById("calendar-start-at");
    const endInput = document.getElementById("calendar-end-at");
    const title = titleInput?.value?.trim() ?? "";
    if (!title) {
      showEventFormError("제목을 입력해 주세요.");
      titleInput?.focus();
      return false;
    }
    const startVal = startInput?.value ?? "";
    if (!startVal) {
      showEventFormError("시작 일시를 선택해 주세요.");
      startInput?.focus();
      return false;
    }
    const endVal = endInput?.value ?? "";
    if (endVal) {
      const s = new Date(startVal).getTime();
      const e = new Date(endVal).getTime();
      if (!Number.isNaN(s) && !Number.isNaN(e) && e < s) {
        showEventFormError("종료 일시는 시작 일시보다 빠를 수 없습니다.");
        endInput?.focus();
        return false;
      }
    }
    const vis =
      eventForm?.querySelector('input[name="visibility"]:checked')?.value ?? "PRIVATE";
    if (vis === "DEPARTMENT") {
      const allDeptSelected = !!document.querySelector(
        '#calendarDeptPanel input[data-select-all="calendarDeptId"]:checked',
      );
      // "전체 선택" 체크 시에는 개별 0개여도 백엔드가 ALL 로 해석하므로 통과
      const n = getSelectedCalendarDeptIds().length;
      if (n === 0 && !allDeptSelected) {
        showEventFormError('공개 범위가 "특정 부서"일 때 공유 부서를 한 개 이상 선택해 주세요.');
        return false;
      }
    }
    if (vis === "SPECIFIC") {
      const n = getSelectedCalendarMemberIds().length;
      if (n === 0) {
        showEventFormError('공개 범위가 "특정 인원"일 때 공유 멤버를 한 명 이상 선택해 주세요.');
        document.getElementById("calendar-member-search")?.focus();
        return false;
      }
    }
    const isRepeat = document.getElementById("calendar-is-repeat")?.checked;
    if (isRepeat) {
      const reEnd = document.getElementById("calendar-repeat-end-at")?.value ?? "";
      if (!reEnd.trim()) {
        showEventFormError("반복 일정일 때 반복 종료 일시를 입력해 주세요.");
        document.getElementById("calendar-repeat-end-at")?.focus();
        return false;
      }
    }
    clearEventFormError();
    return true;
  }

  /**
   * Spring @ModelAttribute(CalendarDto) 바인딩용 x-www-form-urlencoded 본문 생성
   * - 빈 문자열은 Long·LocalDateTime 변환 시 BindException(400)이 나므로 calendarId·categoryId·endAt 등은 값이 있을 때만 넣는다.
   * - 체크박스는 name 이 `isRepeat`·`isAlert`·`allDay` 이고, 미체크 시 FormData 에서 빠지므로 false 를 명시한다.
   * @returns {URLSearchParams}
   */
  function buildCalendarModelAttributeParams() {
    const p = new URLSearchParams();
    const form = document.getElementById("calendarEventForm");
    if (!(form instanceof HTMLFormElement)) return p;

    const skipIfEmpty = new Set([
      "calendarId",
      "categoryId",
      "endAt",
      "description",
      "location",
      "repeatEndAt",
    ]);

    const fd = new FormData(form);
    for (const [key, val] of fd.entries()) {
      const s = typeof val === "string" ? val.trim() : String(val);
      if (key === "startAt" || key === "endAt" || key === "repeatEndAt") continue;
      if (skipIfEmpty.has(key) && s === "") continue;
      if (key === "isRepeat" || key === "isAlert" || key === "allDay") {
        p.append(key, s === "on" ? "true" : s === "" ? "false" : s);
        continue;
      }
      if (s === "") continue;
      p.append(key, s);
    }

    const startNorm = datetimeLocalToSpringParam(document.getElementById("calendar-start-at")?.value ?? "");
    if (startNorm) p.set("startAt", startNorm);

    const endNorm = datetimeLocalToSpringParam(document.getElementById("calendar-end-at")?.value ?? "");
    if (endNorm) p.set("endAt", endNorm);

    const allDay = document.getElementById("calendar-all-day")?.checked ?? false;
    if (!p.has("allDay")) p.set("allDay", String(allDay));

    const isRepeat = document.getElementById("calendar-is-repeat")?.checked ?? false;
    if (!p.has("isRepeat")) p.set("isRepeat", String(isRepeat));
    if (isRepeat) {
      const repeatType = document.getElementById("calendar-repeat-type")?.value ?? "DAILY";
      p.set("repeatType", repeatType);
      const re = datetimeLocalToSpringParam(document.getElementById("calendar-repeat-end-at")?.value ?? "");
      if (re) p.set("repeatEndAt", re);
      // 매주/매월 다중 선택 비트마스크 (0/빈 값이면 서버에서 시작일 기준 단일 반복)
      const wkMask = repeatType === "WEEKLY" ? collectCalendarRepeatWeekdaysMask() : 0;
      const mdMask = repeatType === "MONTHLY" ? collectCalendarRepeatMonthDaysMask() : 0;
      if (wkMask > 0) p.set("repeatWeekdays", String(wkMask));
      else p.delete("repeatWeekdays");
      if (mdMask > 0) p.set("repeatMonthDays", String(mdMask));
      else p.delete("repeatMonthDays");
    } else {
      p.delete("repeatType");
      p.delete("repeatEndAt");
      p.delete("repeatWeekdays");
      p.delete("repeatMonthDays");
    }

    const isAlert = document.getElementById("calendar-is-alert")?.checked ?? false;
    if (!p.has("isAlert")) p.set("isAlert", String(isAlert));
    if (isAlert) {
      p.set("alertMinutesBefore", String(document.getElementById("calendar-alert-minutes")?.value ?? "15"));
    } else {
      p.delete("alertMinutesBefore");
    }

    const visibility =
      form.querySelector('input[name="visibility"]:checked')?.value ?? "PRIVATE";
    p.set("visibility", visibility);

    const catVal = document.getElementById("calendar-category-id")?.value?.trim() ?? "";
    if (catVal) p.set("categoryId", catVal);
    else p.delete("categoryId");

    p.delete("shareDeptIds");
    if (visibility === "DEPARTMENT") {
      getSelectedCalendarDeptIds().forEach((idStr) => {
        const id = Number(idStr);
        if (Number.isFinite(id)) p.append("shareDeptIds", String(id));
      });
    }
    p.delete("shareMemberIds");
    if (visibility === "SPECIFIC") {
      getSelectedCalendarMemberIds().forEach((idStr) => {
        const id = Number(idStr);
        if (Number.isFinite(id)) p.append("shareMemberIds", String(id));
      });
    }

    return p;
  }

  /**
   * 반복 일정 펼치기 — DB 컬럼은 (repeat_type enum + repeat_end_at) 만 사용하는 단일 패턴 모델.
   * DAILY  : 매일 (시작일부터 1일 간격)
   * WEEKLY : 매주 같은 요일 (시작일의 요일 기준)
   * MONTHLY: 매월 같은 일 (시작일의 일자 기준 — 다음 달에 해당 일자가 없으면 그 달 말일)
   * YEARLY : 매년 같은 월·일 (윤년 2/29 → 평년 2/28 보정)
   *
   * @param {number} baseMs 원본 시작 시각(ms)
   * @param {"DAILY"|"WEEKLY"|"MONTHLY"|"YEARLY"} type
   * @param {number} i  0=원본, 1=첫 반복, ...
   * @returns {Date}
   */
  function nextOccurrenceDate(baseMs, type, i) {
    if (i === 0) return new Date(baseMs);
    const d = new Date(baseMs);
    switch (type) {
      case "DAILY":
        return new Date(baseMs + i * 86400000);
      case "WEEKLY":
        return new Date(baseMs + i * 7 * 86400000);
      case "MONTHLY": {
        const result = new Date(
          d.getFullYear(), d.getMonth() + i, d.getDate(),
          d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds(),
        );
        // 5월 31일 → 6월 31일은 7월 1일로 자동 넘어감 → 6월 30일로 보정
        if (result.getDate() !== d.getDate()) result.setDate(0);
        return result;
      }
      case "YEARLY": {
        const result = new Date(
          d.getFullYear() + i, d.getMonth(), d.getDate(),
          d.getHours(), d.getMinutes(), d.getSeconds(), d.getMilliseconds(),
        );
        // 윤년 2/29 → 평년 3/1 자동 넘어감 → 2/28 보정
        if (result.getDate() !== d.getDate()) result.setDate(0);
        return result;
      }
      default:
        return new Date(baseMs);
    }
  }

  /** JS Date.getDay() (Sun=0..Sat=6) → DB 비트마스크 (MON=1, TUE=2, ..., SUN=64) */
  function jsDayToMaskBit(jsDay) {
    // Mon..Sun = 1,2,4,8,16,32,64
    const order = [64, 1, 2, 4, 8, 16, 32]; // index = getDay() (Sun=0)
    return order[jsDay] ?? 0;
  }

  /**
   * 반복 CalendarDto 한 건 → 뷰 범위와 겹치는 occurrence DTO 들로 펼침.
   * - WEEKLY + repeatWeekdays(비트마스크) → 시작일~종료일 사이에서 매주 해당 요일들만
   * - MONTHLY + repeatMonthDays(비트마스크) → 매월 해당 일자들만 (없으면 그 달 말일로 보정)
   * - DAILY / YEARLY 또는 마스크가 0/null → 기존 단일 패턴 (시작일 기준)
   *
   * 비반복 일정이거나 type 이 비정상이면 원본 한 건 반환.
   * 안전 상한: 최대 1500개 occurrence / repeat_end_at 없으면 뷰 끝 + 1년 안에서.
   * @param {Record<string, unknown>} raw
   * @param {Date} rangeStart
   * @param {Date} rangeEndExclusive
   * @returns {Record<string, unknown>[]}
   */
  function expandRepeatingCalendarDto(raw, rangeStart, rangeEndExclusive) {
    const isRep = Boolean(raw?.isRepeat ?? raw?.repeat);
    const rawType = String(raw?.repeatType ?? "").toUpperCase();
    if (!isRep || !["DAILY", "WEEKLY", "MONTHLY", "YEARLY"].includes(rawType)) return [raw];

    const baseStart = calendarDtoTimeToMillis(raw.startAt ?? raw.start);
    const baseEnd = calendarDtoTimeToMillis(raw.endAt ?? raw.end ?? raw.startAt ?? raw.start);
    if (Number.isNaN(baseStart)) return [raw];
    const duration = Number.isNaN(baseEnd) ? 0 : Math.max(0, baseEnd - baseStart);

    const repeatEndMs = calendarDtoTimeToMillis(raw.repeatEndAt);
    const HARD_CAP = rangeEndExclusive.getTime() + 366 * 86400000;
    const ceiling = Number.isNaN(repeatEndMs) ? HARD_CAP : Math.min(repeatEndMs, HARD_CAP);

    const weekMask = Number(raw.repeatWeekdays ?? 0) || 0;
    const monthMask = Number(raw.repeatMonthDays ?? 0) || 0;

    const out = [];
    const MAX = 1500;

    /** occurrence push helper */
    const push = (occMs, idx) => {
      if (occMs + duration >= rangeStart.getTime() && occMs < rangeEndExclusive.getTime()) {
        out.push({
          ...raw,
          startAt: new Date(occMs).toISOString(),
          endAt: new Date(occMs + duration).toISOString(),
          __occurrenceIndex: idx,
        });
      }
    };

    if (rawType === "WEEKLY" && weekMask > 0) {
      // 시작일이 속한 주의 월요일 00:00:00 (시간/분/초는 시작일 그대로 유지) 부터 한 주씩 진행
      const baseDate = new Date(baseStart);
      const baseHours = baseDate.getHours(), baseMins = baseDate.getMinutes(), baseSecs = baseDate.getSeconds(), baseMs = baseDate.getMilliseconds();
      // 월요일 기준 정렬: getDay()=Sun(0),Mon(1)..Sat(6) → Mon 기준 0~6
      const monIndex = (baseDate.getDay() + 6) % 7;
      const weekStart = new Date(baseDate);
      weekStart.setDate(baseDate.getDate() - monIndex);
      weekStart.setHours(baseHours, baseMins, baseSecs, baseMs);

      let idx = 0;
      outer: for (let w = 0; w < 260 /* 5년치 주 */ ; w++) {
        for (let dow = 0; dow < 7; dow++) {
          const bit = [1, 2, 4, 8, 16, 32, 64][dow]; // Mon..Sun
          if ((weekMask & bit) !== bit) continue;
          const d = new Date(weekStart);
          d.setDate(weekStart.getDate() + w * 7 + dow);
          const ms = d.getTime();
          if (ms < baseStart) continue; // 시작일 이전 occurrence 제외
          if (ms > ceiling) break outer;
          push(ms, idx++);
          if (idx >= MAX) break outer;
        }
        const probe = weekStart.getTime() + w * 7 * 86400000;
        if (probe > rangeEndExclusive.getTime() + 7 * 86400000) break;
      }
    } else if (rawType === "MONTHLY" && monthMask > 0) {
      // 매월 monthMask 에 표시된 일자(여러 개)들. 시작 월~종료까지 월 단위로 순회.
      const baseDate = new Date(baseStart);
      const baseHours = baseDate.getHours(), baseMins = baseDate.getMinutes(), baseSecs = baseDate.getSeconds(), baseMsField = baseDate.getMilliseconds();
      let idx = 0;
      outer: for (let m = 0; m < 120 /* 10년치 월 */ ; m++) {
        for (let day = 1; day <= 31; day++) {
          const bit = 1 << (day - 1);
          if ((monthMask & bit) !== bit) continue;
          const occ = new Date(baseDate.getFullYear(), baseDate.getMonth() + m, day, baseHours, baseMins, baseSecs, baseMsField);
          // 해당 월에 그 일자가 없으면 다음 달로 넘어가버림 → 말일로 보정
          if (occ.getDate() !== day) occ.setDate(0);
          const ms = occ.getTime();
          if (ms < baseStart) continue;
          if (ms > ceiling) break outer;
          push(ms, idx++);
          if (idx >= MAX) break outer;
        }
        const probe = new Date(baseDate.getFullYear(), baseDate.getMonth() + m, 1).getTime();
        if (probe > rangeEndExclusive.getTime() + 31 * 86400000) break;
      }
    } else {
      // 기존 단일 패턴 (DAILY / WEEKLY 단일 / MONTHLY 단일 / YEARLY)
      for (let i = 0; i < MAX; i++) {
        const occ = nextOccurrenceDate(baseStart, /** @type {any} */ (rawType), i);
        const occMs = occ.getTime();
        if (occMs > ceiling) break;
        push(occMs, i);
        if (occMs > rangeEndExclusive.getTime()) break;
      }
    }

    return out.length > 0 ? out : [raw];
  }

  /**
   * 서버가 기간 필터 없이 전체 목록을 주므로, FullCalendar 뷰 구간과 겹치는 DTO만 남긴다.
   * @param {Record<string, unknown>[]} rows
   * @param {Date} rangeStart
   * @param {Date} rangeEndExclusive FullCalendar info.end (배타적)
   */
  function filterCalendarDtosByViewRange(rows, rangeStart, rangeEndExclusive) {
    const rs = rangeStart.getTime();
    const re = rangeEndExclusive.getTime();
    return rows.filter((raw) => {
      const startAt = raw.startAt ?? raw.start;
      const endAt = raw.endAt ?? raw.end ?? startAt;
      const es = calendarDtoTimeToMillis(startAt);
      const ee = calendarDtoTimeToMillis(endAt);
      if (Number.isNaN(es)) return false;
      const eeff = Number.isNaN(ee) ? es : ee;
      return es < re && eeff > rs;
    });
  }

  /**
   * GET /api/calendars/{id} 응답(CalendarDto JSON)으로 일정 폼 채우기
   * @param {Record<string, unknown>} raw
   */
  function fillFormFromCalendarDto(raw) {
    const cid = raw.calendarId != null ? String(raw.calendarId) : raw.id != null ? String(raw.id) : "";
    const hiddenId = document.getElementById("calendar-id");
    if (hiddenId) hiddenId.value = cid;

    const titleEl = document.getElementById("calendar-title");
    if (titleEl) titleEl.value = typeof raw.title === "string" ? raw.title : "";

    const desc = document.getElementById("calendar-description");
    if (desc) desc.value = typeof raw.description === "string" ? raw.description : "";

    const loc = document.getElementById("calendar-location");
    if (loc) loc.value = typeof raw.location === "string" ? raw.location : "";

    const categorySel = document.getElementById("calendar-category-id");
    if (categorySel instanceof HTMLSelectElement) {
      categorySel.value = raw.categoryId != null ? String(raw.categoryId) : "";
    }

    const allDayEl = document.getElementById("calendar-all-day");
    const allDay = Boolean(raw.allDay);
    if (allDayEl) allDayEl.checked = allDay;

    const startMs = calendarDtoTimeToMillis(raw.startAt);
    const endMs = calendarDtoTimeToMillis(raw.endAt);
    const s = document.getElementById("calendar-start-at");
    if (s && !Number.isNaN(startMs)) s.value = toDatetimeLocalValue(new Date(startMs));
    const e = document.getElementById("calendar-end-at");
    if (e && !Number.isNaN(endMs)) {
      const displayEnd = allDay ? new Date(endMs - 1) : new Date(endMs);
      e.value = toDatetimeLocalValue(displayEnd);
    }

    const isRepeatEl = document.getElementById("calendar-is-repeat");
    if (isRepeatEl) isRepeatEl.checked = Boolean(raw.isRepeat ?? raw.repeat);
    const rt = document.getElementById("calendar-repeat-type");
    if (rt && raw.repeatType) rt.value = String(raw.repeatType);

    const reEnd = document.getElementById("calendar-repeat-end-at");
    const reMs = calendarDtoTimeToMillis(raw.repeatEndAt);
    if (reEnd && !Number.isNaN(reMs)) reEnd.value = toDatetimeLocalValue(new Date(reMs));

    // 매주/매월 다중 선택 비트마스크 → 칩 상태 동기화
    applyCalendarRepeatWeekdaysMask(Number(raw.repeatWeekdays ?? 0));
    applyCalendarRepeatMonthDaysMask(Number(raw.repeatMonthDays ?? 0));

    const isAlertEl = document.getElementById("calendar-is-alert");
    if (isAlertEl) isAlertEl.checked = Boolean(raw.isAlert ?? raw.alert);
    const am = document.getElementById("calendar-alert-minutes");
    if (am && raw.alertMinutesBefore != null) am.value = String(raw.alertMinutesBefore);

    const vis = typeof raw.visibility === "string" ? raw.visibility : "PRIVATE";
    visibilityRadios().forEach((r) => {
      if (r instanceof HTMLInputElement) r.checked = r.value === vis;
    });

    const shareDeptIds = Array.isArray(raw.shareDeptIds) ? raw.shareDeptIds : [];
    const shareMemberIds = Array.isArray(raw.shareMemberIds) ? raw.shareMemberIds : [];

    applySharedDeptSelectionToPanel(shareDeptIds);
    applySharedMemberSelectionToPanel(shareMemberIds);

    syncAllCalendarComboboxesFromSelects();
  }

  /**
   * 부서 패널의 체크 상태를 주어진 ID 배열로 동기화
   * @param {Array<number|string>} ids
   */
  function applySharedDeptSelectionToPanel(ids) {
    const set = new Set(ids.map(String));
    const selectAll = document.querySelector('input[data-select-all="calendarDeptId"]');
    document
      .querySelectorAll('#calendarDeptPanel input[data-multi-group="calendarDeptId"]')
      .forEach((cb) => {
        if (cb instanceof HTMLInputElement) cb.checked = set.has(cb.value);
      });
    if (selectAll instanceof HTMLInputElement) {
      selectAll.checked = set.size === 0;
    }
    refreshCalendarDeptSummary();
  }

  /**
   * 선택된 멤버 ID 집합을 주어진 ID 배열로 교체 — 편집 모드/리셋 시 호출.
   * 미리보기·상세 리스트·현재 검색 결과 row 체크 상태도 함께 동기화.
   * 메타 정보가 캐시에 없는 ID 가 있으면 백그라운드로 한번 조회해 채운다.
   * @param {Array<number|string>} ids
   */
  function applySharedMemberSelectionToPanel(ids) {
    selectedMemberIds.clear();
    ids.forEach((raw) => {
      const id = String(raw ?? "").trim();
      if (id) selectedMemberIds.add(id);
    });
    // 현재 그려져 있는 검색 결과 row 들의 버튼/배경도 동기화
    document
      .querySelectorAll('#calendar-member-results [data-result-row-id]')
      .forEach((el) => {
        if (el instanceof HTMLElement && el.dataset.resultRowId) {
          updateMemberResultRow(el.dataset.resultRowId);
        }
      });
    refreshSelectedMembersUI();

    // 캐시에 없는 멤버가 있으면 회사 전체 검색 한 번 (q="") 후 캐시 채워서 다시 렌더
    const missing = Array.from(selectedMemberIds).filter((id) => !memberDirectoryCache.has(id));
    if (missing.length > 0) {
      fetchAndCacheMemberMeta().then(() => refreshSelectedMembersUI());
    }
  }

  /** 회사 전체 회원 메타 한 번 로딩 — 편집 모드 진입 시 선택된 멤버 메타 부족 보완 */
  async function fetchAndCacheMemberMeta() {
    try {
      const res = await fetchSessionApi(API_MEMBERS, {
        headers: { Accept: "application/json", ...getCsrfHeaders() },
      });
      if (!res.ok) return;
      const list = await res.json();
      const arr = Array.isArray(list) ? list : [];
      arr.forEach((row) => {
        const id = row?.memberId ?? row?.id;
        if (id == null) return;
        memberDirectoryCache.set(String(id), {
          name: String(row?.name ?? "멤버"),
          deptName: row?.deptName ?? null,
          positionName: row?.positionName ?? null,
        });
      });
    } catch {
      /* 무시 */
    }
  }

  /** @param {boolean} [clearCalendarId=true] */
  function resetEventForm(clearCalendarId = true) {
    clearEventFormError();
    if (clearCalendarId && document.getElementById("calendar-id")) {
      document.getElementById("calendar-id").value = "";
    }
    const fields = [
      ["calendar-title", ""],
      ["calendar-description", ""],
      ["calendar-location", ""],
      ["calendar-start-at", ""],
      ["calendar-end-at", ""],
      ["calendar-repeat-end-at", ""],
    ];
    fields.forEach(([id, val]) => {
      const el = document.getElementById(id);
      if (el) el.value = val;
    });
    const cat = document.getElementById("calendar-category-id");
    if (cat) cat.value = "";
    const allDay = document.getElementById("calendar-all-day");
    if (allDay) allDay.checked = false;
    const isRepeat = document.getElementById("calendar-is-repeat");
    if (isRepeat) isRepeat.checked = false;
    const isAlert = document.getElementById("calendar-is-alert");
    if (isAlert) isAlert.checked = false;
    const repeatType = document.getElementById("calendar-repeat-type");
    if (repeatType) repeatType.value = "DAILY";
    applyCalendarRepeatWeekdaysMask(0);
    applyCalendarRepeatMonthDaysMask(0);
    const alertMin = document.getElementById("calendar-alert-minutes");
    if (alertMin) alertMin.value = "15";

    visibilityRadios().forEach((r) => {
      if (r instanceof HTMLInputElement) r.checked = r.value === "PRIVATE";
    });

    applySharedDeptSelectionToPanel([]);
    applySharedMemberSelectionToPanel([]);
    const searchInput = document.getElementById("calendar-member-search");
    if (searchInput instanceof HTMLInputElement) searchInput.value = "";
    const resultsBox = document.getElementById("calendar-member-results");
    if (resultsBox) resultsBox.innerHTML = "";
    showMemberResults(false);

    syncRepeatSection();
    syncAlertSection();
    syncVisibilityShares();
    syncAllCalendarComboboxesFromSelects();
  }

  /**
   * FullCalendar 이벤트로 폼 채우기 (수정 모드)
   * @param {*} fcEvent
   */
  function fillFormFromFcEvent(fcEvent) {
    const xp = fcEvent.extendedProps || {};
    const cid =
      xp.calendarId != null ? String(xp.calendarId) : fcEvent.id ? String(fcEvent.id) : "";
    const hiddenId = document.getElementById("calendar-id");
    if (hiddenId) hiddenId.value = cid;

    const titleEl = document.getElementById("calendar-title");
    if (titleEl) titleEl.value = fcEvent.title ?? "";

    const desc = document.getElementById("calendar-description");
    if (desc) desc.value = typeof xp.description === "string" ? xp.description : "";

    const loc = document.getElementById("calendar-location");
    if (loc) loc.value = typeof xp.location === "string" ? xp.location : "";

    const categorySel = document.getElementById("calendar-category-id");
    if (categorySel instanceof HTMLSelectElement) {
      categorySel.value = xp.categoryId != null ? String(xp.categoryId) : "";
    }

    const allDayEl = document.getElementById("calendar-all-day");
    const allDay = Boolean(fcEvent.allDay ?? xp.allDay);
    if (allDayEl) allDayEl.checked = allDay;

    const start = fcEvent.start ?? null;
    const end = fcEvent.end ?? null;
    if (start) {
      const s = document.getElementById("calendar-start-at");
      if (s) s.value = toDatetimeLocalValue(start);
    }
    if (end) {
      const displayEnd = allDay ? new Date(end.getTime() - 1) : end;
      const e = document.getElementById("calendar-end-at");
      if (e) e.value = toDatetimeLocalValue(displayEnd);
    }

    const isRepeatEl = document.getElementById("calendar-is-repeat");
    if (isRepeatEl) isRepeatEl.checked = Boolean(xp.isRepeat);
    const rt = document.getElementById("calendar-repeat-type");
    if (rt && xp.repeatType) rt.value = String(xp.repeatType);

    // 비트마스크 동기화 (편집 모달 진입 시) — 단건 fetch 가 정확한 값을 덮어쓰므로 여기서는 캐싱된 추정치
    applyCalendarRepeatWeekdaysMask(Number(xp.repeatWeekdays ?? 0));
    applyCalendarRepeatMonthDaysMask(Number(xp.repeatMonthDays ?? 0));

    const reEnd = document.getElementById("calendar-repeat-end-at");
    if (reEnd && xp.repeatEndAt) {
      const d = new Date(String(xp.repeatEndAt));
      if (!Number.isNaN(d.getTime())) reEnd.value = toDatetimeLocalValue(d);
    }

    const isAlertEl = document.getElementById("calendar-is-alert");
    if (isAlertEl) isAlertEl.checked = Boolean(xp.isAlert);
    const am = document.getElementById("calendar-alert-minutes");
    if (am && xp.alertMinutesBefore != null) am.value = String(xp.alertMinutesBefore);

    const vis = typeof xp.visibility === "string" ? xp.visibility : "PRIVATE";
    visibilityRadios().forEach((r) => {
      if (r instanceof HTMLInputElement) r.checked = r.value === vis;
    });

    if (Array.isArray(xp.shareDeptIds)) {
      applySharedDeptSelectionToPanel(xp.shareDeptIds);
    }
    if (Array.isArray(xp.shareMemberIds)) {
      applySharedMemberSelectionToPanel(xp.shareMemberIds);
    }

    syncAllCalendarComboboxesFromSelects();
  }

  /** @param {EventModalMode} mode */
  function setEventModalMode(mode) {
    eventModalMode = mode;
    if (!eventModalLabel) return;
    eventModalLabel.textContent = mode === "edit" ? "일정 수정" : "일정 등록";

    if (btnSubmit) btnSubmit.classList.toggle("hidden", mode === "edit");
    if (btnUpdate) btnUpdate.classList.toggle("hidden", mode !== "edit");
    if (btnDelete) btnDelete.classList.toggle("hidden", mode !== "edit");
  }

  /**
   * 일정 모달 표시
   * Tailwind `hidden`은 display:none !important 이므로 인라인 style로는 열리지 않음 → classList로 토글
   */
  function openEventModal() {
    closeAllCalendarComboboxPanels();
    eventModal?.classList.remove("hidden");
  }

  function closeEventModal() {
    closeAllCalendarComboboxPanels();
    eventModal?.classList.add("hidden");
    resetEventForm();
    setEventModalMode("create");
  }

  /** 반복 필드 영역 활성/비활성 + 유형별 요일/일자 패널 노출 */
  function syncRepeatSection() {
    const on = document.getElementById("calendar-is-repeat")?.checked ?? false;
    const box = document.getElementById("calendar-repeat-fields");
    if (!box) return;
    box.classList.toggle("opacity-50", !on);
    box.classList.toggle("pointer-events-none", !on);

    const type = document.getElementById("calendar-repeat-type")?.value ?? "DAILY";
    const wkWrap = document.getElementById("calendar-repeat-weekdays-wrap");
    const mdWrap = document.getElementById("calendar-repeat-monthdays-wrap");
    const yrWrap = document.getElementById("calendar-repeat-yearly-wrap");
    if (wkWrap) wkWrap.classList.toggle("hidden", !(on && type === "WEEKLY"));
    if (mdWrap) mdWrap.classList.toggle("hidden", !(on && type === "MONTHLY"));
    if (yrWrap) yrWrap.classList.toggle("hidden", !(on && type === "YEARLY"));
    if (on && type === "YEARLY") refreshCalendarRepeatYearlyLabel();
  }

  /** 매년 반복 안내 라벨 — 시작일 input 값을 보고 "M월 D일" 형태로 갱신 */
  function refreshCalendarRepeatYearlyLabel() {
    const label = document.getElementById("calendar-repeat-yearly-label");
    if (!label) return;
    const startVal = /** @type {HTMLInputElement|null} */ (document.getElementById("calendar-start-at"))?.value;
    if (!startVal) {
      label.textContent = "시작일을 선택해 주세요";
      return;
    }
    const d = new Date(startVal);
    if (Number.isNaN(d.getTime())) {
      label.textContent = "시작일을 선택해 주세요";
      return;
    }
    label.textContent = `${d.getMonth() + 1}월 ${d.getDate()}일`;
  }

  /**
   * 1~31일 미니 그리드 한 번만 동적 생성. 클릭 시 aria-pressed 토글로 선택 상태 유지.
   * 비트마스크는 폼 수집 시점에 DOM 에서 직접 합산하므로 별도 상태 변수 불필요.
   */
  function ensureCalendarRepeatMonthDaysGrid() {
    const grid = document.getElementById("calendar-repeat-monthdays-grid");
    if (!grid || grid.dataset.calInit === "1") return;
    grid.dataset.calInit = "1";
    let html = "";
    for (let d = 1; d <= 31; d++) {
      const bit = 1 << (d - 1);
      html += `<button type="button" data-repeat-monthday="${bit}" aria-pressed="false" class="cal-monthday-btn">${d}</button>`;
    }
    grid.innerHTML = html;
  }

  /** 요일/일자 칩 클릭 위임 — aria-pressed 토글 */
  function bindCalendarRepeatChipHandlers() {
    const repeatBox = document.getElementById("calendar-repeat-fields");
    if (!repeatBox || repeatBox.dataset.calChipBound === "1") return;
    repeatBox.dataset.calChipBound = "1";
    repeatBox.addEventListener("click", (e) => {
      const t = e.target;
      if (!(t instanceof HTMLElement)) return;
      const chip = t.closest("[data-repeat-weekday], [data-repeat-monthday]");
      if (!(chip instanceof HTMLElement)) return;
      const pressed = chip.getAttribute("aria-pressed") === "true";
      chip.setAttribute("aria-pressed", pressed ? "false" : "true");
    });
  }

  /** 현재 선택된 요일 칩들의 비트마스크 합 (없으면 0) */
  function collectCalendarRepeatWeekdaysMask() {
    let mask = 0;
    document.querySelectorAll('[data-repeat-weekday][aria-pressed="true"]').forEach((el) => {
      const v = Number(/** @type {HTMLElement} */ (el).dataset.repeatWeekday ?? "0");
      if (Number.isFinite(v)) mask |= v;
    });
    return mask;
  }

  /** 현재 선택된 일자 칩들의 비트마스크 합 (없으면 0) */
  function collectCalendarRepeatMonthDaysMask() {
    let mask = 0;
    document.querySelectorAll('[data-repeat-monthday][aria-pressed="true"]').forEach((el) => {
      const v = Number(/** @type {HTMLElement} */ (el).dataset.repeatMonthday ?? "0");
      if (Number.isFinite(v)) mask |= v;
    });
    return mask;
  }

  /** 비트마스크 값으로 요일 칩들의 aria-pressed 동기화 */
  function applyCalendarRepeatWeekdaysMask(mask) {
    const m = Number(mask) || 0;
    document.querySelectorAll("[data-repeat-weekday]").forEach((el) => {
      const v = Number(/** @type {HTMLElement} */ (el).dataset.repeatWeekday ?? "0");
      el.setAttribute("aria-pressed", String((m & v) === v && v !== 0));
    });
  }

  /** 비트마스크 값으로 일자 칩들의 aria-pressed 동기화 */
  function applyCalendarRepeatMonthDaysMask(mask) {
    const m = Number(mask) || 0;
    document.querySelectorAll("[data-repeat-monthday]").forEach((el) => {
      const v = Number(/** @type {HTMLElement} */ (el).dataset.repeatMonthday ?? "0");
      el.setAttribute("aria-pressed", String((m & v) === v && v !== 0));
    });
  }

  /** 알림 분 선택 활성/비활성 */
  function syncAlertSection() {
    const on = document.getElementById("calendar-is-alert")?.checked ?? false;
    const box = document.getElementById("calendar-alert-fields");
    if (!box) return;
    box.classList.toggle("opacity-50", !on);
    box.classList.toggle("pointer-events-none", !on);
  }

  /** 공개 범위에 따른 공유 UI 표시 */
  function syncVisibilityShares() {
    const vis =
      eventForm?.querySelector('input[name="visibility"]:checked')?.value ?? "PRIVATE";
    const deptWrap = document.getElementById("calendar-share-dept-wrap");
    const memberWrap = document.getElementById("calendar-share-member-wrap");
    if (deptWrap) deptWrap.classList.toggle("hidden", vis !== "DEPARTMENT");
    if (memberWrap) memberWrap.classList.toggle("hidden", vis !== "SPECIFIC");
  }

  // ---------------------------------------------------------------------------
  // [일정 모달] 단일 선택 커스텀 콤보박스 — 열린 목록 UI를 관리자 패널(managingBoard)과 동일 톤으로 통일
  // 네이티브 <select> 의 OS 팝업은 CSS 로 꾸밀 수 없으므로, 숨은 select + 버튼 + listbox 패널로 대체한다.
  // ---------------------------------------------------------------------------

  /**
   * @param {HTMLElement} root `[data-cal-combobox]` 루트
   */
  function closeCalendarComboboxPanel(root) {
    const panel = root.querySelector("[data-cal-combobox-panel]");
    const trigger = root.querySelector("[data-cal-combobox-trigger]");
    if (panel instanceof HTMLElement) {
      panel.classList.add("hidden");
      panel.style.display = "";
      panel.style.position = "";
      panel.style.left = "";
      panel.style.top = "";
      panel.style.width = "";
      panel.style.zIndex = "";
      panel.style.maxHeight = "";
    }
    if (trigger) trigger.setAttribute("aria-expanded", "false");
  }

  function closeAllCalendarComboboxPanels() {
    document.querySelectorAll("#eventModal [data-cal-combobox]").forEach((r) => {
      if (r instanceof HTMLElement) closeCalendarComboboxPanel(r);
    });
  }

  /**
   * @param {HTMLElement} root
   */
  function openCalendarComboboxPanel(root) {
    closeAllCalendarComboboxPanels();
    const panel = root.querySelector("[data-cal-combobox-panel]");
    const trigger = root.querySelector("[data-cal-combobox-trigger]");
    if (!(panel instanceof HTMLElement) || !(trigger instanceof HTMLElement)) return;
    panel.classList.remove("hidden");
    panel.style.display = "block";
    trigger.setAttribute("aria-expanded", "true");
    const applyPos = () => {
      const r = trigger.getBoundingClientRect();
      if (r.width < 1 || r.height < 1) return;
      panel.style.position = "fixed";
      panel.style.left = `${r.left}px`;
      panel.style.top = `${r.bottom + 4}px`;
      panel.style.width = `${r.width}px`;
      panel.style.zIndex = "100000";
      panel.style.maxHeight = `min(14rem, calc(100vh - ${r.bottom + 12}px))`;
    };
    requestAnimationFrame(() => {
      requestAnimationFrame(applyPos);
    });
  }

  /**
   * 숨은 select 의 option 을 기준으로 listbox 버튼을 다시 그린다.
   * @param {HTMLElement} root
   */
  function rebuildCalendarComboboxPanel(root) {
    const selectId = root.getAttribute("data-cal-select-id");
    const sel = selectId ? document.getElementById(selectId) : null;
    const panel = root.querySelector("[data-cal-combobox-panel]");
    if (!(sel instanceof HTMLSelectElement) || !(panel instanceof HTMLElement)) return;
    panel.innerHTML = "";
    Array.from(sel.options).forEach((opt) => {
      const b = document.createElement("button");
      b.type = "button";
      b.role = "option";
      b.setAttribute("data-cal-value", opt.value);
      b.textContent = (opt.textContent ?? "").trim() || opt.value;
      b.className = "cal-combobox-option";
      b.setAttribute("aria-selected", String(sel.value === opt.value));
      panel.appendChild(b);
    });
    const labelEl = root.querySelector("[data-cal-combobox-label]");
    const cur = sel.options[sel.selectedIndex];
    if (labelEl && cur) labelEl.textContent = (cur.textContent || "").trim();
  }

  /** 일정 모달 내 모든 콤보박스를 숨은 select 값과 동기화 */
  function syncAllCalendarComboboxesFromSelects() {
    document.querySelectorAll("#eventModal [data-cal-combobox]").forEach((r) => {
      if (r instanceof HTMLElement) rebuildCalendarComboboxPanel(r);
    });
  }

  /** 이벤트 위임: 루트별 트리거·패널만 초기화, document 는 1회만 */
  function initEventModalCalendarComboboxes() {
    document.querySelectorAll("#eventModal [data-cal-combobox]").forEach((root) => {
      if (!(root instanceof HTMLElement)) return;
      if (root.dataset.calComboboxInit === "1") return;
      root.dataset.calComboboxInit = "1";
      rebuildCalendarComboboxPanel(root);
      const trigger = root.querySelector("[data-cal-combobox-trigger]");
      const stop = (e) => {
        e.stopPropagation();
      };
      trigger?.addEventListener("mousedown", stop);
      trigger?.addEventListener("click", (e) => {
        stop(e);
        // 패널은 동기화(rebuild) 시 innerHTML 이 바뀌므로 매 클릭 시 루트에서 다시 조회한다.
        const panelLive = root.querySelector("[data-cal-combobox-panel]");
        if (!(panelLive instanceof HTMLElement)) return;
        const isOpen = !panelLive.classList.contains("hidden");
        if (isOpen) closeCalendarComboboxPanel(root);
        else openCalendarComboboxPanel(root);
      });
      const panelForOptions = root.querySelector("[data-cal-combobox-panel]");
      panelForOptions?.addEventListener("click", (e) => {
        const t = e.target;
        const btn = t instanceof Element ? t.closest("button[role='option']") : null;
        if (!btn || !(panelForOptions instanceof HTMLElement) || !panelForOptions.contains(btn)) return;
        e.stopPropagation();
        const selectId = root.getAttribute("data-cal-select-id");
        const sel = selectId ? document.getElementById(selectId) : null;
        if (!(sel instanceof HTMLSelectElement)) return;
        sel.value = btn.getAttribute("data-cal-value") ?? "";
        closeCalendarComboboxPanel(root);
        sel.dispatchEvent(new Event("change", { bubbles: true }));
        rebuildCalendarComboboxPanel(root);
      });
    });

    if (eventModalComboboxGlobalsBound) return;
    eventModalComboboxGlobalsBound = true;

    // 바깥 클릭으로 패널 닫기: 캡처 단계는 다른 전역 리스너·라이브러리와 순서 충돌이 나기 쉬우므로
    // 버블 단계에서만 처리한다. 트리거·옵션 클릭은 stopPropagation 으로 여기까지 오지 않는다.
    document.addEventListener("click", (e) => {
      if (!eventModal || eventModal.classList.contains("hidden")) return;
      const path =
        typeof e.composedPath === "function"
          ? e.composedPath().filter((n) => n instanceof Node)
          : [];
      const t = e.target;
      const primary = t instanceof Node ? t : null;
      document.querySelectorAll("#eventModal [data-cal-combobox]").forEach((root) => {
        if (!(root instanceof HTMLElement)) return;
        const inside =
          path.length > 0 ? path.some((n) => root.contains(n)) : primary != null && root.contains(primary);
        if (!inside) closeCalendarComboboxPanel(root);
      });
    });

    document.addEventListener("keydown", (e) => {
      if (e.key !== "Escape") return;
      if (!eventModal || eventModal.classList.contains("hidden")) return;
      closeAllCalendarComboboxPanels();
    });

    window.addEventListener("resize", () => {
      if (!eventModal || eventModal.classList.contains("hidden")) return;
      closeAllCalendarComboboxPanels();
    });
  }

  // ---------------------------------------------------------------------------
  // [카테고리] 목록 · 셀렉트 · 모달
  // ---------------------------------------------------------------------------

  const categoryModal = document.getElementById("categoryModal");
  const categoryListEl = document.getElementById("category-list");
  const categoryAddForm = document.getElementById("categoryAddForm");
  const categoryEditForm = document.getElementById("categoryEditForm");
  const categoryFormError = document.getElementById("category-form-error");

  function showCategoryFormError(msg) {
    if (!categoryFormError) return;
    categoryFormError.textContent = msg;
    categoryFormError.classList.remove("hidden");
  }

  function clearCategoryFormError() {
    if (!categoryFormError) return;
    categoryFormError.textContent = "";
    categoryFormError.classList.add("hidden");
  }

  function renderCategorySelectOptions() {
    const sel = document.getElementById("calendar-category-id");
    if (!sel) return;
    const preserve = sel.value;
    sel.innerHTML = '<option value="">선택 안 함</option>';
    categoriesCache.forEach((c) => {
      const id = c.categoryId ?? c.id;
      const name = c.name ?? "";
      if (id == null) return;
      const opt = document.createElement("option");
      opt.value = String(id);
      opt.textContent = String(name);
      sel.appendChild(opt);
    });
    if (preserve && Array.from(sel.options).some((o) => o.value === preserve)) {
      sel.value = preserve;
    }
    const catRoot = document.querySelector('#eventModal [data-cal-select-id="calendar-category-id"]');
    if (catRoot instanceof HTMLElement) {
      rebuildCalendarComboboxPanel(catRoot);
      closeCalendarComboboxPanel(catRoot);
    }
  }

  /** 서버에서 카테고리 목록 조회 후 캐시·셀렉트 갱신 */
  async function loadCategories() {
    try {
      const res = await fetchSessionApi(API_CATEGORIES, {
        headers: { Accept: "application/json", ...getCsrfHeaders() },
      });
      if (!res.ok) throw new Error("categories fetch failed");
      const data = await res.json();
      categoriesCache = Array.isArray(data) ? data : [];
    } catch {
      categoriesCache = [];
    }
    renderCategorySelectOptions();
    renderCategoryListUl();
  }

  function renderCategoryListUl() {
    if (!categoryListEl) return;
    if (categoriesCache.length === 0) {
      categoryListEl.innerHTML =
        '<li class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">등록된 카테고리가 없습니다.</li>';
      return;
    }
    categoryListEl.innerHTML = categoriesCache
      .map((c) => {
        const id = c.categoryId ?? c.id;
        const name = escapeHtml(String(c.name ?? ""));
        const color = typeof c.color === "string" ? c.color : "#64748B";
        return `
      <li class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3 shadow-sm dark:border-gray-700 dark:bg-gray-800/40" data-category-id="${id}">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <span class="h-9 w-9 shrink-0 rounded-lg border border-gray-200 shadow-inner dark:border-gray-600" style="background-color:${escapeHtml(color)}" aria-hidden="true"></span>
          <span class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">${name}</span>
        </div>
        <div class="flex shrink-0 gap-2">
          <button type="button" class="btn-cat-edit rounded-lg border border-transparent px-3 py-1.5 text-xs font-medium text-indigo-700 transition bg-indigo-200 hover:bg-indigo-300 dark:bg-indigo-500/25 dark:text-indigo-100 dark:hover:bg-indigo-500/40">수정</button>
          <button type="button" class="btn-cat-delete rounded-lg border border-transparent px-3 py-1.5 text-xs font-medium text-rose-500 transition bg-rose-200 hover:bg-rose-300 dark:bg-rose-500/25 dark:text-rose-200 dark:hover:bg-rose-500/40">삭제</button>
        </div>
      </li>`;
      })
      .join("");
  }

  /** 추가 폼만 초기화 */
  function resetCategoryAddForm() {
    const nameInput = document.getElementById("category-add-name");
    if (nameInput) nameInput.value = "";
    const first = document.querySelector('input[name="categoryAddColor"]');
    if (first instanceof HTMLInputElement) first.checked = true;
  }

  /** 수정 폼만 초기화 */
  function resetCategoryEditForm() {
    const hid = document.getElementById("category-edit-id");
    const nameInput = document.getElementById("category-edit-name");
    if (hid) hid.value = "";
    if (nameInput) nameInput.value = "";
    const first = document.querySelector('input[name="categoryEditColor"]');
    if (first instanceof HTMLInputElement) first.checked = true;
  }

  /** 모달 닫기·오픈 시: 추가·수정 폼 모두 리셋 */
  function resetCategoryForms() {
    clearCategoryFormError();
    resetCategoryAddForm();
    resetCategoryEditForm();
  }

  /**
   * 목록에서 수정 클릭 시 수정 섹션 폼만 채움 (추가 폼은 건드리지 않음)
   * @param {Record<string, unknown>} item
   */
  function fillCategoryEditForm(item) {
    const hid = document.getElementById("category-edit-id");
    const nameInput = document.getElementById("category-edit-name");
    const id = item.categoryId ?? item.id;
    if (hid && id != null) hid.value = String(id);
    if (nameInput) nameInput.value = String(item.name ?? "");
    const col = typeof item.color === "string" ? item.color : "";
    const radios = document.querySelectorAll('input[name="categoryEditColor"]');
    let matched = false;
    radios.forEach((r) => {
      if (r instanceof HTMLInputElement && r.value === col) {
        r.checked = true;
        matched = true;
      }
    });
    if (!matched && radios[0] instanceof HTMLInputElement) radios[0].checked = true;
    document.getElementById("category-edit-name")?.focus();
  }

  function openCategoryModal() {
    categoryModal?.classList.remove("hidden");
    resetCategoryForms();
    loadCategories();
  }

  function closeCategoryModal() {
    categoryModal?.classList.add("hidden");
    resetCategoryForms();
  }

  // ---------------------------------------------------------------------------
  // [일정 조회 모달] 일정 클릭 시 먼저 뜨는 읽기 전용 화면 — 수정/삭제 진입은 별도 버튼
  // ---------------------------------------------------------------------------

  /** 마지막으로 조회한 일정 — view 모달의 [수정]/[삭제] 가 사용. */
  let lastViewedCalendarDto = /** @type {Record<string, unknown>|null} */ (null);

  const viewModal = document.getElementById("viewModal");

  function closeCalendarViewModal() {
    viewModal?.classList.add("hidden");
    lastViewedCalendarDto = null;
  }

  /** Date → "YYYY. MM. DD. (요일) HH:mm" / 종일이면 "YYYY. MM. DD. (요일)" */
  function formatCalendarDateTime(dateLike, allDay = false) {
    if (dateLike == null) return "—";
    const d = dateLike instanceof Date ? dateLike : new Date(String(dateLike));
    if (Number.isNaN(d.getTime())) return "—";
    const pad = (n) => String(n).padStart(2, "0");
    const weekday = ["일", "월", "화", "수", "목", "금", "토"][d.getDay()];
    const base = `${d.getFullYear()}. ${pad(d.getMonth() + 1)}. ${pad(d.getDate())}. (${weekday})`;
    return allDay ? base : `${base} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  /** 반복 패턴 문자열 — DAILY/WEEKLY+mask/MONTHLY+mask/YEARLY */
  function formatRepeatPattern(dto) {
    const type = String(dto?.repeatType ?? "").toUpperCase();
    const start = dto?.startAt ? new Date(String(dto.startAt)) : null;
    switch (type) {
      case "DAILY":
        return "매일";
      case "WEEKLY": {
        const mask = Number(dto?.repeatWeekdays ?? 0) || 0;
        if (mask > 0) {
          const names = ["월", "화", "수", "목", "금", "토", "일"];
          const bits = [1, 2, 4, 8, 16, 32, 64];
          const picked = names.filter((_, i) => (mask & bits[i]) === bits[i]);
          return `매주 ${picked.join("·")}`;
        }
        const w = start ? ["일", "월", "화", "수", "목", "금", "토"][start.getDay()] : "";
        return w ? `매주 ${w}요일` : "매주";
      }
      case "MONTHLY": {
        const mask = Number(dto?.repeatMonthDays ?? 0) || 0;
        if (mask > 0) {
          const days = [];
          for (let d = 1; d <= 31; d++) if ((mask & (1 << (d - 1))) !== 0) days.push(`${d}일`);
          return `매월 ${days.join("·")}`;
        }
        return start ? `매월 ${start.getDate()}일` : "매월";
      }
      case "YEARLY":
        return start ? `매년 ${start.getMonth() + 1}월 ${start.getDate()}일` : "매년";
      default:
        return "반복";
    }
  }

  /** 공유 정보 요약 + 디테일 (모두 / 부서 / 특정 인원) */
  function renderCalendarViewShare(dto) {
    const summaryEl = document.getElementById("view-share-summary");
    const detailEl = document.getElementById("view-share-detail");
    if (!summaryEl || !detailEl) return;
    detailEl.innerHTML = "";
    detailEl.classList.add("hidden");
    const visibility = String(dto?.visibility ?? "PRIVATE");
    switch (visibility) {
      case "PRIVATE":
        summaryEl.textContent = "나만 보기";
        return;
      case "COMPANY": {
        // 특정 부서 케이스와 동일한 타원 칩 스타일로 "모든 인원" 한 개를 표시
        summaryEl.textContent = "모두 공유";
        const chip = document.createElement("span");
        chip.className = "inline-flex items-center rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-200";
        chip.textContent = "모든 인원";
        detailEl.appendChild(chip);
        detailEl.classList.remove("hidden");
        detailEl.classList.add("flex");
        return;
      }
      case "DEPARTMENT": {
        const ids = Array.isArray(dto?.shareDeptIds) ? dto.shareDeptIds.map(String) : [];
        if (ids.length === 0) {
          summaryEl.textContent = "특정 부서 (선택 없음)";
          return;
        }
        summaryEl.textContent = `특정 부서 ${ids.length}개`;
        ids.forEach((id) => {
          const name = deptDirectoryCache.get(id) ?? `부서 #${id}`;
          const chip = document.createElement("span");
          chip.className = "inline-flex items-center rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-200";
          chip.textContent = name;
          detailEl.appendChild(chip);
        });
        detailEl.classList.remove("hidden");
        detailEl.classList.add("flex");
        return;
      }
      case "SPECIFIC": {
        const ids = Array.isArray(dto?.shareMemberIds) ? dto.shareMemberIds.map(String) : [];
        if (ids.length === 0) {
          summaryEl.textContent = "특정 인원 (선택 없음)";
          return;
        }
        summaryEl.textContent = `특정 인원 ${ids.length}명`;
        ids.forEach((id) => {
          const meta = memberDirectoryCache.get(id);
          const name = meta?.name ?? `#${id}`;
          const sub = [meta?.deptName, meta?.positionName].filter(Boolean).join(" · ");
          const chip = document.createElement("span");
          chip.className = "inline-flex items-center gap-1.5 rounded-full bg-gray-100 px-2.5 py-1 text-xs text-gray-700 dark:bg-gray-700/50 dark:text-gray-200";
          chip.innerHTML = `<img src="${memberProfileUrl(id)}" alt="" class="h-4 w-4 rounded-full bg-gray-200 object-cover" onerror="this.onerror=null;this.src='/images/user/default_user.jpg'" />
            <span>${escapeHtml(name)}</span>${sub ? `<span class="text-[10px] text-gray-400">${escapeHtml(sub)}</span>` : ""}`;
          detailEl.appendChild(chip);
        });
        detailEl.classList.remove("hidden");
        detailEl.classList.add("flex");
        // 캐시에 없는 멤버가 있으면 한 번 메타 채우고 다시 그림
        const missing = ids.filter((id) => !memberDirectoryCache.has(id));
        if (missing.length > 0) {
          fetchAndCacheMemberMeta().then(() => {
            if (lastViewedCalendarDto === dto) renderCalendarViewShare(dto);
          });
        }
        return;
      }
      default:
        summaryEl.textContent = "—";
    }
  }

  /**
   * 일정 조회 모달 열기 — 단건 fetch 응답(CalendarDto) 받아 표시.
   * 반복 일정이면 시간 정보 섹션을 숨기고 반복 패턴/반복 시작·종료를 보여준다.
   * @param {Record<string, unknown>} dto
   */
  function openCalendarViewModal(dto) {
    if (!viewModal) return;
    lastViewedCalendarDto = dto;

    // 카테고리 컬러 + 이름
    const color = (dto?.categoryColor && typeof dto.categoryColor === "string") ? dto.categoryColor : "#465fff";
    const bar = document.getElementById("view-category-bar");
    if (bar) bar.style.backgroundColor = color;
    const catName = document.getElementById("view-category-name");
    if (catName) {
      const cn = typeof dto.categoryName === "string" ? dto.categoryName : "";
      if (cn) { catName.textContent = cn; catName.classList.remove("hidden"); }
      else catName.classList.add("hidden");
    }

    // 제목
    const titleEl = document.getElementById("view-title");
    if (titleEl) titleEl.textContent = String(dto?.title ?? "(제목 없음)");

    // 설명
    const descWrap = document.getElementById("view-description-wrap");
    const descEl = document.getElementById("view-description");
    const desc = typeof dto?.description === "string" ? dto.description.trim() : "";
    if (descWrap && descEl) {
      if (desc) { descEl.textContent = desc; descWrap.classList.remove("hidden"); }
      else descWrap.classList.add("hidden");
    }

    // 일시 / 반복 토글
    const timeWrap = document.getElementById("view-time-wrap");
    const repeatWrap = document.getElementById("view-repeat-wrap");
    const allDay = Boolean(dto?.allDay);
    const startMs = calendarDtoTimeToMillis(dto?.startAt);
    const endMs = calendarDtoTimeToMillis(dto?.endAt);
    const startDate = Number.isNaN(startMs) ? null : new Date(startMs);
    const endDate = Number.isNaN(endMs) ? null : (allDay ? new Date(endMs - 1) : new Date(endMs));

    const isRepeat = Boolean(dto?.isRepeat ?? dto?.repeat);
    if (isRepeat) {
      // 반복 — 일시 섹션 숨김, 반복 패턴/반복 시작·종료만 노출
      timeWrap?.classList.add("hidden");
      repeatWrap?.classList.remove("hidden");
      const patternEl = document.getElementById("view-repeat-pattern");
      const repStartEl = document.getElementById("view-repeat-start");
      const repEndEl = document.getElementById("view-repeat-end");
      if (patternEl) patternEl.textContent = formatRepeatPattern(dto);
      if (repStartEl) repStartEl.textContent = startDate ? formatCalendarDateTime(startDate, allDay) : "—";
      const reMs = calendarDtoTimeToMillis(dto?.repeatEndAt);
      if (repEndEl) repEndEl.textContent = Number.isNaN(reMs) ? "지정 없음" : formatCalendarDateTime(new Date(reMs), false);
    } else {
      // 반복 없음 — 시작/종료 일시만 노출
      repeatWrap?.classList.add("hidden");
      timeWrap?.classList.remove("hidden");
      const sEl = document.getElementById("view-start-at");
      const eEl = document.getElementById("view-end-at");
      if (sEl) sEl.textContent = startDate ? formatCalendarDateTime(startDate, allDay) : "—";
      if (eEl) eEl.textContent = endDate ? formatCalendarDateTime(endDate, allDay) : "—";
    }

    // 장소
    const locWrap = document.getElementById("view-location-wrap");
    const locEl = document.getElementById("view-location");
    const loc = typeof dto?.location === "string" ? dto.location.trim() : "";
    if (locWrap && locEl) {
      if (loc) { locEl.textContent = loc; locWrap.classList.remove("hidden"); }
      else locWrap.classList.add("hidden");
    }

    // 공유
    renderCalendarViewShare(dto);

    // 알림
    const alertWrap = document.getElementById("view-alert-wrap");
    const alertEl = document.getElementById("view-alert-summary");
    const isAlert = Boolean(dto?.isAlert ?? dto?.alert);
    if (alertWrap && alertEl) {
      if (isAlert && dto?.alertMinutesBefore != null) {
        alertEl.textContent = `시작 ${dto.alertMinutesBefore}분 전`;
        alertWrap.classList.remove("hidden");
      } else {
        alertWrap.classList.add("hidden");
      }
    }

    viewModal.classList.remove("hidden");
  }

  /** 단건 fetch 가 실패했을 때 fallback — FullCalendar event 의 extendedProps 만으로 표시 */
  function openCalendarViewModalFromFcEvent(fcEvent) {
    const xp = fcEvent?.extendedProps ?? {};
    const proxy = {
      calendarId: xp.calendarId ?? fcEvent?.id,
      title: fcEvent?.title,
      description: xp.description,
      categoryName: null,
      categoryColor: xp.categoryColor,
      startAt: fcEvent?.start ? fcEvent.start.toISOString() : null,
      endAt: fcEvent?.end ? fcEvent.end.toISOString() : null,
      allDay: xp.allDay,
      visibility: xp.visibility,
      shareDeptIds: xp.shareDeptIds,
      shareMemberIds: xp.shareMemberIds,
      location: xp.location,
      isRepeat: xp.isRepeat,
      repeatType: xp.repeatType,
      repeatEndAt: xp.repeatEndAt,
      repeatWeekdays: xp.repeatWeekdays,
      repeatMonthDays: xp.repeatMonthDays,
      isAlert: xp.isAlert,
      alertMinutesBefore: xp.alertMinutesBefore,
    };
    openCalendarViewModal(proxy);
  }

  /** 조회 → 편집 전환 — 기존 편집 모달을 단건 dto 로 채워 띄움 */
  async function openEditFromView() {
    const dto = lastViewedCalendarDto;
    if (!dto) return;
    const id = dto.calendarId ?? dto.id;
    if (id == null) return;
    closeCalendarViewModal();
    setEventModalMode("edit");
    resetEventForm(false);
    fillFormFromCalendarDto(/** @type {Record<string, unknown>} */ (dto));
    syncRepeatSection();
    syncAlertSection();
    syncVisibilityShares();
    openEventModal();
  }

  /** 조회 → 삭제 — 기존 삭제 API 그대로 사용 */
  async function deleteFromView() {
    const dto = lastViewedCalendarDto;
    if (!dto) return;
    const id = dto.calendarId ?? dto.id;
    if (id == null) return;
    if (!window.confirm("이 일정을 삭제할까요?")) return;
    try {
      const res = await fetchSessionApi(`${API_CALENDARS}/${encodeURIComponent(String(id))}/delete`, {
        method: "POST",
        headers: { Accept: "application/json", ...getCsrfHeaders() },
      });
      if (!res.ok) throw new Error(await parseApiErrorBody(res));
      closeCalendarViewModal();
      calendarInstance?.refetchEvents();
    } catch (e) {
      window.alert(e instanceof Error ? e.message : "삭제 중 오류가 발생했습니다.");
    }
  }

  /**
   * FullCalendar 커스텀 버튼은 기본이 텍스트만 지원하므로, 렌더 후 + / 태그 SVG 아이콘을 삽입한다.
   * 뷰·날짜 변경 시 툴바가 다시 그려질 수 있어 datesSet 에서도 호출한다.
   */
  function decorateCalendarToolbarIcons() {
    const addBtn = calendarEl.querySelector(".fc-addEventButton-button");
    if (addBtn) {
      addBtn.setAttribute("aria-label", "일정 추가");
      addBtn.setAttribute("title", "일정 추가");
      if (!addBtn.querySelector('svg[data-cal-toolbar-icon="add"]')) {
        addBtn.innerHTML =
          '<svg data-cal-toolbar-icon="add" xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" /></svg>';
      }
    }
    const catBtn = calendarEl.querySelector(".fc-categoryButton-button");
    if (catBtn) {
      catBtn.setAttribute("aria-label", "카테고리 관리");
      catBtn.setAttribute("title", "카테고리 관리");
      if (!catBtn.querySelector('svg[data-cal-toolbar-icon="tag"]')) {
        catBtn.innerHTML =
          '<svg data-cal-toolbar-icon="tag" xmlns="http://www.w3.org/2000/svg" width="20" height="20" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M9.568 3H5.25A2.25 2.25 0 003 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581c.699.699 1.78.872 2.607.33a18.095 18.095 0 005.223-5.223c.542-.827.369-1.908-.33-2.607L11.772 3.659c-.422-.422-.994-.659-1.591-.659H9.568z" /><path stroke-linecap="round" stroke-linejoin="round" d="M6 6h.008v.008H6V6z" /></svg>';
      }
    }
    const todayBtn = calendarEl.querySelector(".fc-todayCal-button");
    if (todayBtn) {
      todayBtn.setAttribute("aria-label", "오늘 날짜로 이동");
      todayBtn.setAttribute("title", "오늘");
    }
  }

  // ---------------------------------------------------------------------------
  // [공유 대상] 부서 — managingBoard 권한 패널 패턴(체크박스 + data-multi-group + data-select-all)
  // 멤버   — memberList 검색 폼 패턴(이름 LIKE 쿼리 + 결과 리스트)
  // ---------------------------------------------------------------------------

  /** @type {Map<string, {name:string, deptName?:string|null, positionName?:string|null}>} */
  const memberDirectoryCache = new Map();

  /** 부서 ID → 부서명 매핑 (일정 셀 부서명 배지에서 사용) */
  const deptDirectoryCache = new Map();

  /** 선택된 멤버 ID (단일 진실 데이터 소스) — 검색 결과·미리보기·상세 리스트·폼 제출 모두 이 Set 을 본다 */
  const selectedMemberIds = new Set();

  /** 멤버 프로필 이미지 URL (MemberApiController 의 /api/member/{id}/profileImg) */
  function memberProfileUrl(id) {
    return `/api/member/${encodeURIComponent(String(id))}/profileImg`;
  }

  /** GET /api/depts → 체크박스 옵션 렌더 (전체 선택 라벨은 HTML 에 고정, 옵션만 갈아끼움) */
  async function loadDeptOptions() {
    const optionsBox = document.getElementById("calendar-dept-options");
    if (!optionsBox) return;
    try {
      const res = await fetchSessionApi(API_DEPTS, {
        headers: { Accept: "application/json", ...getCsrfHeaders() },
      });
      if (!res.ok) {
        optionsBox.innerHTML = '<p class="px-2 py-2 text-xs text-gray-400">부서 목록을 불러오지 못했습니다.</p>';
        return;
      }
      const list = await res.json();
      const arr = Array.isArray(list) ? list : [];
      // 부서명 캐시도 같이 채움 — 일정 셀 부서명 배지에서 사용
      arr.forEach((row) => {
        const id = row?.deptId ?? row?.id;
        const name = row?.deptName ?? row?.name;
        if (id != null && name) deptDirectoryCache.set(String(id), String(name));
      });
      if (arr.length === 0) {
        optionsBox.innerHTML = '<p class="px-2 py-2 text-xs text-gray-400">등록된 부서가 없습니다.</p>';
        return;
      }
      optionsBox.innerHTML = arr
        .map((row) => {
          const id = row?.deptId ?? row?.id;
          const name = row?.deptName ?? row?.name ?? "부서";
          if (id == null) return "";
          return `
            <label class="flex items-center gap-2 py-1 text-sm text-gray-700 dark:text-gray-300">
              <input type="checkbox" data-multi-group="calendarDeptId" value="${escapeHtml(String(id))}" class="h-4 w-4 rounded border-gray-300" />
              <span>${escapeHtml(String(name))}</span>
            </label>`;
        })
        .join("");
      bindCalendarMultiGroupHandlers("calendarDeptId");
      refreshCalendarDeptSummary();
      // 부서 캐시가 막 채워졌으니 이미 그려진 일정 셀이 부서명을 못 가져왔을 수 있음 → 다시 렌더
      calendarInstance?.render();
    } catch {
      optionsBox.innerHTML = '<p class="px-2 py-2 text-xs text-gray-400">부서 목록을 불러오지 못했습니다.</p>';
    }
  }

  /**
   * 검색 결과 한 row 마크업 — row 우측 "공유" / "해제" 타원 버튼이 단일 선택 UI.
   * 선택된 상태면 옅은 브랜드 배경 + "해제" 회색 버튼, 아니면 흰 배경 + "공유" 브랜드 버튼.
   * @param {any} row
   */
  function renderMemberResultRow(row) {
    const id = String(row?.memberId ?? row?.id ?? "");
    if (!id) return "";
    const name = String(row?.name ?? "");
    const dept = row?.deptName ? String(row.deptName) : "";
    const pos = row?.positionName ? String(row.positionName) : "";
    const sub = [dept, pos].filter(Boolean).join(" · ");
    const isShared = selectedMemberIds.has(id);
    const rowBg = isShared ? "bg-brand-50 dark:bg-brand-500/15" : "";
    const btnCls = isShared
      ? "border-gray-300 bg-white text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
      : "border-indigo-400 bg-indigo-400 text-white hover:bg-indigo-500";
    const btnText = isShared ? "해제" : "공유";
    return `
      <div data-result-row-id="${escapeHtml(id)}"
           class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition ${rowBg}">
        <img src="${memberProfileUrl(id)}" alt="" class="h-7 w-7 shrink-0 rounded-full bg-gray-200 object-cover" onerror="this.onerror=null;this.src='/images/user/default_user.jpg'" />
        <div class="min-w-0 flex-1">
          <span class="font-medium text-gray-900 dark:text-gray-100">${escapeHtml(name)}</span>
          ${sub ? `<span class="ml-1 text-xs text-gray-400">${escapeHtml(sub)}</span>` : ""}
        </div>
        <button type="button" data-share-toggle="${escapeHtml(id)}"
                class="shrink-0 rounded-full border ${btnCls} px-3 py-1 text-xs font-medium transition">
          ${btnText}
        </button>
      </div>`;
  }

  /** 단일 row 의 시각만 갱신 — 토글 후 전체 재렌더 없이 해당 row 만 갱신해 깜빡임 방지 */
  function updateMemberResultRow(id) {
    const rowEl = document.querySelector(`[data-result-row-id="${CSS.escape(id)}"]`);
    if (!(rowEl instanceof HTMLElement)) return;
    const btn = rowEl.querySelector("[data-share-toggle]");
    const isShared = selectedMemberIds.has(id);
    rowEl.classList.toggle("bg-brand-50", isShared);
    rowEl.classList.toggle("dark:bg-brand-500/15", isShared);
    if (btn instanceof HTMLElement) {
      btn.textContent = isShared ? "해제" : "공유";
      btn.className =
        "shrink-0 rounded-full border px-3 py-1 text-xs font-medium transition " +
        (isShared
          ? "border-gray-300 bg-white text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200"
          : "border-indigo-400 bg-indigo-400 text-white hover:bg-indigo-500");
    }
  }

  /** 결과 박스와 닫기 버튼을 함께 표시/숨김 (검색 결과 가시성 단일 진입점) */
  function showMemberResults(show) {
    const box = document.getElementById("calendar-member-results");
    const closeBtn = document.getElementById("calendar-member-search-close-btn");
    if (box) box.classList.toggle("hidden", !show);
    if (closeBtn) closeBtn.classList.toggle("hidden", !show);
  }

  /** GET /api/members?q=... → 결과 리스트 렌더 (memberList 검색 결과 동일 톤) */
  async function searchCalendarMembers() {
    const box = document.getElementById("calendar-member-results");
    if (!box) return;
    showMemberResults(true);
    const q = document.getElementById("calendar-member-search")?.value?.trim() ?? "";
    try {
      const url = q ? `${API_MEMBERS}?q=${encodeURIComponent(q)}` : API_MEMBERS;
      const res = await fetchSessionApi(url, {
        headers: { Accept: "application/json", ...getCsrfHeaders() },
      });
      if (!res.ok) {
        box.innerHTML = '<p class="px-2 py-2 text-xs text-gray-400">검색에 실패했습니다.</p>';
        return;
      }
      const list = await res.json();
      const arr = Array.isArray(list) ? list : [];
      arr.forEach((row) => {
        const id = row?.memberId ?? row?.id;
        if (id == null) return;
        memberDirectoryCache.set(String(id), {
          name: String(row?.name ?? "멤버"),
          deptName: row?.deptName ?? null,
          positionName: row?.positionName ?? null,
        });
      });
      if (arr.length === 0) {
        box.innerHTML = '<p class="px-2 py-3 text-sm text-gray-400">검색 결과가 없습니다.</p>';
      } else {
        box.innerHTML = arr.map((row) => renderMemberResultRow(row)).join("");
      }
    } catch {
      box.innerHTML = '<p class="px-2 py-2 text-xs text-gray-400">검색에 실패했습니다.</p>';
    }
  }

  /** 현재 화면에 체크된 부서 ID 배열 */
  function getSelectedCalendarDeptIds() {
    return Array.from(
      document.querySelectorAll('#calendarDeptPanel input[data-multi-group="calendarDeptId"]:checked'),
    ).map((cb) => /** @type {HTMLInputElement} */ (cb).value);
  }

  /** 현재 선택된 멤버 ID 배열 — selectedMemberIds Set 이 단일 진실 소스 */
  function getSelectedCalendarMemberIds() {
    return Array.from(selectedMemberIds);
  }

  /** 트리거 라벨: 선택 안 됨 → "부서 선택", 1개 → "{이름}", 2개 이상 → "{첫이름} 외 N개" */
  function refreshCalendarDeptSummary() {
    const label = document.querySelector('[data-cal-dept-summary]');
    if (!label) return;
    const checkedLabels = Array.from(
      document.querySelectorAll('#calendarDeptPanel input[data-multi-group="calendarDeptId"]:checked'),
    ).map((cb) => cb.parentElement?.querySelector("span")?.textContent?.trim() ?? "");
    const allSelected = document.querySelector('#calendarDeptPanel input[data-select-all="calendarDeptId"]')?.checked;
    if (checkedLabels.length === 0) {
      label.textContent = allSelected ? "전체 부서" : "부서 선택";
    } else if (checkedLabels.length === 1) {
      label.textContent = checkedLabels[0];
    } else {
      label.textContent = `${checkedLabels[0]} 외 ${checkedLabels.length - 1}개`;
    }
  }

  /**
   * 선택 미리보기·상세 리스트 재렌더 (selectedMemberIds 변경 시 호출).
   * 카카오톡 캘린더 공유 스타일: 앞 4명 프로필 동그라미 + N명 + 옵션 더보기.
   */
  function refreshSelectedMembersUI() {
    const ids = Array.from(selectedMemberIds);
    const summary = document.getElementById("calendar-member-summary");
    const previewBox = document.getElementById("calendar-member-preview");
    const countEl = document.getElementById("calendar-member-count");
    const moreBtn = document.getElementById("calendar-member-more-btn");
    const detailList = document.getElementById("calendar-member-detail-list");

    if (summary) summary.classList.toggle("hidden", ids.length === 0);
    if (moreBtn) moreBtn.classList.toggle("hidden", ids.length === 0);
    if (countEl) countEl.textContent = `${ids.length}명`;

    // 동그라미 미리보기: 앞 4명만, 5명 이상이면 +N 배지
    if (previewBox) {
      const VISIBLE = 4;
      const shown = ids.slice(0, VISIBLE);
      const extra = Math.max(0, ids.length - VISIBLE);
      previewBox.innerHTML =
        shown
          .map((id, idx) => {
            const meta = memberDirectoryCache.get(id);
            const name = meta?.name ?? "";
            const ring = idx === 0 ? "" : "-ml-2";
            return `<img src="${memberProfileUrl(id)}" alt="${escapeHtml(name)}" title="${escapeHtml(name)}"
                         class="h-7 w-7 ${ring} shrink-0 rounded-full border-2 border-white bg-gray-200 object-cover dark:border-gray-800"
                         onerror="this.onerror=null;this.src='/images/user/default_user.jpg'" />`;
          })
          .join("") +
        (extra > 0
          ? `<span class="-ml-2 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-white bg-gray-300 text-[10px] font-semibold text-gray-700 dark:border-gray-800 dark:bg-gray-600 dark:text-gray-100">+${extra}</span>`
          : "");
    }

    // 상세 리스트: 현재 펼침 상태 유지
    if (detailList) {
      detailList.innerHTML = ids
        .map((id) => {
          const meta = memberDirectoryCache.get(id);
          const name = meta?.name ?? `#${id}`;
          const sub = [meta?.deptName, meta?.positionName].filter(Boolean).join(" · ");
          return `
            <li class="flex items-center gap-3 rounded-md px-2 py-2 hover:bg-gray-50 dark:hover:bg-white/5" data-detail-member-id="${escapeHtml(id)}">
              <img src="${memberProfileUrl(id)}" alt="" class="h-9 w-9 shrink-0 rounded-full bg-gray-200 object-cover" onerror="this.onerror=null;this.src='/images/user/default_user.jpg'" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">${escapeHtml(name)}</div>
                ${sub ? `<div class="truncate text-xs text-gray-400">${escapeHtml(sub)}</div>` : ""}
              </div>
              <button type="button" data-detail-remove="${escapeHtml(id)}" aria-label="제거"
                      class="rounded-full p-1 text-gray-400 transition hover:bg-rose-50 hover:text-rose-500 dark:hover:bg-rose-950/40">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="h-4 w-4">
                  <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                </svg>
              </button>
            </li>`;
        })
        .join("");
      // 선택자가 0명이면 펼침 자체 의미가 없으므로 자동 접기
      if (ids.length === 0) {
        detailList.classList.add("hidden");
        document.querySelector("[data-member-more-arrow]")?.classList.remove("rotate-180");
      }
    }
  }

  /**
   * "전체 선택" ↔ 개별 체크박스 연동 (board.js initSelectAllControls 와 동일 규칙)
   * - 전체 체크: 개별 모두 해제 (제출 시 빈 배열 = 백엔드 ALL 해석)
   * - 개별 체크: 전체 해제
   * - 모두 해제: 전체 자동 복귀
   * @param {string} groupId data-multi-group 값
   */
  function bindCalendarMultiGroupHandlers(groupId) {
    const selectAll = document.querySelector(`input[data-select-all="${groupId}"]`);
    const individuals = document.querySelectorAll(`input[data-multi-group="${groupId}"]`);
    if (!(selectAll instanceof HTMLInputElement)) {
      individuals.forEach((cb) => {
        if (!(cb instanceof HTMLInputElement)) return;
        if (cb.dataset.calMultiBound === "1") return;
        cb.dataset.calMultiBound = "1";
        cb.addEventListener("change", () => {
          if (groupId === "calendarDeptId") refreshCalendarDeptSummary();
        });
      });
      return;
    }
    // selectAll 자체 리스너: 1회만 바인딩
    if (selectAll.dataset.calMultiBound !== "1") {
      selectAll.dataset.calMultiBound = "1";
      selectAll.addEventListener("change", () => {
        if (selectAll.checked) {
          document
            .querySelectorAll(`input[data-multi-group="${groupId}"]`)
            .forEach((cb) => {
              if (cb instanceof HTMLInputElement) cb.checked = false;
            });
        }
        if (groupId === "calendarDeptId") refreshCalendarDeptSummary();
      });
    }
    individuals.forEach((cb) => {
      if (!(cb instanceof HTMLInputElement)) return;
      if (cb.dataset.calMultiBound === "1") return;
      cb.dataset.calMultiBound = "1";
      cb.addEventListener("change", () => {
        if (cb.checked) {
          selectAll.checked = false;
        } else {
          const anyChecked = Array.from(
            document.querySelectorAll(`input[data-multi-group="${groupId}"]`),
          ).some((x) => x instanceof HTMLInputElement && x.checked);
          if (!anyChecked) selectAll.checked = true;
        }
        if (groupId === "calendarDeptId") refreshCalendarDeptSummary();
      });
    });
  }

  /** data-toggle-target 패널 토글 (board.js initPermissionPanelToggles 와 동일) */
  function initCalendarToggleTargets() {
    document.querySelectorAll('#eventModal button[data-toggle-target]').forEach((button) => {
      if (!(button instanceof HTMLElement)) return;
      if (button.dataset.calToggleBound === "1") return;
      button.dataset.calToggleBound = "1";
      const panelId = button.getAttribute("data-toggle-target");
      const panel = panelId ? document.getElementById(panelId) : null;
      const arrow = button.querySelector("[data-toggle-arrow]");
      if (!panel) return;
      button.addEventListener("click", () => {
        const willOpen = panel.classList.contains("hidden");
        panel.classList.toggle("hidden", !willOpen);
        button.setAttribute("aria-expanded", willOpen ? "true" : "false");
        if (arrow) arrow.classList.toggle("rotate-180", willOpen);
      });
    });
  }

  // ---------------------------------------------------------------------------
  // [일정 API] 등록 · 수정 · 삭제 — CalendarApiController: POST + @ModelAttribute
  // ---------------------------------------------------------------------------

  async function submitCreateEvent() {
    if (!validateEventForm()) return;
    const body = buildCalendarModelAttributeParams();
    try {
      const res = await fetchSessionApi(`${API_CALENDARS}/new`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          Accept: "application/json",
          ...getCsrfHeaders(),
        },
        body: body.toString(),
      });
      if (!res.ok) throw new Error(await parseApiErrorBody(res));
      calendarInstance?.refetchEvents();
      closeEventModal();
    } catch (e) {
      showEventFormError(e instanceof Error ? e.message : "등록 요청 중 오류가 발생했습니다.");
    }
  }

  async function submitUpdateEvent() {
    if (!validateEventForm()) return;
    const calendarId = document.getElementById("calendar-id")?.value;
    if (!calendarId) {
      showEventFormError("수정할 일정 ID가 없습니다.");
      return;
    }
    const body = buildCalendarModelAttributeParams();
    try {
      const res = await fetchSessionApi(
        `${API_CALENDARS}/${encodeURIComponent(calendarId)}/edit`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            Accept: "application/json",
            ...getCsrfHeaders(),
          },
          body: body.toString(),
        },
      );
      if (!res.ok) throw new Error(await parseApiErrorBody(res));
      calendarInstance?.refetchEvents();
      closeEventModal();
    } catch (e) {
      showEventFormError(e instanceof Error ? e.message : "수정 요청 중 오류가 발생했습니다.");
    }
  }

  async function submitDeleteEvent() {
    const calendarId = document.getElementById("calendar-id")?.value;
    if (!calendarId) return;
    if (!window.confirm("이 일정을 삭제할까요?")) return;
    try {
      const res = await fetchSessionApi(
        `${API_CALENDARS}/${encodeURIComponent(calendarId)}/delete`,
        {
          method: "POST",
          headers: { Accept: "application/json", ...getCsrfHeaders() },
        },
      );
      if (!res.ok) throw new Error(await parseApiErrorBody(res));
      calendarInstance?.refetchEvents();
      closeEventModal();
    } catch (e) {
      showEventFormError(e instanceof Error ? e.message : "삭제 요청 중 오류가 발생했습니다.");
    }
  }

  /**
   * FullCalendar 가 넘기는 날짜 마커(Date / 배열 / 숫자 등)를 JS Date 로 통일
   * titleFormat 등에서 Invalid Date → 렌더 예외로 일 뷰 전환이 막히는 것을 방지
   * @param {unknown} raw
   * @returns {Date}
   */
  function dateMarkerToJsDate(raw) {
    if (raw == null) return new Date();
    // formatRange + 함수형 titleFormat 인 자: { start, end, date } 의 start 는 Date 가 아니라
    // expandZonedMarker 결과( year/month/day + marker ) — marker 가 실제 날짜
    if (typeof raw === "object" && raw !== null && "marker" in raw) {
      const inner = /** @type {{ marker?: unknown }} */ (raw).marker;
      if (inner != null) return dateMarkerToJsDate(inner);
    }
    if (raw instanceof Date) {
      return Number.isNaN(raw.getTime()) ? new Date() : raw;
    }
    if (typeof raw === "number") {
      const d = new Date(raw);
      return Number.isNaN(d.getTime()) ? new Date() : d;
    }
    if (Array.isArray(raw) && raw.length >= 3) {
      const y = Number(raw[0]);
      const mo = Number(raw[1]);
      const day = Number(raw[2]);
      if (![y, mo, day].some((n) => Number.isNaN(n))) {
        return new Date(y, mo, day);
      }
    }
    const d = new Date(/** @type {string} */ (raw));
    return Number.isNaN(d.getTime()) ? new Date() : d;
  }

  // ---------------------------------------------------------------------------
  // [FullCalendar] 초기화
  // ---------------------------------------------------------------------------

  calendarInstance = new Calendar(calendarEl, {
    plugins: [dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin],
    /** 한국어: 요일·월명 등은 locale code(ko) + 브라우저 Intl 기준 */
    locale: koLocale,
    /**
     * 기본 로케일 패키지의 prev/next 가 '이전달/다음달' 고정이라 주·일 뷰와 어긋남 → 네비만 중립적으로 통일
     * @see https://fullcalendar.io/docs/locale
     */
    buttonText: {
      prev: "이전",
      next: "다음",
      today: "오늘",
      month: "월",
      week: "주",
      day: "일",
      list: "목록",
    },
    selectable: true,
    initialView: "dayGridMonth",
    /**
     * 월 그리드 기본값은 시간 있는 일정을 "점 + 3p 제목" 형태로 그려 배경색이 안 보임 → 블록으로 표시.
     * 주간/일간 타임그리드는 기존 슬롯 레이아웃 유지.
     */
    eventDisplay: "block",
    /**
     * 일정 셀 커스텀 렌더 — 공유 일정이면 텍스트 좌측에 공유자 프로필 동그라미(최대 3) + 인원 배지.
     * 본인은 동그라미 목록에서 제외 (A↔B↔C 공유 시 각 사용자 화면에서 자기 자신은 빠진다).
     */
    eventContent: (arg) => renderCalendarEventCellContent(arg),
    views: {
      dayGridMonth: {
        /** "3p" 등 월 뷰 접두 시간 숨김 (시간은 주간/일간·모달에서 확인) */
        displayEventTime: false,
      },
      /**
       * 일 뷰: 플러그인 기본 정의(type/duration)를 반드시 유지해야 함 — 생략 시 뷰 타입이 깨져 '일' 전환 불가
       * @see @fullcalendar/timegrid — timeGridDay: { type: 'timeGrid', duration: { days: 1 } }
       */
      timeGridDay: {
        type: "timeGrid",
        duration: { days: 1 },
        dayHeaders: false,
        titleFormat: (arg) => {
          try {
            const d = dateMarkerToJsDate(arg?.start);
            const dateStr = new Intl.DateTimeFormat("ko-KR", {
              year: "numeric",
              month: "long",
              day: "numeric",
            }).format(d);
            const wd = new Intl.DateTimeFormat("ko-KR", {
              weekday: "short",
            }).format(d);
            return `${dateStr} (${wd})`;
          } catch {
            return "";
          }
        },
      },
    },
    headerToolbar: {
      /**
       * FC: 공백으로 덩어리 분리, 덩어리 안 쉼표는 한 fc-button-group 안에서 인접 배치.
       * `prev,todayCal,next` = 이전·오늘·다음이 한 그룹(기존 prev,next 한 그룹 패턴 + 가운데 오늘).
       */
      left: "prev,todayCal,next addEventButton categoryButton",
      center: "title",
      right: "dayGridMonth,timeGridWeek,timeGridDay",
    },
    events: async (info, successCallback, failureCallback) => {
      try {
        const res = await fetchSessionApi(API_CALENDARS, {
          headers: { Accept: "application/json", ...getCsrfHeaders() },
        });
        if (!res.ok) throw new Error(await parseApiErrorBody(res));
        const data = await res.json();
        const arr = Array.isArray(data) ? data : [];
        // 반복 일정은 클라이언트에서 뷰 범위 내 occurrence 들로 펼침 (서버 DB 변경 없음)
        const expanded = arr.flatMap((raw) => expandRepeatingCalendarDto(raw, info.start, info.end));
        const filtered = filterCalendarDtosByViewRange(expanded, info.start, info.end);
        successCallback(filtered.map(mapCalendarDtoToFcEvent));
      } catch (e) {
        if (typeof failureCallback === "function") failureCallback(e instanceof Error ? e : new Error(String(e)));
        else successCallback([]);
      }
    },
    /**
     * 월/주 뷰 day-cell 우상단에 공휴일/기념일 라벨 인라인 표시.
     *  - 일정 박스(events) 가 아니라 날짜 숫자 옆에 작은 텍스트로 배치.
     *  - 같은 날 여러 휴일이 있으면 첫 번째 것만 표시 (예: 어린이날+부처님오신날 — 추후 정책에 따라 조정).
     */
    dayCellDidMount: (arg) => {
      const top = arg.el.querySelector(".fc-daygrid-day-top");
      if (!(top instanceof HTMLElement)) return;
      // 재렌더링 시 중복 부착 방지.
      top.querySelector(".cal-holiday-label")?.remove();
      const holidays = getHolidaysForDate(arg.date);
      if (holidays.length === 0) return;
      const entry = holidays[0];
      const span = document.createElement("span");
      span.className = "cal-holiday-label";
      span.dataset.holidayKind = entry.kind;
      span.title = entry.title;
      span.textContent = entry.shortTitle;
      top.appendChild(span);
    },
    select: (info) => {
      setEventModalMode("create");
      resetEventForm();
      const allDay = info.allDay;
      const allDayEl = document.getElementById("calendar-all-day");
      if (allDayEl) allDayEl.checked = allDay;

      let start = info.start;
      let end = info.end;
      if (allDay && end) {
        end = new Date(end.getTime() - 1);
      } else if (!end) {
        end = new Date(start);
        if (allDay) end.setHours(23, 59, 0, 0);
        else end.setHours(start.getHours() + 1);
      }

      const startInput = document.getElementById("calendar-start-at");
      const endInput = document.getElementById("calendar-end-at");
      if (startInput) startInput.value = toDatetimeLocalValue(start);
      if (endInput) endInput.value = toDatetimeLocalValue(end);

      syncRepeatSection();
      syncAlertSection();
      syncVisibilityShares();
      openEventModal();
    },
    dateClick: (info) => {
      setEventModalMode("create");
      resetEventForm();
      const allDay = info.allDay;
      const allDayEl = document.getElementById("calendar-all-day");
      if (allDayEl) allDayEl.checked = allDay;
      const start = info.date;
      let end;
      if (allDay) {
        end = new Date(start);
        end.setHours(23, 59, 0, 0);
      } else {
        end = new Date(start);
        end.setHours(end.getHours() + 1);
      }
      const startInput = document.getElementById("calendar-start-at");
      const endInput = document.getElementById("calendar-end-at");
      if (startInput) startInput.value = toDatetimeLocalValue(start);
      if (endInput) endInput.value = toDatetimeLocalValue(end);
      syncRepeatSection();
      syncAlertSection();
      syncVisibilityShares();
      openEventModal();
    },
    eventClick: async (info) => {
      info.jsEvent.preventDefault();
      if (info.event.url) return;
      const idRaw = info.event.extendedProps?.calendarId ?? info.event.id;
      const id = idRaw != null && idRaw !== "" ? String(idRaw) : "";
      if (!id) return;
      // 클릭 시 먼저 "조회 모달" 표시. 수정 버튼 클릭 시 비로소 편집 모달로 전환.
      try {
        const res = await fetchSessionApi(`${API_CALENDARS}/${encodeURIComponent(id)}`, {
          headers: { Accept: "application/json", ...getCsrfHeaders() },
        });
        if (res.ok) {
          const dto = await res.json();
          openCalendarViewModal(/** @type {Record<string, unknown>} */ (dto));
        } else {
          // 단건 fetch 실패 시 fallback: extendedProps 만으로 표시
          openCalendarViewModalFromFcEvent(info.event);
        }
      } catch {
        openCalendarViewModalFromFcEvent(info.event);
      }
    },
    customButtons: {
      /**
       * 오늘: FullCalendar 내장 today 툴바가 안 보일 때 대비한 커스텀 버튼.
       * prev/next 날짜 이동은 headerToolbar 의 기본 prev·next 에만 위임(덮어쓰지 않음).
       */
      todayCal: {
        text: "오늘",
        hint: "오늘 날짜로 이동",
        click: () => {
          calendarInstance?.today();
        },
      },
      addEventButton: {
        /** 실제 표시는 decorateCalendarToolbarIcons 에서 SVG(+)로 교체 */
        text: " ",
        click: () => {
          setEventModalMode("create");
          resetEventForm();
          const now = new Date();
          const start = new Date(now);
          start.setMinutes(0, 0, 0);
          const end = new Date(start);
          end.setHours(end.getHours() + 1);
          const allDayEl = document.getElementById("calendar-all-day");
          if (allDayEl) allDayEl.checked = false;
          const startInput = document.getElementById("calendar-start-at");
          const endInput = document.getElementById("calendar-end-at");
          if (startInput) startInput.value = toDatetimeLocalValue(start);
          if (endInput) endInput.value = toDatetimeLocalValue(end);
          syncRepeatSection();
          syncAlertSection();
          syncVisibilityShares();
          openEventModal();
        },
      },
      categoryButton: {
        /** 일정 추가 버튼 오른쪽 — 표시는 decorateCalendarToolbarIcons 에서 SVG(태그)로 교체 */
        text: " ",
        click: () => {
          openCategoryModal();
        },
      },
    },
  });

  // 첫 render() 직전에 현재 연도 공휴일 캐시를 채워둔다.
  //  - dayCellDidMount 콜백이 호출되는 시점에 캐시가 비어 있으면 라벨이 부착되지 않고,
  //    그 후 events() 비동기 fetch · prefetch · eventsSet 사이의 타이밍에 따라
  //    첫 진입 시 라벨이 늦게 보이거나 일관성이 깨지는 문제가 있었다.
  //  - fetch 실패는 ensureHolidaysForYear 내부에서 lunar fallback 으로 처리되므로 await 안전.
  try {
    await ensureHolidaysForYear(new Date().getFullYear());
  } catch {
    // fetch 실패는 fallback 으로 흡수 — render 자체를 막지 않는다.
  }

  calendarInstance.render();
  decorateCalendarToolbarIcons();

  /**
   * 휴일 prefetch — datesSet 마다 뷰 범위에 걸리는 연도들을 lazy 로드.
   *  - 새 데이터를 받았으면(true) DOM 을 직접 순회해 라벨을 attach.
   *  ※ 원래는 calendarInstance.render() 한 번으로 dayCellDidMount 가 재실행되리라 기대했지만,
   *    FullCalendar v6 의 render() 는 기존 day-cell 을 unmount/remount 하지 않아서 mount 콜백이
   *    다시 호출되지 않는다. 그래서 첫 진입 시 라벨이 안 보이고 다른 달로 이동해야 보이던 문제 발생.
   *    → 캐시가 채워진 직후 visible cell 들을 직접 순회해 라벨을 강제 부착.
   */
  const prefetchHolidaysForCurrentRange = async () => {
    const view = calendarInstance?.view;
    if (!view) return;
    const startYear = view.currentStart.getFullYear();
    const endYear = new Date(view.currentEnd.getTime() - 1).getFullYear();
    const years = new Set();
    for (let y = startYear; y <= endYear; y += 1) years.add(y);
    const results = await Promise.all(Array.from(years).map((y) => ensureHolidaysForYear(y)));
    if (results.some(Boolean)) applyHolidayLabelsToVisibleCells();
  };

  /**
   * 현재 보이는 day-cell 전체에 공휴일/기념일 라벨을 강제 부착.
   *  - dayCellDidMount 로직과 동일한 결과 형태(.cal-holiday-label). 기존 라벨은 제거 후 재부착.
   *  - 캐시(yearCache) 가 채워진 상태에서 호출되어야 의미 있음.
   */
  function applyHolidayLabelsToVisibleCells() {
    if (!calendarEl) return;
    calendarEl.querySelectorAll(".fc-daygrid-day[data-date]").forEach((cell) => {
      const top = cell.querySelector(".fc-daygrid-day-top");
      if (!(top instanceof HTMLElement)) return;
      top.querySelector(".cal-holiday-label")?.remove();
      const ds = cell.getAttribute("data-date");
      if (!ds) return;
      const date = new Date(`${ds}T00:00:00`);
      if (Number.isNaN(date.getTime())) return;
      const holidays = getHolidaysForDate(date);
      if (holidays.length === 0) return;
      const entry = holidays[0];
      const span = document.createElement("span");
      span.className = "cal-holiday-label";
      span.dataset.holidayKind = entry.kind;
      span.textContent = entry.shortTitle;
      top.appendChild(span);
    });
  }

  // 최초 렌더 직후의 초기 범위에도 prefetch 적용.
  prefetchHolidaysForCurrentRange();
  calendarInstance.on("datesSet", () => {
    decorateCalendarToolbarIcons();
    prefetchHolidaysForCurrentRange();
  });
  // events() 가 비동기로 resolve 되면 FullCalendar(Preact) 가 day-cell 을 재렌더하면서
  // 우리가 외부에서 appendChild 한 .cal-holiday-label 을 vDOM 에 없는 자식으로 보고 제거함.
  // → 이벤트 데이터가 적용된 직후 한 번 더 라벨을 재부착해 첫 진입 시 라벨 사라짐 문제 보강.
  calendarInstance.on("eventsSet", () => {
    applyHolidayLabelsToVisibleCells();
  });

  initEventModalCalendarComboboxes();
  initCalendarToggleTargets();

  // 최초 카테고리·부서 옵션 (API 없어도 UI 동작) — 멤버는 검색 시점에 호출
  loadCategories();
  loadDeptOptions();

  // ---------------------------------------------------------------------------
  // [이벤트 리스너] 일정 모달
  // ---------------------------------------------------------------------------

  btnSubmit?.addEventListener("click", submitCreateEvent);
  btnUpdate?.addEventListener("click", submitUpdateEvent);
  btnDelete?.addEventListener("click", submitDeleteEvent);
  btnCancel?.addEventListener("click", closeEventModal);

  document.querySelectorAll("#eventModal .modal-close-btn").forEach((btn) => {
    btn.addEventListener("click", closeEventModal);
  });

  document.getElementById("calendar-is-repeat")?.addEventListener("change", syncRepeatSection);
  // 반복 유형(콤보 안의 hidden select)이 바뀌면 요일/일자/매년 패널 노출 즉시 갱신
  document.getElementById("calendar-repeat-type")?.addEventListener("change", syncRepeatSection);
  // 시작일이 바뀌면 매년 반복 안내 라벨(M월 D일) 도 즉시 갱신
  document.getElementById("calendar-start-at")?.addEventListener("change", refreshCalendarRepeatYearlyLabel);
  document.getElementById("calendar-start-at")?.addEventListener("input", refreshCalendarRepeatYearlyLabel);
  ensureCalendarRepeatMonthDaysGrid();
  bindCalendarRepeatChipHandlers();
  document.getElementById("calendar-is-alert")?.addEventListener("change", syncAlertSection);

  // [일정 조회 모달] 닫기 / 수정 / 삭제 / 배경 클릭 닫기
  document.getElementById("btn-view-close")?.addEventListener("click", closeCalendarViewModal);
  document.getElementById("btn-view-edit")?.addEventListener("click", openEditFromView);
  document.getElementById("btn-view-delete")?.addEventListener("click", deleteFromView);
  document.querySelectorAll("#viewModal .modal-close-btn").forEach((btn) => {
    btn.addEventListener("click", closeCalendarViewModal);
  });
  visibilityRadios().forEach((r) =>
    r.addEventListener("change", syncVisibilityShares),
  );

  // 멤버 검색: 버튼 + Enter (memberList 의 form submit 패턴과 동일 동작)
  document.getElementById("calendar-member-search-btn")?.addEventListener("click", searchCalendarMembers);
  document.getElementById("calendar-member-search")?.addEventListener("keydown", (e) => {
    if (e instanceof KeyboardEvent && e.key === "Enter") {
      e.preventDefault();
      searchCalendarMembers();
    }
  });

  // 검색 결과 닫기 — 결과 박스/닫기 버튼 숨김 + input 비움 (선택된 멤버 미리보기는 유지)
  document.getElementById("calendar-member-search-close-btn")?.addEventListener("click", () => {
    const resultsBox = document.getElementById("calendar-member-results");
    if (resultsBox) resultsBox.innerHTML = "";
    showMemberResults(false);
    const searchInput = document.getElementById("calendar-member-search");
    if (searchInput instanceof HTMLInputElement) {
      searchInput.value = "";
      searchInput.focus();
    }
  });

  // 검색 결과 row 우측 "공유" / "해제" 타원 버튼 클릭 (위임)
  document.getElementById("calendar-member-results")?.addEventListener("click", (e) => {
    const t = e.target;
    if (!(t instanceof HTMLElement)) return;
    const btn = t.closest("[data-share-toggle]");
    if (!(btn instanceof HTMLElement)) return;
    const id = btn.dataset.shareToggle ?? "";
    if (!id) return;
    if (selectedMemberIds.has(id)) selectedMemberIds.delete(id);
    else selectedMemberIds.add(id);
    updateMemberResultRow(id);
    refreshSelectedMembersUI();
  });

  // "옵션 더보기" 버튼: 상세 리스트 펼치기/접기 (화살표 회전)
  document.getElementById("calendar-member-more-btn")?.addEventListener("click", () => {
    const detailList = document.getElementById("calendar-member-detail-list");
    const arrow = document.querySelector("[data-member-more-arrow]");
    if (!detailList) return;
    const willOpen = detailList.classList.contains("hidden");
    detailList.classList.toggle("hidden", !willOpen);
    arrow?.classList.toggle("rotate-180", willOpen);
  });

  // 상세 리스트 × 버튼: 해당 멤버 공유 해제 + 검색 결과에 보이면 row 버튼/배경도 동기화
  document.getElementById("calendar-member-detail-list")?.addEventListener("click", (e) => {
    const t = e.target;
    if (!(t instanceof HTMLElement)) return;
    const removeBtn = t.closest("[data-detail-remove]");
    if (!(removeBtn instanceof HTMLElement)) return;
    const id = removeBtn.dataset.detailRemove ?? "";
    if (!id) return;
    selectedMemberIds.delete(id);
    updateMemberResultRow(id);
    refreshSelectedMembersUI();
  });

  // ---------------------------------------------------------------------------
  // [이벤트 리스너] 카테고리 모달 (열기는 FullCalendar 툴바 categoryButton)
  // ---------------------------------------------------------------------------

  document.getElementById("btn-category-cancel")?.addEventListener("click", closeCategoryModal);
  document.getElementById("category-modal-backdrop")?.addEventListener("click", closeCategoryModal);

  /** 카테고리 신규 등록 (추가 폼) */
  categoryAddForm?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    clearCategoryFormError();
    const nameInput = document.getElementById("category-add-name");
    const name = nameInput?.value?.trim() ?? "";
    if (!name) {
      showCategoryFormError("카테고리 이름을 입력해 주세요.");
      nameInput?.focus();
      return;
    }
    const colorRadio = document.querySelector('input[name="categoryAddColor"]:checked');
    const color = colorRadio instanceof HTMLInputElement ? colorRadio.value : "";
    if (!color) {
      showCategoryFormError("색상을 선택해 주세요.");
      return;
    }
    try {
      const res = await fetchSessionApi(`${API_CATEGORIES}/new`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          ...getCsrfHeaders(),
        },
        body: buildCategoryRequestJson({ name, color }),
      });
      if (!res.ok) throw new Error(await parseApiErrorBody(res));
      resetCategoryAddForm();
      await loadCategories();
      calendarInstance?.refetchEvents();
    } catch (e) {
      showCategoryFormError(e instanceof Error ? e.message : "등록 중 오류가 발생했습니다.");
    }
  });

  /** 카테고리 수정 (수정 폼) */
  categoryEditForm?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    clearCategoryFormError();
    const editId = document.getElementById("category-edit-id")?.value?.trim() ?? "";
    if (!editId) {
      showCategoryFormError("목록에서 수정할 카테고리를 먼저 선택해 주세요.");
      return;
    }
    const nameInput = document.getElementById("category-edit-name");
    const name = nameInput?.value?.trim() ?? "";
    if (!name) {
      showCategoryFormError("카테고리 이름을 입력해 주세요.");
      nameInput?.focus();
      return;
    }
    const colorRadio = document.querySelector('input[name="categoryEditColor"]:checked');
    const color = colorRadio instanceof HTMLInputElement ? colorRadio.value : "";
    if (!color) {
      showCategoryFormError("색상을 선택해 주세요.");
      return;
    }
    try {
      const res = await fetchSessionApi(`${API_CATEGORIES}/${encodeURIComponent(editId)}/edit`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          ...getCsrfHeaders(),
        },
        body: buildCategoryRequestJson({ name, color }),
      });
      if (!res.ok) throw new Error(await parseApiErrorBody(res));
      resetCategoryEditForm();
      await loadCategories();
      calendarInstance?.refetchEvents();
    } catch (e) {
      showCategoryFormError(e instanceof Error ? e.message : "수정 중 오류가 발생했습니다.");
    }
  });

  syncRepeatSection();
  syncAlertSection();
  syncVisibilityShares();

  categoryListEl?.addEventListener("click", async (ev) => {
    const target = ev.target instanceof Element ? ev.target : null;
    const editBtn = target?.closest(".btn-cat-edit");
    const delBtn = target?.closest(".btn-cat-delete");
    const li = target?.closest("li[data-category-id]");
    const idAttr = li?.getAttribute("data-category-id");
    if (!idAttr) return;
    const item = categoriesCache.find((c) => String(c.categoryId ?? c.id) === idAttr);
    if (editBtn && item) {
      fillCategoryEditForm(item);
    }
    if (delBtn) {
      if (!window.confirm("이 카테고리를 삭제할까요?")) return;
      try {
        const res = await fetchSessionApi(`${API_CATEGORIES}/${encodeURIComponent(idAttr)}/delete`, {
          method: "POST",
          headers: { Accept: "application/json", ...getCsrfHeaders() },
        });
        if (!res.ok) throw new Error(await parseApiErrorBody(res));
        const editingId = document.getElementById("category-edit-id")?.value;
        if (editingId === idAttr) resetCategoryEditForm();
        await loadCategories();
        calendarInstance?.refetchEvents();
      } catch (e) {
        showCategoryFormError(e instanceof Error ? e.message : "삭제 중 오류가 발생했습니다.");
      }
    }
  });
});
