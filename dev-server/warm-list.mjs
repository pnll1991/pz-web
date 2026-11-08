// Round 84: which /media requests did real game sessions make? Over a tunnel every synchronous read costs a round trip, so the page
// prefetches this list in parallel before the game starts (port/runtime/prefetch.mjs). Entries are [request url, Range header]; the
// list is only paths and byte ranges the game itself asked for (no file contents) and lives outside the repository.
// scripts/dev_server_8098.sh points PZWEB_WARM_FILE at ~/.local/share/pz-web/warm.json: /tmp is wiped on every reboot.
import { readFileSync, writeFileSync, renameSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

// The access key must never be stored: it travels as ?k= on requests made by cookie-less browsers.
export const stripKey = url => url.replace(/([?&])k=[^&]*&?/, '$1').replace(/[?&]$/, '');

export function createWarmList({ file = process.env.PZWEB_WARM_FILE || '/tmp/pzweb-warm.json', max = 20000 } = {}) {
  const entries = new Map();
  try { for (const [url, range] of JSON.parse(readFileSync(file, 'utf8'))) entries.set(`${url}|${range}`, [url, range]); } catch { /* none yet */ }
  let timer = null;
  const save = () => {
    clearTimeout(timer); timer = null;
    try { mkdirSync(dirname(file), { recursive: true }); writeFileSync(`${file}.tmp`, JSON.stringify([...entries.values()])); renameSync(`${file}.tmp`, file); } catch { /* best effort */ }
  };
  return {
    record(url, range = '') {
      url = stripKey(url);
      const key = `${url}|${range}`;
      if (entries.has(key) || entries.size >= max) return false;
      entries.set(key, [url, range]);
      timer ??= setTimeout(save, 2000).unref();
      return true;
    },
    list: () => [...entries.values()],
    size: () => entries.size,
    flush: save,
  };
}
