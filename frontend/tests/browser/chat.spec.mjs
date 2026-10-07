import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

test('demo supports messages, IME, room creation and owner-only deletion', async ({ page }, testInfo) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.goto('/'); await expect(page.getByText('우리, 이야기할까요?')).toBeVisible();
  await mkdir('.cache/screenshots', { recursive: true });
  await page.screenshot({ path: `.cache/screenshots/welcome-${testInfo.project.name}.png` });
  await page.getByRole('button', { name: '먼저 화면 둘러보기' }).click();
  await expect(page.locator('#active-room-name')).toHaveText('오늘의 라운지');
  await expect(page.locator('#demo-banner')).toBeVisible();
  await page.screenshot({ path: `.cache/screenshots/demo-${testInfo.project.name}.png` });
  await expect(page.locator('#delete-room')).toBeHidden();
  const input = page.getByRole('textbox', { name: '메시지', exact: true });
  await input.fill('한글 입력 중');
  await input.dispatchEvent('keydown', { key: 'Enter', isComposing: true });
  await expect(page.locator('.message-bubble').filter({ hasText: '한글 입력 중' })).toHaveCount(0);
  await input.fill('<img src=x onerror=alert(1)>'); await input.press('Enter');
  await expect(page.locator('.message-bubble').filter({ hasText: '<img src=x onerror=alert(1)>' })).toHaveCount(1);
  await expect(page.locator('#message-list img')).toHaveCount(0); await expect(input).toBeEmpty();
  await page.getByRole('button', { name: '새 채팅방', exact: true }).click();
  await page.getByLabel('채팅방 이름').fill('테스트 이야기'); await page.getByLabel('소개').fill('브라우저 검증');
  await page.getByRole('button', { name: '채팅방 만들기', exact: true }).click();
  await expect(page.locator('#active-room-name')).toHaveText('테스트 이야기');
  if (testInfo.project.name === 'mobile') await page.getByRole('button', { name: '참여자 목록 열기' }).click();
  await page.getByRole('button', { name: '채팅방 삭제', exact: true }).click();
  await page.getByRole('button', { name: '삭제하기', exact: true }).click();
  await expect(page.locator('#chat-view')).toBeHidden();
  expect(errors).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('reconnect restores subscriptions and catches up more than one history page', async ({ page }) => {
  const room = { id: 1, name: '재연결 확인', description: '', ownerUsername: 'me@example.com', memberCount: 1, onlineCount: 1, createdAt: '2026-10-01T10:00:00' };
  let outage = false, socket, connections = 0, subscriptions = 0;
  const message = id => ({ id, roomId: 1, type: 'TALK', senderEmail: 'other@example.com', content: `이야기 ${id}`, createdAt: '2026-10-07T12:30:00' });
  await page.route('**/api/chat/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/me')) return route.fulfill({ contentType: 'text/plain', body: 'me@example.com' });
    if (url.pathname.endsWith('/members')) return route.fulfill({ json: [{ userId: 42, email: 'me@example.com', online: true }] });
    if (url.pathname.endsWith('/messages')) {
      const older = url.searchParams.has('before');
      return route.fulfill({ json: !outage ? { messages: [message(1)], hasMore: false, nextBefore: 1 } : { messages: Array.from({ length: 50 }, (_, index) => message(index + (older ? 1 : 51))), hasMore: !older, nextBefore: older ? 1 : 51 } });
    }
    if (url.pathname === '/api/chat/rooms') return route.fulfill({ json: { content: [room], page: { number: 0, totalPages: 1, totalElements: 1, size: 20 } } });
    return route.fulfill({ json: room });
  });
  await page.routeWebSocket('**/ws', route => {
    socket = route; connections++;
    route.onMessage(frame => {
      if (typeof frame !== 'string') return;
      if (frame.startsWith('CONNECT')) route.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0');
      if (frame.startsWith('SUBSCRIBE') && frame.includes('destination:/topic/room/1\n')) subscriptions++;
    });
  });
  await page.goto('/'); await page.locator('#welcome-connect').click(); await page.locator('#access-token').fill('fixture'); await page.locator('#connect-submit').click(); await page.getByRole('button', { name: '재연결 확인 채팅방 참여' }).click();
  await expect(page.locator('.message-bubble')).toHaveCount(1); await page.locator('#message-input').fill('아직 보내지 않은 내용');
  outage = true; socket.close({ code: 1011, reason: 'test outage' });
  await expect(page.locator('#message-input')).toBeDisabled();
  await expect(page.locator('.message-bubble')).toHaveCount(100, { timeout: 12000 });
  await expect(page.locator('#message-input')).toHaveValue('아직 보내지 않은 내용'); await expect(page.locator('#message-input')).toBeEnabled();
  expect(connections).toBe(2); expect(subscriptions).toBe(2);
});

