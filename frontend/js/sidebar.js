/**
 * Renders the Common Net sidebar into #sidebar-root on every authenticated
 * page. Replaces components/Sidebar.js (+ the inline AccountSwitcher).
 * Mailbox-type views (inbox/sent/drafts/scheduled/starred/important/archive/
 * folder:<id>) live inside inbox.html and switch via ?view=; Trash, Calendar,
 * Profile, Signature and Admin are separate pages, matching the original
 * app's actual navigation (Trash already opened its own route in the React
 * version — see Sidebar.js's handleNavClick).
 */
const NAV_ITEMS = [
  { id: 'inbox', label: 'Inbox', icon: '\uD83D\uDCE5', href: 'inbox.html' },
  { id: 'important', label: 'Important', icon: '\uD83D\uDD34', href: 'inbox.html?view=important' },
  { id: 'sent', label: 'Sent', icon: '\uD83D\uDCE4', href: 'inbox.html?view=sent' },
  { id: 'drafts', label: 'Drafts', icon: '\uD83D\uDCDD', href: 'inbox.html?view=drafts' },
  { id: 'scheduled', label: 'Scheduled', icon: '\uD83D\uDD50', href: 'inbox.html?view=scheduled' },
  { id: 'starred', label: 'Starred', icon: '\u2B50', href: 'inbox.html?view=starred' },
  { id: 'archive', label: 'Archive', icon: '\uD83D\uDCE6', href: 'inbox.html?view=archive' },
  { id: 'calendar', label: 'Calendar', icon: '\uD83D\uDCC5', href: 'calendar.html' },
  { id: 'trash', label: 'Trash', icon: '\uD83D\uDDD1\uFE0F', href: 'trash.html' },
];
const FOLDER_COLORS = ['#0078d4', '#107c10', '#d13438', '#8764b8', '#ca5010', '#038387', '#881798', '#00b7c3'];

const Sidebar = {
  async render(activeView, onComposeClick) {
    const user = Auth.getUser();
    const root = document.getElementById('sidebar-root');
    if (!root || !user) return;

    let folders = [];
    try { folders = (await api.get('/folders')).folders || []; } catch (e) { /* non-fatal */ }

    root.innerHTML = `
      <aside class="mailflow-sidebar" id="mf-sidebar">
        <button class="mailflow-mobile-close" id="mf-sb-close" aria-label="Close menu">\u2715</button>
        <div class="mf-sb-header">
          <div class="mf-sb-logo-row">
            <svg width="22" height="22" viewBox="0 0 36 36" fill="none">
              <rect width="36" height="36" rx="8" fill="rgba(255,255,255,0.18)"/>
              <path d="M8 10h8v8H8zM20 10h8v8h-8zM8 22h8v8H8zM20 22h8v8h-8z" fill="white"/>
            </svg>
            <span class="mf-sb-logo-text">Common Net</span>
          </div>
          <button class="mf-sb-compose" id="mf-compose-btn">\u2795 Compose</button>
          <button class="mf-sb-compose" id="mf-schedule-meeting-btn" style="margin-top:6px;">\uD83D\uDCC5 Schedule Meeting</button>
          <div class="mf-sb-controls" aria-label="Sidebar controls">
              <button type="button" class="mf-sb-control-btn mf-theme-toggle" id="mf-theme-toggle">
                  🌙 Dark
              </button>

              <button type="button" class="mf-sb-control-btn notif-bell" id="mf-notif-bell">
                  🔔 Notifications
              </button>
          </div>
        </div>

        <nav class="mf-sb-nav" id="mf-sb-nav"></nav>

        <div class="mf-sb-footer" id="mf-sb-footer">
          <div class="mf-sb-user-row" id="mf-user-row">
            <div class="avatar">${user.avatar ? `<img src="${user.avatar}" alt="">` : Auth.initials(user.name)}</div>
            <div style="min-width:0;">
              <div class="mf-sb-user-name">${escapeHtml(user.name)}</div>
              <div class="mf-sb-user-email">${escapeHtml(user.email)}</div>
            </div>
          </div>
        </div>
      </aside>`;

    const nav = document.getElementById('mf-sb-nav');
    NAV_ITEMS.forEach(item => {
      const el = document.createElement('div');
      el.className = 'nav-item' + (activeView === item.id ? ' active' : '');
      el.innerHTML = `<span class="nav-icon">${item.icon}</span><span class="nav-label">${item.label}</span>`;
      el.onclick = () => { location.href = item.href; };
      nav.appendChild(el);
    });

    renderFolders(nav, folders, activeView);

    document.getElementById('mf-compose-btn').onclick = () => onComposeClick && onComposeClick();
    const meetingBtn = document.getElementById('mf-schedule-meeting-btn');
    if (meetingBtn) meetingBtn.onclick = () => {
      if (window.MeetingScheduler) return window.MeetingScheduler.open();
      const script=document.createElement('script'); script.src='js/meeting.js';
      script.onload=()=>window.MeetingScheduler && window.MeetingScheduler.open();
      document.body.appendChild(script);
    };
    const themeToggle = document.getElementById('mf-theme-toggle');
    if (themeToggle) themeToggle.onclick = () => window.MailFlowTheme && window.MailFlowTheme.toggle();
    if (window.MailFlowTheme) window.MailFlowTheme.applyTheme(window.MailFlowTheme.getTheme());
    const closeBtn = document.getElementById('mf-sb-close');
    if (closeBtn) closeBtn.onclick = () => document.getElementById('mf-sidebar').classList.remove('mailflow-sidebar-open');

    document.getElementById('mf-user-row').onclick = (e) => toggleUserMenu(e.currentTarget);

    Notifications.connect();
    renderNotifBell();
  },

  openMobile() {
    const sb = document.getElementById('mf-sidebar');
    if (sb) sb.classList.add('mailflow-sidebar-open');
  },
};

