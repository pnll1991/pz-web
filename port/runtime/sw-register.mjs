// Ronda 87: registra el service worker de /sw.js sólo con ?sw=1 (apagado por defecto hasta validarlo en vivo con OK del dueño).
// ?sw=0 lo da de baja y vacía sus cachés (interruptor de emergencia). La versión de la caché de media es el hash del manifest de media.
export async function setupServiceWorker(query = location.search, nav = globalThis.navigator, fetchImpl = globalThis.fetch.bind(globalThis)) {
  const mode = new URLSearchParams(query).get('sw');
  if (!nav?.serviceWorker || (mode !== '1' && mode !== '0')) return { state: 'off' };
  if (mode === '0') {
    for (const reg of await nav.serviceWorker.getRegistrations()) { reg.active?.postMessage({ type: 'clear' }); await reg.unregister(); }
    return { state: 'unregistered' };
  }
  let m = '0';
  try {
    const bytes = new Uint8Array(await (await fetchImpl('/media-manifest.json')).arrayBuffer());
    m = [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].slice(0, 8).map(b => b.toString(16).padStart(2, '0')).join('');
  } catch { /* sin manifest: versión 0 */ }
  const reg = await nav.serviceWorker.register(`/sw.js?m=${m}`, { scope: '/', updateViaCache: 'none' });
  await nav.serviceWorker.ready;
  return { state: 'registered', media: m, reg };
}

/** Tras montar los packs: el worker descarta de su caché los que el índice ya no nombra. */
export function keepPacks(names, nav = globalThis.navigator) {
  nav?.serviceWorker?.controller?.postMessage({ type: 'keep-packs', names });
}
