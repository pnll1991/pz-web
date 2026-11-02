// Contraseña de la cuenta admin del servidor PZ, leída del MISMO archivo que usará `scripts/mp-server.sh start`
// (PZ_MP_ADMIN_PASSWORD_FILE > ~/.config/pz-web/admin-pass > /tmp/pz82/admin.pw). Nunca se imprime ni se escribe en logs.
// Ojo: el servidor en marcha usa la que tenía al arrancar; tras rotarla, el login de admin vale recién después del reinicio.
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

export function adminPassword() {
  const script = resolve(import.meta.dirname, '../../scripts/mp-server.sh');
  const file = execFileSync('bash', [script, 'admin-pass-file'], { encoding: 'utf8' }).trim();
  return readFileSync(file, 'utf8').split('\n')[0].trim();
}
