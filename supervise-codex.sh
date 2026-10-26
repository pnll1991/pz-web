#!/usr/bin/env bash
# Vigilante: relanza Codex por rondas hasta que exista MENU_REACHED o se acabe el límite.
cd /home/agente47/Projects/pz-web
CODEX=/usr/lib/chatgpt/resources/codex
LOG=supervisor.log
MAX_USED=95
round=5; fails=0
say(){ echo "$(date '+%F %T') $*" >> "$LOG"; }
usage(){ f=$(ls -t ~/.codex/sessions/*/*/*/*.jsonl 2>/dev/null | head -1); grep -o '"used_percent":[0-9.]*' "$f" | tail -1 | cut -d: -f2 | cut -d. -f1; }
commit(){ git add -A && git -c user.name=Paolozky -c user.email=paolozky@local commit -q -m "$1" && say "commit: $(git log --oneline -1)"; }
say "vigilante iniciado"
while true; do
  while pgrep -f 'codex exec' >/dev/null; do sleep 60; done
  commit "Ronda $((round-1)) de Codex (automático)" || say "sin cambios para commitear"
  if [ -f MENU_REACHED ]; then say "MENU_REACHED encontrado, fin"; exit 0; fi
  u=$(usage); say "uso semanal Codex: ${u:-?}%"
  if [ -n "$u" ] && [ "$u" -ge "$MAX_USED" ]; then say "límite de uso alcanzado, pauso"; exit 0; fi
  if [ "$fails" -ge 3 ]; then say "3 rondas seguidas fallaron rápido, pauso"; exit 1; fi
  if [ "$round" -gt 30 ]; then say "30 rondas alcanzadas, pauso"; exit 0; fi
  say "lanzando ronda $round"
  start=$(date +%s)
  "$CODEX" exec -m gpt-6-astra -c model_reasoning_effort=max -s workspace-write -c sandbox_workspace_write.network_access=true -C /home/agente47/Projects/pz-web "Leé docs/CODEX_TASK_CONTINUE.md y hacé lo que dice. Esta es la ronda $round." > "codex-run-$round.log" 2>&1 < /dev/null
  rc=$?; dur=$(( $(date +%s) - start ))
  say "ronda $round terminó rc=$rc en ${dur}s"
  if [ "$dur" -lt 300 ]; then fails=$((fails+1)); sleep 600; else fails=0; fi
  round=$((round+1))
done
