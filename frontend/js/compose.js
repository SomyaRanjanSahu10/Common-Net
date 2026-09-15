/**
 * Replaces client/src/components/ComposeModal.js. Opened as an overlay
 * modal from any page (matches the original's behavior — Common Net never
 * had a dedicated "compose page" route).
 *
 * FIX applied here per the migration spec: the original had signature
 * auto-insertion disabled (`// Signature auto-insertion disabled.`). This
 * version fetches /api/signature/render on open and inserts it into the
 * body, positioned so the user can still type above it.
 */
const MAX_FILE_SIZE = 10 * 1024 * 1024;

const Compose = {
  open(draft) {
    if (document.getElementById('mf-compose-overlay')) return;
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.id = 'mf-compose-overlay';
    overlay.innerHTML = composeHtml(draft);
    overlay.onclick = (e) => { if (e.target === overlay) this.close(); };
    document.body.appendChild(overlay);
    wireCompose(draft, () => this.close());
  },
  close() {
    const el = document.getElementById('mf-compose-overlay');
    if (el) el.remove();
  },
};

function composeHtml(draft) {
  const isEdit = !!draft;
  return `
    <div class="compose-modal modal-card">
      <div class="compose-header">
        <h3>${isEdit ? 'Edit Draft' : 'New message'}</h3>
        <div style="display:flex;gap:6px;align-items:center;">
          <button id="cmp-draft-btn" title="Save draft">Draft</button>
          <button id="cmp-close-btn" title="Close"></button>
        </div>
      </div>
      <div id="cmp-msg" style="display:none;padding:8px 20px;font-size:12.5px;"></div>
      <div class="compose-fields">
        <div class="compose-field-row" style="position:relative;">
          <label>To</label>
          <input type="text" id="cmp-to" autocomplete="off" placeholder="Recipient email" value="${draft && draft.toEmail ? draft.toEmail : ''}">
          <button class="compose-cc-bcc-toggle" id="cmp-cc-toggle">Cc</button>
          <button class="compose-cc-bcc-toggle" id="cmp-bcc-toggle">Bcc</button>
          <div class="suggest-dropdown" id="cmp-to-sugg" style="display:none;"></div>
        </div>
        <div class="compose-field-row" id="cmp-cc-row" style="display:${draft && draft.cc && draft.cc.length ? 'flex' : 'none'};position:relative;">
          <label>Cc</label>
          <input type="text" id="cmp-cc" autocomplete="off" value="${draft && draft.cc ? draft.cc.join(', ') : ''}">
        </div>
        <div class="compose-field-row" id="cmp-bcc-row" style="display:${draft && draft.bcc && draft.bcc.length ? 'flex' : 'none'};position:relative;">
          <label>Bcc</label>
          <input type="text" id="cmp-bcc" autocomplete="off" value="${draft && draft.bcc ? draft.bcc.join(', ') : ''}">
        </div>
      </div>
      <div class="compose-subject">
        <input type="text" id="cmp-subject" placeholder="Subject" value="${draft && draft.subject && draft.subject !== '(No Subject)' ? escapeHtml(draft.subject) : ''}">
      </div>
      <div class="compose-body">
        <div class="compose-format-toolbar" id="cmp-format-toolbar" aria-label="Formatting">
          <select id="cmp-font-name" class="compose-format-select" title="Font"><option value="Arial">Arial</option><option value="Calibri" selected>Calibri</option><option value="Georgia">Georgia</option><option value="Times New Roman">Times New Roman</option><option value="Verdana">Verdana</option></select>
          <select id="cmp-font-size" class="compose-format-select" title="Font size"><option value="2">Small</option><option value="3" selected>Normal</option><option value="4">Large</option><option value="5">Extra large</option></select>
          <button type="button" class="compose-format-btn" data-cmd="bold" title="Bold"><b>B</b></button>
          <button type="button" class="compose-format-btn" data-cmd="italic" title="Italic"><i>I</i></button>
          <button type="button" class="compose-format-btn" data-cmd="underline" title="Underline"><u>U</u></button>
          <button type="button" class="compose-format-btn" data-cmd="strikeThrough" title="Strikethrough"><s>S</s></button>
          <input type="color" id="cmp-text-color" class="compose-color-input" title="Text color" value="#333333">
          <button type="button" class="compose-format-btn" data-cmd="justifyLeft" title="Align left">≡</button>
          <button type="button" class="compose-format-btn" data-cmd="justifyCenter" title="Center">≣</button>
          <button type="button" class="compose-format-btn" data-cmd="justifyRight" title="Align right">≡</button>
          <button type="button" class="compose-format-btn" data-cmd="insertOrderedList" title="Numbered list">1.</button>
          <button type="button" class="compose-format-btn" data-cmd="insertUnorderedList" title="Bulleted list">•</button>
          <button type="button" class="compose-format-btn" id="cmp-link-btn" title="Insert link">🔗</button>
          <button type="button" class="compose-format-btn" id="cmp-table-btn" title="Insert table">▦</button>
          <button type="button" class="compose-format-btn compose-language-btn" id="cmp-language-btn" title="Language: English">A文 <span id="cmp-language-label">English</span></button>
          <button type="button" class="compose-format-btn compose-signature-btn" id="cmp-signature-btn" title="Signature">✍ Signature</button>
        </div>
        <div class="compose-editor" id="cmp-body" contenteditable="true">${draft && draft.htmlBody ? draft.htmlBody : (draft && draft.body ? escapeHtml(draft.body) : '')}</div>
        <div class="compose-attachments" id="cmp-att-list"></div>
      </div>
      <div class="compose-footer">
        <button class="btn btn-primary" id="cmp-send-btn" style="padding:9px 22px;">Send</button>
        <button class="compose-toolbar-btn" id="cmp-attach-btn" title="Attach files">📎</button>
        <input type="file" id="cmp-file-input" multiple style="display:none;">
        <button class="compose-toolbar-btn" id="cmp-important-btn" title="Mark important">❗</button>
        <button class="compose-toolbar-btn" id="cmp-meeting-btn" title="Insert meeting link">📅</button>
        <button class="compose-toolbar-btn" id="cmp-schedule-btn" title="Schedule send">🕒</button>
        <button class="compose-toolbar-btn" id="cmp-discard-btn" title="Discard">🗑</button>
        <div class="spacer"></div><span id="cmp-spinner" style="display:none;" class="spinner spinner-dark"></span>
      </div>
    </div>`;
}

