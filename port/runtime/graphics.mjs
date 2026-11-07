// Calidad gráfica del juego en el navegador (código propio; no copia nada del juego).
//
// El juego lee sus opciones gráficas de /Zomboid/options.ini en Core.loadOptions(). En el port ese archivo viene del pack y
// está montado de solo lectura (tools/pack/boot_options.py): lo que se cambie en Opciones dura hasta recargar. Por eso, como
// el idioma (language.mjs), la calidad se fija acá, antes de boot.wasm, reescribiendo solo las líneas de abajo.
//
// Nombres, rangos y significado de cada valor: constructores de ConfigOption en Core.<init> y los combos de
// media/lua/client/OptionScreens/MainOptions.lua de la versión fijada (javap -p -c y lectura local; nada se copia).
// Son opciones de presentación del propio menú de Opciones: no cambian reglas del juego.
//
// Orden (el primero válido gana):
//   1. ?calidad=minima|juego en la URL: se guarda en localStorage (`pzweb.quality`). ?calidad=auto borra la elección.
//   2. La elección guardada.
//   3. DEFAULT_QUALITY (minima).
// `juego` deja el options.ini como viene (valores por defecto del juego).

export const DEFAULT_QUALITY = 'minima';
export const QUALITY_STORAGE_KEY = 'pzweb.quality';
export const OPTIONS_PATH = '/Zomboid/options.ini';
const QUALITIES = new Set(['minima', 'juego']);
const CLEAR = new Set(['auto', 'default']);

/** La opción de options.ini, su valor de calidad mínima y qué es (por defecto del juego entre paréntesis). */
export const MINIMUM_OPTIONS = Object.freeze([
  ['fogQuality', '2'],             // niebla: 0 alta, 1 media, 2 "Legacy (Build 40)" (0)
  ['water', '2'],                  // agua: 0 alta, 1 media, 2 baja (0)
  ['puddles', '2'],                // charcos: 0 alta, 1 media, 2 baja (0)
  ['perfPuddles', '3'],            // dónde hay charcos: 0 todo ... 3 ninguno (0)
  ['perfSkybox', '2'],             // cielo: 0 alto, 1 medio, 2 no (1)
  ['bPerfReflections', 'false'],   // reflejos (true)
  ['lightFPS', '10'],              // fps de la iluminación, 5..60 (15)
  ['uiRenderFPS', '30'],           // fps de la interfaz fuera de pantalla, 10..120 (60)
  ['corpseShadows', 'false'],      // sombras de cadáveres (true)
  ['bloodDecals', '0'],            // manchas de sangre, 0..10 (10)
  ['3DGroundItem', 'false'],       // objetos del suelo en 3D (true)
  ['highResPlacedItems', 'false'], // objetos colocados en alta resolución (true)
  ['simpleClothingTextures', '3'], // ropa simplificada: 1 nadie, 2 zombis, 3 jugadores y zombis (1)
  ['renderPrecipitation', '3'],    // lluvia y nieve: 1 siempre, 2 solo afuera, 3 nunca (1)
  ['doDoorSpriteEffects', 'false'],// efectos de puertas (true)
  ['doContainerOutline', 'false'], // contorno de contenedores (true)
]);

export function normalizeQuality(value) {
  if (typeof value !== 'string') return null;
  const q = value.trim().toLowerCase().replace('í', 'i');
  return QUALITIES.has(q) ? q : null;
}

/** Devuelve { quality, source } con source = 'url' | 'saved' | 'default'. Escribe/borra la elección explícita en `storage`. */
export function resolveQuality({ search = '', storage = null } = {}) {
  const asked = new URLSearchParams(search).get('calidad');
  if (asked !== null) {
    if (CLEAR.has(asked.trim().toLowerCase())) { try { storage?.removeItem(QUALITY_STORAGE_KEY); } catch {} }
    else {
      const quality = normalizeQuality(asked);
      if (quality) {
        try { storage?.setItem(QUALITY_STORAGE_KEY, quality); } catch {}
        return { quality, source: 'url' };
      }
    }
  }
  let saved = null;
  try { saved = normalizeQuality(storage?.getItem(QUALITY_STORAGE_KEY) ?? null); } catch {}
  if (saved) return { quality: saved, source: 'saved' };
  return { quality: DEFAULT_QUALITY, source: 'default' };
}

/** options.ini con cada `name=value` de `options`: reemplaza la primera línea de cada uno (quita repetidas) o la agrega al final. */
export function withOptions(text, options) {
  const wanted = new Map(options);
  for (const [k, v] of wanted) if (/[=\r\n]/.test(k) || /[=\r\n]/.test(v)) throw new Error(`Opción inválida: ${k}=${v}`);
  const done = new Set();
  const out = [];
  for (const line of text.split('\n')) {
    const bare = line.replace(/\r$/, ''), name = bare.split('=')[0];
    if (!bare.startsWith('#') && bare.includes('=') && wanted.has(name)) {
      if (!done.has(name)) { out.push(`${name}=${wanted.get(name)}${line.endsWith('\r') ? '\r' : ''}`); done.add(name); }
      continue;
    }
    out.push(line);
  }
  const missing = [...wanted].filter(([k]) => !done.has(k)).map(([k, v]) => `${k}=${v}`);
  if (missing.length) {
    if (out.length && out[out.length - 1] === '') out.splice(out.length - 1, 0, ...missing);
    else out.push(...missing, '');
  }
  return out.join('\n');
}

/** Aplica la calidad al options.ini montado en la VFS. Devuelve un resumen para la consola; sin options.ini no cambia nada. */
export function applyQuality(vfs, { quality, source }) {
  if (quality === 'juego') return { applied: false, quality, source, reason: 'valores del juego' };
  if (!vfs.exists(OPTIONS_PATH)) return { applied: false, quality, source, reason: 'sin options.ini' };
  const before = new TextDecoder().decode(vfs.readFile(OPTIONS_PATH));
  const text = withOptions(before, MINIMUM_OPTIONS);
  if (text === before) return { applied: true, quality, source, changed: false };
  const after = new TextEncoder().encode(text);
  if (vfs.isReadOnlyMounted?.(OPTIONS_PATH)) vfs.mountReadOnly(OPTIONS_PATH, after);
  else vfs.writeFile(OPTIONS_PATH, after);
  return { applied: true, quality, source, changed: true, options: MINIMUM_OPTIONS.length };
}
