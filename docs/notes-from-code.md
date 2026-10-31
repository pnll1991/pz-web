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

---

## Ronda 35: por qué el gate sigue en FAIL con `--with-game` (análisis de Code, 28-oct)

### Resumen

El fix de la ronda 34 (sin `synchronized`, `IdentityHashMap`) era correcto pero no suficiente. Los monitores **no** son el problema: en TeaVM 0.16 nunca siembran async (ver "Detalle de AsyncMethodFinder"). El problema es que en el build con juego **construir cualquier excepción ya es async**. `Thread.<init>` sigue llamando a `ThreadGroup.registerDefault`, y todo lo que toca `ThreadGroup` (su `<clinit>`, `IdentityHashMap`, `Collections.newSetFromMap`, `Objects.requireNonNull`, `Map.get/put` por interfaz, `Thread.isAlive`) puede lanzar o despachar a código async del juego.

La única solución robusta es que **`Thread.<init>`/`<clinit>` no llamen a nada de `ThreadGroup`**.

### Cadena exacta (game `boot.wasm` 15:06, 143.218.714 B)

Analizada con resolución exacta de slots de vtable: se parsean los `global.get X@vt; ref.func F; struct.set VT<T> campo` del propio wasm y se siguen los thunks `@caller`. Se excluyen `Object.monitorEnter/monitorExit(Object)`, igual que hace TeaVM.

1. **Entrada a Thread (todas llamadas directas):** `Thread::<clinit>` → `Thread::<init>_3` → `Thread::<init>_1` → **`ThreadGroup::registerDefault`**. Es la inyección de `JdkCompatibility.threads()`.
   - Es la única arista async que sale de `Thread`.
   - `DefaultUncaughtExceptionHandler::<init>` y `Object::<init>` son síncronos.
   - `currentThread`/`setCurrentThread` son coroutines solo porque llaman a `Thread@initializer`.
2. **Desde `registerDefault` hay tres caminos async independientes.** Arreglar uno solo no alcanza:
   - **a)** `[clinit] ThreadGroup::<clinit>` → `ThreadGroup::<init>_1` → `Collections::newSetFromMap` / `IdentityHashMap::<init>` / `Objects::requireNonNull`. Todos construyen `IllegalArgumentException`/`NullPointerException` → `Throwable::<init>_0`.
   - **b)** `MEMBERS.put` por interfaz. En el wasm aparece como `VT<pl.mjaron.tinyloki.ILogListener>.put`: es la tabla de interfaz canonicalizada, no tiene que ver con tinyloki. Entre sus implementaciones hay varias async, p. ej. `WeakHashMap::put` → `Object.equals` virtual.
   - **c)** `groupOf` → `Map.get` por interfaz y `Thread.isAlive` → `ThreadGroup::alive` (otra vez `Map.get`).
3. **Por qué `Throwable::<init>` es async (despacho por vtable de clase, exacto):**
   - `Throwable::<init>` → `[virt VT<Throwable>.fillInStackTrace] Throwable::fillInStackTrace` → `Class::getName` → `StringBuilder::append` → `AbstractStringBuilder::append` → `[virt VT<AbstractStringBuilder>.insert_13] AbstractStringBuilder::insert_9` (insert de un `Object`) → **`[virt VT<Object>.toString]`** → un `toString()` del juego que suspende.
   - El camino más corto es `se.krka.kahlua.j2se.KahluaTableImpl::toString` → `rawget` → `zombie.ui.UIManager::debugBreakpoint` → `[virt] UIManager$Sync::endFrame` → `Thread::sleep` (`@Async`).
   - El call graph de TeaVM es por type-flow pero **no sensible al contexto**. Basta con que en algún lugar del juego se haga `sb.append(objetoConToStringAsync)` para que el callsite `toString()` dentro de `AbstractStringBuilder.insert(int,Object)` quede async.
   - De ahí se vuelven async `Class.getName`, `fillInStackTrace` y **todos los constructores de excepción**. Por extensión, también todo método del JDK que pueda lanzar: `String.charAt`, `String.<init>`, `Objects.checkFromIndexSize`, `Integer.toString`, etc.
   - La componente fuertemente conexa que contiene a `Throwable::<init>` tiene **13.191 funciones async**.
4. **Por qué el `boot.wasm` chico sale limpio:** sin el juego no existe ningún override async de `toString`/`equals`/`hashCode`/`run` que fluya a esos callsites, así que `Throwable.<init>` es síncrono y `ThreadGroup` también.

Herramientas usadas, todas de solo lectura y fuera del repo, en `/tmp/code-agent` de agente47:
- `probe8.py <wasm> raiz1,raiz2`: Dijkstra hasta un llamador de `Fiber::suspend`, con slots de vtable exactos.
- `probe9.py <wasm> <func>`: subgrafo async con los destinos de cada slot virtual.
- `probe5.py <wasm> funcs...`: callees y si cada uno es coroutine.
- `probe7.py <wasm> <func>`: tamaño de la SCC.

Advertencia: las llamadas por interfaz se resuelven por nombre de slot, así que sobreaproximan. Las de vtable de clase son exactas respecto de la jerarquía, pero TeaVM usa type-flow, que es más fino. La cadena de `Throwable` va toda por vtables de clase y además está reproducida (ver más abajo).

### Detalle de AsyncMethodFinder (teavm-core 0.16.0)

- `find()` asigna `hasAsyncMethods = findAsyncMethods()` **antes** de sembrar los `@Async`. `findAsyncMethods()` solo mira métodos que ya están en `asyncMethods`, que en ese momento está vacío, así que **siempre devuelve false**.
- Consecuencias:
  - el bloque que siembra métodos con monitor (`hasMonitor`) es código muerto;
  - en `add()`, `Object.monitorEnter/monitorExit(Object)` (1 parámetro) **nunca propagan** a sus llamadores.
- Por eso quitar `synchronized` en la ronda 34 no cambió nada en el build con juego. La propagación real viene de `@Async` (`Thread.sleep`, `Object.wait`, `Thread.yield`, ...) a través de callsites virtuales y de interfaz.

### Fix mínimo propuesto (real, sin stubs)

`Thread.<init>` solo guarda **quién lo creó**, con un campo inyectado y sin ninguna llamada a `ThreadGroup`. La pertenencia se resuelve de forma perezosa en `ThreadGroup` (`groupOf`/`beforeStart`/`alive`/`handler`), que nunca están en la cadena de `<clinit>`. `assign` (constructor con grupo) no cambia.

En `JdkCompatibility.threads()`, en lugar de inyectar `registerDefault(this)`:

```java
var creator = new FieldHolder("$pzCreator");
creator.setType(thread); creator.setLevel(AccessLevel.PUBLIC);
cls.addField(creator);
var creatorRef = new FieldReference("java.lang.Thread", "$pzCreator");
for (int i = 0; i < program.basicBlockCount(); i++) {
    var block = program.basicBlockAt(i);
    if (block.getLastInstruction() instanceof ExitInstruction exit) {
        var cur = program.createVariable();
        var call = new InvokeInstruction(); call.setType(InvocationType.SPECIAL);
        call.setMethod(new MethodReference("java.lang.Thread", "currentThread", thread));
        call.setReceiver(cur);
        var put = new PutFieldInstruction();          // this.$pzCreator = Thread.currentThread();
        put.setInstance(program.variableAt(0)); put.setField(creatorRef);
        put.setFieldType(thread); put.setValue(cur);
        exit.insertPrevious(call); exit.insertPrevious(put);
    }
}
```

En `ThreadGroup.java` (compat), con dos accesores `native` cuyo cuerpo genera `JdkCompatibility` cuando transforma `java.lang.ThreadGroup`:

```java
// cuerpo generado: return t.$pzCreator;  /  t.$pzCreator = null;
private static native Thread creatorOf(Thread t);
private static native void forgetCreator(Thread t);

private static Member member(Thread thread) {
    Member member = MEMBERS.get(thread);
    if (member == null) {                      // equivalente perezoso del antiguo registerDefault
        Thread creator = creatorOf(thread);
        ThreadGroup group = creator == null ? ROOT : groupOf(creator);
        member = new Member(group == null ? ROOT : group, creator == null); // sin creador = hilo bootstrap/main
        MEMBERS.put(thread, member);
        forgetCreator(thread);                 // no retener al creador
    }
    return member;
}
// groupOf / beforeStart / alive usan member(thread) en vez de MEMBERS.get(thread); se borra registerDefault.
```

```java
// en JdkCompatibility, case "java.lang.ThreadGroup":
var get = cls.getMethod(new MethodDescriptor("creatorOf", thread, thread));
get.getModifiers().remove(ElementModifier.NATIVE);
var e = ProgramEmitter.create(get, context.getHierarchy());
e.var(1, thread).getField("$pzCreator", thread).returnValue();
var clear = cls.getMethod(new MethodDescriptor("forgetCreator", thread, ValueType.VOID));
clear.getModifiers().remove(ElementModifier.NATIVE);
e = ProgramEmitter.create(clear, context.getHierarchy());
e.var(1, thread).setField("$pzCreator", e.constantNull(thread)); e.exit();
```

- **Única diferencia semántica:** el grupo se hereda del creador la primera vez que se consulta (o en `start()`), no en el instante del `new`. Solo cambia algo si el creador muere antes de que el hijo sea consultado o arrancado. En ese caso el hijo cae en `ROOT`, que es lo mismo que ya hacía `registerDefault` con un creador muerto (`groupOf` → `null` → `ROOT`).
- **No hay que intentar** volver "segura" a `ThreadGroup` evitando excepciones y llamadas virtuales. Cualquier `requireNonNull`, colección o `toString` futuro lo vuelve a romper, porque en el build con juego todo lo que puede lanzar es async.

### Cómo se verificó

1. **Análisis del artefacto real.** `port/target/web/game/boot.wasm` (15:06) con las herramientas de `/tmp/code-agent` (cadena de arriba). No se compiló nada del juego ni se tocó el repo, salvo este append.
2. **Repro en la box** (`/workspace/fiber-repro`, teavm-maven-plugin 0.16.0, WasmGC):
   - `Group34` es un análogo de la `ThreadGroup` de la ronda 34: `IdentityHashMap`, `newSetFromMap`, `requireNonNull`, `Map` por interfaz, sin monitores.
   - El "veneno" es una clase con `toString()` que hace `Thread.yield()`, pasada a `new StringBuilder().append(obj)`.
   - Se cuenta `Fiber::popInt` por función en el `.wat`:

| modo | `currentThread` / `setCurrentThread` | `Thread::<init>` | `Throwable::<init>` / `Class::getName` / `Group34::<clinit>` / `groupOf` |
|---|---|---|---|
| `r34` (inyecta `registerDefault`) | **coroutine / coroutine** (= gate FAIL del juego) | coroutine | coroutine |
| `field` (fix: `$pzCreator` + resolución perezosa) | **sync / sync** | sync | siguen coroutine (no importa) |

   - Esto reproduce en chico exactamente el FAIL del juego. Sin el `append(obj)` todo sale sync, igual que el `boot.wasm` chico.
   - El fix funciona aunque `Throwable.<init>` y `ThreadGroup` sigan siendo async.
   - **Pendiente:** no corrí el repro en Chrome. Con el veneno, ese repro choca antes con la recursión `Fiber::push` → `Arrays::copyOf` → `Fiber::reversePush`, que en el proyecto ya está resuelta con `patch_fiber()`. Al meter ese mismo parche en mi jar con una sola clase reemplazada, el backend de TeaVM tira un NPE en `WasmTypeGraphBuilder`.
3. **Para validar en el build real**, después de aplicar el fix y en una corrida `--with-game` (nunca en paralelo con otra):
   - el gate de `scripts/build.sh` debe dar `fiberops: False` para `currentThread` y `setCurrentThread`;
   - `python3 /tmp/code-agent/probe8.py port/target/web/game/boot.wasm 'java.lang.Thread::<clinit>'` debe imprimir `no seed`;
   - `probe5.py ... 'java.lang.Thread::<init>_1'` debe listar solo callees `fiber False`.
   - Opcional y definitivo: instrumentar en la copia de `prepare_teavm.py` el `AsyncMethodFinder.add()` para imprimir `stack` (ya construye un `CallStack` y nunca lo usa) cuando `methodRef` sea de `java.lang.Thread`.

## Ronda 40: monitor no se suelta en wait()

(Code, 2026-10-29 00:30 hora de Mendoza. Solo lectura sobre el repo; analizado `boot.wasm` de round40c (00:04) y `diag90c.log` (00:21).)

### Causa raíz
- En TeaVM 0.16 Wasm GC, `Object.wait` termina en `@Async native waitImpl(...)`; la implementación con
  callback (`waitImpl_0`) corre *mientras la fibra del llamador se está suspendiendo*. Cualquier llamada
  dentro de ese cuerpo a una función que sea corrutina (fiberops True) ve la fibra en estado "suspending"
  y vuelve sin ejecutar nada.
- El upstream liberaba con `monitorExit(this, monitor.count)`. `monitorExit_0` es corrutina sólo por su
  camino de excepción (`new IllegalMonitorStateException()` → `Throwable::<init>` → … → `Thread.sleep`, la
  misma cadena de la ronda 37). Resultado: listener registrado (fase 4), pero `count -= ...; owner = null`
  nunca corre → dueño = thread 2 para siempre, `notifyAll` registrado `9:2`, listener nunca otorgado.
- Los helpers `monitorOf/monitorIdOr0/monitorCountOr0` (fix del `unreachable`) NO rompen la liberación:
  son lecturas planas, fiberops False en el wasm de round40c.