function renderFolders(nav, folders, activeView) {
  const header = document.createElement('div');
  header.className = 'mf-sb-folders-header';
  header.innerHTML = `<span>Folders</span><button class="btn-icon" id="mf-new-folder-btn" style="color:white;font-size:14px;">+</button>`;
  nav.appendChild(header);

  const list = document.createElement('div');
  list.id = 'mf-folder-list';
  nav.appendChild(list);

  const formHolder = document.createElement('div');
  formHolder.id = 'mf-new-folder-form-holder';
  nav.appendChild(formHolder);

  folders.forEach(f => {
    const el = document.createElement('div');
    el.className = 'folder-item' + (activeView === `folder:${f.id}` ? ' active' : '');
    el.draggable = false;
    el.innerHTML = `
      <span class="folder-dot" style="background:${f.color}"></span>
      <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(f.name)}</span>
      <span class="folder-actions">
        <button title="Rename" data-action="rename">\u270F\uFE0F</button>
        <button title="Delete" data-action="delete">\uD83D\uDDD1</button>
      </span>`;
    el.onclick = (e) => {
      if (e.target.dataset && e.target.dataset.action) return;
      location.href = `inbox.html?view=folder:${f.id}`;
    };
    el.ondragover = (e) => { e.preventDefault(); el.classList.add('drag-over'); };
    el.ondragleave = () => el.classList.remove('drag-over');
    el.ondrop = async (e) => {
      e.preventDefault(); el.classList.remove('drag-over');
      const emailId = e.dataTransfer.getData('emailId');
      if (!emailId) return;
      try { await api.put(`/folders/${f.id}/emails/${emailId}`); if (window.onFolderMoveDone) window.onFolderMoveDone(); }
      catch { alert('Failed to move email.'); }
    };
    el.querySelector('[data-action="rename"]').onclick = async () => {
      const name = prompt('Rename folder', f.name);
      if (name && name.trim()) {
        try { await api.put(`/folders/${f.id}`, { name: name.trim(), color: f.color }); location.reload(); }
        catch (err) { alert(err.message || 'Failed to rename folder.'); }
      }
    };
    el.querySelector('[data-action="delete"]').onclick = async () => {
      if (!confirm(`Delete folder "${f.name}"?`)) return;
      try { await api.delete(`/folders/${f.id}`); location.reload(); }
      catch { alert('Failed to delete folder.'); }
    };
    list.appendChild(el);
  });

  document.getElementById('mf-new-folder-btn').onclick = () => showNewFolderForm(formHolder);
}

