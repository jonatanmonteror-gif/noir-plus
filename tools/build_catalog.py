#!/usr/bin/env python3
"""
Genera el catálogo de plataformas en España (Netflix, Disney+, Max…) a partir de TMDB,
para que la app lo descargue de una vez (un archivo pequeño) en lugar de hacer miles de consultas.

Salida: un archivo pequeño por plataforma y tipo: cat-movie-netflix.tsv.gz, cat-tv-disney.tsv.gz…
Formato (una línea por título, separado por tabuladores, ligero de leer en la tele):
    id <TAB> aaaammdd <TAB> géneros separados por comas <TAB> títulos normalizados separados por |
"""
import gzip, json, os, re, sys, time, unicodedata, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor

KEY = os.environ.get("TMDB_KEY", "").strip()
if not KEY:
    sys.exit("Falta TMDB_KEY")

MAIN = ["Netflix", "Disney+", "Max", "Prime Video", "Apple TV+", "Movistar Plus+", "SkyShowtime", "Filmin"]
RULES = [
    (r"\bnetflix\b", "Netflix"), (r"\bdisney", "Disney+"), (r"\bhbo\b|\bmax\b", "Max"),
    (r"\bprime\b|\bamazon\b", "Prime Video"), (r"\bapple\b", "Apple TV+"), (r"\bmovistar", "Movistar Plus+"),
    (r"\bskyshowtime\b|\bsky\b", "SkyShowtime"), (r"\bparamount", "Paramount+"), (r"\bfilmin\b", "Filmin"),
    (r"\batres", "Atresplayer"), (r"\brtve\b", "RTVE Play"), (r"\bcrunchyroll\b", "Crunchyroll"), (r"\bdazn\b", "DAZN"),
]

def strip_marks(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")

def group(name):
    n = strip_marks(name).lower()
    for rx, g in RULES:
        if re.search(rx, n):
            return g
    return name

def title_key(s):
    return re.sub(r"[^a-z0-9]+", " ", strip_marks(s).lower()).strip()

def get(path, params):
    q = dict(params, api_key=KEY, language="es-ES")
    url = "https://api.themoviedb.org/3" + path + "?" + urllib.parse.urlencode(q)
    for attempt in range(5):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return json.load(r)
        except Exception as e:  # reintento con espera creciente (límite de peticiones, cortes)
            time.sleep(1.5 * (attempt + 1))
    return None

LOGOS = {}

def build(kind):
    typ = "tv" if kind == "tv" else "movie"
    provs = get(f"/watch/providers/{typ}", {"watch_region": "ES"}) or {}
    ids = {}
    ordered = sorted(provs.get("results", []), key=lambda p: (p.get("display_priorities") or {}).get("ES", p.get("display_priority", 999)))
    for p in ordered:
        g = group(p.get("provider_name", ""))
        if g in MAIN:
            ids.setdefault(g, []).append(str(p["provider_id"]))
            if g not in LOGOS and p.get("logo_path"):
                LOGOS[g] = "https://image.tmdb.org/t/p/w92" + p["logo_path"]
    out = {}
    for g in MAIN:
        if g not in ids:
            continue
        base = {"watch_region": "ES", "with_watch_providers": "|".join(ids[g]),
                "with_watch_monetization_types": "flatrate|free|ads", "sort_by": "popularity.desc"}
        first = get(f"/discover/{typ}", dict(base, page=1))
        if not first:
            continue
        pages = max(1, min(500, first.get("total_pages", 1)))
        results = list(first.get("results", []))
        with ThreadPoolExecutor(8) as ex:
            for r in ex.map(lambda pg: get(f"/discover/{typ}", dict(base, page=pg)), range(2, pages + 1)):
                if r:
                    results.extend(r.get("results", []))
        seen = {}
        for o in results:
            tid = o.get("id")
            if tid is None or tid in seen:
                continue
            rd = (o.get("first_air_date") if typ == "tv" else o.get("release_date")) or ""
            date = int(rd.replace("-", "")[:8]) if re.fullmatch(r"\d{4}-\d{2}-\d{2}", rd) else (int(rd[:4]) * 10000 if rd[:4].isdigit() else 0)
            titles = []
            for k in ("title", "original_title", "name", "original_name"):
                t = o.get(k)
                if t:
                    tk = title_key(t)
                    if tk and tk not in titles:
                        titles.append(tk)
            seen[tid] = [tid, date, o.get("genre_ids", []), titles]
        out[g] = list(seen.values())
        print(f"{typ} {g}: {pages} páginas, {len(seen)} títulos", flush=True)
    return out

SLUG = {"Netflix": "netflix", "Disney+": "disney", "Max": "max", "Prime Video": "prime", "Apple TV+": "apple",
        "Movistar Plus+": "movistar", "SkyShowtime": "skyshowtime", "Filmin": "filmin"}

if __name__ == "__main__":
    for kind in ("movie", "tv"):
        for g, rows in build(kind).items():
            name = f"cat-{kind}-{SLUG[g]}.tsv.gz"
            lines = []
            for tid, date, genres, titles in rows:
                titles = [t.replace("|", " ").replace("\t", " ") for t in titles]
                lines.append(f"{tid}\t{date}\t{','.join(str(x) for x in genres)}\t{'|'.join(titles)}")
            raw = ("\n".join(lines) + "\n").encode("utf-8")
            with gzip.open(name, "wb", compresslevel=9) as f:
                f.write(raw)
            print(f"{name}: {len(rows)} títulos, {len(raw)//1024} KB sin comprimir", flush=True)
    with open("logos.json", "w", encoding="utf-8") as f:
        json.dump(LOGOS, f, ensure_ascii=False)
    print("logos.json:", len(LOGOS), "logos", flush=True)
