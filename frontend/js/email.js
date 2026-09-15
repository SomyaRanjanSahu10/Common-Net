/**
 * Powers inbox.html: fetching + rendering the email list and detail pane
 * for every "mailbox" view (inbox/sent/drafts/scheduled/starred/important/
 * archive/folder:<id>/search). Replaces components/EmailList.js and
 * components/EmailDetail.js.
 */
const MailApp = {
  view: 'inbox',
  emails: [],
  selected: null,
  page: 1,
  pages: 1,
  searchQuery: '',

  async init() {
    const params = new URLSearchParams(location.search);
    this.view = params.get('view') || 'inbox';
    await Sidebar.render(this.viewKeyForSidebar(), () => Compose.open());
    document.getElementById('mf-toolbar-title').textContent = this.titleForView();
    this.wireToolbar();
    await this.load();

    window.onEmailSent = () => this.load();
    window.onEmailDrafted = () => this.load();
    window.onFolderMoveDone = () => this.load();
    Notifications.onNewEmail(() => { if (this.view === 'inbox') this.load(); });
    Notifications.onImportant(() => { if (this.view === 'important') this.load(); });
  },

  viewKeyForSidebar() {
    if (this.view.startsWith('folder:')) return this.view;
    return this.view;
  },

  titleForView() {
    if (this.view.startsWith('folder:')) return 'Folder';
    return { inbox: 'Inbox', sent: 'Sent', drafts: 'Drafts', scheduled: 'Scheduled', starred: 'Starred', important: 'Important', archive: 'Archive' }[this.view] || 'Inbox';
  },

  wireToolbar() {
    const menuBtn = document.getElementById('mf-mobile-menu-btn');
    if (menuBtn) menuBtn.onclick = () => Sidebar.openMobile();
    const searchInput = document.getElementById('mf-search-input');
    if (searchInput) {
      searchInput.addEventListener('input', debounce((e) => {
        this.searchQuery = e.target.value.trim();
        this.page = 1;
        this.load();
      }, 300));
    }
  },

  async load() {
    const listEl = document.getElementById('mf-email-list');
    listEl.innerHTML = `<div class="empty-state"><span class="spinner spinner-dark"></span></div>`;

    try {
      let emails = [];
      let unreadCount = null;

      if (this.searchQuery) {
        const res = await api.get('/email/search', { q: this.searchQuery });
        emails = res.emails || [];
      } else if (this.view === 'inbox') {
        const res = await api.get('/email/inbox', { page: this.page, limit: 20 });
        emails = res.emails || []; this.pages = res.pages || 1; unreadCount = res.unreadCount;
      } else if (this.view === 'sent') {
        const res = await api.get('/email/sent', { page: this.page, limit: 20 });
        emails = res.emails || []; this.pages = res.pages || 1;
      } else if (this.view === 'drafts') {
        emails = (await api.get('/email/drafts')).emails || [];
      } else if (this.view === 'starred') {
        emails = (await api.get('/email/starred')).emails || [];
      } else if (this.view === 'important') {
        emails = (await api.get('/email/important')).emails || [];
      } else if (this.view === 'archive') {
        emails = (await api.get('/email/archive')).emails || [];
      } else if (this.view === 'scheduled') {
        emails = (await api.get('/email/scheduled')).emails || [];
      } else if (this.view.startsWith('folder:')) {
        const id = this.view.split(':')[1];
        const res = await api.get(`/folders/${id}/emails`);
        emails = res.emails || [];
      }

      this.emails = emails;
      this.renderList();
      if (typeof unreadCount === 'number') this.updateFavicon(unreadCount);
    } catch (err) {
      listEl.innerHTML = `<div class="empty-state"><span class="empty-icon">\u26A0\uFE0F</span>Failed to load emails.</div>`;
    }
  },

  updateFavicon() { /* no-op placeholder — original app didn't badge the favicon either */ },

  renderList() {
    const listEl = document.getElementById('mf-email-list');
    const me = Auth.getUser();
    if (!this.emails.length) {
      listEl.innerHTML = `<div class="empty-state"><span class="empty-icon"></span>No emails here.</div>`;
      return;
    }
    listEl.innerHTML = this.emails.map(e => {
      const isSentView = this.view === 'sent' || this.view === 'drafts' || this.view === 'scheduled';
      const person = isSentView ? e.receiver : e.sender;
      const name = person ? person.name : (e.toEmail || 'Unknown');
      const date = e.createdAt ? formatDate(e.createdAt) : '';
      const snippet = (e.body || '').slice(0, 80);
      return `
        <div class="email-item ${!e.isRead && !isSentView ? 'unread' : ''} ${this.selected && this.selected.id === e.id ? 'selected' : ''}" data-id="${e.id}" draggable="true">
          <button class="star-btn ${e.isStarred ? 'starred' : ''}" data-star="${e.id}">${e.isStarred ? '\u2B50' : '\u2606'}</button>
          <div class="email-meta">
            <div class="email-row1">
              <span class="email-name">${escapeHtml(name)}</span>
              <span class="email-date">${date}</span>
            </div>
            <div class="email-subject">${e.isImportant ? '' : ''}${escapeHtml(e.subject || '(No subject)')}</div>
            <div class="email-snippet">${escapeHtml(snippet)}</div>
            <div class="email-flags">
              ${e.attachments && e.attachments.length ? '<span class="attach-icon"></span>' : ''}
              ${e.isRecalled ? '<span style="color:var(--ms-danger);font-size:11px;">Recalled</span>' : ''}
            </div>
          </div>
        </div>`;
    }).join('');

    listEl.querySelectorAll('.email-item').forEach(item => {
      item.onclick = (e) => {
        if (e.target.dataset.star) return;
        this.openEmail(item.dataset.id);
      };
      item.ondragstart = (e) => e.dataTransfer.setData('emailId', item.dataset.id);
    });
    listEl.querySelectorAll('[data-star]').forEach(btn => {
      btn.onclick = async (e) => {
        e.stopPropagation();
        try { await api.patch(`/email/${btn.dataset.star}/star`); this.load(); } catch {}
      };
    });
  },

  async openEmail(id) {
    if (this.view === 'drafts') { const draft = this.emails.find(e => e.id === id); Compose.open(draft); return; }

    const detailEl = document.getElementById('mf-email-detail');
    detailEl.classList.add('show-on-mobile');
    document.getElementById('mf-email-list').classList.add('hide-on-mobile-detail');
    detailEl.innerHTML = `<div class="empty-state"><span class="spinner spinner-dark"></span></div>`;
    try {
      const res = await api.get(`/email/${id}`);
      this.selected = res.email;
      this.renderDetail(res.email);
      this.renderList();
    } catch (err) {
      detailEl.innerHTML = `<div class="empty-state">Failed to load email.</div>`;
    }
  },

  renderDetail(e) {
    const me = Auth.getUser();
    const isSentView = this.view === 'sent' || this.view === 'scheduled';
    const person = isSentView ? e.receiver : e.sender;
    const canRecall = this.view === 'sent' && !e.isRecalled;
    const detailEl = document.getElementById('mf-email-detail');

    detailEl.innerHTML = `
      <button class="btn-icon" id="mf-back-btn" style="display:none;margin-bottom:10px;"> Back</button>
      ${e.isRecalled ? '<div class="recall-banner">This email was recalled by the sender.</div>' : ''}
      <div class="detail-header">
        <div class="detail-subject">${e.isImportant ? '' : ''}${escapeHtml(e.subject || '(No subject)')}</div>
        <div class="detail-from-row">
          <div class="avatar">${person && person.avatar ? `<img src="${person.avatar}" alt="">` : Auth.initials(person ? person.name : e.toEmail)}</div>
          <div class="detail-from-meta">
            <div class="detail-from-name">${escapeHtml(person ? person.name : e.toEmail)}</div>
            <div class="detail-from-email">${escapeHtml(person ? person.email : '')} ${isSentView ? '' : `to me`}</div>
          </div>
          <div class="detail-date">${e.createdAt ? formatDate(e.createdAt, true) : ''}</div>
        </div>
        <div class="detail-actions">
          <button class="btn btn-secondary" id="mf-reply-btn">Reply</button>
          <button class="btn btn-secondary" id="mf-star-btn">${e.isStarred ? 'Starred' : 'Star'}</button>
          <button class="btn btn-secondary" id="mf-important-btn">${e.isImportant ? 'Important' : 'Mark important'}</button>
          <button class="btn btn-secondary" id="mf-archive-btn">${e.isArchived ? 'Unarchive' : 'Archive'}</button>
          ${canRecall ? `<button class="btn btn-secondary" id="mf-recall-btn">Recall</button>` : ''}
          <button class="btn btn-danger" id="mf-delete-btn">Delete</button>
        </div>
      </div>
      ${e.attachments && e.attachments.length ? `
        <div class="attachments-row">
          ${e.attachments.map(a => `<div class="att-card" data-att="${a.id}" data-name="${escapeHtml(a.originalName)}">${escapeHtml(a.originalName)}</div>`).join('')}
        </div>` : ''}
      <div class="email-html-body">${e.htmlBody || escapeHtml(e.body || '')}</div>`;

    const backBtn = document.getElementById('mf-back-btn');
    if (window.innerWidth <= 900) {
      backBtn.style.display = 'inline-flex';
      backBtn.onclick = () => {
        detailEl.classList.remove('show-on-mobile');
        document.getElementById('mf-email-list').classList.remove('hide-on-mobile-detail');
      };
    }

    document.getElementById('mf-reply-btn').onclick = () => Compose.open({
      toEmail: person ? person.email : '', subject: 'Re: ' + (e.subject || ''),
    });
    document.getElementById('mf-star-btn').onclick = async () => { await api.patch(`/email/${e.id}/star`); this.openEmail(e.id); this.load(); };
    document.getElementById('mf-important-btn').onclick = async () => { await api.patch(`/email/${e.id}/important`); this.openEmail(e.id); this.load(); };
    document.getElementById('mf-archive-btn').onclick = async () => { await api.put(`/email/archive/${e.id}`); this.load(); };
    document.getElementById('mf-delete-btn').onclick = async () => {
      if (!confirm('Move this email to Trash?')) return;
      await api.delete(`/email/${e.id}`);
      detailEl.innerHTML = `<div class="empty-state">Select an email to read.</div>`;
      this.load();
    };
    const recallBtn = document.getElementById('mf-recall-btn');
    if (recallBtn) recallBtn.onclick = async () => {
      try { await api.patch(`/email/recall/${e.id}`); this.openEmail(e.id); this.load(); }
      catch (err) { alert(err.message || 'Failed to recall.'); }
    };
    detailEl.querySelectorAll('[data-att]').forEach(card => {
      card.onclick = async () => {
        try {
          const { blob, filename } = await api.download(`/email/${e.id}/attachments/${card.dataset.att}`);
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url; a.download = card.dataset.name || filename;
          document.body.appendChild(a); a.click(); a.remove();
          URL.revokeObjectURL(url);
        } catch { alert('Failed to download attachment.'); }
      };
    });
  },
};

function formatDate(iso, long) {
  const d = new Date(iso);
  if (long) return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function debounce(fn, ms) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}

function escapeHtml(s) {
  if (s == null) return '';
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
