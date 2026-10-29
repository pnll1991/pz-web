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
