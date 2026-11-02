// Round 86 scenario for tools/diag/play-repl.mjs (copy to <out>/cmd.js; needs --admin). Both clients are already in the world on the TEST server.
// a = admin (god mode, bat, real mouse); b = second player. Checks: zombies are the same on both clients (ids/positions), they attack, and a kill
// (standing zombies only: a sitting/crawling one is out of reach of a swing) made with the real mouse (right button held = aim, left click = swing) disappears from b's list too. Prints "RESULT ..." lines.
const SAY = s => `say("${s}")`;
const LIST = `local list = getCell():getZombieList(); local ids = {}
for i = 0, list:size() - 1 do local z = list:get(i); ids[#ids + 1] = tostring(z:getOnlineID()) end
say("IDS " .. table.concat(ids, ","))`;
const TARGET = `local p = getPlayer(); local list = getCell():getZombieList(); local best, bz = 1e9
for i = 0, list:size() - 1 do local z = list:get(i); local d = math.abs(z:getX() - p:getX()) + math.abs(z:getY() - p:getY()); if d < best and not z:isDead() and not z:isOnFloor() then best = d; bz = z end end
if bz then say("TARGET " .. tostring(bz:getOnlineID()) .. " " .. string.format("%.1f", best) .. " " .. string.format("%.2f", bz:getHealth()) .. " " .. isoToScreenX(0, bz:getX(), bz:getY(), bz:getZ()) .. " " .. isoToScreenY(0, bz:getX(), bz:getY(), bz:getZ())) end`;
async function grab(c, code, re) {
  let got = null; const h = m => { const g = re.exec(m.text()); if (g) got = g; };
  c.page.on('console', h); await lua(c, code); c.page.off('console', h); return got;
}
const bu = await b.user();
await lua(a, `SendCommandToServer("/godmode -true")`);
await lua(a, `SendCommandToServer("/setaccesslevel \\"${bu}\\" admin")`);
await sleep(3000);
await lua(b, `SendCommandToServer("/godmode -true")`);
await lua(a, `SendCommandToServer("/additem \\"admin\\" \\"Base.BaseballBat\\" 1")`);
await sleep(3000);
await lua(a, `local p = getPlayer(); local it = p:getInventory():getFirstTypeRecurse("BaseballBat"); if it then p:setPrimaryHandItem(it); p:setSecondaryHandItem(it); say("equipped bat") else say("no bat") end`);
await lua(a, `SendCommandToServer("/createhorde 3")`);
await sleep(12000);
await lua(a, `PZW_REC = true`);
let killed = null;
for (let n = 0; n < 10 && !killed; n++) {
  const g = await grab(a, TARGET, /PZWEB-STATE:TARGET (\S+) (\S+) (\S+) (\S+) (\S+)/);
  if (!g) { log('a', 'no zombie'); break; }
  const t = { id: g[1], d: +g[2], hp: +g[3], x: +g[4], y: +g[5] };
  log('a', 'attempt', n, JSON.stringify(t));
  if (t.d > 2.2) {
    await lua(a, `local p = getPlayer(); local list = getCell():getZombieList(); local best, bz = 1e9
for i = 0, list:size() - 1 do local z = list:get(i); local d = math.abs(z:getX() - p:getX()) + math.abs(z:getY() - p:getY()); if d < best and not z:isDead() and not z:isOnFloor() then best = d; bz = z end end
if bz then ISTimedActionQueue.add(ISPathFindAction:pathToLocationF(p, bz:getX() - 1.3, bz:getY(), 0)) end`);
    await sleep(6000); continue;
  }
  await a.page.mouse.move(Math.max(5, Math.min(1275, t.x)), Math.max(5, Math.min(715, t.y - 25))); await sleep(400);
  await a.page.mouse.down({ button: 'right' }); await sleep(700);
  for (let k = 0; k < 4; k++) { await a.page.mouse.down(); await sleep(250); await a.page.mouse.up(); await sleep(1100); }
  await a.page.mouse.up({ button: 'right' }); await sleep(800);
  const after = await grab(a, LIST, /PZWEB-STATE:IDS (\S*)/);
  if (after && !after[1].split(',').includes(t.id)) killed = t.id;
}
await lua(a, `PZW_REC = false`);
await sleep(3000);
const ia = await grab(a, LIST, /PZWEB-STATE:IDS (\S*)/), ib = await grab(b, LIST, /PZWEB-STATE:IDS (\S*)/);
const A = new Set((ia?.[1] || '').split(',')), B = new Set((ib?.[1] || '').split(','));
log('both', `RESULT killed=${killed} inA=${A.has(killed)} inB=${B.has(killed)} zombiesA=${A.size} zombiesB=${B.size} common=${[...A].filter(x => B.has(x)).length}`);
await shot('r86-final');
