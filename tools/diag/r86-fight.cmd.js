// Round 86 scenario for tools/diag/play-repl.mjs (copy to <out>/cmd.js; needs --admin). Both clients are already in the world on the TEST server.
// a = admin (god mode, real mouse); b = second player (admin too, god mode, so it survives and is only an observer).
// For every weapon in WEAPONS: equip it on a, pick a STANDING zombie within reach (a sitting/crawling one is out of reach of a swing)
// that b ALSO has in its list, kill it with the real mouse (right button held = aim, left click = swing) and check, from b's side,
// that the same zombie id is gone, that b got OnZombieDead for it and that a corpse lies on its square.
// Prints "RESULT ..." lines (one per weapon) and "SUMMARY".  Edit WEAPONS to test others (Base.<Type>).
const WEAPONS = ['Base.BaseballBat', 'Base.Axe', 'Base.Crowbar', 'Base.Machete'];
const MAX_TRIES = 6;
const LIST = `local list = getCell():getZombieList(); local ids = {}
for i = 0, list:size() - 1 do local z = list:get(i); if not z:isDead() then ids[#ids + 1] = tostring(z:getOnlineID()) end end
say("IDS " .. table.concat(ids, ","))`;
const TARGET = `local p = getPlayer(); local list = getCell():getZombieList(); local best, bz = 1e9
for i = 0, list:size() - 1 do local z = list:get(i); local d = math.abs(z:getX() - p:getX()) + math.abs(z:getY() - p:getY()); if d < best and not z:isDead() and not z:isOnFloor() then best = d; bz = z end end
if bz then say("TARGET " .. tostring(bz:getOnlineID()) .. " " .. string.format("%.1f", best) .. " " .. string.format("%.2f", bz:getHealth()) .. " " .. isoToScreenX(0, bz:getX(), bz:getY(), bz:getZ()) .. " " .. isoToScreenY(0, bz:getX(), bz:getY(), bz:getZ()))
  say("TPOS " .. string.format("%.2f %.2f", bz:getX(), bz:getY())) end`;
const CORPSE = (x, y) => `local n = 0
for dx = -1, 1 do for dy = -1, 1 do local sq = getCell():getGridSquare(math.floor(${x}) + dx, math.floor(${y}) + dy, 0); if sq and sq:getDeadBody() then n = n + 1 end end end
say("CORPSES " .. n)`;
async function grab(c, code, re) {
  let got = null; const h = m => { const g = re.exec(m.text()); if (g) got = g; };
  c.page.on('console', h); await lua(c, code); c.page.off('console', h); return got;
}
const ids = async c => { const g = await grab(c, LIST, /PZWEB-STATE:IDS (\S*)/); return g ? new Set(g[1].split(',').filter(Boolean)) : null; };
const deadEvents = { a: new Set(), b: new Set() };
for (const [n, c] of [['a', a], ['b', b]]) c.page.on('console', m => { const g = /PZWEB-STATE:evt:zombiedead id=(\S+)/.exec(m.text()); if (g) deadEvents[n].add(g[1]); });

const bu = await b.user();
await lua(a, `SendCommandToServer("/godmode -true")`);
await lua(a, `SendCommandToServer("/setaccesslevel \\"${bu}\\" admin")`);
await sleep(3000);
await lua(b, `SendCommandToServer("/godmode -true")`);
await lua(a, `PZW_REC = true`); await lua(b, `PZW_REC = true`);
await lua(a, `SendCommandToServer("/createhorde 12")`);
await sleep(12000);

const results = [];
for (const W of WEAPONS) {
  const type = W.replace(/^Base\./, ''), r = { weapon: W, equipped: false, swung: false, killed: null, inBbefore: false, goneFromB: false, bDeadEvent: false, bCorpse: -1, aDeadEvent: false };
  await lua(a, `SendCommandToServer("/additem \\"admin\\" \\"${W}\\" 1")`);
  await sleep(3000);
  const eq = await grab(a, `local p = getPlayer(); local it = p:getInventory():getFirstTypeRecurse("${type}"); if it then p:setPrimaryHandItem(it); p:setSecondaryHandItem(it); say("EQUIPPED " .. it:getType() .. " twoHand=" .. tostring(it:isTwoHandWeapon())) else say("EQUIPPED none") end`, /PZWEB-STATE:EQUIPPED (.*)/);
  log('a', W, 'equip:', eq?.[1]); r.equipped = !!eq && !/none/.test(eq[1]);
  if (!r.equipped) { results.push(r); continue; }
  for (let n = 0; n < MAX_TRIES && !r.killed; n++) {
    const g = await grab(a, TARGET, /PZWEB-STATE:TARGET (\S+) (\S+) (\S+) (\S+) (\S+)/);
    if (!g) { log('a', 'no standing zombie, waiting'); await sleep(8000); continue; }
    const t = { id: g[1], d: +g[2], hp: +g[3], x: +g[4], y: +g[5] };
    const ib = await ids(b);
    log('a', W, 'attempt', n, JSON.stringify(t), 'inB=' + (ib ? ib.has(t.id) : '?'));
    if (t.d > 2.2) {
      await lua(a, `local p = getPlayer(); local list = getCell():getZombieList(); local best, bz = 1e9
for i = 0, list:size() - 1 do local z = list:get(i); local d = math.abs(z:getX() - p:getX()) + math.abs(z:getY() - p:getY()); if d < best and not z:isDead() and not z:isOnFloor() then best = d; bz = z end end
if bz then ISTimedActionQueue.add(ISPathFindAction:pathToLocationF(p, bz:getX() - 1.3, bz:getY(), 0)) end`);
      await sleep(6000); continue;
    }
    const pos = await grab(a, TARGET, /PZWEB-STATE:TPOS (\S+) (\S+)/);
    r.inBbefore = !!ib && ib.has(t.id);
    await a.page.mouse.move(Math.max(5, Math.min(1275, t.x)), Math.max(5, Math.min(715, t.y - 25))); await sleep(400);
    await a.page.mouse.down({ button: 'right' }); await sleep(700);
    for (let k = 0; k < 4; k++) { await a.page.mouse.down(); await sleep(250); await a.page.mouse.up(); await sleep(1100); }
    await a.page.mouse.up({ button: 'right' }); await sleep(800);
    r.swung = true;
    const after = await ids(a);
    if (after && !after.has(t.id)) { r.killed = t.id; r.pos = pos ? [pos[1], pos[2]] : null; }
  }
  if (r.killed) {
    await sleep(4000);
    const ia = await ids(a), ib2 = await ids(b);
    r.goneFromB = !!ib2 && !ib2.has(r.killed);
    r.aDeadEvent = deadEvents.a.has(r.killed); r.bDeadEvent = deadEvents.b.has(r.killed);
    if (r.pos) { const c = await grab(b, CORPSE(r.pos[0], r.pos[1]), /PZWEB-STATE:CORPSES (\d+)/); r.bCorpse = c ? +c[1] : -1; }
    await shot(`r86-${type}`);
  }
  log('both', `RESULT weapon=${r.weapon} equipped=${r.equipped} swung=${r.swung} killed=${r.killed} inBbefore=${r.inBbefore} goneFromB=${r.goneFromB} aDeadEvent=${r.aDeadEvent} bDeadEvent=${r.bDeadEvent} bCorpseSquares=${r.bCorpse}`);
  results.push(r);
}
await lua(a, `PZW_REC = false`); await lua(b, `PZW_REC = false`);
log('both', `SUMMARY ${results.filter(r => r.killed && r.goneFromB && r.bDeadEvent).length}/${results.length} weapons: kill seen by the 2nd client`);
