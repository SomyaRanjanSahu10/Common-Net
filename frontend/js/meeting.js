window.MeetingScheduler = {
  open() {
    if (document.getElementById('mf-meeting-overlay')) return;
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.id = 'mf-meeting-overlay';
    overlay.innerHTML = `
      <div class="meeting-modal modal-card">
        <div class="compose-header">
          <h3>Schedule Meeting</h3>
          <button id="mt-close" aria-label="Close">\u2715</button>
        </div>
        <div id="mt-msg" style="display:none;padding:8px 20px;font-size:12.5px;"></div>
        <div class="meeting-form">
          <label>Title<input id="mt-title" class="form-input" placeholder="Meeting title"></label>
          <label>Date &amp; time<input id="mt-time" class="form-input" type="datetime-local"></label>
          <label>Duration
            <select id="mt-duration" class="form-input">
              <option value="15">15 minutes</option><option value="30" selected>30 minutes</option>
              <option value="45">45 minutes</option><option value="60">1 hour</option>
              <option value="90">1.5 hours</option><option value="120">2 hours</option>
            </select>
          </label>
          <label>Participants
            <div class="meeting-user-picker">
              <input id="mt-user-search" class="form-input" autocomplete="off" placeholder="Search users to add">
              <div id="mt-user-results" class="meeting-user-results" style="display:none;"></div>
              <div id="mt-selected-users" class="meeting-selected-users"></div>
            </div>
          </label>
          <label>Description<textarea id="mt-description" class="form-input meeting-description" placeholder="Optional description"></textarea></label>
        </div>
        <div class="compose-footer">
          <button class="btn btn-primary" id="mt-schedule">Schedule</button>
          <button class="btn btn-secondary" id="mt-cancel">Cancel</button>
          <span class="spacer"></span><span id="mt-spinner" style="display:none;" class="spinner spinner-dark"></span>
        </div>
      </div>`;
    overlay.onclick = e => { if (e.target === overlay) this.close(); };
    document.body.appendChild(overlay);
    wireMeeting();
  },
  close() { document.getElementById('mf-meeting-overlay')?.remove(); }
};

function wireMeeting() {
  const state = { selected: [], users: [] };
  const now = new Date(Date.now() + 60000);
  document.getElementById('mt-time').min = now.toISOString().slice(0,16);
  document.getElementById('mt-close').onclick = () => MeetingScheduler.close();
  document.getElementById('mt-cancel').onclick = () => MeetingScheduler.close();

  const search = document.getElementById('mt-user-search');
  search.addEventListener('input', debounce(async () => {
    const q = search.value.trim();
    if (q.length < 2) { document.getElementById('mt-user-results').style.display='none'; return; }
    try {
      const res = await api.get('/users/search', {q});
      state.users = res.users || [];
      const box = document.getElementById('mt-user-results');
      box.innerHTML = state.users.filter(u => !state.selected.some(s=>s.id===u.id)).map(u =>
        `<div class="meeting-user-result" data-id="${escapeHtml(u.id)}"><strong>${escapeHtml(u.name)}</strong><span>${escapeHtml(u.email)}</span></div>`).join('');
      box.style.display = box.innerHTML ? 'block' : 'none';
      box.querySelectorAll('.meeting-user-result').forEach(el => el.onclick = () => {
        const u=state.users.find(x=>x.id===el.dataset.id); if(!u) return;
        if(!state.selected.some(x=>x.id===u.id)) state.selected.push(u);
        search.value=''; box.style.display='none'; renderSelectedMeetingUsers(state);
      });
    } catch(e) {}
  },250));

  document.getElementById('mt-schedule').onclick = async () => {
    const title=document.getElementById('mt-title').value.trim();
    const local=document.getElementById('mt-time').value;
    if(!title || !local){ showMeetingMsg('Meeting title and date/time are required.',true); return; }
    const start=new Date(local);
    if(isNaN(start) || start<=new Date()){ showMeetingMsg('Please select a future date and time.',true); return; }
    setMeetingBusy(true);
    try {
      await api.post('/meetings', {
        title, startTime:start.toISOString(),
        durationMinutes:Number(document.getElementById('mt-duration').value),
        description:document.getElementById('mt-description').value,
        participants:state.selected.map(u=>({id:u.id}))
      });
      showMeetingMsg('Meeting scheduled successfully!',false);
      setTimeout(()=>MeetingScheduler.close(),500);
      if(window.CalendarApp) window.CalendarApp.load();
    } catch(e) { showMeetingMsg(e.message || 'Failed to schedule meeting.',true); }
    finally { setMeetingBusy(false); }
  };
}
function renderSelectedMeetingUsers(state){
  const box=document.getElementById('mt-selected-users');
  box.innerHTML=state.selected.map((u,i)=>
    `<span class="meeting-user-chip">${escapeHtml(u.name)} <small>${escapeHtml(u.email)}</small><button data-i="${i}" aria-label="Remove ${escapeHtml(u.name)}">\u2715</button></span>`).join('');
  box.querySelectorAll('button').forEach(b=>b.onclick=()=>{state.selected.splice(+b.dataset.i,1);renderSelectedMeetingUsers(state);});
}
function showMeetingMsg(msg,error){const el=document.getElementById('mt-msg');el.textContent=msg;el.style.display='block';el.style.color=error?'#a4262c':'var(--ms-text-secondary)';}
function setMeetingBusy(v){document.getElementById('mt-spinner').style.display=v?'inline-block':'none';document.getElementById('mt-schedule').disabled=v;}
