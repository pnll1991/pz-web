// Pantalla de carga de game.html. La primera vez el navegador baja cientos de MB (boot.wasm, los packs, los
// rangos de /media) y la página solo decía "Comprobando la compilación del juego…". Esto muestra qué se está haciendo, con
// una barra real cuando hay bytes que contar (los packs: el índice trae el tamaño de cada uno) y una animación
// indeterminada cuando no (compilar boot.wasm, arrancar Java). Se va sola con el primer cuadro que dibuja el juego.

/** Reads a Response body counting bytes as they arrive; returns the same ArrayBuffer arrayBuffer() would. */
export async function readWithProgress(response, expectedBytes, onBytes = () => {}) {
  if (!response.body || !Number.isSafeInteger(expectedBytes) || expectedBytes < 0) {
    const bytes = await response.arrayBuffer();
    onBytes(bytes.byteLength);
    return bytes;
  }
  // Preallocated to the size the pack index promises: no chunk list and no second copy. A body that is longer than
  // promised falls back to collecting chunks, so the caller's size check still sees the real length.
  let buffer = new Uint8Array(expectedBytes), length = 0;
  const reader = response.body.getReader();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    if (length + value.byteLength > buffer.byteLength) {
      const grown = new Uint8Array(Math.max(buffer.byteLength * 2, length + value.byteLength));
      grown.set(buffer.subarray(0, length));
      buffer = grown;
    }
    buffer.set(value, length);
    length += value.byteLength;
    onBytes(value.byteLength);
  }
  return length === buffer.byteLength ? buffer.buffer : buffer.buffer.slice(0, length);
}

export const formatMB = bytes => `${Math.round(bytes / 1e6).toLocaleString('es-AR')} MB`;

const STEPS = [
  ['build', 'Comprobando la versión del juego'],
  ['engine', 'Motor del juego'],
  ['packs', 'Recursos del juego'],
  ['prepare', 'Preparando gráficos, guardado y física'],
  ['prefetch', 'Precargando el mapa'],
  ['start', 'Iniciando Project Zomboid'],
];

const CSS = `
#pz-loading{position:fixed;inset:0;z-index:4;display:grid;place-items:center;padding:24px;background:radial-gradient(ellipse at 50% 35%,#132027 0%,#0b1116 60%,#06090c 100%);color:#e9eeec;font-family:system-ui,-apple-system,sans-serif;transition:opacity .45s ease}
#pz-loading.pz-out{opacity:0;pointer-events:none}
#pz-loading .pz-card{width:min(560px,100%)}
#pz-loading .pz-eyebrow{font:11px ui-monospace,monospace;letter-spacing:2px;color:#77cbae;margin:0 0 14px}
#pz-loading h1{font-size:clamp(28px,4vw,40px);line-height:1.1;letter-spacing:-1px;font-weight:600;margin:0 0 28px}
#pz-loading .pz-bar{position:relative;height:6px;border-radius:3px;background:#1a262c;overflow:hidden}
#pz-loading .pz-fill{position:absolute;inset:0 auto 0 0;width:0;background:#74d9af;border-radius:3px;transition:width .25s linear}
#pz-loading .pz-bar.pz-indet .pz-fill{width:30%;animation:pz-slide 1.4s ease-in-out infinite}
@keyframes pz-slide{0%{left:-30%}100%{left:100%}}
#pz-loading .pz-meta{display:flex;justify-content:space-between;gap:12px;margin:10px 0 30px;font-size:13px;color:#a5b1b8}
#pz-loading .pz-meta b{font:12px ui-monospace,monospace;color:#c9d6d0;font-weight:400;white-space:nowrap}
#pz-loading ol{list-style:none;margin:0;padding:0;display:grid;gap:10px}
#pz-loading li{display:flex;gap:12px;align-items:center;font-size:13px;color:#5f7179}
#pz-loading li[hidden]{display:none}
#pz-loading li i{flex:none;width:14px;height:14px;border-radius:50%;border:1.5px solid #34444a}
#pz-loading li span{flex:1}
#pz-loading li small{font:11px ui-monospace,monospace;color:#7d8f96}
#pz-loading li.pz-active{color:#e9eeec}
#pz-loading li.pz-active i{border-color:#74d9af;border-right-color:transparent;animation:pz-spin .8s linear infinite}
#pz-loading li.pz-done{color:#94a6a1}
#pz-loading li.pz-done i{border-color:#74d9af;background:#74d9af;box-shadow:inset 0 0 0 3px #0b1116}
#pz-loading li.pz-failed{color:#f5b29e}
#pz-loading li.pz-failed i{border-color:#db8e77;background:#db8e77}
@keyframes pz-spin{to{transform:rotate(360deg)}}
#pz-loading .pz-note{margin:30px 0 0;font-size:12px;line-height:1.6;color:#6f8780}
#pz-loading pre{margin:20px 0 0;max-height:200px}
@media (prefers-reduced-motion:reduce){#pz-loading *{animation-duration:3s!important}}`;

