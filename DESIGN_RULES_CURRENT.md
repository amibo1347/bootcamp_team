# 템플릿 공통 디자인 규칙서

## 1) 범위

- 이 문서는 인트라넷 프론트엔드에서 앞으로 생성/수정되는 모든 템플릿에 공통 적용할 레이아웃/색상/버튼 스타일 기준을 정의한다.
- 아래 목록은 기준을 도출한 참조 템플릿이며, 적용 범위를 제한하지 않는다.
  - `src/main/resources/templates/admin/managingDept.html`
  - `src/main/resources/templates/admin/managingPosition.html`
  - `src/main/resources/templates/board/list.html`
  - `src/main/resources/templates/board/createPost.html`
  - `src/main/resources/templates/board/viewPost.html`

## 2) 템플릿 레이아웃 규칙

### 2-1. 공통 페이지 골격

- 전체 구조: `Preloader -> Sidebar -> Overlay/Header -> Main Content`
- 루트 래퍼: `flex h-screen overflow-hidden`
- 콘텐츠 영역: `relative flex flex-1 flex-col overflow-y-auto overflow-x-hidden` (또는 동일 의미 클래스 조합)
- 메인 여백/간격:
  - `main` 기준 `p-8`
  - 섹션 간 간격 `space-y-6` 또는 `space-y-8`
- 다크모드 루트 토글:
  - `:class="{ 'dark bg-gray-900': darkMode === true }"`

### 2-2. 카드/섹션 컨테이너

- 주요 콘텐츠 박스(카드형):
  - `rounded-xl border border-stroke bg-white shadow-sm`
  - 다크모드: `dark:border-strokedark dark:bg-boxdark`
- 목록형 박스(행 hover 포함):
  - 외곽: `rounded-xl border border-gray-400 dark:border-gray-700`
  - 행 hover: `hover:bg-gray-50`, 다크모드 `dark:hover:bg-white/5`

### 2-3. 타이포그래피

- 페이지 제목: `text-2xl font-black text-gray-900 dark:text-white`
- 섹션 제목: `text-xl font-semibold` 계열
- 본문/보조 텍스트:
  - 기본 본문: `text-gray-700`
  - 보조 텍스트: `text-gray-500` 또는 `text-gray-600`
  - 다크모드 대응: `dark:text-gray-300` 또는 `dark:text-gray-200`

## 3) 색상 규칙 (공통 기본값)

### 3-1. 배경/표면

- 앱 다크 배경: `bg-gray-900` (dark 루트)
- 카드/패널 기본: `bg-white`
- 카드/패널 다크: `dark:bg-boxdark`
- 헤더/테이블 헤드 보조 배경: `bg-gray-50` 또는 `dark:bg-meta-4/60`

### 3-2. 경계선

- 카드 경계 기본: `border-stroke`
- 다크 카드 경계: `dark:border-strokedark`
- 리스트/입력 경계: `border-gray-300` 또는 `border-gray-400`
- 다크 리스트 경계: `dark:border-gray-700`

### 3-3. 포커스/인터랙션 포인트

- 주요 포커스 링: `focus:ring-indigo-300`
- 체크박스 강조: `text-indigo-600`, `focus:ring-indigo-500`

## 4) 버튼 디자인/색상 규칙

### 4-1. Primary Action (메인 실행)

- 스타일:
  - `bg-indigo-400 text-white`
  - `hover:bg-indigo-500`
  - 공통 형태: `rounded-xl font-medium` (+ 필요 시 `px-6~8 py-2.5~3`)
- 동일 규칙으로 묶이는 버튼:
  - `생성` (부서/직급 생성)
  - `글쓰기`
  - `게시글 등록`

### 4-2. Secondary Action (보조/편집 저장 계열)

- 스타일:
  - `bg-indigo-200 text-indigo-700`
  - `hover:bg-indigo-300`
  - `rounded-xl font-medium`
- 동일 규칙으로 묶이는 버튼:
  - `수정`
  - `저장`

### 4-3. Danger Action (삭제)

- 스타일:
  - `bg-rose-200 text-rose-500`
  - hover는 클래스 대신 고정 CSS `.btn-delete-hover:hover { background-color: #fda4af; }`
  - `rounded-xl font-medium`
- 동일 규칙으로 묶이는 버튼:
  - `삭제` (부서/직급 목록)

### 4-4. Neutral Action (취소/목록 이동)

- 유형 A: 라이트 중립 버튼
  - 스타일: `bg-gray-200 text-gray-700 hover:bg-gray-300`
  - 버튼:
    - `취소` (게시글 작성 페이지 링크 버튼)
    - `목록으로` (게시글 상세 페이지, 라이트 모드 기준)
- 유형 B: 편집 취소(리스트 아이템 내)
  - 스타일: `bg-gray-200 text-gray-500 hover:bg-gray-300`
  - 다크모드: `dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-800`
  - 버튼:
    - `취소` (부서/직급 목록 편집 취소)

## 5) 입력 컴포넌트 색상 규칙

- 기본 인풋:
  - `border-gray-300` 또는 `border-gray-400`
  - `bg-white text-gray-900`
  - `focus:ring-indigo-300`
- 다크모드 인풋:
  - `dark:bg-meta-4 dark:text-white`
  - `dark:border-gray-600`

## 6) 문서 사용 원칙

- 새로운 템플릿을 만들 때는 본 문서의 레이아웃/색상/버튼 규칙을 기본값으로 적용한다.
- 기존 템플릿을 수정할 때도 본 문서와 불일치하는 스타일은 가능한 범위에서 본 기준으로 정렬한다.
- 텍스트가 달라도 역할이 같으면 동일 버튼 규칙을 재사용한다.
- 신규 색상 또는 신규 버튼 타입이 필요하면, 먼저 본 문서에 규칙을 추가한 뒤 템플릿에 반영한다.
