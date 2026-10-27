"""Local metadata and explicitly selected resource pack; no game source output."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools/inventory'))
from inventory import resolve_game_home

home = resolve_game_home(os.environ['PZ_HOME'])
out = ROOT / 'build/real-browser'
out.mkdir(parents=True, exist_ok=True)
text = home / 'media/WorkshopTags.txt'
texture = home / 'media/ui/Title.png'
icon = home / 'media/ui/zomboidIcon32.png'
with Image.open(texture) as image:
    rgba = image.convert('RGBA')
    png = {'url':'/media/ui/Title.png', 'width':rgba.width, 'height':rgba.height,
           'rgba_sha256':hashlib.sha256(rgba.tobytes()).hexdigest()}
with Image.open(icon) as image:
    rgba_icon = image.convert('RGBA')
    icon_metadata = {'url':'/media/ui/zomboidIcon32.png', 'width':rgba_icon.width,
                     'height':rgba_icon.height,
                     'rgba_sha256':hashlib.sha256(rgba_icon.tobytes()).hexdigest()}
metadata = {'text':{'url':'/media/WorkshopTags.txt', 'size':text.stat().st_size,
                    'sha256':hashlib.sha256(text.read_bytes()).hexdigest()}, 'png':png,
            'icon':icon_metadata}
(out / 'assets.json').write_text(json.dumps(metadata, indent=2) + '\n')
(out / 'startup.txt').write_text('media/WorkshopTags.txt\nmedia/ui/zomboidIcon32.png\n')
subprocess.run([sys.executable, str(ROOT / 'tools/pack/pack.py'), '--pz-home', str(home),
                '--files', str(out / 'startup.txt'), '--out', str(out / 'packs')], check=True)
