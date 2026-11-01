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
local infoTicks = 0
Events.OnPlayerUpdate.Add(function(player)
    if infoTicks < 240 then infoTicks = infoTicks + 1; if infoTicks % 20 == 0 then closeInfo() end end
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
            CFG.host or "127.0.0.1", "", tostring(CFG.port or 16261), "", false, true, 0)
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
