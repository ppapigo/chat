# API 계약

기본 주소: `http://localhost:8092`. `/`, `/index.html`, `GET /assets/**`, `GET /actuator/health`는 공개입니다. `/ws`의 HTTP 핸드셰이크는 공개이고 **STOMP CONNECT 프레임에서 인증**합니다. 모든 REST API는 `Authorization: Bearer <access-token>`이 필요합니다.

## HTTP

| 메서드 | 경로 | 요청 / 파라미터 | 응답 | 조건 |
| --- | --- | --- | --- | --- |
| GET | `/api/chat/me` | 없음 | 200, 이메일 plain text | 인증된 Board 사용자 |
| GET | `/api/chat/rooms` | `page=0&size=20` | 200, 페이지 객체 | 생성일 내림차순 |
| POST | `/api/chat/rooms` | `{name, description}` | 201, Room | 생성자는 자동 참여 |
| GET | `/api/chat/rooms/{id}` | 없음 | 200, Room | 존재하는 방 |
| POST | `/api/chat/rooms/{id}/join` | 본문 없음 | 200, Room | 이미 참여한 사용자도 성공 |
| DELETE | `/api/chat/rooms/{id}/leave` | 본문 없음 | 204 | 참여하지 않은 사용자도 성공 |
| DELETE | `/api/chat/rooms/{id}` | 본문 없음 | 204 | 개설자만 가능 |
| GET | `/api/chat/rooms/{id}/members` | 없음 | 200, Member 배열 | 해당 방 참여자 |
| GET | `/api/chat/rooms/{id}/messages` | `before`, `size=50` | 200, MessagePage | 해당 방 참여자 |

방 이름은 비어 있을 수 없고 최대 50자, 소개는 최대 200자입니다. 이름은 DB에서 유일합니다. 프론트엔드는 입력의 앞뒤 공백을 제거합니다. 방 삭제 시 메시지와 참여 정보를 모두 삭제합니다. 방 목록은 전체 공개 방을 반환하며 별도 개인 방 목록·서버 검색 API는 없습니다.

### Room

```json
{
  "id": 12,
  "name": "오늘의 라운지",
  "description": "편하게 나누는 이야기",
  "ownerUsername": "owner@example.com",
  "memberCount": 5,
  "onlineCount": 3,
  "createdAt": "2026-10-07T12:00:00"
}
```

목록의 직렬화는 Spring Data `PagedModel` 구조입니다:

```json
{
  "content": [],
  "page": { "size": 20, "number": 0, "totalElements": 0, "totalPages": 0 }
}
```

### Member

```json
{
  "userId": 42,
  "email": "owner@example.com",
  "online": true,
  "joinAt": "2026-10-07T12:00:00"
}
```

`userId`는 Board 사용자 ID이며 참여 레코드 ID가 아닙니다. 필드명은 현재 계약인 `joinAt`을 유지합니다. `online`은 해당 방의 STOMP 구독이 하나 이상 있는 상태입니다. HTTP 참여와 온라인 접속은 서로 다른 상태입니다.

### Message와 MessagePage

```json
{
  "messages": [
    {
      "id": 91,
      "roomId": 12,
      "type": "TALK",
      "senderUserId": 42,
      "senderEmail": "owner@example.com",
      "content": "안녕하세요",
      "createdAt": "2026-10-07T12:30:00"
    }
  ],
  "hasMore": true,
  "nextBefore": 91
}
```

`type`: `TALK`, `ENTER`, `LEAVE`. 메시지와 입·퇴장 안내는 DB에 함께 저장됩니다. 응답 배열은 ID 오름차순입니다. 최초 요청은 최신 메시지를 반환하고, 이전 기록은 `before=nextBefore`로 요청합니다. `before`는 해당 ID를 포함하지 않습니다. `size`는 서버가 1~100으로 제한합니다. 빈 결과의 `nextBefore`는 null입니다. `createdAt`과 `joinAt`은 오프셋이 없는 서버 로컬 시각입니다.

## WebSocket / STOMP

네이티브 WebSocket 경로는 `/ws`입니다. SockJS는 사용하지 않습니다. HTTP와 동일한 호스트에서 HTTP는 `ws`, HTTPS는 `wss`를 사용합니다. CONNECT 헤더:

```text
Authorization: Bearer <access-token>
accept-version: 1.2
```

