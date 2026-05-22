# 템플릿 공통 디자인 규칙서

## 1) 범위

- 이 문서는 인트라넷 프론트엔드에서 앞으로 생성/수정되는 모든 템플릿에 공통 적용할 레이아웃/색상/버튼 스타일 기준을 정의한다.
- 아래 목록은 기준을 도출한 참조 템플릿이며, 적용 범위를 제한하지 않는다.
  - `src/main/resources/templates/admin/managingDept.html`
  - `src/main/resources/templates/admin/managingPosition.html`
  - `src/main/resources/templates/board/list.html`
  - `src/main/resources/templates/board/createPost.html`
  - `src/main/resources/templates/board/viewPost.html`
  - `src/main/resources/templates/admin/managingBoard.html` (네이티브 `select`·커스텀 드롭다운 패턴 참조)

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
  - 공통 형태: `rounded-lg font-medium` (+ 필요 시 `px-6~8 py-2.5~3`)
- 동일 규칙으로 묶이는 버튼:
  - `생성` (부서/직급 생성)
  - `글쓰기`
  - `게시글 등록`

### 4-2. Secondary Action (보조/편집 저장 계열)

- 스타일:
  - `bg-indigo-200 text-indigo-700`
  - `hover:bg-indigo-300`
  - `rounded-lg font-medium`
- 동일 규칙으로 묶이는 버튼:
  - `수정`
  - `저장`

### 4-3. Danger Action (삭제)

- 스타일:
  - `bg-rose-200 text-rose-500`
  - hover는 클래스 대신 고정 CSS `.btn-delete-hover:hover { background-color: #fda4af; }`
  - `rounded-lg font-medium`
- 동일 규칙으로 묶이는 버튼:
  - `삭제` (부서/직급 목록)

### 4-4. Neutral Action (취소/목록 이동)

- 유형 A: 라이트 중립 버튼
  - 스타일: `bg-gray-200 text-gray-700 hover:bg-gray-300 rounded-lg font-medium`
  - 버튼:
    - `취소` (게시글 작성 페이지 링크 버튼)
    - `목록으로` (게시글 상세 페이지, 라이트 모드 기준)
- 유형 B: 편집 취소(리스트 아이템 내)
  - 스타일: `bg-gray-200 text-gray-500 hover:bg-gray-300 rounded-lg font-medium`
  - 다크모드: `dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-800`
  - 버튼:
    - `취소` (부서/직급 목록 편집 취소)

### 4-5. State Toggle - Pause (일시 보류 / 비활성화)

- 스타일:
  - `bg-violet-200 text-violet-700`
  - `hover:bg-violet-300`
  - `rounded-lg font-medium`
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
  - `rounded-lg font-medium`
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

### 5-2. 드롭다운 (`<select>` 및 커스텀 패널)

기준 템플릿: `src/main/resources/templates/admin/managingBoard.html`. 네이티브 셀렉트와, 버튼으로 열리는 목록(부서·직급 다중 선택 등)을 같은 톤으로 맞춘다.

#### 라벨·래퍼

- 필드 라벨: `mb-1 block text-sm text-gray-600 dark:text-gray-300`
- `select` 또는 트리거·패널이 겹침(z-index)이 필요한 카드 안에서는 바깥 래퍼에 `relative z-20`을 둔다(동일 파일의 그리드·접기 패널과 일치).

#### 네이티브 `<select>` (폼 필드형)

- **열린 상태(옵션 목록 팝업):** 브라우저·OS가 그리는 영역이라 **Tailwind로 디자인 시스템과 동일하게 꾸밀 수 없다.** 열림 UI까지 통일하려면 `managingBoard`의 부서·직급처럼 **버튼 + 패널(커스텀 listbox)** 으로 바꾸거나, 캘린더 일정 모달 단일 선택처럼 **숨은 `select` + 트리거 버튼 + 패널** 패턴을 쓴다.
- 공통 클래스 조합:
  - `relative z-20 w-full appearance-none rounded-lg border border-gray-300 bg-transparent px-4 py-2.5 text-gray-900`
  - 포커스: `focus:outline-none focus:ring-2 focus:ring-indigo-300`
  - 다크: `dark:border-gray-700 dark:bg-meta-4 dark:text-white dark:[color-scheme:dark] dark:focus:ring-indigo-400/40`
