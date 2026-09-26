#!/usr/bin/env bash
# Hotspots: churn x size x clones per source file (code-atlas, design D3).
# No new dependencies: git + wc + optional npx jscpd (already part of the review flow).
# Usage: scripts/hotspots.sh   (run from the repo root)
set -euo pipefail
SRC=src/main
PKG=fr/raconteur/simpleskinswapper
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# Churn: lines ever added+deleted per file (whole history)
git log --format= --numstat -- "$SRC" | awk -F'\t' 'NF==3 {churn[$3]+=$1+$2} END {for (f in churn) print churn[f]"\t"f}' > "$TMP/churn"

# Size: current line count
find "$SRC" \( -name '*.kt' -o -name '*.java' \) -print0 | xargs -0 wc -l | sed 's/^ *//' | grep -v ' total$' | awk '{print $1"\t"$2}' > "$TMP/size"

# Clones: duplicated lines per file from jscpd (skipped silently if npx unavailable).
# jscpd names files relative to the scanned dir; churn/size use repo paths -> keys are
# normalized to "fr/raconteur/simpleskinswapper/..." on both sides before joining.
CLONES="$TMP/clones"; : > "$CLONES"
if command -v npx >/dev/null 2>&1; then
  if npx --yes jscpd "$SRC/kotlin" --min-tokens 60 --reporters json --output "$TMP/jscpd" --silent >/dev/null 2>&1; then
    python3 - "$TMP/jscpd/jscpd-report.json" > "$CLONES" <<'PY'
import json, sys, collections
d = json.load(open(sys.argv[1]))
per = collections.Counter()
for c in d.get("duplicates", []):
    per[c["firstFile"]["name"]] += c["lines"]
    per[c["secondFile"]["name"]] += c["lines"]
for f, n in sorted(per.items()):
    print(f"{n}\t{f}")
PY
  fi
fi

python3 - "$TMP/churn" "$TMP/size" "$CLONES" "$PKG" <<'PY'
import sys

def load(p, strip):
    d = {}
    for line in open(p):
        line = line.rstrip("\n")
        if not line:
            continue
        n, f = line.split("\t", 1)
        d[f.replace(strip, "")] = int(n)
    return d

pkg = sys.argv[4]
churn = load(sys.argv[1], "src/main/kotlin/")
size = load(sys.argv[2], "src/main/kotlin/")
clones = load(sys.argv[3], "")
rows = [(churn.get(f, 0), size.get(f, 0), clones.get(f, 0), f)
        for f in set(churn) | set(size) if size.get(f, 0) > 0]
rows.sort(reverse=True)
print(f"{'churn':>7} {'size':>6} {'clones':>7}  file (existing files only)")
for c, s, k, f in rows[:20]:
    print(f"{c:>7} {s:>6} {k:>7}  {f.replace(pkg + '/', '')}")
PY