function wireCompose(draft, close) {
  const state = {
    isImportant: !!(draft && draft.isImportant),
    files: [],
    existingAtts: (draft && draft.attachments) || [],
    draftId: (draft && draft.id) || null,
    scheduledTime: null,
    activeSuggField: 'to',
    language: 'English',
    signatureSelection: null,
  };

  document.getElementById('cmp-close-btn').onclick = close;
  document.getElementById('cmp-cc-toggle').onclick = () => toggle('cmp-cc-row');
  document.getElementById('cmp-bcc-toggle').onclick = () => toggle('cmp-bcc-row');
  document.getElementById('cmp-important-btn').onclick = (e) => {
    state.isImportant = !state.isImportant;
    e.target.textContent = state.isImportant ? '' : '';
  };
  document.getElementById('cmp-meeting-btn').onclick = () => {
    const link = `https://meet.mailflow.app/${Math.random().toString(36).slice(2, 10).toUpperCase()}`;
    const body = document.getElementById('cmp-body');
    body.innerHTML += `<p><a href="${link}" target="_blank">Join Meeting: ${link}</a></p>`;
  };
  document.getElementById('cmp-attach-btn').onclick = () => document.getElementById('cmp-file-input').click();
  document.getElementById('cmp-file-input').onchange = (e) => {
    Array.from(e.target.files || []).forEach(f => {
      if (f.size > MAX_FILE_SIZE) { showMsg(`${f.name} exceeds 10 MB`, true); return; }
      state.files.push(f);
    });
    e.target.value = '';
    renderAttachments(state);
  };
  document.getElementById('cmp-schedule-btn').onclick = () => openSchedulePopover(state);
  document.getElementById('cmp-table-btn').onclick = () => insertComposeTable();
  document.getElementById('cmp-language-btn').onclick = () => openLanguagePopover(state);
  document.getElementById('cmp-signature-btn').onclick = () => openSignaturePopover(state);

  ['cmp-to', 'cmp-cc', 'cmp-bcc'].forEach(id => {
    const field = id.replace('cmp-', '');
    const input = document.getElementById(id);
    if (!input) return;
    input.addEventListener('input', debounce(() => handleRecipientSearch(field, input.value), 250));
    input.addEventListener('focus', () => { state.activeSuggField = field; });
  });

  document.querySelectorAll('#cmp-format-toolbar [data-cmd]').forEach(btn => {
    btn.onclick=()=>{const body=document.getElementById('cmp-body');body.focus();document.execCommand(btn.dataset.cmd,false,null);};
  });
  document.getElementById('cmp-font-name').onchange=e=>{document.getElementById('cmp-body').focus();document.execCommand('fontName',false,e.target.value);};
  document.getElementById('cmp-font-size').onchange=e=>{document.getElementById('cmp-body').focus();document.execCommand('fontSize',false,e.target.value);};
  document.getElementById('cmp-text-color').oninput=e=>{document.getElementById('cmp-body').focus();document.execCommand('foreColor',false,e.target.value);};
  document.getElementById('cmp-link-btn').onclick=()=>{const url=window.prompt('Enter link URL:','https://');if(url){document.getElementById('cmp-body').focus();document.execCommand('createLink',false,url);}};
  document.getElementById('cmp-discard-btn').onclick=()=>close();
  document.getElementById('cmp-draft-btn').onclick = () => saveDraft(state, false, close);
  document.getElementById('cmp-send-btn').onclick = () => sendEmail(state, close);

  renderAttachments(state);

  // Preserve the existing default-signature behavior for new messages, but
  // keep it in a dedicated editable block so the Signature control can replace
  // or remove it without touching the rest of the message.
  if (!draft) {
    api.get('/signature/render').then(res => {
      if (res && res.html) insertSignatureHtml(res.html, null, true);
    }).catch(() => {});
  }
}

