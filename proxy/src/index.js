// The app's one permanent address. Every request, WebSocket upgrades included, is passed through to
// whichever host runs the server now (BACKEND_URL in wrangler.jsonc), so moving hosts is a config
// change here instead of a new app build, and shared /join, /m and /t links keep working.
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const target = new URL(url.pathname + url.search, env.BACKEND_URL);
    // Copies the method, headers, body and the incoming request's manual redirect mode, so the
    // server's redirects reach the app as they are rather than being followed here.
    return fetch(new Request(target, request));
  },
};