### Fix (ya está en `scripts/prepare_teavm.py`, ronda 40, `wait_release_edits`)
```diff
-        monitorExit(this, monitor.count);
+        releaseForWait(monitor);
+    }
+
+    private static void releaseForWait(Monitor monitor) {
+        monitor.count = 0;
+        monitor.owner = null;
+        reportMonitor(5, ...);
+        if (monitor.enteringThreads != null && !monitor.enteringThreads.isEmpty()) {
+            EventQueue.offer(() -> waitForOtherThreads(monitor.object));
+            return;
+        }
+        monitor.object.isEmptyMonitor();
     }
```
más el campo `TObject object` en `Monitor`, asignado en `createMonitor` (`o.monitor.object = o`).
El chequeo de dueño no se pierde: `wait0` ya llama `holdsLock(this)` antes de `waitImpl`. El conteo se
guarda en `NotifyListenerImpl.lockCount` y se restaura con `free.count += lockCount` (fase 14) o vía
`monitorEnterWait` (fases 16→1/2). No hace falta otro cambio.

### Evidencia (verificado en la build real, no sólo repro)
- `wasmfn.py` sobre round40c: `waitImpl_0` fiberops **False**, ya no llama `monitorExit_0`, llama
  `releaseForWait` (fiberops False; callees: isEmptyMonitor, reportMonitor, currentThread, EventQueue.offer,
  todos False).
- `diag90c.log`: `cnt` = `4:2` 4 (waits), `5:2` 108 (liberaciones del thread 2), `7:2` 4 (listener corre),
  `16:2` 4 y `1:2` 4 (re-adquiere). Fibra 2 `7/4` suspends/resumes (antes `1/0`).
  `RenderContextQueueItem#invoke` y `invokeOnRenderContext` returned 4. **El wait/notify funciona.**

### Nuevo bloqueo (siguiente paso)
- El MainThread avanza hasta `GameWindow#initFonts()` (entered, sin returned) y muere con una excepción no
  capturada: `GameWindow#uncaughtExceptionMainThread` → `ExceptionLogger#logException` →
  `onGameThreadExited`; `g.phase = main-returned`, `swap` 2, `menu:false`.
- Verificar/diagnosticar: en el diag, capturar clase + mensaje + stack del Throwable que recibe
  `uncaughtExceptionMainThread` (por ejemplo hook en `ExceptionLogger.logException` hacia
  `BrowserMonitor`/consola, sin `StringBuilder` dentro de Fiber). Con ese dato se ataca `initFonts`.
- Cómo verificar este fix en futuras builds: `python3 /tmp/code-agent/wasmfn.py port/target/web/game/boot.wasm
  'java.lang.Object::waitImpl_0'` debe dar fiberops False y sin `monitorExit_0` en calls; en Chrome,
  `cnt["7:2"] == cnt["4:2"]` y fibra 2 con resumes > 0.

## Ronda 47: AttackState

(Code, 2026-10-29 ~11:30 hora de Mendoza. Solo lectura; evidencia: `diag135.log`, `local-run-45/46/47.log`, bytecode del jar con `javap`.)

### Causa raíz
- Trap: `RuntimeError: dereferencing a null pointer` en `Class::isAssignableFrom` ← `State$Param::<init>` ←
  `ofSupplier` ← `of` ← `ofBool` ← `AttackState::<clinit>` (disparado por `LuaJavaClassExposer.exposeStatics` → `Field.get`).
- `State$Param.<init>` (bytecode 18-42) averigua la clase llamadora así:
  `StackWalker.getInstance(RETAIN_CLASS_REFERENCE).walk(s -> s.map(getDeclaringClass).filter(not(Param.class::equals)).findFirst().orElse(null))`
  y después hace `State.class.isAssignableFrom(caller)`.
- Nuestro compat `port/compat/java/lang/StackWalker.walk` itera `Thread.currentThread().getStackTrace()`.
  En TeaVM 0.16 `TThread.getStackTrace()` es literalmente `return new StackTraceElement[0]` (verificado con
  javap sobre `teavm-classlib-0.16.0.jar`). Stream vacío ⇒ `orElse(null)` ⇒ `isAssignableFrom(null)`.
  En la JVM sería NPE; en Wasm GC TeaVM no inserta null-check ⇒ trap no capturable que mata el MainThread.
- `port/runtime/wasmsymbols.mjs` (ronda 46) no lo arregla: nadie lo importa todavía y, aunque se conecte,
  sólo puede simbolizar `Throwable` (stack de V8), no `Thread.getStackTrace()`; además depender del stack
  en runtime es frágil (inlining, nombres `of_0`, frames de lambdas).

### Fix exacto (determinista, sin stack en runtime, sin fingir)
En la JVM el resultado del walk es SIEMPRE la clase que llama a una fábrica `State$Param.of*` (todas las
fábricas viven en `Param`, que el filtro descarta). Eso se conoce en compilación. Transformer TeaVM nuevo:

1. `port/runtime/StateParamCaller.java`:
```java
package port.runtime;
public final class StateParamCaller {
    private static Class<?>[] stack = new Class<?>[16]; private static int depth;
    public static void enter(Class<?> c){ if(depth==stack.length) stack=java.util.Arrays.copyOf(stack,depth*2); stack[depth++]=c; }
    public static void exit(){ stack[--depth]=null; }
    /** Mismo contrato que el walk original: la clase que llamó a la fábrica. */
    public static Object callerClass(){
        if(depth==0) throw new IllegalStateException("State$Param: fábrica llamada sin call-site instrumentado");
        return stack[depth-1];
    }
}
```
2. `port/compiler/StateParamCallerCompatibility.java` (ClassHolderTransformer, registrarlo junto a
   `CurrencyCompatibility`):
   - En toda clase `C != zombie.ai.State$Param`: por cada `InvokeInstruction` estática a
     `zombie.ai.State$Param` cuyo nombre empiece con `of` (of, ofBool, ofSupplier, …): insertar ANTES
     `ClassConstantInstruction(C) -> v; invoke static StateParamCaller.enter(Class)V(v)` y DESPUÉS
     `invoke static StateParamCaller.exit()V`.
   - En `zombie.ai.State$Param.<init>(String,Class,Supplier)`: reemplazar la `InvokeInstruction` virtual a
     `java.lang.StackWalker.walk(Function)Object` por `invoke static StateParamCaller.callerClass()Object`
     con el MISMO receiver; el `getInstance` y el lambda quedan muertos (los quita el optimizador).
   - Fallar el build (RuntimeException) si en `Param.<init>` no hay exactamente 1 `walk`, o si hay 0 call
     sites (cambio de versión del juego ⇒ no reescribir a ciegas).
   Alcance medido: 50 clases, todas en `zombie.ai.states.*` (ClimbOverFenceState 16 sitios … AttackState 1),
   casi todas en `<clinit>`/campos estáticos. Sin `try/finally`: si la fábrica lanza, el `<clinit>` falla
   igual (en la JVM también) y la pila queda inconsistente sólo en un estado ya fatal.

Alternativa mínima si se quiere algo en 5 min: en el compat `StackWalker.walk`, si `getStackTrace()` viene
vacío, lanzar `UnsupportedOperationException("TeaVM: Thread.getStackTrace vacío")` en vez de entregar un
stream vacío. No arregla el arranque, pero convierte el trap en una excepción con nombre (no es fix).

### Cómo verificar
- Build única (cuando no haya otra corriendo): `wasmfn.py boot.wasm 'zombie.ai.State$Param::<init>'` no debe
  llamar a `java.lang.StackWalker::walk` y sí a `port.runtime.StateParamCaller::callerClass`.
- Chrome: desaparece el trap de `isAssignableFrom`; `exposeStatics` pasa por los 50 `*State`.
  Chequeo de semántica: `State.PARAMS_BY_STATE` debe tener la clave `AttackState` con `skip_test_defence`.

### Otros bloqueos a la vista (camino al menú)
- `Thread.getStackTrace()` vacío también afecta `zombie.debug.StackTraceContainer.getStackTrace()/(int,Predicate)`
  (usado por `DebugLogStream.generateCallerPrefix`, `printStackTrace`, `LuaManager$GlobalObject.getISUIStackTrace`,
  `WorldGenUtils.methodsCall`). `DebugLogStream` usa `PZArrayUtil.getOrDefault` ⇒ imprime "(UnknownStack)", no
  crashea; `WorldGenUtils.methodsCall` indexa directo ⇒ posible AIOOBE (sólo worldgen, no bloquea menú).
- Único consumidor de StackWalker/getCallerClass en `zombie/` y `se/krka`: `State$Param`. log4j `StackLocator`
  y lwjgl `StackWalkUtil` (META-INF/versions) no están en el camino del menú.
- Patrón general: cualquier NPE de Java en Wasm GC es un trap no capturable; código del juego que hace
  `try{…}catch(NullPointerException)` va a morir igual. Si aparecen más traps "dereferencing a null pointer",
  mirar primero si el null viene de un stub de classlib que devuelve vacío/null.

## Ronda 49: StateRun render

**Trap:** `null pointer` en `SpriteRenderer$RingBuffer$StateRun::render` (wasm-function[385500], 0x3b50ac8). Decodificado con wasm-tools: `DefaultShader.isActive == true` y `SceneShaderStore.defaultShader == null` → llamada virtual `defaultShader.setTextureActive(...)` sobre null (trap Wasm GC, no NPE).

**Cadena causal (verificada en runtime):**
1. `Core.initGlobalShader` crea `DefaultShader("default")` y llama `getID()` → compile lazy en render context. `glCreateProgram` se llama (1 vez), pero ningún `createShader`/`linkProgram`.
2. `ShaderUnit.compile` → `loadShaderFile` → `preProcessShaderFile` → `IndieFileLoader.getStreamReader("media/shaders/default.vert")` falla: hook en `pzRuntime.vfs` muestra `exists('/media/shaders/default.vert') == false`, `list('/media/shaders') == []` (y también busca en `/Zomboid/mods/media/shaders/`). La excepción se loguea con DebugType.Shader (deshabilitado) → silenciosa.
3. Programa destruido → `shaderId 0` → `!isCompiled()` → `initGlobalShader` pone `defaultShader = null`, `defaultShaderId = 0`.
4. `ShaderHelper.glUseProgramObjectARB(0)` → `glUseProgram(0)` (visto 2×) y `DefaultShader.isActive = true` → StateRun.render desreferencia null.

**Fix exacto:** `build/real-browser/startup.txt` (lista de `tools/pack/pack.py`) no contiene ningún `media/shaders/*` (0 coincidencias; el `packs/index.json` servido tampoco). Agregar `media/shaders/default.vert`, `media/shaders/default.frag` y todo archivo que referencien por `#include` (resolver con `processIncludeLine`), idealmente todo `media/shaders/` usado en startup, y re-empaquetar. No poner guardia null en StateRun (sería stub falso).
Los shaders traducidos ya compilan y linkean en Chrome (`/tmp/code-agent/r49/t.mjs`).

**Bug latente secundario (gl.mjs ~1126-1156):** `shaderParameter` trata 0x8B81 como SHADER_TYPE (0x8B81 es COMPILE_STATUS; SHADER_TYPE = 0x8B4F); `programParameter` no mapea 0x8B82 LINK_STATUS ni 0x8B83 VALIDATE_STATUS (lanzaría "Unsupported WebGL2 program parameter"). Correctos: DELETE_STATUS 0x8B80, COMPILE 0x8B81, LINK 0x8B82, VALIDATE 0x8B83, INFO_LOG_LENGTH 0x8B84, ATTACHED_SHADERS 0x8B85, ACTIVE_UNIFORMS 0x8B86, ACTIVE_UNIFORM_MAX_LENGTH 0x8B87, SHADER_SOURCE_LENGTH 0x8B88, ACTIVE_ATTRIBUTES 0x8B89. Se alcanzará apenas se carguen los shaders: corregir junto.

**Verificación:** sin build; Playwright contra servidor existente :8092 (`/tmp/code-agent/r49/d.mjs`, `v.mjs`): trap reproducido ~125 s, hooks GL (1 createProgram, 0 shaders, useProgram(0) ×2), hooks VFS (shader ausente).

**Otros bloqueos hacia el menú:** AngelCodeFont sin páginas (`mainfont_0.png`, `mainfont2_0.png`, `zomboidDialogue.bmfc_0.png` faltan en startup.txt), 8× FileNotFoundException (probablemente misma causa: lista de startup incompleta), oshi y RakNet64 UnsatisfiedLinkError; 1 `invokeOnRenderContext(Runnable)` queda sin retornar (7 entra / 6 sale).

## Ronda 49b: próximos frenos

Método: sin build ni cambios al repo. Playwright contra :8092 (`/tmp/code-agent/r49/s.mjs`) montando en el VFS **en memoria** (XHR síncrono + `vfs.mount`) los archivos de la instalación local (`pz-pinned/.../media/shaders/**` y 7 `.pack`). Nada se publica.

1. **8× FileNotFoundException.** 7 son `GameWindow.initShared → LoadTexturePack`: faltan en `build/real-browser/startup.txt` estas líneas: `media/texturepacks/UI.pack` (1.3 MB), `UI2.pack` (54.6 MB), `IconsMoveables.pack`, `RadioIcons.pack`, `ApComUI.pack`, `Mechanics.pack`, `WeatherFx.pack`. **Verificado**: al montarlas desaparecen las 7 FNF y `LoadTexturePack` entra y retorna 7/7. Ojo: `UI2.pack` supera `--max-bytes` (32 MB) de `pack.py` y debe ir sola en un blob o hay que subir el límite. La FNF restante (antes de los texturepacks, después de `Translator.loadFiles`, en `LuaManager.init`/`initShared`) no pasa por `vfs.exists`. **No identificada**: hay que hookear `readFile`/`IndieFileLoader` para ver la ruta.
   **Shaders** (Ronda 49): además hay que agregar todo `media/shaders/**` (196 archivos, incluye includes), no solo `default.*`.
   **Fuentes:** `mainfont_0.png`, `mainfont2_0.png` y `fonts/zomboidDialogue.bmfc_0.png` **no existen tampoco en la instalación vanilla** (`mainfont.fnt` apunta a `test_0.tga`). Es error vanilla tolerado: el código debe tolerarlo y no hay que empaquetar nada (inferido; no hay log vanilla para comparar).
