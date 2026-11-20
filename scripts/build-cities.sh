#!/usr/bin/env bash
# Rebuilds server/src/main/resources/cities/india.tsv, the cities the app suggests, from GeoNames
# (https://www.geonames.org, CC BY 4.0): every Indian town of 15,000 people or more, with its state and
# the other names people write it as (Bombay for Mumbai, Bangalore for Bengaluru).
#
#   scripts/build-cities.sh
set -euo pipefail

cd "$(dirname "$0")/.."
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
curl -sSf -o "$work/cities.zip" https://download.geonames.org/export/dump/cities15000.zip
curl -sSf -o "$work/admin1.txt" https://download.geonames.org/export/dump/admin1CodesASCII.txt
unzip -oq "$work/cities.zip" -d "$work"
mkdir -p server/src/main/resources/cities
python3 -I - "$work/cities15000.txt" "$work/admin1.txt" server/src/main/resources/cities/india.tsv <<'PY'
import re, sys
cities, admin1, out = sys.argv[1:]
states = {}
for line in open(admin1, encoding="utf-8"):
    code, name, ascii_name, _ = line.rstrip("\n").split("\t")
    if code.startswith("IN."):
        states[code[3:]] = ascii_name
# Sections of a city (PPLX) and abandoned places (PPLQ) aren't somewhere you'd say you live.
skip = {"PPLX", "PPLQ", "PPLH", "PPLW"}
latin = re.compile(r"^[A-Za-z][A-Za-z .'-]{2,}$")
rows = []
for line in open(cities, encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    if f[8] != "IN" or f[7] in skip:
        continue
    name, population = f[2] if f[1] != f[2] and latin.match(f[2]) else f[1], int(f[14] or 0)
    seen = {name.lower()}
    aliases = []
    for alt in f[3].split(","):
        alt = alt.strip()
        # Airport codes (BOM) and foreign spellings in other scripts don't help anyone typing in India.
        if not latin.match(alt) or alt.isupper() or alt.lower() in seen:
            continue
        seen.add(alt.lower())
        aliases.append(alt)
    rows.append((population, name, states.get(f[10], ""), aliases))
rows.sort(key=lambda r: (-r[0], r[1]))
with open(out, "w", encoding="utf-8") as o:
    o.write("# Indian cities from GeoNames (https://www.geonames.org), CC BY 4.0. Built by scripts/build-cities.sh.\n")
    o.write("# name\tstate\tpopulation\tother names, comma-separated\n")
    for population, name, state, aliases in rows:
        o.write(f"{name}\t{state}\t{population}\t{','.join(aliases)}\n")
print(f"{len(rows)} cities")
PY
