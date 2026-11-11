#!/usr/bin/env python3
"""Check every Sherpa-ONNX model file URL in SherpaModelCatalog.kt is alive.

Reads SherpaModelCatalog.kt, extracts each model's hfRepo and SherpaFileSpec
entries, sends an HTTP HEAD request (following redirects), and verifies that
every file returns HTTP 200 and matches or exceeds the expected byte size.
"""
import re
import sys
import urllib.request
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[1] / "app/src/main/java/com/charles/livecaptionn/speech/SherpaModelCatalog.kt"
MODEL_BLOCK = re.compile(
    r'SherpaModel\s*\((.*?)\n\s*\)',
    re.DOTALL
)
REPO_RE = re.compile(r'hfRepo\s*=\s*"([^"]+)"')
ID_RE = re.compile(r'id\s*=\s*"([^"]+)"')
SPEC_RE = re.compile(r'SherpaFileSpec\(\s*"([^"]+)"\s*,\s*([\d_]+)\s*\)')
UA = {"User-Agent": "LiveSubPro-model-link-check/1.0"}


def head(url: str):
    req = urllib.request.Request(url, method="HEAD", headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.status, int(r.headers.get("Content-Length") or 0)


def main() -> int:
    src = CATALOG.read_text(encoding="utf-8")
    blocks = MODEL_BLOCK.findall(src)
    if not blocks:
        print(f"::error::no SherpaModel entries found in {CATALOG}")
        return 2

    failures = []
    checked_files = 0
    for block in blocks:
        id_m = ID_RE.search(block)
        repo_m = REPO_RE.search(block)
        if not id_m or not repo_m:
            continue
        model_id = id_m.group(1)
        hf_repo = repo_m.group(1)
        specs = SPEC_RE.findall(block)
        for name, raw_bytes in specs:
            expected_bytes = int(raw_bytes.replace("_", ""))
            url = f"https://huggingface.co/{hf_repo}/resolve/main/{name}"
            checked_files += 1
            try:
                status, length = head(url)
            except Exception as e:  # noqa: BLE001
                failures.append(f"{model_id}/{name}: {e}")
                print(f"FAIL {model_id}/{name}: {e}")
                continue

            problems = []
            if status != 200:
                problems.append(f"HTTP {status}")
            if length < expected_bytes:
                problems.append(f"size {length} < expected {expected_bytes}")

            if problems:
                failures.append(f"{model_id}/{name}: " + ", ".join(problems))
                print(f"FAIL {model_id}/{name}: " + ", ".join(problems))
            else:
                print(f"ok   {model_id:18s} {name:52s} {length:>12,d} bytes")

    print(f"\n{checked_files} model files checked across {len(blocks)} models, {len(failures)} broken")
    for f in failures:
        print(f"::error::dead model link: {f}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