async function openSignaturePopover(state) {
  const existing = document.getElementById('cmp-signature-popover');
  if (existing) { existing.remove(); return; }

  // Preserve the editor selection before focus moves to the popover.
  saveComposeSelection(state);
  const pop = document.createElement('div');
  pop.className = 'compose-signature-popover';
  pop.id = 'cmp-signature-popover';
  pop.innerHTML = `
    <div class="compose-signature-popover-header">
      <strong>Signature</strong>
      <button type="button" class="compose-signature-close" aria-label="Close">×</button>
    </div>
    <div class="compose-signature-status">Loading signature…</div>
    <div class="compose-signature-actions" style="display:none;">
      <button type="button" class="btn btn-primary" id="cmp-signature-insert">Insert Signature</button>
      <button type="button" class="btn btn-secondary" id="cmp-signature-remove">Remove from email</button>
      <a class="compose-signature-manage" href="signature.html">Create / Edit Signature</a>
    </div>`;
  document.querySelector('.compose-footer').appendChild(pop);
  pop.querySelector('.compose-signature-close').onclick = () => pop.remove();

  const status = pop.querySelector('.compose-signature-status');
  const actions = pop.querySelector('.compose-signature-actions');
  try {
    const res = await api.get('/signature');
    const sig = res && res.signature;
    if (!sig) {
      status.innerHTML = 'No saved signature yet.';
      actions.style.display = 'flex';
      pop.querySelector('#cmp-signature-insert').disabled = true;
      pop.querySelector('#cmp-signature-remove').disabled = !document.querySelector('#cmp-body [data-compose-signature]');
    } else if (sig.isEnabled === false) {
      status.innerHTML = 'Your saved signature is currently disabled.';
      actions.style.display = 'flex';
      pop.querySelector('#cmp-signature-insert').disabled = true;
    } else {
      const html = sig.htmlContent && sig.htmlContent.trim()
        ? sig.htmlContent
        : await api.get('/signature/render').then(r => r.html || '');
      state.signatureSelection = html;
      status.innerHTML = `<div class="compose-signature-preview">${html || 'Signature is empty.'}</div>`;
      actions.style.display = 'flex';
    }

    pop.querySelector('#cmp-signature-insert').onclick = () => {
      if (state.signatureSelection) {
        insertSignatureHtml(state.signatureSelection, state.signatureSelection, false);
        pop.remove();
      }
    };
    pop.querySelector('#cmp-signature-remove').onclick = () => {
      const block = document.querySelector('#cmp-body [data-compose-signature]');
      if (block) block.remove();
      pop.remove();
    };
  } catch (err) {
    status.textContent = err.message || 'Unable to load signature.';
    actions.style.display = 'flex';
    pop.querySelector('#cmp-signature-insert').disabled = true;
  }
}

