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

### 4-5. State Toggle - Pause (일시 보류 / 비활성화)

- 스타일:
  - `bg-violet-200 text-violet-700`
  - `hover:bg-violet-300`
  - `rounded-xl font-medium`
- 동일 규칙으로 묶이는 버튼:
  - `휴직` (직원 목록)
- 적용 맥락:
  - 활성 상태를 **일시적으로 비활성**으로 토글하는 동작
  - 데이터 삭제(Danger)와는 구분되며, 되돌릴 수 있는 상태 변경에 사용
  - 시각적으로는 Secondary(`indigo-200`)와 Danger(`rose-200`) 사이에 위치하여 색상환의 자연스러운 흐름을 유지

### 4-6. State Toggle - Restore (회복 / 재활성화)

- 스타일:
  - `bg-emerald-200 text-emerald-700`
  - `hover:bg-emerald-300`
  - `rounded-xl font-medium`
- 동일 규칙으로 묶이는 버튼:
  - `복귀` (휴직 직원 목록)
- 적용 맥락:
  - 비활성 상태를 **다시 활성**으로 되돌리는 긍정적 회복 동작
  - `4-5 Pause`와 짝(pair)을 이루는 반대 액션이며, 의미 구분을 위해 의도적으로 다른 색조(보라 ↔ 초록)를 사용

## 5) 입력 컴포넌트 색상 규칙

- 기본 인풋:
  - `border-gray-300` 또는 `border-gray-400`
  - `bg-white text-gray-900`
  - `focus:ring-indigo-300`
- 다크모드 인풋:
  - `dark:bg-meta-4 dark:text-white`
  - `dark:border-gray-600`

## 6) 다크모드 시 템플릿 지정값 (라이트 → 다크)

Tailwind `dark:` 변형은 **`body`(또는 상위 래퍼)에 `.dark` 클래스가 있을 때** 적용된다. 이 프로젝트에서는 Alpine.js로 `darkMode === true`일 때 `dark bg-gray-900`를 `body`에 붙인다.

### 6-1. 루트·상태

| 항목 | 라이트(기본) | 다크(활성) |
|------|----------------|------------|
| `body` 클래스 | (없음 또는 기본 배경) | `dark bg-gray-900` |
| 테마 저장 | — | `localStorage` 키 `darkMode` (JSON boolean) |
| 공통 스타일 조각 | — | `partials/darkmode-style :: darkmodeStyle` (링크·로그인/회원가입 필드 보정) |

### 6-2. 색상 토큰 (Tailwind `@theme` 기준)

디자인 규칙서에서 쓰는 **의미 있는 이름 → 실제 색**(다크 조합 시 참고용):

| 토큰 이름 | HEX (참고) |
|-----------|------------|
| `stroke` | `#e4e7ec` |
| `strokedark` | `#344054` |
| `boxdark` | `#1d2939` |
| `meta-4` | `#313d4a` |

### 6-3. 템플릿에서 반복되는 라이트 → 다크 클래스 매핑

아래는 게시판·관리자·`partials` 등에서 함께 쓰는 **한 쌍**을 정리한 것이다. 새 화면도 같은 역할이면 동일 쌍을 우선 적용한다.

