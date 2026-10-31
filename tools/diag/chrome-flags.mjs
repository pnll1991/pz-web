// Ronda 80: opciones de lanzamiento de Chrome comunes a todos los drivers de prueba.
// PZ_GPU=nvidia (por defecto) | swiftshader | <nombre de perfil en PROFILES>.
// PZ_CHROME_HEADED=1 fuerza ventana visible. PZ_CHROME_USERDIR fija el --user-data-dir (siempre bajo /tmp).
// Nunca usar el perfil del usuario: cada lanzamiento usa un directorio propio en /tmp.
import { mkdtempSync } from 'node:fs';

export const CHROME = process.env.PZ_CHROME || '/usr/bin/google-chrome';
const COMMON = ['--disable-dev-shm-usage', '--no-first-run', '--no-default-browser-check'];

// Perfiles probados en ronda 80 con tools/diag/gpu_check.mjs (ver docs/decisions.md).
export const PROFILES = {
  swiftshader: { headless: true, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] },
  // ANGLE sobre OpenGL del driver NVIDIA (backend por defecto de Chrome en Linux), headless funciona.
  nvidia: { headless: true, args: ['--enable-gpu', '--ignore-gpu-blocklist', '--use-angle=gl'] },
  // Alternativa: ANGLE sobre Vulkan del driver NVIDIA (también headless).
  'nvidia-vulkan': { headless: true, args: ['--enable-gpu', '--ignore-gpu-blocklist', '--use-angle=vulkan',
    '--enable-features=Vulkan', '--disable-vulkan-surface'] },
};

export function profileName() { return process.env.PZ_GPU || 'nvidia'; }

export function launchOptions(extra = {}) {
  const name = profileName();
  const p = PROFILES[name];
  if (!p) throw new Error(`PZ_GPU desconocido: ${name} (opciones: ${Object.keys(PROFILES).join(', ')})`);
  const headless = process.env.PZ_CHROME_HEADED === '1' ? false : p.headless;
  return { executablePath: CHROME, headless, args: [...COMMON, ...p.args, ...(extra.args || [])], env: { ...process.env, ...(p.env || {}) } };
}

// Directorio de perfil temporal propio (para launchPersistentContext).
export function userDataDir(tag = 'pzchrome') {
  return process.env.PZ_CHROME_USERDIR || mkdtempSync(`/tmp/${tag}-`);
}