function saveComposeSelection(state) {
  const body = document.getElementById('cmp-body');
  if (!body) return;
  const sel = window.getSelection();
  if (!sel || !sel.rangeCount || !body.contains(sel.anchorNode)) {
    state.signatureSelection = state.signatureSelection || null;
    return;
  }
  state.composeRange = sel.getRangeAt(0).cloneRange();
  window.__mailflowComposeRange = state.composeRange.cloneRange();
}

function insertSignatureHtml(html, selectionHtml, isAutoInsert) {
  const body = document.getElementById('cmp-body');
  if (!body || !html) return;

  // Selecting/inserting a signature replaces the existing signature block only.
  const old = body.querySelector('[data-compose-signature]');
  if (old) old.remove();

  const wrapper = document.createElement('div');
  wrapper.className = 'compose-signature-block';
  wrapper.dataset.composeSignature = 'true';
  wrapper.setAttribute('contenteditable', 'true');
  wrapper.innerHTML = html;

  if (isAutoInsert) {
    body.appendChild(wrapper);
    return;
  }

  const range = (window.getSelection()?.rangeCount && body.contains(window.getSelection().anchorNode))
    ? window.getSelection().getRangeAt(0)
    : null;
  const saved = window.getSelection();
  let insertRange = range;

  // Prefer the selection captured before the Signature popover opened.
  if (window.__mailflowComposeRange && body.contains(window.__mailflowComposeRange.startContainer)) {
    insertRange = window.__mailflowComposeRange.cloneRange();
  }

  if (!insertRange) {
    insertRange = document.createRange();
    insertRange.selectNodeContents(body);
    insertRange.collapse(false);
  }

  insertRange.collapse(false);
  insertRange.insertNode(wrapper);
  const after = document.createRange();
  after.setStartAfter(wrapper);
  after.collapse(true);
  saved.removeAllRanges();
  saved.addRange(after);
  body.focus();
}

function toggle(id) {
  const el = document.getElementById(id);
  el.style.display = el.style.display === 'none' ? 'flex' : 'none';
}

function handleRecipientSearch(field, value) {
  const last = value.split(',').pop().trim();
  const suggBox = document.getElementById('cmp-to-sugg');
  if (field !== 'to' || last.length < 2) { if (suggBox) suggBox.style.display = 'none'; return; }
  api.get('/users/search', { q: last }).then(res => {
    const users = res.users || [];
    if (!users.length) { suggBox.style.display = 'none'; return; }
    suggBox.innerHTML = users.map(u => `
      <div class="sugg-item" data-email="${escapeHtml(u.email)}">
        <strong>${escapeHtml(u.name)}</strong><br><span style="color:var(--ms-text-muted);">${escapeHtml(u.email)}</span>
      </div>`).join('');
    suggBox.style.display = 'block';
    suggBox.querySelectorAll('.sugg-item').forEach(item => {
      item.onmousedown = () => {
        const input = document.getElementById('cmp-to');
        const parts = input.value.split(',');
        parts[parts.length - 1] = ' ' + item.dataset.email;
        input.value = parts.join(',').replace(/^\s*,/, '').trim();
        suggBox.style.display = 'none';
      };
    });
  }).catch(() => {});
}

