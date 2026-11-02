// Round 88: movement-mechanics scenario for tools/diag/play-repl.mjs (test instance only). Cuerpo de cmd.js: ve { a, b, clients, sleep, lua, shot, log }.
//   node tools/diag/play-repl.mjs --url http://127.0.0.1:82xx/play.html --port 162x1 --admin --only 1 --out /tmp/x --profiles /tmp/x --console &
//   cp tools/diag/mobility-scenario.js /tmp/x/cmd.js            # corre una vez cuando el cliente está en el mundo
// Para cada mecánica: busca un objeto real cerca (valla, ventana, pared alta, soga), coloca al jugador a su lado, encola la MISMA acción
// temporizada que usa el menú contextual (ISClimbOverFence / ISClimbThroughWindow / ISClimbSheetRopeAction) y mira cada ~1 s el estado
// del motor. Una mecánica "funciona" si el jugador sale del estado de trepar y terminó al otro lado; si queda atascado en el estado, falla.
// Resultado: líneas "MOB-RESULT <mecánica> <ok|FAIL|no-target> ..." en out.txt, y las capturas <out>/a-mob-*.png.
const MECHANICS = (process.env.MOB_ONLY || '').split(',').filter(Boolean);
const got = [];
const onConsole = m => { const t = m.text(), i = t.indexOf('PZWEB-STATE:mob-'); if (i >= 0) got.push(t.slice(i + 'PZWEB-STATE:'.length)); };
a.page.on('console', onConsole);
const take = prefix => { const r = got.filter(s => s.startsWith(prefix)); for (const s of r) got.splice(got.indexOf(s), 1); return r; };

// Lua común: snapshot del estado del jugador, búsqueda de objetivos y colocación. Variables globales para que sobrevivan entre fragmentos.
await lua(a, `
function MOB_SNAP(tag)
  local p = getPlayer()
  local function t(f) local ok, v = pcall(f); return ok and tostring(v) or "ERR" end
  local st = t(function() return p:getCurrentStateName() end)
  local act = t(function() return p:getActionStateName() end)
  local clm = t(function() return p:isClimbing() end)
  local q = t(function() return ISTimedActionQueue.isPlayerDoingAction(p) end)
  say(string.format("mob-snap:%s x=%.2f y=%.2f z=%.2f", tag, p:getX(), p:getY(), p:getZ()))
  say("mob-snap2:" .. tag .. " state=" .. st .. " action=" .. act .. " climbing=" .. clm .. " queue=" .. q)
end
function MOB_SCAN(kind, z, radius)
  local p = getPlayer()
  local cx, cy = math.floor(p:getX()), math.floor(p:getY())
  local best, bd = nil, 1e9
  for dx = -radius, radius do
    for dy = -radius, radius do
      local sq = getCell():getGridSquare(cx + dx, cy + dy, z)
      if sq then
        local objs = sq:getObjects()
        for i = 0, objs:size() - 1 do
          local o = objs:get(i)
          local found = nil
          pcall(function()
            local pr = o:getProperties()
            if kind == "fence" and not o:isTallHoppable() and (pr:Is(IsoFlagType.HoppableN) or pr:Is(IsoFlagType.HoppableW)) and not instanceof(o, "IsoWindow") then
              found = pr:Is(IsoFlagType.HoppableN) and "N" or "W"
            elseif kind == "tall" and o:isTallHoppable() then
              found = o:isNorthHoppable() and "N" or "W"
            elseif kind == "window" and instanceof(o, "IsoWindow") then
              found = o:getNorth() and "N" or "W"
            end
          end)
          if found then
            local d = math.abs(dx) + math.abs(dy)
            if d < bd then bd = d; best = { sq = sq, o = o, dir = found } end
          end
        end
      end
    end
  end
  MOB_TARGET = MOB_TARGET or {}
  MOB_TARGET[kind] = best
  if best then say(string.format("mob-target:%s %d,%d,%d dir=%s dist=%d", kind, best.sq:getX(), best.sq:getY(), best.sq:getZ(), best.dir, bd)) else say("mob-target:" .. kind .. " none") end
end
function MOB_PLACE(kind, flip)
  local t = MOB_TARGET and MOB_TARGET[kind]
  if not t then say("mob-place:" .. kind .. " no-target"); return end
  local p = getPlayer()
  local x, y = t.sq:getX() + 0.5, t.sq:getY() + 0.5
  if t.dir == "N" then y = flip and (y - 1.0) or (y + 0.1) else x = flip and (x - 1.0) or (x + 0.1) end
  p:setX(x); p:setY(y); p:setLx(x); p:setLy(y); p:setZ(t.sq:getZ())
  say(string.format("mob-place:%s at %.2f,%.2f,%d", kind, x, y, t.sq:getZ()))
end
`);

