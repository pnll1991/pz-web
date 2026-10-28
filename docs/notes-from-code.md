# Notas de Code: crash `Fiber::popInt <- Thread::currentThread <- Fiber::suspend`

Fecha: 2026-10-28. Autor: Code (Grok Bot), ayudando a Gaming. No se tocó ningún archivo del repo salvo este.

## TL;DR
- **No es un problema de dos fibers ni de `lastPendingCall`.** TeaVM 0.16.0 vanilla (WasmGC) soporta bien dos fibers alternando con wait/notify + `@Async` + `Thread.yield()`. El repro lo demuestra.
- **Causa raíz:** `java.lang.Thread::currentThread` y `::setCurrentThread` quedaron transformadas como **corutinas**, porque `Thread.<clinit>` es async. Es async porque `Thread.<init>` llama (vía la inyección de `JdkCompatibility.threads()`) al `ThreadGroup` compat, que usa `synchronized` + `WeakHashMap<Thread,…>`. `Fiber.suspend` llama `Thread.currentThread()` *durante el resume*, y el prólogo de la corutina hace `popInt` de un estado que nunca pusheó. El resultado es `array element access out of bounds`. Crashea **incluso con un solo fiber**.
- **Fix:** que nada alcanzable desde `Thread.<init>/<clinit>/start/isAlive/getThreadGroup` use monitores ni `hashCode/equals` virtuales. Eso deja sincrónicos `<clinit>`, `currentThread` y `setCurrentThread`. Además, agregar un gate de build que falle si esas funciones contienen `Fiber::popInt`.
- El `ResumeEvent` de la ronda 33 (resume diferido vía `EventQueue.offer`) **no arregla nada**. Probado: mismo crash.

## Causa raíz (con referencias a TeaVM 0.16.0)
1. **`org.teavm.runtime.Fiber.suspend` (teavm-core, Fiber.java ~L239–261)**
   - Hace `Thread javaThread = Thread.currentThread();` (L246) **antes** de `if (fiber.isResuming())` (L247). En cada resume de cualquier `@Async` (sleep, wait, monitor, yield) se llama `currentThread()` con `fiber.state == RESUMING`.
   - `Fiber` está excluida de la transformación de corutinas. `Thread`, no.
2. **`org.teavm.model.util.AsyncMethodFinder`**
   - Siembra los métodos `@Async` y propaga "async" a todos los callers por el CallGraph, **incluidos los targets virtuales**.
   - Un `synchronized` solo no siembra; el método compila a `monitorEnterSync`. Pero si el método ya es async por un callee, el monitor pasa a ser el `Object::monitorEnter` async.
   - `WeakHashMap.put/get` llaman `hashCode()/equals()` virtuales. Con que *alguna* clase del programa tenga un override async (el juego tiene muchos), todo `WeakHashMap.get/put` queda async.
3. **Inicialización de clases en WasmGC**
   - Cada método estático de una clase con `<clinit>` arranca con `global.get <Class>@initializer; call_ref`.
   - Si `<clinit>` es async, los estáticos que disparan init (`currentThread`, `setCurrentThread`) se transforman como corutinas. Su prólogo es `isPresentAndResuming → popInt…` y su epílogo es `isPresentAndSuspending → push…`.
4. **Cadena en PZ**
   - `JdkCompatibility.threads()` inyecta `ThreadGroup.registerDefault(this)` en `Thread.<init>(Runnable,String)`, `beforeStart` en `start()`, y reescrituras de `getThreadGroup/isAlive/join/...`.
   - `port/compat/java/lang/ThreadGroup` tiene `static final Map<Thread,Member> MEMBERS = new WeakHashMap<>()`, `synchronized (ThreadGroup.class)` en `registerDefault/handler`, y `static synchronized` en `assign/beforeStart/alive/groupOf/stackTraces`.
   - El resultado es `registerDefault` async → `Thread.<init>` async → `Thread.<clinit>` async (crea el main thread) → `currentThread/setCurrentThread` corutinas → crash en el primer resume.
5. **`setCurrentThread` como corutina es igual de grave**
   - Si se llama mientras el fiber está SUSPENDING (p. ej. desde `monitorEnterWait`/callbacks dentro de `call.run`), guarda estado y retorna **sin asignar** el thread actual.
   - Por eso solo reordenar `Fiber.suspend` no alcanza (ver resultados).

### Evidencia en `port/target/web/game/boot.wasm` (build 14:06)
Scan read-only del name section (`/tmp/code-agent/wasmfn.py` en agente47):
```
java.lang.Thread::currentThread     idx 338    fiberops: True
java.lang.Thread::setCurrentThread  idx 5204   fiberops: True
java.lang.Thread::<clinit>          idx 23055  calls Thread::<init>_3          fiberops: True
java.lang.Thread::<init>_3          idx 18577  calls Thread::<init>_1          fiberops: True
java.lang.ThreadGroup::registerDefault idx 219605 calls Object::monitorEnter (async), monitorExit,
                                    Thread::currentThread, ThreadGroup::groupOf ...  fiberops: True
```
En una build sana, `currentThread` y `setCurrentThread` **no** deben tener ops de Fiber.

## ¿Se soportan dos fibers alternando?
**Sí.** El "único `lastPendingCall` estático corrompe dos fibers" de la ronda 32 es falso: es solo una lista enlazada de bookkeeping.
- `EventQueue.offer` en WasmGC es un intrinsic que importa `teavmAsync.offer`, que en `wasm-gc-runtime.js` es `setTimeout(fn, max(0, t-Date.now()))`.
- Todo resume (sleep, `switchContext`, notify, handoff de monitor) es un macrotask top-level independiente. Dos fibers estacionados se reanudan sin problema.
- El repro, con dos fibers, 200 handoffs wait/notifyAll más `@Async` sobre `setTimeout` crudo más `Thread.yield()`, da `REPRO_OK`.

