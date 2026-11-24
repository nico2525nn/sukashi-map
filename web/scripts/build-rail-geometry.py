#!/usr/bin/env python3
"""Build compact per-line railway geometries for the web app from railways.db.

Run from the repository root:
  python3 web/scripts/build-rail-geometry.py

The output is committed so the Pages build needs neither Python nor SQLite.
"""
from __future__ import annotations

import collections
import hashlib
import json
import math
import os
import sqlite3

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DB = os.path.join(ROOT, "app/src/main/assets/railways.db")
OUT = os.path.join(ROOT, "web/public/data")
GEOM = os.path.join(OUT, "rail_geometry")
LINES = os.path.join(OUT, "rail_lines.json")
PRECISION = 1e5
SIMPLIFY_METERS = 25.0
MERGE_DISTANCE_DEG = 0.012  # ~1.3km: closes tiny station-area gaps, not real branches


def keypt(p):
    return (round(p[0], 4), round(p[1], 4))


def build_chains(sections):
    edges = []
    seen = set()
    for pts in sections:
        t = tuple(keypt(p) for p in pts)
        if len(t) < 2 or t in seen:
            continue
        seen.add(t)
        edges.append(list(t))
    adj = collections.defaultdict(list)
    for i, e in enumerate(edges):
        adj[e[0]].append((i, False))
        adj[e[-1]].append((i, True))
    used = [False] * len(edges)

    def walk(start, idx, reverse):
        points = []
        cur, rev = idx, reverse
        while True:
            used[cur] = True
            e = edges[cur] if not rev else list(reversed(edges[cur]))
            points.extend(e if not points else e[1:])
            end = e[-1]
            nxt = next(((j, r) for j, r in adj[end] if not used[j]), None)
            if nxt is None:
                return points
            cur, rev = nxt

    chains = []
    for i, e in enumerate(edges):
        if used[i]:
            continue
        for point, reverse in ((e[0], False), (e[-1], True)):
            if used[i]:
                break
            if len(adj[point]) == 1:
                chains.append(walk(point, i, reverse))
    for i, e in enumerate(edges):
        if not used[i]:
            chains.append(walk(e[0], i, False))
    return [c for c in chains if len(c) >= 2]


def chain_length(points):
    total = 0.0
    for a, b in zip(points, points[1:]):
        dx = (b[0] - a[0]) * math.cos(math.radians((a[1] + b[1]) / 2))
        dy = b[1] - a[1]
        total += math.hypot(dx, dy)
    return total


def merge_chains(chains):
    chains = [list(c) for c in chains]
    changed = True
    while changed and len(chains) > 1:
        changed = False
        best = None
        for i in range(len(chains)):
            for j in range(i + 1, len(chains)):
                a, b = chains[i], chains[j]
                for ai, ap in ((0, a[0]), (-1, a[-1])):
                    for bi, bp in ((0, b[0]), (-1, b[-1])):
                        d = math.hypot(ap[0] - bp[0], ap[1] - bp[1])
                        if d <= MERGE_DISTANCE_DEG and (best is None or d < best[0]):
                            best = (d, i, j, ai, bi)
        if best is not None:
            _, i, j, ai, bi = best
            a, b = chains[i], chains[j]
            if ai == -1 and bi == 0:
                merged = a + b[1:]
            elif ai == -1 and bi == -1:
                merged = a + list(reversed(b))[1:]
            elif ai == 0 and bi == -1:
                # Join a[0] to b[-1]: reverse both, keeping the two join points.
                merged = list(reversed(a)) + list(reversed(b))
            elif ai == 0 and bi == 0:
                # Join a[0] to b[0]: reverse a, then keep b's join point.
                merged = list(reversed(a)) + b
            else:
                merged = a + b
            chains = [c for k, c in enumerate(chains) if k not in (i, j)] + [merged]
            changed = True
    chains.sort(key=chain_length, reverse=True)
    return chains


