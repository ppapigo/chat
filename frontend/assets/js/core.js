/** Pure helpers shared by UI, adapters and tests. */
export function normalizeToken(value) {
  return value.trim().replace(/^Bearer\s+/i, '').trim();
}
export function websocketUrl(location) {
  return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws`;
}
export function mergeMessages(current, incoming) {
  return [...new Map([...current, ...incoming].map(message => [String(message.id), message])).values()]
    .sort((a, b) => Number(a.id) - Number(b.id));
}
export function filterRooms(rooms, search, filter) {
  const query = search.trim().toLocaleLowerCase();
  return rooms.filter(room => (filter !== 'online' || room.onlineCount > 0) &&
    `${room.name} ${room.description || ''}`.toLocaleLowerCase().includes(query));
}
export function displayName(email) { return (email || '알 수 없음').split('@')[0]; }
export function avatarColor(value) {
  const colors = ['#e9eedf', '#f0e8dd', '#e5eced', '#ece5ee', '#e1ebdf'];
  let hash = 0;
  for (const char of String(value)) hash = (hash * 31 + char.charCodeAt(0)) | 0;
  return colors[Math.abs(hash) % colors.length];
}
export function validateMessage(content) {
  const text = content.trim();
  if (!text) throw new Error('메시지를 입력해 주세요.');
  if (text.length > 1000) throw new Error('메시지는 1000자까지 보낼 수 있어요.');
  return text;
}
export function errorText(error) {
  const messages = {
    LOGIN_REQUIRED: '계정 연결이 필요합니다. 유효한 액세스 토큰으로 다시 연결해 주세요.',
    TOKEN_EXPIRED: '토큰이 만료되었습니다. Board에서 새 토큰을 발급받아 다시 연결해 주세요.',
    NOT_ROOM_MEMBER: '채팅방 참여 정보가 없습니다. 방을 다시 선택해 주세요.',
    ROOM_NOT_FOUND: '삭제되었거나 존재하지 않는 채팅방입니다.',
    ACCESS_DENIED: '이 작업을 수행할 권한이 없습니다.',
    DUPLICATE_ROOM_NAME: '같은 이름의 채팅방이 있어요. 다른 이름을 입력해 주세요.',
    MESSAGE_TOO_LONG: '메시지는 1000자까지 보낼 수 있어요.',
    INTERNAL_SERVER_ERROR: '서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
  };
  return messages[error.code] || error.message || '요청을 처리하지 못했습니다.';
}