function renderAttachments(state) {
  const list = document.getElementById('cmp-att-list');
  const fmtSize = b => b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(1) + ' MB';
  list.innerHTML = [
    ...state.existingAtts.map((a, i) => `<span class="compose-att-chip"> ${escapeHtml(a.originalName)} <button data-existing="${i}">\u2715</button></span>`),
    ...state.files.map((f, i) => `<span class="compose-att-chip"> ${escapeHtml(f.name)} (${fmtSize(f.size)}) <button data-new="${i}">\u2715</button></span>`),
  ].join('');
  list.querySelectorAll('[data-new]').forEach(btn => btn.onclick = () => { state.files.splice(+btn.dataset.new, 1); renderAttachments(state); });
  list.querySelectorAll('[data-existing]').forEach(btn => btn.onclick = () => { state.existingAtts.splice(+btn.dataset.existing, 1); renderAttachments(state); });
}

function openSchedulePopover(state) {
  const existing = document.getElementById('cmp-schedule-pop');
  if (existing) { existing.remove(); return; }
  const min = new Date(Date.now() + 60000).toISOString().slice(0, 16);
  const pop = document.createElement('div');
  pop.className = 'schedule-popover';
  pop.id = 'cmp-schedule-pop';
  pop.innerHTML = `
    <div class="form-label">Send later</div>
    <input type="datetime-local" id="cmp-sched-input" class="form-input" min="${min}" value="${state.scheduledTime || ''}">
    <div style="display:flex;gap:6px;margin-top:8px;">
      <button class="btn btn-primary" id="cmp-sched-set" style="flex:1;">Set</button>
      <button class="btn btn-secondary" id="cmp-sched-clear" style="flex:1;">Clear</button>
    </div>`;
  document.querySelector('.compose-footer').appendChild(pop);
  document.getElementById('cmp-sched-set').onclick = () => {
    state.scheduledTime = document.getElementById('cmp-sched-input').value;
    pop.remove();
    if (state.scheduledTime) showMsg('\u23F0 Will schedule on send', false);
  };
  document.getElementById('cmp-sched-clear').onclick = () => { state.scheduledTime = null; pop.remove(); };
}

function insertComposeTable() {
  const existing=document.getElementById('cmp-table-pop'); if(existing){existing.remove();return;}
  const pop=document.createElement('div'); pop.className='compose-popover'; pop.id='cmp-table-pop';
  pop.innerHTML=`<div class="form-label">Insert Table</div><label>Rows<input id="cmp-table-rows" class="form-input" type="number" min="1" max="20" value="3"></label><label>Columns<input id="cmp-table-cols" class="form-input" type="number" min="1" max="10" value="4"></label><div style="display:flex;gap:6px;margin-top:8px"><button class="btn btn-secondary" id="cmp-table-cancel">Cancel</button><button class="btn btn-primary" id="cmp-table-insert">Insert Table</button></div>`;
  document.getElementById('cmp-format-toolbar').appendChild(pop);
  document.getElementById('cmp-table-cancel').onclick=()=>pop.remove();
  document.getElementById('cmp-table-insert').onclick=()=>{
    const rows=Number(document.getElementById('cmp-table-rows').value),cols=Number(document.getElementById('cmp-table-cols').value);
    if(!Number.isInteger(rows)||!Number.isInteger(cols)||rows<1||rows>20||cols<1||cols>10){showMsg('Please enter valid row and column counts.',true);return;}
    const body=document.getElementById('cmp-body');body.focus();const sel=window.getSelection();let range=null;
    if(sel&&sel.rangeCount&&body.contains(sel.anchorNode))range=sel.getRangeAt(0);
    let html='<table class="compose-table"><tbody>';for(let r=0;r<rows;r++){html+='<tr>';for(let c=0;c<cols;c++)html+='<td contenteditable="true"><br></td>';html+='</tr>';}html+='</tbody></table><p><br></p>';
    if(range){range.deleteContents();const holder=document.createElement('div');holder.innerHTML=html;const frag=document.createDocumentFragment();while(holder.firstChild)frag.appendChild(holder.firstChild);range.insertNode(frag);}else body.insertAdjacentHTML('beforeend',html);
    pop.remove();
  };
}

