<div align="center">

# 🏢 인트라넷 메이커 (Intranet Maker)

**회사별로 독립된 사내 인트라넷을 즉시 구축·운영할 수 있는 멀티테넌트 그룹웨어 플랫폼**

전자결재 · 게시판 · 일정 공유 · 실시간 채팅 · 근태 관리 · 조직 관리 · AI 비서를 하나로

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Oracle](https://img.shields.io/badge/Oracle%20Autonomous%20DB-F80000?logo=oracle&logoColor=white)](https://www.oracle.com/autonomous-database/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-4.0-38B2AC?logo=tailwindcss&logoColor=white)](https://tailwindcss.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

🔗 **운영 데모:** [https://intranet-maker.n-e.kr](https://intranet-maker.n-e.kr)

</div>

---

## 📑 목차

- [프로젝트 소개](#-프로젝트-소개)
- [주요 기능](#-주요-기능)
- [기술 스택](#-기술-스택)
- [시스템 아키텍처](#-시스템-아키텍처)
- [프로젝트 구조](#-프로젝트-구조)
- [시작하기](#-시작하기)
- [환경 변수](#-환경-변수)
- [빌드 및 배포](#-빌드-및-배포)
- [팀 구성 및 역할 분담](#-팀-구성-및-역할-분담)
- [라이선스](#-라이선스)

---

## 📖 프로젝트 소개

**테마형 인트라넷 메이커**는 한 번의 배포로 **여러 회사가 각자 격리된 인트라넷을 운영**할 수 있도록 설계한 멀티테넌트(multi-tenant) 사내 그룹웨어입니다.

- **MASTER 운영자**가 회사를 생성하면, 각 회사는 고유 도메인 경로(`/{회사}/login`)로 독립된 공간을 갖습니다.
- 회사별 **관리자(ADMIN) → 부관리자(SUB_ADMIN) → 일반 사용자(USER)** 의 권한 계층으로 조직을 운영합니다.
- 전자결재부터 실시간 채팅, AI 비서까지 **사내 업무에 필요한 핵심 기능을 단일 애플리케이션**에 통합했습니다.

> 본 프로젝트는 부트캠프 팀 프로젝트로, 기획 → DB 설계 → 백엔드/프론트엔드 구현 → 클라우드 배포까지 전 과정을 직접 수행했습니다.

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| 📝 **전자결재** | 휴가·지출결의·일반 양식 / 단계별 결재선 / 결재 상태 추적(대기→진행→승인/반려) / 양식 관리 / 첨부파일 |
| 📋 **게시판** | 공지·정책·자유·Q&A·익명 등 유형별 게시판 / 목록·앨범·카드 뷰 / 부서·직급별 읽기·쓰기 권한 / 댓글·첨부·인라인 이미지 / 유형별 보관 정책 |
| 📅 **일정 / 캘린더** | 일정 CRUD / 반복 일정 / 카테고리(색상) / 회원·부서 단위 공유 / 가시성 제어 / 공휴일 연동 |
| 💬 **실시간 채팅** | 1:1 대화 / **SSE 기반 실시간 수신** / 파일·이미지 첨부 / 대화방 고정·제목변경·나가기 / 미확인 메시지 카운트 |
| 🔔 **알림** | 게시글·채팅·결재 이벤트 알림 / 읽음 처리 |
| 🕒 **근태 관리** | 출퇴근 기록 / 지각·조퇴·결근·휴가 자동 판정 / 월별 근태표 / 회사별 근무 정책 |
| 🏛 **조직 관리** | 부서·직급·회원 관리 / 가입 승인 / 8종 세분화 권한(SubAdminPermission) |
| ⚙️ **시스템 관리(MASTER)** | 회사 생성·관리 / **TOTP 2차 인증** / AI 설정 / 점검 모드 / 시스템 공지 / 사용 통계 / 감사 로그 |
| 🤖 **AI 비서** | Google Gemini 연동 / 휴가·지출·일정 신청 자동 제안 / 컨텍스트 기반 응답 |

---

## 🛠 기술 스택

### Backend
- **Java 21** / **Spring Boot 3.5.13**
- **Spring Security** — 폼 로그인(회사별) + **TOTP 기반 2차 인증**(MASTER)
- **Spring Data JPA** / Hibernate
- **Thymeleaf** — 서버 사이드 렌더링 템플릿 엔진
- **Lombok**, **jsoup**(HTML 정제), **dev.samstevens.totp**(2FA)

### Frontend
- **Tailwind CSS 4** (기반 템플릿: [TailAdmin](https://tailadmin.com/))
- **Alpine.js** — 경량 상태 관리/인터랙션
- **Webpack 5** — 정적 자산 번들링
- **FullCalendar**, **ApexCharts**, **Chart.js**, **Dropzone**, **Flatpickr**, **korean-lunar-calendar**

### Database
- **Oracle Autonomous Database** (Cloud, Wallet 접속)
- **ojdbc11** + Oracle PKI (oraclepki / osdt)

### Infra / DevOps
- **Oracle Cloud Infrastructure (Always Free VM)** — Oracle Linux 9
- **Nginx** — 리버스 프록시
- **Let's Encrypt (certbot)** — HTTPS / 자동 갱신
- **systemd** — 애플리케이션 서비스 관리
- **Gradle**(bootJar) / **npm** 빌드

### External API
- **Google Gemini** — AI 비서
- **공공데이터포털 특일정보 API**(data.go.kr) — 공휴일 조회

---

## 🏗 시스템 아키텍처

```
                          🌐 Internet (HTTPS)
                                 │
                                 ▼
                   ┌──────────────────────────┐
                   │  Let's Encrypt TLS 🔒      │
                   │  Nginx (Reverse Proxy)     │   :443 / :80→301
                   └──────────────┬─────────────┘
                                  │  proxy_pass 127.0.0.1:8080
                                  ▼
                   ┌──────────────────────────┐
                   │  Spring Boot (systemd)     │   :8080 (loopback only)
                   │  ┌─────────────────────┐   │
                   │  │ Thymeleaf View       │   │ ← 서버 렌더링 화면
                   │  │ REST API (/api/**)   │   │ ← 비동기 데이터
                   │  │ SSE (/api/chat/...)  │   │ ← 실시간 채팅
                   │  │ Spring Security      │   │ ← 회사별 폼로그인 + MASTER TOTP
                   │  └─────────────────────┘   │
                   └──────┬───────────┬─────────┘
                          │           │
              JDBC(Wallet)│           │ HTTPS
                          ▼           ▼
              ┌────────────────┐  ┌──────────────────────┐
              │ Oracle         │  │ Google Gemini /        │
              │ Autonomous DB  │  │ 공휴일 API(data.go.kr) │
              └────────────────┘  └──────────────────────┘
```

**멀티테넌트 보안 모델**

```
MASTER (플랫폼 운영자, TOTP 2FA)
  └─ implies ADMIN (회사 관리자)
        └─ implies SUB_ADMIN (부관리자, 8종 세분 권한)
              └─ USER (일반 사원)
```
- 회사 간 데이터는 `MemberCompanyGuardFilter`로 격리
- 점검 모드는 `SystemMaintenanceGuardFilter`로 전역 차단

---

## 📂 프로젝트 구조

```
bootcamp_team/
├── src/main/
│   ├── java/com/team/intranet/
│   │   ├── config/          # 보안·세션·필터 (SecurityConfig, TOTP/회사격리/점검 필터 등)
│   │   ├── controller/
│   │   │   ├── view/        # Thymeleaf 화면 컨트롤러
│   │   │   └── api/         # REST / SSE API 컨트롤러
│   │   ├── service/         # 비즈니스 로직 (approval, board, chat, calendar, attendance ...)
│   │   │   └── ai/          # Gemini 연동 AI 서비스
│   │   ├── repository/      # JPA Repository
│   │   ├── entity/          # JPA 엔티티 (회원·회사·결재·게시판·채팅·근태 등)
│   │   ├── dto/             # 요청/응답 DTO (approval, ai 하위 포함)
│   │   ├── enums/           # 상태·권한·유형 Enum (member, board, attendance 하위 포함)
│   │   ├── event/           # 비동기 이벤트 (게시글/채팅/로그)
│   │   ├── scheduler/       # 보관기간 만료·정리 스케줄러
│   │   └── IntranetApplication.java
│   │
│   └── resources/
│       ├── templates/       # Thymeleaf (approval, board, calendar, master, partials ...)
│       ├── static/          # 프론트 자산 (css, js, images)  ← webpack/tailwind 빌드 결과
│       ├── application.properties           # ⚠️ Git 제외 (직접 구성)
│       └── Wallet_IntranetDB/               # ⚠️ Git 제외 (Oracle Wallet)
│
├── deploy/                  # 배포 자산
│   ├── DEPLOY.md            # OCI VM 배포 단계별 가이드
│   ├── team.service         # systemd 유닛
│   ├── team.env.example     # 환경변수 템플릿
│   └── nginx-team.conf      # Nginx 리버스 프록시 설정
│
├── build.gradle             # 백엔드 빌드 정의
├── package.json             # 프론트 빌드 스크립트
├── webpack.config.js        # 정적 자산 번들 설정
└── tailwind.config.js
```

---

## 🚀 시작하기

### 사전 요구사항

| 도구 | 버전 |
|------|------|
| JDK | 21 |
| Node.js | 18.x 이상 |
| Gradle | Wrapper 포함 (`gradlew`) |
| Oracle Autonomous DB | Wallet 파일 보유 |

> ⚠️ `application.properties`와 `Wallet_IntranetDB/`는 보안상 Git에 포함되지 않습니다. 팀 내부 채널을 통해 전달받아 `src/main/resources/` 아래에 배치하세요.

### 설치 및 로컬 실행

```bash
# 1. 저장소 클론
git clone https://github.com/amibo1347/bootcamp_team.git
cd bootcamp_team

# 2. 프론트엔드 자산 빌드
npm install
npm run build          # Webpack 번들
npm run css:build      # Tailwind output.css 생성

# 3. 백엔드 실행
./gradlew bootRun      # (Windows) .\gradlew.bat bootRun
```

실행 후 브라우저에서 **http://localhost:8080** 접속.

### 개발 편의 스크립트

```bash
npm run css:watch      # Tailwind 실시간 컴파일
npm run start          # webpack-dev-server (정적 화면 개발용)
```

---

## 🔐 환경 변수

코드는 환경 변수로 설정을 덮어쓸 수 있도록 작성되어 있습니다(미지정 시 로컬 기본값 사용). 배포 환경에서는 아래 값을 **반드시 환경 변수로 주입**하세요.

| 변수 | 설명 | 예시 |
|------|------|------|
| `SERVER_PORT` | 애플리케이션 포트 | `8080` |
| `TNS_ADMIN_DIR` | Oracle Wallet 경로 (배포 시 절대경로) | `/opt/team/wallet` |
| `DB_PASSWORD` | DB 계정 비밀번호 | `(비공개)` |
| `GEMINI_API_KEY` | Google Gemini API 키 | [발급](https://aistudio.google.com/app/apikey) |
| `HOLIDAY_API_KEY` | 공공데이터포털 특일정보 API 키 | [발급](https://www.data.go.kr/) |

---

## 📦 빌드 및 배포

### 배포용 JAR 빌드

```bash
npm install && npm run build && npm run css:build
./gradlew clean bootJar
# 결과물: build/libs/bootcamp-0.0.1-SNAPSHOT.jar
```

### 운영 환경 (현재 구성)

| 구성 요소 | 내용 |
|-----------|------|
| **호스팅** | Oracle Cloud Always Free VM (Oracle Linux 9) |
| **앱 실행** | `systemd` 서비스(`team.service`), `127.0.0.1:8080` 바인딩 |
| **리버스 프록시** | Nginx (`:443`/`:80`), 100MB 업로드 허용 |
| **HTTPS** | Let's Encrypt 인증서 + HTTP→HTTPS 자동 리다이렉트 (certbot 자동 갱신) |
| **도메인** | https://intranet-maker.n-e.kr |

> 단계별 배포 절차(인프라 생성 · 방화벽 · systemd · Nginx · HTTPS)는 [`deploy/DEPLOY.md`](deploy/DEPLOY.md)에 정리되어 있습니다.

### 재배포 (코드 수정 후)

```bash
./gradlew clean bootJar
scp -i <키> build/libs/bootcamp-0.0.1-SNAPSHOT.jar opc@<IP>:/opt/team/
ssh -i <키> opc@<IP> "sudo systemctl restart team"
```

---

## 👥 팀 구성 및 역할 분담

본 프로젝트는 **2인 팀**으로, 백엔드/프론트엔드를 분담하되 **DB 설계는 공통**으로 함께 진행했습니다.

| 역할 | 담당자 | 주요 업무 |
|------|--------|-----------|
| 🧭 **팀장 · 백엔드 / 서버 · 배포** | **강태호** ([@amibo1347](https://github.com/amibo1347)) | Spring Boot 백엔드(컨트롤러·서비스·보안·전자결재·채팅 등) 구현, Spring Security & TOTP 2차 인증, Oracle DB 연동, **OCI 클라우드 서버 구축 · Nginx · HTTPS · systemd 배포 전반** |
| 🎨 **팀원 · 프론트엔드** | **손주형** ([@sonsjh](https://github.com/sonsjh)) | Thymeleaf 화면 마크업, Tailwind CSS · Alpine.js 기반 UI/UX, FullCalendar·차트 등 프론트 라이브러리 연동, 화면 인터랙션 및 반응형 디자인 구현 |
| 🗄 **공통 · DB 설계** | **전원** | ERD 설계, 테이블·관계 정의, 도메인 모델링 (전자결재·게시판·근태·채팅 등 스키마 공동 설계) |

> 협업 방식: Git Flow 기반 브랜치 전략(`feat/*` 작업 → Pull Request → 팀장 리뷰·머지), `main`은 배포 가능 상태만 유지.

---

## 📄 라이선스

본 프로젝트는 [TailAdmin](https://tailadmin.com/) 템플릿을 기반으로 하며, **MIT License**를 따릅니다. 자세한 내용은 [`LICENSE`](LICENSE) 파일을 참고하세요.

<div align="center">

---

**테마형 인트라넷 메이커** · 부트캠프 팀 프로젝트
🔗 [https://intranet-maker.n-e.kr](https://intranet-maker.n-e.kr)

</div>
