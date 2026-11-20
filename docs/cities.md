# Cities · One spelling for each city

**Goal:** "Mumbai", "Bombay" and "mumbai " are the same place, so city pages, rankings, the community and "Open to join" should treat them as one.

## How it works

- **Still free text.** Any city can be saved, so small towns aren't blocked.
- **Suggestions as you type.** Every city field (profile, new team, new tournament, Community and "Open to join") shows matching cities once two letters are in: names that start with what's typed first, then other names ("Bomb" → Mumbai), bigger cities first. Each shows its state, so the two Aurangabads can be told apart. Picking one fills in its standard spelling.
- **One spelling on the server.** Whatever the app sends, the server saves the standard name of a city it knows ("bangalore" → "Bengaluru", "Gurgaon" → "Gurugram") and tidies the rest (extra spaces). City searches go through the same lookup, so "Bombay" finds Mumbai's tournaments.
- **Old data.** At startup the server rewrites cities saved before this to their standard names. Once everything is standard this only reads the distinct cities.

## The list

`server/src/main/resources/cities/india.tsv`: every Indian town of 15,000 people or more from [GeoNames](https://www.geonames.org) (CC BY 4.0), with its state, population and other names. Airport codes and names in other scripts are left out. `scripts/build-cities.sh` rebuilds it.

A city's own name always wins over another city's other name, so a town that shares a name with a big city's old name keeps its own.

- `GET /cities?q=` → up to 8 `CitySuggestion(name, state)`.
- Suggestions come from our server, not a third-party API: no per-request cost, no key, and they work wherever the app does.

## Not yet

- Places inside a city (neighbourhoods, grounds). If we want "Shivaji Park, Dadar" we'd look at Google Places then.
- Cities outside India.
