import { test, expect } from '@playwright/test';
import { createHmac, randomUUID } from 'node:crypto';
import { mkdir } from 'node:fs/promises';

// Public, isolated test key matching BrowserIntegrationTests; never reads .env.
function token(email) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const payload = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: email, jti: randomUUID(), exp: Math.floor(Date.now() / 1000) + 600 })}`;
  return `${payload}.${createHmac('sha256', Buffer.from('MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=', 'base64')).update(payload).digest('base64url')}`;
}

test('two browsers share persisted messages, presence, rejoin history and deletion', async ({ browser, baseURL }, testInfo) => {
  const ownerContext = await browser.newContext(testInfo.project.use);
  const guestContext = await browser.newContext(testInfo.project.use);
  const owner = await ownerContext.newPage(), guest = await guestContext.newPage(), errors = [];
  for (const page of [owner, guest]) page.on('pageerror', error => errors.push(error.message));
  const ownerToken = token('owner@example.com'), guestToken = token('guest@example.com');
  const name = `브라우저 연동 ${randomUUID().slice(0, 8)}`;
  async function connect(page, accessToken) {
    await page.goto(baseURL); await page.locator('#welcome-connect').click();
    await page.locator('#access-token').fill(accessToken); await page.locator('#connect-submit').click();
    await expect(page.locator('#connection-status')).toContainText('실시간 연결');
  }
  async function members(page) {
    if (testInfo.project.name === 'mobile' && !await page.locator('#members-panel').isVisible()) await page.locator('#toggle-members').click();
  }
  async function roomList(page) {
    if (testInfo.project.name === 'mobile' && !await page.locator('#rooms-panel').isVisible()) await page.locator('#mobile-rooms').click();
  }
  try {
    await connect(owner, ownerToken); await connect(guest, guestToken);
    await owner.locator('#new-room').click(); await owner.locator('#room-name').fill(name); await owner.locator('#room-description').fill('실제 Spring Boot와 브라우저의 통합 검증'); await owner.locator('#create-submit').click();
    await expect(owner.locator('#active-room-name')).toHaveText(name);
    await roomList(guest); await guest.getByRole('button', { name: `${name} 채팅방 참여` }).click();
    await expect(guest.locator('#active-room-name')).toHaveText(name);
    await members(owner); await expect(owner.locator('#online-summary')).toContainText('2명이');
    await owner.locator('#message-input').fill('브라우저에서 백엔드로'); await owner.locator('#message-input').press('Enter');
    await expect(guest.locator('.message-bubble').filter({ hasText: '브라우저에서 백엔드로' })).toHaveCount(1);
    await expect(owner.locator('#message-input')).toBeEmpty();
    await guest.locator('#message-input').fill('실제 WebSocket 응답 확인'); await guest.locator('#message-input').press('Enter');
    await expect(owner.locator('.message-bubble').filter({ hasText: '실제 WebSocket 응답 확인' })).toHaveCount(1);
    await mkdir('.cache/screenshots', { recursive: true });
    await guest.screenshot({ path: `.cache/screenshots/integration-${testInfo.project.name}.png` });
    await guest.reload(); await expect(guest.locator('#welcome')).toBeVisible();
    await expect(owner.locator('#online-summary')).toContainText('1명이', { timeout: 10000 });
    await connect(guest, guestToken); await roomList(guest); await guest.getByRole('button', { name: `${name} 채팅방 참여` }).click();
    await expect(guest.locator('.message-bubble').filter({ hasText: '브라우저에서 백엔드로' })).toHaveCount(1);
    await expect(guest.locator('.message-bubble').filter({ hasText: '실제 WebSocket 응답 확인' })).toHaveCount(1);
    await members(guest); await expect(guest.locator('#delete-room')).toBeHidden();
    await guest.locator('#leave-room').click(); await guest.locator('#confirm-action').click(); await expect(guest.locator('#chat-view')).toBeHidden();
    await expect(owner.locator('#member-count')).toHaveText('1');
    await roomList(guest); await guest.getByRole('button', { name: `${name} 채팅방 참여` }).click();
    await expect(guest.locator('#active-room-name')).toHaveText(name);
    await owner.locator('#delete-room').click(); await owner.locator('#confirm-action').click();
    await expect(owner.locator('#chat-view')).toBeHidden(); await expect(guest.locator('#chat-view')).toBeHidden();
    expect(errors).toEqual([]);
  } finally { await guestContext.close(); await ownerContext.close(); }
});
