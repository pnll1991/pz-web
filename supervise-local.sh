#!/usr/bin/env bash
# Vigilante local: relanza Codex+OpenCodex con Gyro-M (Strata :8083) por rondas hasta MENU_REACHED.
cd /home/agente47/Projects/pz-web
export PATH="$HOME/.local/share/pi-node/node-v22.23.4-linux-x64/bin:$HOME/.local/bin:$PATH"
LOG=supervisor.log
round=7; fails=0
say(){ echo "$(date '+%F %T') $*" >> "$LOG"; }
commit(){ git add -A && git -c user.name=Paolozky -c user.email=paolozky@local commit -q -m "$1" && say "commit: $(git log --oneline -1)"; }
up(){ python3 -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8083/v1/models',timeout=5)" 2>/dev/null; }
say "vigilante local (Gyro-M/Codex+OpenCodex) iniciado"
while true; do
  if [ -f MENU_REACHED ]; then say "MENU_REACHED encontrado, fin"; exit 0; fi
  if ! up; then say "Strata no responde en 8083, espero"; sleep 300; up || { say "Strata sigue caído, pauso"; exit 1; }; fi
  if [ "$fails" -ge 3 ]; then say "3 rondas seguidas fallaron rápido, pauso"; exit 1; fi
  if [ "$round" -gt 200 ]; then say "200 rondas alcanzadas, pauso"; exit 0; fi
  say "lanzando ronda $round (local)"
  start=$(date +%s)
  timeout 4h /usr/lib/chatgpt/resources/codex exec -m strata/qwen3.8-flash-next-gyro-gyro-m -s workspace-write -c sandbox_workspace_write.network_access=true -C /home/agente47/Projects/pz-web "Leé docs/CODEX_TASK_CONTINUE.md y hacé lo que dice. Esta es la ronda $round." > "local-run-$round.log" 2>&1 < /dev/null
  rc=$?; dur=$(( $(date +%s) - start ))
  say "ronda $round terminó rc=$rc en ${dur}s"
  commit "Ronda $round local Gyro-M (automático)" || say "sin cambios para commitear"
  if [ "$dur" -lt 60 ]; then fails=$((fails+1)); sleep 300; else fails=0; fi
  round=$((round+1))
done
