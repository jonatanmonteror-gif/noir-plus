#!/usr/bin/env python3
"""
Próximos estrenos (90 días) para la sección «Próximos» de la app, a partir de TMDB.

Salida: upcoming.json.gz — lista de estrenos:
  {"t": "tv"|"movie", "id": 123, "n": "Título", "d": aaaammdd, "k": "new"|"season"|"episode"|"film",
   "s": temporada, "e": capítulo, "en": "nombre del capítulo", "p": "Netflix" (o "" si no se sabe), "w": "cines"|"digital"|"",
   "img": "/ruta_poster.jpg", "g": [géneros], "keys": ["titulo normalizado", ...], "last": aaaammdd (último capítulo emitido)}
Las series traen su próximo capítulo; la app enseña los capítulos sueltos solo de las series que sigues.
"""
import gzip, json, os, re, sys, time, unicodedata, urllib.parse, urllib.request
from concurrent.futures import ThreadPoolExecutor

KEY = os.environ.get("TMDB_KEY", "").strip()
if not KEY:
    sys.exit("Falta TMDB_KEY")

DAYS = 90
TODAY = time.strftime("%Y-%m-%d")
UNTIL = time.strftime("%Y-%m-%d", time.gmtime(time.time() + DAYS * 86400))
PAST = time.strftime("%Y-%m-%d", time.gmtime(time.time() - 10 * 86400))  # para avisos de capítulos recién salidos

MAIN = ["Netflix", "Disney+", "Max", "Prime Video", "Apple TV+", "Movistar Plus+", "SkyShowtime", "Filmin"]
RULES = [
    (r"\bnetflix\b", "Netflix"), (r"\bdisney", "Disney+"), (r"\bhbo\b|\bmax\b", "Max"),
    (r"\bprime\b|\bamazon\b", "Prime Video"), (r"\bapple\b", "Apple TV+"), (r"\bmovistar", "Movistar Plus+"),
    (r"\bskyshowtime\b", "SkyShowtime"), (r"\bfilmin\b", "Filmin"),
]
# Cadenas de TMDB (las que se emiten en esa plataforma). Se comprueba el nombre antes de usarlas.
NETWORK_CANDIDATES = {
    "Netflix": [213], "Prime Video": [1024], "Disney+": [2739], "Apple TV+": [2552],
    "Max": [49, 3186, 6783], "Movistar Plus+": [2773, 3268, 1278], "SkyShowtime": [4405, 4581], "Filmin": [3076],
}


