/**
 * Replaces client/src/context/SocketContext.js. Uses SockJS + STOMP.js
 * (loaded via CDN in each page's <head>) to connect to the Spring WebSocket
 * endpoint configured in WebSocketConfig.java, subscribe to
 * /user/queue/notifications, and dispatch the same three event types the
 * original Socket.IO client handled: new_email, mention, important_email.
 *
 * Usage on any page with the sidebar:
 *   Notifications.connect();
 *   Notifications.onNewEmail(email => { ... });
 *   Notifications.onMention((email, mentionedBy) => { ... });
 *   Notifications.onImportant(email => { ... });
 */
const Notifications = (() => {
  let stompClient = null;
  let notifications = [];
  let notifCount = 0;
  const listeners = { email: [], mention: [], important: [], count: [] };

  function push(type, message, email, extra = {}) {
    notifications = [{ id: Date.now() + Math.random(), type, message, email, timestamp: new Date(), ...extra }, ...notifications].slice(0, 25);
    notifCount += 1;
    listeners.count.forEach(cb => cb(notifCount, notifications));
  }

  function connect() {
    const user = Auth.getUser();
    const token = Auth.getToken();
    if (!user || !token || stompClient) return;

    // SockJS + @stomp/stompjs are loaded globally via CDN <script> tags in
    // each HTML page's <head> (window.SockJS, window.StompJs.Client).
    stompClient = new window.StompJs.Client({
      webSocketFactory: () => new window.SockJS(`${window.MAILFLOW_WS_BASE || '/ws'}?token=${encodeURIComponent(token)}`),
      reconnectDelay: 4000, // mirrors Socket.IO's auto-reconnect behavior
      onConnect: () => {
        stompClient.subscribe('/user/queue/notifications', (message) => {
          handleEnvelope(JSON.parse(message.body));
        });
      },
      onWebSocketClose: () => { /* reconnectDelay handles retry automatically */ },
    });
    stompClient.activate();
  }

  function handleEnvelope(envelope) {
    const { type, data } = envelope;
    if (type === 'new_email') {
      push('email', `New email from ${data.sender?.name || 'someone'}: ${data.subject}`, data);
      listeners.email.forEach(cb => cb(data));
    } else if (type === 'mention') {
      push('mention', `${data.mentionedBy} mentioned you in: ${data.email.subject}`, data.email);
      listeners.mention.forEach(cb => cb(data.email, data.mentionedBy));
    } else if (type === 'important_email') {
      push('important', `Important email from ${data.sender?.name}: ${data.subject}`, data);
      listeners.important.forEach(cb => cb(data));
    } else if (type === 'email_recalled') {
      listeners.email.forEach(cb => cb({ _recalled: true, ...data }));
    }
  }

  function disconnect() {
    if (stompClient) { try { stompClient.deactivate(); } catch (e) {} stompClient = null; }
  }

  return {
    connect,
    disconnect,
    getNotifications: () => notifications,
    getCount: () => notifCount,
    clearCount() { notifCount = 0; listeners.count.forEach(cb => cb(notifCount, notifications)); },
    onNewEmail: cb => listeners.email.push(cb),
    onMention: cb => listeners.mention.push(cb),
    onImportant: cb => listeners.important.push(cb),
    onCountChange: cb => listeners.count.push(cb),
  };
})();