2. **oshi** (`UnsupportedOperationException: Unsupported platform: UNSUPPORTED`). El llamador es `MainScreenState.printSpecs()` → `new SystemInfoFFM().getHardware()...`, que solo loguea specs. Todo el método está envuelto en `catch (Exception | UnsatisfiedLinkError)`, así que **no bloquea** y es solo ruido. La versión real recomendada para silenciarlo es una sustitución TeaVM de `SystemInfoFFM`/`HardwareAbstractionLayer` respaldada por el navegador: `navigator.hardwareConcurrency` (núcleos lógicos), `navigator.deviceMemory` (GB aprox.), `WEBGL_debug_renderer_info` (nombre y vendor de GPU) y `navigator.userAgent`. Los valores desconocidos (placa base, VRAM) deben quedar como "unknown"/0, nunca inventados. Prioridad baja.
3. **RakNet64.** `SteamUtils.init()` intenta `steam_api`/`RakNet64`/`ZNetJNI64`/`ZNetNoSteam64` dentro de `try/catch UnsatisfiedLinkError`. Al fallar, el propio juego pone `netEnabled=false` y `steamEnabled=false` y loguea "Failed to load networking libraries" (camino vanilla para quien no tiene red). **No hace falta para single-player ni para el menú.** Fix: ninguno obligatorio. Para quitar la traza, que `NativeRegistry` falle rápido con `UnsatisfiedLinkError` igual que ahora (es el camino real) y no implementar RakNet. No hay que simular éxito, porque activaría `netEnabled` con nativos inexistentes.
4. **`invokeOnRenderContext` 7 entradas / 6 salidas.** El 7.º llamado se hace inmediatamente después de `setTexturePackLookup` (t ≈ 114.94 s). Su predicado de espera (`lambda$invokeOnRenderContext$0`) corre, pero el trap de StateRun.render ocurre en ese mismo frame, así que el fiber nunca se reanuda. **Es consecuencia del trap, no una causa aparte** (verificado por la secuencia de timestamps; el llamador exacto se infiere del orden en `GameWindow.init`). Fix: ninguno propio. Se resuelve con los shaders.
5. **Siguiente freno (verificado):** con los shaders montados, `ShaderUnit.compile` ahora llega a GL y falla con `Unsupported WebGL2 shader parameter: 0x8b84` (`INFO_LOG_LENGTH`) → `RenderContextQueueException` → `defaultShader` null → el mismo trap. Confirma que **el fix de `gl.mjs` (tabla pname de Ronda 49) es obligatorio junto con los shaders**: `shaderParameter` debe soportar 0x8B81 COMPILE_STATUS, 0x8B84 INFO_LOG_LENGTH, 0x8B88 SHADER_SOURCE_LENGTH, 0x8B4F SHADER_TYPE y 0x8B80 DELETE_STATUS. `programParameter` debe soportar 0x8B82/0x8B83/0x8B84/0x8B85/0x8B86/0x8B87/0x8B89 y 0x8B80. INFO_LOG_LENGTH sale de `getShaderInfoLog().length+1` (0 si está vacío).
   Después de eso (inferido): la compilación de los demás shaders del menú (`media/shaders/**`) y la carga de las texturas del menú desde UI/UI2.pack.

## Ronda 50: shader vacío

**No es una lectura vacía.** En diag148 `/media/shaders` tiene 196 entradas, no falta ningún archivo, y `default.vert`/`default.frag` compilan y linkean (programa 257 OK). El que falla es **puddles**.

**Causa:** `ShaderUnit.compile`, por cada `#include "x.h"`, carga además `x.glsl` como **otra unidad del mismo stage** ("Loading additional shader unit", constante `.glsl` en el bytecode) y la attachea al mismo programa. Desktop GL permite varios shader objects por stage y resuelve `puddlesMain()` al linkear. GLSL ES / WebGL2 no lo permite: cada shader necesita su propio `main()`.
- Vert 337 = `puddles_common.vert.glsl` → `Missing main()`.
- Vert 338 = `puddles_*.vert`, que solo tiene el prototipo → `puddlesMain() ... is undefined`.
- Frag 339 = `puddles_common.frag.glsl`: `glsl.mjs:222` lanza "Fragment shader has no main() to attach the alpha test" antes de `shaderSource`. Por eso la fuente queda **len 0**: no es un problema de Reader, charset ni pack.
- Hay 30 shaders con `#include`, así que el problema es general.

**Fix exacto (bridge, `runtime/gl.mjs` + `glsl.mjs`; el juego no se toca):**
1. `glsl.mjs`: si la unidad no tiene `main()`, marcarla como *librería*. No inyectar el alpha test ni lanzar error.
2. `compileShader` de una librería: guardar la fuente traducida y validarla de verdad, compilando `fuente + "\nvoid main(){}"` en un shader temporal. Reportar ese COMPILE_STATUS y ese info log. No es un éxito falso.
3. `linkProgram`: por stage, tomar la única unidad con `main()` y concatenarle el cuerpo de las librerías attacheadas del mismo stage (sin `#version`/`precision`; los prototipos se pueden quedar). Compilar ese shader fusionado e inyectarle el alpha test solo ahí (frag). Linkear con los fusionados y mapearlos al handle del programa. Si un stage tiene 0 o más de 1 `main()`, fallar el link con un log claro.
4. Al hacer `detach`/`delete` de unidades, invalidar la caché fusionada.

**Verificado:** trail GL de diag148 (fuentes, logs, len 0 en el frag 339), los archivos `puddles_common.*.{h,glsl}` de la instalación y las constantes de `ShaderUnit`. **Inferido:** que la fusión compila. Se espera que sí, porque `.h` solo trae prototipos y `.glsl` las definiciones. No lo probé en Chrome.

**Warnings de items** (`InvalidParameterException: Sharpness/HeadCondition is not a valid parameter in item: …`, ×2 cada uno): son del parser de scripts de items de 42.x. El juego los captura, los loguea y sigue. **Tolerados, no bloquean.** Son datos vanilla, no hay que tocarlos.

**Siguiente:** después de puddles vienen los demás shaders con `#include` (30) por el mismo camino, y luego `invokeOnRenderContext` 14 entradas / 12 salidas, que probablemente sean las mismas llamadas de compile que esperan.

## Ronda 50b: parche probado

- **Parche:** `/datos/Projects/pz-web-code/patches/ronda50-shader-merge.patch`. Son 253 líneas y solo tocan `port/runtime/gl.mjs` y `port/runtime/glsl.mjs`, contra HEAD 212361e.
- **Cómo aplicarlo**, desde `/datos/Projects/pz-web`: `git apply /datos/Projects/pz-web-code/patches/ronda50-shader-merge.patch`. `git apply --check` ya pasa.

### Qué hace

- **Unidades sin `main()`:**
  - Las unidades de librería (`*.glsl` que se cargan por cada `#include`) se compilan como una sonda: la fuente más un `main` vacío.
  - Es una validación real. Solo se toleran los errores de funciones no definidas que la unidad tiene prototipadas, porque desktop las resuelve al linkear.
- **`linkProgram`:**
  - Hace un merge por stage: un único `#version`, después las librerías y al final la unidad con `main`.
  - Traduce y compila el resultado una sola vez. El alpha test se inyecta solo en el frag mergeado.
- **Errores claros:** si un stage tiene 0 o más de un `main()`, `linkProgram` falla con un log explícito.
- **Invalidación:** los shaders mergeados se invalidan con detach, delete, `shaderSource` o recompile. También al borrar el program.
- **Nuevo (`glsl.mjs`):**
  - Los inicializadores globales no constantes, que GLSL 1.20 permite y ES 3.00 no (por ejemplo `float wetLand = PuddlesParams[1].y*...`), se pasan a asignaciones al principio de `main()`, en el mismo orden en que se declaran.
  - En una librería quedan en `globalInits` y terminan dentro del `main` mergeado.
- **Tablas de pname:** ya estaban completas en HEAD; no hubo que cambiarlas.

### Qué se verificó

- Corrida de Playwright de 220 s contra :8092, con `gl.mjs` y `glsl.mjs` overrideados desde el worktree. La salida del build no se tocó.
- 0 errores de "no main()" o "Missing main()".
- Links: 2 de 2 OK, incluido puddles. En la corrida anterior, sin el hoist, puddles fallaba con "global variable initializers must be constant expressions".
- No hubo pageerror ni trap.
- El boot avanza hasta la carga de items y scripts, igual que antes. Los shaders de vehicle todavía no se alcanzan.

### Próximo freno

1. `Java_org_lwjgl_opengl_GL12C_nglDrawRangeElements__IIIIIJ` tira UnsupportedOperationException: falta en el bridge GL. Ahora `invokeOnRenderContext` queda en 14 entradas y 12 retornos; antes eran 14 y 14. Hay que implementarlo con `drawRangeElements` de WebGL2.
2. `FluidContainerScript` da "missing no-arg constructor" por la reflexión de TeaVM. Por eso se eliminan los items con fluidos (Wine2, JuiceLemon, etc.).
3. Los InvalidParameterException de items (Sharpness/HeadCondition) siguen siendo tolerables.

## Ronda 51: drawRangeElements y FluidContainerScript

**Parche:** `/datos/Projects/pz-web-code/patches/ronda51-drawrange.patch`.
- Es **acumulativo**: incluye todo ronda50 más lo de esta ronda, contra HEAD 212361e. Se aplica solo, sin ronda50: `git apply /datos/Projects/pz-web-code/patches/ronda51-drawrange.patch`.
- Verificado que aplica sobre un HEAD limpio.
- **Ojo:** en main, `port/runtime/glsl.mjs` tiene un cambio sin commitear de la ronda (17:24, `alphaTestDeferred`) que choca con el hunk de glsl. Hay que elegir uno de los dos: el merge real de ronda50 reemplaza ese workaround.

### 1. Draw calls
- **Causa:** ningún draw call estaba ruteado. `tools/glgen/generate.py` no mapeaba `GL11C.glDrawArrays`, `GL11C.nglDrawElements` ni `GL12C.nglDrawRangeElements`, así que `FailFastNatives` las convertía en UnsupportedOperationException. `GLBridge.drawElements` existía, pero nada lo llamaba.
- **Uso real en el jar del juego** (búsqueda en el bytecode):
  - `glDrawRangeElements`: VBORenderer, IsoPuddles, IsoWater, WorldMapVBOs.
  - `glDrawElements` y `glDrawElementsInstanced`: VertexBufferObject.
  - `glDrawArrays`: VisibilityPolygon2.
  - `glDrawArraysInstanced`: Particles.
  - `glMultiDrawElements` y `glDrawElementsBaseVertex`: 0 usos. No se implementaron.
- **Cambios:**
  - `generate.py`: nuevo `DRAW_DIRECT`, con esas 3 natives apuntando a `port.shim.GL11`.
  - `GL11.java`: `glDrawArrays`, `nglDrawElements` y `nglDrawRangeElements` llaman al bridge. `indices` es el offset en bytes dentro del ELEMENT_ARRAY_BUFFER bindeado. Si es negativo o mayor que int, raise con la identidad de la native.
  - `gl.mjs`: `drawRangeElements` y `drawArrays`, con la misma validación que `drawElements` (buffer bindeado, tipo de índice, alineación del offset).
- **Verificado en Chrome** con un contexto WebGL2 real, sin build:
  - `drawRangeElements` con offset 2: getError 0 y píxel rojo.
  - Offset impar: error claro.
  - Sin buffer bindeado: error claro.
  - `drawElements`: también OK.
- **No verificado:** el ruteo Java. Necesita un build del juego (regenerar `gl-overrides.tsv` y recompilar). Después del build hay que confirmar que no aparece la UnsupportedOperationException y que `invokeOnRenderContext` entra y retorna la misma cantidad de veces (ahora 14 contra 12).
- **Probable siguiente freno:** los draws instanced (`glDrawElementsInstanced` y `glDrawArraysInstanced`, en GL31C/ARB), que siguen sin mapear.

### 2. FluidContainerScript "missing no-arg constructor"
- **Causa:**
  - `ComponentType.CreateComponentScript` va a `ComponentScriptFactory$ScriptConstructor.findConstructor`.
  - Ese método prueba `ClassReflection.getConstructor()` y después `getDeclaredConstructor()` + `setAccessible(true)`.
  - Todos los `ComponentScript` y los `Component` (por ejemplo `FluidContainer`, creado por `ComponentFactory$ComponentPool`) tienen el constructor sin argumentos **private**.
  - `ReflectionConfiguration.setup()` solo hace `reflectablePublicMembers()` sobre `reflection.txt`, así que TeaVM no emite metadata de ese constructor.
  - La clase sí está en `reflection.txt`.
  - Fluid es simplemente el primer `ComponentType` que se crea. Attributes, CraftLogic, SpriteConfig, Parts y los demás fallarían igual.
- **Fix:** ya está en el parche.
  - En `ReflectionConfiguration.setup()` se agrega `selectClasses(clases de reflection.txt que extienden zombie.scripting.entity.ComponentScript o zombie.entity.Component).reflectableMethods(m -> m.isConstructor() && m.parameters().isEmpty())`.
  - Requiere build del juego. No está compilado ni probado.

## Ronda 52b: vehicle script, packs y cursores (contra HEAD 16655b9)

