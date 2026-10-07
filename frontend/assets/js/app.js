import { ChatApi } from './api.js';
import { ChatTransport } from './transport.js';
import { DemoApi, DemoTransport } from './demo.js';
import { normalizeToken, mergeMessages, filterRooms, displayName, avatarColor, validateMessage, errorText } from './core.js';

const $ = id => document.getElementById(id);
const state = { api: null, transport: null, email: '', demo: false, connected: false, rooms: [], page: 0, totalPages: 0, totalRooms: 0, filter: 'all', active: null, messages: [], members: [], hasMore: false, before: null, loading: false, historyReady: false, drafts: new Map(), pending: null };
let session = 0, selection = 0, roomsRequest = 0, connectController, toastTimer, presenceTimer, confirmCallback;
const icon = name => { const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('class', 'icon'); svg.setAttribute('aria-hidden', 'true'); const use = document.createElementNS(svg.namespaceURI, 'use'); use.setAttribute('href', `#i-${name}`); svg.append(use); return svg; };
const el = (tag, className, text) => { const node = document.createElement(tag); if (className) node.className = className; if (text !== undefined) node.textContent = text; return node; };
const isCurrent = (epoch, roomId) => epoch === session && (roomId === undefined || state.active?.id === roomId);
const avatar = email => { const node = el('span', 'avatar', displayName(email).slice(0, 1).toUpperCase()); node.style.background = avatarColor(email); return node; };
const dateText = value => new Date(value).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' });
const timeText = value => new Date(value).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });

