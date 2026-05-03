self.addEventListener('install', (e) => {
  self.skipWaiting();
});

self.addEventListener('push', (e) => {
  const options = {
    body: e.data.text(),
    icon: 'https://cdn-icons-png.flaticon.com/512/591/591564.png',
    badge: 'https://cdn-icons-png.flaticon.com/512/591/591564.png'
  };
  e.waitUntil(self.registration.showNotification('Clarity Reminder', options));
});