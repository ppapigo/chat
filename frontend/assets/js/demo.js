import { ApiError } from './api.js';
// Explicit, in-memory preview. This adapter never calls the backend.
export class DemoApi {
  constructor() {
    this.email = 'you@example.com';
    this.roomsData = [
      { id: 1, name: '오늘의 라운지', description: '소소한 일상, 편하게 나누는 이야기', ownerUsername: 'hello@example.com', memberCount: 5, onlineCount: 3 },
      { id: 2, name: '퇴근 후 책 한 권', description: '책 속에서 만난 문장과 생각들', ownerUsername: 'you@example.com', memberCount: 4, onlineCount: 2 },
      { id: 3, name: '같이 만드는 사이드 프로젝트', description: '작은 아이디어를 함께 현실로', ownerUsername: 'dev@example.com', memberCount: 7, onlineCount: 4 },
      { id: 4, name: '주말의 발견', description: '산책, 전시, 그리고 새로운 장소', ownerUsername: 'walk@example.com', memberCount: 3, onlineCount: 0 }
    ].map(room => ({ ...room, createdAt: '2026-10-01T10:00:00' }));
    this.nextId = 100; this.nextRoomId = 5; this.joined = new Set();
    this.membersData = [
      { userId: 1, email: 'hello@example.com', online: true },
      { userId: 2, email: this.email, online: true },
      { userId: 3, email: 'spring@example.com', online: true },
      { userId: 4, email: 'blue@example.com', online: false },
      { userId: 5, email: 'slow@example.com', online: false }
    ];
    this.messagesData = new Map(this.roomsData.map(room => [room.id, [
      { id: room.id * 10, roomId: room.id, type: 'TALK', senderEmail: 'hello@example.com', content: '안녕하세요! 오늘도 반가워요 👋', createdAt: this.time(-18) },
      { id: room.id * 10 + 1, roomId: room.id, type: 'TALK', senderEmail: 'spring@example.com', content: room.id === 2 ? '요즘 읽고 있는 책 있으세요? 저는 오늘 마음에 드는 문장을 발견했어요.' : '점심시간에 잠깐 산책했는데 날씨가 정말 좋더라고요.', createdAt: this.time(-15) },
      { id: room.id * 10 + 2, roomId: room.id, type: 'TALK', senderEmail: this.email, content: '좋네요! 이렇게 이야기 나누니까 기분 좋은 하루가 될 것 같아요.', createdAt: this.time(-12) },
      { id: room.id * 10 + 3, roomId: room.id, type: 'TALK', senderEmail: 'hello@example.com', content: '맞아요. 작은 이야기도 함께 나누면 더 즐겁죠 ☕', createdAt: this.time(-10) }
    ]]));
  }
  time(minutes = 0) { const time = new Date(Date.now() + minutes * 60000); return new Date(time.getTime() - time.getTimezoneOffset() * 60000).toISOString().slice(0, 23); }
  async me() { return this.email; }
  async rooms() { return { content: this.roomsData.map(room => ({ ...room })), page: { number: 0, totalPages: 1, totalElements: this.roomsData.length, size: 20 } }; }
  async room(id) { const room = this.roomsData.find(room => room.id === id); if (!room) throw new ApiError('ROOM_NOT_FOUND'); return { ...room }; }
  async create(body) {
    if (this.roomsData.some(room => room.name === body.name)) throw new ApiError('DUPLICATE_ROOM_NAME');
    const room = { ...body, id: this.nextRoomId++, ownerUsername: this.email, memberCount: 1, onlineCount: 1, createdAt: this.time() };
    this.roomsData.unshift(room); this.messagesData.set(room.id, []); this.joined.add(room.id); return { ...room };
  }
  async join(id) { const room = await this.room(id); this.joined.add(id); return room; }
  async leave(id) { await this.room(id); this.joined.delete(id); }
  async delete(id) {
    if ((await this.room(id)).ownerUsername !== this.email) throw new ApiError('ACCESS_DENIED');
    this.roomsData = this.roomsData.filter(room => room.id !== id); this.messagesData.delete(id);
  }
  async members(id) { const room = await this.room(id); return this.membersData.slice(0, Math.min(room.memberCount, 5)).map(member => ({ ...member, joinAt: room.createdAt })); }
  async history(id, before = null, size = 50) {
    await this.room(id);
    const all = (this.messagesData.get(id) || []).filter(message => before === null || message.id < before);
    const messages = all.slice(-size); return { messages, hasMore: all.length > size, nextBefore: messages[0]?.id ?? null };
  }
  send(id, content) {
    const message = { id: this.nextId++, roomId: id, type: 'TALK', senderEmail: this.email, content, createdAt: this.time() };
    this.messagesData.get(id).push(message); return message;
  }
}
export class DemoTransport {
  constructor(api, callbacks) { this.api = api; this.callbacks = callbacks; this.client = { connected: false }; }
  activate() { this.client.connected = true; queueMicrotask(() => { if (this.client.connected) this.callbacks.connected(); }); }
  selectRoom(id) { this.roomId = id; }
  send(id, content) { const message = this.api.send(id, content); queueMicrotask(() => { if (this.client.connected) this.callbacks.message(message); }); }
  async deactivate() { this.client.connected = false; }
}
