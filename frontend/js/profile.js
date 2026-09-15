/** Replaces client/src/pages/ProfilePage.js. */
(async () => {
  const user = await Auth.requireAuth();
  if (!user) return;
  document.getElementById('mf-mobile-menu-btn').onclick = () => Sidebar.openMobile();
  await Sidebar.render(null, () => Compose.open());

  let currentUser = null;

  function renderAvatar(u) {
    const av = document.getElementById('pf-avatar');
    av.innerHTML = u.avatar ? `<img src="${u.avatar}" alt="" style="width:100%;height:100%;object-fit:cover;border-radius:50%;">` : Auth.initials(u.name);
    document.getElementById('pf-name-display').textContent = u.name;
    document.getElementById('pf-email-display').textContent = u.email;
  }

  async function load() {
    try {
      const res = await api.get('/profile');
      currentUser = res.user;
      document.getElementById('pf-name').value = currentUser.name || '';
      document.getElementById('pf-designation').value = currentUser.designation || '';
      document.getElementById('pf-department').value = currentUser.department || '';
      document.getElementById('pf-phone').value = currentUser.phone || '';
      document.getElementById('pf-bio').value = currentUser.bio || '';
      renderAvatar(currentUser);
    } catch (e) { /* fall back to cached */ renderAvatar(user); }
  }

  function showToast(msg) {
    const el = document.getElementById('pf-toast');
    el.textContent = msg;
    el.style.display = 'block';
    setTimeout(() => { el.style.display = 'none'; }, 2500);
  }

  document.getElementById('pf-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('pf-save-btn');
    btn.disabled = true;
    try {
      const res = await api.put('/profile', {
        name: document.getElementById('pf-name').value.trim(),
        designation: document.getElementById('pf-designation').value.trim(),
        department: document.getElementById('pf-department').value.trim(),
        phone: document.getElementById('pf-phone').value.trim(),
        bio: document.getElementById('pf-bio').value.trim(),
      });
      Auth.updateUser({ ...Auth.getUser(), ...res.user });
      renderAvatar(res.user);
      showToast('\u2705 Profile updated');
    } catch (err) {
      showToast('\u26A0 ' + (err.message || 'Failed to update profile.'));
    } finally {
      btn.disabled = false;
    }
  });

  document.getElementById('pf-avatar-btn').onclick = () => document.getElementById('pf-avatar-input').click();
  document.getElementById('pf-avatar-input').onchange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    document.getElementById('pf-avatar-overlay').style.display = 'flex';
    const fd = new FormData();
    fd.append('avatar', file);
    try {
      const res = await api.postForm('/profile/avatar', fd);
      renderAvatar({ ...currentUser, avatar: res.avatar });
      Auth.updateUser({ ...Auth.getUser(), avatar: res.avatar });
      showToast('\u2705 Avatar updated');
    } catch (err) {
      showToast('\u26A0 ' + (err.message || 'Failed to upload avatar.'));
    } finally {
      document.getElementById('pf-avatar-overlay').style.display = 'none';
      e.target.value = '';
    }
  };

  await load();
})();