Revisado contra 16655b9 y build/real-browser/debuglog-round57.txt + diag161.log:
- Bullet.defineVehicleScript: ya resuelto en HEAD (commit b4bdd82, ronda 52, bullet.mjs exporta defineVehicleScript sobre pzbullet_define_vehicle_script). diag161 y debuglog-round57: 0 apariciones. Nada que parchear.
- .pack: el índice servido (build/real-browser/packs/index.json) ya tiene UI, UI2, IconsMoveables, RadioIcons, ApComUI, Mechanics, WeatherFx, Tiles2x y Clock2x. --max-bytes NO excluye archivos (pack.py sólo corta el blob; un archivo >32 MB va en su propio blob). Quedan 7 FileNotFoundException: son los 2x packs no montados a propósito (startup_extra.py, para cuidar memoria) y el juego los tolera (el arranque sigue). No cambiar salvo que se necesite un tile de esos packs.
- Cursores: cursor_blank.png y cursor_white.png SÍ están empaquetados; el error real es "Invalid PNG image: only 8-bit non-interlaced RGB/RGBA is supported": ambos son PNG 8-bit gray+alpha (color type 4). Tolerado (sólo falta el cursor), pero cualquier PNG gris/paleta falla igual.
  Parche: /datos/Projects/pz-web-code/patches/ronda52b-png-grayalpha.patch (BrowserStb.decodePng acepta color types 0, 3 con PLTE/tRNS y 4, además de 2 y 6). git apply --check OK contra 16655b9 y contra el árbol actual. NO probado: es Java, necesita build del juego. Verificar que desaparezcan las 2 líneas "failed to read image /media/ui/cursor_*.png".

## Ronda 59b: estancamiento y jassimp64

### Por qué se estancó la 59
No es un loop de javap idéntico: es parálisis de análisis. Tras el build OK (04:33) el modelo decidió reemplazar jassimp
por una implementación Java "substituida" vía TeaVM y se puso a investigar CÓMO hacer la sustitución de clases dentro de TeaVM:
- l.21635 `unzip -l teavm-core-0.16.0.jar | grep -i substitut`, l.22028 `grep -iE 'substitut|renam|ClassHolderTransformer'`
- l.22046 `javap -p -c ... org.teavm.parsing.RenamingClassHolderSource > /tmp/RCHS.txt`, l.24476 `grep substituteToOriginal /tmp/RCHS.txt`
- l.21904 lee CompatReferences.java (usa ClassRefsRenamer) y entre l.21646 y l.24629 vuelca con javap toda la API jassimp
  (AiMesh, AiNodeAnim, AiScene, ProcessedAiScene, JAssImpImporter) y relee MaleBody.x / Bob_Idle.x.
~465 `exec` en el log, 28 menciones a RenamingClassHolderSource, cero apply_patch desde el build. El objetivo (reimplementar
jassimp.* entero con la misma ABI) es enorme y nunca llegó a un primer archivo, así que cada paso “necesitaba” más javap.
La regla de "máx 10 lecturas antes de editar" está en CODEX_TASK_CONTINUE pero no se aplica después de la primera edición.

### jassimp64: no hace falta assimp
El juego ya trae un cargador Java puro: ModelLoader.loadTxt / ModelTxt (formato "# Project Zomboid Animated Mesh",
ejemplo media/Bob_Sprint.txt). FileTask_AbstractLoadModel.checkExtensionType(): para un nombre sin extensión prueba en
media/anims_x .fbx, .glb, .x y, si ninguno existe, media/anims/<nombre>.txt → ModelFileExtensionType.Txt → loadTxt(),
sin tocar jassimp. Solución: convertir offline .x → .txt y montar media/anims/... SIN los .x.
Descartado: assimp a wasm (pesado, mismo ABI JNI que emular) y reimplementar jassimp.* (lo que estancó la 59).

Parche: /datos/Projects/pz-web-code/patches/ronda59b-x-anim-to-txt.patch (solo agrega tools/pack/x_anim_to_txt.py,
git apply --check OK contra 4fee0f0 con el árbol sucio de la 59). Probado aislado (sin build):
- convierte las 1650 .x de anims_X/Bob (incluye subcarpetas), 0 fallos, 62 s, 30 MB RAM.
- convención verificada contra Bob_Sprint.txt del juego: quat .x (w,x,y,z) → txt (-x,-y,-z,w); Dummy01 y Bip01
  salen idénticos en signo/orden (Bip01 run: 0.430,0.561,0.430,0.561); traslación igual; t = ticks/AnimTicksPerSecond.
- NO probado en el juego: que loadTxt acepte 0 vértices/0 caras en un archivo solo-anim, ni el nombre que da
  getAnimName al listar. Se confirma en el próximo build.
