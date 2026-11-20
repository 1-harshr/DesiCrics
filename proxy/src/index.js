// The app's one permanent address. Page loads from a browser get the web app (web/, built into
// ../web/dist and served as static assets); every other request, WebSocket upgrades included, is passed
// through to whichever host runs the server now (BACKEND_URL in wrangler.jsonc). Moving hosts is a config
// change here instead of a new app build, and shared /join, /m and /t links keep working.

// Server pages a browser opens directly, which stay with the server.
const SERVER_PAGES = [/^\/overlay\//, /^\/m\/[^/]+\/overlay\.json$/, /^\/\.well-known\//, /^\/images\//, /^\/health$/];

/** A browser loading a page or one of the web app's files, rather than the app or the web app calling the API. */
function isWebApp(request, url) {
  if (request.method !== "GET" && request.method !== "HEAD") return false;
  if (request.headers.get("Upgrade")) return false;
  if (SERVER_PAGES.some((page) => page.test(url.pathname))) return false;
  if (/^\/(assets|fonts)\//.test(url.pathname) || url.pathname === "/icon.svg") return true;
  return request.headers.get("Sec-Fetch-Mode") === "navigate" || (request.headers.get("Accept") ?? "").includes("text/html");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    // Unknown paths fall back to index.html, so the web app's router can show them.
    if (isWebApp(request, url)) return env.ASSETS.fetch(request);
    const target = new URL(url.pathname + url.search, env.BACKEND_URL);
    // Copies the method, headers, body and the incoming request's manual redirect mode, so the
    // server's redirects reach the app as they are rather than being followed here.
    return fetch(new Request(target, request));
  },
};
