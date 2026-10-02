const CACHE = 'area-estimator-v12-20261002-10';
const ASSETS = [
  './', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png',
  './vision_bundle.mjs', './hand_landmarker.task', './magic_touch.tflite',
  './wasm/vision_wasm_internal.js', './wasm/vision_wasm_internal.wasm',
  './wasm/vision_wasm_module_internal.js', './wasm/vision_wasm_module_internal.wasm',
  './wasm/vision_wasm_nosimd_internal.js', './wasm/vision_wasm_nosimd_internal.wasm'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(ASSETS)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(
    keys.filter(key => key.startsWith('area-estimator-') && key !== CACHE).map(key => caches.delete(key))
  )).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (event.request.mode === 'navigate') {
    event.respondWith(fetch(event.request).then(response => {
      if (response.ok) caches.open(CACHE).then(cache => cache.put('./index.html', response.clone()));
      return response;
    }).catch(() => caches.match('./index.html')));
  } else {
    event.respondWith(caches.match(event.request).then(hit => hit || fetch(event.request).then(response => {
      if (response.ok) caches.open(CACHE).then(cache => cache.put(event.request, response.clone()));
      return response;
    })));
  }
});
