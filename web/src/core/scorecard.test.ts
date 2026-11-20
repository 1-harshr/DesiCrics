import { describe, expect, it } from "vitest";
import type { MatchDetails } from "@/api/types";
import { scorecard } from "./index";

const players = (prefix: string) => [1, 2, 3].map((n) => ({ id: `${prefix}${n}`, name: `${prefix.toUpperCase()}${n}` }));

describe("scorecard", () => {
  it("runs the Kotlin scoring engine in the browser", () => {
    const details: MatchDetails = {
      match: {
        id: "m1",
        createdBy: "u1",
        createdAt: 0,
        teamA: { name: "Strikers", players: players("a") },
        teamB: { name: "Royals", players: players("b") },
        oversPerInnings: 2,
        tossWinner: "A",
        tossDecision: "BAT",
        status: "IN_PROGRESS",
      },
      deliveries: [
        { seq: 0, innings: 1, strikerId: "a1", nonStrikerId: "a2", bowlerId: "b1", runsOffBat: 4 },
        { seq: 1, innings: 1, strikerId: "a1", nonStrikerId: "a2", bowlerId: "b1", extra: "WIDE", extraRuns: 1 },
        {
          seq: 2,
          innings: 1,
          strikerId: "a1",
          nonStrikerId: "a2",
          bowlerId: "b1",
          wicket: { kind: "CAUGHT", playerOutId: "a1", fielderId: "b2" },
        },
      ],
    };

    const innings = scorecard(details).innings[0];

    expect(innings.scoreText).toBe("5/1");
    expect(innings.oversText).toBe("0.2");
    expect(innings.batters[0].dismissal).toEqual({ kind: "CAUGHT", bowler: "B1", fielder: "B2" });
    expect(innings.bowlers[0].wickets).toBe(1);
  });
});
