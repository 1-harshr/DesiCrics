// What WebCore (core/src/jsMain/.../web/WebCore.kt) returns. Keep in step with its view classes.
import type { Delivery, DismissalKind, MatchResult, Player, TeamSide } from "@/api/types";

export interface ScorecardView {
  innings: InningsView[];
  isComplete: boolean;
  result: MatchResult | null;
  scoreSummary: string;
}

export interface InningsView {
  number: number;
  battingSide: TeamSide;
  battingTeam: string;
  bowlingTeam: string;
  runs: number;
  wickets: number;
  legalBalls: number;
  maxBalls: number;
  scoreText: string;
  oversText: string;
  runRate: number;
  target: number | null;
  runsNeeded: number | null;
  ballsRemaining: number;
  requiredRate: number | null;
  isComplete: boolean;
  isFreeHit: boolean;
  isPowerplay: boolean;
  declared: boolean;
  isRevised: boolean;
  extras: { wides: number; noBalls: number; byes: number; legByes: number; penalties: number; total: number };
  batters: BatterView[];
  bowlers: BowlerView[];
  yetToBat: Player[];
  currentOver: Delivery[];
  strikerId: string | null;
  nonStrikerId: string | null;
  bowlerId: string | null;
  overs: OverView[];
}

export interface BatterView {
  player: Player;
  runs: number;
  balls: number;
  fours: number;
  sixes: number;
  strikeRate: number;
  isOut: boolean;
  dismissal: { kind: DismissalKind; bowler: string | null; fielder: string | null } | null;
  retired: "HURT" | "NOT_OUT" | "OUT" | null;
}

export interface BowlerView {
  player: Player;
  oversText: string;
  legalBalls: number;
  maidens: number;
  runs: number;
  wickets: number;
  economy: number;
}

export interface OverView {
  number: number;
  bowler: string;
  runs: number;
  wickets: number;
  scoreAfter: string;
  balls: BallView[];
}

export interface BallView {
  ballNumber: string;
  delivery: Delivery;
  striker: string;
  bowler: string;
  playerOut: string | null;
  fielder: string | null;
}
