#!/usr/bin/env python3
"""Build compact, Web-only map data from the Android source assets.

The Web map box is small, so keeping every source vertex costs bandwidth
without a visible benefit. Each ring is simplified with Douglas-Peucker and
then stored as a flat delta-encoded integer array (1e-3 degrees, about 110 m)
which gzip-compresses far better than decimal JSON. Android keeps the original
high-resolution assets.
"""
from __future__ import annotations

import gzip
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID_ASSETS = ROOT / "app" / "src" / "main" / "assets"
WEB_DATA = ROOT / "web" / "public" / "data"
SCALE = 1000

DATASETS = [
    ("japan_coastline_highres.json", "japan_coastline_highres.json", 0.002),
    ("japan_prefectures.json", "japan_prefectures.json", 0.002),
    ("us_states.json", "us_states.json", 0.004),
    ("world_map_coarse.json", "world_map_coarse.json", 0.0),
    ("japan_waterbodies.json", "japan_waterbodies.json", 0.0),
]


def rdp(points: list[list[float]], epsilon: float) -> list[list[float]]:
    if epsilon <= 0 or len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        start, end = stack.pop()
        if end <= start + 1:
            continue
        ax, ay = points[start]
        bx, by = points[end]
        dx, dy = bx - ax, by - ay
        denominator = dx * dx + dy * dy
        best = -1.0
        best_index = -1
        for index in range(start + 1, end):
            px, py = points[index]
            if denominator == 0:
                distance = math.hypot(px - ax, py - ay)
            else:
                t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / denominator))
                distance = math.hypot(px - (ax + t * dx), py - (ay + t * dy))
            if distance > best:
                best = distance
                best_index = index
        if best > epsilon:
            keep[best_index] = True
            stack.append((start, best_index))
            stack.append((best_index, end))
    return [point for point, kept in zip(points, keep) if kept]


def encode_ring(ring: list[list[float]]) -> list[int]:
    x, y = round(ring[0][0] * SCALE), round(ring[0][1] * SCALE)
    flat = [x, y]
    for lon, lat in ring[1:]:
        nx, ny = round(lon * SCALE), round(lat * SCALE)
        flat.extend((nx - x, ny - y))
        x, y = nx, ny
    return flat


def build(source: str, target: str, epsilon: float) -> None:
    rings = json.loads((ANDROID_ASSETS / source).read_text())
    source_points = sum(len(ring) for ring in rings)
    simplified = []
    kept_points = 0
    for ring in rings:
        reduced = rdp(ring, epsilon)
        if len(reduced) >= 2:
            simplified.append(reduced)
            kept_points += len(reduced)
    encoded = {"scale": SCALE, "rings": [encode_ring(ring) for ring in simplified]}
    raw = json.dumps(encoded, separators=(",", ":")).encode()
    (WEB_DATA / target).write_bytes(raw)
    print(
        f"{target}: {len(rings)} rings, {source_points} -> {kept_points} points, "
        f"{len(raw) / 1024:.0f} KB raw, {len(gzip.compress(raw, 9)) / 1024:.0f} KB gzip"
    )


if __name__ == "__main__":
    for dataset in DATASETS:
        build(*dataset)