function openLanguagePopover(state) {
  const existing=document.getElementById('cmp-language-pop');
  if(existing){existing.remove();return;}
  const pop=document.createElement('div');
  pop.className='language-popover'; pop.id='cmp-language-pop';
  const langs=['English','Hindi','Bengali','Telugu','Tamil','Kannada','Malayalam','Marathi','Gujarati','Punjabi','Urdu','Spanish','French','German','Chinese','Japanese'];
  pop.innerHTML=`<div class="form-label">Compose language</div><select id="cmp-language-select" class="form-input">${langs.map(l=>`<option>${l}</option>`).join('')}</select><button class="btn btn-primary" id="cmp-language-set" style="margin-top:8px;width:100%;">Set language</button>`;
  document.querySelector('.compose-footer').appendChild(pop);
  const select=document.getElementById('cmp-language-select'); select.value=state.language;
  document.getElementById('cmp-language-set').onclick=()=>{
    state.language=select.value;
    document.getElementById('cmp-language-btn').title='Language: '+state.language; document.getElementById('cmp-language-label').textContent=state.language;
    document.getElementById('cmp-body').setAttribute('lang', languageCode(state.language));
    pop.remove();
    showMsg('Language set to '+state.language+'.',false);
  };
}
function languageCode(language) {
  return {'English':'en','Hindi':'hi','Bengali':'bn','Telugu':'te','Tamil':'ta','Kannada':'kn','Malayalam':'ml','Marathi':'mr','Gujarati':'gu','Punjabi':'pa','Urdu':'ur','Spanish':'es','French':'fr','German':'de','Chinese':'zh','Japanese':'ja'}[language] || 'en';
}

function buildFormData(state) {
  const fd = new FormData();
  const rawHtml = document.getElementById('cmp-body').innerHTML;
  fd.append('to', document.getElementById('cmp-to').value.trim());
  fd.append('subject', document.getElementById('cmp-subject').value.trim());
  fd.append('htmlBody', rawHtml);
  fd.append('body', rawHtml.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim());
  fd.append('cc', (document.getElementById('cmp-cc') ? document.getElementById('cmp-cc').value : '').trim());
  fd.append('bcc', (document.getElementById('cmp-bcc') ? document.getElementById('cmp-bcc').value : '').trim());
  fd.append('isImportant', String(state.isImportant));
  if (state.draftId) fd.append('draftId', state.draftId);
  state.files.forEach(f => fd.append('attachments', f));
  return fd;
}

async function sendEmail(state, close) {
  const to = document.getElementById('cmp-to').value.trim();
  const subject = document.getElementById('cmp-subject').value.trim();
  if (!to || !subject) { showMsg('Recipient and subject are required.', true); return; }
  setBusy(true);
  try {
    if (state.scheduledTime) {
      const d = new Date(state.scheduledTime);
      if (isNaN(d) || d <= new Date()) { showMsg('Please select a future time.', true); setBusy(false); return; }
      const fd = buildFormData(state);
      fd.append('scheduledTime', d.toISOString());
      await api.postForm('/email/schedule', fd);
      showMsg('\u23F0 Email scheduled!', false);
    } else if (state.draftId) {
      await api.postForm(`/email/draft/${state.draftId}/send`, buildFormData(state));
      showMsg('\u2705 Draft sent!', false);
    } else {
      await api.postForm('/email/send', buildFormData(state));
      showMsg('\u2705 Email sent!', false);
    }
    setTimeout(() => { if (window.onEmailSent) window.onEmailSent(); close(); }, 800);
  } catch (err) {
    showMsg(err.message || 'Failed to send.', true);
  } finally {
    setBusy(false);
  }
}

async function saveDraft(state, silent, close) {
  const subject = document.getElementById('cmp-subject').value.trim();
  if (!subject) { showMsg('Subject required to save draft.', true); return; }
  try {
    await api.postForm('/email/draft', buildFormData(state));
    showMsg('Draft saved!', false);
    setTimeout(() => { if (window.onEmailDrafted) window.onEmailDrafted(); close(); }, 800);
  } catch (err) {
    showMsg(err.message || 'Failed to save draft.', true);
  }
}

function setBusy(busy) {
  document.getElementById('cmp-spinner').style.display = busy ? 'inline-block' : 'none';
  document.getElementById('cmp-send-btn').disabled = busy;
}

function showMsg(text, isError) {
  const el = document.getElementById('cmp-msg');
  el.textContent = text;
  el.style.display = 'block';
  el.style.color = isError ? 'var(--ms-danger)' : 'var(--ms-success)';
}

function debounce(fn, ms) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}
