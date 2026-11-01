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

  /**
   * HTTP gate. Returns false when the request may continue (local, or public with a valid cookie); true when this function already
   * answered (403, or the 302 that turns ?k= into the cookie).
   */
  function handle(req, res, headers) {
    if (!isPublic(req)) return false;
    const deny = message => { res.writeHead(403, { ...headers, 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(req.method === 'HEAD' ? undefined : message); return true; };
    if (!enabled) return deny('Public mode is off');
    if (!hostAllowed(req)) return deny('Forbidden');
    let url;
    try { url = new URL(req.url, `https://${req.headers.host}`); } catch { return deny('Forbidden'); }
    const k = url.searchParams.get('k');
    if (k !== null) {
      if (!sameKey(k)) return deny('Forbidden');
      url.searchParams.delete('k');
      res.writeHead(302, { ...headers, 'Cache-Control': 'no-store', Location: url.pathname + url.search,
        'Set-Cookie': `${COOKIE}=${encodeURIComponent(key)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000` });
      res.end();
      return true;
    }
    return cookieValid(req) ? false : deny('Forbidden');
  }

  /** WebSocket upgrade: public ones need the cookie and an Origin equal to https://<their own tunnel host>. */
  function wsAllowed(req) {
    if (!isPublic(req)) return null; // not public: the caller applies its normal loopback rules
    if (!enabled || !hostAllowed(req) || !cookieValid(req)) return false;
    return req.headers.origin === `https://${req.headers.host}`;
  }

  return { enabled, isPublic, hostAllowed, cookieValid, handle, wsAllowed };
}
