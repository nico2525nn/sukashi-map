"""Convert open-license geo sources into Passage-style ring JSON assets.

Input (tools/mapdata/raw/):
  - ne_50m_coastline.json / ne_10m_coastline.json (Natural Earth, public domain)
  - ne_50m_admin_0_countries.json (Natural Earth country borders)
  - ne_10m_admin_1_states_provinces.json (Natural Earth, US states filter)
  - japan_prefectures.geojson (dataofjapan/land, 元:地球地図日本. 出典明記で利用)
  - ne_50m_lakes.json / ne_50m_rivers_lake_centerlines.json (Natural Earth)
Output (app/src/main/assets/):
  - world_map_coarse.json / world_map_fine.json   (coastline rings [[lon,lat],...])
  - country_borders.json                          (country border rings)
  - us_states.json
  - japan_prefectures.json / japan_coastline.json
  - japan_waterbodies.json (lakes+rivers in JP bbox)
Format: [[[lon,lat],...], ...]  (list of rings), rounded to 4-5 decimals.
"""
import json
import math
import os
import sys

RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "raw")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "..", "app", "src", "main", "assets")


def load(name):
    with open(os.path.join(RAW, name), encoding="utf-8") as f:
        return json.load(f)


def rings_of_geometry(geom, decimals):
    t = geom["type"]
    coords = geom["coordinates"]
    rings = []
    if t == "LineString":
        rings.append(coords)
    elif t == "MultiLineString":
        rings.extend(coords)
    elif t == "Polygon":
        rings.extend(coords)
    elif t == "MultiPolygon":
        for poly in coords:
            rings.extend(poly)
    out = []
    for ring in rings:
        pts = [[round(float(x), decimals), round(float(y), decimals)] for x, y in ring]
        if len(pts) >= 2:
            out.append(pts)
    return out


def collect(features, filt=None, decimals=4):
    rings = []
    for f in features:
        if filt and not filt(f.get("properties", {})):
            continue
        g = f.get("geometry")
        if not g:
            continue
        rings.extend(rings_of_geometry(g, decimals))
    return rings


def simplify_radial(rings, tol):
    """Cheap radial-distance simplification to bound asset size."""
    tol2 = tol * tol
    out = []
    for ring in rings:
        kept = [ring[0]]
        lx, ly = ring[0]
        for x, y in ring[1:]:
            dx, dy = x - lx, y - ly
            if dx * dx + dy * dy >= tol2:
                kept.append([x, y])
                lx, ly = x, y
        if len(kept) >= 2:
            out.append(kept)
    return out


def save(name, rings):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(rings, f, separators=(",", ":"))
    kb = os.path.getsize(path) // 1024
    print(f"{name}: {len(rings)} rings, {kb} KB")


def main():
    # World coastline coarse (50m) / fine (10m, simplified)
    coarse = load("ne_50m_coastline.json")
    save("world_map_coarse.json", collect(coarse["features"], decimals=3))
    fine = load("ne_10m_coastline.json")
    save("world_map_fine.json", simplify_radial(collect(fine["features"], decimals=4), 0.02))

    # Country borders (50m admin0 boundaries -> polygon exterior rings)
    countries = load("ne_50m_admin_0_countries.json")
    save("country_borders.json", collect(countries["features"], decimals=3))

    # US states from 10m admin1
    admin1 = load("ne_10m_admin_1_states_provinces.json")
    save("us_states.json", simplify_radial(
        collect(admin1["features"], lambda p: p.get("adm0_a3") == "USA", decimals=4), 0.01))

    # Japan prefectures (47 features, MultiPolygon)
    jp = load("japan_prefectures.geojson")
    jp_rings = collect(jp["features"], decimals=4)
    save("japan_prefectures.json", simplify_radial(jp_rings, 0.004))
    # Japan coastline: reuse prefecture exterior as coastline approximation
    save("japan_coastline.json", simplify_radial(jp_rings, 0.004))

    # Japan waterbodies: lakes+rivers clipped to JP bbox
    def in_jp_bbox(p):
        return True  # clipped per-point below
    lakes = load("ne_50m_lakes.json")
    rivers = load("ne_50m_rivers_lake_centerlines.json")
    rings = collect(lakes["features"], decimals=3) + collect(rivers["features"], decimals=3)
    jp_only = []
    for ring in rings:
        pts = [[x, y] for x, y in ring if 128.0 <= x <= 146.5 and 30.0 <= y <= 46.0]
        if len(pts) >= 2:
            jp_only.append(pts)
    save("japan_waterbodies.json", jp_only)
    print("done.")


if __name__ == "__main__":
    sys.exit(main())
