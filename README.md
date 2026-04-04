## 프로젝트 Title : 테마형 인트라넷 메이커(?)

## 시작하기 
프로젝트를 로컬 환경에서 실행하려면 아래 단계를 따르세요.

   1. 필수 조건
   Node.js (버전 18.x 이상 권장)

   npm (Node.js 설치 시 함께 설치됨)

   2. 설치 및 실행
   터미널 실행(cmd)
   # 저장소 복제 (방법 2가지 원래 폴더 이름 그대로 사용, 원하는 이름 설정)
   git clone https://github.com/amibo1347/Bootcamp_TeamProject
   git clone https://github.com/amibo1347/Bootcamp_TeamProject 바꾸고싶은 폴더 이름

   # 프로젝트 폴더로 이동
   cd Bootcamp_TeamProject (폴더 이름 바꾼 경우 : cd 바꾼 폴더 이름)

   # 의존성 패키지 설치
   npm install

   # 개발 서버 실행 (localhost:8080)
   npm run start & npm start (둘 중 하나 택 1 : 차이점 없음)

   # 배포용 빌드 (dist 폴더 생성 : 프로젝트 100% 완성하고 나서 사용하는 코드)
   npm run build

## 기술스택
Frontend: Mustache 서버 데이터를 화면에 뿌려주는 템플릿 엔진

UI/UX:	Tailwind CSS + Alpine.js 현대적이고 반응형인 인트라넷 디자인 구현

Build Tool: Webpack 5 / Gradle 프론트 자산 관리 및 자바 프로젝트 빌드

Database: MySQL 각종 데이터 저장용

Backend:	Java 17 / Spring Boot 시스템 로직 및 API 컨트롤러 관리

## 프로젝트 구조
src/
├── css/            # Tailwind 및 커스텀 스타일
├── js/             # 메인 로직 및 차트 초기화
├── partials/       # 재사용 가능한 HTML 조각 (Sidebar, Header 등)
├── images/         # 사진
└── index.html      # 메인 대시보드 페이지

## 팀 협업 규칙
팀 협업 규칙 (Git Flow)
Main Branch: 배포 가능한 상태의 코드만 관리 (메인은 건드리지 않기)

origin = 현재 레포지토리 주소 별칭

브랜치 생성 : git checkout -b 이름(feat/이름)

## 필수 명령어
   git pull origin	: 원격 저장소 내용 가져오기
      ex/ git pull origin main : origin(GitHub주소)의 main 브라우저 내용을 내 컴퓨터로 가져온다

   git add . : 변경 사항 스테이징	

   git commit -m "내용"	: 로컬 저장소에 저장	(ex/ git commit -m "feat/로그인 구현")

   git push	origin: 원격 저장소에 올리기	
      ex/ git push origin main : 내 컴퓨터의 main 브랜치 내용을 origin(GitHub주소)로 보낸다
Commit Message:

   feat: 새로운 기능 추가

   fix: 버그 수정

   docs: 문서 수정 (README 등)

   style: 스타일 변경 (코드 변경 없음)

## 작업 흐름

   브랜치 생성 (git checkout -b 브랜치이름) 후 작업

   이미 생성된 브랜치 이동 git checkouut 브랜치이름

   내 브랜치에 push 하기

   GitHub 사이트에서 Pull Request (PR) 생성

   팀장의 승인 후 main에 합치기(Merge)

## ⚠️ 주의 사항
node_modules는 절대 Push하지 마세요.

중요한 보안 키나 환경 변수는 .env 파일에 작성하고 깃허브에 올리지 않습니다.

## License
This project is based on [TailAdmin](https://tailadmin.com/) and is licensed under the MIT License.
