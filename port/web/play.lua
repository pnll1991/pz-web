-- pz-web round 83: "direct mode". Own code (not game code), loaded last from /media/lua/client by the game's Lua loader.
-- A generated first line defines PZWEB_AUTOJOIN = { user, pass, host, port, name, acceptTos }. This script
--  * accepts the Terms of Service screen when acceptTos is set (the page URL says so),
--  * connects to the local dedicated server with the per-browser account, using the game's own ConnectToServer flow,
--  * presses NEXT on the spawn/character screens (the default random character), and
--  * closes the survival guide when the world starts.
-- Progress is reported as "PZWEB-STATE:<name>" lines on the console (read by port/runtime/autojoin.mjs).
local CFG = PZWEB_AUTOJOIN
if not CFG then return end

local function say(s) print("PZWEB-STATE:" .. s) end

-- Terms of Service: the game raises OnGameStateEnter for its Java state and its Lua UI creates the modal.
if CFG.acceptTos then
    Events.OnGameStateEnter.Add(function(state)
        local ui = ISTermsOfServiceUI and ISTermsOfServiceUI.instance
        if ui and ui:isVisible() then
            say("tos-accepted")
            ui:onButtonAccept(nil)
        end
    end)
end

-- The character info window opens by itself for a new character; close it once the player exists.
local function closeInfo()
    pcall(function()
        local info = getPlayerInfoPanel and getPlayerInfoPanel(0)
        if info and info:isVisible() then info:setVisible(false) end
    end)
end
-- Diagnostic: ?cmd=/createhorde 10;/removezombies sends those chat commands once the world is running (the server only obeys an admin).
local cmdList, cmdTicks, diagTicks = {}, 0, 0
for c in string.gmatch(CFG.cmd or "", "[^;]+") do table.insert(cmdList, c) end
local infoTicks = 0
Events.OnPlayerUpdate.Add(function(player)
    if infoTicks < 240 then infoTicks = infoTicks + 1; if infoTicks % 20 == 0 then closeInfo() end end
    -- Diagnostic (only with ?cmd=): what zombies does this client know about, and where are they relative to the local player?
    if (CFG.cmd or "") ~= "" then
        diagTicks = (diagTicks or 0) + 1
        if diagTicks % 300 == 0 then
            pcall(function()
                local list = getCell():getZombieList()
                local n, best, info, bestZ = list:size(), 1e9, "", nil
                for i = 0, n - 1 do
                    local z = list:get(i)
                    local d = math.abs(z:getX() - player:getX()) + math.abs(z:getY() - player:getY())
                    if d < best then best = d; bestZ = z; info = string.format("(%.0f,%.0f,%.0f) remote=%s hp=%s target=%s state=%s", z:getX(), z:getY(), z:getZ(), tostring(z:isRemoteZombie()), tostring(z:getHealth()), tostring(z:getTarget() ~= nil), tostring(z:getCurrentStateName())) end
                end
                local function t0(f) local ok, v = pcall(f); return ok and tostring(v) or "ERR" end
                say(string.format("zombies:%d nearest=%s me=(%.2f,%.2f) health=%s bitten=%s scratched=%s", n, info, player:getX(), player:getY(),
                    t0(function() return player:getBodyDamage():getHealth() end), t0(function() return player:getBodyDamage():getNumPartsBitten() end),
                    t0(function() return player:getBodyDamage():getNumPartsScratched() end)))
                -- render/visibility facts of the nearest zombie, with the local player as the control
                local function t(f) local ok, v = pcall(f); return ok and tostring(v) or "ERR" end
                local function facts(c)
                    local sq = c:getCurrentSquare()
                    return string.format("model=%s culled=%s onscreen=%s invisible=%s alpha=%s target=%s sqCould=%s sqCan=%s sqSeen=%s",
                        t(function() return c:hasActiveModel() end), t(function() return c:isSceneCulled() end), t(function() return c:isOnScreen() end),
                        t(function() return c:isInvisible() end), t(function() return c:getAlpha(0) end), t(function() return c:getTargetAlpha(0) end),
                        t(function() return sq:isCouldSee(0) end), t(function() return sq:isCanSee(0) end), t(function() return sq:isSeen(0) end))
                end
                local function look(c)
                    return string.format("female=%s skeleton=%s skinIdx=%s skinTex=%s outfit=%s itemVisuals=%s animPlayer=%s ready=%s action=%s onFloor=%s",
                        t(function() return c:isFemale() end), t(function() return c:getVisual():isSkeleton() end), t(function() return c:getVisual():getSkinTextureIndex() end),
                        t(function() return c:getVisual():getSkinTexture() end), t(function() return c:getOutfitName() end), t(function() return c:getItemVisuals():size() end),
                        t(function() return c:hasAnimationPlayer() end), t(function() return c:getAnimationPlayer():isReady() end), t(function() return c:getActionStateName() end),
                        t(function() return c:isOnFloor() end))
                end
                if bestZ then
                    say("zlook:zombie " .. look(bestZ)); say("zlook:player " .. look(player))
                    say("zfacts:zombie " .. facts(bestZ)); say("zfacts:player " .. facts(player))
                    say("zpath:" .. t(function() local b = bestZ:getPathFindBehavior2(); return "goal=" .. tostring(b:getTargetX()) .. "," .. tostring(b:getTargetY()) .. " isMoving=" .. tostring(bestZ:isMoving()) end))
                end
            end)
        end
    end
    if #cmdList > 0 then
        cmdTicks = cmdTicks + 1
        if cmdTicks == 900 or (cmdTicks > 900 and (cmdTicks - 900) % 300 == 0) then
            local c = table.remove(cmdList, 1)
            say("cmd:" .. c)
            if c == "@path" then -- pathfinding probe: walk the local player 8 tiles east with the game's own path action (the next "me=" lines show whether it moved)
                pcall(function() ISTimedActionQueue.add(ISPathFindAction:pathToLocationF(player, player:getX() + 8, player:getY(), player:getZ())) end)
            else
                pcall(function() SendCommandToServer(c) end)
            end
        end
    end
end)

