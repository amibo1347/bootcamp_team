# 프로젝트 종합 점검 보고서

작성일 기준으로 src/ 전체를 둘러보면서 발견한 보안 위험, 잠재 에러, 리팩토링 기회를 정리했습니다. 각 항목의 위치는 파일:라인 형식으로, 위험도는 **상/중/하** 로 표기했습니다.

> 일부 항목은 대화 중에 일부 수정되었을 수 있습니다. 적용 전 현재 코드 상태를 다시 확인해 주세요.

---

## 1. 보안 위험 (Security)

### 1.1 DB 자격증명 평문 노출 — **상**
- **위치**: `src/main/resources/application.properties`
- **문제**: 운영 DB의 비밀번호/접속 정보가 그대로 평문으로 들어가 있어 깃에 커밋되면 그대로 노출됩니다.
- **제안**: `${DB_PASSWORD}` 형태로 환경변수 참조, 또는 Spring Cloud Config / Vault / `.env` 분리. `application-local.properties`만 깃 무시(.gitignore) 처리.

### 1.2 `/api/**` 전 구간 CSRF 비활성화 — **상**
- **위치**: `src/main/java/com/team/intranet/config/SecurityConfig.java` (csrf ignoring matchers 부분)
- **문제**: `/api/**`, `/company/**` 의 모든 POST가 CSRF 토큰 검증을 거치지 않습니다. 세션 인증이 살아 있는 상태에서 외부 사이트가 사용자 권한으로 상태 변경 요청을 보낼 수 있습니다.
- **제안**: 상태 변경 API(POST/PUT/DELETE)에는 CSRF 토큰을 강제. 폼/AJAX 모두 `_csrf` 메타 태그 → `X-CSRF-TOKEN` 헤더로 보내는 패턴은 이미 일부 JS에서 쓰고 있으므로 어렵지 않습니다.

### 1.3 `dept.js`에서 CSRF 토큰 헤더 누락 — **상**
- **위치**: `src/main/resources/static/js/admin/dept.js:13-17`
- **문제**: `createDept` fetch 요청에 CSRF 토큰을 헤더에 싣지 않습니다. 1.2의 csrf ignore 덕분에 동작은 하지만, 보호를 켜는 순간 모두 깨집니다.
- **제안**: `memberList.js`처럼 `meta[name="_csrf"]` / `meta[name="_csrf_header"]`를 읽어 헤더에 추가.

### 1.4 프로필 이미지 조회의 인증 누락 — **중**
- **위치**: `src/main/java/com/team/intranet/controller/api/MemberApiController.java:77-89`
- **문제**: `/api/member/{id}/profileImg` 가 어떤 인증/회사 검사도 없이 임의의 ID로 호출 가능. 다른 회사 직원의 프로필 사진을 통째로 열람 가능합니다.
- **제안**: 세션의 `MemberSession`을 받아 본인이거나 같은 회사 소속만 통과시키도록 검증. `@PreAuthorize` 또는 컨트롤러 진입 시 검사.

### 1.5 회원가입 시 부서/직급의 회사 소속 검증 누락 — **상**
- **위치**: `MemberService.join()`, `MemberService.acceptMember(...)`
- **문제**: 회원가입(또는 승인) 시 사용자가 폼으로 보낸 `deptId`/`positionId`가 자기 회사의 것인지 검증하지 않습니다. 다른 회사의 부서/직급 ID를 알면 그쪽으로 묶인 회원을 만들 수 있습니다. (`updateMemberInfo`는 검증함)
- **제안**: `dept.getCompany().getCompanyId().equals(targetCompanyId)` 검증을 회원 생성/승인 경로 모두에 추가.

### 1.6 로그인 실패 메시지가 계정 상태 노출 — **하**
- **위치**: `src/main/java/com/team/intranet/config/LoginFailureHandler.java`
- **문제**: "승인 대기 중", "반려됨", "탈퇴됨" 등 상태별로 다른 메시지를 노출 → 계정 열거(enumeration) 공격 가능.
- **제안**: 모든 실패를 "아이디 또는 비밀번호가 올바르지 않습니다"로 통일. 상세 사유는 서버 로그에만 기록.

