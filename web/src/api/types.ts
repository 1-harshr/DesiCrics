// The API's JSON, mirroring the @Serializable classes in core/src/commonMain (model/ and api/).
// Only what the web app reads so far; add fields as screens need them.

export type TeamSide = "A" | "B";
export type TossDecision = "BAT" | "BOWL";
export type MatchStatus = "SETUP" | "SCHEDULED" | "IN_PROGRESS" | "COMPLETED";
export type ExtraType = "WIDE" | "NO_BALL" | "BYE" | "LEG_BYE";
export type DismissalKind =
  | "BOWLED"
  | "CAUGHT"
  | "LBW"
  | "RUN_OUT"
  | "STUMPED"
  | "HIT_WICKET"
  | "OBSTRUCTING_FIELD"
  | "HIT_BALL_TWICE"
  | "OTHER";
export type FieldZone = "THIRD_MAN" | "POINT" | "COVER" | "LONG_OFF" | "LONG_ON" | "MID_WICKET" | "SQUARE_LEG" | "FINE_LEG";
export type MarginUnit = "RUNS" | "WICKETS";
export type MatchEnding = "ABANDONED" | "CONCEDED" | "WALKOVER";
export type AbandonReason = "RAIN" | "BAD_LIGHT" | "GROUND" | "OTHER";
export type Stage = "LEAGUE" | "ROUND" | "QUARTERFINAL" | "SEMIFINAL" | "FINAL" | "MATCH";

export interface Player {
  id: string;
  name: string;
  profileId?: string | null;
}

export interface Team {
  name: string;
  players: Player[];
  teamId?: string | null;
  captainId?: string | null;
  keeperId?: string | null;
}

export interface MatchResult {
  winner: TeamSide | null;
  margin?: number;
  marginUnit?: MarginUnit | null;
  ending?: MatchEnding | null;
  reason?: AbandonReason | null;
}

export interface InningsScore {
  battingSide: TeamSide;
  runs: number;
  wickets: number;
  legalBalls: number;
  target?: number | null;
  maxBalls?: number | null;
  allOut?: boolean;
}

export interface FixtureInfo {
  tournamentId: string;
  stage: Stage;
  round: number;
  number: number;
  group?: number | null;
}

export interface Match {
  id: string;
  createdBy: string;
  createdAt: number;
  teamA: Team;
  teamB: Team;
  oversPerInnings: number;
  tossWinner: TeamSide;
  tossDecision: TossDecision;
  status?: MatchStatus;
  scoreSummary?: string;
  result?: MatchResult | null;
  innings?: InningsScore[];
  scorerUid?: string;
  scheduledAt?: number | null;
  fixture?: FixtureInfo | null;
  venue?: string;
}

export interface Wicket {
  kind: DismissalKind;
  playerOutId: string;
  fielderId?: string | null;
}

export interface Delivery {
  seq: number;
  innings: number;
  strikerId: string;
  nonStrikerId: string | null;
  bowlerId: string;
  runsOffBat?: number;
  extra?: ExtraType | null;
  extraRuns?: number;
  wicket?: Wicket | null;
  timestamp?: number;
  extraPenalty?: number;
  countsAsBall?: boolean;
  zone?: FieldZone | null;
}

/** A match with every ball and event so far: everything a scorecard needs. Events are passed through to :core. */
export interface MatchDetails {
  match: Match;
  deliveries: Delivery[];
  events?: unknown[];
}

export interface ApiErrorBody {
  message: string;
}
