// Idioma del juego en el navegador (código propio; no copia nada del juego).
//
// El juego elige su idioma en Translator.getLanguage(): primero Core.getOptionLanguageName() (la línea `language=` de
// /Zomboid/options.ini), después System.getProperty("user.language") y por último el idioma por defecto (EN). Los idiomas que
// existen salen de Languages.init(): cada carpeta media/lua/shared/Translate/<CÓDIGO>/ con un language.json (javap -p -c de la
// versión fijada). En el port, /Zomboid/options.ini viene del pack y está montado de solo lectura (tools/pack/boot_options.py),
// así que el juego nunca lo guarda: lo que se elija en el menú de Opciones dura hasta recargar. Por eso el idioma se fija acá,
// antes de que arranque boot.wasm, reescribiendo solo la línea `language=` del options.ini montado.
//
// Orden (el primero válido gana):
//   1. ?lang=<CÓDIGO> en la URL: elección explícita, se guarda en localStorage (`pzweb.language`) para las próximas cargas.
//      ?lang=auto (o default) borra esa elección.
//   2. La elección guardada en localStorage.
//   3. El idioma que anuncia el dev server en /media-manifest.json (`language`, de PZWEB_LANGUAGE; por defecto ES).
//   4. DEFAULT_LANGUAGE (ES).
// Si el código elegido no tiene Translate/<CÓDIGO>/language.json en la VFS, no se toca options.ini (el juego seguiría igual con
// su propio idioma por defecto) y se avisa en la consola.

export const DEFAULT_LANGUAGE = 'ES';
export const LANGUAGE_STORAGE_KEY = 'pzweb.language';
export const OPTIONS_PATH = '/Zomboid/options.ini';
// Códigos de carpeta de Translate (EN, ES, ES_MX, PTBR...). Sin '=' ni saltos: ConfigFile corta cada línea en el primer '='.
const CODE = /^[A-Z][A-Z0-9_]{1,15}$/;
const CLEAR = new Set(['AUTO', 'DEFAULT']);

export function normalizeLanguage(value) {
  if (typeof value !== 'string') return null;
  const code = value.trim().toUpperCase();
  return CODE.test(code) ? code : null;
}

export const translatePath = code => `/media/lua/shared/Translate/${code}/language.json`;

/** Devuelve { code, source } con source = 'url' | 'saved' | 'server' | 'default'. Escribe/borra la elección explícita en `storage`. */
export function resolveLanguage({ search = '', storage = null, serverDefault = null, fallback = DEFAULT_LANGUAGE } = {}) {
  const asked = new URLSearchParams(search).get('lang');
  if (asked !== null) {
    const upper = asked.trim().toUpperCase();
    if (CLEAR.has(upper)) { try { storage?.removeItem(LANGUAGE_STORAGE_KEY); } catch {} }
    else {
      const code = normalizeLanguage(asked);
      if (code) {
        try { storage?.setItem(LANGUAGE_STORAGE_KEY, code); } catch {}
        return { code, source: 'url' };
      }
    }
  }
  let saved = null;
  try { saved = normalizeLanguage(storage?.getItem(LANGUAGE_STORAGE_KEY) ?? null); } catch {}
  if (saved) return { code: saved, source: 'saved' };
  const server = normalizeLanguage(serverDefault);
  if (server) return { code: server, source: 'server' };
  return { code: normalizeLanguage(fallback) ?? DEFAULT_LANGUAGE, source: 'default' };
}

/** options.ini con `language=<code>`: reemplaza la línea si está (la primera; las demás se quitan) o la agrega al final. */
export function withLanguage(text, code) {
  const valid = normalizeLanguage(code);
  if (!valid) throw new Error(`Código de idioma inválido: ${code}`);
  const lines = text.split('\n');
  let done = false;
  const out = [];
  for (const line of lines) {
    if (/^language=/.test(line.replace(/\r$/, ''))) {
      if (!done) { out.push(`language=${valid}${line.endsWith('\r') ? '\r' : ''}`); done = true; }
      continue;
    }
    out.push(line);
  }
  if (!done) {
    if (out.length && out[out.length - 1] === '') out.splice(out.length - 1, 0, `language=${valid}`);
    else out.push(`language=${valid}`, '');
  }
  return out.join('\n');
}

export const languageOf = text => /^language=([^\r\n]*)/m.exec(text)?.[1] ?? null;

/** Aplica el idioma al options.ini montado en la VFS. Devuelve un resumen para la consola; nunca finge: si no hay options.ini
 *  o falta la carpeta del idioma, no cambia nada y lo dice. */
export function applyLanguage(vfs, { code, source }) {
  if (!vfs.exists(OPTIONS_PATH)) return { applied: false, code, source, reason: 'sin options.ini' };
  if (!vfs.exists(translatePath(code))) return { applied: false, code, source, reason: `falta ${translatePath(code)}` };
  const before = new TextDecoder().decode(vfs.readFile(OPTIONS_PATH));
  const previous = languageOf(before);
  if (previous === code) return { applied: true, code, source, previous, changed: false };
  const after = new TextEncoder().encode(withLanguage(before, code));
  // El options.ini del pack es de solo lectura para el juego; se vuelve a montar igual (solo lectura) con la línea cambiada.
  if (vfs.isReadOnlyMounted?.(OPTIONS_PATH)) vfs.mountReadOnly(OPTIONS_PATH, after);
  else vfs.writeFile(OPTIONS_PATH, after);
  return { applied: true, code, source, previous, changed: true };
}
