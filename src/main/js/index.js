// ============================================================
// [의존성 임포트] : Alpine.js (UI 반응형), flatpickr (날짜 선택), Dropzone (파일 업로드)
// ============================================================
import Alpine from "alpinejs";
import persist from "@alpinejs/persist";
import flatpickr from "flatpickr";
import Dropzone from "dropzone";

// [서드파티 CSS 동적 삽입] : Vite 번들에 포함되지 않는 CSS를 <link>로 주입
// output.css는 각 mustache 파일의 <head>에 이미 <link>로 선언되어 있으므로 여기서 제외
const injectCss = (href) => {
  try {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    document.head.appendChild(link);
  } catch (e) {
    // 비브라우저 환경(테스트 등)에서 무시
  }
};

injectCss('/css/jsvectormap.min.css');
injectCss('/css/flatpickr.min.css');
injectCss('/css/dropzone.css');

// ============================================================
// [Alpine.js 초기화] : persist 플러그인으로 다크모드 등 localStorage 연동
// ============================================================
Alpine.plugin(persist);
window.Alpine = Alpine;
Alpine.start();

// ============================================================
// [flatpickr 초기화] : .datepicker 클래스 요소에 날짜 범위 선택기 적용 (캘린더 페이지)
// ============================================================
flatpickr(".datepicker", {
  mode: "range",
  static: true,
  monthSelectorType: "static",
  dateFormat: "M j",
  defaultDate: [new Date().setDate(new Date().getDate() - 6), new Date()],
  prevArrow:
    '<svg class="stroke-current" width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M15.25 6L9 12.25L15.25 18.5" stroke="" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  nextArrow:
    '<svg class="stroke-current" width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M8.75 19L15 12.75L8.75 6.5" stroke="" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
  onReady: (selectedDates, dateStr, instance) => {
    instance.element.value = dateStr.replace("to", "-");
    const customClass = instance.element.getAttribute("data-class");
    instance.calendarContainer.classList.add(customClass);
  },
  onChange: (selectedDates, dateStr, instance) => {
    instance.element.value = dateStr.replace("to", "-");
  },
});

// ============================================================
// [Dropzone 초기화] : #demo-upload 요소가 있을 때만 파일 업로드 영역 활성화
// ============================================================
const dropzoneArea = document.querySelectorAll("#demo-upload");
if (dropzoneArea.length) {
  new Dropzone("#demo-upload", { url: "/file/post" });
}

// ============================================================
// [DOMContentLoaded 이벤트] : DOM 준비 후 실행되는 UI 초기화 로직
// ============================================================
document.addEventListener("DOMContentLoaded", () => {

  // [연도 표시] : #year 요소에 현재 연도 자동 삽입 (푸터 등)
  const year = document.getElementById("year");
  if (year) {
    year.textContent = new Date().getFullYear();
  }

  // [URL 복사 버튼] : #copy-button 클릭 시 #website-input 값을 클립보드에 복사, 2초 후 텍스트 복원
  const copyInput = document.getElementById("copy-input");
  if (copyInput) {
    const copyButton = document.getElementById("copy-button");
    const copyText = document.getElementById("copy-text");
    const websiteInput = document.getElementById("website-input");

    if (copyButton) {
      copyButton.addEventListener("click", () => {
        navigator.clipboard.writeText(websiteInput.value).then(() => {
          copyText.textContent = "Copied";
          setTimeout(() => {
            copyText.textContent = "Copy";
          }, 2000);
        });
      });
    }
  }

  // [검색 키보드 단축키] : ⌘K 또는 Ctrl+K, 그리고 '/' 키로 검색창 포커스
  const searchInput = document.getElementById("search-input");
  const searchButton = document.getElementById("search-button");

  if (searchInput && searchButton) {
    searchButton.addEventListener("click", () => searchInput.focus());

    document.addEventListener("keydown", (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key === "k") {
        event.preventDefault();
        searchInput.focus();
      }
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "/" && document.activeElement !== searchInput) {
        event.preventDefault();
        searchInput.focus();
      }
    });
  }
});
