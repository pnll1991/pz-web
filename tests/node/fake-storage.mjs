// Fakes mínimos de la Cache API y de OPFS para probar las cachés del runtime en Node (sin navegador).
const keyOf = request => typeof request === 'string' ? request : request instanceof URL ? request.href : request.url;

export class FakeCache {
  constructor() { this.map = new Map(); }
  async match(request) { const hit = this.map.get(keyOf(request)); return hit ? new Response(hit.slice()) : undefined; }
  async put(request, response) { this.map.set(keyOf(request), new Uint8Array(await response.arrayBuffer())); }
  async keys() { return [...this.map.keys()].map(url => new Request(url)); }
  async delete(request) { return this.map.delete(keyOf(request)); }
}

export class FakeCaches {
  constructor() { this.stores = new Map(); }
  async open(name) { if (!this.stores.has(name)) this.stores.set(name, new FakeCache()); return this.stores.get(name); }
  async delete(name) { return this.stores.delete(name); }
}

export class FakeDirectory {
  constructor() { this.files = new Map(); this.writes = 0; }
  async getFileHandle(name, { create = false } = {}) {
    if (!this.files.has(name) && !create) throw new DOMException(`${name} not found`, 'NotFoundError');
    const dir = this;
    return {
      async getFile() { const bytes = dir.files.get(name) ?? new Uint8Array(0); return { size: bytes.length, arrayBuffer: async () => bytes.slice().buffer }; },
      async createWritable() {
        const chunks = [];
        return {
          async write(data) { chunks.push(new Uint8Array(data instanceof ArrayBuffer ? data.slice(0) : data)); },
          async close() { dir.files.set(name, new Uint8Array(Buffer.concat(chunks))); dir.writes++; },
          async abort() {},
        };
      },
    };
  }
  async removeEntry(name) { if (!this.files.delete(name)) throw new DOMException(`${name} not found`, 'NotFoundError'); }
  async *keys() { yield* [...this.files.keys()]; }
}

export const fakeStorage = dir => ({ getDirectory: async () => ({ getDirectoryHandle: async () => dir }) });
