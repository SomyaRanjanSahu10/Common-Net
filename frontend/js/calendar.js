/** Replaces client/src/components/CalendarView.js. */
window.CalendarApp = {
  current: new Date(),
  holidays: [],
  scheduledEmails: [],
  meetings: [],

  async init() {
    document.getElementById('cal-prev').onclick = () => this.shiftMonth(-1);
    document.getElementById('cal-next').onclick = () => this.shiftMonth(1);
    document.getElementById('cal-today').onclick = () => { this.current = new Date(); this.load(); };
    await this.load();
  },

  async shiftMonth(delta) {
    this.current = new Date(this.current.getFullYear(), this.current.getMonth() + delta, 1);
    await this.load();
  },

  async load() {
    const year = this.current.getFullYear();
    try {
      const [holidayRes, scheduledRes, meetingRes] = await Promise.all([
        api.get(`/calendar/holidays/${year}`),
        api.get('/email/scheduled'),
        api.get('/meetings'),
      ]);
      this.holidays = holidayRes.events || [];
      this.scheduledEmails = scheduledRes.emails || [];
      this.meetings = meetingRes.meetings || [];
    } catch (e) { /* non-fatal */ }
    this.render();
  },

  render() {
    const monthLabel = this.current.toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
    document.getElementById('cal-month-label').textContent = monthLabel;

    const year = this.current.getFullYear();
    const month = this.current.getMonth();
    const firstDay = new Date(year, month, 1);
    const startOffset = firstDay.getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const today = new Date();

    const eventsByDate = {};
    this.holidays.forEach(h => { (eventsByDate[h.date] = eventsByDate[h.date] || []).push({ name: h.name, type: h.type }); });
    this.scheduledEmails.forEach(e => {
      if (!e.scheduledTime) return;
      const d = new Date(e.scheduledTime).toISOString().slice(0, 10);
      (eventsByDate[d] = eventsByDate[d] || []).push({ name: '' + (e.subject || 'Scheduled email'), type: 'scheduled' });
    });
    this.meetings.forEach(m => {
      if (!m.startTime) return;
      const d = new Date(m.startTime).toISOString().slice(0, 10);
      const participantCount = Array.isArray(m.participants) ? m.participants.length : 0;
      (eventsByDate[d] = eventsByDate[d] || []).push({
        name: 'Meeting: ' + (m.title || 'Untitled') + (participantCount ? ` (${participantCount + 1})` : ''),
        type: 'meeting'
      });
    });

    let cells = '';
    for (let i = 0; i < startOffset; i++) cells += `<div class="cal-cell" style="visibility:hidden;"></div>`;
    for (let day = 1; day <= daysInMonth; day++) {
      const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const isToday = today.getFullYear() === year && today.getMonth() === month && today.getDate() === day;
      const events = eventsByDate[dateStr] || [];
      cells += `
        <div class="cal-cell ${isToday ? 'today' : ''}">
          <div class="cal-num">${day}</div>
          ${events.map(ev => `<div class="cal-event ${ev.type === 'holiday' ? 'holiday' : ''}" title="${escapeHtmlCal(ev.name)}">${escapeHtmlCal(ev.name)}</div>`).join('')}
        </div>`;
    }
    document.getElementById('cal-grid').innerHTML = cells;
  },
};

function escapeHtmlCal(s) {
  if (s == null) return '';
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