### 1.7 회사코드 검증 시 정보 누설 — **하**
- **위치**: `MemberService.join()` 의 `NOT_COMPANY` 응답
- **문제**: 잘못된 `companyCode` 입력 시 "기업이 존재하지 않음"이 회신되어, 유효한 회사 코드 추측이 쉽습니다.
- **제안**: 가입 단계의 모든 검증 실패를 일반 메시지로 통일.

### 1.8 프로필 이미지 업로드 검증 부재 — **중**
- **위치**: `AdminApiController.updateMember(...)` (profileImg 처리부)
- **문제**: MIME 타입/확장자/크기 검증 없이 `byte[]`로 그대로 저장. 악성 파일/거대 파일/SVG XSS 등 위험.
- **제안**: 화이트리스트(`image/jpeg`, `image/png`, `image/webp`)와 파일 크기 상한(예: 5MB) 검증. 가능하면 ImageIO로 이미지 디코딩 검증까지.

### 1.9 권한 체크 패턴 비일관 — **중**
- **위치**: `AdminApiController` 안의 메서드들
- **문제**: 어떤 메서드는 `@SessionAttribute("memberSession") MemberSession ms`로, 어떤 메서드는 `HttpSession`에서 직접 꺼내고, `Role.ADMIN`만 검사하지만 enum에는 `MASTER`, `SUB_ADMIN` 등이 정의되어 있어 우회 가능성/혼란.
- **제안**: `@PreAuthorize("hasRole('ADMIN')")` 또는 공통 인터셉터로 일원화. Role 매트릭스 문서화.

### 1.10 `innerHTML`로 서버 HTML 삽입 (XSS 잠재) — **중**
- **위치**: `static/js/admin/dept.js:23`, `static/js/subAdmin/memberList.js:176`
- **문제**: 서버가 내려주는 HTML 청크를 그대로 `innerHTML`에 주입. 서버 측 escape가 어디 한 군데라도 빠지면 XSS로 직결.
- **제안**: 단기 - Thymeleaf 출력은 `th:text`(escape)만 사용하고 `th:utext` 금지. 장기 - JSON 응답 + 클라이언트 템플릿으로 전환.

### 1.11 동적 데이터의 escape 미보장 — **중**
- **위치**: 템플릿 전반
- **문제**: 회원 이름, 부서명 등 사용자 입력이 들어가는 자리에 `th:text`가 일관되게 적용되어 있는지 점검 필요. 특히 `th:utext`가 쓰인 곳이 있다면 위험.
- **제안**: 전 템플릿에서 `th:utext` 검색 → 정당한 사용처 외엔 `th:text`로 교체.

### 1.12 회사별 데이터 격리가 서비스 메서드 별로 산재 — **상**
- **위치**: 모든 서비스
- **문제**: `companyId` 필터/검증을 메서드마다 수동으로 챙김. 신규 메서드 추가 시 빠뜨릴 가능성 큼.
- **제안**: 도메인 레벨에서 멀티테넌시(예: Hibernate `@Filter`, JPA `EntityListener`, 인터셉터로 `companyId` 자동 필터)로 강제.

### 1.13 OPEN REDIRECT 위험 (낮음이지만 점검) — **하**
- **위치**: `Login` 관련 핸들러
- **문제**: 리다이렉트 대상 URL을 외부에서 받는 경우 점검.
- **제안**: 항상 화이트리스트된 path만 허용.

---

## 2. 잠재적 에러 (Bugs)

### 2.1 `MemberRepository.findProfileImgById` 쿼리 필드명 오류 — **상**
- **위치**: `src/main/java/com/team/intranet/repository/MemberRepository.java`
- **문제**: 쿼리에서 `m.id`로 작성된 곳이 있는데 엔티티 필드명은 `memberId`. 호출 시 `QuerySyntaxException`(런타임).
- **제안**: `WHERE m.memberId = :id`로 수정. (현재 사용 여부도 점검 — 안 쓰면 삭제)

### 2.2 `AdminApiController.updateMember`가 `parseBirthDay()`를 호출하지 않음 — **상**
- **위치**: `AdminApiController.java:92`
- **문제**: 헬퍼 `parseBirthDay`를 만들어 두고도 직접 `LocalDateTime.parse(birthDay)` 호출 → "yyyy-MM-dd" / "yyyyMMdd" 모두 파싱 실패.
- **제안**: `LocalDateTime.parse(birthDay)` → `parseBirthDay(birthDay)`로 교체.