Pendiente aparte: las mallas Skinned (models_X/Skinned/MaleBody.x etc.) también pasan por jassimp en FileTask_LoadMesh;
hay que extender el conversor a Mesh/MeshNormals/MeshTextureCoords/SkinWeights → media/models/*.txt.

### Tamaño
anims_X/Bob: 387 MB .x → 272 MB .txt, 77 MB con gzip -1. Para el boot montar solo lo que pide ModelManager para el
menú (Bob_Idle/Walk/Run, como ya hace ANIMS_X_REQUIRED en startup_extra.py) pero en .txt bajo media/anims/Bob/, y el
resto como pack diferido comprimido. Bob_Idle: 72 KB txt / 11 KB gzip.

### Texto para docs/CODEX_TASK_CONTINUE.md (ronda 60)
```
## Ronda 60: jassimp64 → cargador .txt del juego
Bloqueo: UnsatisfiedLinkError jassimp64 en UI_Loading_ModelsAnimations → 'Required Animation Asset not found: bob/bob_idle'.
Diagnóstico YA hecho (no repetir): FileTask_AbstractLoadModel cae a media/anims/<nombre>.txt (ModelLoader.loadTxt, Java puro)
si no existe .x/.fbx/.glb en media/anims_x.
Primera acción obligatoria: git apply /datos/Projects/pz-web-code/patches/ronda59b-x-anim-to-txt.patch
Luego: en tools/pack/startup_extra.py montar la salida de
  python3 tools/pack/x_anim_to_txt.py $PZ_HOME/media/anims_X/Bob <tmp>/anims/Bob --only Bob_Idle.x Bob_Walk.x Bob_Run.x
como media/anims/Bob/*.txt y NO montar esos .x. Build, probar, commit.
Si loadTxt falla con 0 vértices, arreglá el conversor (no el juego).

## Regla anti-loop (obligatoria)
- Máximo 5 javap/unzip seguidos sin un apply_patch en el medio; al llegar a 5, editá algo o escribí en STATUS.md el bloqueo y terminá.
- Prohibido investigar internas de TeaVM (org.teavm.parsing.*, RenamingClassHolderSource, ClassRefsRenamer, substitute*).
  Para sustituir clases copiá un transformer existente de port/src/main/java/port/compiler/.
- Prohibido reimplementar librerías nativas enteras (jassimp, assimp) en Java: buscá primero un camino del juego que no las use.
- Máximo 15 comandos de lectura en total entre dos ediciones.
```

## Ronda 63 (diagnóstico de Code, 30/10 11:20 hora de Mendoza)

Conclusión: no hay un cuelgue medido en la carga de modelos. Las sondas terminan antes de que la carga tenga tiempo de correr.

Evidencia:
- diag172/173/174 usan `PZ_DIAG_WAIT=170000/175000` (local-run-62.log) y sacan una sola foto.
- En diag174, `ModelManager#create` entra a los 187211 ms (performance.now) y la foto FIBERS es de `now: 188556`. La carga de modelos llevaba 1,3 s. En ese lapso: 75 `FileTask_AbstractLoadModel#<init>`, 2 `isLoadingAnimationMeshes`, 13/12 `invokeOnRenderContext` y la entrada sin retorno es de 188533, 23 ms antes de la foto.
- En diag173 el ring termina en 186150 con `invokeOnRenderContext` recién entrado (también ms antes de la foto). El "entered 15 / returned 14" es el último instante muestreado, no un estancamiento.
- diag168 (09:00) y diag170 también terminan en UI_Loading_ModelsAnimations. Las claves UI_EpilepsyWarning_* son claves de traducción cargadas, no avance real. No hay regresión entre d584901 y d75c415 que explique esto. El FBO pasa (FBO_PROBE FRAMEBUFFER_COMPLETE; TextureFBO init devuelve).
- Bucle real (javap de ModelManager.initAnimationMeshes(Z), no copiado): `while (isLoadingAnimationMeshes()) { fileSystem.updateAsyncTransactions(); Thread.sleep(10); Core.StartFrame/EndFrame/StartFrameUI/EndFrameUI }`. Las FileTask corren en un `Executors.newFixedThreadPool(availableProcessors)` (port.compat Pool: fibras TeaVM). Solo avanzan cuando la fibra del juego cede en `Thread.sleep(10)` o en `Object.wait` de `RenderContextQueueItem.waitUntilFinished`.

Riesgos reales a medir (no confirmados): `Thread.sleep(10)` del juego no está redirigido a BrowserYield.pause (ronda 31: sleep(0) no reanudaba), y cada iteración hace un `invokeOnRenderContext` que espera a la fibra de render.

Parche: /datos/Projects/pz-web-code/patches/ronda63-models-poll.patch agrega `tools/diag/models_poll.mjs`. Es una sonda que muestrea cada 15 s hasta 15 min y corta en OnGameBoot o tras 180 s sin cambios. Solo agrega un archivo, no compila nada y no finge éxito.

## Ronda 64b — captura del menú y excepciones (2026-10-30, 14:28–14:45 hora de Mendoza)

Una sola ejecución liviana (`pz-web-code/tools/menu_pixels.mjs`, servidor propio en :8097, mismo
build ya generado, Chrome headless + SwiftShader). Se lee el framebuffer por defecto con `readPixels`
dentro del mismo callback de rAF, una vez que `MainScreenState#render` llegó a 3 (`preserveDrawingBuffer`
ya era `true` en `GLBridge`).

- **No está negro.** El render 1 de `MainScreenState#render:returned` aparece a ~445 s del arranque, el 2 a
  ~605 s. Frames 765/766: 66,95 % de pixeles no negros; frame 767: 90,97 % (2 clears y 12 draws, todo
  al framebuffer por defecto, 0 blits, ningún clear después de los draws). Captura: `~/pzshots/menu-64b.png`.
- Lo que se ve: violeta (128,64,191) en todo el canvas de 1280x720 excepto un rectángulo de 640x480
  abajo a la izquierda. Ese rectángulo está negro en el frame 765 y empieza a tener contenido muy oscuro en el
  767 (probablemente el fade-in). Además se ve la caja "ERROR 26" del contador de errores (la fuente
  se dibuja bien).
- **Causa del violeta:** `GLBridge.framebufferProbe()` (gl.mjs ~1820) hace `viewport(0,0,64,64)` y
  `clearColor(0.5,0.25,0.75,1)` para probar el FBO y no restaura ninguno de los dos. Al final del frame,
  `VIEWPORT` todavía vale `[0,0,64,64]`. Los clears del juego al framebuffer por defecto heredan ese color.
  Parche: `pz-web-code/patches/ronda64b-fbo-probe-restore-state.patch`, que guarda y restaura viewport
  y clearColor.
- El área de 640x480 es el tamaño de pantalla del Core, no del canvas. Sigue pendiente confirmar con
  una corrida posterior, cuando el fade termine, si las texturas Title aparecen.
- Las capturas negras anteriores (diag-menu68, menu-round64c) se tomaron antes del primer render
  real, o el `page.screenshot` venció porque el loop de wasm ocupa el hilo principal.

Excepciones (javap sobre el jar fijado, sin copiar código):
- `glfwCreateCursor` ×2: solo se llama desde `org.lwjglx.input.Cursor.<init>`, que usan
  `zombie.input.Mouse.loadCursor` y `Mouse.initCustomCursor` (lo llama el constructor de `GameWindow`). Se registra
  como "Exception thrown" y el arranque sigue hasta `MainScreenState#render`. **No bloquea.** Efecto real: no hay
  cursor personalizado, se ve el cursor del navegador. Es un hueco honesto y queda pendiente
  implementarlo con CSS `cursor:url()`.
- `Bullet.defineRagdollBodyDynamics`: solo lo llama `RagdollScript.uploadBodyDynamics` ←
  `toBullet` / `resetBodyDynamicsToDefaultValues`, durante la carga de scripts (y desde
  `RagdollDebugWindow`). Después del error el log sigue (`UI_Loading_Clothing`) y llega al menú. **No bloquea
  el menú.** Sí faltan los parámetros de ragdoll en Bullet: eso importa dentro de la partida, no en el
  menú.

## Ronda 66 (Code, 30/10 ~17:25 hora de Mendoza) — bloqueo real: memoria de buffers directos
Build 16:43 (incluye 12f8017 PNG grandes y 102ae4c cache de NativeArena). Corrida única de
`tools/diag/menu_pixels.mjs` (:8097, PZ_HOME pinned, nuevo `PZ_SHOT_AFTER=560` para capturar aunque
`MainScreenState#render` no llegue). Restos de corridas previas (chrome headless y server :8097) matados.
- Ya no hay "Invalid image size" de depthmaps, pero `TileDepthTextures.createTileset` →
  `TilesetDepthTexture.load` → `BrowserStb.decodePng` → `NativeMemory.memAlloc` → `NativeArena.allocate` →
  `ByteBuffer.allocateDirect` → `Heap.allocFromFreeRecord` termina en **RuntimeError: memory access out of
  bounds** (pageerror). Es el mismo tipo de error que los 2 pageerror de la ronda 65: el heap de buffers
  directos de TeaVM se queda sin lugar/corrompe con depthmaps de 1024x11008 RGBA (~45 MB cada uno).
- Después del error la wasm queda muerta: rAF sigue (~60 fps) pero 0 draws; `MainScreenState#render`
  nunca corre (sí `<init>`, `preloadBackgroundTextures`, `loadIcons`). Captura: ~/pzshots/menu-r66.png (negro, 5 % pixeles casi negros).
- Viewport: ventana creada 640x480 y luego `resized` 1280x720; ya hay viewports 0,0,1280,720 (355) además de 640x480 (46). El problema del recuadro chico probablemente quedó resuelto por a449524, falta verlo con el menú.
- Antes de 12f8017 (corrida 16:37) los depthmaps fallaban con "Invalid image size" y el arranque avanzaba muy lento (TreeMap de NativeArena), tampoco llegaba al menú.
Próximo paso: subir el heap de buffers directos (`TeaVMTool.setMinDirectBuffersSize` / `WasmGCTarget.setBufferHeapMinSize`,
hoy sin configurar en BuildCompiler) y/o liberar el buffer de stb tras subir la textura (revisar que `stbi_image_free`
llegue a `NativeArena.free`). Sin stubs: los depthmaps se tienen que cargar de verdad.

## Ronda 67 (Code, 30/10 ~19:10 hora de Mendoza) — OOB de buffers directos resuelto
- Causa real: en TeaVM 0.16 Wasm GC, `ByteBuffer.allocateDirect` sólo devuelve memoria al `Heap` desde un
  `FinalizationRegistry` de JS (asíncrono, entre tareas). `NativeArena.free` era sólo contabilidad, así que los
  depthmaps de ~45 MB se acumulaban hasta el tope de 2 GiB de la memoria lineal. Además `HeapIntrinsic` (Heap.grow)
  descarta el resultado de `memory.grow`: un grow fallido se toma como éxito y termina en
  "memory access out of bounds" en `Heap.allocFromFreeRecord`. Subir el heap a 1 GiB (corrida 18:10) NO alcanzó: el OOB volvió a ~530 s.
- Fix: `NativeArena` reutiliza los bloques grandes liberados (pool ≥64 KiB, hasta 768 MiB, reuso si capacidad ≤ 1,25× lo pedido,
  se ponen en cero como calloc). `BuildCompiler`: `setMinDirectBuffersSize(256 MiB)` para el juego.
  `menu_pixels.mjs` imprime cada pageerror en vivo.
- Corrida 18:50 (build 18:48): sin pageerror en 740 s. Pero el arranque sigue en trabajo sincrónico después de
  `exit TISLogoState` (~4 frames cada 40 s); `MainScreenState#render` todavía no corre. Captura: ~/pzshots/menu-r67*.png.
- Próximo: perfil CPU (`PZ_PROFILE_AT=400`) para ver qué consume ese tramo (sospecha: decode PNG/zeroing de depthmaps o NativeArena).

## Ronda 68
- Perfil CPU (PZ_PROFILE_AT=400, build 41db5d5): top self-time = Object.monitorExit/Enter (~12.7 s), NativeArena.putByte (4.8 s), NativeArena.block (3 s), memCopy, fill; llamado desde TileDepthTexture.load / BrowserStb.decodePng. Causa: copia byte a byte sincronizada de los depthmaps (~45 MB).
- Fix: NativeArena.putBytes/getBytes masivos (ByteBuffer.put(int,byte[],..)), copy = memmove masivo vía temporal, fill por bloques de 64 KiB; NativeMemory.memCopy(byte[]) usa los masivos.
- Resultado r68b: a los 200 s se ve el logo TIS real (antes negro). El menú aún no se alcanza dentro de la ventana de ejecución; el log stdout de menu_pixels quedó vacío (sin perfil). Pendiente: repetir con ventana mayor + perfil y pasar a bulk los memCopy de short/int/float.

## Ronda 69
- memCopy bulk para short/char/int/long/float/double (vista LE sin registrar NativeArena.raw) y memASCII/UTF8/UTF16 con getBytes.
- malloc vs calloc: nmemAlloc/nmemAlignedAlloc -> NativeArena.allocateUninit (bloque reciclado del pool sin poner a cero, como malloc real); nmemCalloc/memCalloc* siguen poniendo a cero. ByteBuffer.allocateDirect/allocate() por defecto ponen a cero.
- r69 (PZ_SHOT_AFTER=400): se ve el diálogo real "TERMS OF SERVICE AND PRIVACY NOTICE" de PZ (mapache juez, botones ACCEPT/QUIT) — UI real de Lua/Xui renderizada, previa al menú principal.
- Perfil a 300 s: getError 15.6 s (gl.mjs drainQueue llama gl.getError tras CADA llamada GL: sync roundtrip), NativeArena.free 1.8 s + removeIf 2.4 s (escaneo lineal de views por cada free). Próximos fixes.

## Ronda 70
- gl.mjs: drenado diferido de gl.getError (solo inmediato para texImage2D/texSubImage2D/readPixels/compressedTexImage2D; flushErrors() antes de glGetError, errorsFor, internalErrors y de cada marca de seq interna). PZ_GL_EAGER_ERRORS=true restaura el modo por llamada.
- NativeArena: índice de vistas por bloque (Block.owned); free() ya no recorre todas las vistas. retired acotado a 4096 entradas.
- menu_pixels: log también a <shot>-log.txt; PZ_CLICK_AT/PZ_CLICK_AFTER hacen clic real con el mouse.
- r70 (puerto 8098): getError 15.6 s -> 0.9 s; ~9400 frames a los 424 s (antes ~850). TermsOfServiceState renderiza; hover sobre ACCEPT se resalta pero el clic no se aplica.
- Causa: window.mjs poll() hacía input.drain() (vacía toda la cola) y despachaba solo events[0]; los clics tras movimientos de cursor se perdían. Fix: shift de un evento por poll.
- Nuevo hot spot: TileDepthTexture.load 7.7 s + decodePng 4 s + ByteBufferWasmGC.copy 1.4 s + inflate 1.3 s.

## Ronda 71

- Probe r71 (8098) con el fix de `poll()` de r70: 4 clics reales en ACCEPT (610,509); el diálogo TOS sigue, ~21k frames a 608 s, nunca entra MainScreenState. El fix de poll era correcto pero no suficiente.

## Ronda 72

- Instrumentación JS (window.mjs: `kindStats`, `pollCalls/pollNulls`, `buttonQueries`, `buttonTrue`; menu_pixels.mjs las vuelca en cada `CLICK`).
- Resultado: los eventos de botón se despachan al callback GLFW (handle registrado para kind 1) y la cola no se atasca; PZ consulta `glfwGetMouseButton` ~33 veces/s (zombie.input.MouseState.poll en RenderThread, swap en Mouse.update del game thread).
- Agregado latch de presión (estilo GLFW_STICKY_MOUSE_BUTTONS) en input.mjs/window.mjs para que un press+release entre dos muestreos no se pierda.
- r72d: Java recibe el botón 0 en estado presionado (`buttonTrue` 1→4→9 tras los clics), pero ACCEPT (ISTermsOfServiceUI:onButtonAccept → saveOptions → fromLua0("exit")) sigue sin dispararse y no hay errores nuevos en consola. El fallo está del lado Java/Lua (UIManager.updateMouseButtons → ISButton onMouseDown/onMouseUp, o saveOptions), no en el input del navegador.
- Próximo: traza Java en UIManager.updateMouseButtons / Mouse.isButtonPressed y en el dispatch Lua de onMouseUp (requiere rebuild).

## Ronda 73

- Reporte real de Paolozky (Chrome propio, 8097): aviso de fotosensibilidad dibujado en el cuadrante inferior izquierdo y recortado. Causa: su pantalla es HiDPI; window.mjs multiplicaba el drawing buffer por devicePixelRatio mientras PZ/lwjglx dimensionan glViewport y la UI con el tamaño de ventana (Display.getWidth) → dibujo en 1/4 del canvas y coordenadas de cursor x2.
- Fix (solo JS): buffer == tamaño de ventana (ratio 1) en create/setWindowSize/input.resize, contentScale 1, foco del canvas al crear la ventana (teclado).
- Probe r73 con `PZ_DPR=2` (nuevo env en menu_pixels; perfil ahora concurrente con los clics): TOS ocupa todo el canvas y centrado. ACCEPT sigue sin dispararse aunque Java lee el botón presionado (trues 1→4→9) → falta traza Java en UIManager/ISButton (requiere rebuild).

## Ronda 74 (30-oct, 22:40 -03)
- Nuevas sondas en StartupProgress: Mouse#update, UIManager#updateMouseButtons, UIElement#onConsumeMouseButtonDown/Up, onMouseDown/Up, TermsOfServiceState#fromLua0. menu_pixels: PZ_CLICKS y linea COUNTS (pzGameCounts) 8 s despues de cada click.
- Resultado (r74c, 2 clicks en 610,509, DPR 1): la cadena Java llega hasta UIElement#onMouseDown 1 vez y onMouseUp 2 veces, pero onMouseDown NO recursa a ningun hijo: el ISButton ACCEPT nunca recibe el click. fromLua0 = 1, y esa unica llamada es "created" desde enter(), no "exit". TermsOfServiceState#update siguio devolviendo Remain (557 updates); MainScreenState#enter = 0.
- Una espera larga (r74, 1 click, +6 min) tampoco sale del TOS: no es solo lentitud.
- "error 26" de Paolozky: es casi seguro el contador de errores en pantalla de PZ (unas 27 "Exception thrown" = FileNotFoundException durante el boot, mas fallos de AngelCodeFont con mainfont_0.png). Ocurre antes del TOS; no explica ACCEPT por si solo.
- Siguiente: registrar x,y y los rects de los hijos en el hit-test de UIElement#onMouseDown (offset/escala vs hover).

## Ronda 75 (30-oct, 23:00 -03)
- Traza de argumentos (BrowserProgress.traceInts/traceDoubles, inyectada en UIManager#updateMouseButtons y UIElement#onMouseDown; la sonda la vuelca como UITRACE).
- r75 (click en viewport 610,509): mx,my=560,507 y el panel recibe 220,319. ACCEPT empieza en x=10+anchoTextura+10, y el hit-test de PZ es estricto (x > ui.x), asi que el click caia justo en el borde izquierdo: no habia desfase ni error de escala.
- r75b (click en 660,515, que da 270,325 en el panel y 50,16 en el boton): el boton recibe onMouseDown, fromLua0 llega a 2 (created + exit), MainScreenState#enter = 1 y la consola muestra "STATE: exit zombie.gameStates.TermsOfServiceState". ACCEPT FUNCIONA.
- Despues del TOS hay 47 lineas de FileNotFoundException y despues silencio, aunque los frames siguen subiendo; falta ver que dibuja MainScreen.
- Fuentes: mainfont_0.png y las otras no existen sueltas en PZ_HOME/media; estan dentro de texturepacks/UI.pack. AngelCodeFont llama a Texture.getSharedTexture antes de que el pack este disponible. Hay que confirmar si el juego nativo hace lo mismo.

## Ronda 76–77 (30/10, ~23:40 Mendoza)

- **r76**: con capturas periódicas tras ACCEPT, `MainScreenState#enter` entra y no retorna; perfil wasm 100% idle.
  Causa en `menu-r76c-log.txt`: `RuntimeError: dereferencing a null pointer` en
  `HumanVisual::getHairModel ← HumanVisual::copyFrom ← AnimatedModel::setModelData_0`.
  Claves nuevas en StartupProgress (setMusicState, triggerEvent, getSharedTexture, invokeOnRenderContext,
  getOperatingSystemMXBean): ninguna entra antes del crash.
- **r77, causa raíz**: `HairStyles.init()` (GameWindow:208) parsea `media/hairStyles/hairStyles.xml`; no estaba
  montado (0 entradas en packs/index.json) → `HairStyles.Parse` traga la FileNotFoundException → `instance == null`
  → getHairModel deref null. Es una de las FileNotFoundException del arranque.
- **Fix** (sin stubs): `startup_extra.py` monta `media/hairStyles/`, `media/clothing/`, `media/voiceStyles/`
  (1860 archivos, ~9 MB). Packs regenerados en `build/real-browser/packs-r77`: pack.py + alias_index (fonts/,
  fonts/EN/1x/) + case_alias (AnimSets/, anims_X/) + boot_options. Verificado: superset exacto del índice anterior
  (10559 rutas, 0 faltantes, 0 sha distintos) + las nuevas = 12419.

## Ronda 78
- MapSpawnSelect.lua:954 "expected 3 arguments, got 1": `DateFormat.format(Date)` no era reflectable (solo SimpleDateFormat estaba en la lista). `tools/exposure/generate.py` ahora agrega los supertipos JDK (`javap -public`) de cada clase externa expuesta, así DateFormat/Format entran en reflection-lua.txt. MainScreen:instantiate completa.
- Menú principal dibujado: logo arriba-izquierda + columna de botones (x≈160–280, y 280–680), verificado por píxeles (r77: 0).
- `ReflectionConfiguration`: los constructores sin args de subclases de COMPONENT_BASES se seleccionan siempre (ya no filtrado por `names`). Arregla "Class cannot be created (missing no-arg constructor)" de ResourcesScript/DurabilityScript, que borraba items (BeerImported...) en Loading Scripts.
- Fuentes AngelCode (mainfont_0, mainfont2_0, zomboidDialogue.bmfc_0): initFonts() corre antes de LoadTexturePack("UI"), así que texturePackTextures está vacío; no hay PNG suelto en PZ_HOME. Es comportamiento del juego original, no del port; no se falsea.
- FileNotFoundException restantes: 6 son packs 2x no montados (Overlays2x, JumboTrees2x, ...), esperados; 3 sin ruta (tras LightingFPS, salida de TISLogo, salida de TOS) pendientes.
- Test real: `playwright.menu.config.mjs` + `tests/browser/menu.spec.mjs` (puerto 8098, packs reales, sin prepare_real.py).

## Ronda 79 (Code, 31/10 madrugada) — del menú hacia la partida
Objetivo: Nueva partida -> spawn -> personaje -> mundo con jugador WASD. Puerto 8098, driver `tools/diag/drive.mjs`
(un directorio por corrida, heartbeat cada 30 s) y simbolizador `tools/diag/wasm_addr.mjs` (convierte
`fakeMethod(Throwable.java:N)` en el símbolo Java usando la sección `name` del `boot.wasm` de ESA corrida).
- **Media bajo demanda**: `media/maps` son 4,3 GB (12k archivos) y no entran en el preload de 512 MB. El dev server
  publica `/media-manifest.json` (47k rutas + tamaños, solo metadatos) y `vfs.mjs` registra esas rutas como remotas:
  `kind/size/children` sin bajar nada, lecturas con XHR síncrono + `Range` en bloques de 1 MiB (caché LRU 384 MiB),
  `readFile` en trozos de 16 MiB. Los packs mantienen prioridad. Se excluyen `models_X/` y `anims_X/`: con esos
  `.x/.fbx/.glb` visibles el juego elige jassimp64 (nativa ausente) en vez de los `.txt` de la ronda 60.
- `children()` era lineal sobre todo el VFS; ahora hay un índice padre->hijos (`ChildIndex`).
- Canvas fijo en (0,0) en `game.html` (antes empezaba ~50 px a la derecha y NEXT quedaba cortado). `menu.spec.mjs`
  hace clic en ACCEPT en (610,515).
- NEW GAME (selección Apocalypse/...) se dibuja y NEXT funciona. Frenos encontrados en orden:
  1. `SpawnRegions.lua` sin `media/maps` -> resuelto con el manifest.
  2. `reloadLuaFile(spawnpoints.lua)` -> `IndexOutOfBoundsException` en `TDefaultPath.normalize` de TeaVM: para una
     ruta relativa copiaba el primer segmento desde `start-1 = -1`. Parche en `scripts/prepare_teavm.py`
     (`patch_default_path`).
  3. `ImagePyramid.openZipFile` -> `FileSystems.newFileSystem(zip)` sin provider en TeaVM. Nuevo
     `port.runtime.ZipFileSystem` (solo lectura, sobre `java.util.zip.ZipFile`; test JVM `ZipFileSystemTest`).
  4. `WorldMapRenderer`: `glPolygonMode(GL_FILL)` (aceptado: WebGL2 siempre rellena; otros modos lanzan) y
     `glDrawRangeElements` con índices en memoria cliente (sin ELEMENT_ARRAY_BUFFER): `gl.mjs#drawElementsClient`
     los sube a un buffer scratch para ese draw.
- Nativas del mundo (simplificaciones DOCUMENTADAS, no la librería original):
  - `Lighting64`: las 50 nativas de `LightingJNI`. Luz = cielo de `stateEndFrame` (rmod/gmod/bmod * ambient) + luces
    puntuales sin oclusión; todo cuadrado dentro de la distancia de visión es SEEN|CAN_SEE|COULD_SEE (no hay línea de
    visión: las paredes no ocultan). Formato de `getSquareLighting` tomado de cómo lo decodifica
    `LightingJNI$JNILighting.update()` (javap, no copiado).
  - `PopMan64`: entradas in-world aceptadas; **sin población virtual** (no se crean zombis desde la densidad del mapa).
  - Pathfinding: `/Zomboid/debug-options.ini` con `Pathfind.UseNativeCode=false` (lo escribe `game.mjs` si no existe):
    `IsoWorld.init` usa entonces el pathfinder Java del propio juego (`PolygonalMap2`).
- Más frenos resueltos (en orden de aparición):
  5. Lecturas remotas devolvían 200 (archivo entero): Chrome revalida su entrada parcial con `If-Range` + ETag débil.
     URL única por bloque (`?r=a-b`) + `Cache-Control: no-store`. Probado aislado en Chrome.
  6. `TZipFile` de TeaVM: EOFException al inflar entradas raw; parche `patch_zip_inflater_stream` (byte ficticio
     final, igual que `java.util.zip.ZipFile`). Con esto **la selección de spawn se ve completa** (mapa de Muldraugh).
  7. GL del mapa: binding de ELEMENT_ARRAY_BUFFER es por VAO (se consulta a WebGL2), `glEnable/DisableClientState`,
     `GL_VERTEX_ARRAY`+`glVertexPointer` -> `pzVertex` en ubicación fija 15, `GL_QUADS` emulado con triángulos.
  8. Profesión/rasgos OK. Avatar: trampa en `AnimationPlayer.updateModelTransformsInternal` por esqueletos `.txt`
     con 3 raíces; `x_anim_to_txt.py` ahora deja una sola raíz (como `Bob_Sprint.txt`) y `tools/pack/overlay.py`
     reapunta las entradas del índice de packs a un blob nuevo (índice previo en `index.json.prev`).
  9. GLSL: inicializadores de uniform con aritmética constante (`1.0 / 64.0`); `glFramebufferTexture2D(...,0,...)`
     desacopla. Pantalla de personalización OK (el avatar 3D aún no se ve en la vista previa).
  10. PLAY -> `GameLoadingState` ("THESE ARE THE END-TIMES"). `IsoMetaGrid.Create` tarda ~150 s (4k lotheaders por
      XHR). `ObjectIDManager.createObjectID` necesitaba constructores reflejables de `ObjectID$*`.
  11. Trampa wasm por NPE en `InventoryItem.getActualWeightUnmodded` (ítem con `name` nulo creado desde Lua). En la JVM
      es una excepción capturable; se activa `TeaVMTool.setStrict(true)` para el juego.
- Pendientes vistos: listas de rasgos sin recorte (scissor/stencil), ropa/pelo sin modelo (`models_X` excluido),
  `AnimNode` XML con JAXB `NoSuchMethodException`, `options.ini` no se puede guardar (montado de solo lectura).
- Continuación (builds k–o, corridas pzd13–pzd17):
  12. `TeaVMTool.setStrict(true)` para el juego: el NPE de `getActualWeightUnmodded` pasa a ser excepción Java que
      Lua (forageSystem) atrapa, como en la JVM. +3 MB de wasm.
  13. MapCollision64: init/meta/chunks aceptados, sin trabajo pendiente; `n_pathTask` sigue lanzando con su nombre JNI.
  14. SQLite: conexiones al mismo archivo comparten la base (refcount). Queda un "Cannot persist an uncommitted SQLite
      transaction" no fatal (export mientras otra conexión tiene una transacción abierta).
  15. PopMan64.n_init: firma real (minX, minY, width, height); el mínimo es negativo (-250).
  16. `BulletWorld`: mundo físico sin cuerpos (initWorld, chunks, ToBullet contado, stepSimulation, conteos 0) y las
      definiciones que suben los scripts (ragdoll constraints/anchors/bodyPartInfo/bodyDynamics, mallas). Sin
      `defineRagdollBodyDynamics` el ScriptManager marcaba error de carga e `IsoWorld.init` rechazaba el mundo.
- **Resultado pzd17**: `IsoWorld.init` completo, `GameWindow.save()`, "game loading took 343 seconds", "Click to Start",
  `IngameState.enter` con "1 players found / player 0 is mouse". Pantalla negra después.
- **Bloqueo actual**: `zombie.vehicles.Clipper.newInstance` (nativa Clipper2, 13 natives + 6 de `ClipperOffset`) en
  `FBORenderOcclusion.init` aborta `IngameState.enter`. Clipper también lo usan `CollideWithObstaclesPoly` (colisión del
  jugador), `PolygonalMap2` (pathfinder Java elegido), `VisibilityPolygon2`, `WorldMapGeometry`, `Zone`. Plan: implementar
  las natives sobre una librería Java de Clipper (licencia Boost, instalada en `.toolchain/m2`), derivando el formato de
  cada `ByteBuffer` (addPath/getPolygon/triangulate) de sus consumidores con javap, con un test JVM por formato. No
  improvisar semántica de `addLine`/`generatePolygons(type, delta, join)` sin verificarla en el bytecode.
- Otros pendientes vistos: `Bullet.setVehicleParams` al aparecer vehículos en chunks (no fatal; vehículos sin física),
  `ClipperOffset` en zonas, `CopyFrame` NPE (anims faltantes), avatar 3D invisible en la vista previa, listas sin recorte.
- Tiempos (SwiftShader): menú ~5,5 min; NEW GAME->spawn ~1 min; carga del mundo 343 s (lotheaders por XHR ~160 s).

## Ronda 80 (Code, 31/10) — Clipper, vehículos y pantalla negra
Commits en `code/menu`: `acf5ac6` (nativas Clipper), `d80bc78` (`Bullet.setVehicleParams`), `f676527`
(`glGenerateMipmap` + `glUniformMatrix` con transpose), `0b3f48f` (renombre de built-ins GLSL, `glColor*`,
`glBlitFramebuffer`, pilas de atributos).
- **PZClipper64 en Java**: las 13 nativas de `zombie.vehicles.Clipper` (incluida `n_init`) y las 6 de `ClipperOffset` en
  `port/natives/ClipperEngine.java` y `port/natives/ClipperOffsetEngine.java`, sobre Clipper2-java 2.0.1 (BSL-1.0,
  jitpack) + earcut4j 2.2.2 (ISC). `scripts/install_clipper_libs.sh` las instala en `.toolchain/m2` con sha256 fijado.
  El transformer `port/compiler/ClipperNativeCompatibility.java` agrega un campo `port$engine` a las clases del juego y
  reenvía las natives de instancia.
  - Semántica tomada del bytecode de los consumidores (`CollideWithObstaclesPoly`, `FBORenderOcclusion`, `Zone`,
    `WorldMapGeometry`, `IsoMetaGrid`, `VisibilityPolygon2`) y de un objdump local de `libPZClipper64.so` (no versionado):
    - escala 1000: `floor(v*1000f+0.5f)`;
    - buffers absolutos desde el índice 0, big-endian, sin tocar `position`; `BufferOverflowException` al pasar la
      capacidad (`BufferUnderflowException` en la entrada);
    - `FillRule.Positive` + `PolyTree`; offset con `ClipperOffset(2.0, 0.0)`, cerrados=`Polygon`, abiertos=`Butt`;
      los resultados abiertos se agregan como polígonos sin agujeros;
    - `clear()` solo descarta las entradas;
    - `generateTriangulatePolygons` = celda 8x8 menos los anillos exteriores (EvenOdd);
    - `triangulate` = (x,y) planos por índice de earcut, devuelve la cantidad de índices; `triangulate2` = `short`
      vertexCount + vértices + índices `short`.
  - El orden del enum `JoinType` difiere entre Clipper2-java y C++: se mapea por nombre.
  - Test JVM `port/src/test/java/port/ClipperEngineTest.java`: 13/13.
  - Resultado: Clipper ya no aborta `IngameState.enter`.
- **Siguiente freno: `Bullet.setVehicleParams`** (`BaseVehicle.updateBulletStats`). `libPZBullet64`, para un id de
  vehículo sin cuerpo, loguea `PZBullet::setVehicleParams> Vehicle not found. VehicleId:%d` y devuelve -1; `BulletWorld`
  hace lo mismo (el llamador descarta el resultado).
- **Después: `Bullet.addVehicle`** (`CarController.<init>` vía `BaseVehicle.createPhysics` en
  `IsoChunk.doLoadGridsquare`). NO implementado: `getVehiclePhysics` devuelve cada frame un registro completo por vehículo
  (transform de Bullet, velocidad, ruedas); un modelo de vehículo estacionado requeriría conversiones de coordenadas
  derivadas, así que no se inventó. Para probar: Custom Sandbox con "Vehicles" destildado (opción del jugador en la UI, no
  un cambio de código). Así `IngameState.enter` completa.
- **Puente GL**: `glGenerateMipmap(GL_TEXTURE_2D)` -> `generateMipmap` de WebGL2; `glUniformMatrix*fv` con
  `transpose=true` sube una copia transpuesta en CPU. Test `tests/node/gl-transpose.test.mjs`.
- **Pantalla negra, causa 1 (GLSL)**: GLSL ES 3.00 prohíbe redeclarar built-ins y `media/shaders/util/math.glsl` del juego
  define `max/min/clamp`. Log de ANGLE: `'max' : Name of a built-in function cannot be redeclared as function` (visto en
  Chrome con `pzRuntime.gl.shaderTrail`). `ShaderProgram.compile` destruye el programa en silencio (`DebugType.Shader`
  apagado), `Shader.onProgramCompiled` nunca corre, `matrixId` queda en 0 y `setMatrixPalette` -> "Unknown uniform
  location 0". Fix: `glsl.mjs#renameBuiltinOverloads` renombra las sobrecargas del usuario a `pz_user_*` y reescribe las
  llamadas según tipo; el conjunto de prototipos de `gl.mjs` incluye los nombres renombrados. Afectaba a 21 programas del
  juego (basicEffect, floorTile, wallTile, fog, screen, blur, shaders de vehículos, ...). Tests
  `tests/node/glsl-builtin-redeclare.test.mjs`.
- **Pantalla negra, causa 2 (análisis estático)**: `GL11.glColor3f/4f` sin mapear lanzaba cada frame dentro de
  `ItemModelRenderer.DoRender` / `IsoObjectModelDrawer.renderModel` después de `glPushAttrib/glPushClientAttrib` sin
  `finally` -> las pilas se llenaban ("client attribute stack is full at 64"). Fix: color actual en el puente, aplicado con
  `vertexAttrib4f` a `pzColor` cuando su array está deshabilitado; `GL_CURRENT_BIT` en push/pop; las pilas ahora registran
  `GL_STACK_OVERFLOW/UNDERFLOW` (0x0503/0x0504) como en escritorio en vez de lanzar; `glBlitFramebuffer` mapeado.
- Huecos conocidos de la revisión estática: modo inmediato `glBegin/glEnd` (`DeadBodyAtlas`, `WorldItemAtlas`), pila de
  matrices de función fija (`ModelManager.RenderParticles`), estado de arrays habilitados por VAO; notas de revisión
  (`typeOf` del tipo de retorno de llamadas a funciones de usuario en `glsl.mjs`; sugerencia de ubicación fija para
  `pzColor`).
- Diagnóstico agregado: `StartupProgress` `EXTRA_CLASSES` con `ShaderProgram` + `skinnedmodel.shader.Shader`; contadores
  `uniformNames` en `gl.mjs`.
- Tests: Node 68/68, inventario python OK, JVM `ClipperEngineTest` 13/13.
- Proceso: un solo build de Maven y un solo Chrome a la vez (RAM); planes del driver en `/tmp/pzdNN/plan.txt`; secuencia
  de clics del sandbox: Custom Sandbox (956,540) -> NEXT -> NEXT -> pestaña Vehicles (58,376) -> destildar (938,127).
- Resultado de la corrida pzd86 (build e, Custom Sandbox sin vehículos):
  - La UI de la partida se dibuja: Survival Guide, inventario, panel del personaje y "- Game Paused -"
    (`~/pzshots/r86-after-d.png`, no versionada).
  - Al cerrar la guía, el mundo mostró sólo un lavatorio 3D y rectángulos de contorno rojos/verdes sobre negro
    (`r86-noguide.png`).
  - `Display.update` quedó congelado en 5963: `glGetInteger(GL_CURRENT_PROGRAM)` usaba 0x8bad en vez de 0x8B8D;
    `WeatherParticleDrawer` abortaba cada frame. Corregido en `d117493`.
  - Errores de shaders restantes en esa corrida: `pow/clamp` con argumentos int, identificador `texture` reservado,
    `ivec2*float`. Corregidos en `421a22d` (conversiones implícitas int->float y renombre de identificadores
    reservados), sin probar en el juego al momento de esa corrida.

## Ronda 80 — iteración (frente C, 31/10)
- JS sólo (`port/web/*`, `port/runtime/*.mjs`): `bash scripts/sync_web.sh` (~0,2 s). Es exactamente la parte web de
  `scripts/build.sh` (que ahora la llama): copia los archivos cuyo contenido cambió (los demás conservan mtime -> mismo
  ETag -> 304), `copy_web_vendor.py`, `prepare_sqlite.py`. No toca `boot.wasm`, `boot.wasm-runtime.js`, `pulse-map.json`
  ni `build-state.json` (salen de Java/TeaVM). El dev server lee cada archivo por petición (`Cache-Control: no-cache`,
  ETag débil tamaño+mtime), así que basta recargar la página; sólo hay que reiniciarlo si cambia `dev-server/*.mjs`
  o el manifest de media. Cambios JS que agregan imports Wasm nuevos sí necesitan el build Java.
- Medición del build `--with-game` por defecto (logs `build-round79*/80*-game.log`): 6:17–9:05 min, mediana ~6:55.
  TeaVM (`compile_ms`) es 366–527 s, ~97 %; el resto: exposición Lua 10 s (javap + escaneo del jar), glgen 0,3 s,
  `prepare_teavm` + JdkContracts ~1 s, javac 302 fuentes + tests JUnit (~6 s, CompilerRegressionTest 5 s) + jar ~12 s,
  gates 3 s, `PolymorphicCalls.scan` 1,7 s. Con marcas de tiempo nuevas en `BuildCompiler` (build-round80g-dev1):
  DEPENDENCY_ANALYSIS 364 s, COMPILING+emisión 39 s.
- JFR del build (`-XX:StartFlightRecording`): GC 8,4 s de pausa en total (2 %; cambiar de GC no vale la pena).
  `DependencyAnalyzer.filterType` = `classFilters.stream().allMatch(...)` en CADA propagación de tipo: ~38 % de las
  muestras (maquinaria de streams + `JSOPlugin$1.accept` -> `JSTypeHelper.isJavaScriptClass`). El único `ClassFilter`
  de 0.16.0 es el de JSOPlugin, función pura del nombre de clase sobre el class source sin procesar; el
  `DependencyType` está internado con índice único. `prepare_teavm.py` `patch_dependency_filter` agrega un memo por
  índice (byte[]), opt-in con `-Dpz.teavm.filterCache=true`; sin la propiedad corre el stream original.
- `scripts/build.sh --with-game --dev` (por defecto sin cambios): mismas opciones de TeaVM (SIMPLE, strict, mismos
  transformers y gates) +: memo de filterType; exposición Lua cacheada por hash de entradas
  (`port/target/cache/exposure/<sha>`: inventory.json, que fija los jars por SHA-256, generadores y `$JAVA_HOME/release`);
  fuentes generadas idénticas conservan su mtime (javac no recompila por eso); `mvn -o`; sin tests JUnit
  (`-Dmaven.test.skip`) ni jar (fase `prepare-package`). Compila en `port/target/web-staging/game` y, con los gates
  aprobados, `scripts/swap_staged.sh` reemplaza boot.wasm/pulse-map.json/build-state.json servidos por rename atómico
  y corre sync_web: el `boot.wasm` servido nunca desaparece. `PZ_SWAP_LOCK=/tmp/pz80-locks/chrome.lock` copia el staging a
  `web-staging/ready-<ts>` y deja un proceso desacoplado (setsid, fds >2 cerrados para no heredar build.lock) que
  publica al obtener ese lock (log `port/target/web-staging/swap.log`); el build termina y suelta build.lock.
  `PZ_NO_SWAP=1` deja el resultado en staging. Comando de la ronda:
  `flock /tmp/pz80-locks/build.lock env PZ_SWAP_LOCK=/tmp/pz80-locks/chrome.lock bash scripts/build.sh --with-game --dev`.
- Resultado medido (mismas fuentes salvo 28fed96): dev1 (sin memo, con JFR) TeaVM 403 s / maven 411 s;
  dev2 (con memo) DEPENDENCY_ANALYSIS 203 s, TeaVM 238 s, maven 246 s, prepare 1 s (antes 11–12 s). Total
  ~4:10 contra ~7:00. Clases alcanzables iguales (10319); la diferencia de funciones del name section entre dev1 y
  dev2 es exactamente lo agregado por 28fed96 (19 `port.shim.GL11::*` nuevas, 7 `GL*C` que dejaron de alcanzarse).
- Descartado: optimización por debajo de SIMPLE (no existe; ya es la mínima); `setIncremental` (cachea programas por
  fecha de clase, no ve las entradas de nuestros transformers —natives.tsv, gl-overrides, reflection.txt— y no
  cachea el análisis de dependencias, que es el 90 %); `setFastDependencyAnalysis` (menos preciso: más métodos
  alcanzables y más corrutinas, arriesga el gate de Thread.currentThread). TeaVM no es determinista entre builds
  (orden de `$$_impl` de anotaciones en pulse-map), así que no se puede comparar boot.wasm por hash.
- Cambios que también afectan al build por defecto: `DependencyAnalyzer`/`DependencyNode` se recompilan desde las
  fuentes 0.16.0 (javap: mismos miembros salvo numeración de lambdas; sin la propiedad ejecutan el camino original),
  `port/pom.xml` declara `teavm-relocated-libs-asm(-tree)` (ya estaban en el classpath de ejecución), sync_web copia
  sólo lo que cambió y BuildCompiler imprime tiempos por fase.
- Riesgos: `--dev` no corre los JUnit (correr `scripts/build.sh` normal antes de commitear cambios del compilador);
  la caché de exposición no ve cambios de `tools/exposure` fuera de `generate.py`; el swap no es atómico entre los
  tres archivos (una página que carga justo en ese instante podría ver boot.wasm nuevo con pulse-map viejo).

## Ronda 80 — frente H (31/10): JAXB de AnimNode, clips y root motion del jugador
- **JAXB `onground_breathing.xml`** (único XML de AnimSets que falla, el único con `<m_BlendCurve><Sequence>`): la
  cadena es `AnimNode.blendCurve` (InterpolatorSlot) -> `@XmlElements({@XmlElement(name="Sequence", type=LerpSequence.class), ...})`.
  `LerpSequence`/`LerpPolyline` sólo aparecen como valores `Class` de esa anotación (ningún `new` en el jar). En TeaVM
  0.16.0 `AnnotationGenerationHelper.propagateAnnotationInstance` propaga el valor a un pseudo-campo `$type` que el
  accesor generado nunca lee (devuelve `ClassInfoUtil.resolve(info.type()).classObject()`), así que el `Class` no llega al
  nodo de clase de `getDeclaredConstructor`/`getName`: sin constructor ni nombre -> `NoSuchMethodException` y
  "Cannot instantiate null". En el boot.wasm de pzd88 no existen `LerpSequence::<init>` ni `LerpSequence$LerpSequenceEntry`.
  Fix: `scripts/prepare_teavm.py` `patch_annotation_class_values` (propaga también al resultado del accesor si el
  miembro es `Class`/`Class[]`). Test: `CompilerRegressionTest.annotationClassValuesReachReflection` (falla sin el
  parche, pasa con él).
- **`afterUnmarshal`**: JAXB llama al callback de clase `afterUnmarshal(Unmarshaller, Object padre)`; `LerpSequence` y
  `LerpPolyline` (los únicos del jar) arman ahí la tabla (`parse()`). `PortXmlBeans` no lo llamaba: la curva quedaba con
  0 entradas y `lerp()` devolvía 0 sin error. Ahora se llama tras leer cada bean (padre = bean contenedor, null en la raíz).
  Nota RI: `LerpSequenceEntry.lerpType` es `@XmlElement(name="out")` pero el XML trae `out` como atributo: el RI lo
  ignora (queda null) y nosotros también.
- **Root motion**: `AnimNode` por defecto `m_DeferredBoneName=Translation_Data`, eje Y, `useDeferredMovement=true`;
  `AnimationPlayer.play` busca ese hueso en el SkinningData del modelo ("Deferred bone not found" si falta: 0 veces en
  pzd88). `IsoGameCharacter.doDeferredMovement` traslada con `getDeferredMovement` sólo si
  `isAnimationUpdatingThisFrame()`. `Bob_Walk.txt` convertido conserva la pista T de `Translation_Data` igual al .x
  (ticks 160/4800 -> 0,0333 s; z -0,047244 por clave; R (w,x,y,z)=(-.5,-.5,.5,.5) -> (.5,-.5,-.5,-.5)). Los clips tienen
  35 huesos (Translation_Data=34) y `male_skeleton.txt` 45 (=44): `ModelLoader.loadTxt` remapea `Keyframe.bone` por
  nombre contra el SkinningData destino, como el camino .x. La conversión no es la causa de la falta de traslación.
- **Clips**: `ModelManager.loadAnimsFromDir` carga TODO lo que lista `media/anims_X/<dir>` al arrancar (el juego real
  tiene los 2209 clips en memoria). Sólo servimos Bob_Idle/Walk/Run. Faltan p. ej. `Bob_WalkSlow`, limps (2D blend de
  `defaultWalk`), `Bob_Idle_HeavyBreathing` (2D blend de `Idle`), giros `Bob_IdleTurn90L/R`, `Bob_IdleTurn180L/R`,
  `Bob_IdleToWalk180L/R`, `Bob_IdleToRun`; `CopyFrame.exec` NPE = `getAnimationClip("Bob_IdleDrinkPopCan")` nulo
  (scripts/generated/animations.txt). Cierre de `m_AnimName` en AnimSets/player/{idle,movement,run,sprint,turning*}:
  241 nombres, 239 existen (49 MB de .x, ~35 MB .txt); todo AnimSets/player: 1388 (323 MB .x); todo anims_X: 2209 archivos,
  513 MB .x (Bob 1650/387 MB -> 272 MB .txt, 77 MB gzip -1, conversión ~62 s medida en la 59b; Zombie 185/46 MB).
- **Clips servidos (aprobado por coord.)**: `stage_txt.py --bob-anims locomotion` -> 240 clips (cierre de 239 + 
  `Bob_IdleDrinkPopCan`; faltan en el juego `AnAnim` y `Bob_SprintToStop_2h_Heavy`), 14 s, 55 MB por copia.
  Se montan dos paths por clip: `media/anims_X/Bob/<Nombre>.txt` (lo que lista `loadHumanAnimations`: `animsXFile`
  con mayúsculas reales y `isAnimationsMeshDirectory` = `ArrayList.contains("Bob")`, sensible a mayúsculas) y
  `media/anims/bob/<minúsculas>.txt` (lo que lee `checkExtensionType`). El alias `anims_x/bob/*.txt` no hace falta
  (`checkExtensionType` sólo busca `.fbx/.glb/.x` ahí). `overlay.py --add` pone ambos sobre el mismo rango y
  `vfs.mjs`/`packs.mjs` lo cuentan una vez. Presupuesto: packs 519,9 MB de 536,9 MB (antes 464,8): quedan ~17 MB para
  `/Zomboid` (partidas). Bob completo (1650 clips, 272 MB) no entra en el preload de packs: haría falta servirlo por el
  manifest remoto (hoy excluye `anims_X/`) o subir `maxBytes`; además `loadAnimsFromDir` lo parsearía entero al arrancar.

## Ronda 80 — frente K (31/10): personaje invisible (sólo la sombra)
Análisis estático (`javap -c` del jar fijado, volcado en /tmp; `translateShader` sobre basicEffect real):
- **Causa más probable: el layout de vértices de las mallas `.txt` no coincide con las ubicaciones de basicEffect.**
  `VertexBufferObject.BeginDraw` usa el índice del elemento del `VertexFormat` como índice de atributo
  (`glVertexAttribPointer(i, ...)`). `ModelLoader.loadTxt` (el camino que usamos: MaleBody.x convertido a `.txt` por
  `tools/pack/x_anim_to_txt.py`) arma 0 Vertex(12), 1 Normal(12), 2 Tangent(12), 3 TexCoord(8), 4 BlendWeight(16),
  5 BlendIndex(16), y el caso Tangent de BeginDraw no llama `glVertexAttribPointer` ni habilita el array.
  `ImportedSkinnedMesh` (camino jassimp de los `.x`, el de escritorio) arma 0 V, 1 N, 2 BlendWeight, 3 BlendIndex,
  4 TexCoord. `basicEffect.vert` (y animalEffect, door, vehicle*, aim_outline_solid, basicEffect_instanced) declaran
  `layout(location=2) boneWeights`, `3 boneIndices`, `4 uv`; `ShaderUnit` también los fija con `glBindAttribLocation`
  cuando reescribe a `#version 120`. Con la malla `.txt`: `boneWeights` lee el valor genérico constante
  ((0,0,0,1) si nadie lo cambió) -> sólo pesa `.w`; `boneIndices` = uv (vec2 -> (u,v,0,1)) -> todo vértice usa
  `MatrixPalette[1]`; `uv` = pesos.xy -> la textura se muestrea en (w0,w1) y `texSample.w < 0.01` descarta. La
  sombra sale por otro camino. No es un error del puente: escritorio haría lo mismo con esa malla. El static
  (`basicEffect_static.vert`: uv en 2) tampoco coincide con el static `.txt` (Tangent en 2, uv en 3).
- Arreglo fiel propuesto (Java, no hecho en este frente): reemplazar `jassimp.Jassimp.aiImportFile` (nativa) por una
  implementación que lea un caché preconvertido de la salida de assimp ("Preconvertir en tu PC y leer el caché",
  ROADMAP) para que el código del juego (`ImportedSkinnedMesh`) arme su propio VBO. Alternativa descartada: remapear
  atributos en `gl.mjs` (inventaría un comportamiento; afectaría a cualquier otro programa).
- Descartado: presupuesto de uniforms. Vértice traducido: ModelViewProjection 4 + MatrixPalette[60] 240 + targetDepth,
  UVScale, HighResDepthMultiplier, FinalScale (DepthBias no se usa) = 248 vectores como máximo, < 256 (SwiftShader) y
  4096 (NVIDIA). El puente lee LINK_STATUS e info log tras cada link (`linkProgram`, `shaderTrail`), no falla en silencio.
- Descartado: transpuesta. `Shader.setMatrixPalette(FloatBuffer)` -> `setMatrixPalette(fb, true)` ->
  `glUniformMatrix4fv(matrixId, true, fb)` (count = remaining/16 en el wrapper LWJGL); el puente transpone una vez en CPU
  y sube con false: el shader ve lo mismo que en escritorio.
- Descartado: tipos de atributos. BlendIndex/BlendWeight se suben con `GL_FLOAT` tamaño 4 (`glVertexAttribPointer`, sin
  `IPointer`) y el shader los declara `vec4` (`int(boneIndices.x)`): coinciden.
- Descartado: defaults de uniforms. `applyUniformDefaults` corre justo después de un link exitoso, antes de que
  `ShaderProgram` avise a `Shader.onProgramCompiled`; antes del link no hay ubicaciones válidas, así que no pisa nada.
- Descartado: `Alpha`. `Shader.startCharacter` llama `setAlpha(ModelSlotRenderData.alpha)` -> `glUniform1f(alpha)` con
  la ubicación de "Alpha" (la que lee basicEffect.frag) en cada dibujo.
- Diagnóstico nuevo: `pzRuntime.gl.disabledAttribDraws` cuenta, por `"<programa>:<atributo>@<ubicación>"`, los dibujos
  en que un atributo activo del programa actual no tiene su array habilitado (sin cambiar nada). Con la hipótesis de
  arriba debe aparecer `<basicEffect>:boneWeights@2`. Test `tests/node/gl-disabled-attribs.test.mjs`.

## Ronda 80 — frente L (31/10): jassimp64 sobre caché preconvertido
- Nativas de `jassimp.Jassimp` (inventario): `aiImportFile(String,long,AiIOSystem,AiProgressHandler)`, `getErrorString`,
  y 8 tamaños (`getVKeysize`… que `loadLibrary()` guarda en `NATIVE_*`; en linux-x86_64: 24, 24, 12, 4, 4, 4, 8, 8). La
  carga la hace `JAssImpImporter$LibraryLoader` -> `System.loadLibrary("jassimp64")`.
- Llamadores (`importFile(String,Set)`): `FileTask_LoadMesh.loadX/loadFBX/loadGLTF`, `FileTask_LoadPhysicsShape`,
  `FileTask_LoadAnimation` (sólo MAKE_LEFT_HANDED + REMOVE_REDUNDANT_MATERIALS), `FileTask_LoadAiScene` (x2). Para `.x`
  los flags son fijos (sin `postProcess` del script); FBX/GLB suman `handlePostProcessFlags` y escalan 0,01/rotan -90°.
  `checkExtensionType` prueba `media/models_x/<minúsculas>.{fbx,glb,x}` con `File.exists` antes del `.txt`.
- La nativa arma: `AiScene()`; por malla `AiMesh()` privado + `m_name`, `m_materialIndex`, `setPrimitiveTypes(raw)`,
  `allocateBuffers(nv,nf,pura,bytesCaras)`, `allocateDataChannel(tipo,i)` (0 N, 1 T, 2 B, 3 color, 4/5/6 uv 1D/2D/3D) y
  copia en los ByteBuffer directos; `AiBone` (`m_name`, `m_offsetMatrix = Jassimp.wrapMatrix`) + `AiBoneWeight`;
  materiales `AiMaterial$Property` (String/Float/Integer/AiColor/ByteBuffer); `AiAnimation(nombre,dur,tps)` +
  `AiNodeAnim(nombre,nPos,nRot,nEsc,pre,post)` (aloca sus claves); nodos con `Jassimp.wrapSceneNode` (el ctor de AiNode
  se cuelga del padre). El juego sólo lee materiales/luces/cámaras para vaciarlos (`takeOutTheTrash`).
- Implementación: `tools/jassimp/JassimpCache.java` (corre la .so real, `--verify` = ida y vuelta por el lector del
  navegador, idéntica byte a byte), `tools/pack/stage_jassimp.py` (staging con ruta real + minúsculas), `port.natives.
  Jassimp64` / `JassimpCacheReader` / `JassimpObjects` (en la JVM por reflexión; en TeaVM
  `JassimpNativeCompatibility` reemplaza cada cuerpo por IR directo). Test `JassimpCacheTest` (escena sintética,
  rechazos, reescritura sin reflexión). Mallas: MaleBody v617 f916 27 huesos con peso (el `.txt` tenía los mismos 617
  vértices y 916 caras; sus 35 "huesos" son nodos de la jerarquía), FemaleBody v660 f984, Male_Skeleton v789,
  Female_Skeleton v792.
- Riesgo abierto: los clips siguen en `.txt` (x_anim_to_txt, calibrado contra `Bob_Sprint.txt` del juego); el esqueleto
  ahora viene del camino jassimp (MAKE_LEFT_HANDED). Si el personaje aparece pero deformado/girado, la siguiente pieza es
  servir también los clips por jassimp (perfil `x-anim` del dumper) en vez de `.txt`.

## Ronda 80 — frente M (31/10): sonda del jugador (W llega a JS pero el jugador no se traslada)
- Diagnóstico de sólo lectura, opt-in: `port.runtime.PlayerProbe.frame()` se inyecta antes de cada retorno de
  `zombie.gameStates.IngameState#update()` (`PlayerProbeCompatibility`, sólo `--with-game`). Apagada cuesta un contador
  y una llamada JS que devuelve un booleano cacheado; el flag (`globalThis.PZ_PLAYER_PROBE === true` o `?probe=1`) se
  relee como mucho una vez por segundo. Encendida publica `pzRuntime.playerProbe` en cada frame de juego.
- Nuestras fuentes no compilan contra el jar: los stubs `g*` de PlayerProbe (tipos del juego como `Object`) se reescriben
  con IR directa (getstatic/getfield/invokevirtual de getters públicos; descriptores de `javap -s` del jar fijado). El
  build falla si falta el miembro del juego o una reescritura; `PlayerProbeTest` compara stubs y reescrituras en JVM.
- Getters elegidos por no tener efectos (`javap -c`): `getDeferredMovement(Vector2)` = `(v,false)`, copia sin poner a
  cero el acumulador; `GameKeyboard.isKeyDown(KeybindId)` = `isKeyDown(getId())` -> binding de `Core`. Descartado
  `IsoPlayer.getInputMoveVector`: llama a `setAutoWalkDirection` (escribe estado).
- Campos: frames/samples, `IngameState.numberTicks/paused`, `GameTime.isGamePaused/getMultiplier/getTrueMultiplier`,
  `SpeedControls.getCurrentGameSpeed`, `UIManager.isShowPausedMessage`, `Core.isDoingTextEntry`; `KeybindId.FORWARD/RIGHT`
  (id, `Core.getKey`, `GameKeyboard.isKeyDown`), `GameKeyboard.isKeyDown(17/32)`, `org.lwjglx.input.Keyboard.isKeyDown(17/32)`;
  `IsoPlayer.players[0]`: x/y/z, forward, moveForwardVec, isMoving/isPlayerMoving/isWalking/isRunning/isSprinting/
  isAiming/autoWalk/blockMovement/performingAction/pathfindRunning/ghost/dead/asleep/falling/sitOnGround, moveSpeed,
  pathSpeed, currentState/actionState/actionContextState, hasAnimationPlayer, isAnimationUpdatingThisFrame,
  deferredX/Y/Len; AdvancedAnimator: estado y capas (root + sub0..7) con sus LiveAnimNode (nombre, peso, activo).
  Acumulados desde que se enciende: forwardFrames, movingFrames, animUpdatingFrames, deferredNonZeroFrames,
  translatedFrames, maxDist.
- Lectura esperada: tecla sin `forwardDown` -> binding/GameKeyboard; `forwardDown` sin `isPlayerMoving` -> entrada del
  jugador (estado/acción/UI/pausa); `isPlayerMoving` sin `deferredLen>0` -> root motion (clip/hueso Translation_Data);
  `deferredLen>0` con `animUpdatingThisFrame=false` -> `doDeferredMovement` no aplica (ver frente H).
- Presupuesto (simulado sobre una copia del índice): +4 cachés (317 291 B; ruta real y minúsculas comparten un rango) y
  -4 `models/skinned/*.txt` (1 015 617 B), inalcanzables con el `.x` presente (`checkExtensionType` devuelve X antes que
  Txt y `loadX` no reintenta con `.txt`). Packs 520 168 208 B (quedan 16,70 MB de 536 870 912), archivos montados
  516 786 274 B (quedan 20,08 MB). Volver atrás: `index.json.before-frontL`.

## Ronda 80 — frente O (31/10): cuelgue tras Esc -> QUIT
- Síntoma (/tmp/pz80v): guarda (worldgen params, GlobalModData), "STATE: exit zombie.gameStates.IngameState" es la última
  línea; el hilo principal del renderer queda al 100 %, evaluate/screenshot no responden y a los ~23 min "Target crashed".
- `GameStateMachine.update` imprime "STATE: exit X" y llama `X.exit()`: el cuelgue está dentro de `IngameState.exit()` (no
  hay "STATE: enter" del estado siguiente). `DebugType.ExitDebug` no está activo, por eso no se ven "IngameState.exit N".
- Orden de `IngameState.exit()` (javap): fundido `ScreenFader` (350 ms, `Thread.sleep(33)` por vuelta: cede), espera
  `WorldStreamer.isBusy()` con sleep y tope de 6 s (cede), luego **`WorldStreamer.stop()`**: `finished=true; while
  (worldStreamer.isAlive()) MainThread.busyWait();` y `busyWait` sólo vacía `invokeOnMainQueue`: nunca suspende. Con
  corrutinas TeaVM el hilo "World Streamer" está dormido en `Thread.sleep` dentro de `threadLoop` y sólo puede ver
  `finished` si la fibra del juego cede: espera infinita = 100 % CPU, ningún timer (autoFlush de la VFS, rAF) corre.
- Después vienen dos esperas iguales: `LightingThread.stop()` (`while (lightingThread.isAlive());` si
  `PerformanceSettings.lightingThread`) e `IsoRegionWorker.stop()` (desde `IsoRegions.reset()`, `while (thread.isAlive());`).
  Barrido de las 45 clases del jar que nombran `isAlive`: el resto de las esperas sobre `Thread.isAlive` duermen
  (`MapCollisionData.stop/save`, `PolygonalMap2.stop`, `PathfindNativeThread.stopThread`, `WorldGenerate`, `ZipBackup`...).
- Arreglo (necesita el próximo build dev): `port.compiler.SpinWaitCompatibility` reescribe el `Thread.isAlive()` de esos tres
  `stop()` a `JdkSupport.aliveYielding` (mismo valor + cesión de `onSpinWait`). Test: `TransformerPatchTest`
  (`spinWaitsOnThreadIsAliveYield` sobre el jar fijado, control `PolygonalMap2.stop` intacto, objetivo vacío falla).
- Nativas del resto de `exit()` revisadas: `LightingJNI.stop` -> `destroy` (implementada), `ZombiePopulationManager.n_stop`
  (PopMan64.stop), `MapCollisionData.n_stop` (MapCollision64.stop), `Bullet.destroyWorld` (BulletWorld), `PathfindNative.stop`
  sólo si la opción de debug `pathfindUseNativeCode` (si no, `PolygonalMap2.stop`, que duerme). `AnimalPopulationManager.n_stop`
  es Java, no nativa.
- Secundario (no cuelga, cuesta memoria): cada sentencia SQLite en autocommit llama `BrowserConnection.persist()` ->
  `Sqlite.export` + `vfs.flush()` completo, que copia (`slice`) todos los archivos de /Zomboid y reescribe IndexedDB entero;
  durante el guardado eso es O(tamaño de la partida) por sentencia. Candidato: `flushChanges` con los paths sucios.
