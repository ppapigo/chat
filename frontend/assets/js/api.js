export class ApiError extends Error {
  constructor(code, message, status = 0, errors = []) {
    super(message); this.name = 'ApiError'; this.code = code; this.status = status; this.errors = errors;
  }
}
export class ChatApi {
  constructor(token, fetcher = globalThis.fetch.bind(globalThis)) { this.token = token; this.fetcher = fetcher; }
  async request(path, { method = 'GET', body, signal } = {}) {
    let response;
    try {
      response = await this.fetcher(`/api/chat${path}`, {
        method, signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000), cache: 'no-store',
        headers: { Authorization: `Bearer ${this.token}`, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
        ...(body === undefined ? {} : { body: JSON.stringify(body) })
      });
    } catch (error) {
      if (error.name === 'AbortError') throw error;
      throw new ApiError('NETWORK_ERROR', '서버에 연결할 수 없습니다. 네트워크와 서버 상태를 확인해 주세요.');
    }
    if (response.status === 204) return null;
    const text = await response.text();
    let data = text;
    if (response.headers.get('content-type')?.includes('json') && text) {
      try { data = JSON.parse(text); } catch { throw new ApiError('INVALID_RESPONSE', '서버 응답을 읽을 수 없습니다.', response.status); }
    }
    if (!response.ok) {
      throw new ApiError(data?.code || (response.status === 401 ? 'LOGIN_REQUIRED' : 'HTTP_ERROR'),
        data?.message || `요청에 실패했습니다 (${response.status}).`, response.status, data?.errors || []);
    }
    return data;
  }
  me(signal) { return this.request('/me', { signal }); }
  rooms(page = 0) { return this.request(`/rooms?page=${page}&size=20`); }
  create(body) { return this.request('/rooms', { method: 'POST', body }); }
  room(id) { return this.request(`/rooms/${id}`); }
  join(id) { return this.request(`/rooms/${id}/join`, { method: 'POST' }); }
  leave(id) { return this.request(`/rooms/${id}/leave`, { method: 'DELETE' }); }
  delete(id) { return this.request(`/rooms/${id}`, { method: 'DELETE' }); }
  members(id) { return this.request(`/rooms/${id}/members`); }
  history(id, before = null, size = 50) {
    const params = new URLSearchParams({ size: String(size) });
    if (before !== null) params.set('before', String(before));
    return this.request(`/rooms/${id}/messages?${params}`);
  }
}
