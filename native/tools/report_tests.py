"""Read real JUnit cases, retain failures, and make module-level CI results inspectable."""
import argparse
import collections
import json
import os
import pathlib
import re
import struct
import sys
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("root", type=pathlib.Path)
parser.add_argument("output", type=pathlib.Path)
parser.add_argument("--scope", required=True)
parser.add_argument("--diagnostics", type=pathlib.Path)
args = parser.parse_args()
classes = collections.defaultdict(lambda: dict(tests=0, failures=0, errors=0, skipped=0))
failures = []
files = sorted(args.root.rglob("TEST-*.xml"))
for path in files:
    root = ET.parse(path).getroot()
    cases = list(root.iter("testcase"))
    if root.tag == "testsuite" and root.get("tests") is not None and int(root.get("tests")) != len(cases):
        raise RuntimeError(f"Declared test count differs from real cases in {path}")
    for case in cases:
        result = classes[case.get("classname", root.get("name", "unknown"))]
        result["tests"] += 1
        for tag in ("failure", "error", "skipped"):
            entries = list(case.findall(tag))
            if entries:
                result[{"failure": "failures", "error": "errors", "skipped": "skipped"}[tag]] += 1
                if tag != "skipped":
                    failures.append({"class": case.get("classname"), "test": case.get("name"),
                        "message": "\n".join((e.get("message", "") + "\n" + (e.text or "")) for e in entries)[:1800]})
total = {key: sum(value[key] for value in classes.values()) for key in ("tests", "failures", "errors", "skipped")}
result = {**total, "scope": args.scope, "commit": os.environ.get("GITHUB_SHA"),
          "api": os.environ.get("TEST_API"), "classes": dict(sorted(classes.items())), "failed_cases": failures}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
if args.diagnostics and args.diagnostics.exists():
    # Only disposable emulator output. Do not inspect real user data or environment variables.
    pattern = re.compile(r"appearance|darkIntensity|dark_intensity|lightNavigation|navigationBarMode|navBarMode|navbarColor|navigationBarColor|mIsUserSetupComplete|mNavButtons|mTaskbar|navHeight|legacy=|focus=", re.I)
    for path in sorted(args.diagnostics.rglob("*.txt")):
        if not ("navigation" in str(path) and path.stat().st_size <= 2_000_000):
            continue
        lines = [line[:400] for line in path.read_text(encoding="utf-8", errors="replace").splitlines() if pattern.search(line)]
        if lines:
            print(f"NAVIGATION DIAGNOSTIC {path.name}\n" + "\n".join(lines[:70]))
    for path in sorted(args.diagnostics.rglob("*.png")):
        if "navigation" not in str(path) and path.name != "plain-window.png":
            continue
        with path.open("rb") as stream:
            header = stream.read(24)
        if header[:8] == b"\x89PNG\r\n\x1a\n" and len(header) == 24:
            print("PNG DIMENSIONS", path.name, *struct.unpack(">II", header[16:24]))
if not files or total["tests"] == 0:
    raise SystemExit("No executed tests were reported; an empty report is not a pass")
if total["failures"] or total["errors"]:
    raise SystemExit(1)
