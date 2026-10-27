#!/usr/bin/env python3
"""Merge exports (.zip or .csv) from several Visitor Capture devices into one CSV + photos folder.
Usage: python merge_visitors.py OUTPUT_DIR export1.zip export2.zip ...
Duplicates (same UID) are dropped; first one seen wins."""
import csv, io, os, sys, zipfile

def read(path):
    photos = {}
    if zipfile.is_zipfile(path):
        z = zipfile.ZipFile(path)
        with z.open("visitors.csv") as f:
            rows = list(csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig", newline="")))
        for n in z.namelist():
            if n.startswith("photos/") and not n.endswith("/"):
                photos[os.path.basename(n)] = (z, n)
    else:
        with open(path, encoding="utf-8-sig", newline="") as f:
            rows = list(csv.DictReader(f))
    return rows, photos

def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out = sys.argv[1]
    os.makedirs(os.path.join(out, "photos"), exist_ok=True)
    merged, fields, dupes = {}, None, 0
    for path in sys.argv[2:]:
        rows, photos = read(path)
        for r in rows:
            fields = fields or list(r.keys())
            uid = (r.get("UID") or "").strip()
            if not uid or uid in merged:
                dupes += 1
                continue
            merged[uid] = r
            name = uid + ".jpg"
            if name in photos:
                z, n = photos[name]
                with open(os.path.join(out, "photos", name), "wb") as f:
                    f.write(z.read(n))
    ordered = sorted(merged.values(), key=lambda r: int(r.get("TimestampMs") or 0))
    with open(os.path.join(out, "merged_visitors.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields or [])
        w.writeheader(); w.writerows(ordered)
    print(f"{len(ordered)} unique visitors written, {dupes} duplicates skipped -> {out}")

main()
