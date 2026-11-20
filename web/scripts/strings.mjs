// Copies the Android app's English strings into the web app's i18next catalogue, so both apps use the
// same words and one translation covers both. `%1$s` becomes `{{1}}`; plurals become `key_one` / `key_other`.
// Run with `npm run strings` after changing strings.xml. Web-only text lives in src/i18n/web.json.
import { readFileSync, writeFileSync } from "node:fs";

const sources = [
  "../../app/designsystem/src/commonMain/composeResources/values/strings.xml",
  "../../app/shared/src/commonMain/composeResources/values/strings.xml",
].map((path) => new URL(path, import.meta.url).pathname);
const target = new URL("../src/i18n/app.generated.json", import.meta.url).pathname;

const text = (raw) =>
  raw
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/%(\d+)\$[sd]/g, "{{$1}}")
    .replace(/%[sd]/g, "{{1}}")
    .replace(/\\n/g, "\n")
    .replace(/\\(["'@?])/g, "$1")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, "&")
    .trim();

const out = {};
for (const source of sources) {
  const xml = readFileSync(source, "utf8").replace(/<!--[\s\S]*?-->/g, "");
  for (const [, name, value] of xml.matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) out[name] = text(value);
  for (const [, name, items] of xml.matchAll(/<plurals name="([^"]+)"[^>]*>([\s\S]*?)<\/plurals>/g)) {
    for (const [, quantity, value] of items.matchAll(/<item quantity="([a-z]+)">([\s\S]*?)<\/item>/g)) out[`${name}_${quantity}`] = text(value);
  }
  for (const [, name, items] of xml.matchAll(/<string-array name="([^"]+)"[^>]*>([\s\S]*?)<\/string-array>/g)) {
    out[name] = [...items.matchAll(/<item>([\s\S]*?)<\/item>/g)].map(([, value]) => text(value));
  }
}

writeFileSync(target, JSON.stringify(out, null, 2) + "\n");
console.log(`${Object.keys(out).length} strings`);