def rdp(points, epsilon_m):
    if len(points) <= 3:
        return points
    lat = math.radians(points[len(points) // 2][1])
    mx = 111320.0 * max(abs(math.cos(lat)), 0.1)
    my = 111132.0

    def dist(p, a, b):
        px, py = p[0] * mx, p[1] * my
        ax, ay = a[0] * mx, a[1] * my
        bx, by = b[0] * mx, b[1] * my
        dx, dy = bx - ax, by - ay
        l2 = dx * dx + dy * dy
        t = 0.0 if l2 < 1e-9 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        if b <= a + 1:
            continue
        best, idx = 0.0, a
        for i in range(a + 1, b):
            d = dist(points[i], points[a], points[b])
            if d > best:
                best, idx = d, i
        if best > epsilon_m:
            keep[idx] = True
            stack.extend([(a, idx), (idx, b)])
    return [p for i, p in enumerate(points) if keep[i]]


def encode(points):
    out = []
    prev_lat = prev_lon = 0
    for lon, lat in points:
        ilat = round(lat * PRECISION)
        ilon = round(lon * PRECISION)
        dlat, dlon = ilat - prev_lat, ilon - prev_lon
        prev_lat, prev_lon = ilat, ilon
        for value in (dlat, dlon):
            v = value << 1 if value >= 0 else ~(value << 1)
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
    return "".join(out)


def main():
    con = sqlite3.connect(DB)
    by_group = collections.defaultdict(list)
    for line, company, geometry_json in con.execute(
        "select line_name, company_name, geometry_json from railroad_sections"
    ):
        try:
            points = json.loads(geometry_json)
        except Exception:
            continue
        if len(points) >= 2:
            by_group[(line, company)].append(points)
    con.close()

    os.makedirs(GEOM, exist_ok=True)
    for old in os.listdir(GEOM):
        if old.endswith(".json"):
            os.unlink(os.path.join(GEOM, old))

    existing = json.load(open(LINES, encoding="utf-8"))
    by_company = {(x["line"], x["company"]): dict(x) for x in existing}
    for entry in by_company.values():
        for field in ("geometryKey", "geometryPoints", "geometryChains"):
            entry.pop(field, None)
    total_points = 0
    total_bytes = 0
    for (line, company), sections in sorted(by_group.items()):
        entry = by_company.get((line, company))
        if entry is None:
            continue
        chains = merge_chains(build_chains(sections))
        simplified = []
        for chain in chains:
            simplified_chain = rdp(chain, SIMPLIFY_METERS)
            # Drop only sub-200 m noise. The old 0.15-degree (about 16 km)
            # cutoff silently removed short lines such as Hakone Tozan.
            if len(simplified_chain) >= 2 and chain_length(simplified_chain) > 0.002:
                simplified.append(simplified_chain)
        if not simplified:
            continue
        encoded = [encode(c) for c in simplified]
        key = hashlib.sha1(f"{company}\u0000{line}".encode("utf-8")).hexdigest()[:12]
        payload = {
            "line": line,
            "company": company,
            "chains": encoded,
            "points": sum(len(c) for c in simplified),
        }
        raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
        with open(os.path.join(GEOM, key + ".json"), "wb") as f:
            f.write(raw)
        total_points += payload["points"]
        total_bytes += len(raw)
        entry["geometryKey"] = key
        entry["geometryPoints"] = payload["points"]
        entry["geometryChains"] = len(encoded)

    payload = sorted(by_company.values(), key=lambda x: (x["line"], x["company"]))
    with open(LINES, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))

    errors = []
    for entry in payload:
        key = entry.get("geometryKey")
        if not key:
            continue
        path = os.path.join(GEOM, key + ".json")
        if not os.path.isfile(path):
            errors.append(f"missing geometry: {entry['company']} {entry['line']} -> {key}")
            continue
        data = json.load(open(path, encoding="utf-8"))
        if data.get("line") != entry["line"] or data.get("company") != entry["company"]:
            errors.append(
                f"geometry mismatch: {entry['company']} {entry['line']} -> "
                f"{data.get('company')} {data.get('line')}"
            )
    if errors:
        raise SystemExit("\n".join(errors))

    print(f"groups={len(by_group)} points={total_points} geometry_bytes={total_bytes}")
    print(f"manifest={os.path.getsize(LINES)} bytes, files={len(os.listdir(GEOM))}")


if __name__ == "__main__":
    main()
