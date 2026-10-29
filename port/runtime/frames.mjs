/**
 * Round 47: real Java frames for port.compat.java.lang.StackWalker.
 *
 * TeaVM 0.16.0 `TThread.getStackTrace()` returns an empty array, so a StackWalker built on it walks
 * zero frames and callers such as `zombie.ai.State$Param.<init>` get `null` for the declaring class
 * and then trap inside `Class.isAssignableFrom(null)`. Chrome unwinds Wasm frames into the V8 stack,
 * and because boot.wasm carries a complete `name` custom section each line already reads
 * `at zombie.ai.State$Param::<init> (…:wasm-function[459582]:0x44d86fb)`. This module reads that
 * stack synchronously (no exception is constructed inside Wasm, so no fiber can suspend) and returns
 * only the Java frames of the caller, never a fabricated frame.
 */
const HIDDEN = /^(java\.lang\.(Thread|Throwable)|java\.lang\.StackWalker|java\.lang\.Throwable\$FakeClass|port\.compat\.java\.lang\.StackWalker|port\.shim\.BrowserStack)$/;

function symbol(line) {
  const at = line.trimStart();
  if (!at.startsWith('at ')) return null;
  const name = at.slice(3).split(' (')[0].trim();
  return name.includes('::') ? name : null;
}

/** Java frames below the StackWalker call site, innermost first, as `com.Example::method`. */
export function javaFrames(limit = 64) {
  let frames = [];
  const previous = Error.stackTraceLimit;
  try {
    // Wasm frames are collected only while the limit allows them, so capping here keeps the
    // capture cheap even in the render loop, where the fiber stack is deep.
    Error.stackTraceLimit = Math.max(4, limit | 0);
    frames = String(new Error().stack || '').split('\n').map(symbol).filter((n) => n !== null);
  } catch {
    frames = [];
  } finally {
    Error.stackTraceLimit = previous;
  }
  while (frames.length && HIDDEN.test(frames[0].split('::')[0])) frames.shift();
  return frames;
}
