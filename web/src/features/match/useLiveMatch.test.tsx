import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Delivery, MatchDetails } from "@/api/types";
import { useLiveMatch } from "./useLiveMatch";

class FakeSocket {
  static last: FakeSocket | null = null;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;
  constructor(readonly url: string) {
    FakeSocket.last = this;
  }
  close() {}
}

const players = (prefix: string) => [1, 2, 3].map((n) => ({ id: `${prefix}${n}`, name: `${prefix}${n}` }));
const ball = (seq: number, runsOffBat: number): Delivery => ({ seq, innings: 1, strikerId: "a1", nonStrikerId: "a2", bowlerId: "b1", runsOffBat });
const details = (deliveries: Delivery[]): MatchDetails => ({
  match: {
    id: "m1",
    createdBy: "",
    createdAt: 0,
    teamA: { name: "Strikers", players: players("a") },
    teamB: { name: "Royals", players: players("b") },
    oversPerInnings: 2,
    tossWinner: "A",
    tossDecision: "BAT",
    status: "IN_PROGRESS",
  },
  deliveries,
});

describe("useLiveMatch", () => {
  beforeEach(() => {
    vi.stubGlobal("WebSocket", FakeSocket);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(details([ball(0, 4)])))));
  });
  afterEach(() => vi.unstubAllGlobals());

  it("loads the match, then takes every update from the live feed", async () => {
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
    const { result } = renderHook(() => useLiveMatch("m1"), { wrapper });

    await waitFor(() => expect(result.current.scorecard?.innings[0]?.scoreText).toBe("4/0"));
    const socket = FakeSocket.last!;
    expect(socket.url).toContain("/public/matches/m1/live");

    act(() => {
      socket.onopen?.();
      socket.onmessage?.({ data: JSON.stringify(details([ball(0, 4), ball(1, 6)])) });
    });

    expect(result.current.scorecard?.innings[0]?.scoreText).toBe("10/0");
    expect(result.current.reconnecting).toBe(false);
  });
});
