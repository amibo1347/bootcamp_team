import { Calendar } from "@fullcalendar/core";
import koLocale from "@fullcalendar/core/locales/ko";
import dayGridPlugin from "@fullcalendar/daygrid";
import listPlugin from "@fullcalendar/list";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";

/** @typedef {'create'|'edit'} EventModalMode */

document.addEventListener("DOMContentLoaded", () => {
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
    if (res.status === 401) return "로그인이 필요합니다.";
    if (res.status === 403) return "접근 권한이 없습니다.";
    const text = await res.text();
    if (!text.trim()) return `요청 실패 (${res.status})`;
    try {
      const j = JSON.parse(text);
      if (j && typeof j.message === "string") return j.message;
    } catch {
      /* 본문이 JSON이 아님 */
    }
    return text.trim();
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
   * FullCalendar eventContent — 셀 안에 [전사/부서 배지] + [동그라미 최대 3] + [+N] + [제목] 가로 배치.
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

    // 1) 전사 공유 → "모두" 배지
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

    return {
      id: String(calendarId ?? ""),
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
      p.set("repeatType", document.getElementById("calendar-repeat-type")?.value ?? "DAILY");
      const re = datetimeLocalToSpringParam(document.getElementById("calendar-repeat-end-at")?.value ?? "");
      if (re) p.set("repeatEndAt", re);
    } else {
      p.delete("repeatType");
      p.delete("repeatEndAt");
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

  /** 반복 필드 영역 활성/비활성 */
  function syncRepeatSection() {
    const on = document.getElementById("calendar-is-repeat")?.checked ?? false;
    const box = document.getElementById("calendar-repeat-fields");
    if (!box) return;
    box.classList.toggle("opacity-50", !on);
    box.classList.toggle("pointer-events-none", !on);
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
          <button type="button" class="btn-cat-edit rounded-lg border border-transparent px-3 py-1.5 text-xs font-medium text-brand-600 transition hover:bg-brand-50 dark:text-brand-400 dark:hover:bg-brand-500/10">수정</button>
          <button type="button" class="btn-cat-delete rounded-lg border border-transparent px-3 py-1.5 text-xs font-medium text-rose-600 transition hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-950/40">삭제</button>
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
      : "border-brand-500 bg-brand-500 text-white hover:bg-brand-600";
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
          : "border-brand-500 bg-brand-500 text-white hover:bg-brand-600");
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
        const filtered = filterCalendarDtosByViewRange(arr, info.start, info.end);
        successCallback(filtered.map(mapCalendarDtoToFcEvent));
      } catch (e) {
        if (typeof failureCallback === "function") failureCallback(e instanceof Error ? e : new Error(String(e)));
        else successCallback([]);
      }
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
      setEventModalMode("edit");
      fillFormFromFcEvent(info.event);
      try {
        const res = await fetchSessionApi(`${API_CALENDARS}/${encodeURIComponent(id)}`, {
          headers: { Accept: "application/json", ...getCsrfHeaders() },
        });
        if (res.ok) {
          const dto = await res.json();
          if (dto && typeof dto === "object") fillFormFromCalendarDto(/** @type {Record<string, unknown>} */ (dto));
        }
      } catch {
        /* 목록 이벤트만으로 폼 유지 */
      }
      syncRepeatSection();
      syncAlertSection();
      syncVisibilityShares();
      openEventModal();
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

  calendarInstance.render();
  decorateCalendarToolbarIcons();
  calendarInstance.on("datesSet", () => {
    decorateCalendarToolbarIcons();
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
  document.getElementById("calendar-is-alert")?.addEventListener("change", syncAlertSection);
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