function toast(message, error = false) {
  clearTimeout(toastTimer); $('toast').textContent = message; $('toast').classList.toggle('error', error); $('toast').hidden = false;
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, error ? 6500 : 3500);
}
function formError(id, message = '') { $(id).textContent = message; $(id).hidden = !message; }
function roomError(message = '') { $('room-error').querySelector('span').textContent = message; $('room-error').hidden = !message; }
function setStatus(value) {
  const labels = { offline: '연결 전', connecting: '연결 중', connected: state.demo ? '둘러보기' : '실시간 연결', reconnecting: '재연결 중' };
  $('connection-status').className = `connection-status ${value}`;
  $('connection-status').lastElementChild.textContent = labels[value];
}
function renderAccount() {
  $('account-button').textContent = state.email ? displayName(state.email).slice(0, 1).toUpperCase() : '?';
  $('account-button').setAttribute('aria-label', state.email ? '연결된 계정 보기' : '계정 연결');
  $('footer-user').textContent = state.email ? `${state.demo ? '예시 계정 · ' : ''}${state.email}` : '당신의 이야기를 기다리고 있어요.';
  $('demo-banner').hidden = !state.demo; $('new-room').disabled = !state.api; $('refresh-rooms').disabled = !state.api;
  $('welcome-connect').textContent = state.email ? '채팅방 선택하기' : '계정 연결하기'; $('welcome-connect').append(icon('arrow'));
  $('start-demo').hidden = Boolean(state.email);
}
function renderRooms() {
  $('room-count').textContent = state.totalRooms;
  const rooms = filterRooms(state.rooms, $('room-search').value, state.filter);
  $('rooms-notice').hidden = rooms.length > 0;
  $('rooms-notice').textContent = !state.api ? '계정을 연결하면 채팅방을 볼 수 있어요.' : state.rooms.length ? '조건에 맞는 채팅방이 없어요.' : '아직 채팅방이 없어요. 첫 방을 만들어 보세요.';
  const fragment = document.createDocumentFragment();
  for (const room of rooms) {
    const button = el('button', `room-card${state.active?.id === room.id ? ' selected' : ''}`);
    button.setAttribute('aria-label', `${room.name} 채팅방 참여`); button.setAttribute('aria-pressed', String(state.active?.id === room.id)); button.disabled = state.loading;
    button.append(el('span', 'room-avatar', '#'));
    const copy = el('span', 'room-card-copy'); copy.append(el('span', 'room-card-name', room.name), el('span', 'room-card-description', room.description || '함께 이야기를 나누는 공간'));
    const meta = el('span', 'room-card-meta'); meta.append(icon('users'), el('span', '', `${room.memberCount}명`));
    if (room.onlineCount > 0) { const online = el('span', 'online-label'); online.append(el('span', 'status-dot'), el('span', '', `${room.onlineCount}명 온라인`)); meta.append(online); }
    copy.append(meta); button.append(copy); button.addEventListener('click', () => void enterRoom(room)); fragment.append(button);
  }
  $('room-list').replaceChildren(fragment); $('more-rooms').hidden = !state.api || state.page + 1 >= state.totalPages;
}
function renderActive() {
  const active = state.active; $('welcome').hidden = Boolean(active); $('chat-view').hidden = !active; $('members-placeholder').hidden = Boolean(active); $('members-view').hidden = !active;
  if (!active) { $('members-panel').classList.remove('open'); return; }
  $('active-room-name').textContent = active.name; $('active-room-description').textContent = active.description || '함께 이야기를 나누는 공간';
  $('room-owner').textContent = active.ownerUsername; $('room-created').textContent = dateText(active.createdAt);
  $('delete-room').hidden = active.ownerUsername !== state.email;
}
function renderMembers() {
  const fragment = document.createDocumentFragment();
  for (const member of [...state.members].sort((a, b) => Number(b.online) - Number(a.online))) {
    const row = el('div', 'member'), wrap = el('span', 'member-avatar-wrap'); wrap.append(avatar(member.email)); if (member.online) wrap.append(el('span', 'member-online'));
    const copy = el('div', 'member-copy'), name = el('span', 'member-name', displayName(member.email)); name.title = member.email;
    if (member.email === state.email) name.append(el('span', 'my-badge', '나'));
    copy.append(name, el('span', 'member-state', member.online ? '지금 온라인' : '오프라인')); row.append(wrap, copy); fragment.append(row);
  }
  $('member-list').replaceChildren(fragment); $('member-count').textContent = state.members.length;
  $('online-summary').textContent = `${state.members.filter(member => member.online).length}명이 지금 함께하고 있어요`;
}
function renderMessages() {
  const fragment = document.createDocumentFragment(); let day = '';
  for (const message of state.messages) {
    const currentDay = message.createdAt?.slice(0, 10);
    if (currentDay && day !== currentDay) { fragment.append(el('div', 'date-divider', dateText(message.createdAt))); day = currentDay; }
    if (message.type !== 'TALK') { fragment.append(el('div', 'system-message', `${displayName(message.senderEmail)}님이 ${message.type === 'ENTER' ? '들어왔어요.' : '자리를 비웠어요.'}`)); continue; }
    const mine = message.senderEmail === state.email;
    const row = el('article', `message${mine ? ' mine' : ''}`); row.dataset.messageId = message.id;
    const content = el('div', 'message-content'), author = el('div', 'message-author'); author.append(el('span', '', mine ? '나' : displayName(message.senderEmail)));
    const time = el('time', '', timeText(message.createdAt)); time.dateTime = message.createdAt; author.append(time);
    content.append(author, el('div', 'message-bubble', message.content)); row.append(avatar(message.senderEmail), content); fragment.append(row);
  }
  $('message-list').replaceChildren(fragment); $('empty-messages').hidden = state.messages.length > 0 || !state.historyReady;
  $('older-messages').hidden = !state.hasMore; $('older-messages').disabled = state.loading;
}
function updateComposer() {
  const input = $('message-input'); input.disabled = !state.active || !state.connected || !state.historyReady || state.loading;
  $('send-message').disabled = input.disabled || !input.value.trim() || Boolean(state.pending);
  $('message-counter').textContent = `${input.value.length} / 1000`;
  $('composer-hint').textContent = state.pending ? '전송 확인 중…' : !state.connected && state.active ? '실시간 연결 후 메시지를 보낼 수 있어요.' : 'Enter로 전송 · Shift + Enter로 줄바꿈';
  input.style.height = 'auto'; input.style.height = `${Math.min(120, Math.max(35, input.scrollHeight))}px`;
}
function scrollBottom() { const scroll = $('message-scroll'); scroll.scrollTop = scroll.scrollHeight; $('new-messages').hidden = true; }
function clearPending() { if (state.pending) clearTimeout(state.pending.timer); state.pending = null; updateComposer(); }
function acknowledge(message) {
  const pending = state.pending;
  if (pending && message.roomId === pending.roomId && message.senderEmail === state.email && message.content === pending.content && message.type === 'TALK') {
    if ($('message-input').value.trim() === pending.content && state.active?.id === pending.roomId) { $('message-input').value = ''; state.drafts.delete(pending.roomId); }
    clearPending();
  }
}
async function handleError(error) {
  if (error.name === 'AbortError') return;
  if (error.code === 'LOGIN_REQUIRED' || error.code === 'TOKEN_EXPIRED' || error.status === 401) {
    await reset(); formError('connect-error', errorText(error)); $('connect-dialog').showModal();
  } else { toast(errorText(error), true); }
}
async function loadRooms(append = false) {
  const epoch = session, api = state.api; if (!api) return;
  const requestId = ++roomsRequest, targetPage = append ? state.page + 1 : state.page;
  const current = () => isCurrent(epoch) && requestId === roomsRequest;
  $('refresh-rooms').disabled = true; $('more-rooms').disabled = true;
  try {
    const response = await api.rooms(append ? targetPage : 0); if (!current()) return;
    const lastPage = append ? response.page.number : Math.min(targetPage, Math.max(0, response.page.totalPages - 1));
    const extra = append ? [] : await Promise.all(Array.from({ length: lastPage }, (_, index) => api.rooms(index + 1)));
    if (!current()) return;
    const rooms = append ? [...state.rooms, ...response.content] : [response, ...extra].flatMap(page => page.content);
    state.rooms = [...new Map(rooms.map(room => [room.id, room])).values()];
    state.page = lastPage; state.totalPages = response.page.totalPages; state.totalRooms = response.page.totalElements; renderRooms();
  } catch (error) { if (current()) await handleError(error); }
  finally { if (current()) { $('refresh-rooms').disabled = !state.api; $('more-rooms').disabled = false; } }
}
async function refreshMembers() {
  const epoch = session, id = state.active?.id, api = state.api; if (!id || !api) return;
  try { const [members, room] = await Promise.all([api.members(id), api.room(id)]); if (!isCurrent(epoch, id)) return; state.members = members; state.active = room; state.rooms = state.rooms.map(item => item.id === id ? room : item); renderMembers(); renderActive(); renderRooms(); }
  catch (error) { if (isCurrent(epoch, id)) await handleError(error); }
}
function schedulePresenceRefresh() { clearTimeout(presenceTimer); presenceTimer = setTimeout(() => void refreshMembers(), 250); }
async function loadHistory(older = false, catchUp = false) {
  const epoch = session, id = state.active?.id, api = state.api; if (!id || !api) return;
  const selectionId = selection; const scroll = $('message-scroll'), height = scroll.scrollHeight, top = scroll.scrollTop;
  state.loading = true; updateComposer(); $('older-messages').disabled = true; roomError();
  try {
    const newest = state.messages.at(-1)?.id;
    let response = await api.history(id, older ? state.before : null), incoming = response.messages;
    // Recover every missed page after reconnect, even when more than 50 messages arrived.
    if (catchUp && newest) {
      let cursor = response;
      while (cursor.hasMore && cursor.nextBefore > newest) {
        cursor = await api.history(id, cursor.nextBefore); incoming = [...cursor.messages, ...incoming];
        if (!isCurrent(epoch, id) || selectionId !== selection) return;
      }
    }
    if (!isCurrent(epoch, id) || selectionId !== selection) return;
    for (const message of incoming) acknowledge(message);
    const firstLoad = !state.historyReady; state.messages = mergeMessages(state.messages, incoming);
    if (older || firstLoad) { state.hasMore = response.hasMore; state.before = response.nextBefore; }
    state.historyReady = true; renderMessages();
    if (older) scroll.scrollTop = top + scroll.scrollHeight - height;
    else if (firstLoad || top + scroll.clientHeight >= height - 80) scrollBottom();
    else $('new-messages').hidden = false;
  } catch (error) { if (isCurrent(epoch, id) && selectionId === selection) { roomError(errorText(error)); await handleError(error); } }
  finally { if (isCurrent(epoch, id) && selectionId === selection) { state.loading = false; $('older-messages').disabled = false; renderRooms(); updateComposer(); } }
}
async function enterRoom(room) {
  if (state.loading || !state.api) return;
  if (state.active?.id === room.id) { $('rooms-panel').classList.remove('open'); return; }
  const epoch = session, api = state.api; state.loading = true; updateComposer(); renderRooms();
  try {
    const joined = await api.join(room.id); if (!isCurrent(epoch)) return;
    if (state.active) state.drafts.set(state.active.id, $('message-input').value);
    selection++; clearPending(); state.active = joined; state.messages = []; state.members = []; state.historyReady = false; state.hasMore = false; state.before = null;
    $('message-input').value = state.drafts.get(room.id) || ''; $('new-messages').hidden = true; roomError();
    state.transport.selectRoom(room.id); renderActive(); renderMembers(); renderMessages(); renderRooms(); $('rooms-panel').classList.remove('open');
    await Promise.all([loadHistory(), refreshMembers()]);
  } catch (error) { if (isCurrent(epoch)) await handleError(error); }
  finally { if (isCurrent(epoch)) { state.loading = false; renderRooms(); updateComposer(); } }
}
function clearActive() {
  selection++; clearPending(); state.transport?.selectRoom(null); state.active = null; state.messages = []; state.members = []; state.historyReady = false; state.loading = false;
  $('message-input').value = ''; roomError(); renderActive(); renderRooms(); updateComposer();
}
function callbacks(epoch) {
  return {
    connected: () => {
      if (!isCurrent(epoch)) return; const reconnect = state.connectedOnce; state.connected = true; state.connectedOnce = true; setStatus('connected'); updateComposer();
      if (!state.demo) void loadRooms();
      if (state.active) { void loadHistory(false, reconnect); void refreshMembers(); }
    },
    disconnected: () => { if (isCurrent(epoch)) { state.connected = false; setStatus('reconnecting'); clearPending(); } },
    error: error => { if (isCurrent(epoch)) { if (state.transport?.stopped) { state.connected = false; setStatus('offline'); } clearPending(); void handleError(error); } },
    message: message => {
      if (!isCurrent(epoch, message.roomId)) return; acknowledge(message); const scroll = $('message-scroll'), nearBottom = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 90;
      state.messages = mergeMessages(state.messages, [message]); renderMessages(); if (nearBottom || message.senderEmail === state.email) scrollBottom(); else $('new-messages').hidden = false;
      if (message.type !== 'TALK') schedulePresenceRefresh();
    },
    roomEvent: event => {
      if (!isCurrent(epoch)) return;
      if (event.type === 'ROOM_DELETED') {
        state.rooms = state.rooms.filter(room => room.id !== event.room.id);
        if (state.active?.id === event.room.id) { clearActive(); toast('채팅방이 삭제되었습니다.'); }
        void loadRooms();
      } else if (event.type === 'ROOM_CREATED') { void loadRooms(); }
      else { state.rooms = state.rooms.map(room => room.id === event.room.id ? event.room : room); if (state.active?.id === event.room.id) { state.active = event.room; renderActive(); schedulePresenceRefresh(); } }
      renderRooms();
    }
  };
}
async function reset() {
  session++; selection++; roomsRequest++; clearTimeout(presenceTimer); clearPending(); const transport = state.transport;
  Object.assign(state, { api: null, transport: null, email: '', demo: false, connected: false, connectedOnce: false, rooms: [], active: null, messages: [], members: [], totalRooms: 0, totalPages: 0, page: 0, loading: false, historyReady: false });
  state.drafts.clear(); $('message-input').value = ''; $('access-token').value = ''; $('room-search').value = '';
  document.querySelectorAll('dialog[open]').forEach(dialog => dialog.close()); roomError(); renderAccount(); renderRooms(); renderActive(); updateComposer(); setStatus('offline');
  if (transport) await transport.deactivate();
}
function showConnect() { formError('connect-error'); $('connect-dialog').showModal(); }
$('connect-form').addEventListener('submit', async event => {
  event.preventDefault(); const token = normalizeToken($('access-token').value); if (!token) { formError('connect-error', '액세스 토큰을 입력해 주세요.'); return; }
  connectController?.abort(); const controller = new AbortController(); connectController = controller;
  const epoch = session; $('connect-submit').disabled = true; formError('connect-error');
  try {
    const api = new ChatApi(token); const email = await api.me(controller.signal);
    if (!isCurrent(epoch) || controller.signal.aborted || !$('connect-dialog').open) return;
    const newEpoch = session + 1, transport = new ChatTransport(token, callbacks(newEpoch));
    await reset(); if (!isCurrent(newEpoch)) { await transport.deactivate(); return; }
    state.api = api; state.email = email; state.transport = transport; renderAccount(); setStatus('connecting'); state.transport.activate();
    toast('계정을 연결했습니다. 채팅방을 선택해 주세요.'); $('rooms-panel').classList.add('open');
  } catch (error) { if (controller.signal.aborted || !isCurrent(epoch)) return; if (!$('connect-dialog').open) $('connect-dialog').showModal(); formError('connect-error', errorText(error)); }
  finally { if (connectController === controller) { connectController = null; $('connect-submit').disabled = false; } }
});
$('connect-dialog').addEventListener('close', () => { connectController?.abort(); $('access-token').value = ''; });
$('start-demo').addEventListener('click', async () => {
  await reset(); state.demo = true; state.api = new DemoApi(); state.email = await state.api.me(); state.transport = new DemoTransport(state.api, callbacks(session));
  renderAccount(); state.transport.activate(); await loadRooms(); if (state.rooms[0]) await enterRoom(state.rooms[0]);
});
$('exit-demo').addEventListener('click', async () => { await reset(); showConnect(); });
$('welcome-connect').addEventListener('click', () => { if (state.email) $('rooms-panel').classList.add('open'); else showConnect(); });
$('account-button').addEventListener('click', () => { if (state.email) { $('account-email').textContent = state.email; $('account-dialog').showModal(); } else showConnect(); });
$('disconnect').addEventListener('click', () => void reset());
$('new-room').addEventListener('click', () => { $('create-form').reset(); formError('create-error'); $('create-dialog').showModal(); });
$('create-form').addEventListener('submit', async event => {
  event.preventDefault(); const name = $('room-name').value.trim(), description = $('room-description').value.trim();
  if (!name) { formError('create-error', '채팅방 이름을 입력해 주세요.'); return; }
  const epoch = session, api = state.api; $('create-submit').disabled = true; formError('create-error');
  try { const room = await api.create({ name, description }); if (!isCurrent(epoch)) return; $('create-dialog').close(); await loadRooms(); await enterRoom(room); toast('새 채팅방을 만들었어요.'); }
  catch (error) { if (isCurrent(epoch)) { formError('create-error', errorText(error)); if (error.status === 401) await handleError(error); } }
  finally { $('create-submit').disabled = false; }
});
$('message-input').addEventListener('input', () => { if (state.active) state.drafts.set(state.active.id, $('message-input').value); updateComposer(); });
$('message-input').addEventListener('keydown', event => { if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) { event.preventDefault(); if (!$('send-message').disabled) $('message-form').requestSubmit(); } });
$('message-form').addEventListener('submit', event => {
  event.preventDefault(); if ($('send-message').disabled || !state.active) return;
  try {
    const content = validateMessage($('message-input').value), roomId = state.active.id;
    state.pending = { roomId, content, timer: setTimeout(() => { clearPending(); toast('전송을 확인하지 못했습니다. 대화 기록을 확인한 뒤 다시 시도해 주세요.', true); void loadHistory(false, true); }, 10000) };
    updateComposer(); state.transport.send(roomId, content);
  } catch (error) { clearPending(); toast(errorText(error), true); }
});
$('older-messages').addEventListener('click', () => { if (!state.loading) void loadHistory(true); });
$('retry-room').addEventListener('click', () => { if (!state.loading) { void loadHistory(); void refreshMembers(); } });
$('new-messages').addEventListener('click', scrollBottom);
$('message-scroll').addEventListener('scroll', () => { const scroll = $('message-scroll'); if (scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 70) $('new-messages').hidden = true; });
$('refresh-rooms').addEventListener('click', () => void loadRooms()); $('more-rooms').addEventListener('click', () => void loadRooms(true)); $('room-search').addEventListener('input', renderRooms);
document.querySelectorAll('[data-filter]').forEach(button => button.addEventListener('click', () => { state.filter = button.dataset.filter; document.querySelectorAll('[data-filter]').forEach(item => { const selected = item === button; item.classList.toggle('selected', selected); item.setAttribute('aria-pressed', String(selected)); }); renderRooms(); }));
for (const id of ['mobile-rooms', 'show-rooms']) $(id).addEventListener('click', () => $('rooms-panel').classList.toggle('open'));
$('close-rooms').addEventListener('click', () => $('rooms-panel').classList.remove('open'));
function toggleMembers(open) { $('members-panel').classList.toggle('open', open); $('toggle-members').setAttribute('aria-expanded', String(open)); }
$('toggle-members').addEventListener('click', () => toggleMembers(!$('members-panel').classList.contains('open'))); $('close-members').addEventListener('click', () => toggleMembers(false));
document.querySelectorAll('[data-close]').forEach(button => button.addEventListener('click', () => $(button.dataset.close).close()));
document.querySelectorAll('dialog').forEach(dialog => dialog.addEventListener('click', event => { if (event.target === dialog) { const rect = dialog.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close(); } }));
function confirm(title, description, action, callback) { $('confirm-title').textContent = title; $('confirm-description').textContent = description; $('confirm-action').textContent = action; confirmCallback = callback; $('confirm-dialog').showModal(); }
$('leave-room').addEventListener('click', () => confirm('채팅방을 나갈까요?', '참여자 목록에서 제외됩니다. 나중에 다시 참여해 대화 기록을 볼 수 있어요.', '나가기', async () => { const epoch = session, id = state.active.id; state.transport.selectRoom(null); try { await state.api.leave(id); if (isCurrent(epoch, id)) { state.drafts.delete(id); clearActive(); await loadRooms(); toast('채팅방에서 나왔어요.'); } } catch (error) { if (isCurrent(epoch, id)) state.transport.selectRoom(id); throw error; } }));
$('delete-room').addEventListener('click', () => confirm('채팅방을 삭제할까요?', '모든 참여자의 대화 기록과 채팅방이 영구 삭제됩니다. 이 작업은 되돌릴 수 없어요.', '삭제하기', async () => { const epoch = session, id = state.active.id; await state.api.delete(id); if (isCurrent(epoch)) { if (state.active?.id === id) clearActive(); await loadRooms(); toast('채팅방을 삭제했어요.'); } }));
$('confirm-action').addEventListener('click', async () => { $('confirm-action').disabled = true; try { await confirmCallback?.(); $('confirm-dialog').close(); } catch (error) { await handleError(error); } finally { $('confirm-action').disabled = false; } });
window.addEventListener('pagehide', () => { void state.transport?.deactivate(); });
renderAccount(); renderRooms(); renderActive(); updateComposer();