Events.OnGameStart.Add(function()
    say("ingame")
    local guide = SurvivalGuide and SurvivalGuide.instance
    if guide and guide:isVisible() then
        guide:setVisible(false)
        if guide.removeFromUIManager then guide:removeFromUIManager() end
        setGameSpeed(1)
    end
end)

-- After the login the game reloads its Lua (this file runs again): a client is already connected, so only the creation screens remain.
local S = { t = 0, phase = isClient() and "connecting" or "wait", since = 0, tries = 0, mainSeen = 0 }

local function visible(ui) return ui and ui.isVisible and ui:isVisible() end

local function connect()
    S.tries = S.tries + 1
    S.phase = "connecting"; S.since = S.t
    say("connect-" .. S.tries)
    local ok, err = pcall(function()
        getCore():setNoSave(false)
        ConnectToServer.instance:connect(MainScreen.instance.multiplayer, CFG.name or "pzweb", CFG.user, CFG.pass,
            CFG.host or "127.0.0.1", "", tostring(CFG.port or 16261), "", false, true, CFG.authType or 0)
    end)
    if not ok then say("connect-error:" .. tostring(err)); S.phase = "retry"; S.since = S.t end
end

local function onTick()
    S.t = S.t + 1
    local main = MainScreen and MainScreen.instance
    if S.phase == "wait" then
        if main and main.multiplayer and ConnectToServer and ConnectToServer.instance and MapSpawnSelect and MapSpawnSelect.instance
                and not visible(ISTermsOfServiceUI and ISTermsOfServiceUI.instance) then
            S.mainSeen = S.mainSeen + 1
            if S.mainSeen == 1 then say("menu") end
            if S.mainSeen > 30 then connect() end
        end
    elseif S.phase == "connecting" then
        local c = ConnectToServer.instance
        if visible(MapSpawnSelect.instance) then
            say("spawn-select")
            MapSpawnSelect.instance:onOptionMouseDown({ internal = "NEXT" }, 0, 0)
        elseif visible(main.charCreationProfession) then
            say("profession")
            main.charCreationProfession:onOptionMouseDown({ internal = "NEXT" }, 0, 0)
        elseif visible(main.charCreationMain) then
            S.created = (S.created or 0) + 1
            if S.created == 40 then -- let the screen build its random character first
                say("character")
                main.charCreationMain:onOptionMouseDown({ internal = "NEXT" }, 0, 0)
                S.phase = "loading"
            end
        elseif c and c.failMessage then
            say("connect-failed:" .. tostring(c.failMessage))
            S.phase = "retry"; S.since = S.t
        elseif S.t - S.since > 60 * 60 * 3 then
            S.phase = "retry"; S.since = S.t
        end
    elseif S.phase == "retry" then
        if S.t - S.since > 60 * 5 and S.tries < 8 then connect() end
    end
end

Events.OnFETick.Add(onTick)
say("script-loaded")
