#!/usr/bin/env python3
"""Single source of truth for the browser VFS memory budget.

Must stay in sync with the default maxBytes in port/runtime/vfs.mjs (round 86 raised it from 512 to 640 MiB (608 MB of packs) for the
player combat clips; round 52 raised it from
256 MB to 512 MB because Core.getOptionTexture2x() is hardcoded true in the pinned jar, so
GameWindow.enter() always mounts media/texturepacks/Tiles2x.pack, 321 MB on its own).
Mounted files are views over the immutable blobs, so this budget counts mounted file bytes;
blob bytes on disk are separate.
"""
VFS_MAX_BYTES = 640 * 1024 * 1024
