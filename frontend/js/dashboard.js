/**
 * Bootstraps inbox.html — replaces client/src/pages/Dashboard.js. The actual
 * list/detail rendering logic lives in email.js (MailApp); this file just
 * wires up the page on load, matching the file split the spec calls for.
 */
(async () => {
  const user = await Auth.requireAuth();
  if (!user) return;
  await MailApp.init();
})();
