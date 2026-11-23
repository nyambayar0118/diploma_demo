#!/usr/bin/env python3
"""Downloads every company from the Minu Map API (map.minu.mn) into a JSON Lines file.

The search endpoint returns all ~44k companies for an empty search, paged with start/end.
Requests are spaced out (MINU_DELAY seconds, default 1) and progress is saved after each
page, so an interrupted run continues where it stopped.

Environment:
  MINU_API_TOKEN  required, the token from map.minu.mn/developer ("Token авах")
  MINU_OUT        output file            (default /data/raw/minu/companies.jsonl)
  MINU_PAGE       companies per request  (default 100)
  MINU_DELAY      seconds between calls  (default 1.0)
  MINU_FRESH=1    ignore an earlier partial download and start over

Also writes categories.csv next to the output: how many companies each Minu category has,
which is what minu_category_mappings is tuned against.
"""
import collections
import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://map.minu.mn/api-gateway/v1/proxy/search-company"
TOKEN = os.environ.get("MINU_API_TOKEN", "").strip()
OUT = os.environ.get("MINU_OUT", "/data/raw/minu/companies.jsonl")
PAGE = int(os.environ.get("MINU_PAGE", "100"))
DELAY = float(os.environ.get("MINU_DELAY", "1.0"))
STATE = OUT + ".state"


def call(start, end, attempt=1):
    url = BASE + "?" + urllib.parse.urlencode({"search": "", "start": start, "end": end})
    req = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {TOKEN}",
        "Accept": "application/json",
        "User-Agent": "num-planner-diploma-import/1.0",
    })
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            body = json.load(r)
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            sys.exit(f"!! {e.code}: the token was refused. Check MINU_API_TOKEN.")
        if attempt >= 5:
            raise
        wait = 5 * attempt
        print(f"   HTTP {e.code}, retrying in {wait}s", flush=True)
        time.sleep(wait)
        return call(start, end, attempt + 1)
    except (urllib.error.URLError, TimeoutError) as e:
        if attempt >= 5:
            raise
        wait = 5 * attempt
        print(f"   {e}, retrying in {wait}s", flush=True)
        time.sleep(wait)
        return call(start, end, attempt + 1)
    # Response shape: [[{"numfound": N, "documents": [...]}], [], []]
    block = body[0][0] if body and body[0] else {"numfound": 0, "documents": []}
    return int(block.get("numfound", 0)), block.get("documents", [])


def main():
    if not TOKEN:
        sys.exit("!! Set MINU_API_TOKEN first.")
    os.makedirs(os.path.dirname(OUT) or ".", exist_ok=True)

    start = 0
    if os.environ.get("MINU_FRESH") != "1" and os.path.exists(STATE) and os.path.exists(OUT):
        start = int(open(STATE).read().strip() or 0)
        print(f">> Continuing from company {start}")
    else:
        open(OUT, "w").close()
        if os.path.exists(OUT + ".done"):
            os.remove(OUT + ".done")

    total, _ = call(0, 1)
    print(f">> Minu has {total} companies; fetching {PAGE} per request")
    with open(OUT, "a", encoding="utf-8") as out:
        while start < total:
            _, docs = call(start, start + PAGE)
            if not docs:
                print(f"   empty page at {start}, stopping")
                break
            for d in docs:
                out.write(json.dumps(d, ensure_ascii=False) + "\n")
            out.flush()
            start += len(docs)
            with open(STATE, "w") as s:
                s.write(str(start))
            print(f"   {start}/{total}", flush=True)
            time.sleep(DELAY)

    # Category report (deduplicated by companyId)
    seen, counts = set(), collections.Counter()
    with open(OUT, encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            if d.get("companyId") in seen:
                continue
            seen.add(d.get("companyId"))
            for name in d.get("categoryNameEng") or []:
                counts[name.strip()] += 1
    report = os.path.join(os.path.dirname(OUT) or ".", "categories.csv")
    with open(report, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["category_en", "companies"])
        w.writerows(counts.most_common())
    with open(OUT + ".done", "w") as f:
        f.write(str(len(seen)))
    print(f">> Done: {len(seen)} unique companies in {OUT}; category counts in {report}")


if __name__ == "__main__":
    main()
