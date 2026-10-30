#!/usr/bin/env python3
"""Brand artwork for messaging and AI apps that are not installed: fetch and check.

The policy is in docs/BRAND_ASSETS.md. In short: an installed app always shows the icon Android gives
us; a bundled copy is used only to *identify* a supported app that is not installed, with its source
recorded here. The catalog is brand-assets/CATALOG.tsv.

  check            Validate the catalog; every row marked `bundled` must have its file in
                   app/src/main/res/drawable-nodpi with the recorded sha256, every file there must be in
                   the catalog. Writes nothing. CI runs this (tests/run.sh).
  fetch [--only P] For `play` rows still `pending`, download the developer-supplied icon that the
                   listing publishes as og:image, write it to drawable-nodpi and mark the row `bundled`
                   with its sha256. Run on a machine with internet access; review the result before
                   committing. `appstore` and `vendor` rows are fetched by hand: save the file as
                   <file>.png (or .webp, .jpg) in drawable-nodpi and run `record`.
  record           Mark rows `bundled` (with sha256) when their file is already in drawable-nodpi.
"""
import argparse
import csv
import hashlib
import os
import sys
import urllib.request
from html.parser import HTMLParser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "brand-assets", "CATALOG.tsv")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
COLUMNS = ["kind", "name", "packages", "source", "source_url", "install_url", "file", "status", "sha256"]
EXTENSIONS = {"image/png": ".png", "image/webp": ".webp", "image/jpeg": ".jpg"}


class OgImageParser(HTMLParser):
    """Finds <meta property="og:image" content="..."> without a backtracking regular expression."""

    def __init__(self):
        super().__init__()
        self.url = None

    def handle_starttag(self, tag, attrs):
        if tag != "meta" or self.url:
            return
        values = dict(attrs)
        if (values.get("property") or "").lower() == "og:image" and values.get("content"):
            self.url = values["content"]


def og_image(html):
    parser = OgImageParser()
    parser.feed(html)
    return parser.url


def load():
    with open(CATALOG, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f, delimiter="\t")
        if reader.fieldnames != COLUMNS:
            sys.exit("CATALOG.tsv columns must be: " + " ".join(COLUMNS))
        return list(reader)


def save(rows):
    with open(CATALOG, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def files_for(row):
    if not os.path.isdir(RES):
        return []
    return [n for n in os.listdir(RES) if os.path.splitext(n)[0] == row["file"]]


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def row_problems(row):
    """Everything wrong with one catalog row, as printable messages."""
    name = row["name"]
    problems = []
    if row["kind"] not in ("messaging", "ai"):
        problems.append("unknown kind: %s %s" % (row["kind"], name))
    if row["source"] not in ("play", "appstore", "vendor"):
        problems.append("unknown source: %s %s" % (name, row["source"]))
    if not row["source_url"].startswith("https://"):
        problems.append("source_url must be https: " + name)
    if row["status"] not in ("pending", "bundled"):
        problems.append("status must be pending or bundled: " + name)
    found = files_for(row)
    if row["status"] == "bundled":
        if len(found) != 1:
            problems.append("bundled but file missing or ambiguous: %s %s" % (name, row["file"]))
        elif sha256(os.path.join(RES, found[0])) != row["sha256"]:
            problems.append("sha256 differs from the catalog: " + name)
    elif found:
        problems.append("file present but row is pending (run: tools/brand_assets.py record): " + name)
    return problems


def untracked_artwork(rows):
    known = {r["file"] for r in rows}
    if not os.path.isdir(RES):
        return []
    return [
        "artwork not in the catalog (add its source first): " + n
        for n in os.listdir(RES)
        if n.startswith("brand_") and os.path.splitext(n)[0] not in known
    ]


def cmd_check(_args):
    rows = load()
    problems = []
    seen = set()
    for row in rows:
        key = (row["kind"], row["name"])
        if key in seen:
            problems.append("duplicate row: %s" % (key,))
        seen.add(key)
        problems += row_problems(row)
    problems += untracked_artwork(rows)
    for message in problems:
        print("FAIL", message)
    if not problems:
        done = sum(1 for r in rows if r["status"] == "bundled")
        print("PASS brand assets: %d catalog rows, %d bundled, %d pending" % (len(rows), done, len(rows) - done))
    return 1 if problems else 0


def fetch_play_icon(row):
    package = row["packages"].split(",")[0]
    request = urllib.request.Request(row["source_url"], headers={"User-Agent": "Mozilla/5.0 tenseven-brand-assets"})
    with urllib.request.urlopen(request, timeout=30) as page:  # noqa: S310 (https URL from the catalog)
        image_url = og_image(page.read().decode("utf-8", "replace"))
    if not image_url:
        raise RuntimeError("no og:image on the listing for " + package)
    with urllib.request.urlopen(urllib.request.Request(image_url, headers={"User-Agent": "Mozilla/5.0"}), timeout=30) as img:  # noqa: S310
        data = img.read()
        extension = EXTENSIONS.get(img.headers.get_content_type())
    if extension is None:
        raise RuntimeError("unsupported image type for " + package)
    return data, extension


def cmd_fetch(args):
    rows = load()
    os.makedirs(RES, exist_ok=True)
    failed = 0
    for row in rows:
        if row["status"] != "pending" or row["source"] != "play":
            continue
        if args.only and args.only not in row["packages"].split(","):
            continue
        try:
            data, extension = fetch_play_icon(row)
        except Exception as error:  # report every failure, keep going
            print("FAIL", row["name"], error)
            failed += 1
            continue
        path = os.path.join(RES, row["file"] + extension)
        with open(path, "wb") as f:
            f.write(data)
        row["status"], row["sha256"] = "bundled", sha256(path)
        print("bundled", row["name"], "->", os.path.relpath(path, ROOT))
    save(rows)
    return 1 if failed else 0


def cmd_record(_args):
    rows = load()
    for row in rows:
        found = files_for(row)
        if len(found) == 1:
            row["status"], row["sha256"] = "bundled", sha256(os.path.join(RES, found[0]))
    save(rows)
    return cmd_check(_args)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check")
    f = sub.add_parser("fetch")
    f.add_argument("--only", help="fetch only the row with this package name")
    sub.add_parser("record")
    args = p.parse_args()
    sys.exit({"check": cmd_check, "fetch": cmd_fetch, "record": cmd_record}[args.cmd](args))


if __name__ == "__main__":
    main()