### 2.3 `Member.createPendingMember`에 `birthDay`/`dept` 미설정 — **상**
- **위치**: `Member.java:95-108`
- **문제**: dto 값에서 `birthDay`(`getFullBirthDate()`)와 `dept`를 entity에 복사하지 않음. 가입 신청 시 NULL로 INSERT 되어, 승인 후에도 그대로 NULL.
- **제안**:
  ```java
  member.birthDay = dto.getFullBirthDate();
  member.dept = dept; // dto에서 변환된 부서 객체
  ```

### 2.4 `MemberDto`에 `deptId` 필드 부재 — **상**
- **위치**: `MemberDto.java`
- **문제**: signup 폼이 `deptId`(Long) hidden input으로 전송하는데 DTO엔 `Dept dept` 필드만 있어 바인딩 실패. dto.dept는 항상 null.
- **제안**: `private Long deptId;` 추가하고 서비스에서 `deptRepository.findById(dto.getDeptId())`로 조회.

### 2.5 `DeptDto.toEntity()` 타입 불일치 — **상 (컴파일 에러 위험)**
- **위치**: `DeptDto.java:18-20`
- **문제**: `new Dept(null, deptName, deptCode, companyId)`에서 `Dept`의 마지막 인자는 `Company`인데 `Long`을 넘김. 컴파일 에러.
- **제안**: 메서드 삭제 또는 `toEntity(Company company)`로 시그니처 변경.

### 2.6 `DeptApiController.createDept`의 `MemberSession`에 `@SessionAttribute` 누락 — **상**
- **위치**: `DeptApiController.java:27`
- **문제**: 어노테이션 없이 받으면 빈 객체가 만들어져 `getRole()`이 null → `validateAdmin`에서 항상 실패 → 500.
- **제안**: `@SessionAttribute(name = "memberSession", required = false) MemberSession ms` 추가.

### 2.7 `DeptApiController` fragment 경로 잘못됨 — **상**
- **위치**: `DeptApiController.java:42, 55, 64`
- **문제**: `subAdmin/memberList :: #deptListContainer`를 가리키지만 `#deptListContainer`는 `admin/managingDept.html`에 있음. fragment selector 매칭 실패.
- **제안**: `admin/managingDept :: #deptListContainer`로 교정.

### 2.8 `DeptApiController` 모델 키 불일치 — **상**
- **위치**: `DeptApiController.java:40` / `managingDept.html:70`
- **문제**: 컨트롤러는 `deptList`로 추가, 템플릿은 `${departments}`를 읽음 → 빈 목록.
- **제안**: `model.addAttribute("departments", deptList)` 로 통일.

### 2.9 `MemberApiController.getProfileImg` NPE 가능 — **중**
- **위치**: `MemberApiController.java:80-88`
- **문제**: `memberService.getProfileImg(id)` 가 `null`인 경우 `profileImg.length` 호출에서 NPE — 다만 현재 `if (profileImg != null && profileImg.length > 0)` 로 막혀 있어 OK. 향후 수정 시 순서 바뀌면 위험.
- **제안**: 그대로 유지하되, 단축평가 순서 유지에 주의 표기.

### 2.10 `MemberRepository.searchMembers`에 `profileImg`(byte[]) = String 비교 — **상**
- **위치**: `MemberRepository.java`
- **문제**: LOB 타입 `byte[]` 컬럼을 String 파라미터로 `=` 비교. 일부 다이얼렉트는 파싱 단계에서 거부, Oracle은 ORA-00932. (이 항목은 이미 인지/제거되었을 수 있음 — 현재 상태 확인 필요)
- **제안**: 해당 조건 자체를 제거. 프로필 이미지 유무로 필터링이 정말 필요하면 `m.profileImg IS NULL` 또는 별도 boolean 컬럼.

### 2.11 `MemberService.rejectMember` / `fireMember`의 `orElseThrow()` 메시지 미지정 — **하**
- **위치**: `MemberService.java:197, 211`
- **문제**: 인자 없는 `orElseThrow()`는 기본 `NoSuchElementException`만 던져 디버깅 어려움.
- **제안**: `orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND))` 로 통일.

