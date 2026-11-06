// The download button is a plain link to /download/app.apk (our own domain, stable URL);
// the server 302s it to a freshly signed storage URL. This script only fills in the
// version/size line from the public release manifest.
(() => {
  const meta = document.getElementById('meta');
  const toMb = (bytes) => (bytes / 1048576).toFixed(1) + ' MB';

  fetch('/api/app/release/latest', { cache: 'no-store' })
    .then((res) => (res.ok ? res.json() : null))
    .then((manifest) => {
      if (!manifest || !manifest.versionName) return;
      meta.textContent = 'v' + manifest.versionName
        + (manifest.sizeBytes ? ' · ' + toMb(manifest.sizeBytes) : '')
        + ' · Android 8.0+';
    })
    .catch(() => {});

  document.getElementById('year').textContent = new Date().getFullYear();
})();