/** Full-page loading overlay. Every method is a no-op after finish(), so late progress events cannot bring it back. */
export class LoadingScreen {
  constructor(doc = document) {
    this.doc = doc;
    this.finished = false;
    const style = doc.createElement('style');
    style.textContent = CSS;
    doc.head.appendChild(style);
    this.root = doc.createElement('div');
    this.root.id = 'pz-loading';
    this.root.setAttribute('role', 'status');
    this.root.innerHTML = `<div class="pz-card"><p class="pz-eyebrow">PROJECT ZOMBOID · NAVEGADOR</p><h1>Preparando el juego</h1>
<div class="pz-bar pz-indet" role="progressbar" aria-valuemin="0" aria-valuemax="100"><div class="pz-fill"></div></div>
<div class="pz-meta"><span class="pz-label">Comprobando la versión del juego…</span><b class="pz-right"></b></div>
<ol>${STEPS.map(([id, text]) => `<li data-step="${id}"${id === 'prefetch' ? ' hidden' : ''}><i></i><span>${text}</span><small></small></li>`).join('')}</ol>
<p class="pz-note">La primera vez se descarga bastante; el navegador lo guarda y los arranques siguientes son mucho más rápidos. No cierres esta pestaña.</p></div>`;
    doc.body.appendChild(this.root);
    this.barEl = this.root.querySelector('.pz-bar');
    this.fillEl = this.root.querySelector('.pz-fill');
    this.labelEl = this.root.querySelector('.pz-label');
    this.rightEl = this.root.querySelector('.pz-right');
    this.titleEl = this.root.querySelector('h1');
    this.noteEl = this.root.querySelector('.pz-note');
  }

  /** state: 'pending' | 'active' | 'done' | 'failed'. detail goes on the right of the step (a size, a percentage). */
  step(id, state, detail) {
    if (this.finished) return;
    const item = this.root.querySelector(`li[data-step="${id}"]`);
    if (!item) return;
    item.hidden = false;
    item.className = state === 'pending' ? '' : `pz-${state}`;
    if (detail !== undefined) item.querySelector('small').textContent = detail;
  }

  /** fraction in [0, 1] draws a real bar; null draws the indeterminate one. */
  bar(fraction, label, right = '') {
    if (this.finished) return;
    const known = typeof fraction === 'number' && Number.isFinite(fraction);
    this.barEl.classList.toggle('pz-indet', !known);
    this.fillEl.style.width = known ? `${Math.max(0, Math.min(1, fraction)) * 100}%` : '';
    if (known) this.barEl.setAttribute('aria-valuenow', String(Math.round(fraction * 100)));
    else this.barEl.removeAttribute('aria-valuenow');
    if (label !== undefined) this.labelEl.textContent = label;
    this.rightEl.textContent = right;
  }

  fail(message) {
    if (this.finished) return;
    for (const item of this.root.querySelectorAll('li.pz-active')) item.className = 'pz-failed';
    this.titleEl.textContent = 'No se pudo arrancar el juego';
    this.barEl.hidden = true;
    this.labelEl.textContent = 'Recargá la página para reintentar. Si se repite, pasale este mensaje a quien administra el servidor.';
    this.rightEl.textContent = '';
    this.noteEl.hidden = true;
    const pre = this.root.querySelector('pre') ?? this.root.querySelector('.pz-card').appendChild(this.doc.createElement('pre'));
    pre.textContent = message;
  }

  finish() {
    if (this.finished) return;
    this.finished = true;
    this.root.classList.add('pz-out');
    setTimeout(() => this.root.remove(), 500);
  }
}
