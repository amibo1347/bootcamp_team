import { Calendar } from "@fullcalendar/core";
import dayGridPlugin from "@fullcalendar/daygrid";
import listPlugin from "@fullcalendar/list";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";

/** @typedef {'create'|'edit'} EventModalMode */

document.addEventListener("DOMContentLoaded", () => {
  const calendarEl = document.querySelector("#calendar");
  if (!calendarEl) return;

  // ---------------------------------------------------------------------------
  // [상수] API 경로 — CalendarApiController / CategoryApiController 와 동일 경로 유지
  // (변경 요청은 프로젝트 정책상 PUT/DELETE 대신 POST 하위 경로 사용)
  // ---------------------------------------------------------------------------
  const API_CALENDAR_EVENTS = "/api/calendar/events";
  /** 일정 수정: POST 본문에 `id` + 필드 포함 (@PostMapping .../update) */
  const API_CALENDAR_EVENTS_UPDATE = `${API_CALENDAR_EVENTS}/update`;
  /** 일정 삭제: POST 본문 `{ id }` (@PostMapping .../delete) */
  const API_CALENDAR_EVENTS_DELETE = `${API_CALENDAR_EVENTS}/delete`;
  /** CategoryApiController: `/api/categories` — 생성·수정은 JSON이 아니라 form-urlencoded(@ModelAttribute) */
  const API_CATEGORIES = "/api/categories";
  const API_DEPTS = "/api/depts";
  const API_MEMBERS = "/api/members";

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
   * datetime-local 값을 ISO 문자열로 변환 (JSON 전송용)
   * @param {string} localStr
   */
  function datetimeLocalToIso(localStr) {
    if (!localStr || !localStr.trim()) return null;
    const d = new Date(localStr);
    if (Number.isNaN(d.getTime())) return null;
    return d.toISOString();
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
   * CategoryDto 폼 필드 — CategoryApiController 의 @ModelAttribute 바인딩용 (body 에 넣으면 fetch 가 urlencoded Content-Type 설정)
   * @param {{ name: string; color: string }} fields
   * @returns {URLSearchParams}
   */
  function buildCategoryFormParams(fields) {
    const p = new URLSearchParams();
    p.set("name", fields.name);
    p.set("color", fields.color);
    return p;
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
   * 서버 DTO 한 건을 FullCalendar 이벤트 객체로 매핑
   * @param {Record<string, unknown>} raw
   */
  function mapCalendarDtoToFcEvent(raw) {
    const cat = raw.category && typeof raw.category === "object" ? raw.category : null;
    const color =
      (cat && typeof cat.color === "string" && cat.color) ||
      (typeof raw.categoryColor === "string" && raw.categoryColor) ||
      "#465fff";
    const calendarId = raw.calendarId ?? raw.id;
    const shareMemberIds = Array.isArray(raw.shareMemberIds) ? raw.shareMemberIds : [];
    const shareDeptIds = Array.isArray(raw.shareDeptIds) ? raw.shareDeptIds : [];

    return {
      id: String(calendarId ?? ""),
      title: typeof raw.title === "string" ? raw.title : "",
      start: raw.startAt ?? raw.start,
      end: raw.endAt ?? raw.end ?? raw.startAt ?? raw.start,
      allDay: Boolean(raw.allDay),
      backgroundColor: color,
      borderColor: color,
      extendedProps: {
        calendarId,
        description: raw.description ?? "",
        categoryId: cat?.categoryId ?? raw.categoryId ?? null,
        categoryColor: color,
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
    const deptSelect = document.getElementById("calendar-share-depts");
    const memberSelect = document.getElementById("calendar-share-members");
    if (vis === "DEPARTMENT") {
      const n = deptSelect?.selectedOptions?.length ?? 0;
      if (n === 0) {
        showEventFormError('공개 범위가 "특정 부서"일 때 공유 부서를 한 개 이상 선택해 주세요.');
        deptSelect?.focus();
        return false;
      }
    }
    if (vis === "SPECIFIC") {
      const n = memberSelect?.selectedOptions?.length ?? 0;
      if (n === 0) {
        showEventFormError('공개 범위가 "특정 인원"일 때 공유 멤버를 한 명 이상 선택해 주세요.');
        memberSelect?.focus();
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
   * 폼 값을 API 요청 본문 객체로 직렬화
   * @returns {Record<string, unknown>}
   */
  function collectEventPayload() {
    const categorySel = document.getElementById("calendar-category-id");
    const catVal = categorySel?.value ?? "";
    const visibility =
      eventForm?.querySelector('input[name="visibility"]:checked')?.value ?? "PRIVATE";
    const deptSelect = document.getElementById("calendar-share-depts");
    const memberSelect = document.getElementById("calendar-share-members");

    const shareDeptIds = deptSelect
      ? Array.from(deptSelect.selectedOptions).map((o) => Number(o.value)).filter((id) => !Number.isNaN(id))
      : [];
    const shareMemberIds = memberSelect
      ? Array.from(memberSelect.selectedOptions).map((o) => Number(o.value)).filter((id) => !Number.isNaN(id))
      : [];

    const isRepeat = document.getElementById("calendar-is-repeat")?.checked ?? false;
    const isAlert = document.getElementById("calendar-is-alert")?.checked ?? false;
    const repeatTypeEl = document.getElementById("calendar-repeat-type");
    const alertMinutesEl = document.getElementById("calendar-alert-minutes");

    const startIso = datetimeLocalToIso(document.getElementById("calendar-start-at")?.value ?? "");
    const endIso = datetimeLocalToIso(document.getElementById("calendar-end-at")?.value ?? "");
    const repeatEndIso = datetimeLocalToIso(document.getElementById("calendar-repeat-end-at")?.value ?? "");

    return {
      title: document.getElementById("calendar-title")?.value?.trim() ?? "",
      description: document.getElementById("calendar-description")?.value?.trim() || null,
      location: document.getElementById("calendar-location")?.value?.trim() || null,
      startAt: startIso,
      endAt: endIso,
      allDay: document.getElementById("calendar-all-day")?.checked ?? false,
      categoryId: catVal ? Number(catVal) : null,
      visibility,
      shareDeptIds,
      shareMemberIds,
      isRepeat,
      repeatType: isRepeat ? repeatTypeEl?.value ?? "DAILY" : null,
      repeatEndAt: isRepeat ? repeatEndIso : null,
      isAlert,
      alertMinutesBefore: isAlert ? Number(alertMinutesEl?.value ?? 15) : null,
    };
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

    const deptSelect = document.getElementById("calendar-share-depts");
    const memberSelect = document.getElementById("calendar-share-members");
    if (deptSelect) Array.from(deptSelect.options).forEach((o) => (o.selected = false));
    if (memberSelect) Array.from(memberSelect.options).forEach((o) => (o.selected = false));

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

    const deptSelect = document.getElementById("calendar-share-depts");
    if (deptSelect && Array.isArray(xp.shareDeptIds)) {
      Array.from(deptSelect.options).forEach((opt) => {
        opt.selected = xp.shareDeptIds.map(Number).includes(Number(opt.value));
      });
    }
    const memberSelect = document.getElementById("calendar-share-members");
    if (memberSelect && Array.isArray(xp.shareMemberIds)) {
      Array.from(memberSelect.options).forEach((opt) => {
        opt.selected = xp.shareMemberIds.map(Number).includes(Number(opt.value));
      });
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
      const panel = root.querySelector("[data-cal-combobox-panel]");
      const stop = (e) => {
        e.stopPropagation();
      };
      trigger?.addEventListener("mousedown", stop);
      trigger?.addEventListener("click", (e) => {
        stop(e);
        if (!(panel instanceof HTMLElement)) return;
        const isOpen = !panel.classList.contains("hidden");
        if (isOpen) closeCalendarComboboxPanel(root);
        else openCalendarComboboxPanel(root);
      });
      panel?.addEventListener("click", (e) => {
        const t = e.target;
        const btn = t instanceof Element ? t.closest("button[role='option']") : null;
        if (!btn || !panel.contains(btn)) return;
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

    document.addEventListener(
      "click",
      (e) => {
        const t = e.target;
        if (!(t instanceof Node)) return;
        if (!eventModal || eventModal.classList.contains("hidden")) return;
        document.querySelectorAll("#eventModal [data-cal-combobox]").forEach((root) => {
          if (root instanceof HTMLElement && !root.contains(t)) closeCalendarComboboxPanel(root);
        });
      },
      true,
    );

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

  // ---------------------------------------------------------------------------
  // [공유 대상] 부서·멤버 멀티 셀렉트 채우기 (API 스키마 유연 처리)
  // ---------------------------------------------------------------------------

  /**
   * @param {HTMLSelectElement} selectEl
   * @param {unknown[]} items
   * @param {(row: Record<string, unknown>) => { id: string; label: string }} pick
   */
  function populateMultiSelect(selectEl, items, pick) {
    selectEl.innerHTML = "";
    items.forEach((raw) => {
      const row = raw && typeof raw === "object" ? /** @type {Record<string, unknown>} */ (raw) : {};
      const { id, label } = pick(row);
      if (!id) return;
      const opt = document.createElement("option");
      opt.value = id;
      opt.textContent = label;
      selectEl.appendChild(opt);
    });
  }

  async function loadDeptAndMemberOptions() {
    const deptSelect = document.getElementById("calendar-share-depts");
    const memberSelect = document.getElementById("calendar-share-members");
    try {
      const [dRes, mRes] = await Promise.all([
        fetch(API_DEPTS, { headers: { Accept: "application/json" }, credentials: "same-origin" }),
        fetch(API_MEMBERS, { headers: { Accept: "application/json" }, credentials: "same-origin" }),
      ]);
      if (deptSelect && dRes.ok) {
        const depts = await dRes.json();
        const arr = Array.isArray(depts) ? depts : [];
        populateMultiSelect(deptSelect, arr, (row) => ({
          id: String(row.deptId ?? row.id ?? ""),
          label: String(row.deptName ?? row.name ?? row.label ?? "부서"),
        }));
      }
      if (memberSelect && mRes.ok) {
        const members = await mRes.json();
        const arr = Array.isArray(members) ? members : [];
        populateMultiSelect(memberSelect, arr, (row) => ({
          id: String(row.memberId ?? row.id ?? ""),
          label: String(row.name ?? row.loginId ?? row.label ?? "멤버"),
        }));
      }
    } catch {
      /* API 미구현 시 빈 셀렉트 유지 */
    }
  }

  // ---------------------------------------------------------------------------
  // [일정 API] 등록 · 수정 · 삭제 — 모두 POST(수정/삭제는 전용 URL)
  // ---------------------------------------------------------------------------

  async function submitCreateEvent() {
    if (!validateEventForm()) return;
    const payload = collectEventPayload();
    try {
      const res = await fetch(API_CALENDAR_EVENTS, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json", ...getCsrfHeaders() },
        credentials: "same-origin",
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const t = await res.text();
        throw new Error(t || "등록에 실패했습니다.");
      }
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
    const payload = { id: Number(calendarId), ...collectEventPayload() };
    try {
      const res = await fetch(API_CALENDAR_EVENTS_UPDATE, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json", ...getCsrfHeaders() },
        credentials: "same-origin",
        body: JSON.stringify(payload),
      });
      if (!res.ok) {
        const t = await res.text();
        throw new Error(t || "수정에 실패했습니다.");
      }
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
      const res = await fetch(API_CALENDAR_EVENTS_DELETE, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json", ...getCsrfHeaders() },
        credentials: "same-origin",
        body: JSON.stringify({ id: Number(calendarId) }),
      });
      if (!res.ok) throw new Error("삭제에 실패했습니다.");
      calendarInstance?.refetchEvents();
      closeEventModal();
    } catch (e) {
      showEventFormError(e instanceof Error ? e.message : "삭제 요청 중 오류가 발생했습니다.");
    }
  }

  // ---------------------------------------------------------------------------
  // [FullCalendar] 초기화
  // ---------------------------------------------------------------------------

  calendarInstance = new Calendar(calendarEl, {
    plugins: [dayGridPlugin, timeGridPlugin, listPlugin, interactionPlugin],
    selectable: true,
    initialView: "dayGridMonth",
    headerToolbar: {
      left: "prev,next addEventButton",
      center: "title",
      right: "dayGridMonth,timeGridWeek,timeGridDay",
    },
    events: async (info, successCallback, failureCallback) => {
      try {
        const url = new URL(API_CALENDAR_EVENTS, window.location.origin);
        url.searchParams.set("start", info.startStr);
        url.searchParams.set("end", info.endStr);
        const res = await fetch(url.toString(), {
          headers: { Accept: "application/json", ...getCsrfHeaders() },
          credentials: "same-origin",
        });
        if (!res.ok) throw new Error("fetch failed");
        const data = await res.json();
        const arr = Array.isArray(data) ? data : [];
        successCallback(arr.map(mapCalendarDtoToFcEvent));
      } catch {
        successCallback([]);
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
    eventClick: (info) => {
      info.jsEvent.preventDefault();
      if (info.event.url) return;
      setEventModalMode("edit");
      fillFormFromFcEvent(info.event);
      syncRepeatSection();
      syncAlertSection();
      syncVisibilityShares();
      openEventModal();
    },
    customButtons: {
      addEventButton: {
        text: "일정 추가 +",
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
    },
  });

  calendarInstance.render();

  initEventModalCalendarComboboxes();

  // 최초 카테고리·공유 옵션 (API 없어도 UI 동작)
  loadCategories();
  loadDeptAndMemberOptions();

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

  // ---------------------------------------------------------------------------
  // [이벤트 리스너] 카테고리 모달
  // ---------------------------------------------------------------------------

  document.getElementById("btn-open-category-modal")?.addEventListener("click", openCategoryModal);
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
        headers: { Accept: "application/json", ...getCsrfHeaders() },
        body: buildCategoryFormParams({ name, color }),
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
        headers: { Accept: "application/json", ...getCsrfHeaders() },
        body: buildCategoryFormParams({ name, color }),
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