async function sample(tag, seconds, until) {
  const out = [];
  const end = Date.now() + seconds * 1000;
  while (Date.now() < end) {
    await lua(a, `MOB_SNAP("${tag}")`, 8000);
    const s = take('mob-snap'); out.push(...s);
    const st = (s.find(x => x.startsWith('mob-snap2')) || '').match(/state=(\S+)/)?.[1];
    if (until && st && until(st)) break;
    await sleep(250);
  }
  return out;
}
const parse = s => { const m = /x=([\d.]+) y=([\d.]+) z=([\d.]+)/.exec(s); return m ? { x: +m[1], y: +m[2], z: +m[3] } : null; };
const STUCK = /Climb|Vault|Fall|Jump/i;
async function runOne(name, scan, place, act, expect) {
  if (MECHANICS.length && !MECHANICS.includes(name)) return;
  await lua(a, scan); const tg = take('mob-target')[0] || '';
  if (!tg || / none/.test(tg)) { log('both', `MOB-RESULT ${name} no-target (${tg})`); return; }
  await lua(a, place); await sleep(1500); take('mob-place');
  await sample('pre-' + name, 2);
  const pre = parse(take('mob-snap')[0] || '') || null;
  await lua(a, act);
  const seen = take('mob-act'); // lo que dijo la acción al encolarse
  const rows = await sample(name, 20, st => !STUCK.test(st) && st !== 'PlayerActionState');
  const snaps = rows.filter(r => r.startsWith('mob-snap:')), st2 = rows.filter(r => r.startsWith('mob-snap2:'));
  const last = parse(snaps[snaps.length - 1] || ''), first = parse(snaps[0] || '');
  const lastSt = (st2[st2.length - 1] || '').match(/state=(\S+)/)?.[1];
  const states = [...new Set(st2.map(r => r.match(/state=(\S+)/)?.[1]))].join('>');
  const moved = first && last ? Math.hypot(last.x - first.x, last.y - first.y) : 0;
  const ok = lastSt && !STUCK.test(lastSt) && (expect === 'move' ? moved >= 1.0 : expect === 'up' ? (last.z - (first?.z ?? 0)) >= 0.9 : (last.z - (first?.z ?? 0)) <= -0.9);
  await a.shot('mob-' + name).catch(() => {});
  log('both', `MOB-RESULT ${name} ${ok ? 'ok' : 'FAIL'} states=${states} last=${lastSt} moved=${moved.toFixed(2)} dz=${((last?.z ?? 0) - (first?.z ?? 0)).toFixed(2)} ${seen.join(' ')} samples=${snaps.length}`);
}

// 1) saltar una valla (ISClimbOverFence, la misma acción del menú "Saltar valla")
await runOne('fence', `MOB_SCAN("fence", 0, 25)`, `MOB_PLACE("fence", false)`, `
  local t = MOB_TARGET.fence; local p = getPlayer()
  local dir = (t.dir == "N") and IsoDirections.N or IsoDirections.W
  say("mob-act:fence-queued " .. tostring(t.o:getObjectIndex()) .. " dir=" .. t.dir)
  ISTimedActionQueue.add(ISClimbOverFence:new(p, t.o, dir))`, 'move');
// 2) trepar por una ventana (ISClimbThroughWindow); se abre primero si está cerrada
await runOne('window', `MOB_SCAN("window", 0, 40)`, `MOB_PLACE("window", false)`, `
  local t = MOB_TARGET.window; local p = getPlayer()
  local w = t.o
  pcall(function() if not w:IsOpen() and not w:isDestroyed() then w:setIsLocked(false); w:ToggleWindow(p) end end)
  local can = false
  pcall(function() can = w:canClimbThrough(p) end)
  say("mob-act:window-open=" .. tostring(w:IsOpen()) .. " destroyed=" .. tostring(w:isDestroyed()) .. " canClimbThrough=" .. tostring(can))
  ISTimedActionQueue.add(ISClimbThroughWindow:new(p, w, 20))`, 'move');
// 3) pared/valla alta (climbOverWall, con la misma acción ISClimbOverFence)
await runOne('wall', `MOB_SCAN("tall", 0, 40)`, `MOB_PLACE("tall", false)`, `
  local t = MOB_TARGET.tall; local p = getPlayer()
  local dir = (t.dir == "N") and IsoDirections.N or IsoDirections.W
  say("mob-act:wall-queued dir=" .. t.dir)
  ISTimedActionQueue.add(ISClimbOverFence:new(p, t.o, dir))`, 'move');

a.page.off('console', onConsole);
log('both', 'MOB-SCENARIO-DONE');
