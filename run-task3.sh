#!/bin/bash
export PATH="$HOME/.local/bin:$PATH"
P=launch-gyro-m-128k-optimo
cd ~/Projects/pz-web
restart() { echo "Reiniciando Strata..."; strata runtime start --profile $P; echo STRATA_START_EXIT $?; strata runtime status --profile $P; }
trap restart EXIT
strata runtime stop --profile $P; echo STRATA_STOP_EXIT $?
sleep 5; free -g | head -2
/usr/lib/chatgpt/resources/codex exec -m gpt-6-astra -c model_reasoning_effort=max -s workspace-write -c sandbox_workspace_write.network_access=true -C ~/Projects/pz-web "Leé docs/CODEX_TASK_3.md y hacé lo que dice." > codex-run-3.log 2>&1
echo CODEX_EXIT $?
git add -A && git -c user.name=Paolozky -c user.email=paolozky@local commit -qm "Tarea 3: APIs JDK, build completo, avance F3" && echo COMMIT_OK