### 2.12 `LocalDateTime` 시간 부분 의미 없음 — **중**
- **위치**: `Member.birthDay`, `MemberDto.birthDay`
- **문제**: 생년월일에 시·분·초 정보가 항상 00:00:00 → 의미 없는 정보를 들고다님. 시간대 이슈도 잠재.
- **제안**: 필드를 `LocalDate`로 교체. 화면 표시는 변동 없음.

### 2.13 `AdminController.memberList` 트랜잭션/페치 부재로 인한 LIE 위험 — **중**
- **위치**: `controller/view/AdminController.java`
- **문제**: 컨트롤러가 `@Transactional`을 갖지 않은 상태에서 `Member`의 lazy 연관(`dept`, `position`)을 템플릿에서 접근. open-in-view 끄면 `LazyInitializationException`.
- **제안**: 서비스 메서드를 `@Transactional(readOnly = true)`로 묶고 `JOIN FETCH` 또는 `@EntityGraph`로 미리 로딩.

### 2.14 부서 코드 자동 생성의 동시성 결함 — **중**
- **위치**: `DeptService.createDept` (deptCode max+10 로직)
- **문제**: `MAX(deptCode)`를 SELECT → 계산 → INSERT 사이에 다른 트랜잭션이 끼어들면 동일 코드 중복 생성 가능.
- **제안**: `tbl_dept(company_id, dept_code)`에 UNIQUE 제약 + 충돌 시 재시도, 또는 시퀀스/쿼리 한 방으로(`INSERT ... SELECT MAX+10`) 처리.

### 2.15 부서 삭제 시 회원 dept_id 처리 정책 부재 — **중**
- **위치**: `DeptService.deleteDept`
- **문제**: 그 부서를 참조 중인 회원이 있을 때 처리 미정의. FK 제약에 따라 실패 또는 NULL로 떨어짐.
- **제안**: 사전에 사용 중인 회원 수 검사 → 있으면 거부 또는 "미지정" 부서로 이전. `@OnDelete` 정책 명시.

### 2.16 `LoginSuccessHandler`의 `orElseThrow()` 메시지 미지정 — **하**
- **위치**: `LoginSuccessHandler.java`
- **문제**: 사용자 조회 실패 시 무의미한 스택트레이스.
- **제안**: 명시적 예외(예: `UsernameNotFoundException`).

### 2.17 `IOException` 미처리(전파만) — **중**
- **위치**: `AdminApiController.updateMember(... throws IOException)`
- **문제**: 컨트롤러에서 throws만 하고 글로벌 예외 처리기가 없으면 사용자에게 500과 함께 스택트레이스 노출.
- **제안**: `@ControllerAdvice` + `@ExceptionHandler(IOException.class)` 또는 try-catch.

### 2.18 `Member.profileImg` 항상 즉시 로드되는 경로 — **중**
- **위치**: 회원 목록을 fetch하는 모든 경로
- **문제**: `@Lob @Basic(fetch = LAZY)`로 두었지만, JPA의 LAZY는 byte[]/CLOB에서 항상 동작하지 않음(프로바이더/바이트코드 인스트루멘트 의존). 결과적으로 항상 LOB을 끌어와 메모리 낭비.
- **제안**: 프로필 이미지를 별도 엔티티 `MemberProfileImage(memberId, bytes)`로 분리하거나, 별도 테이블/외부 스토리지에 저장 후 path만 보관.

---

## 3. 리팩토링 기회 (Refactoring)

### 3.1 엔티티 직접 노출 (DTO 부재)
- **위치**: 다수의 컨트롤러
- **문제**: `Member`, `Dept`, `Position`을 모델에 그대로 담아 템플릿/응답에 노출. 필드 추가/변경의 파급 큼.
- **제안**: `MemberResponse`, `DeptResponse` 같은 응답 DTO를 두고 매핑.

### 3.2 응답 형태 비일관 (HTML fragment vs JSON vs redirect)
- **위치**: 여러 API 컨트롤러
- **문제**: 같은 도메인의 변경 API들이 어떤 건 `redirect:`, 어떤 건 fragment, 어떤 건 JSON. 클라이언트가 케이스마다 분기.
- **제안**: API는 JSON `ResponseEntity` 통일. 페이지 갱신은 별도 GET endpoint로 분리.

