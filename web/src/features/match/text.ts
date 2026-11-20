// Match wording, as the app's ui/text/ScoringText.kt and ballFor in MatchScreen.kt: the same strings and rules.
import { useMemo } from "react";
import type { AbandonReason, Delivery, Match, MatchResult, TeamSide } from "@/api/types";
import type { BallView, BatterView } from "@/core";
import type { BallKind } from "@/designsystem/components";
import { useText, type StringKey } from "@/i18n";

const teamName = (match: Match, side: TeamSide) => (side === "A" ? match.teamA : match.teamB).name;
const other = (side: TeamSide): TeamSide => (side === "A" ? "B" : "A");

const reasonLabel: Record<AbandonReason, StringKey> = {
  RAIN: "end_reason_rain",
  BAD_LIGHT: "end_reason_light",
  GROUND: "end_reason_ground",
  OTHER: "end_reason_other",
};

/** Overs from balls, as scorers write them: 20, or 16.4 after rain. */
export const oversOf = (balls: number) => (balls % 6 === 0 ? String(balls / 6) : `${Math.floor(balls / 6)}.${balls % 6}`);

/** Two decimals, as the app's format2(). */
export const format2 = (value: number) => value.toFixed(2);

/** "Sat 23 Nov, 4:30 pm" in the viewer's locale and time zone. */
export function formatDayAndTime(epochMillis: number): string {
  return new Intl.DateTimeFormat(undefined, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }).format(epochMillis);
}

