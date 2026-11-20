import { afterEach, expect, it, vi } from "vitest";

afterEach(() => {
  vi.unstubAllEnvs();
  window.history.replaceState(null, "", "/");
});

it("opens a shared match link under the GitHub Pages project path", async () => {
  vi.stubEnv("VITE_HASH_ROUTING", "true");
  window.history.replaceState(null, "", "/DesiCrics/#/m/match-1");

  const { router } = await import("./router");
  try {
    expect(router.state.matches.at(-1)?.params.matchId).toBe("match-1");
    await router.navigate("/search");
    expect(window.location.pathname + window.location.hash).toBe("/DesiCrics/#/search");
  } finally {
    router.dispose();
  }
});
