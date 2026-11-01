// Round 84: optional public mode for the local dev server (Cloudflare quick tunnel, scripts/public-tunnel.sh).
// A request is "public" when it carries cf-connecting-ip or its Host is not 127.0.0.1/localhost. Public requests need the
// shared key: ?k=<key> sets an HttpOnly/Secure/SameSite=Lax cookie and redirects to the same URL without k; afterwards the cookie
// is the credential (compared in constant time). Without PZWEB_PUBLIC_KEY, public-looking requests are refused outright.
// Local requests (Host 127.0.0.1/localhost, no cf-connecting-ip) are never touched by any of this.
import { createHash, timingSafeEqual } from 'node:crypto';

export const COOKIE = 'pzweb_k';
const LOCAL_HOST = /^(127\.0\.0\.1|localhost)(:\d+)?$/i;
const TUNNEL_HOST = /^[a-z0-9-]+\.trycloudflare\.com$/i;

export function createPublicGate({ key = process.env.PZWEB_PUBLIC_KEY || '' } = {}) {
  const enabled = typeof key === 'string' && key.length >= 16;
  const digest = value => createHash('sha256').update(String(value)).digest();
  const keyDigest = enabled ? digest(key) : null;
  const sameKey = value => enabled && typeof value === 'string' && value.length > 0 && timingSafeEqual(digest(value), keyDigest);

  const isPublic = req => Boolean(req.headers['cf-connecting-ip']) || !LOCAL_HOST.test(req.headers.host || '');
  const hostAllowed = req => TUNNEL_HOST.test(req.headers.host || '');
  const cookieValid = req => {
    for (const part of String(req.headers.cookie || '').split(';')) {
      const i = part.indexOf('=');
      if (i > 0 && part.slice(0, i).trim() === COOKIE) return sameKey(decodeURIComponent(part.slice(i + 1).trim()));
    }
    return false;
  };

  // Our own code and the page chrome carry nothing private, and the browser fetches them with no way to add the key
  // (module imports, favicon): public. The game's files (wasm, packs, media) and the pages themselves stay behind the key.
  const PUBLIC_STATIC = /^\/(favicon\.ico|style\.css|boot\.wasm-runtime\.js|support\.js|keyshim\.js|[A-Za-z0-9_.-]+\.mjs|runtime\/[A-Za-z0-9_.-]+\.mjs|vendor\/.+\.m?js)$/;
  const keyInQuery = url => sameKey(url.searchParams.get('k'));
  const cookieHeader = () => `${COOKIE}=${encodeURIComponent(key)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000`;

  /**
   * HTTP gate. Returns false when the request may continue (local, a public static file, or public with a valid credential);
   * true when this function already answered (403). The credential is the cookie OR ?k=<key> (a fallback for browsers that
   * refuse cookies); a valid ?k= also sets the cookie, on whatever page it arrives, and the request is served as usual.
   */
  function handle(req, res, headers) {
    if (!isPublic(req)) return false;
    const deny = message => { res.writeHead(403, { ...headers, 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(req.method === 'HEAD' ? undefined : message); return true; };
    if (!enabled) return deny('Public mode is off');
    if (!hostAllowed(req)) return deny('Forbidden');
    let url;
    try { url = new URL(req.url, `https://${req.headers.host}`); } catch { return deny('Forbidden'); }
    if (url.searchParams.has('k')) {
      if (!keyInQuery(url)) return deny('Forbidden');
      if (!cookieValid(req)) res.setHeader('Set-Cookie', cookieHeader());
      return false;
    }
    if (cookieValid(req) || PUBLIC_STATIC.test(url.pathname)) return false;
    return deny('Forbidden');
  }

  /** WebSocket upgrade: public ones need the cookie (or ?k=) and an Origin equal to https://<their own tunnel host>. */
  function wsAllowed(req) {
    if (!isPublic(req)) return null; // not public: the caller applies its normal loopback rules
    if (!enabled || !hostAllowed(req)) return false;
    let url; try { url = new URL(req.url, `https://${req.headers.host}`); } catch { return false; }
    if (!cookieValid(req) && !keyInQuery(url)) return false;
    return req.headers.origin === `https://${req.headers.host}`;
  }

  return { enabled, isPublic, hostAllowed, cookieValid, handle, wsAllowed };
}
