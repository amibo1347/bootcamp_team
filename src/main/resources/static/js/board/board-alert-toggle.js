/**
 * 게시판 알림 토글 (list/card/album 게시판 페이지 공통).
 * - 페이지 진입 시 GET /api/boards/{boardId}/alert 로 현재 on/off 상태 읽어 색 반영
 * - 버튼 클릭 시 POST /api/boards/{boardId}/alert/toggle 호출 → 응답의 enabled 로 색 갱신
 *
 * 마크업 약속: `<button data-board-alert-toggle="{boardId}" aria-pressed="false">` + 종 SVG.
 *  aria-pressed=true 일 때 인디고색, false 일 때 회색(테두리만).
 */
(() => {
  document.addEventListener("DOMContentLoaded", () => {
    const buttons = document.querySelectorAll("button[data-board-alert-toggle]");
    if (buttons.length === 0) return;

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content") || "";
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content") || "X-CSRF-TOKEN";

    /**
     * 버튼 시각 상태 동기화 — aria-pressed 만 바꾸면 CSS(.board-alert-toggle[aria-pressed="true"]) 가
     * 알아서 brand-500 풀채움 + 흰 아이콘 + shadow 를 적용한다.
     * 아이콘 자체는 항상 표시되며 배경색만 바뀐다.
     */
    function applyState(btn, enabled) {
      btn.setAttribute("aria-pressed", enabled ? "true" : "false");
      btn.setAttribute("title", enabled ? "이 게시판 알림 끄기" : "이 게시판 알림 받기");
      btn.setAttribute("aria-label", enabled ? "이 게시판 알림 끄기" : "이 게시판 알림 받기");
    }

    /** 페이지 진입 시 현재 상태 1회 조회 */
    async function loadInitialState(btn) {
      const boardId = btn.getAttribute("data-board-alert-toggle");
      if (!boardId) return;
      try {
        const res = await fetch(`/api/boards/${encodeURIComponent(boardId)}/alert`, {
          credentials: "same-origin",
          headers: { Accept: "application/json" },
        });
        if (!res.ok) return;
        const data = await res.json();
        applyState(btn, Boolean(data?.enabled));
      } catch {
        /* 비로그인·네트워크 오류는 조용히 무시 — 기본 off 유지 */
      }
    }

    /** 클릭 시 토글 */
    async function onToggleClick(btn) {
      const boardId = btn.getAttribute("data-board-alert-toggle");
      if (!boardId || btn.dataset.busy === "1") return;
      btn.dataset.busy = "1";
      try {
        const res = await fetch(`/api/boards/${encodeURIComponent(boardId)}/alert/toggle`, {
          method: "POST",
          credentials: "same-origin",
          headers: {
            Accept: "application/json",
            ...(csrfToken ? { [csrfHeader]: csrfToken } : {}),
          },
        });
        if (!res.ok) {
          if (res.status === 401) window.alert("로그인이 필요합니다.");
          return;
        }
        const data = await res.json();
        applyState(btn, Boolean(data?.enabled));
      } catch {
        /* 무시 */
      } finally {
        btn.dataset.busy = "";
      }
    }

    buttons.forEach((btn) => {
      loadInitialState(btn);
      btn.addEventListener("click", () => onToggleClick(btn));
    });
  });
})();
