/**
 * Replaces client/src/utils/api.js (Axios instance) with a plain Fetch
 * wrapper, per migration spec section 13. Every call attaches the JWT
 * bearer token from localStorage and throws a normalized Error with
 * `.status` and `.data` on non-2xx responses, so calling code can do
 * `catch (err) { toast(err.message) }` just like the old `err.response.data.message`.
 */
// const API_BASE = window.MAILFLOW_API_BASE || '/api';

const API_BASE = 'http://localhost:5000/api';

function authHeaders(extra = {}) {
  const token = localStorage.getItem('mf_token');
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extra,
  };
}

async function handle(response) {
  let data = null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    data = await response.json().catch(() => null);
  }
  if (!response.ok) {
    const err = new Error((data && data.message) || `Request failed (${response.status})`);
    err.status = response.status;
    err.data = data;
    if (response.status === 401) {
      // Mirrors the old Axios interceptor: expired/invalid token -> force re-login.
      localStorage.removeItem('mf_token');
      localStorage.removeItem('mf_user');
      if (!location.pathname.endsWith('login.html')) {
        location.href = 'login.html';
      }
    }
    throw err;
  }
  return data;
}

const api = {
  get(path, params) {
    const url = new URL(API_BASE + path, location.origin);
    if (params) Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
    });
    return fetch(url, { headers: authHeaders() }).then(handle);
  },

  post(path, body) {
    return fetch(API_BASE + path, {
      method: 'POST',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body || {}),
    }).then(handle);
  },

  put(path, body) {
    return fetch(API_BASE + path, {
      method: 'PUT',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify(body || {}),
    }).then(handle);
  },

  patch(path, body) {
    return fetch(API_BASE + path, {
      method: 'PATCH',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: body ? JSON.stringify(body) : undefined,
    }).then(handle);
  },

  delete(path, body) {
    return fetch(API_BASE + path, {
      method: 'DELETE',
      headers: authHeaders({ 'Content-Type': 'application/json' }),
      body: body ? JSON.stringify(body) : undefined,
    }).then(handle);
  },

  /** multipart/form-data (attachments, avatar) — pass a FormData instance. */
  postForm(path, formData) {
    return fetch(API_BASE + path, {
      method: 'POST',
      headers: authHeaders(), // no Content-Type: browser sets the multipart boundary
      body: formData,
    }).then(handle);
  },

  /** Downloads a binary/file response (attachment download). Returns a Blob + filename. */
  async download(path) {
    const response = await fetch(API_BASE + path, { headers: authHeaders() });
    if (!response.ok) throw new Error('Download failed');
    const disposition = response.headers.get('content-disposition') || '';
    const match = disposition.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
    const filename = match ? decodeURIComponent(match[1]) : 'download';
    const blob = await response.blob();
    return { blob, filename };
  },
};
