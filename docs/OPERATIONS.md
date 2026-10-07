# 실행·운영 가이드

## 전제 조건

- Java 21. Gradle wrapper 9.7.1은 저장소에 포함합니다.
- MySQL과 `chat` 데이터베이스. 서비스 계정은 Chat 테이블을 읽고 쓸 수 있어야 합니다. 현재 `ddl-auto=update`이므로 최초 실행에 테이블 생성·수정 권한도 필요합니다.
- 같은 MySQL 서버의 Board 스키마와 `users(id, email, nick_name)` 테이블. Chat DB 계정에 해당 테이블 SELECT 권한이 있어야 합니다.
- Redis 접속과 Board에서 쓰는 것과 동일한 `deny:<jti>` 키 규칙. 키가 있으면 토큰을 거부합니다. TTL 설정·폐기 키 쓰기는 Board의 책임입니다.
- Board의 HMAC JWT 키와 액세스 토큰. 토큰에는 `sub`(사용자 이메일), `jti`, `exp`가 있어야 합니다. 서명·만료뿐 아니라 Board 사용자 존재도 검사합니다.

현재 앱에 별도 로그인·갱신 엔드포인트는 없습니다. 토큰 발급을 위해 기존 Board 로그인을 사용하세요. Chat은 닉네임을 읽지만 현재 응답에는 이메일을 전달하며 화면은 이메일의 `@` 앞부분을 이름으로 표시합니다.

## 환경변수

`.env.example`을 참고해 프로젝트 루트의 `.env`를 준비합니다. 기존 `.env`를 덮어쓰지 마세요. Spring Boot는 실행 디렉터리의 `.env`를 properties 파일로 읽습니다. Docker에서는 Compose의 env_file로 주입합니다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| DB_HOST | localhost | MySQL 호스트 |
| DB_PORT | 3306 | MySQL 포트 |
| DB_NAME | chat | Chat 데이터베이스 |
| DB_USERNAME | root | 실제 운영에는 서비스 전용 DB 계정 권장 |
| DB_PASSWORD | 없음, 필수 | DB 비밀번호 |
| REDIS_HOST | localhost | Redis 호스트 |
| REDIS_PORT | 6379 | Redis 포트 |
| JWT_SECRET | 없음, 필수 | Base64 HMAC 키, 디코딩 후 최소 32바이트, Board와 동일 |
| APP_BOARD_SCHEMA | board | Board 스키마. 이전 `APP_BOARD_SHCEMA`도 호환 |
| APP_WS_ALLOWED_ORIGINS | `http://localhost:*` | 쉼표로 구분한 WebSocket browser origin 패턴 |

`APP_BOARD_SCHEMA`에는 실제 DB 스키마 식별자만 넣으세요. 개발 환경에서 localhost와 127.0.0.1을 둘 다 쓴다면 `.env.example`처럼 두 origin을 설정합니다. 운영은 실제 HTTPS origin을 지정합니다.

DB 시간대 URL 옵션은 Asia/Seoul이며 DTO는 `LocalDateTime`을 반환합니다. 서버/JVM과 브라우저 표시 기준을 맞추려면 서버 시간대를 Asia/Seoul로 설정하세요. Compose는 `TZ=Asia/Seoul`을 지정합니다. 전 세계 사용자 지원 시 UTC·offset이 있는 시각 계약으로 바꾸는 작업이 필요합니다.

## 로컬

Windows:

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```sh
./gradlew bootRun
```

<http://localhost:8092>에서 실제 토큰으로 연결합니다. 프론트엔드 파일은 루트의 `frontend/`에 있습니다. Gradle `processResources`가 화면과 자산을 서버의 static 리소스로 복사합니다. `bootRun`을 재실행하면 변경된 자산이 반영됩니다.

Gradle 기본 캐시 위치에 쓰기 권한이 없다면 프로젝트 캐시를 지정할 수 있습니다:

```powershell
.\gradlew.bat --no-daemon -g .cache/gradle test bootJar
```

화면 검토는 `npm run preview` 후 <http://127.0.0.1:4173>의 **먼저 화면 둘러보기**를 사용합니다. 이 정적 서버는 API나 WebSocket proxy를 제공하지 않습니다.

## Docker