- `appearance-none`으로 OS 기본 화살표를 숨긴 경우, 필요하면 별도 배경 이미지나 형제 요소로 화살표를 보강한다(해당 템플릿은 브라우저 기본과 맞춘 구성을 따른다).
- **빌드·캐시 안정성:** 템플릿에만 긴 유틸 클래스를 나열하면 환경에 따라 누락으로 보일 수 있다. 캘린더 일정 모달의 **다중 선택 `select`** 는 `input.css`의 **`.calendar-native-select-multiple`**(`@apply`)로 `output.css`에 포함시킨다. 단일 선택은 위 이유로 **커스텀 콤보박스**(`calendar-event-modal.html` + `calendar-init.js`, 스타일: **`.calendar-combobox-trigger`**, **`.cal-combobox-panel`**, **`.cal-combobox-option`**)를 사용한다.

#### 커스텀 드롭다운 트리거(버튼, 예: 부서·직급 선택)

- 트리거 버튼:
  - `flex w-full items-center justify-between gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2.5 text-left text-sm font-medium text-gray-800 shadow-sm transition`
  - hover: `hover:border-indigo-200 hover:bg-indigo-50/40`
  - 포커스: `focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-300`
  - 다크: `dark:border-strokedark dark:bg-boxdark dark:text-gray-200 dark:hover:border-indigo-500/40 dark:hover:bg-indigo-950/30`
- 표시 텍스트는 길어질 수 있으므로 `span`에 `truncate`를 둔다.
- 화살표 영역(아이콘 래퍼): `inline-flex shrink-0 text-gray-400 transition-transform duration-200 ease-out dark:text-gray-300` (접기/펼치기 스크립트와 연동 시 `rotate-180` 등은 기존 패턴에 맞춘다).

#### 드롭다운 패널(목록 박스)

- 패널 컨테이너:
  - `mt-2 hidden max-h-36 overflow-y-auto rounded-lg border border-gray-300 bg-white p-3`
  - 다크: `dark:border-strokedark dark:bg-meta-4 dark:text-gray-300`
- 목록 상단 구역 제목(예: 전체 선택 행): `flex items-center gap-2 py-1 text-sm font-semibold text-gray-700 border-b border-gray-200 mb-2 pb-2 dark:border-gray-600 dark:text-gray-300`
- 일반 행 라벨: `flex items-center gap-2 py-1 text-sm text-gray-700 dark:text-gray-300`

#### 대형 섹션 접기/펼치기(아코디언형, 예: 권한 설정·기타 설정)

- 전체 폭 버튼 트리거: `group flex w-full items-center justify-between gap-3 rounded-lg border border-gray-200 bg-gradient-to-br from-gray-50 to-white px-4 py-3.5 text-left shadow-sm transition hover:border-indigo-200 hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400 focus-visible:ring-offset-2 dark:border-strokedark dark:from-meta-4 dark:to-boxdark dark:hover:border-indigo-500/50 dark:focus-visible:ring-offset-gray-900`
- 아이콘 타일·부제·화살표 색은 해당 템플릿의 `권한 설정` / `기타 설정` 블록과 동일 계열(indigo·slate 아이콘 박스, `group-hover:text-indigo-500` 등)을 재사용한다.

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
| 네이티브 `select` | `border-gray-300`, `text-gray-900`, `focus:ring-indigo-300` | `dark:border-gray-700`, `dark:bg-meta-4`, `dark:text-white`, `dark:focus:ring-indigo-400/40`, `dark:[color-scheme:dark]` |
| 커스텀 드롭다운 트리거 | `border-gray-200`, `bg-white`, `text-gray-800` | `dark:border-strokedark`, `dark:bg-boxdark`, `dark:text-gray-200` |
| 드롭다운 패널 | `border-gray-300`, `bg-white` | `dark:border-strokedark`, `dark:bg-meta-4`, `dark:text-gray-300` |
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

- 새로운 템플릿을 만들 때는 본 문서의 레이아웃/색상/버튼·**5-2 드롭다운** 규칙을 기본값으로 적용한다.
- 기존 템플릿을 수정할 때도 본 문서와 불일치하는 스타일은 가능한 범위에서 본 기준으로 정렬한다.
- 텍스트가 달라도 역할이 같으면 동일 버튼 규칙을 재사용한다.
- 신규 색상 또는 신규 버튼 타입이 필요하면, 먼저 본 문서에 규칙을 추가한 뒤 템플릿에 반영한다.
- 네이티브 `select`·커스텀 패널 등 **드롭다운 UI를 새로 도입**할 때는 **5-2**에 맞추고, 6-3 표의 `select`/패널 행과 톤을 맞춘다.
- 다크모드 대응을 추가할 때는 **6절 매핑**과 기존 `partials` 패턴을 맞춘다.
- 상태를 토글하는 **반대쌍 버튼**(예: `휴직`↔`복귀`)은 같은 색이 아니라 **의미가 다른 색**(Pause/Restore 규칙 참조)으로 구분한다.
