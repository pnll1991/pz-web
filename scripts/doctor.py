#!/usr/bin/env python3
"""Read-only readiness check; saves its report only inside ignored .toolchain/."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
TC = ROOT/'.toolchain'
commands = {
    'jdk':['java','--version'], 'javac':['javac','--version'], 'maven':['mvn','--version'],
    'node':['node','--version'], 'npm':['npm','--version'], 'chrome':[os.environ.get('CHROME_PATH','google-chrome'),'--version'],
    'cmake':['cmake','--version'], 'ninja':['ninja','--version'], 'apitrace':['apitrace','version'],
    'renderdoc':['renderdoccmd','version'],
    'emscripten':[str(TC/'emsdk/upstream/emscripten/emcc'),'--version'],
    'binutils':['nm','--version'], 'readelf':['readelf','--version'], 'file':['file','--version'],
    'gcc':['gcc','--version'], 'python':[sys.executable,'--version'], 'git':['git','--version']}
results = {}
for name,command in commands.items():
    try:
        p = subprocess.run(command,cwd=ROOT,capture_output=True,text=True,timeout=30)
        results[name] = {'ok':p.returncode==0,'version':(p.stdout or p.stderr).splitlines()[0]}
    except (OSError,subprocess.TimeoutExpired) as error: results[name] = {'ok':False,'error':str(error)}
for name,file in {'ghidra':TC/'ghidra/Ghidra/application.properties','vineflower':TC/'vineflower.jar'}.items():
    results[name] = {'ok':file.is_file(),'path':str(file),'runtime_check':'scripts/check-toolchains.sh'}
required_versions = {'jdk':'25.', 'javac':'25.', 'node':'v24.'}
for name,expected in required_versions.items():
    if expected not in results[name].get('version',''): results[name]['ok'] = False
report = {'platform':sys.platform,'root':str(ROOT),'tools':results,'ok':all(t['ok'] for t in results.values())}
TC.mkdir(exist_ok=True)
(TC/'doctor.json').write_text(json.dumps(report,indent=2)+'\n')
for name,result in results.items(): print(f"{'OK' if result['ok'] else 'MISSING'} {name}: {result.get('version',result.get('error',result.get('path')))}")
sys.exit(0 if report['ok'] else 1)
