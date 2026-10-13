"""Build prepopulated Room seed data (ships as JSON; imported on first run).

Input (tools/mapdata/raw/):
  - airports.dat / airlines.dat (OpenFlights, ODbL)
  - mlit_lines.json / mlit_stations.json / mlit_sections.json (mlit-json, 国土数値情報利用約款)
Output (app/src/main/assets/seed/):
  - airports.json  [{iata,icao,name,city,country,lat,lon}]
  - airlines.json  [{code,name}] (IATA codes only)
  - rail_lines.json [{name,operator,type}]
  - rail_stations.json [{name,lat,lon,line,operator}]
  - rail_sections.json [{line,operator,coords:[[lon,lat],...]}] (simplified)
"""
import csv
import json
import os
import sys

RAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "raw")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "..", "app", "src", "main", "assets", "seed")


def save(name, obj):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, separators=(",", ":"), ensure_ascii=False)
    print(f"{name}: {len(obj)} records, {os.path.getsize(path)//1024} KB")


def build_airports():
    rows = []
    with open(os.path.join(RAW, "airports.dat"), encoding="utf-8") as f:
        for r in csv.reader(f):
            if len(r) < 12:
                continue
            iata, icao = r[4].strip(), r[5].strip()
            try:
                lat, lon = float(r[6]), float(r[7])
            except ValueError:
                continue
            if iata in ("", "\\N", "-") and icao in ("", "\\N", "-"):
                continue
            rows.append({"iata": "" if iata == "\\N" else iata,
                         "icao": "" if icao == "\\N" else icao,
                         "name": r[1], "city": r[2], "country": r[3],
                         "lat": round(lat, 5), "lon": round(lon, 5)})
    save("airports.json", rows)


def build_airlines():
    rows, seen = [], set()
    with open(os.path.join(RAW, "airlines.dat"), encoding="utf-8") as f:
        for r in csv.reader(f):
            if len(r) < 8:
                continue
            code, name, active = r[4].strip(), r[1].strip(), r[7].strip()
            if code in ("", "\\N", "-", "--", "N/A") or code in seen:
                continue
            seen.add(code)
            rows.append({"code": code, "name": name, "active": active == "Y"})
    rows.sort(key=lambda x: (not x["active"], x["code"]))
    save("airlines.json", rows)


def build_rail():
    with open(os.path.join(RAW, "mlit_lines.json"), encoding="utf-8") as f:
        lines = json.load(f)
    save("rail_lines.json", [
        {"name": l["name"], "operator": l.get("operator", ""),
         "type": l.get("type", 0), "stations": l.get("stations", [])}
        for l in lines])
    with open(os.path.join(RAW, "mlit_stations.json"), encoding="utf-8") as f:
        stations = json.load(f)
    save("rail_stations.json", [
        {"name": s["name"], "lat": s["lat"], "lon": s["lng"],
         "line": s.get("line", ""), "operator": s.get("operator", "")}
        for s in stations])
    with open(os.path.join(RAW, "mlit_sections.json"), encoding="utf-8") as f:
        sections = json.load(f)
    out = []
    for s in sections:
        coords = s.get("coordinates", [])
        # radial simplify
        kept = []
        lx = ly = None
        for x, y in coords:
            if lx is None or (x - lx) ** 2 + (y - ly) ** 2 >= 0.00002 ** 2:
                kept.append([round(x, 5), round(y, 5)])
                lx, ly = x, y
        if len(kept) >= 2:
            out.append({"line": s.get("line", ""), "operator": s.get("operator", ""),
                        "coords": kept})
    save("rail_sections.json", out)


def main():
    build_airports()
    build_airlines()
    build_rail()
    print("done.")


if __name__ == "__main__":
    sys.exit(main())
