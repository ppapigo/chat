# MOA CASE 01 — 채팅 메시지 커밋 전 발행 위험 개선

## 문제
기존 ChatMessageService는 JPA save 직후, 트랜잭션이 커밋되기 전에 STOMP 메시지를 보냈습니다.
이후 DB 트랜잭션이 롤백되면 저장되지 않은 메시지가 클라이언트에 전송될 수 있는 위험이 있었습니다.
이는 코드에서 확인한 정합성 위험이며 실제 운영 장애를 확인한 것은 아닙니다.

## 원인
- JPA save 성공과 DB 커밋 성공은 다릅니다.
- DB 트랜잭션의 롤백으로 이미 발행한 STOMP 메시지를 취소할 수 없습니다.

## 선택지와 결정
- 즉시 발행: 간단하나 롤백 위험이 남아 제외
- Spring TransactionalEventListener(AFTER_COMMIT): 추가 인프라 없이 커밋 성공 후 발행. **선택**
- Transactional Outbox: 재시도·복구에 유리하나 테이블/워커/멱등 처리 등 복잡도 증가. 향후 고려
- 메시지 브로커: 현재 규모에는 추가 인프라 부담

## 변경
- ChatMessageSavedEvent를 트랜잭션 안에서 발행하고, ChatMessageCommittedListener에서 AFTER_COMMIT에 STOMP로 방송
- 전송 실패는 메시지/방 ID를 로그로 남김
- 기존 DTO, STOMP 구독 주소(/topic/room/{roomId}) 및 컨트롤러 변경 없음

## 테스트와 한계
ChatMessageCommitPublicationTest는 Spring의 트랜잭션 동기화와 Mockito로
1. 커밋 이전 발행 없음 → 커밋 후 1회 발행
2. 롤백 시 발행 없음
3. 커밋 이후 전송 예외가 커밋 결과를 되돌리지 않음
을 검증하도록 작성했습니다.

**단위 테스트 작성과 실제 실행 성공은 다릅니다.**
실제 MySQL 트랜잭션 연동, 운영 STOMP 수신, 정량 성능 비교는 추후 검증이 필요합니다.
커밋 직후 프로세스가 종료되면 전송이 유실될 수 있으며, 자동 재전송/수신 보장은 없습니다.
확실한 전달 보장이 필요하면 Outbox 및 멱등 재시도를 도입해야 합니다.

방 생성·입장·퇴장·삭제 이벤트 발행 시점은 이번 변경 범위에 포함하지 않았습니다.
