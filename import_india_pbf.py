import argparse
import json
import os
import sys
import time
import urllib.request
import urllib.error
from collections import Counter

try:
    import osmium
except ImportError:
    print("ERROR: pyosmium is not installed. Run: python -m pip install osmium", file=sys.stderr)
    raise


def zone_type(tags):
    amenity = str(tags.get("amenity", "")).lower()
    if amenity == "school":
        return "SCHOOL"

    leisure = str(tags.get("leisure", "")).lower()
    boundary = str(tags.get("boundary", "")).lower()
    if leisure in {"nature_reserve", "wildlife_park"} or boundary in {
        "protected_area", "national_park", "wildlife_park"
    }:
        return "WILDLIFE"

    natural = str(tags.get("natural", "")).lower()
    if natural in {"hill", "peak", "mountain", "cliff"}:
        return "HILL_AREA"

    return None


def radius_for(kind):
    return {"SCHOOL": 300.0, "WILDLIFE": 500.0, "HILL_AREA": 500.0}[kind]


def speed_for(kind):
    return 30


def name_for(tags, kind):
    return (
        tags.get("name")
        or tags.get("official_name")
        or {"SCHOOL": "School Safety Zone", "WILDLIFE": "Wildlife Safety Zone", "HILL_AREA": "Hill Area"}[kind]
    )[:150]


class ExtractHandler(osmium.SimpleHandler):
    def __init__(self, batcher):
        super().__init__()
        self.batcher = batcher
        self.counts = Counter()
        self.seen = set()
        self.geo = osmium.geom.GeoJSONFactory()

    def _feature(self, obj, geometry_json, lat, lon):
        tags = dict(obj.tags)
        kind = zone_type(tags)
        if not kind:
            return

        key = f"{obj.type_str()}|{obj.id}|{kind}"
        if key in self.seen:
            return
        self.seen.add(key)

        feature = {
            "type": "Feature",
            "geometry": json.loads(geometry_json) if isinstance(geometry_json, str) else geometry_json,
            "properties": {
                **tags,
                "@type": obj.type_str(),
                "@id": int(obj.id),
            },
        }
        self.batcher.add(feature)
        self.counts[kind] += 1

        total = sum(self.counts.values())
        if total and total % 10000 == 0:
            print(f"Processed {total:,} matching OSM objects | {dict(self.counts)}", flush=True)

    def node(self, n):
        kind = zone_type(n.tags)
        if not kind:
            return
        self._feature(n, self.geo.create_point(n.location), n.location.lat, n.location.lon)

    def way(self, w):
        kind = zone_type(w.tags)
        if not kind:
            return
        # Closed school/protected-area ways are handled by the area callback.
        # Keep open linear features such as cliffs in the way callback.
        try:
            if hasattr(w, "is_closed") and w.is_closed() and kind in {"SCHOOL", "WILDLIFE"}:
                return
            geom = self.geo.create_linestring(w)
            if not geom:
                return
            coords = json.loads(geom)["coordinates"]
            if not coords:
                return
            lon = sum(p[0] for p in coords) / len(coords)
            lat = sum(p[1] for p in coords) / len(coords)
            self._feature(w, geom, lat, lon)
        except Exception:
            return

    def area(self, a):
        kind = zone_type(a.tags)
        if not kind:
            return
        try:
            geom = self.geo.create_multipolygon(a)
            if not geom:
                return
            g = json.loads(geom)
            points = []
            def collect(x):
                if isinstance(x, list) and len(x) >= 2 and isinstance(x[0], (int, float)) and isinstance(x[1], (int, float)):
                    points.append(x)
                elif isinstance(x, list):
                    for child in x:
                        collect(child)
            collect(g.get("coordinates", []))
            if not points:
                return
            lon = sum(p[0] for p in points) / len(points)
            lat = sum(p[1] for p in points) / len(points)
            self._feature(a, geom, lat, lon)
        except Exception:
            return


class BatchSender:
    def __init__(self, base_url, batch_size, progress_file):
        self.url = base_url.rstrip("/") + "/api/safety-zones/import/geojson-batch"
        self.batch_size = batch_size
        self.items = []
        self.sent = 0
        self.progress_file = progress_file

    def add(self, item):
        self.items.append(item)
        if len(self.items) >= self.batch_size:
            self.flush()

    def flush(self):
        if not self.items:
            return
        payload = json.dumps(self.items, separators=(",", ":")).encode("utf-8")
        last_error = None
        for attempt in range(1, 6):
            try:
                req = urllib.request.Request(
                    self.url,
                    data=payload,
                    headers={
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                        "User-Agent": "SafeRoad-Local-OSM-Importer/1.0",
                    },
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=300) as response:
                    if response.status < 200 or response.status >= 300:
                        raise RuntimeError(f"HTTP {response.status}")
                    response.read()
                self.sent += len(self.items)
                with open(self.progress_file, "w", encoding="utf-8") as f:
                    f.write(str(self.sent))
                print(f"Saved batch: +{len(self.items):,} | total sent: {self.sent:,}", flush=True)
                self.items.clear()
                return
            except Exception as exc:
                last_error = exc
                print(f"Batch failed (attempt {attempt}/5): {exc}", file=sys.stderr, flush=True)
                time.sleep(min(30, 3 * attempt))
        raise RuntimeError(f"Could not save batch after 5 attempts: {last_error}")

    def finish(self):
        self.flush()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pbf", required=True)
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--batch-size", type=int, default=500)
    ap.add_argument("--work-dir", required=True)
    args = ap.parse_args()

    if not os.path.exists(args.pbf):
        raise SystemExit(f"PBF not found: {args.pbf}")

    os.makedirs(args.work_dir, exist_ok=True)
    progress = os.path.join(args.work_dir, "pbf-import-progress.txt")
    sender = BatchSender(args.base_url, max(50, args.batch_size), progress)
    handler = ExtractHandler(sender)

    print("Reading India PBF locally. Overpass is NOT used.", flush=True)
    # Area callbacks cause pyosmium to build closed ways/multipolygon relations.
    handler.apply_file(args.pbf, locations=True)
    sender.finish()

    print("=====================================================")
    print("LOCAL OSM IMPORT COMPLETE")
    print(f"Schools   : {handler.counts['SCHOOL']:,}")
    print(f"Hills     : {handler.counts['HILL_AREA']:,}")
    print(f"Wildlife  : {handler.counts['WILDLIFE']:,}")
    print(f"Total     : {sum(handler.counts.values()):,}")
    print("=====================================================")


if __name__ == "__main__":
    main()