function showNewFolderForm(holder) {
  let selectedColor = FOLDER_COLORS[0];
  holder.innerHTML = `
    <div class="new-folder-form">
      <input class="form-input" id="mf-nf-name" placeholder="Folder name" style="margin-bottom:8px;font-size:12.5px;padding:6px 8px;">
      <div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:8px;">
        ${FOLDER_COLORS.map((c, i) => `<span class="folder-color-dot${i === 0 ? ' selected' : ''}" data-color="${c}" style="background:${c}"></span>`).join('')}
      </div>
      <div id="mf-nf-error" class="form-error" style="display:none;"></div>
      <div style="display:flex;gap:6px;">
        <button class="btn btn-primary" id="mf-nf-save" style="flex:1;padding:6px;font-size:12px;">Create</button>
        <button class="btn btn-secondary" id="mf-nf-cancel" style="flex:1;padding:6px;font-size:12px;">Cancel</button>
      </div>
    </div>`;
  holder.querySelectorAll('.folder-color-dot').forEach(dot => {
    dot.onclick = () => {
      holder.querySelectorAll('.folder-color-dot').forEach(d => d.classList.remove('selected'));
      dot.classList.add('selected');
      selectedColor = dot.dataset.color;
    };
  });
  document.getElementById('mf-nf-cancel').onclick = () => { holder.innerHTML = ''; };
  document.getElementById('mf-nf-save').onclick = async () => {
    const name = document.getElementById('mf-nf-name').value.trim();
    const errEl = document.getElementById('mf-nf-error');
    if (!name) { errEl.textContent = 'Please enter a folder name.'; errEl.style.display = 'block'; return; }
    try {
      await api.post('/folders', { name, color: selectedColor });
      holder.innerHTML = '';
      location.reload();
    } catch (err) {
      errEl.textContent = err.message || 'Failed to create folder.';
      errEl.style.display = 'block';
    }
  };
}

function toggleUserMenu(anchor) {
  const existing = document.getElementById('mf-user-menu');
  if (existing) { existing.remove(); return; }
  const user = Auth.getUser();
  const menu = document.createElement('div');
  menu.className = 'user-menu';
  menu.id = 'mf-user-menu';
  menu.innerHTML = `
    <div class="user-menu-item" data-go="profile.html">\uD83D\uDC64 Profile</div>
    <div class="user-menu-item" data-go="signature.html">\u270D\uFE0F Signature</div>
    ${user.role === 'admin' ? '<div class="user-menu-item" data-go="admin.html">\uD83D\uDEE1\uFE0F Admin Dashboard</div>' : ''}
    <div class="user-menu-item danger" id="mf-logout-item">\uD83D\uDEAA Logout</div>`;
  document.getElementById('mf-sb-footer').appendChild(menu);
  menu.querySelectorAll('[data-go]').forEach(el => el.onclick = () => { location.href = el.dataset.go; });
  document.getElementById('mf-logout-item').onclick = () => Auth.logout();
  setTimeout(() => {
    document.addEventListener('click', function close(e) {
      if (!menu.contains(e.target) && e.target !== anchor) { menu.remove(); document.removeEventListener('click', close); }
    });
  }, 0);
}

function renderNotifBell() {
  const bellWrap = document.getElementById('mf-notif-bell');
  if (!bellWrap) return;
  Notifications.onCountChange((count) => {
    const badge = document.getElementById('mf-notif-count');
    if (!badge) return;
    badge.textContent = count > 9 ? '9+' : count;
    badge.style.display = count > 0 ? 'inline-flex' : 'none';
  });
  bellWrap.onclick = (e) => {
    e.stopPropagation();
    const existing = document.getElementById('mf-notif-panel');
    if (existing) { existing.remove(); return; }
    const notes = Notifications.getNotifications();
    const panel = document.createElement('div');
    panel.className = 'notif-panel'; panel.id = 'mf-notif-panel';
    panel.innerHTML = notes.length
      ? notes.map(n => `<div class="notif-item"><strong>${n.type === 'mention' ? '🔔' : n.type === 'important' ? '🔴' : '📬'}</strong> ${escapeHtml(n.message)}</div>`).join('')
      : `<div class="notif-item">No notifications yet.</div>`;
    document.body.appendChild(panel);
    const rect = bellWrap.getBoundingClientRect();
    panel.style.position='fixed';
    panel.style.top=(rect.bottom+6)+'px';
    panel.style.left=Math.min(rect.left, window.innerWidth-320)+'px';
    setTimeout(() => {
      document.addEventListener('click', function close(e2) {
        if (!panel.contains(e2.target) && e2.target !== bellWrap) {
          panel.remove(); document.removeEventListener('click', close);
        }
      });
    }, 0);
  };
}
