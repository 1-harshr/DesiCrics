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

export type TournamentFormat = "KNOCKOUT" | "ROUND_ROBIN" | "GROUPS_KNOCKOUT" | "BOX_LEAGUE" | "DOUBLE_OUT" | "BEST_OF_THREE" | "CUSTOM";
export type EntryPolicy = "ORGANISERS" | "APPROVAL" | "OPEN";
export type EntryStatus = "CONFIRMED" | "PENDING" | "INVITED" | "WAITLISTED";

export interface TournamentTeam {
  teamId: string;
  name: string;
  seed: number;
  group?: number | null;
  status?: EntryStatus;
}

export interface Organiser {
  name: string;
  role: "ORGANISER" | "CO_ORGANISER";
}

/** The public view: no account IDs, player pool, offers or squad changes. */
export interface Tournament {
  id: string;
  name: string;
  city?: string;
  format: TournamentFormat;
  oversPerInnings: number;
  groups?: number;
  advance?: number;
  startsAt?: number | null;
  teamEntry?: EntryPolicy;
  playerEntry?: EntryPolicy;
  maxTeams?: number | null;
  entryClosesAt?: number | null;
  organisers?: Organiser[];
  teams?: TournamentTeam[];
  started?: boolean;
}

export interface PointsRow {
  teamId: string;
  name: string;
  played?: number;
  won?: number;
  lost?: number;
  tied?: number;
  noResult?: number;
  runsFor?: number;
  ballsFaced?: number;
  runsAgainst?: number;
  ballsBowled?: number;
}

export interface StatLeader {
  name: string;
  team: string;
  value: number;
  profileId?: string | null;
}

export interface KeyStats {
  mostRuns?: StatLeader[];
  mostWickets?: StatLeader[];
  mostSixes?: StatLeader[];
}

export interface TournamentDetails {
  tournament: Tournament;
  fixtures?: Match[];
  tables?: PointsRow[][];
  keyStats?: KeyStats;
  championId?: string | null;
  /** Value is tenths of an MVP point. */
  playerOfTournament?: StatLeader | null;
}

export type PlayerRole = "BATTER" | "BOWLER" | "ALL_ROUNDER" | "WICKET_KEEPER";
export type TeamRole = "ADMIN" | "MEMBER";
export type FormatFilter = "ALL" | "SHORT" | "T20" | "LONG";

export interface PlayerSummary {
  profileId: string;
  name: string;
  city?: string;
  role?: PlayerRole | null;
  isPlaceholder?: boolean;
  photoUrl?: string | null;
}

export interface TeamSummary {
  id: string;
  name: string;
  city: string;
  memberCount: number;
  logoUrl?: string | null;
}

export interface TeamMember {
  player: PlayerSummary;
  role: TeamRole;
}

export interface SavedTeam {
  id: string;
  name: string;
  city: string;
  members: TeamMember[];
  logoUrl?: string | null;
}

/** Completed matches and how they ended; abandoned ones count as played only. */
export interface WinLoss {
  played?: number;
  won?: number;
  lost?: number;
  tied?: number;
}

export interface RecordAgainst {
  opponentId: string;
  opponentName: string;
  record: WinLoss;
}

export interface PublicTeamPage {
  team: SavedTeam;
  /** The latest, newest first. */
  matches?: Match[];
  /** Every completed match. */
  record?: WinLoss;
  /** Against each saved team it has played, most played first. */
  against?: RecordAgainst[];
}

export interface PlayerPage {
  player: PlayerSummary;
  teams?: TeamSummary[];
  matches?: Match[];
  followers?: number;
}

export interface TodayFeed {
  live?: Match[];
  upcoming?: Match[];
  recent?: Match[];
}

export interface SearchResults {
  teams?: TeamSummary[];
  players?: PlayerSummary[];
  matches?: Match[];
  tournaments?: TournamentSummary[];
}

export interface TournamentSummary {
  id: string;
  name: string;
  city: string;
  format: TournamentFormat;
  teams: number;
  startsAt?: number | null;
  started?: boolean;
  finished?: boolean;
}

export interface BattingStats {
  innings?: number;
  notOuts?: number;
  runs?: number;
  balls?: number;
  fours?: number;
  sixes?: number;
  highest?: number;
  highestNotOut?: boolean;
  fifties?: number;
  hundreds?: number;
}

export interface BowlingStats {
  innings?: number;
  balls?: number;
  runs?: number;
  wickets?: number;
  maidens?: number;
  bestWickets?: number;
  bestRuns?: number;
}

export interface FormEntry {
  matchId: string;
  playedAt: number;
  runs: number;
  notOut: boolean;
  wickets: number;
  batted: boolean;
}

export type Badge =
  | "FIRST_FIFTY"
  | "CENTURY"
  | "FIVE_WICKET_HAUL"
  | "RUNS_500"
  | "RUNS_1000"
  | "WICKETS_25"
  | "WICKETS_50"
  | "MATCHES_25"
  | "MATCHES_50"
  | "PLAYER_OF_THE_MATCH";

export interface CareerStats {
  profileId: string;
  matches?: number;
  won?: number;
  batting?: BattingStats;
  bowling?: BowlingStats;
  fielding?: { catches?: number; runOuts?: number; stumpings?: number };
  mvpTenths?: number;
  playerOfTheMatch?: number;
  form?: FormEntry[];
  badges?: Badge[];
}
