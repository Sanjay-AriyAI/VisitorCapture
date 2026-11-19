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
            parts = n.split("/")
            if n.startswith("photos/") and not n.endswith("/"):      # older exports
                photos[os.path.basename(n)] = (z, n)
            elif len(parts) == 3 and parts[0] == "leads" and parts[2] == "business-card.jpg":
                uid = parts[1].split(" - ")[0].strip()              # leads/<UID - Name - Company>/
                photos[uid + ".jpg"] = (z, n)
            elif len(parts) == 3 and parts[0] == "leads" and parts[2] == "business-card-back.jpg":
                uid = parts[1].split(" - ")[0].strip()
                photos[uid + "_back.jpg"] = (z, n)
    else:
        with open(path, encoding="utf-8-sig", newline="") as f:
            rows = list(csv.DictReader(f))
    return rows, photos

def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out = sys.argv[1]
    os.makedirs(os.path.join(out, "photos"), exist_ok=True)
    merged, fields, dupes = {}, [], 0
    for path in sys.argv[2:]:
        rows, photos = read(path)
        for r in rows:
            fields += [k for k in r.keys() if k not in fields]   # older exports may lack newer columns (e.g. PhotoBack)
            uid = (r.get("UID") or "").strip()
            if not uid or uid in merged:
                dupes += 1
                continue
            merged[uid] = r
            for name in (uid + ".jpg", uid + "_back.jpg"):
                if name in photos:
                    z, n = photos[name]
                    with open(os.path.join(out, "photos", name), "wb") as f:
                        f.write(z.read(n))
    ordered = sorted(merged.values(), key=lambda r: int(r.get("TimestampMs") or 0))
    with open(os.path.join(out, "merged_visitors.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, restval="")
        w.writeheader(); w.writerows(ordered)
    print(f"{len(ordered)} unique visitors written, {dupes} duplicates skipped -> {out}")

main()
