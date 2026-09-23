# 프로젝트 점검 및 실행 방법

2026-09-17 기준. Windows PowerShell에서 실행합니다.

## 구성과 구현 상태

- `backend`: Java 21, Spring Boot 4.1.0, Gradle, JPA, Spring Security/JWT, MySQL.
- `frontend`: React 19, TypeScript, Vite 8, Tailwind CSS, Axios.
- 회원가입/로그인/토큰 갱신/로그아웃, 회원정보 수정/비밀번호 변경/탈퇴 API와 화면이 있습니다.
- 공연 목록/카테고리/인기 순위/상세/네이버 지도, 주문 생성/토스 결제 승인/예매 내역 코드가 연결되어 있습니다. 실제 외부 결제와 지도 동작은 이번 점검에서 검증하지 않았습니다.
- 판매 관리 화면은 고정 예시 데이터이며 등록/수정/삭제 버튼에 기능이 없습니다.
- 주간 순위 쿼리는 현재 주간 날짜 조건 없이 누적 판매량을 집계합니다.
- `backup.sql`에는 테이블 생성문만 있습니다. 새 MySQL 볼륨에는 공연·티켓 데이터가 들어가지 않습니다. 기존 데이터가 필요하면 별도 데이터 덤프가 필요합니다.

## 실행 순서

먼저 Docker Desktop이 정상 실행되어 있어야 합니다. DB → 백엔드 → 프론트엔드 순서입니다.
Windows의 `MySQL80` 서비스도 실행 중이면 3306 포트가 충돌합니다. Docker MySQL을 사용할 때는 `MySQL80`을 중지한 상태로 둡니다.

### 1. DB 및 백엔드 — 터미널 1

```powershell
cd "C:\Users\옥준서\Desktop\인프전\code\backend"
docker compose up -d
docker compose ps
docker compose logs --tail 50 mysql-db
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat bootRun
```

MySQL 준비 완료 후 백엔드를 실행합니다. 백엔드는 기본 8080 포트를 사용합니다.
`http://localhost:8080/api/events`로 목록 API를 확인할 수 있습니다.
백엔드 작업 폴더에서 실행해야 해당 폴더의 `.env`를 읽습니다.

현재 PC 기본 Java는 24이므로 위처럼 설치된 JDK 21을 명시합니다.
JDK 21 지정 후 소스 컴파일은 통과했지만, Gradle 테스트 실행은
`GradleWorkerMain` 클래스를 찾지 못하는 환경 오류로 실패했습니다.
테스트 통과는 아직 확인되지 않았습니다. 이후 Docker 복구 및 포트 충돌 해결 후 백엔드 기동과 목록 API HTTP 200 응답을 확인했습니다.

### 2. 프론트엔드 — 터미널 2

```powershell
cd "C:\Users\옥준서\Desktop\인프전\code\frontend"
npm.cmd ci
npm.cmd run dev
```

`npm.cmd ci`는 최초 설치 또는 lock 파일 변경 시 실행하면 됩니다. 이번 점검에서 설치했습니다.
접속 주소는 보통 `http://localhost:5173`이며, 터미널에 표시된 주소를 따릅니다.
프론트엔드 `.env`의 `VITE_BACKEND_URL`은 `http://localhost:8080/api`입니다.
환경변수를 변경하면 개발 서버를 재시작합니다.

### 3. 종료

서버 터미널에서 `Ctrl+C`를 누릅니다. DB는 backend 폴더에서 `docker compose stop`으로 중지합니다.
DB 데이터를 유지하려면 `docker compose down -v`나 Docker 공장 초기화를 사용하지 마세요.

## 환경 설정

두 폴더에 `.env`가 이미 있고 필요한 값이 설정되어 있습니다. 키의 유효성까지 확인한 것은 아닙니다.

- backend: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION`, `TOSS_SECRET_KEY`, `TOSS_API_BASE_URL`.
- frontend: `VITE_BACKEND_URL`, `VITE_TOSS_CLIENT_KEY`, `VITE_NAVER_MAP_CLIENT_ID`.
- frontend `.env.example`에는 네이버 지도 항목이 빠져 있습니다.
- backend `.env.example`의 JWT 값은 비어 있으므로 새 환경에서는 별도로 설정해야 합니다. JWT 만료 시간 단위는 밀리초이며 현재 1800000입니다.

## 확인 결과 및 현재 장애

- 프론트엔드 의존성 설치 완료, 프로덕션 빌드 성공, 기존 테스트 4개 통과.
- 프론트엔드 개발 서버에서 HTTP 200 응답 확인.
- ESLint는 기존 오류 8개로 실패했습니다: 네이버 지도 `any` 타입 3개, AuthContext 컴포넌트/훅 혼합 export 1개, `prefer-const` 4개.
- npm 설치 결과 취약점 7개(높음 6, 보통 1)가 보고되었습니다. 자동 버전 변경은 하지 않았습니다.
- 최초 Docker Desktop 4.80.0 시작 시 `dockerInference` 소켓 접근 오류가 발생했습니다. 이후 사용자가 Docker를 실행하고 Windows `MySQL80` 서비스를 중지한 뒤 Docker MySQL과 백엔드가 정상 실행되었습니다.
- `http://localhost:8080/api/events` 및 `http://localhost:5173`에서 HTTP 200 응답을 확인했습니다. 현재 Docker DB의 공연 목록은 비어 있습니다.
- 같은 오류가 Docker 이슈에 보고되어 있습니다: https://github.com/docker/desktop-feedback/issues/527
- 우선 Docker 오류 창에서 Quit 후 Windows를 재시작하고 Docker를 다시 실행합니다. 해결을 보장하는 절차는 아닙니다. 반복되면 Docker 로그와 남아 있는 런타임 소켓을 점검해야 합니다. 한글 사용자 경로가 원인이라고 확정하지 않았습니다.
- 기존 DB 보존이 필요하므로 공장 초기화, 볼륨 삭제, WSL 배포판 삭제는 피합니다.

## 다음 개발 시 우선 확인할 부분

1. Docker 복구 후 기존 DB 데이터 유무와 주요 API 응답 확인.
2. Gradle 테스트 실행 환경 오류 해결 및 백엔드 테스트 수행.
3. 회원가입 → 로그인 → 티켓 선택 → 테스트 결제 → 예매 내역 전체 흐름 확인.
4. 판매 등록/수정/삭제 API와 화면 구현.
5. 주간 순위 날짜 조건 구현 및 결제 승인 이후 DB 처리 실패 시 보상 처리 검토.

이번 점검에서는 애플리케이션 소스와 DB 데이터를 변경하지 않았습니다.
