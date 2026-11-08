// Ronda 92: relleno de los tramos de un pack perezoso fuera del hilo principal (ver fillShared en lazypack.mjs). Recibe una vez el índice del
// archivo y los SharedArrayBuffer de datos y estados; contesta { chunk: i } por cada tramo escrito y { done: true, ... } al terminar.
import { fillShared } from './lazypack.mjs';

self.onmessage = async ({ data }) => {
  const result = await fillShared({ ...data, post: message => self.postMessage(message) });
  self.postMessage({ done: true, ...result });
};