export function useMatchText() {
  const text = useText();
  return useMemo(() => {
    const { s, p } = text;

    /** "Strikers won by 24 runs", "Match tied", "No result · Rain", "Royals won by walkover". */
    const resultText = (match: Match, result: MatchResult): string => {
      switch (result.ending) {
        case "ABANDONED":
          return result.reason && result.reason !== "OTHER"
            ? s("match_result_no_result_reason", s(reasonLabel[result.reason]))
            : s("match_result_no_result");
        case "CONCEDED":
          return s("match_result_conceded", teamName(match, result.winner!), teamName(match, other(result.winner!)));
        case "WALKOVER":
          return s("match_result_walkover", teamName(match, result.winner!));
      }
      if (!result.winner) return s("match_result_tie");
      const margin = result.margin ?? 0;
      const key = result.marginUnit === "WICKETS" ? "match_result_won_by_wickets" : "match_result_won_by_runs";
      return p(key, margin, teamName(match, result.winner), margin);
    };

    /** The result, the chase, the start time of a fixture, or the toss: one line on where a match stands. */
    const statusText = (match: Match): string => {
      if (match.result) return resultText(match, match.result);
      if (match.status === "SCHEDULED") return match.scheduledAt ? formatDayAndTime(match.scheduledAt) : s("fixture_to_play");
      const [first, chase] = match.innings ?? [];
      if (first && chase) {
        const needed = Math.max(0, (chase.target ?? first.runs + 1) - chase.runs);
        const balls = Math.max(0, (chase.maxBalls ?? match.oversPerInnings * 6) - chase.legalBalls);
        return p("match_need_runs", balls, teamName(match, chase.battingSide), needed, balls);
      }
      return s(match.tossDecision === "BAT" ? "match_toss_chose_bat" : "match_toss_chose_bowl", teamName(match, match.tossWinner));
    };

    /** "c Rohit b Bumrah", "run out (Jadeja)", "not out", "retired hurt". */
    const dismissalText = (card: Pick<BatterView, "dismissal" | "retired">): string => {
      if (card.retired === "HURT") return s("scorecard_retired_hurt");
      if (card.retired === "OUT") return s("scorecard_retired_out");
      if (card.retired === "NOT_OUT") return s("scorecard_retired_not_out");
      const d = card.dismissal;
      if (!d) return s("scorecard_not_out");
      const bowler = d.bowler ?? "";
      switch (d.kind) {
        case "BOWLED":
          return s("scorecard_out_bowled", bowler);
        case "CAUGHT":
          if (!d.fielder) return s("scorecard_out_caught_no_fielder", bowler);
          return d.fielder === d.bowler ? s("scorecard_out_caught_and_bowled", bowler) : s("scorecard_out_caught", d.fielder, bowler);
        case "LBW":
          return s("scorecard_out_lbw", bowler);
        case "RUN_OUT":
          return d.fielder ? s("scorecard_out_run_out", d.fielder) : s("scorecard_out_run_out_no_fielder");
        case "STUMPED":
          return d.fielder ? s("scorecard_out_stumped", d.fielder, bowler) : s("scorecard_out_stumped_no_fielder", bowler);
        case "HIT_WICKET":
          return s("scorecard_out_hit_wicket", bowler);
        case "OBSTRUCTING_FIELD":
          return s("scorecard_out_obstructing");
        case "HIT_BALL_TWICE":
          return s("scorecard_out_hit_twice");
        case "OTHER":
          return s("scorecard_out_other");
      }
    };

    /** How a ball looks in an over strip: "4", "W", "2wd", "nb", "1lb", "•". */
    const ballFor = (d: Delivery): { kind: BallKind; text: string } => {
      if (d.wicket) return { kind: "Wicket", text: s("scoring_ball_wicket") };
      const bat = d.runsOffBat ?? 0;
      const extraRuns = d.extraRuns ?? 0;
      if (!d.extra) {
        if (bat === 0) return { kind: "Dot", text: "•" };
        if (bat === 4) return { kind: "Four", text: "4" };
        if (bat === 6) return { kind: "Six", text: "6" };
        return { kind: "Runs", text: String(bat) };
      }
      const [key, runs]: [StringKey, number] = {
        WIDE: ["scoring_ball_wide", extraRuns],
        NO_BALL: ["scoring_ball_no_ball", bat + extraRuns],
        BYE: ["scoring_ball_bye", extraRuns],
        LEG_BYE: ["scoring_ball_leg_bye", extraRuns],
      }[d.extra] as [StringKey, number];
      const short = s(key);
      const plain = (d.extra === "WIDE" || d.extra === "NO_BALL") && runs <= (d.extraPenalty ?? 1);
      return { kind: "Extra", text: plain ? short : s("scoring_ball_extra_with_runs", runs, short) };
    };

    /** "Dev to Rohan, FOUR", "Dev to Rohan, OUT! Rohan c Esha b Dev". */
    const commentaryText = (ball: BallView): string => {
      const d = ball.delivery;
      const bat = d.runsOffBat ?? 0;
      const extraRuns = d.extraRuns ?? 0;
      const penalty = d.extraPenalty ?? 1;
      const wideOrNoBall = d.extra === "WIDE" || d.extra === "NO_BALL";
      let event: string;
      if (d.wicket) {
        const kind = d.wicket.kind;
        const creditedToBowler = ["BOWLED", "CAUGHT", "LBW", "STUMPED", "HIT_WICKET"].includes(kind);
        const dismissal = dismissalText({ dismissal: { kind, bowler: creditedToBowler ? ball.bowler : null, fielder: ball.fielder }, retired: null });
        const runsRun = bat + extraRuns - (wideOrNoBall ? penalty : 0);
        const out = ball.playerOut ?? "";
        event = runsRun > 0 ? p("commentary_out_after_runs", runsRun, runsRun, out, dismissal) : s("commentary_out", out, dismissal);
      } else if (d.extra === "WIDE") {
        event = extraRuns <= penalty ? s("commentary_wide") : p("commentary_wide_runs", extraRuns, extraRuns);
      } else if (d.extra === "NO_BALL") {
        event = bat === 0 ? s("commentary_no_ball") : p("commentary_no_ball_runs", bat, bat);
      } else if (d.extra === "BYE") {
        event = p("commentary_byes", extraRuns, extraRuns);
      } else if (d.extra === "LEG_BYE") {
        event = p("commentary_leg_byes", extraRuns, extraRuns);
      } else if (bat === 4) {
        event = s("commentary_four");
      } else if (bat === 6) {
        event = s("commentary_six");
      } else if (bat === 0) {
        event = s("commentary_dot");
      } else {
        event = p("commentary_runs", bat, bat);
      }
      return s("commentary_line", ball.bowler, ball.striker, event);
    };

    return { resultText, statusText, dismissalText, ballFor, commentaryText };
  }, [text]);
}