현재 Compose는 **앱 컨테이너만** 정의합니다. 기존 외부 `board-net` 네트워크에 MySQL 컨테이너 `mysql8`, 설정한 Redis, Board 데이터가 있어야 합니다. 파일만 실행해 새 MySQL·Redis가 만들어지지는 않습니다. MySQL 호스트는 Compose가 `mysql8`로 덮어씁니다.

```sh
# 로컬 접근 포트 포함
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build -d
docker compose -f docker-compose.yml -f docker-compose.local.yml ps
docker compose -f docker-compose.yml -f docker-compose.local.yml logs --tail=100 chat-app
```

local override는 `127.0.0.1:8092:8092`만 공개합니다. 기본 compose는 포트를 호스트에 공개하지 않아 같은 network의 reverse proxy를 통해 접근합니다. Docker healthcheck는 공개된 `/actuator/health`를 확인합니다. health 응답은 헬스 상태를 제공하고 외부에 상세 DB·Redis 정보를 공개하지 않도록 기존 Actuator 기본값을 유지합니다.

Dockerfile은 Java 21 빌드 단계에서 `bootJar`를 만들고 Java 21 JRE의 비관리자 `spring` 사용자로 실행합니다. npm이나 CDN 접근 없이 포함된 정적 자산을 서비스합니다. `.dockerignore`는 실제 `.env`, Git과 의존성·테스트 캐시를 빌드 컨텍스트에서 제외합니다. Dockerfile의 JAR 빌드는 테스트를 생략하므로 배포 전 개발/CI에서 `gradlew test`를 실행하세요. 브라우저와 서버를 함께 확인하려면 `npm ci` 후 `npm run test:integration`을 실행합니다.

## 배포

1. 같은 origin의 HTTPS reverse proxy에서 앱의 `/`, `/assets/`, `/api/chat/`, `/ws`를 제공합니다.
2. `/ws`에 WebSocket Upgrade/Connection 헤더와 장시간 연결 timeout을 설정합니다.
3. `APP_WS_ALLOWED_ORIGINS`를 실제 브라우저 origin으로 맞춥니다.
4. proxy의 Forwarded 헤더를 신뢰할 수 있는 경계에서만 전달합니다. 앱은 `server.forward-headers-strategy=framework`를 사용합니다.
5. 실제 Board 키와 DB/Redis 접속값을 서버의 비밀 설정으로 주입합니다. `.env`를 이미지나 Git에 포함하지 않습니다.
6. DB/Redis/Board 데이터 접근, 로그, health와 실제 채팅 연결을 확인합니다.

현재는 단일 인스턴스 배포가 기준입니다. 다중 인스턴스에는 외부 STOMP broker와 공유 presence가 필요합니다. 운영 DB는 `ddl-auto=update` 대신 검토된 마이그레이션을 도입하고 백업·보존 정책을 마련하세요. 방 삭제는 기록까지 영구 삭제합니다.

## 문제 해결

| 현상 | 확인할 내용 |
| --- | --- |
| 서버가 시작되지 않음 | Java 21, DB 접속·권한·데이터베이스, Base64 JWT 키와 길이 |
| REST LOGIN_REQUIRED | Bearer 토큰, exp/sub/jti, 공유 키, Board 이메일, Redis deny 키 |
| Redis 오류 / 인증 실패 | Redis host/port와 연결 상태. 인증은 Redis에 의존 |
| Board 사용자 조회 오류 | 스키마 변수, users 컬럼, Chat 계정의 SELECT 권한 |
| 웹 화면은 열리나 소켓은 재연결 반복 | origin 허용값, proxy Upgrade, `/ws` 경로, HTTPS의 wss |
| NOT_ROOM_MEMBER | join 성공 후 구독·기록 조회 여부 |
| 방 삭제 403 | 연결 이메일이 실제 개설자인지 확인 |
| 연결 해제 후 온라인 인원이 계속 표시 | 새 코드 배포 여부, 소켓 종료 감지 시간, 여러 탭의 남은 구독 |
| 누락되거나 중복된 대화 | 서버 ID 기준 이력·실시간 병합, 연결 상태, DB 기록. 전송 실패 시 무조건 재전송하지 않기 |
| npm 도구 없이 화면 실행 불가 | `/assets/vendor/stomp.umd.min.js`가 포함되었는지 확인. 기본 실행에는 npm 불필요 |

로그 확인 시 실제 토큰·비밀번호를 복사하거나 공개하지 마세요. UI는 STOMP의 raw frame debug를 출력하지 않습니다.
