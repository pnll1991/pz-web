"""Generate only our own fixture files under ignored build/, never real game data."""
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
fixture = ROOT/'build/browser-fixture'
media = fixture/'game/media'
media.mkdir(parents=True,exist_ok=True)
(fixture/'game/ProjectZomboid64.json').write_text(json.dumps({'fixture':True,'classpath':['.','resources.jar'],'mainClass':'not-a-game'}))
(media/'probe.txt').write_text('Paquete sintético propio — áéí ñ\n')
(fixture/'startup.txt').write_text('media/probe.txt\n')
subprocess.run([sys.executable,str(ROOT/'tools/pack/pack.py'),'--pz-home',str(fixture/'game'),
                '--files',str(fixture/'startup.txt'),'--out',str(fixture/'packs')],check=True)

resource = fixture/'game/port/runtime/fixture-resource.txt'
resource.parent.mkdir(parents=True,exist_ok=True)
resource.write_text('Recurso de classpath propio — ñ\n')
import zipfile
with zipfile.ZipFile(fixture/'game/resources.jar','w',compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('port/runtime/fixture-resource.txt','Segundo recurso dentro del jar propio\n')
