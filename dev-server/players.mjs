// Panel de rendimiento por jugador: cada pestaña del juego manda su último resumen (port/runtime/perfhud.mjs) con
// GET /perf/report?u=<cuenta>&fps=&worst=&busy=&ping=&mem= cada 5 s; GET /perf devuelve todos los vivos más el estado
// de esta máquina (carga, memoria, puentes RakNet abiertos), que es lo que comparten todos los jugadores.
// Solo números acotados y un nombre de cuenta validado: nada de IPs, claves ni texto libre.
import os from 'node:os';

const NAME = /^[a-z0-9-]{1,20}$/;
const FIELDS = { fps: 1000, worst: 600000, busy: 100, ping: 600000, mem: 1 << 20 };

export function createPlayerBoard({ ttlMs = 30000, max = 64, now = () => Date.now(), bridges = () => null } = {}) {
  const players = new Map();
  const prune = t => { for (const [name, p] of players) if (t - p.at > ttlMs) players.delete(name); };

  /** Guarda un reporte; false si el nombre no es válido o ya hay demasiados jugadores. */
  function report(params) {
    const name = params.get('u'), t = now();
    if (!name || !NAME.test(name)) return false;
    prune(t);
    if (!players.has(name) && players.size >= max) return false;
    const previous = players.get(name);
    const entry = { name, at: t, gapS: previous ? Math.round((t - previous.at) / 100) / 10 : null };
    for (const [field, limit] of Object.entries(FIELDS)) {
      const raw = params.get(field), value = raw === null || raw === '' ? NaN : Number(raw);
      entry[field] = Number.isFinite(value) ? Math.min(limit, Math.max(0, Math.round(value * 10) / 10)) : null;
    }
    players.set(name, entry);
    return true;
  }

  function snapshot() {
    const t = now();
    prune(t);
    const list = [...players.values()].sort((a, b) => a.name.localeCompare(b.name))
      .map(p => ({ ...p, ageS: Math.round((t - p.at) / 100) / 10, at: undefined }));
    const [load1, load5] = os.loadavg();
    return { players: list, server: { load1: Math.round(load1 * 100) / 100, load5: Math.round(load5 * 100) / 100, cpus: os.cpus().length,
      freeMemMB: Math.round(os.freemem() / 1048576), totalMemMB: Math.round(os.totalmem() / 1048576),
      devServerMB: Math.round(process.memoryUsage().rss / 1048576), bridges: bridges() } };
  }

  return { report, snapshot, players };
}
