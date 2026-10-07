import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeToken, websocketUrl, mergeMessages, validateMessage, filterRooms } from '../assets/js/core.js';
import { ChatApi, ApiError } from '../assets/js/api.js';

test('history and live events merge once in server ID order', () => {
  assert.deepEqual(mergeMessages([{ id: 3, content: 'live' }], [{ id: 1 }, { id: 3, content: 'persisted' }, { id: 2 }]), [{ id: 1 }, { id: 2 }, { id: 3, content: 'persisted' }]);
});
test('normalizes pasted Bearer header and derives secure same-origin websocket', () => {
  assert.equal(normalizeToken('  Bearer abc.def.xyz  '), 'abc.def.xyz');
  assert.equal(websocketUrl({ protocol: 'https:', host: 'chat.example.com:8443' }), 'wss://chat.example.com:8443/ws');
});
test('enforces the Java UTF-16 message length limit, including emoji', () => {
  assert.throws(() => validateMessage('  ')); assert.throws(() => validateMessage('😀'.repeat(501)));
  assert.equal(validateMessage('  안녕하세요\n반가워요  '), '안녕하세요\n반가워요');
});
test('room search and online filter work together', () => {
  const rooms = [{ id: 1, name: '책 모임', description: null, onlineCount: 0 }, { id: 2, name: '독서', description: '책 이야기', onlineCount: 2 }];
  assert.deepEqual(filterRooms(rooms, '책', 'online').map(room => room.id), [2]);
});
test('REST uses Bearer auth, request contract, plain-text me and 204 leave', async () => {
  const requests = []; const api = new ChatApi('fixture', async (url, options) => { requests.push({ url, options }); return url.endsWith('/me') ? new Response('you@example.com') : new Response(null, { status: 204 }); });
  assert.equal(await api.me(), 'you@example.com'); assert.equal(await api.leave(8), null);
  assert.equal(requests[1].url, '/api/chat/rooms/8/leave'); assert.equal(requests[1].options.method, 'DELETE'); assert.equal(requests[0].options.headers.Authorization, 'Bearer fixture');
});
test('REST retains structured auth errors and cursor parameters', async () => {
  const api = new ChatApi('fixture', async (url) => { assert.equal(url, '/api/chat/rooms/1/messages?size=50&before=91'); return Response.json({ code: 'LOGIN_REQUIRED', message: '로그인이 필요합니다' }, { status: 401 }); });
  await assert.rejects(api.history(1, 91), error => error instanceof ApiError && error.code === 'LOGIN_REQUIRED' && error.status === 401);
});
