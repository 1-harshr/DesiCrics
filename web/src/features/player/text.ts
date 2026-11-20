import type { Badge, FormatFilter, PlayerRole } from "@/api/types";
import type { StringKey } from "@/i18n";

export const playerRoleLabel: Record<PlayerRole, StringKey> = {
  BATTER: "player_role_batter",
  BOWLER: "player_role_bowler",
  ALL_ROUNDER: "player_role_all_rounder",
  WICKET_KEEPER: "player_role_wicket_keeper",
};

export const formatFilterLabel: Record<FormatFilter, StringKey> = {
  ALL: "stats_format_all",
  SHORT: "stats_format_short",
  T20: "stats_format_t20",
  LONG: "stats_format_long",
};

export const badgeLabel: Record<Badge, StringKey> = {
  FIRST_FIFTY: "badge_first_fifty",
  CENTURY: "badge_century",
  FIVE_WICKET_HAUL: "badge_five_wickets",
  RUNS_500: "badge_runs_500",
  RUNS_1000: "badge_runs_1000",
  WICKETS_25: "badge_wickets_25",
  WICKETS_50: "badge_wickets_50",
  MATCHES_25: "badge_matches_25",
  MATCHES_50: "badge_matches_50",
  PLAYER_OF_THE_MATCH: "badge_player_of_the_match",
};
