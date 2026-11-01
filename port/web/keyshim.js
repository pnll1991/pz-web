// Round 85: when the page was opened with ?k=<key> (public tunnel), add the same key to this site's own requests (fetch, XMLHttpRequest,
// WebSocket). The server also sets a cookie, but a browser that refuses cookies would otherwise get 403 on every request after the page.
// Classic script, runs before any module. Cross-origin URLs are never touched.
(function (root) {
  const k = new URLSearchParams(root.location.search).get('k');
  if (!k) return;
  const same = (input) => { try { const u = new URL(input, root.location.href); return u.host === root.location.host ? u : null; } catch { return null; } };
  const withKey = (input) => { const u = same(input); if (!u || u.searchParams.has('k')) return input; u.searchParams.set('k', k); return u.href; };
  const nativeFetch = root.fetch;
  root.fetch = function (input, init) {
    if (typeof input === 'string' || (typeof URL !== 'undefined' && input instanceof URL)) return nativeFetch.call(root, withKey(String(input)), init);
    if (typeof Request !== 'undefined' && input instanceof Request && same(input.url) && !new URL(input.url).searchParams.has('k')) return nativeFetch.call(root, new Request(withKey(input.url), input), init);
    return nativeFetch.call(root, input, init);
  };
  const nativeOpen = root.XMLHttpRequest.prototype.open;
  root.XMLHttpRequest.prototype.open = function (method, url, ...rest) { return nativeOpen.call(this, method, withKey(String(url)), ...rest); };
  const NativeWebSocket = root.WebSocket;
  root.WebSocket = class extends NativeWebSocket {
    constructor(url, protocols) { const u = same(String(url).replace(/^ws/, 'http')); if (u && !u.searchParams.has('k')) u.searchParams.set('k', k); super(u ? u.href.replace(/^http/, 'ws') : url, protocols); }
  };
})(typeof window !== 'undefined' ? window : globalThis);