## Patrón recomendado
Regla: **el camino de `Thread.<init>`, `<clinit>`, `start`, `isAlive`, `getThreadGroup` y `getUncaughtExceptionHandler` tiene que ser 100% sincrónico.** Eso significa sin `synchronized`, sin `wait`, sin colecciones hasheadas por `hashCode/equals` virtuales y sin `Thread.sleep/yield`. Los threads son cooperativos (un solo hilo JS), así que en ese código el lock no protege nada: no hay suspensión entre lectura y escritura.

Opción A (preferida): un slot inyectado en `java.lang.Thread`, sin mapa.
```java
// port/compat: helper con cuerpos generados por JdkCompatibility (getfield/putfield)
public final class ThreadSlot {
    public static native Object get(Thread t);           // -> t.$portMember
    public static native void set(Thread t, Object m);   // -> t.$portMember = m
}

// En JdkCompatibility.threads(): agregar el campo al ClassHolder de java.lang.Thread
FieldHolder f = new FieldHolder("$portMember");
f.setType(ValueType.object("java.lang.Object"));
f.setLevel(AccessLevel.PUBLIC);
thread.addField(f);
// y generar el body de ThreadSlot.get/set con GetFieldInstruction/PutFieldInstruction.

// ThreadGroup compat: sin synchronized, sin WeakHashMap
public static void registerDefault(Thread t) {
    if (ThreadSlot.get(t) != null) return;
    Thread cur = Thread.currentThread();           // null durante Thread.<clinit>
    ThreadGroup g = cur == null ? ROOT : groupOf(cur);
    ThreadSlot.set(t, new Member(g));
}
public static ThreadGroup groupOf(Thread t) {
    Member m = (Member) ThreadSlot.get(t);
    return m == null ? ROOT : m.group;
}
public static void beforeStart(Thread t) { member(t).started = true; }
public static boolean alive(Thread t, boolean running) { /* lectura plana del Member */ }
```
Opción B (más rápida de probar): sacar todos los `synchronized` y cambiar `WeakHashMap<Thread,…>` por un mapa que use **solo identidad**, como `IdentityHashMap` o un array con `==`, para no llamar `hashCode/equals` virtuales. Se pierde la semántica weak; para threads del juego es aceptable. `stackTraces()` y los `children` del grupo pueden seguir como están **si** no son alcanzables desde los métodos de arriba. El gate lo verifica.

### Gate de build (obligatorio, barato)
Después de cada build, fallar si estas funciones del name section contienen llamadas a `org.teavm.runtime.Fiber::*`:
`java.lang.Thread::currentThread`, `java.lang.Thread::setCurrentThread` y, recomendado, `java.lang.Thread::<clinit>`.
- Con wasm-tools: `wasm-tools print boot.wasm | awk '/\(func \$java.lang.Thread::currentThread /,/^  \)/' | grep -c Fiber::popInt` tiene que dar 0. Es pesado para 143 MB.
- Alternativa liviana: el parser de name section `/tmp/code-agent/wasmfn.py` (en agente47; copiarlo a `scripts/` si se adopta), que tarda ~1 s:
  ```
  python3 wasmfn.py boot.wasm 'java.lang.Thread::currentThread' 'java.lang.Thread::setCurrentThread'
  # fiberops: False en ambas -> OK
  ```

### Defensa opcional
En `Fiber.suspend`, mover `Thread.currentThread()` después del branch `isResuming`. Es inocuo, pero **no alcanza solo** (ver resultados). Mantener `ResumeEvent` de la ronda 33 es opcional: es un `setTimeout` más y no cambia la semántica. Una vez aplicado el fix, el workaround `pzYieldSuspend` debería poder retirarse; validarlo con el gate más una corrida.

## Repro
Ubicación: **`/workspace/fiber-repro` en el box de Grok Bot** (no en agente47). Incluye el proyecto Maven teavm-maven-plugin 0.16.0 WASM_GC, `run.sh` (Chrome headless `--dump-dom`) y `build.sh <sync|plain> <stock|r33|reorder>`. No contiene código del juego.
- Dos fibers: un "game thread" hace 200× `offer(item); item.waitUntilFinished()` (wait/notifyAll) y el main "render" hace poll/finish más `@Async pause()` (setTimeout crudo) más `Thread.yield()`. Con `?single=1` hay un solo fiber que pausa 50 veces.
- Un plugin TeaVM (`ReproPlugin`) imita a `JdkCompatibility`: inyecta en cada `Thread.<init>` un registro.
  - `sync` usa `synchronized` más `WeakHashMap` con una clave cuyo `hashCode` es async, igual que el patrón de `ThreadGroup` compat.
  - `plain` es un contador plano, igual que el patrón recomendado.

| Variante | `currentThread`/`setCurrentThread` corutina | 2 fibers | 1 fiber |
|---|---|---|---|
| sync + Fiber stock (= PZ hoy) | sí / sí | **crash** `Fiber::popInt <- Thread::currentThread <- Fiber::suspend <- Yield::pause` (idéntico a PZ) | **crash** igual |
| sync + ResumeEvent ronda 33 | sí / sí | **crash** igual | **crash** igual |
| sync + reorder de `Fiber.suspend` | sí / sí | **se cuelga** sin error (queda `PENDING`) | `SINGLE_OK` |
| **plain + Fiber stock (fix)** | **no / no** | **`REPRO_OK`** (`GAME_DONE sum=20300`, `iter=201 processed=200`) | **`SINGLE_OK`** |
