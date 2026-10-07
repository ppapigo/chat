# 모임 · Chat

Spring Boot 채팅 백엔드와 HTML·CSS·일반 JavaScript로 작성한 한국어 반응형 웹 프론트엔드입니다. 같은 서버에서 화면, REST API, STOMP WebSocket을 제공하므로 별도 프론트엔드 배포나 CORS 설정이 필요하지 않습니다.

## 제공 기능

- Board 액세스 토큰으로 계정 연결, 인증 실패·만료 안내, 화면 연결 해제
- 채팅방 목록·페이지 추가 로딩, 불러온 방 이름/소개 검색, 온라인 방 필터
- 방 생성·참여·퇴장, 방 개설자의 삭제, 참여자·온라인 상태
- 실시간 메시지, 이전 기록의 커서 페이지 로딩, 재연결과 누락 기록 복구
- 한글 IME 입력, Enter 전송·Shift+Enter 줄바꿈, 방별 임시 작성 내용
- 좁은 화면의 채팅방·참여자 패널, 키보드 접근, 로딩·빈 화면·오류 처리
- 서버 호출 없이 동작하는 명시적인 **화면 둘러보기** 모드

채팅 서비스 자체는 로그인·회원가입·토큰 갱신 API를 제공하지 않습니다. 실제 연결에는 Board 로그인으로 받은 토큰과 해당 Board 사용자가 필요합니다. 토큰은 브라우저 메모리에만 보관하고 저장소나 URL에 쓰지 않습니다.

## 프로젝트 구조

| 위치 | 내용 |
| --- | --- |
| [`frontend/`](frontend/README.md) | 화면 HTML, CSS, JavaScript, npm 설정, 도구와 프론트엔드 테스트 |
| `src/main/java/` | Spring Boot 백엔드 |
| `src/main/resources/application.yaml` | 백엔드 실행 설정 |
| `src/test/java/` | 백엔드 및 브라우저 통합 검증 실행기 |
| `docs/` | API, 구조, 화면, 운영, 검증 문서 |

프론트엔드는 `frontend/`에서 편집합니다. Gradle이 웹 자산만 `build/resources/main/static/`에 복사해 서버와 배포 JAR에 포함합니다. 루트 npm 명령은 `frontend` workspace로 전달되며 `cd frontend` 후 같은 명령도 사용할 수 있습니다.

## 빠른 시작

### 화면만 확인

별도 설치 없이 정적 파일을 제공할 수 있습니다. Node.js 22 이상에서:

```sh
npm run preview
```

<http://127.0.0.1:4173>에서 **먼저 화면 둘러보기**를 선택하세요. 예시 방 생성·전송·삭제는 메모리에서만 처리되며 새로고침하면 초기화됩니다. 이 서버는 프론트엔드 검토용입니다. 실제 API 연결은 아래 Spring Boot 서버에서 합니다.

### 실제 백엔드 연결

Java 21, MySQL, Redis, Board 사용자 테이블이 필요합니다.

1. `.env.example`을 `.env`로 복사하고 DB·Redis·Board JWT 설정을 입력합니다. 기존 `.env`가 있다면 필요한 항목만 확인하세요.
2. MySQL의 `chat` 데이터베이스와 같은 서버에 있는 `board.users`에 접근 가능한 DB 계정을 준비합니다.
3. 서버를 실행합니다.

```powershell
# Windows
.\gradlew.bat bootRun
```

```sh
# Linux / macOS
./gradlew bootRun
```

4. <http://localhost:8092>에서 **계정 연결하기**를 누르고 Board의 액세스 토큰을 입력합니다. `Bearer ` 접두어를 포함한 토큰도 사용할 수 있습니다.

운영에 필요한 설정과 Docker 실행 방법은 [실행·운영 가이드](docs/OPERATIONS.md)를 참고하세요. npm 설치나 프론트엔드 빌드 없이 Gradle/Docker JAR에 웹 자산이 포함됩니다.

## 개발 및 검증

```sh
npm ci
npm test
npx playwright install chromium
npm run test:e2e
npm run test:integration
```

```powershell
.\gradlew.bat test bootJar
```

브라우저 테스트는 기본 Chromium을 사용합니다. 설치된 Chrome으로 실행하려면 PowerShell에서 `$env:CHAT_BROWSER_PATH = 'C:\Program Files\Google\Chrome\Application\chrome.exe'`를 설정하세요. `test:e2e`는 프로토콜 fixture와 화면 회귀를, `test:integration`은 실제 Spring Boot 서버에 연결한 두 사용자 브라우저를 검증합니다. 통합 검증은 임의 포트에서 서버를 시작하고 종료까지 자동 처리합니다. 백엔드 검증은 H2와 테스트용 Board/Redis 대체 객체를 사용하며 실제 `.env`, DB, Redis를 변경하지 않습니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [전체 구조](docs/ARCHITECTURE.md) | 서비스 책임, 인증·메시지·접속 흐름, 데이터 모델 |
| [API 계약](docs/API.md) | HTTP 경로, 요청·응답, STOMP 목적지, 오류 |
| [프론트엔드](docs/FRONTEND.md) | 화면 사용법, 모듈, 상태 처리, 접근성, 확장 방법 |
| [실행·운영](docs/OPERATIONS.md) | 환경변수, Board 연동, 로컬·Docker 배포, 문제 해결 |
| [검증 기록](docs/VERIFICATION.md) | 자동 테스트 범위와 검증의 한계 |

## 기술 구성

백엔드: Java 21, Spring Boot 4.1.1, Spring Security, JPA, MySQL, Redis, JJWT. 프론트엔드: HTML/CSS, 브라우저 ES modules, STOMP.js 7.3.0. 개발 도구: Node.js, Node test runner, Playwright.

STOMP.js 번들과 라이선스는 `frontend/assets/vendor/`에 포함했습니다. 버전을 변경할 때 `npm ci` 후 `npm run vendor`를 실행하고 번들·라이선스·잠금 파일을 함께 반영하세요. 라이브러리 사용 방식은 [STOMP.js 공식 가이드](https://stomp-js.github.io/guide/stompjs/using-stompjs-v5.html)를 참고했습니다.
