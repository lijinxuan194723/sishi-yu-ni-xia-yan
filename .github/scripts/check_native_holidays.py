"""Validate packaged calendars and, optionally, compare with pinned public upstream blobs.

Only public holiday data is fetched. No repository token, app state or user data is sent.
The online check compares parsed JSON because local whitespace is intentionally compact.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import pathlib
import re
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "native/assets/holidays"
EXPECTED_COUNTS = {2023: 34, 2024: 36, 2025: 33, 2026: 39}


def public_blob(sha: str) -> bytes:
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid pinned upstream object id")
    request = urllib.request.Request(
        f"https://api.github.com/repos/NateScarlet/holiday-cn/git/blobs/{sha}",
        headers={"Accept": "application/vnd.github+json", "User-Agent": "four-seasons-calendar-verification"},
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                payload = response.read(1_000_001)
            if len(payload) > 1_000_000:
                raise ValueError("Unexpectedly large upstream response")
            obj = json.loads(payload)
            if obj.get("sha") != sha or obj.get("encoding") != "base64":
                raise ValueError("Upstream returned a different object")
            data = base64.b64decode(obj["content"])
            header = b"blob " + str(len(data)).encode("ascii") + b"\0"
            if hashlib.sha1(header + data).hexdigest() != sha:
                raise ValueError("Git blob verification failed")
            return data
        except urllib.error.HTTPError as exc:
            if exc.code not in (429, 500, 502, 503, 504) or attempt == 2:
                raise
            time.sleep(2 ** attempt)
    raise RuntimeError("No upstream response")


def main() -> None:
    args = argparse.ArgumentParser()
    args.add_argument("--online", action="store_true")
    online = args.parse_args().online
    provenance = json.loads((ASSETS / "provenance.json").read_text(encoding="utf-8"))
    results = []
    for year, count in EXPECTED_COUNTS.items():
        path = ASSETS / f"{year}.json"
        raw = path.read_bytes()
        local = json.loads(raw)
        assert local["year"] == year
        assert len(local["days"]) == count
        assert local["papers"] and all(isinstance(p, str) for p in local["papers"])
        dates = set()
        for day in local["days"]:
            assert re.fullmatch(r"\d{4}-\d{2}-\d{2}", day["date"])
            date = dt.date.fromisoformat(day["date"])
            assert dt.date(year - 1, 12, 1) <= date <= dt.date(year, 12, 31)
            assert date not in dates
            dates.add(date)
            assert type(day["isOffDay"]) is bool
            assert isinstance(day["name"], str) and day["name"]
        upstream_sha = provenance["upstreamBlobs"][str(year)]
        if online:
            upstream = json.loads(public_blob(upstream_sha))
            if upstream != local:
                raise AssertionError(f"{year}: packaged JSON differs from pinned upstream data")
        results.append({"year": year, "dates": count, "sha256": hashlib.sha256(raw).hexdigest(),
                        "upstreamBlob": upstream_sha, "upstreamJsonEqual": True if online else None})
    licence = (ASSETS / "LICENSE.txt").read_text(encoding="utf-8")
    assert "MIT License" in licence and "Copyright (c) 2019 NateScarlet" in licence
    report = {"scope": "Bundled historical/current calendars only; future API availability is not guaranteed",
              "online": online, "years": results}
    output = ROOT / "native-holiday-verification"
    output.mkdir(exist_ok=True)
    (output / "summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
