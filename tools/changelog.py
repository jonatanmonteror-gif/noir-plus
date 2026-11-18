#!/usr/bin/env python3
"""
Novedades por versión (una lista para Android y otra para Windows).

Uso: changelog.py <android|windows> <versión> <url del changelog anterior> <salida>
Lee changelog/<plataforma>.txt (líneas «- punto»), descarga el historial anterior y, si los puntos son
nuevos, añade la versión al principio. Si no hay puntos nuevos, el historial queda igual.
"""
import json, sys, time, urllib.request

plat, version, prev_url, out = sys.argv[1:5]
items = []
with open(f"changelog/{plat}.txt", encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if line.startswith("- "):
            items.append(line[2:].strip())
hist = []
try:
    with urllib.request.urlopen(prev_url, timeout=20) as r:
        hist = json.load(r)
        if not isinstance(hist, list):
            hist = []
except Exception:
    hist = []
already = any(h.get("items") == items for h in hist[:3])
if items and not already:
    hist.insert(0, {"v": version, "date": time.strftime("%Y-%m-%d"), "items": items})
hist = hist[:60]
with open(out, "w", encoding="utf-8") as f:
    json.dump(hist, f, ensure_ascii=False, indent=1)
print(f"{out}: {len(hist)} versiones; {'añadida ' + version if items and not already else 'sin novedades nuevas'}")
