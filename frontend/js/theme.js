/* Common Net theme support - no dependencies */
(function () {
  const STORAGE_KEY = 'mailflow-theme';

  function getTheme() {
    return localStorage.getItem(STORAGE_KEY) === 'dark' ? 'dark' : 'light';
  }

  function applyTheme(theme) {
    const isDark = theme === 'dark';
    document.documentElement.classList.toggle('dark-theme', isDark);
    if (document.body) document.body.classList.toggle('dark-theme', isDark);
    document.documentElement.style.colorScheme = isDark ? 'dark' : 'light';

    document.querySelectorAll('.mf-theme-toggle').forEach(function (button) {
      button.innerHTML = isDark ? '☀️ <span>Light</span>' : '🌙 <span>Dark</span>';
      button.setAttribute('aria-label', isDark ? 'Switch to light mode' : 'Switch to dark mode');
      button.setAttribute('title', isDark ? 'Switch to light mode' : 'Switch to dark mode');
      button.setAttribute('aria-pressed', String(isDark));
    });
  }

  function toggle() {
    const next = getTheme() === 'dark' ? 'light' : 'dark';
    localStorage.setItem(STORAGE_KEY, next);
    applyTheme(next);
  }

  window.MailFlowTheme = { getTheme, applyTheme, toggle };

  /* Apply before the page renders to minimize theme flashing after refresh. */
  applyTheme(getTheme());

  document.addEventListener('DOMContentLoaded', function () {
    applyTheme(getTheme());

    /* Auth pages do not have the shared sidebar, so provide the same control
       in the existing page area without changing the auth layout. */
    // if (!document.getElementById('mf-sidebar') && !document.querySelector('.auth-theme-toggle')) {
    //   const button = document.createElement('button');
    //   button.type = 'button';
    //   button.className = 'mf-theme-toggle auth-theme-toggle';
    //   button.addEventListener('click', toggle);
    //   document.body.appendChild(button);
    //   applyTheme(getTheme());
    // }
  });
}());