test('real adapter attaches token and handles STOMP echo and auth failure', async ({ page }) => {
  const room = { id: 12, name: 'API 연동방', description: '실제 프로토콜 계약', ownerUsername: 'me@example.com', memberCount: 1, onlineCount: 1, createdAt: '2026-10-01T10:00:00' };
  const calls = []; let messageId = 2;
  await page.route('**/api/chat/**', async route => {
    const request = route.request(); calls.push({ url: request.url(), method: request.method(), token: request.headers().authorization });
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/me')) return route.fulfill({ contentType: 'text/plain', body: 'me@example.com' });
    if (path.endsWith('/members')) return route.fulfill({ json: [{ userId: 42, email: 'me@example.com', online: true }] });
    if (path.endsWith('/messages')) return route.fulfill({ json: { messages: [], hasMore: false, nextBefore: null } });
    if (path === '/api/chat/rooms') return route.fulfill({ json: { content: [room], page: { number: 0, totalPages: 1, totalElements: 1, size: 20 } } });
    return route.fulfill({ json: room });
  });
  await page.routeWebSocket('**/ws', socket => {
    let roomSubscription;
    socket.onMessage(message => {
      if (typeof message !== 'string') return;
      if (message.startsWith('CONNECT') || message.startsWith('STOMP')) { expect(message).toContain('Authorization:Bearer fixture-token'); socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0'); }
      if (message.startsWith('SUBSCRIBE') && message.includes('destination:/topic/room/12')) roomSubscription = /\nid:([^\n]+)/.exec(message)?.[1];
      if (message.startsWith('SEND')) { const content = JSON.parse(message.split('\n\n')[1].replace(/\0$/, '')).content; const body = JSON.stringify({ id: messageId++, roomId: 12, type: 'TALK', senderEmail: 'me@example.com', content, createdAt: '2026-10-07T12:30:00' }); socket.send(`MESSAGE\nsubscription:${roomSubscription}\nmessage-id:message-${messageId}\ndestination:/topic/room/12\n\n${body}\0`); }
    });
  });
  await page.goto('/'); await page.getByRole('button', { name: '계정 연결하기', exact: true }).click(); await page.getByLabel('액세스 토큰', { exact: true }).fill('Bearer fixture-token'); await page.getByRole('button', { name: '연결하기', exact: true }).click();
  await page.getByRole('button', { name: 'API 연동방 채팅방 참여' }).click(); await expect(page.locator('#message-input')).toBeEnabled();
  await page.locator('#message-input').fill('연동 확인'); await page.locator('#message-input').press('Enter'); await expect(page.locator('.message-bubble')).toHaveText('연동 확인');
  expect(calls.every(call => call.token === 'Bearer fixture-token')).toBe(true); expect(calls.some(call => call.url.endsWith('/join') && call.method === 'POST')).toBe(true);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  await page.getByRole('button', { name: '연결된 계정 보기' }).click(); await page.getByRole('button', { name: '연결 해제', exact: true }).click(); await expect(page.locator('#welcome')).toBeVisible();
  await page.route('**/api/chat/me', route => route.fulfill({ status: 401, json: { code: 'LOGIN_REQUIRED' } }));
  await page.getByRole('button', { name: '계정 연결하기', exact: true }).click(); await page.getByLabel('액세스 토큰', { exact: true }).fill('invalid'); await page.getByRole('button', { name: '연결하기', exact: true }).click(); await expect(page.locator('#connect-error')).toContainText('유효한 액세스 토큰');
});

test('closing the account dialog cancels an in-flight authentication request', async ({ page }) => {
  let release;
  const responseGate = new Promise(resolve => { release = resolve; });
  await page.route('**/api/chat/me', async route => { await responseGate; await route.fulfill({ contentType: 'text/plain', body: 'late@example.com' }); });
  await page.goto('/'); await page.locator('#welcome-connect').click(); await page.locator('#access-token').fill('fixture');
  await Promise.all([page.waitForRequest('**/api/chat/me'), page.locator('#connect-submit').click()]);
  const cancelled = page.waitForEvent('requestfailed', { predicate: request => request.url().endsWith('/api/chat/me') });
  await page.keyboard.press('Escape'); await cancelled; release();
  await expect(page.locator('#connect-submit')).toBeEnabled(); await expect(page.locator('#connect-dialog')).not.toBeVisible();
  await expect(page.locator('#account-button')).toHaveText('?'); await expect(page.locator('#connection-status')).toContainText('연결 전');
});

test('room refresh preserves already loaded pagination and search results', async ({ page }) => {
  const rooms = Array.from({ length: 40 }, (_, index) => ({ id: index + 1, name: `페이지 방 ${index + 1}`, description: '', ownerUsername: 'me@example.com', memberCount: 1, onlineCount: 0, createdAt: '2026-10-01T10:00:00' }));
  await page.route('**/api/chat/**', route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/me')) return route.fulfill({ contentType: 'text/plain', body: 'me@example.com' });
    const number = Number(url.searchParams.get('page') || 0);
    return route.fulfill({ json: { content: rooms.slice(number * 20, (number + 1) * 20), page: { number, size: 20, totalPages: 2, totalElements: 40 } } });
  });
  await page.routeWebSocket('**/ws', socket => socket.onMessage(frame => { if (typeof frame === 'string' && frame.startsWith('CONNECT')) socket.send('CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0'); }));
  await page.goto('/'); await page.locator('#welcome-connect').click(); await page.locator('#access-token').fill('fixture'); await page.locator('#connect-submit').click();
  await expect(page.locator('.room-card')).toHaveCount(20); await page.locator('#more-rooms').click(); await expect(page.locator('.room-card')).toHaveCount(40);
  await page.locator('#refresh-rooms').click(); await expect(page.locator('#refresh-rooms')).toBeEnabled(); await expect(page.locator('.room-card')).toHaveCount(40);
  await page.locator('#room-search').fill('페이지 방 40'); await expect(page.locator('.room-card')).toHaveCount(1);
  await page.locator('#refresh-rooms').click(); await expect(page.locator('#refresh-rooms')).toBeEnabled(); await expect(page.locator('.room-card')).toHaveCount(1);
  await expect(page.locator('.room-card-name')).toHaveText('페이지 방 40');
});
