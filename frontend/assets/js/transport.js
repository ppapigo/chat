import { websocketUrl } from './core.js';
export class ChatTransport {
  constructor(token, callbacks, Client = globalThis.StompJs?.Client) {
    if (!Client) throw new Error('실시간 연결 모듈을 불러오지 못했습니다. 페이지를 새로고침해 주세요.');
    this.callbacks = callbacks;
    this.client = new Client({
      brokerURL: websocketUrl(globalThis.location),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000, connectionTimeout: 10000,
      heartbeatIncoming: 0, heartbeatOutgoing: 10000,
      debug: () => {},
      onConnect: () => {
        this.roomSubscription = null;
        this.client.subscribe('/topic/rooms', frame => this.deliver(frame, callbacks.roomEvent));
        this.client.subscribe('/user/queue/errors', frame => this.deliver(frame, callbacks.error));
        if (this.roomId) this.subscribeRoom(this.roomId);
        callbacks.connected();
      },
      onWebSocketClose: () => { if (!this.stopped) callbacks.disconnected(); },
      onStompError: frame => {
        const code = frame.headers.code || 'INTERNAL_SERVER_ERROR';
        const error = { code, message: frame.headers.message };
        this.stopped = true;
        void this.client.deactivate({ force: true });
        callbacks.error(error);
      }
    });
  }
  deliver(frame, callback) {
    let value;
    try { value = JSON.parse(frame.body); } catch { this.callbacks.error({ message: '실시간 응답을 읽을 수 없습니다.' }); return; }
    callback(value);
  }
  activate() { this.stopped = false; this.client.activate(); }
  selectRoom(id) {
    if (this.roomSubscription && this.client.connected) this.roomSubscription.unsubscribe();
    this.roomSubscription = null; this.roomId = id;
    if (id && this.client.connected) this.subscribeRoom(id);
  }
  subscribeRoom(id) {
    this.roomSubscription = this.client.subscribe(`/topic/room/${id}`, frame => this.deliver(frame, this.callbacks.message));
  }
  send(id, content) {
    if (!this.client.connected) throw new Error('실시간 연결을 기다려 주세요.');
    this.client.publish({ destination: `/app/room/${id}/message`, body: JSON.stringify({ content }), headers: { 'content-type': 'application/json' } });
  }
  async deactivate() {
    this.stopped = true; this.roomId = null; this.roomSubscription = null;
    await this.client.deactivate({ force: true });
    this.client.connectHeaders = {};
  }
}