def strip_marks(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def group(name):
    n = strip_marks(name or "").lower()
    for rx, g in RULES:
        if re.search(rx, n):
            return g
    return None


def title_key(s):
    return re.sub(r"[^a-z0-9]+", " ", strip_marks(s).lower()).strip()


def ymd(s):
    return int(s.replace("-", "")[:8]) if s and re.fullmatch(r"\d{4}-\d{2}-\d{2}", s) else 0


def get(path, params=None):
    q = dict(params or {}, api_key=KEY, language="es-ES")
    url = "https://api.themoviedb.org/3" + path + "?" + urllib.parse.urlencode(q)
    for attempt in range(5):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return json.load(r)
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return None


def discover_all(path, params, max_pages=40):
    first = get(path, dict(params, page=1))
    if not first:
        return []
    pages = max(1, min(max_pages, first.get("total_pages", 1)))
    out = list(first.get("results", []))
    with ThreadPoolExecutor(8) as ex:
        for r in ex.map(lambda pg: get(path, dict(params, page=pg)), range(2, pages + 1)):
            if r:
                out.extend(r.get("results", []))
    return out


def keys_of(o):
    ks = []
    for k in ("title", "original_title", "name", "original_name"):
        t = o.get(k)
        if t:
            tk = title_key(t)
            if tk and tk not in ks:
                ks.append(tk)
    return ks


def providers(typ):
    provs = get(f"/watch/providers/{typ}", {"watch_region": "ES"}) or {}
    ids = {}
    for p in provs.get("results", []):
        g = group(p.get("provider_name", ""))
        if g:
            ids.setdefault(g, []).append(str(p["provider_id"]))
    return ids


def networks():
    out = {}
    for g, cands in NETWORK_CANDIDATES.items():
        for nid in cands:
            n = get(f"/network/{nid}") or {}
            if group(n.get("name", "")) == g:
                out.setdefault(g, []).append(str(nid))
        print(f"cadenas {g}: {out.get(g, [])}", flush=True)
    return out


def build_tv():
    # Series con algún capítulo entre hace 10 días y dentro de 90, por plataforma (cadena y disponibilidad en España)
    plat_of = {}
    base_window = {"air_date.gte": PAST, "air_date.lte": UNTIL, "sort_by": "popularity.desc"}
    for g, nids in networks().items():
        for o in discover_all("/discover/tv", dict(base_window, with_networks="|".join(nids))):
            plat_of.setdefault(o["id"], g)
    for g, pids in providers("tv").items():
        if g not in MAIN:
            continue
        for o in discover_all("/discover/tv", dict(base_window, watch_region="ES", with_watch_providers="|".join(pids),
                                                    with_watch_monetization_types="flatrate|free|ads")):
            plat_of.setdefault(o["id"], g)
    # Además, las series más populares en general (por si sigues alguna de otra cadena)
    for o in discover_all("/discover/tv", base_window, max_pages=15):
        plat_of.setdefault(o["id"], "")
    print("series a revisar:", len(plat_of), flush=True)

    ids = list(plat_of)
    with ThreadPoolExecutor(8) as ex:
        details = list(ex.map(lambda t: get(f"/tv/{t}"), ids))
    out = []
    for tid, d in zip(ids, details):
        if not d:
            continue
        nxt = d.get("next_episode_to_air") or {}
        last = d.get("last_episode_to_air") or {}
        first = ymd(d.get("first_air_date") or "")
        nd = ymd(nxt.get("air_date") or "")
        base = {"t": "tv", "id": tid, "n": d.get("name") or "", "p": plat_of[tid] or (group(((d.get("networks") or [{}])[0]).get("name", "")) or ""),
                "img": d.get("poster_path") or "", "g": [x.get("id") for x in d.get("genres") or []], "keys": keys_of(d),
                "last": ymd(last.get("air_date") or ""), "ls": last.get("season_number") or 0, "le": last.get("episode_number") or 0,
                "pop": round(d.get("popularity") or 0, 1), "lang": d.get("original_language") or ""}
        if nd and TODAY.replace("-", "") <= str(nd) <= UNTIL.replace("-", ""):
            s, e = nxt.get("season_number") or 0, nxt.get("episode_number") or 0
            kind = "new" if (first == 0 or first >= nd) and s <= 1 else ("season" if e == 1 else "episode")
            out.append(dict(base, d=nd, k=kind, s=s, e=e, en=nxt.get("name") or ""))
        elif base["last"]:
            # Sin próximo capítulo conocido: solo sirve para avisar de capítulos recién salidos
            out.append(dict(base, d=0, k="aired", s=0, e=0, en=""))
    return out


def build_movies():
    out = {}
    # Películas de cada plataforma que se estrenan en ella (cuando TMDB ya lo sabe)
    for g, pids in providers("movie").items():
        if g not in MAIN:
            continue
        for o in discover_all("/discover/movie", {"watch_region": "ES", "with_watch_providers": "|".join(pids),
                                                  "primary_release_date.gte": TODAY, "primary_release_date.lte": UNTIL,
                                                  "sort_by": "popularity.desc"}, max_pages=10):
            out.setdefault(o["id"], (o, g, "digital"))
    # Estrenos en cines y en digital en España
    for rtype, label in (("2|3", "cines"), ("4|6", "digital")):
        for o in discover_all("/discover/movie", {"region": "ES", "with_release_type": rtype, "release_date.gte": TODAY,
                                                  "release_date.lte": UNTIL, "sort_by": "popularity.desc"}, max_pages=10):
            out.setdefault(o["id"], (o, "", label))
    res = []
    for mid, (o, g, w) in out.items():
        d = ymd(o.get("release_date") or "")
        if not d:
            continue
        res.append({"t": "movie", "id": mid, "n": o.get("title") or "", "d": d, "k": "film", "p": g, "w": w,
                    "img": o.get("poster_path") or "", "g": o.get("genre_ids") or [], "keys": keys_of(o),
                    "pop": round(o.get("popularity") or 0, 1), "lang": o.get("original_language") or ""})
    return res


if __name__ == "__main__":
    tv = build_tv()
    mv = build_movies()
    allx = sorted(tv + mv, key=lambda x: (x["d"] or 99999999, x["n"]))
    raw = json.dumps(allx, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with gzip.open("upcoming.json.gz", "wb", compresslevel=9) as f:
        f.write(raw)
    kinds = {}
    for x in allx:
        kinds[x["k"]] = kinds.get(x["k"], 0) + 1
    print("upcoming.json.gz:", len(allx), kinds, len(raw) // 1024, "KB", flush=True)
