# 모임 프론트엔드

HTML·CSS·일반 JavaScript로 만든 한국어 반응형 채팅 화면입니다. 이 폴더가 프론트엔드 소스의 기준입니다. TypeScript 컴파일 단계는 없습니다.

```text
frontend/
├── index.html
├── assets/
│   ├── styles.css
│   ├── favicon.svg
│   ├── js/                 # 화면, REST, STOMP, 공통 함수, 둘러보기
│   └── vendor/             # 고정 버전 STOMP.js와 라이선스
├── package.json
├── playwright.config.mjs
├── playwright.integration.config.mjs
├── tools/                  # 미리보기, 라이브러리 복사, 통합 검증 실행
└── tests/                  # 단위, 브라우저 회귀, 실제 서버 연동 검증
```

## 화면 미리보기

프로젝트 루트에서 `npm run preview`를 실행하거나, 아래처럼 프론트엔드 폴더로 이동해 실행합니다:

```sh
cd frontend
npm run preview
```

<http://127.0.0.1:4173>에서 **먼저 화면 둘러보기**를 선택하세요. 미리보기 서버는 정적 화면 검토용이며 실제 API를 proxy하지 않습니다. 실제 계정은 Spring Boot 서버 <http://localhost:8092>에서 연결합니다.

## 설치와 테스트

의존성은 루트 npm workspace에서 관리하며 잠금 파일은 루트의 `package-lock.json`입니다.

```sh
# 프로젝트 루트
npm ci
npm test
npx playwright install chromium
npm run test:e2e
npm run test:integration
```

이 폴더에서도 `npm test`, `npm run test:e2e`, `npm run test:integration`을 실행할 수 있습니다. 설치된 Chrome을 쓰려면 `CHAT_BROWSER_PATH`를 지정합니다. 프론트엔드 테스트 결과와 화면 캡처는 `frontend/test-results/`, `frontend/playwright-report/`, `frontend/.cache/screenshots/`에 생성됩니다.

## 백엔드 포함 방식

Gradle `processResources`가 `index.html`과 `assets/`만 `build/resources/main/static/`에 복사합니다. `bootRun`, 테스트 서버와 배포 JAR가 모두 이 자산을 사용합니다. Docker 빌드도 같은 경로를 포함하며 npm 설치 없이 실행할 수 있습니다. 직접 소스를 수정할 때는 이 폴더를 편집하세요.

API 경로는 같은 origin의 `/api/chat`, 소켓은 `/ws`입니다. 구체적인 화면·상태 처리와 운영 설정은 [프론트엔드 문서](../docs/FRONTEND.md), [API 계약](../docs/API.md), [실행 가이드](../docs/OPERATIONS.md)를 참고하세요.
