// Round 83: direct mode (/play.html). One account per browser (localStorage), a generated first line for port/web/play.lua, the
// script mounted into the game's own Lua directory, and the click that "Click to Start" needs. The password never reaches a form.
const KEY = 'pzweb.play.v1';

const randomHex = (n, rand) => Array.from(rand(n), b => b.toString(16).padStart(2, '0')).join('').slice(0, n);
const cryptoBytes = n => globalThis.crypto.getRandomValues(new Uint8Array(n));

/** Account of this browser: created once, then reused (the server creates the account on its first login). */
export function playerIdentity(storage = globalThis.localStorage, rand = cryptoBytes) {
  try {
    const saved = JSON.parse(storage.getItem(KEY) || 'null');
    if (saved && /^[a-z0-9]{6,16}$/.test(saved.user) && /^[0-9a-f]{16,64}$/.test(saved.pass)) return saved;
  } catch { /* unreadable: replaced below */ }
  const alphabet = 'abcdefghjkmnpqrstuvwxyz23456789';
  const user = 'p' + Array.from(rand(6), b => alphabet[b % alphabet.length]).join('');
  const identity = { user, pass: randomHex(24, rand) };
  storage.setItem(KEY, JSON.stringify(identity));
  return identity;
}

const luaString = s => '"' + String(s).replace(/[\\"\n\r\0]/g, c => ({ '\\': '\\\\', '"': '\\"', '\n': '\\n', '\r': '\\r', '\0': '\\0' }[c])) + '"';

export function buildLua(template, { user, pass, host = '127.0.0.1', port = 16261, name = 'pzweb', acceptTos = true }) {
  return `PZWEB_AUTOJOIN = { user = ${luaString(user)}, pass = ${luaString(pass)}, host = ${luaString(host)}, port = ${Number(port) | 0}, name = ${luaString(name)}, acceptTos = ${acceptTos ? 'true' : 'false'} }\n${template}`;
}

/** Mounts the generated script as the last file of media/lua/client (the loader lists that directory in order). */
export async function installAutoJoin(vfs, query, { fetchImpl = globalThis.fetch.bind(globalThis), storage } = {}) {
  const q = new URLSearchParams(query);
  const identity = playerIdentity(storage);
  const response = await fetchImpl('/play.lua', { cache: 'no-cache' });
  if (!response.ok) throw new Error(`/play.lua unavailable: HTTP ${response.status}`);
  const lua = buildLua(await response.text(), {
    ...identity, host: q.get('host') || '127.0.0.1', port: q.get('port') || 16261, acceptTos: q.get('tos') !== '1',
  });
  vfs.mount('/media/lua/client/zzz_pzweb_play.lua', new TextEncoder().encode(lua));
  return { user: identity.user, state: 'installed', log: [] };
}

/** Watches the console for the script's PZWEB-STATE lines and for the game's "game loading took" line. */
export function watchConsole(auto, { consoleObj = globalThis.console, onState = () => {} } = {}) {
  for (const method of ['log', 'info', 'warn', 'error']) {
    const original = consoleObj[method]?.bind(consoleObj);
    if (!original) continue;
    consoleObj[method] = (...args) => {
      const text = typeof args[0] === 'string' ? args[0] : '';
      const m = /PZWEB-STATE:(\S.*)$/.exec(text);
      if (m) { auto.state = m[1]; auto.log.push([Date.now(), m[1]]); onState(m[1]); }
      else if (/game loading took \d+ seconds/.test(text)) { auto.state = 'loaded'; auto.log.push([Date.now(), 'loaded']); onState('loaded'); }
      original(...args);
    };
  }
}

/** The loading screen waits for a left-button press anywhere: hold it for a few frames, repeatedly, until the world starts. */
export function clickUntilIngame(auto, input, canvas, { intervalMs = 2500, holdMs = 250, maxMs = 5 * 60000, setTimeoutFn = setTimeout } = {}) {
  const started = Date.now();
  const tick = () => {
    if (auto.state === 'ingame' || Date.now() - started > maxMs) return;
    // The game polls the button state (Mouse.isButtonDown), so keep CanvasInput's own state in step with the events,
    // exactly like its pointerdown/pointerup handlers do.
    input.emit({ type: 'cursor', x: canvas.width / 2, y: canvas.height / 2, dx: 0, dy: 0 });
    input.buttons?.add(0); (input.latched ??= new Set()).add(0);
    input.emit({ type: 'button', button: 0, action: 1 });
    setTimeoutFn(() => { input.buttons?.delete(0); input.emit({ type: 'button', button: 0, action: 0 }); setTimeoutFn(tick, intervalMs); }, holdMs);
  };
  tick();
}
