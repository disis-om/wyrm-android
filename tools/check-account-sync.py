#!/usr/bin/env python3
"""Every preference file the app writes must be listed in AccountSync.FILES.

OM, 2026-10-01: settings live in the player's account (data/AccountSync.kt).
A new SharedPreferences file that is not listed would neither follow the
account nor be wiped on log out. Run before every Android build:

    python "Wyrm Android/tools/check-account-sync.py"
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1] / "android" / "app" / "src" / "main" / "java"
REGISTRY = ROOT / "com" / "wyrm" / "omrajput" / "data" / "AccountSync.kt"

listed = set(re.findall(r'PrefFile\("([a-z0-9_]+)"', REGISTRY.read_text(encoding="utf-8")))
used = {}
for path in list(ROOT.rglob("*.kt")) + list(ROOT.rglob("*.java")):
    text = path.read_text(encoding="utf-8")
    names = set(re.findall(r'getSharedPreferences\(\s*"([a-z0-9_]+)"', text))
    names |= set(re.findall(r'(?:PREFS\w*|PREF_\w+)\s*=\s*"((?:wyrm|vlither)_[a-z0-9_]+)"', text))
    names |= set(re.findall(r'String PREFS\w*\s*=\s*"([a-z0-9_]+)"', text))
    for name in names:
        used.setdefault(name, set()).add(path.name)

missing = {name: files for name, files in used.items() if name not in listed}
if missing:
    print("Preference files missing from AccountSync.FILES (sync, wipe or keep?):")
    for name, files in sorted(missing.items()):
        print(f"  {name}  ({', '.join(sorted(files))})")
    sys.exit(1)
print(f"account sync registry ok: {len(used)} preference files, all listed")