| 동작 | 목적지 | 본문 / 조건 |
| --- | --- | --- |
| SUBSCRIBE | `/topic/rooms` | 모든 인증 사용자, RoomEvent |
| SUBSCRIBE | `/topic/room/{id}` | 해당 방 참여자, Message |
| SUBSCRIBE | `/user/queue/errors` | 자신의 메시지 처리 오류, ErrorResponse |
| SEND | `/app/room/{id}/message` | `{"content":"안녕하세요"}`, 해당 방 참여자 |
| SEND | `/app/echo` | 기존 연결 테스트용 `{"content":"hello"}` |
| SUBSCRIBE | `/topic/echo` | 기존 연결 테스트용 브로드캐스트 |

방 메시지는 앞뒤 공백을 제거하며 빈 문자열을 거부합니다. 최대 1000 UTF-16 코드 단위로 Java `String.length()`와 브라우저 문자열 길이가 동일합니다. 이모지 일부는 길이 2로 계산합니다. `SEND`와 `SUBSCRIBE` 때 만료·Redis denylist를 다시 확인합니다. 방 구독도 서버에서 참여 권한을 검사합니다. 임의 토픽·다른 사용자의 큐 구독과 broker 토픽으로 직접 보내는 SEND는 거부합니다.

RoomEvent:

```json
{ "type": "MEMBER_COUNT", "room": { "id": 12, "name": "오늘의 라운지", "description": "", "ownerUsername": "owner@example.com", "memberCount": 5, "onlineCount": 3, "createdAt": "2026-10-07T12:00:00" } }
```

종류는 `ROOM_CREATED`, `ROOM_DELETED`, `MEMBER_COUNT`입니다. 마지막 종류는 참여 인원과 온라인 인원 변경에 사용합니다. 방 구독 시 첫 온라인 접속에 ENTER, 마지막 구독 해제·접속 종료에 LEAVE 메시지를 보냅니다. 여러 탭은 같은 이메일을 온라인 1명으로 계산합니다.

프론트엔드는 연결될 때마다 로비·오류 큐·활성 방을 다시 구독합니다. STOMP `ERROR`에는 `code`, `message` 헤더가 있고 연결이 종료됩니다. 메시지 비즈니스 오류는 `/user/queue/errors`로 전달하며 소켓을 유지합니다. Simple broker에는 애플리케이션 전송 성공 영수증이 없으므로 프론트엔드는 **서버가 보내온 자기 메시지**로 전송을 확인합니다. 10초 내 확인되지 않으면 기록 확인을 안내하고 자동 재전송하지 않습니다.

## 오류

```json
{
  "code": "INVALID_INPUT",
  "message": "입력값이 올바르지 않습니다",
  "timeStamp": "2026-10-07T12:30:00",
  "errors": [{ "field": "name", "reason": "must not be blank" }]
}
```

`errors`는 검증 오류에만 포함하며 `timeStamp`의 대소문자는 현재 계약을 유지합니다.

| 코드 | HTTP 상태 | 의미 |
| --- | --- | --- |
| LOGIN_REQUIRED | 401 | 토큰 없음·유효하지 않음·폐기됨, Board 사용자 없음 |
| TOKEN_EXPIRED | 401 / STOMP ERROR | 만료된 STOMP 인증 또는 프레임 |
| ACCESS_DENIED | 403 | 개설자가 아닌 삭제, 허용되지 않은 구독 |
| NOT_ROOM_MEMBER | 404 | 방 참여 권한 없음. 현재 백엔드 계약의 404 유지 |
| ROOM_NOT_FOUND | 404 | 방 없음 |
| USER_NOT_FOUND | 404 | 정의된 사용자 오류 코드 |
| RESOURCE_NOT_FOUND | 404 | 요청한 정적 경로 없음 |
| INVALID_INPUT | 400 | 입력 검증, 빈 메시지 |
| MALFORMED_REQUEST | 400 | 읽을 수 없는 JSON |
| DUPLICATE_ROOM_NAME | 400 | 같은 이름의 방 |
| MESSAGE_TOO_LONG | 400 | 1000자 초과 |
| INTERNAL_SERVER_ERROR | 500 | 예상하지 못한 메시지 처리 오류 |

REST의 만료·비정상 토큰은 현재 필터 계약에 따라 `LOGIN_REQUIRED`로 응답합니다. 클라이언트의 네트워크 오류는 서버 오류 코드와 구별해 안내합니다.
