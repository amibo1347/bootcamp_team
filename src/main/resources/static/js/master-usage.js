/**
 * MASTER 회사 사용량 대시보드 — 클라이언트 사이드 동작.
 *
 *  1) data-bytes 속성이 있는 모든 셀(스토리지 / KPI 카드)을 사람이 읽는 단위로 포맷팅.
 *  2) 회사명 검색 — 부분 일치, 대소문자 무시. CSV 내보내기 링크에 ?search= 도 동기화.
 *  3) 컬럼 헤더 정렬 — text / number / boolean / date 4 종 타입.
 *  4) 검색·정렬 결과에 맞춰 합계 행 (회원/신규/게시글/결재/스토리지) 자동 재계산.
 *  5) 최근 30일 추이 라인 차트 — Chart.js 로 신규 회원 + 신규 게시글 2 시리즈.
 *
 *  서버에서 window.__masterTrend 에 [{date, newMembers, newArticles}, ...] 형태로 주입되어 있다.
 */
(() => {
  document.addEventListener("DOMContentLoaded", () => {
    formatByteCells();
    bindSearch();
    bindSortHandlers();
    refreshTotalsRow();
    renderTrendChart();
  });

  // ---------------------------------------------------------------------------
  // 바이트 포맷팅
  // ---------------------------------------------------------------------------
  /** 1024 진수, 소수 1자리. 0 이면 "0 B". */
  function formatBytes(bytes) {
    const n = Number(bytes);
    if (!Number.isFinite(n) || n <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let v = n;
    let i = 0;
    while (v >= 1024 && i < units.length - 1) {
      v /= 1024;
      i += 1;
    }
    const fixed = i === 0 ? String(Math.round(v)) : v.toFixed(1);
    return `${fixed} ${units[i]}`;
  }

  function formatByteCells() {
    document.querySelectorAll("[data-bytes]").forEach((el) => {
      const raw = el.getAttribute("data-bytes");
      el.textContent = formatBytes(raw);
    });
  }

  // ---------------------------------------------------------------------------
  // 검색
  // ---------------------------------------------------------------------------
  function bindSearch() {
    const input = document.getElementById("master-usage-search");
    const csvLink = document.getElementById("master-usage-csv");
    if (!input) return;
    input.addEventListener("input", () => {
      applySearch(input.value);
      if (csvLink) {
        const url = new URL(csvLink.getAttribute("href"), window.location.origin);
        const q = input.value.trim();
        if (q) url.searchParams.set("search", q);
        else url.searchParams.delete("search");
        csvLink.setAttribute("href", url.pathname + (url.search ? url.search : ""));
      }
    });
  }

  function applySearch(rawQuery) {
    const tbody = document.querySelector("#master-usage-table tbody");
    if (!tbody) return;
    const q = (rawQuery || "").trim().toLowerCase();
    tbody.querySelectorAll("tr[data-name]").forEach((tr) => {
      const name = tr.getAttribute("data-name") || "";
      const visible = !q || name.indexOf(q) >= 0;
      tr.classList.toggle("is-hidden", !visible);
    });
    refreshTotalsRow();
  }

  // ---------------------------------------------------------------------------
  // 정렬
  // ---------------------------------------------------------------------------
  function bindSortHandlers() {
    const table = document.getElementById("master-usage-table");
    if (!table) return;
    const headers = table.querySelectorAll("th[data-sort-key]");
    headers.forEach((th) => {
      th.addEventListener("click", () => toggleSort(th, headers));
    });
  }

  function toggleSort(clickedTh, allTh) {
    const current = clickedTh.getAttribute("data-sort-dir");
    const next = current === "asc" ? "desc" : "asc";
    allTh.forEach((th) => th.removeAttribute("data-sort-dir"));
    clickedTh.setAttribute("data-sort-dir", next);
    sortRowsBy(clickedTh.getAttribute("data-sort-key"),
               clickedTh.getAttribute("data-sort-type") || "text",
               next);
  }

  function sortRowsBy(key, type, direction) {
    const tbody = document.querySelector("#master-usage-table tbody");
    if (!tbody) return;
    const rows = Array.from(tbody.querySelectorAll("tr[data-name]"));
    const factor = direction === "asc" ? 1 : -1;
    const dataAttr = sortKeyToDataAttr(key);
    const cmp = (a, b) => compare(a.getAttribute(dataAttr), b.getAttribute(dataAttr), type) * factor;
    rows.sort(cmp);
    rows.forEach((tr) => tbody.appendChild(tr));
  }

  function sortKeyToDataAttr(key) {
    switch (key) {
      case "name":         return "data-name";
      case "active":       return "data-active";
      case "members":      return "data-members";
      case "newMembers":   return "data-new-members";
      case "articles":     return "data-articles";
      case "approvals":    return "data-approvals";
      case "storage":      return "data-storage";
      case "lastActivity": return "data-last-activity";
      default:             return "data-name";
    }
  }

  function compare(a, b, type) {
    if (type === "num" || type === "bool") {
      const na = Number(a);
      const nb = Number(b);
      const va = Number.isFinite(na) ? na : 0;
      const vb = Number.isFinite(nb) ? nb : 0;
      return va - vb;
    }
    if (type === "date") {
      // ISO yyyy-MM-ddTHH:mm:ss 문자열 사전순 비교 == 시간순. 빈 값은 가장 작게.
      const sa = a || "";
      const sb = b || "";
      if (sa === sb) return 0;
      if (sa === "") return -1;
      if (sb === "") return 1;
      return sa < sb ? -1 : 1;
    }
    return String(a || "").localeCompare(String(b || ""), "ko");
  }

  // ---------------------------------------------------------------------------
  // 합계 행
  // ---------------------------------------------------------------------------
  function refreshTotalsRow() {
    const tbody = document.querySelector("#master-usage-table tbody");
    const totalRow = document.getElementById("master-usage-total-row");
    if (!tbody || !totalRow) return;
    let members = 0, newMembers = 0, articles = 0, approvals = 0, storage = 0;
    tbody.querySelectorAll("tr[data-name]").forEach((tr) => {
      if (tr.classList.contains("is-hidden")) return;
      members    += toLong(tr.getAttribute("data-members"));
      newMembers += toLong(tr.getAttribute("data-new-members"));
      articles   += toLong(tr.getAttribute("data-articles"));
      approvals  += toLong(tr.getAttribute("data-approvals"));
      storage    += toLong(tr.getAttribute("data-storage"));
    });
    setText("total-members",     members);
    setText("total-new-members", newMembers);
    setText("total-articles",    articles);
    setText("total-approvals",   approvals);
    const storageCell = document.getElementById("total-storage");
    if (storageCell) {
      storageCell.setAttribute("data-bytes", String(storage));
      storageCell.textContent = formatBytes(storage);
    }
  }

  function setText(id, n) {
    const el = document.getElementById(id);
    if (el) el.textContent = String(n);
  }

  function toLong(s) {
    const n = Number(s);
    return Number.isFinite(n) ? n : 0;
  }

  // ---------------------------------------------------------------------------
  // 시계열 차트 (Chart.js)
  // ---------------------------------------------------------------------------
  function renderTrendChart() {
    const canvas = document.getElementById("master-usage-trend");
    if (!canvas) return;
    if (typeof window.Chart === "undefined") {
      // CDN 로드 실패 등 — 캔버스 자리 안내 텍스트로 대체.
      canvas.replaceWith(Object.assign(document.createElement("p"), {
        className: "master-meta",
        textContent: "차트를 표시할 수 없습니다 (Chart.js 로드 실패)."
      }));
      return;
    }

    const points = Array.isArray(window.__masterTrend) ? window.__masterTrend : [];
    const labels = points.map((p) => fmtShortDate(p.date));
    const memberData  = points.map((p) => Number(p.newMembers)  || 0);
    const articleData = points.map((p) => Number(p.newArticles) || 0);

    new window.Chart(canvas, {
      type: "line",
      data: {
        labels,
        datasets: [
          {
            label: "신규 회원",
            data: memberData,
            borderColor: "#0f172a",        // 진한 슬레이트 — 기준 색
            backgroundColor: "rgba(15, 23, 42, 0.12)",
            tension: 0.25,
            fill: true,
            pointRadius: 2,
            borderWidth: 2,
          },
          {
            label: "신규 게시글",
            data: articleData,
            borderColor: "#f59e0b",        // 호박색(amber) — 슬레이트와 명확히 구분되는 따뜻한 색
            backgroundColor: "rgba(245, 158, 11, 0.14)",
            tension: 0.25,
            fill: true,
            pointRadius: 2,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        plugins: {
          legend: { position: "bottom", labels: { boxWidth: 12, font: { size: 11 } } },
          tooltip: { mode: "index", intersect: false },
        },
        scales: {
          x: { ticks: { font: { size: 10 }, maxRotation: 0 }, grid: { display: false } },
          y: { ticks: { font: { size: 10 }, precision: 0 }, beginAtZero: true },
        },
      },
    });
  }

  /**
   * 서버에서 내려오는 LocalDate 직렬화 형태가 환경에 따라 다를 수 있어 방어적으로 처리.
   *  - 문자열 "yyyy-MM-dd"  : 그대로 잘라서 MM-dd 만 라벨로.
   *  - 배열 [yyyy, mm, dd] : 동일 변환.
   *  - 그 외          : toString fallback.
   */
  function fmtShortDate(d) {
    if (typeof d === "string") {
      const m = d.match(/^(\d{4})-(\d{2})-(\d{2})/);
      if (m) return `${m[2]}-${m[3]}`;
      return d;
    }
    if (Array.isArray(d) && d.length >= 3) {
      return `${pad2(d[1])}-${pad2(d[2])}`;
    }
    return String(d);
  }

  function pad2(n) { return String(n).padStart(2, "0"); }
})();
