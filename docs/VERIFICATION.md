# 검증 기록

검증 날짜: 2026-10-07. Windows, Java 21, Node.js 24, 설치된 Chrome에서 확인했습니다.

## 결과

| 검증 | 결과 | 범위 |
| --- | --- | --- |
| `npm test` | 6개 통과 | 토큰·URL, 메시지 병합·길이, 검색, REST 인증·응답·오류 |
| `npm run test:e2e` | 10개 통과 | 데스크톱/모바일 각각 5개 브라우저 시나리오 |
| `gradlew test` | 6개 통과 | 실제 HTTP/WebSocket 통합 5개 + 접속 추적 1개 |
| `npm run test:integration` | 브라우저 2개 통과 | 실제 Spring Boot에 두 사용자 연결, 데스크톱/모바일 |
| `gradlew bootJar` | 성공 | 정적 웹 자산을 포함한 실행 가능 JAR |
| 화면 캡처 검토 | 완료 | 데스크톱 1440×960, Pixel 7 크기의 시작·대화 화면 |

브라우저 시나리오:

1. 시작 화면 → 명시적인 둘러보기 → 메시지 입력 → 한글 조합 중 Enter 차단 → 사용자 HTML을 텍스트로 표시 → 방 생성 → 개설자 삭제 → 화면 해제. JavaScript 오류와 가로 넘침도 확인했습니다.
2. 실제 `ChatApi`/`ChatTransport`에 맞춘 HTTP/STOMP fixture → Bearer 헤더와 join 호출 → 서버 메시지 echo → localStorage/sessionStorage 미사용 → 연결 해제 → 401 안내.
3. 소켓 단절 → 입력 차단·작성 내용 보존 → 자동 재연결 → 구독 복구 → 두 페이지에 걸친 100개 메시지 복구.
4. 인증 요청 진행 중 창 닫기 → HTTP 요청 취소 → 늦은 응답으로 연결되지 않음.
5. 방 40개를 두 페이지로 로딩 → 갱신 후 페이지 유지 → 검색 결과가 갱신 후에도 유지.

실제 서버와 브라우저의 통합 시나리오:

- `BrowserIntegrationTests`가 임의 포트에서 Spring Boot를 시작하고 Playwright를 실행합니다. 검증이 끝나면 Spring 테스트 컨텍스트가 서버를 종료합니다.
- 각 화면 크기에서 독립적인 개설자·참여자 브라우저를 연결하고 방 생성, 실시간 메시지의 양방향 전달, 전송 확인, 온라인 인원 변경을 확인합니다.
- 참여자 새로고침 후 인증 데이터 초기화와 오프라인 전환, 재참여 시 DB 기록 복원, 퇴장 시 참여 인원 감소를 확인합니다.
- 개설자에게만 삭제 버튼이 표시되고, 방 삭제가 양쪽 화면에 반영되는지 확인합니다.
- 이 검증에는 HTTP/WebSocket route fixture가 없으며 실제 컨트롤러·인증 필터·broker·서비스·DB 기록을 사용합니다. JUnit 실행기 1개에서 브라우저 시나리오 2개를 실행합니다.

백엔드 통합 시나리오:

- 인증 없는 화면·CSS·JS·STOMP 번들·health 접근과 REST 401, 인증한 `/me`
- 방 생성, 비참여 기록 접근 거부, 참여 저장과 재참여의 중복 방지, Board userId 응답, 비개설자 삭제 403, 퇴장·삭제
- 잘못된 이름·JSON, 폐기된 JWT와 잘못된 JWT
- 실제 STOMP 연결·구독·한글 메시지 전송, DB 기록·커서 페이지, UNSUBSCRIBE와 연결 종료의 온라인 인원 정리
- 잘못된 토큰, 비참여 구독, broker 토픽 직접 SEND 위조의 거부
- 여러 탭·중복 구독이 한 사용자로 집계되고 마지막 구독 해제만 퇴장 처리

## 실행

```powershell
npm ci
npm test
$env:CHAT_BROWSER_PATH = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
npm run test:e2e
npm run test:integration
.\gradlew.bat --no-daemon -g .cache/gradle test bootJar
```

설치된 Chrome을 지정하지 않으면 `npx playwright install chromium`으로 Playwright 브라우저를 준비합니다. 테스트 보고서는 `frontend/playwright-report/index.html`, `build/reports/tests/test/index.html`, `build/reports/tests/browserIntegrationTest/index.html`입니다. 브라우저 테스트는 `frontend/.cache/screenshots/`에 예시·실제 서버 연결 화면을 저장합니다. 보고서·스크린샷·의존성 캐시는 Git에 포함하지 않습니다. 통합 검증만 Gradle에서 실행하려면 `gradlew browserIntegrationTest`를 사용합니다. 일반 `gradlew test`는 브라우저 실행 태그를 제외하므로 npm이나 브라우저 설치가 없어도 실행됩니다.

프론트엔드를 `frontend/`로 옮긴 뒤 루트·프론트엔드 npm 실행 경로와 깨끗한 Gradle 빌드, 실제 HTTP/STOMP 연동을 다시 확인했습니다. Dockerfile도 새 소스 위치를 복사하도록 갱신했습니다.

## 범위와 한계

백엔드 통합 테스트는 실제 Spring Boot 서버와 HTTP/WebSocket을 사용하며 영속화는 H2로 확인합니다. Board 사용자 조회와 Redis denylist는 Mockito 대체 객체를 사용합니다. 테스트 전용으로 Redis health contributor를 끄므로 실제 Redis 연결을 증명하지 않습니다. 실제 `.env`와 외부 DB·Redis는 변경하지 않습니다.

`test:e2e`는 HTTP와 WebSocket 계약 fixture를 사용하고, `test:integration`은 실제 Spring Boot 서버를 사용합니다. 두 검증 모두 운영 MySQL·Redis·Board 및 reverse proxy를 한 번에 연결한 환경 테스트는 아닙니다. 배포 환경의 자격증명, DB 권한, 외부 서비스 연결, proxy의 WebSocket upgrade는 운영 가이드에 따라 별도로 확인해야 합니다. Firefox·Safari와 부하·다중 인스턴스 동작은 이번 검증 범위에 포함하지 않습니다.
