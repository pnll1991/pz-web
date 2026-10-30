#!/usr/bin/env python3
"""Append the font resources the real game asks for, derived from PZ_HOME (never guessed).

Why (round 60, diag167.log + debuglog-round60.txt): regenerating build/real-browser/startup.txt with
tests/prepare_real.py + startup_extra.py dropped the font entries the earlier rounds had written by
hand, so GameWindow#initFonts -> AngelCodeFont::<init> threw FileNotFoundException, the MainThread
died (RuntimeException wrapping it) and main returned before GameWindow#enter was ever reached.

The names come from the game itself, never invented:
 - media/fonts/EN/fonts.txt: every `fnt = X,` / `img = X,` entry (the trailing comma is why the
   round-40 regex of a non-space run produced paths ending in a comma).
 - media/fonts/zomboidNormal<N>.fnt for N=11..25, which TextManager.Init asks for on its own.
 - every `page id=... file="..."` inside each resolved .fnt (the atlas PNGs).
Resolution order matches TextManager.getFontFilePath's constant-pool templates:
media/fonts/EN/1x/<file>, media/fonts/<file>, media/<file>. Files that do not exist in the pinned
copy (mainfont_0.png, mainfont2_0.png, zomboidDialogue.bmfc_0.png) stay unmounted: the game logs
"failed to load page 0 texture" and continues, so inventing them would fake success.
"""
import argparse
import os
import re
import sys
from pathlib import Path

NORMAL_FONTS = tuple(f'zomboidNormal{n}.fnt' for n in range(11, 26))
DIRS = ('fonts/EN/1x/', 'fonts/', '')
# Round 60: the listings themselves must be mounted too.  TextManager.Init calls
# FontsFile.read(media/fonts/EN/fonts.txt), which *catches* FileNotFoundException and returns
# false, so a missing listing does not raise: fontsFileFontMap stays empty, every UIFont keeps a
# null AngelCodeFont, and the first TextManager.getFontHeight() (ExceptionLogger.render from
# Core.EndFrameUI) traps with "dereferencing a null pointer" inside AngelCodeFont.getLineHeight.
# That is exactly what diag167.errs showed while UI_Loading_ModelsAnimations was running.
LISTINGS = ('fonts/{language}/fonts.txt', 'fonts/{language}/fontsDyslexic.txt')


def resolve(media: Path, name: str):
    for d in DIRS:
        cand = media / d / name
        if cand.is_file():
            return 'media/' + cand.relative_to(media).as_posix()
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pz-home', required=True)
    parser.add_argument('--files', required=True, type=Path, help='startup list to append to')
    parser.add_argument('--language', default='EN')
    args = parser.parse_args()
    home = Path(args.pz_home).expanduser().resolve()
    media = home / 'media'
    if not media.is_dir():
        raise SystemExit(f'PZ_HOME has no media directory: {home}')
    names = []
    listing = media / 'fonts' / args.language / 'fonts.txt'
    if listing.is_file():
        for match in re.finditer(r'(?:fnt|img)\s*=\s*([^,\s;]+)', listing.read_text()):
            names.append(match.group(1))
    for template in LISTINGS:
        rel = template.format(language=args.language)
        if (media / rel).is_file():
            names.append(rel)
    names.extend(NORMAL_FONTS)
    wanted = []
    seen = set()

    def add(name):
        path = resolve(media, name)
        if path is not None and path not in seen:
            seen.add(path)
            wanted.append(path)

    for name in names:
        add(name)
    for path in list(wanted):
        if not path.endswith('.fnt'):
            continue
        text = (home / path).read_text(errors='replace')
        base = Path(path).parent
        for match in re.finditer(r'page\s+id=\d+\s+file="([^"]+)"', text):
            page = match.group(1)
            cand = media / base / page
            if cand.is_file():
                rel = 'media/' + cand.relative_to(media).as_posix()
                if rel not in seen:
                    seen.add(rel)
                    wanted.append(rel)
            else:
                add(os.path.basename(page))
    existing = {line.strip() for line in args.files.read_text().splitlines() if line.strip()}
    added = [name for name in wanted if name not in existing]
    with args.files.open('a') as handle:
        for name in added:
            handle.write(name + '\n')
    size = sum((home / name).stat().st_size for name in added)
    print(f'Appended {len(added)} font entries ({size} B): fnt='
          f'{sum(1 for n in added if n.endswith(".fnt"))} png='
          f'{sum(1 for n in added if n.endswith(".png"))} (fonts.txt entries='
          f'{len(names)}, resolved={len(wanted)})')


if __name__ == '__main__':
    main()