| 용도 | 라이트(지정값) | 다크(지정값) |
|------|----------------|--------------|
| 앱 전체 느낌의 본문 배경 | (기본 또는 흰 계열 카드 위주) | `body`: `bg-gray-900` + 자식 카드는 `dark:bg-*`로 대비 |
| 카드/패널 배경·테두리 | `bg-white`, `border-stroke` | `dark:bg-boxdark`, `dark:border-strokedark` |
| 목록 컨테이너 외곽선 | `border-gray-400` | `dark:border-gray-700` |
| 목록 행 hover | `hover:bg-gray-50` | `dark:hover:bg-white/5` |
| 페이지 제목 | `text-gray-900` | `dark:text-white` |
| 강조 제목/본문 텍스트 | `text-black`, `text-gray-900` | `dark:text-white` |
| 본문·라벨 | `text-gray-700`, `text-gray-600` | `dark:text-gray-300` (또는 맥락에 따라 `dark:text-gray-200`) |
| 보조·설명 | `text-gray-500` | `dark:text-gray-400` 또는 `dark:text-gray-300` |
| 빈 상태·알림 보조 | `text-gray-400`, `text-gray-500` | `dark:text-gray-300` |
| 테이블 헤더 배경 | `bg-gray-50` | `dark:bg-meta-4/60` |
| 테이블 헤더 글자 | `text-gray-600` | `dark:text-gray-300` |
| 아이콘/보조 버튼(테두리형) | `border-stroke`, `text-gray-600` | `dark:border-strokedark`, `dark:text-gray-300` (+ hover는 `dark:hover:border-indigo-500/60`, `dark:hover:bg-indigo-500/10` 등 기존 패턴 유지) |
| 입력 필드 | `bg-white`, `text-gray-900`, `border-gray-300`/`400` | `dark:bg-meta-4`, `dark:text-white`, `dark:border-gray-600` |
| placeholder | `placeholder:text-gray-400` | `dark:placeholder:text-gray-300` (해당 인풋에 명시하는 경우) |
| 구분선(세로 막대 등) | `bg-gray-200` | `dark:bg-strokedark` |
| Neutral B 취소 버튼 | `bg-gray-200 text-gray-500` | `dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-800` |
| Pause 버튼(휴직) | `bg-violet-200 text-violet-700 hover:bg-violet-300` | (다크 전용 클래스 없음, 라이트 톤 그대로 사용) |
| Restore 버튼(복귀) | `bg-emerald-200 text-emerald-700 hover:bg-emerald-300` | (다크 전용 클래스 없음, 라이트 톤 그대로 사용) |
| 사이드바(공통 partial) | `border-gray-200`, `bg-white` | `dark:border-gray-800`, `dark:bg-black` |
| 로고 이미지 | 라이트용 `dark:hidden` | 다크용 `hidden dark:block` + `logo-dark.svg` |

#### 프로필·캘린더 등 ‘반투명 카드’ 계열

일부 화면은 카드에 `border-gray-200 bg-white` 대신 **`dark:border-gray-800 dark:bg-white/[0.03]`** 를 쓴다. 동일 UI 계열을 새로 만들 때 이 패턴을 재사용할 수 있다.

#### 위험/경고 박스 (예: 통합 휴지통 오류 영역)

| 라이트 | 다크 |
|--------|------|
| `border-rose-200 bg-rose-50 text-rose-800` | `dark:border-rose-500/40 dark:bg-rose-950/40 dark:text-rose-200` |

### 6-4. `darkmode-style` 조각으로만 보정되는 값

Tailwind 클래스만으로 안 맞는 부분은 `partials/darkmode-style.html`에서 보완한다.

| 대상 | 다크에서의 지정 동작 |
|------|----------------------|
| `main` 안 **클래스에 `text-`가 없는 링크** | 기본 글자색 **하늘색 계열** (`rgb(96 165 250)`), hover 시 더 밝게 (`rgb(147 197 253)`) |
| 로그인/회원가입 카드 (`.login-card`, `.signup-card`) | 라벨 `#d1d5db`; 입력 배경 `#313d4a`, 글자 `#f2f4f7`, 테두리 투명 처리, placeholder `#98a2b3` (인라인 스타일보다 우선) |

---

## 7) 문서 사용 원칙

- 새로운 템플릿을 만들 때는 본 문서의 레이아웃/색상/버튼 규칙을 기본값으로 적용한다.
- 기존 템플릿을 수정할 때도 본 문서와 불일치하는 스타일은 가능한 범위에서 본 기준으로 정렬한다.
- 텍스트가 달라도 역할이 같으면 동일 버튼 규칙을 재사용한다.
- 신규 색상 또는 신규 버튼 타입이 필요하면, 먼저 본 문서에 규칙을 추가한 뒤 템플릿에 반영한다.
- 다크모드 대응을 추가할 때는 **6절 매핑**과 기존 `partials` 패턴을 맞춘다.
- 상태를 토글하는 **반대쌍 버튼**(예: `휴직`↔`복귀`)은 같은 색이 아니라 **의미가 다른 색**(Pause/Restore 규칙 참조)으로 구분한다.
