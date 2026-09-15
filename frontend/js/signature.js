/** Replaces client/src/pages/SignaturePage.js. */
(async () => {
  const user = await Auth.requireAuth();
  if (!user) return;
  document.getElementById('mf-mobile-menu-btn').onclick = () => Sidebar.openMobile();
  await Sidebar.render(null, () => Compose.open());

  const fields = ['name', 'designation', 'company', 'phone', 'regards'];

  function esc(s) {
    return s == null ? '' : String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function currentForm() {
    const f = { isEnabled: document.getElementById('sig-enabled').checked };
    fields.forEach(k => { f[k] = document.getElementById('sig-' + k).value; });
    return f;
  }

  function renderPreview() {
    const f = currentForm();
    document.getElementById('sig-preview').innerHTML = `
      <p style="margin:0 0 4px;font-size:15px;font-weight:700;color:#0078d4;">${esc(f.regards || 'Best Regards')},</p>
      ${f.name ? `<p style="margin:0;font-weight:600;">${esc(f.name)}</p>` : ''}
      ${f.designation ? `<p style="margin:0;">${esc(f.designation)}</p>` : ''}
      ${f.company ? `<p style="margin:0;">${esc(f.company)}</p>` : ''}
      ${f.phone ? `<p style="margin:0;">${esc(f.phone)}</p>` : ''}`;
  }

  function fillForm(sig) {
    document.getElementById('sig-name').value = sig?.name || '';
    document.getElementById('sig-designation').value = sig?.designation || '';
    document.getElementById('sig-company').value = sig?.company || '';
    document.getElementById('sig-phone').value = sig?.phone || '';
    document.getElementById('sig-regards').value = sig?.regards || 'Best Regards';
    document.getElementById('sig-enabled').checked = sig ? sig.isEnabled !== false : true;
    renderPreview();
  }

  document.querySelectorAll('#sig-form input').forEach(el => el.addEventListener('input', renderPreview));
  document.getElementById('sig-enabled').addEventListener('change', renderPreview);

  function showToast(msg) {
    const el = document.getElementById('sig-toast');
    el.textContent = msg;
    el.style.display = 'block';
    setTimeout(() => { el.style.display = 'none'; }, 2500);
  }

  document.getElementById('sig-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = document.getElementById('sig-save-btn');
    btn.disabled = true;
    try {
      await api.post('/signature', currentForm());
      showToast('\u2705 Signature saved');
    } catch (err) {
      showToast('\u26A0 ' + (err.message || 'Failed to save signature.'));
    } finally {
      btn.disabled = false;
    }
  });

  document.getElementById('sig-remove-btn').onclick = async () => {
    if (!confirm('Remove your email signature?')) return;
    try {
      await api.delete('/signature');
      fillForm(null);
      showToast('Signature removed');
    } catch (err) {
      showToast('\u26A0 ' + (err.message || 'Failed to remove signature.'));
    }
  };

  try {
    const res = await api.get('/signature');
    fillForm(res.signature);
  } catch (e) {
    fillForm(null);
  }
})();
