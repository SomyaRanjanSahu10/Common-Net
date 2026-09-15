/**
 * Replaces client/src/context/AuthContext.js. Since there's no React
 * context, every page calls these helpers directly on load.
 */
const Auth = {
  getToken() { return localStorage.getItem('mf_token'); },

  getUser() {
    const raw = localStorage.getItem('mf_user');
    return raw ? JSON.parse(raw) : null;
  },

  isLoggedIn() { return !!this.getToken(); },

  setSession(token, user) {
    localStorage.setItem('mf_token', token);
    localStorage.setItem('mf_user', JSON.stringify(user));
  },

  updateUser(user) {
    localStorage.setItem('mf_user', JSON.stringify(user));
  },

  logout() {
    localStorage.removeItem('mf_token');
    localStorage.removeItem('mf_user');
    location.href = 'login.html';
  },

  /** Call at the top of every protected page. Redirects to login if no session,
   *  then refreshes user info from /auth/me in the background (mirrors
   *  AuthContext's initial `checkAuth()` effect). */
  async requireAuth() {
    if (!this.isLoggedIn()) {
      location.href = 'login.html';
      return null;
    }
    try {
      const data = await api.get('/auth/me');
      this.updateUser(data.user);
      return data.user;
    } catch (e) {
      return this.getUser(); // fall back to cached user if /me fails transiently
    }
  },

  /** Call at the top of login/register/forgot-password pages to bounce
   *  already-authenticated users straight to the inbox. */
  redirectIfLoggedIn() {
    if (this.isLoggedIn()) location.href = 'inbox.html';
  },

  initials(name) {
    if (!name) return '?';
    return name.trim().split(/\s+/).slice(0, 2).map(w => w[0].toUpperCase()).join('');
  },
};