### 3.3 `AdminApiController`의 `HttpSession` vs `@SessionAttribute` 혼용
- **위치**: `AdminApiController` 전체
- **문제**: 같은 컨트롤러 안에서 두 방식이 섞여 있어 가독성/일관성↓.
- **제안**: 한 가지 방식으로 통일. 어노테이션 권장.

### 3.4 권한 검증 코드 중복
- **위치**: 모든 `*ApiController`
- **문제**: `if (ms == null || ms.getRole() != Role.ADMIN) return "redirect:/member/login"` 패턴이 반복.
- **제안**: `@PreAuthorize` 사용 또는 `HandlerInterceptor` 또는 커스텀 어노테이션 `@RequireAdmin`.

### 3.5 매직 문자열/숫자
- **위치**: `DeptService` (코드 10, "%04d"), `MemberService` ("desc"/"asc"), 템플릿의 날짜 포맷
- **문제**: 코드 곳곳에 같은 문자열이 흩어져 있음.
- **제안**: `Constants`/`AppDateFormats`/Enum (`SortDirection`)로 추출.

### 3.6 `System.out.println`/`System.err.println` 사용
- **위치**: `MemberService.acceptMember`, `DeptService.createDept`
- **문제**: 표준 출력으로 로그 → 환경별 라우팅/레벨 조정 불가.
- **제안**: `Slf4j` `private static final Logger log = ...`. (`@Slf4j` Lombok)

### 3.7 회원 상태 변경 메서드의 보일러플레이트
- **위치**: `MemberService.acceptMember/rejectMember/fireMember`
- **문제**: "관리자 조회 → 권한 검증 → 대상 조회 → 회사 검증 → 상태 변경" 패턴 반복.
- **제안**: 공통 `applyMemberStateChange(Long memberId, Long adminId, Consumer<Member> mutator)` 추상화.

### 3.8 이름 컨벤션 위반
- **위치**: `MemberDto.is_Password_Match()`
- **문제**: snake_case + camelCase 혼용.
- **제안**: `isPasswordMatch()` 또는 `passwordMatches()`.

### 3.9 `@Transactional` 위치 일관성
- **위치**: `MemberService`, `DeptService`
- **문제**: 일부 메서드만 `@Transactional`. 조회는 보통 빠져 있음 → LIE 위험(2.13 참조).
- **제안**: 클래스에 `@Transactional(readOnly = true)` 적용 후, 변경 메서드만 `@Transactional`로 오버라이드.

### 3.10 검증 로직 분산
- **위치**: `signup.js` 와 `MemberService.join()`
- **문제**: 동일한 정규식/규칙이 양쪽에 중복.
- **제안**: Bean Validation(`@Valid`, `@Pattern`, `@Email`, `@Size`) 도입. JS는 UX용 보조 검증으로 한정.

### 3.11 글로벌 예외 처리기 부재
- **위치**: 프로젝트 전체
- **문제**: `BusinessException` 등이 컨트롤러에서 그대로 터지면 500 스택트레이스가 사용자에게 노출.
- **제안**: `@ControllerAdvice` + `@ExceptionHandler` 로 일관된 에러 응답(JSON/페이지) 제공.

### 3.12 동적 쿼리 복잡도
- **위치**: `MemberRepository.searchMembers`
- **문제**: 옵셔널 파라미터 다수가 JPQL의 `:x IS NULL OR ...` 패턴으로 장황.
- **제안**: QueryDSL 또는 JPA Specification 도입.

### 3.13 정렬 로직이 메모리 단계
- **위치**: `MemberService.findFilteredMembers`
- **문제**: DB에서 가져와 자바에서 `Comparator`로 정렬. 데이터 커지면 비효율.
- **제안**: JPQL `ORDER BY` 또는 Specification에 정렬 위임. NULLS LAST 명시.

### 3.14 `Member`의 `@Setter` 전체 노출
- **위치**: `Member.java`
- **문제**: `@Setter`가 클래스 단위라 모든 필드 setter 공개 → 도메인 무결성 위협.
- **제안**: setter 제거 또는 필드 단위로 제한. 변경은 `accept()`/`updateInfo()` 같은 의도된 메서드로만.

### 3.15 회원가입 폼 partial 검증
- **위치**: `signup.html` 부서 선택 optional vs 서비스 처리 정책
- **문제**: 화면은 선택 안 해도 진행 가능, 백엔드 처리 정책 불명확.
- **제안**: 정책 결정(필수/선택) 후 양쪽 일관되게.

