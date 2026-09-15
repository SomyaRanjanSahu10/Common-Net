/** Replaces client/src/pages/AdminDashboard.js. */
const AdminApp = {
  tab: 'dashboard',
  page: 1,
  pages: 1,
  search: '',

  async init() {
    document.querySelectorAll('.admin-nav-btn').forEach(btn => {
      btn.onclick = () => { this.tab = btn.dataset.tab; this.page = 1; this.setActiveNav(); this.load(); };
    });
    this.setActiveNav();
    await this.load();
  },

  setActiveNav() {
    document.querySelectorAll('.admin-nav-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === this.tab));
  },

  async load() {
    const content = document.getElementById('admin-content');
    content.innerHTML = `<div class="empty-state"><span class="spinner spinner-dark"></span></div>`;
    try {
      if (this.tab === 'dashboard') {
        const res = await api.get('/admin/dashboard');
        this.renderDashboard(res);
      } else if (this.tab === 'users') {
        const res = await api.get('/admin/users', { search: this.search, page: this.page, limit: 15 });
        this.pages = res.pages;
        this.renderUsers(res.users, res.total);
      } else if (this.tab === 'emails') {
        const res = await api.get('/admin/emails', { page: this.page, limit: 15 });
        this.pages = res.pages;
        this.renderEmails(res.emails);
      } else if (this.tab === 'logs') {
        const res = await api.get('/admin/logs', { page: this.page, limit: 20 });
        this.pages = res.pages;
        this.renderLogs(res.logs, 'Admin Action Logs');
      } else if (this.tab === 'activity') {
        const res = await api.get('/admin/activity', { page: this.page, limit: 20 });
        this.pages = res.pages;
        this.renderLogs(res.logs, 'User Activity');
      }
    } catch (err) {
      content.innerHTML = `<div class="empty-state">Failed to load: ${err.message || ''}</div>`;
    }
  },

  renderDashboard(data) {
    const s = data.stats;
    document.getElementById('admin-content').innerHTML = `
      <div class="stat-grid">
        <div class="stat-card"><div class="stat-value">${s.totalUsers}</div><div class="stat-label">Total Users</div></div>
        <div class="stat-card"><div class="stat-value">${s.activeUsers}</div><div class="stat-label">Active Users</div></div>
        <div class="stat-card"><div class="stat-value">${s.totalEmails}</div><div class="stat-label">Total Emails Sent</div></div>
        <div class="stat-card"><div class="stat-value">${s.sentToday}</div><div class="stat-label">Sent Today</div></div>
        <div class="stat-card"><div class="stat-value">${s.trashCount}</div><div class="stat-label">In Trash</div></div>
        <div class="stat-card"><div class="stat-value">${s.deletedCount}</div><div class="stat-label">Deleted</div></div>
      </div>
      <h3 style="margin-bottom:10px;">Recent Emails</h3>
      <table class="admin-table" style="margin-bottom:24px;">
        <thead><tr><th>Subject</th><th>From</th><th>To</th><th>Date</th><th>SMTP</th></tr></thead>
        <tbody>${(data.recentEmails || []).map(e => `
          <tr>
            <td>${esc(e.subject || '(No subject)')}${e.isRecalled ? ' <span style="color:var(--ms-danger);font-size:11px;">(recalled)</span>' : ''}</td>
            <td>${e.sender ? esc(e.sender.name) : ''}</td>
            <td>${e.receiver ? esc(e.receiver.name) : ''}</td>
            <td>${fmt(e.createdAt)}</td>
            <td>${e.smtpSent ? '\u2705' : '\u274C'}</td>
          </tr>`).join('')}
        </tbody>
      </table>`;
  },

  renderUsers(users, total) {
    const me = Auth.getUser();
    document.getElementById('admin-content').innerHTML = `
      <div style="display:flex;gap:10px;margin-bottom:14px;">
        <input class="form-input" id="admin-user-search" placeholder="Search users..." style="max-width:280px;" value="${esc(this.search)}">
        <span style="align-self:center;font-size:12px;color:var(--ms-text-muted);">${total} total</span>
      </div>
      <table class="admin-table">
        <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Joined</th><th>Actions</th></tr></thead>
        <tbody>${users.map(u => `
          <tr>
            <td>${esc(u.name)}</td>
            <td>${esc(u.email)}</td>
            <td><span class="role-badge ${u.role}">${u.role}</span></td>
            <td><span class="status-badge ${u.isActive ? 'active' : 'suspended'}">${u.isActive ? 'Active' : 'Suspended'}</span></td>
            <td>${fmt(u.createdAt)}</td>
            <td style="white-space:nowrap;">
              ${u.role !== 'admin' ? `<button class="btn-icon" data-toggle="${u.id}" title="${u.isActive ? 'Suspend' : 'Activate'}">${u.isActive ? '\u23F8' : '\u25B6\uFE0F'}</button>` : ''}
              ${u.role !== 'admin' ? `<button class="btn-icon" data-promote="${u.id}" title="Make admin"></button>` : ''}
              <button class="btn-icon" data-reset="${u.id}" data-name="${esc(u.name)}" title="Reset password"></button>
              ${u.role !== 'admin' ? `<button class="btn-icon" data-delete="${u.id}" title="Delete"></button>` : ''}
            </td>
          </tr>`).join('')}
        </tbody>
      </table>
      ${this.paginationHtml()}`;

    document.getElementById('admin-user-search').addEventListener('input', debounceAdmin((e) => {
      this.search = e.target.value; this.page = 1; this.load();
    }, 350));

    document.querySelectorAll('[data-toggle]').forEach(btn => btn.onclick = async () => {
      try { await api.patch(`/admin/users/${btn.dataset.toggle}/toggle-active`); toastAdmin('User status updated', 'success'); this.load(); }
      catch (err) { toastAdmin(err.message || 'Failed', 'error'); }
    });
    document.querySelectorAll('[data-promote]').forEach(btn => btn.onclick = async () => {
      if (!confirm('Promote this user to admin?')) return;
      try { await api.patch(`/admin/users/${btn.dataset.promote}/make-admin`); toastAdmin('Promoted to admin', 'success'); this.load(); }
      catch (err) { toastAdmin(err.message || 'Failed', 'error'); }
    });
    document.querySelectorAll('[data-delete]').forEach(btn => btn.onclick = async () => {
      if (!confirm('Delete this user? This cannot be undone.')) return;
      try { await api.delete(`/admin/users/${btn.dataset.delete}`); toastAdmin('User deleted', 'success'); this.load(); }
      catch (err) { toastAdmin(err.message || 'Failed', 'error'); }
    });
    document.querySelectorAll('[data-reset]').forEach(btn => btn.onclick = async () => {
      const pw = prompt(`New password for ${btn.dataset.name} (min 6 chars):`);
      if (!pw) return;
      try { await api.patch(`/admin/users/${btn.dataset.reset}/reset-password`, { newPassword: pw }); toastAdmin('Password reset', 'success'); }
      catch (err) { toastAdmin(err.message || 'Failed', 'error'); }
    });
    this.wirePagination();
  },

  renderEmails(emails) {
    document.getElementById('admin-content').innerHTML = `
      <table class="admin-table">
        <thead><tr><th>Subject</th><th>Sender</th><th>Receiver</th><th>Date</th><th>Status</th><th></th></tr></thead>
        <tbody>${emails.map(e => `
          <tr>
            <td>${esc(e.subject || '(No subject)')}</td>
            <td>${e.sender ? esc(e.sender.name || e.sender) : ''}</td>
            <td>${e.receiver ? esc(e.receiver.name || e.receiver) : ''}</td>
            <td>${fmt(e.createdAt)}</td>
            <td>${e.smtpSent ? '\u2705 Sent' : '\u274C Failed'} ${e.isRecalled ? '(recalled)' : ''}</td>
            <td><button class="btn-icon" data-del-email="${e.id}"></button></td>
          </tr>`).join('')}
        </tbody>
      </table>
      ${this.paginationHtml()}`;
    document.querySelectorAll('[data-del-email]').forEach(btn => btn.onclick = async () => {
      if (!confirm('Delete this email as admin?')) return;
      try { await api.delete(`/admin/emails/${btn.dataset.delEmail}`); toastAdmin('Email deleted', 'success'); this.load(); }
      catch (err) { toastAdmin(err.message || 'Failed', 'error'); }
    });
    this.wirePagination();
  },

  renderLogs(logs, title) {
    document.getElementById('admin-content').innerHTML = `
      <h3 style="margin-bottom:10px;">${title}</h3>
      <table class="admin-table">
        <thead><tr><th>User</th><th>Action</th><th>Detail</th><th>When</th></tr></thead>
        <tbody>${logs.map(l => `
          <tr>
            <td>${l.admin ? esc(l.admin.name || l.admin) : (l.user ? esc(l.user.name || l.user) : '')}</td>
            <td>${esc(l.action)}</td>
            <td>${esc(l.detail || l.target || '')}</td>
            <td>${fmt(l.createdAt)}</td>
          </tr>`).join('')}
        </tbody>
      </table>
      ${this.paginationHtml()}`;
    this.wirePagination();
  },

  paginationHtml() {
    return `<div class="pagination">
      <button class="btn btn-secondary" id="admin-prev" ${this.page <= 1 ? 'disabled' : ''} style="padding:5px 12px;">Prev</button>
      <span>Page ${this.page} of ${this.pages}</span>
      <button class="btn btn-secondary" id="admin-next" ${this.page >= this.pages ? 'disabled' : ''} style="padding:5px 12px;">Next</button>
    </div>`;
  },

  wirePagination() {
    const prev = document.getElementById('admin-prev');
    const next = document.getElementById('admin-next');
    if (prev) prev.onclick = () => { this.page--; this.load(); };
    if (next) next.onclick = () => { this.page++; this.load(); };
  },
};

function esc(s) { return s == null ? '' : String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
function fmt(iso) { return iso ? new Date(iso).toLocaleDateString(undefined, { dateStyle: 'medium' }) : ''; }
function debounceAdmin(fn, ms) { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; }
function toastAdmin(msg, type) {
  const c = document.getElementById('toast-container');
  const el = document.createElement('div');
  el.className = `toast toast-${type || 'info'}`;
  el.textContent = msg;
  c.appendChild(el);
  setTimeout(() => el.remove(), 3000);
}

(async () => {
  const user = await Auth.requireAuth();
  if (!user) return;
  if (user.role !== 'admin') { location.href = 'inbox.html'; return; }
  document.getElementById('mf-mobile-menu-btn').onclick = () => Sidebar.openMobile();
  await Sidebar.render(null, () => Compose.open());
  await AdminApp.init();
})();
