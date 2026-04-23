# Intranet 프로젝트 전환 문서

> 이 문서는 기능 요구사항(PRD) 문서가 아니라, **원래 프로젝트(Spring Boot + Mustache)** 에서 **현재 구조(Spring Boot REST API + Next.js 프론트엔드 모노레포)** 로 바뀌면서 달라진 점을 정리한 전환 문서입니다. 혼자서든 팀원과든 이 프로젝트를 다시 잡았을 때 "뭐가 어디 있고, 왜 이렇게 바뀌었고, 어떻게 돌리고, 어디를 건드려야 하는지" 를 빠르게 파악하기 위한 자료입니다.

---

## 1. 프로젝트 구조

루트는 모노레포(monorepo)입니다. 즉 하나의 Git 저장소 안에 백엔드(`backend/`)와 프론트엔드(`frontend/`) 두 개의 프로젝트가 같이 살고 있습니다. 각자 자기 자신의 의존성 파일(`build.gradle`, `package.json`)을 가지고 독립적으로 실행됩니다.

```
team/
├── backend/                    Spring Boot REST API 서버
│   ├── src/main/java/com/team/intranet/
│   │   ├── IntranetApplication.java     애플리케이션 시작점
│   │   ├── config/                       Spring 설정 클래스
│   │   │   ├── SecurityConfig.java          JWT 단일 체인 + CORS 설정
│   │   │   ├── CustomUserDetailsService.java   DB에서 사용자 로드 (AuthenticationManager 가 사용)
│   │   │   └── jwt/                            JWT 관련 클래스
│   │   │       ├── JwtProperties.java             app.jwt.* 설정 바인딩
│   │   │       ├── JwtTokenProvider.java          토큰 발급·검증
│   │   │       └── JwtAuthenticationFilter.java   Authorization 헤더에서 JWT 읽어 인증 세팅
│   │   ├── controller/
│   │   │   └── api/                        REST API (JSON 응답, `/api/**`)
│   │   │       ├── AuthApiController.java     /api/auth/login, signup, company/verify, me
│   │   │       └── MemberApiController.java   /api/member/check-id (아이디 중복 확인)
│   │   ├── dto/                            데이터 전송 객체 (요청/응답 DTO)
│   │   │   ├── MemberDto.java, DeptDto.java, PositionDto.java
│   │   │   └── auth/                          API 전용 인증 DTO
│   │   │       ├── LoginRequest.java, LoginResponse.java
│   │   │       └── SignupRequest.java, CompanyVerifyResponse.java
│   │   ├── entity/                         JPA 엔티티 (DB 테이블에 매핑)
│   │   │   └── Member.java, Company.java, Dept.java, Position.java
│   │   ├── enums/                          타입 상수
│   │   │   ├── IsActive.java, ErrorCode.java
│   │   │   └── member/Role.java, member/Status.java, member/MemberType.java
│   │   ├── exception/                      예외 처리
│   │   │   ├── BusinessException.java        도메인 예외
│   │   │   └── ApiExceptionHandler.java      `/api/**` 예외를 JSON 에러 응답으로 변환
│   │   ├── repository/                     Spring Data JPA 리포지토리
│   │   │   └── MemberRepository.java, CompanyRepository.java, DeptRepository.java, PositionRepository.java
│   │   └── service/                        비즈니스 로직
│   │       └── MemberService.java, DeptService.java, PositionService.java
│   ├── src/main/resources/
│   │   ├── application.yml                 공통 설정 (환경변수 기반)
│   │   ├── application-prod.yml            Docker/운영 프로파일
│   │   ├── application-local.yml           로컬 개발용 (git-ignored, DB 자격증명 포함)
│   │   ├── application-dev.yml.example     팀원 세팅용 템플릿
│   │   └── Wallet_IntranetDB/              Oracle 자격증명 Wallet (git-ignored)
│   ├── src/test/                           JUnit 테스트
│   ├── build.gradle, settings.gradle       Gradle 빌드 설정
│   ├── gradlew, gradlew.bat, gradle/       Gradle wrapper
│   └── Dockerfile, .dockerignore           컨테이너 빌드 설정
│
├── frontend/                   Next.js 프론트엔드
│   ├── app/                                App Router (Next.js 라우팅)
│   │   ├── layout.tsx                         루트 레이아웃 (모든 페이지 공통 HTML 껍데기)
│   │   ├── globals.css                        Tailwind v4 import
│   │   ├── login/                             /login 페이지
│   │   │   ├── page.tsx                          로그인 폼
│   │   │   └── company-verify-modal.tsx          기업 코드 인증 모달
│   │   ├── signup/
│   │   │   └── page.tsx                          /signup 페이지
│   │   └── (dashboard)/                      라우트 그룹 (URL 에는 안 나타남)
│   │       ├── layout.tsx                        인증 가드 + 사이드바 + 헤더 껍데기
│   │       ├── sidebar.tsx                       좌측 메뉴
│   │       ├── header.tsx                        상단 바
│   │       ├── page.tsx                          / (대시보드 홈)
│   │       ├── calendar/page.tsx                 /calendar (FullCalendar)
│   │       ├── profile/page.tsx                  /profile (TailAdmin 데모)
│   │       ├── settings/page.tsx                 /settings (placeholder)
│   │       ├── form-elements/page.tsx            /form-elements (placeholder)
│   │       ├── tables/page.tsx                   /tables (placeholder)
│   │       └── blank/page.tsx                    /blank (placeholder)
│   ├── lib/                                공통 유틸
│   │   ├── types.ts                           백엔드 DTO 와 맞춘 TypeScript 타입
│   │   ├── api.ts                             fetch 래퍼 (JWT 자동 주입, 에러 변환)
│   │   └── auth-store.ts                      Zustand 로 JWT·사용자 정보 보관
│   ├── public/                              정적 파일 (Next.js 가 / 로 서빙)
│   ├── .env.local                           로컬 환경변수 (NEXT_PUBLIC_API_BASE_URL 등, git-ignored)
│   ├── .env.example                         팀원 세팅용 템플릿
│   ├── package.json                         Next.js / React / Tailwind / Zustand 등 의존성
│   ├── tsconfig.json                        TypeScript 설정
│   └── next.config.ts, postcss.config.mjs, eslint.config.mjs
│
├── docker-compose.yml          백엔드 컨테이너 + Wallet 볼륨 마운트 정의
├── .env.example                docker-compose 용 환경변수 템플릿
├── .gitignore                  build 산출물, node_modules, 자격증명 yml, Wallet 등 제외
├── docs/
│   └── prd.md                  (이 문서)
└── LICENSE, README.md, .vscode/, .gitattributes
```

### 구조에서 신경 써야 할 포인트

- **백엔드와 프론트엔드는 포트가 다른 별개의 서버**입니다. 백엔드는 기본 `8080`, 프론트엔드는 기본 `3000`. 둘 다 띄워야 로그인이 동작합니다.
- **백엔드는 순수 JSON REST API 서버**입니다. 이전 Mustache 뷰 / form-login / 세션 저장 / webpack CSS 번들링 파이프라인은 전부 제거됐고, 지금은 `/api/**` 경로로 JSON 만 주고받습니다.
- **관리자용 UI는 현재 없습니다.** 백엔드에는 회원 승인/반려·부서 CRUD·직급 CRUD 같은 서비스 로직(`MemberService`, `DeptService`, `PositionService`)이 남아있지만, 이걸 노출하는 REST 컨트롤러는 아직 만들지 않았습니다. 원본 프로젝트에서도 관리자 UI 는 템플릿이 없어 동작하지 않았기 때문에 "없던 것을 억지로 만들지 않는다" 는 방침으로 그대로 둔 상태입니다.

---

## 2. 프로젝트 실행 방법

### 2-1. 처음 한 번만 할 준비

필수:
- **JDK 21** (Gradle wrapper 가 같이 들어있어서 Gradle 자체는 설치 안 해도 됨)
- **Node.js 20 이상** 권장 (개발 당시 v24 확인)
- `backend/src/main/resources/Wallet_IntranetDB/` 가 실제로 있는지 확인. 없으면 Oracle Autonomous DB 에서 다시 받아서 넣어야 함 (git-ignored 라 클론해도 안 따라옴).
- `backend/src/main/resources/application-local.yml` 가 있는지 확인. 없으면 `application-dev.yml.example` 을 복사해서 DB 비밀번호와 JWT 시크릿을 채워 넣어야 함.
- `frontend/.env.local` 가 있는지 확인. 없으면 `frontend/.env.example` 을 복사. 기본값은 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`.

선택:
- **Docker Desktop** — Docker 로 백엔드를 실행하고 싶을 때만 필요.

### 2-2. 로컬에서 개발할 때

터미널 두 개를 나란히 띄워놓고 쓰는 게 편합니다.

**터미널 1 — 백엔드**
```
cd backend
./gradlew bootRun
```
- 기본 프로파일은 `local` 이라 `application-local.yml` 을 읽어서 로컬 Wallet 경로와 DB 자격증명으로 접속합니다.
- 8080 포트로 뜹니다.
- 코드 수정 시 `spring-boot-devtools` 가 자동 재시작을 시도합니다 (거의 즉시 반영됨).

**터미널 2 — 프론트엔드**
```
cd frontend
npm install       # 처음 한 번만, 또는 package.json 이 바뀌었을 때
npm run dev
```
- 3000 포트로 뜹니다. 이미 사용 중이면 3001, 3002 순으로 올라갑니다.
- 파일을 저장하면 브라우저가 자동 리로드됩니다 (Hot Reload).

**접속**
- 브라우저에서 `http://localhost:3000/login` 으로 이동.
- 로그인 → JWT 를 받아 localStorage 에 저장 → 대시보드(`/`) 로 리다이렉트.

### 2-3. Docker 로 백엔드를 실행할 때

```
cp .env.example .env     # 한 번만. DB 자격증명, JWT 시크릿 등 채워 넣기
docker compose up --build
```
- `docker-compose.yml` 이 Wallet 폴더를 컨테이너의 `/app/wallet` 에 읽기 전용 볼륨으로 마운트합니다.
- 프로파일은 자동으로 `prod` 가 활성화됩니다 (`.env` 의 `SPRING_PROFILES_ACTIVE` 기본값).
- 프론트엔드는 현재 docker-compose 에 포함돼 있지 않으니, 프론트엔드는 여전히 로컬에서 `npm run dev` 로 띄우면 됩니다.

### 2-4. 배포 빌드 확인

```
# 백엔드 fat jar 빌드
cd backend
./gradlew clean bootJar
# build/libs/*.jar 가 나옴

# 프론트엔드 프로덕션 빌드
cd frontend
npm run build
npm run start   # 빌드 결과물 실행
```

---

## 3. 이 프로젝트에서 알아야 할 것들

### 3-1. 인증은 JWT Bearer 토큰 한 방식입니다
- 로그인 성공 시 `AuthApiController` 가 `JwtTokenProvider` 로 토큰을 발급하고 JSON 으로 반환합니다.
- 프론트엔드는 그 토큰을 `Authorization: Bearer <토큰>` 헤더에 실어 모든 API 요청에 붙입니다. `lib/api.ts` 가 자동으로 해줍니다.
- 서버는 `JwtAuthenticationFilter` 에서 토큰을 검증하고 `SecurityContextHolder` 에 Authentication 을 세팅. 이후 컨트롤러에서 `Authentication` 파라미터로 현재 사용자 정보를 받을 수 있습니다.
- 서버는 세션을 저장하지 않는 stateless 구조이므로, 로드 밸런싱이나 재시작에도 토큰만 유효하면 상태가 유지됩니다.

### 3-2. JWT 는 localStorage 에 저장됩니다
- `lib/auth-store.ts` 의 Zustand persist 가 `intranet-auth` 라는 키로 localStorage 에 보관.
- 장점: 간단함, 구현이 빠름. 단점: XSS 공격에 노출될 수 있음 (페이지에 악성 스크립트가 주입되면 토큰 탈취 가능).
- 프로덕션으로 올리기 전에는 httpOnly 쿠키 방식으로 바꾸는 걸 검토할 가치가 있습니다. 지금은 개발 편의성 쪽으로 선택한 상태.

### 3-3. 자격증명과 Wallet 은 저장소에 없습니다
- `application-local.yml`, `application-dev.yml`, `.env`, `.env.local`, `backend/src/main/resources/Wallet_IntranetDB/` 는 전부 `.gitignore` 처리됨.
- 새 컴퓨터에서 클론하면 이 파일들을 **직접 다시 준비**해야 합니다.
- 팀원 온보딩용으로는 `.env.example`, `application-dev.yml.example` 이 참고가 됩니다.

### 3-4. Oracle Autonomous DB 연결은 Wallet 기반입니다
- 일반 JDBC URL 처럼 "host:port" 를 직접 쓰지 않고, Wallet 폴더를 가리키면 그 안의 `tnsnames.ora` / `cwallet.sso` 등을 읽어서 자동으로 암호화 연결을 엽니다.
- 그래서 Docker 로 실행할 때도 Wallet 폴더를 볼륨 마운트해줘야 합니다. `TNS_ADMIN` 환경변수가 그 경로를 가리킵니다.

### 3-5. 관리자 기능의 현재 상태
- 백엔드 서비스 레이어: **살아 있음.** `MemberService` 의 승인/반려/퇴사 메서드, `DeptService` / `PositionService` 의 CRUD 가 도메인 로직으로 남아있습니다. 파라미터는 `Member admin` 을 받도록 정리돼 있어 REST 컨트롤러에서 바로 꽂아 쓸 수 있습니다.
- REST 컨트롤러: **없음.** `/api/admin/**` 같은 엔드포인트는 지금 노출돼 있지 않습니다. 필요해지면 해당 서비스를 호출하는 `@RestController` 를 만들고, SecurityConfig 에 `hasRole("ADMIN")` 규칙을 추가하면 됩니다.
- 프론트엔드 UI: **없음.** 사이드바에도 관리자 메뉴가 없습니다.

### 3-6. `/404` 경로는 Next.js 가 선점한 이름이라 못 씁니다
- 사이드바 "미정2" 링크가 `/404` 로 가는데, Next.js 의 내장 404 핸들러가 잡아서 커스텀 페이지가 뜨지 않습니다. 원본 Mustache 에서도 `href="404.html"` 이라는 깨진 링크였으므로 동작 차이는 없음.

---

## 4. 작업 가이드 — 어떻게 코드를 추가/수정하는가

### 4-1. 새로운 API 엔드포인트 추가 (백엔드)

예: "부서에 소속된 멤버 수를 보여주는 API" 를 만든다고 가정.

1. **(필요하면) 리포지토리 메서드 추가** — `repository/DeptRepository.java` 같은 인터페이스에 Spring Data JPA 가 해석할 수 있는 메서드 시그니처 추가. 복잡하면 `@Query` 로 직접 JPQL 작성.
2. **서비스 레이어에 비즈니스 로직** — `service/DeptService.java` 에 public 메서드 추가. 트랜잭션이 필요하면 `@Transactional` 붙이기. 권한 체크(관리자인지, 같은 회사인지)도 여기서.
3. **DTO 정의** — 응답이 엔티티 전체를 내보내지 않도록 `dto/` 안에 record 로 응답 타입을 만듭니다. 비밀번호 같은 민감 필드 노출 방지 목적.
4. **컨트롤러에 엔드포인트 추가** — `controller/api/` 안의 적절한 클래스에 메서드 추가. 없으면 새 `@RestController` 를 만든 뒤 `@RequestMapping("/api/...")` 로 경로 지정.
   - 현재 로그인한 사용자가 필요하면 메서드 파라미터에 `Authentication auth` 를 받고 `auth.getName()` 으로 loginId 를 얻은 뒤 `MemberRepository.findByLoginId(...)` 로 엔티티 조회. (예시는 `AuthApiController.me` 참고.)
   - 요청 본문 검증은 `@Valid @RequestBody RequestDto dto` 로 지정하면 DTO 의 `@NotBlank`, `@Email` 등이 자동 체크.
   - 에러는 `throw new BusinessException(ErrorCode.XXX)` 로 던지면 `ApiExceptionHandler` 가 JSON 에러 응답으로 변환.
5. **권한 제어** — 관리자 전용이면 `SecurityConfig.securityFilterChain` 의 `authorizeHttpRequests` 블록에 `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 같은 규칙 추가. 기본값은 `anyRequest().authenticated()` 라 인증된 사용자면 모두 접근 가능.
6. **검증** — `./gradlew compileJava` 로 컴파일 확인. 다음 `./gradlew bootRun` 으로 띄워서 `curl` 이나 브라우저 개발자도구에서 호출해보기. 토큰이 필요한 경우 먼저 `POST /api/auth/login` 으로 토큰 발급받아 `Authorization: Bearer <token>` 헤더로 호출.

### 4-2. 새로운 페이지 추가 (프론트엔드)

예: `/teams` 라는 새 페이지를 만든다고 가정.

1. **파일 위치 결정** — 로그인이 필요한 내부 페이지면 `app/(dashboard)/teams/page.tsx`, 공개 페이지면 `app/teams/page.tsx`. `(dashboard)` 는 route group 이라 URL 에는 나타나지 않고, 그 안의 `layout.tsx` (인증 가드 + 사이드바 + 헤더) 가 자동 적용됨.
2. **페이지 컴포넌트 작성** — 기본 export 한 React 컴포넌트. 서버 컴포넌트가 기본값이고, 클릭 이벤트·useState 같은 브라우저 API 를 쓰려면 파일 맨 위에 `"use client";` 지시어를 붙여 클라이언트 컴포넌트로 전환.
3. **타입 정의** — 백엔드 응답 구조에 맞게 `lib/types.ts` 에 `interface` 추가. 이게 있어야 API 호출 결과가 타입 안전.
4. **API 호출** — `lib/api.ts` 의 `api.get/post/put/del` 사용. JWT 는 자동으로 헤더에 붙고, 401 오면 자동으로 auth-store 를 비웁니다.
   ```ts
   import { api } from "@/lib/api";
   import type { TeamSummary } from "@/lib/types";

   const teams = await api.get<TeamSummary[]>("/api/teams");
   ```
5. **사용자 정보 쓸 때** — `useAuthStore((s) => s.user)` 로 현재 로그인한 사용자 가져오기. 역할(`user.role`)에 따라 UI 분기 가능.
6. **스타일** — Tailwind v4 유틸리티 클래스 사용. 커스텀 색/폰트는 `globals.css` 의 `@theme inline` 블록에 추가.
7. **아이콘** — `import { X } from "lucide-react"` 식으로. 단, `lucide-react` v1 대는 일부 브랜드 아이콘(Facebook, Twitter 등)이 빠져있어서 필요하면 inline `<svg>` 로 직접 박는 편이 안전.
8. **사이드바에 노출** — `app/(dashboard)/sidebar.tsx` 상단의 `groups` 배열에 항목 추가. 단독 링크면 `href` 만, 하위 메뉴 그룹이면 `children` 배열로.
9. **검증** — `npm run dev` 로 띄워서 `http://localhost:3000/teams` 접속. 브라우저 콘솔과 터미널 로그를 같이 보면 됩니다. 타입 에러가 의심되면 `npx tsc -p tsconfig.json --noEmit` 으로 전체 타입체크.

### 4-3. 풀스택 기능 추가 플로우 (처음부터 끝까지)

"회원이 자기 프로필을 수정할 수 있는 기능" 을 새로 만든다고 가정하면, 보통 이 순서로 진행합니다.

1. **API 계약(contract) 설계** — 먼저 어떤 엔드포인트를 쓰고, 요청 바디와 응답 바디가 어떤 JSON 인지 머리로 그리기. (예: `PUT /api/auth/me` 로 `{ name, email, phone }` 보내면 `UserInfo` 돌려줌.)
2. **백엔드 먼저 구현** — 위 "4-1" 단계에 따라. `AuthApiController.me()` 옆에 `updateMe(@Valid @RequestBody UpdateProfileRequest req, Authentication auth)` 추가 → 서비스 메서드에서 엔티티 찾고 필드 업데이트 (JPA 의 dirty checking 이 알아서 UPDATE 쏨) → DTO 변환해서 리턴.
3. **curl 로 한 번 찍어서 검증** — 백엔드만 띄우고 로그인으로 토큰 얻은 뒤 `curl -X PUT -H "Authorization: Bearer ..." -H "Content-Type: application/json" -d '{...}' http://localhost:8080/api/auth/me`. 응답 구조가 의도한 대로인지 확인.
4. **프론트엔드 타입 추가** — `lib/types.ts` 에 `UpdateProfileRequest` 같은 타입 추가. 백엔드 DTO 와 필드 이름을 **정확히** 맞출 것 (JSON 직렬화는 필드명 그대로 감).
5. **페이지/컴포넌트 작성** — `app/(dashboard)/profile/page.tsx` 의 편집 모달에서 `api.put<UserInfo>("/api/auth/me", payload)` 호출, 성공하면 `useAuthStore.setSession(token, newUser)` 로 스토어 갱신.
6. **실패 경로 확인** — 일부러 틀린 값을 넣어서 400/401 이 잘 돌아오는지, UI 에 에러 메시지가 뜨는지 확인. `ApiError` 의 `message` 필드가 토스트나 inline 에러로 보여지면 OK.
7. **빌드 통과 확인** — 백엔드는 `./gradlew compileJava`, 프론트는 `npx tsc --noEmit`. 둘 다 깨끗하면 커밋.

### 4-4. 작업할 때 자주 밟는 함정들
- **포트 충돌** — 이전에 띄워둔 dev 서버가 남아있으면 Next.js 가 자동으로 3001, 3002 로 올라갑니다. `http://localhost:3000` 에서 이상한 동작이면 먼저 좀비 프로세스부터 확인.
- **CORS 에러** — 프론트가 백엔드에 못 붙고 브라우저 콘솔에 CORS 에러가 뜨면, `SecurityConfig.corsConfigurationSource()` 의 허용 origin 에 현재 프론트 주소가 들어있는지 확인. 환경변수 `CORS_ALLOWED_ORIGINS` 또는 `application-local.yml` 의 `app.cors.allowed-origins` 를 조정.
- **Wallet 경로 오류** — 백엔드가 DB 에 못 붙으면 `application-local.yml` 의 `TNS_ADMIN` 경로가 현재 시스템의 실제 Wallet 폴더를 가리키는지 확인. OS 가 바뀌면 (윈도우 → 맥) 경로 구분자도 바뀌어야 함.
- **JWT 유효기간 만료** — 기본 1시간. 만료되면 API 호출 시 401 이 떨어지고 `api.ts` 가 자동으로 스토어를 비워 `/login` 으로 튕김. 유효기간은 `JWT_EXPIRATION_MS` 환경변수로 조절.
- **dev 서버 재시작 없이 타입이 반영 안 됨** — `.next/` 디렉토리에 생성된 타입 파일이 stale 해지는 경우가 있습니다. `rm -rf frontend/.next` 후 재시작하면 해결.

---

## 5. 이전 프로젝트에서 바뀌면서 필요해진 설치 파일

### 5-1. 백엔드 의존성 변화 (`backend/build.gradle`)

**추가**

| 의존성 | 용도 | 왜 추가됐나 |
|---|---|---|
| `io.jsonwebtoken:jjwt-api:0.12.6` (+ `jjwt-impl`, `jjwt-jackson` 런타임) | JWT 토큰 발급·서명·검증 | 원래는 JSESSIONID 쿠키로 세션을 유지했는데, 다른 포트에서 도는 Next.js SPA 가 붙으려면 쿠키 대신 토큰 기반 stateless 인증이 필요. |
| `spring-boot-starter-validation` | `@Valid`, `@NotBlank`, `@Email` 등 요청 DTO 검증 | 원래 Mustache 폼은 브라우저 HTML5 `required` 속성으로 검증했지만, JSON API 는 서버에서 검증해야 안전하므로 Bean Validation 추가. |

**제거**

| 의존성 | 왜 제거됐나 |
|---|---|
| `spring-boot-starter-mustache` | Mustache 뷰 렌더링이 더 이상 없음. 프론트는 전부 Next.js 가 담당. |

그 외 (Oracle JDBC/Wallet, Spring Web/Security/JPA, Lombok, devtools) 는 그대로 유지.

### 5-2. 프론트엔드는 전면 신규

Mustache + webpack + Tailwind v3 + Alpine.js 조합이던 원래 프론트엔드를, 완전히 새로운 Next.js 스택으로 교체했습니다. 원래 `backend/` 안에 있던 webpack 파이프라인(`package.json`, `webpack.config.js`, `tailwind.config.js`, `postcss.config.js`, `src/main/js/`, `src/main/resources/static/`, `src/main/resources/templates/`)은 전부 제거됐고, 이제 프론트엔드는 `frontend/` 폴더가 유일합니다.

`frontend/package.json` 에 들어있는 주요 의존성:

| 패키지 | 용도 | 왜 필요한가 |
|---|---|---|
| `next` 16 | 프론트엔드 프레임워크 | SPA + 라우팅 + 빌드 + dev 서버 일체. 기존 webpack/Mustache 조합을 대체. |
| `react` 19, `react-dom` 19 | UI 라이브러리 | Next.js 의 기반. 컴포넌트 기반 UI 작성. |
| `typescript` 5, `@types/*` | 타입 체크 | 백엔드 DTO 와 프론트 타입을 맞춰서 런타임 오류 줄임. 자동완성 편의. |
| `tailwindcss` 4 + `@tailwindcss/postcss` | 유틸리티 CSS | 기존 프로젝트도 Tailwind 를 썼으나 v3. Next.js 스캐폴드 기본값이 v4 라서 같이 올림. 사용감은 비슷. |
| `eslint` + `eslint-config-next` | 린팅 | 흔한 실수 사전 방지. |
| `zustand` 5 | 클라이언트 전역 상태 저장 | JWT 토큰과 현재 사용자 정보를 localStorage 에 영속화하고 어느 컴포넌트에서나 읽기 위해. 기존 Mustache 는 서버가 세션으로 들고 있어서 필요 없었음. |
| `lucide-react` 1 | 아이콘 세트 | 원본 Mustache 는 인라인 SVG 로 아이콘을 그렸으나, React 컴포넌트 기반으로 다시 짜는 게 편해서 도입. 단, 이 버전엔 일부 브랜드 아이콘(Facebook 등)이 빠져있음. |
| `@fullcalendar/react`, `@fullcalendar/core`, `@fullcalendar/daygrid`, `@fullcalendar/timegrid`, `@fullcalendar/list`, `@fullcalendar/interaction` | 캘린더 컴포넌트 | 원본도 FullCalendar 를 썼는데 바닐라 JS API (`new Calendar(el, ...)`) 방식. React 래퍼가 별도 패키지라 React 쪽으로 전환하면서 추가. |

### 5-3. 인프라성 추가

| 파일 | 용도 | 왜 추가됐나 |
|---|---|---|
| `backend/Dockerfile` | 멀티스테이지 Docker 이미지 빌드 (JDK 21 빌드 → JRE 21 런타임) | 운영 환경에 Spring Boot 를 컨테이너로 배포할 수 있도록. 선택 사항. |
| `backend/.dockerignore` | 컨테이너 빌드 컨텍스트에서 `.gradle/`, `Wallet`, `application-local.yml` 등 제외 | 이미지 용량 축소 + 자격증명 유출 방지. |
| `docker-compose.yml` (루트) | 백엔드 서비스 + Wallet 볼륨 마운트 정의 | `docker compose up` 한 번으로 띄우기 위함. |
| `.env.example` (루트, 프론트 양쪽) | 환경변수 템플릿 | 팀원이 클론한 뒤 뭘 채워야 하는지 보여주는 참고 파일. |
| `application.yml` / `application-prod.yml` / `application-dev.yml.example` | 프로파일별 Spring 설정 분리 | 원본은 `application.properties` 하나에 DB 자격증명까지 박혀 있었음. 환경변수 기반 + git-ignored 로컬 파일 구조로 정리. |

### 5-4. 삭제된 레거시 코드 요약

백엔드가 순수 API 서버가 되면서 다음이 모두 제거됐습니다.

- **Mustache 뷰 계열**: `backend/src/main/resources/templates/` 전체, view 컨트롤러 5개 (`MainController`, `MemberController`, `AdminController`, `DeptController`, `PositionController`).
- **세션 기반 폼 로그인 계열**: `LoginSuccessHandler`, `LoginFailureHandler`, `CsrfControllerAdvice`, `MemberSession`, 그리고 `SecurityConfig` 에 있던 두 번째 filter chain.
- **레거시 webpack 파이프라인**: `backend/package.json`, `webpack.config.js`, `tailwind.config.js`, `postcss.config.js`, `.browserslistrc`, `.prettierrc`, `src/main/js/`, `src/main/resources/static/`.
- **Gradle 의존성**: `spring-boot-starter-mustache`.
- **API 중 쓰이지 않게 된 메서드**: `MemberApiController.verifyCompany` (세션에 쓰던 버전). Next.js 는 `AuthApiController` 의 stateless 버전 `/api/auth/company/verify` 를 씀.

---

## 6. 작업할 때 설정해야 할 환경변수 요약

### `backend/src/main/resources/application-local.yml` (로컬 개발)
```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@intranetdb_high?TNS_ADMIN=<Wallet 폴더 절대경로>
    username: <DB 사용자>
    password: <DB 비밀번호>
    driver-class-name: oracle.jdbc.OracleDriver
app:
  jwt:
    secret: <64자 이상 랜덤 문자열>
    expiration-ms: 3600000
  cors:
    allowed-origins: http://localhost:3000
```

### 루트 `.env` (Docker 실행 시)
```
SPRING_PROFILES_ACTIVE=prod
DB_TNS_NAME=intranetdb_high
DB_USERNAME=...
DB_PASSWORD=...
WALLET_PATH=./backend/src/main/resources/Wallet_IntranetDB
JWT_SECRET=...
JWT_EXPIRATION_MS=3600000
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

### `frontend/.env.local`
```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

---

## 7. 문서에 나온 용어 풀이

| 용어 | 뜻 |
|---|---|
| **모노레포 (Monorepo)** | 하나의 Git 저장소 안에 여러 독립 프로젝트(이 경우 backend + frontend)를 같이 담는 구조. 버전 통합 관리와 원자적 커밋이 쉬워지는 대신 각 프로젝트의 도구를 따로 돌려야 함. |
| **SPA (Single Page Application)** | 한 번 페이지를 받은 뒤 이후 화면 전환을 자바스크립트로 처리하는 구조. 전통적인 서버 렌더링(Mustache)과 반대 개념. |
| **SSR (Server-Side Rendering)** | 서버에서 HTML 을 완성해 보내주는 방식. Next.js 는 SSR + SPA 를 같이 할 수 있음. |
| **App Router** | Next.js 13 이후 도입된 새로운 라우팅 방식. `app/` 디렉토리 구조를 URL 에 자동 매핑. |
| **Route Group (라우트 그룹)** | 폴더 이름을 괄호로 감싼 것 (`(dashboard)`). URL 에는 안 나타나지만 그 안의 페이지들이 공통 `layout.tsx` 를 공유. 이 프로젝트에선 인증 가드 + 사이드바/헤더를 한 번에 거는 용도. |
| **Client Component / Server Component** | Next.js App Router 기준. 서버에서만 실행되는 컴포넌트(Server)가 기본값. 브라우저 이벤트·훅을 쓰려면 파일 상단에 `"use client"` 를 붙여 Client 로 전환. |
| **Hydration** | 서버에서 그린 HTML 에 브라우저 JS 가 붙어 인터랙티브해지는 과정. 이 사이에 localStorage 등 브라우저 전용 API 를 건드리면 불일치 에러가 나므로, 이 프로젝트는 `hydrated` 플래그를 둬서 완료 전에는 임시 UI 를 보여줌. |
| **JWT (JSON Web Token)** | 사용자 정보를 담고 서명된 토큰. 서버는 검증만 하면 되고 따로 세션을 저장하지 않음. 포맷은 `<header>.<payload>.<signature>` 세 조각. |
| **Bearer Token** | HTTP 요청의 `Authorization` 헤더에 `Bearer <토큰>` 형식으로 실어 보내는 인증 방식. "이 토큰을 가진 사람이 곧 주인" 이라는 의미. |
| **Stateless (상태 없음)** | 서버가 이전 요청의 상태를 기억하지 않는 설계. 토큰마다 자기 자신이 누군지 담고 있어서 서버는 매 요청마다 독립적으로 검증만 함. 확장성은 좋지만 토큰 무효화가 어려움. |
| **CORS (Cross-Origin Resource Sharing)** | 다른 origin(도메인+포트 조합) 간의 브라우저 요청을 제어하는 메커니즘. `localhost:3000` 프론트가 `localhost:8080` 백엔드를 호출하려면 백엔드가 "허용한다" 는 헤더를 보내야 함. |
| **CSRF (Cross-Site Request Forgery)** | 악성 사이트가 로그인된 사용자 브라우저를 이용해 몰래 요청을 날리는 공격. 세션 쿠키 기반 UI 에 CSRF 토큰이 필요한 이유. JWT 를 헤더로 보내는 API 는 CSRF 방어가 불필요해서 비활성화함. |
| **Session / JSESSIONID** | 서버가 사용자별로 상태를 저장하던 기존 방식. 이 프로젝트는 JWT 로 전환하면서 제거. 역사적 참고용. |
| **localStorage** | 브라우저가 영구 저장하는 키-값 스토리지. 탭을 닫아도 남음. XSS 에 노출되면 탈취 위험. |
| **sessionStorage** | localStorage 와 비슷하지만 탭을 닫으면 사라짐. 기업 코드 인증 결과를 임시로 넘길 때 사용 중. |
| **DTO (Data Transfer Object)** | 엔티티를 그대로 반환하지 않고 노출할 필드만 추린 전송용 객체. 비밀번호 같은 민감 필드를 숨기고 API 응답 스키마를 고정하기 위해 사용. |
| **ORM / JPA / Hibernate** | ORM 은 객체 ↔ 관계형 DB 사이의 매핑 기술. JPA 는 자바 표준 스펙이고 Hibernate 는 그 구현체. 이 프로젝트는 Spring Data JPA 를 써서 `@Entity` 클래스를 테이블에 자동 매핑. |
| **Dirty Checking** | JPA 의 핵심 기능. 트랜잭션 안에서 엔티티 필드를 바꾸면 커밋 시점에 자동으로 UPDATE SQL 이 실행됨. 직접 `save()` 를 부르지 않아도 됨. |
| **Spring Profile** | 환경별로 다른 설정을 활성화하는 메커니즘. `SPRING_PROFILES_ACTIVE=prod` 같이 지정하면 `application-prod.yml` 이 `application.yml` 위에 덮어씌워짐. |
| **Bean / Dependency Injection** | Spring 이 관리하는 객체 단위. 클래스에 `@Component`, `@Service`, `@Controller`, `@Configuration` 등을 달면 Spring 이 인스턴스를 만들어서 필요한 곳에 자동으로 꽂아줌(주입). |
| **`@RestController` vs `@Controller`** | `@Controller` 는 메서드 리턴 값을 템플릿 이름으로 보고 뷰를 렌더. `@RestController` 는 리턴 값을 JSON 으로 직렬화해서 응답 바디에 씀. 이 프로젝트는 `@RestController` 만 사용. |
| **Security Filter Chain** | Spring Security 의 요청 가로채기 파이프라인. 이 프로젝트는 JWT 기반 단일 체인만 운영. |
| **Oracle Wallet** | Oracle Autonomous DB 에 연결하기 위한 자격증명·인증서 묶음 폴더. 안에 `cwallet.sso`, `tnsnames.ora` 같은 파일이 들어있고 JDBC 드라이버가 이걸 읽어 mTLS 연결을 엶. |
| **TNS / TNS_ADMIN** | Oracle 의 네트워크 서비스 이름(Transparent Network Substrate) 식별자 체계. `TNS_ADMIN` 환경변수가 Wallet 폴더를 가리키면 그 안의 `tnsnames.ora` 에서 호스트·포트를 찾아냄. |
| **Multi-stage Build (Docker)** | Dockerfile 을 여러 스테이지로 나눠서 빌드용 JDK 이미지에서 jar 를 만든 뒤 가벼운 JRE 이미지에 복사하는 패턴. 최종 이미지 용량 감소. |
| **Volume Mount** | 호스트 OS 의 폴더를 컨테이너 안의 특정 경로에 연결하는 기능. Wallet 폴더처럼 이미지에 포함시키기 싫은 자료를 넘길 때 사용. |
| **Gradle wrapper (`gradlew`)** | 프로젝트에 포함된 스크립트로, 적절한 Gradle 버전을 자동으로 내려받아 빌드를 실행. 사용자가 Gradle 을 따로 설치할 필요 없음. |
| **`bootJar` / fat jar** | Spring Boot 애플리케이션을 의존성과 함께 하나의 jar 로 말아내는 Gradle 태스크. `java -jar app.jar` 로 바로 실행 가능. |
| **Zustand** | 가벼운 React 전역 상태 라이브러리. Redux 보다 보일러플레이트가 적고, 이 프로젝트처럼 "토큰/사용자 정보 한두 개 들고다니기" 용도에 적합. |
| **Turbopack** | Next.js 의 새 번들러(Webpack 대체). dev 서버가 빨라지는 게 주된 이점. 이 프로젝트 Next.js 16 은 기본으로 Turbopack 을 dev 에 사용. |
| **Middleware → Proxy (Next.js 16)** | Next.js 15 까지는 루트의 `middleware.ts` 파일로 요청을 가로채는 코드를 작성했는데, 16 부터 이름이 `proxy.ts` 로 바뀜. 이 프로젝트는 클라이언트 사이드 가드를 쓰기 때문에 사용하지 않음. |
| **Mustache** | 로직 없는 템플릿 엔진. `{{변수}}`, `{{>partial}}` 같은 문법으로 서버 렌더링 HTML 을 만듦. 이 프로젝트의 **이전** UI 가 Mustache 기반이었고, 현재는 전부 제거됨. |
| **Alpine.js** | 작은 크기의 선언형 자바스크립트 라이브러리. `x-data`, `x-show` 같은 디렉티브로 가벼운 인터랙션을 만들 수 있음. Mustache 템플릿 안에서 모달 열고 닫기 등에 사용됐었고, Next.js 전환과 함께 제거됨. |