### 3.16 CSS 프레임워크 혼용
- **위치**: 모든 템플릿
- **문제**: Tailwind CDN + 별도 `output.css` 동시 로드. Tailwind CDN은 운영 환경에서 사용 비권장.
- **제안**: PostCSS 빌드로 정적 CSS 생성, CDN 제거.

### 3.17 감사 로그/이력 부재
- **위치**: 회원 상태 변경 / 부서·직급 변경
- **문제**: 누가 언제 어떤 변경을 했는지 추적 불가. 인사 데이터엔 필수.
- **제안**: `AuditLog(actorId, action, entityType, entityId, before, after, at)` 엔티티 + `EntityListener`.

### 3.18 명시적 에러 코드/메시지 분리 부족
- **위치**: `enums/ErrorCode.java`
- **문제**: 메시지/HTTP 상태/사용자 메시지가 같이 묶여 있는지 점검.
- **제안**: 코드(시스템용) ↔ 메시지(사용자용) ↔ HTTP status를 분리.

### 3.19 프로필 이미지 기본값 처리 분산
- **위치**: `MemberApiController.getProfileImg`(404), `memberList.js`(폴백 처리)
- **문제**: 기본 이미지 처리가 클라/서버에 흩어져 있음.
- **제안**: 서버에서 기본 아바타 이미지로 200 응답하면 콘솔 에러도 사라지고 클라이언트 분기도 단순화.

### 3.20 동일 회사 검증 로직 중앙화
- **위치**: 여러 서비스
- **문제**: `getCompany().getCompanyId().equals(...)` 패턴이 곳곳에 하드코딩.
- **제안**: `SecurityValidator.assertSameCompany(target, actor)` 정도의 헬퍼로 중앙화.

---

## 4. 우선순위 정리

다음 순서로 처리하면 위험과 영향이 큰 것부터 정리됩니다.

### 즉시 수정 (Hot)
1. (2.5) `DeptDto.toEntity()` 컴파일 에러 — 빌드부터 통과시켜야 다른 작업 가능
2. (2.1) `MemberRepository.findProfileImgById` 쿼리 필드명 오류
3. (2.2) `parseBirthDay()` 미호출
4. (2.3) `createPendingMember`의 birthDay/dept 누락 + (2.4) MemberDto의 deptId
5. (2.6~2.8) DeptApiController의 세 가지 결함
6. (1.1) DB 평문 자격증명을 환경변수로 이동

### 단기 (1-2일)
7. (1.4) 프로필 이미지 인증
8. (1.5) 부서/직급 회사 소속 검증
9. (1.2 / 1.3) CSRF 일관 적용
10. (3.11) 글로벌 예외 처리기 도입
11. (2.13) `@Transactional`/JOIN FETCH로 LIE 차단

### 중기 (1주)
12. (3.1) 응답 DTO 도입 + (3.2) 응답 형식 통일
13. (3.4) `@PreAuthorize`로 권한 일원화
14. (3.6) Slf4j 로거 도입
15. (2.12) `LocalDateTime` → `LocalDate`
16. (1.8) 파일 업로드 검증

### 장기
17. (2.18 / 3.16) 프로필 이미지 별도 엔티티/스토리지 + Tailwind 빌드 정식화
18. (3.17) 감사 로그
19. (3.12) QueryDSL/Specification 도입

---

## 부록: 점검 체크리스트

다음 항목을 한 번씩 grep해서 일치 여부를 확인하면 좋습니다.

- [ ] `th:utext` 등장 위치 모두 검토 — 사용자 입력에 적용된 곳 없는지
- [ ] `System.out`/`System.err` 검색 — 모두 logger로 교체
- [ ] `orElseThrow()` 빈 인자 호출부 — 메시지/예외 명시
- [ ] `redirect:` 반환하는 API — 의도가 페이지 이동인지, JSON 응답이어야 하는지
- [ ] `@Transactional` 누락된 변경 메서드
- [ ] 컨트롤러에서 `Long companyId = ms.getCompanyId()` 후 검증 누락 패스
- [ ] `meta[name="_csrf"]` 안 읽고 fetch하는 JS
- [ ] `byte[]` 또는 LOB 컬럼이 쿼리 조건/조인에 들어가는 곳
