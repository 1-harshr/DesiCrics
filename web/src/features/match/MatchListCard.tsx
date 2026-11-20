import type { Match, TeamSide } from "@/api/types";
import { LiveBadge, MatchCard, type MatchSide } from "@/designsystem/components";
import { useText } from "@/i18n";
import { dayLabel, oversOf, useMatchText } from "./text";

const other = (side: TeamSide): TeamSide => (side === "A" ? "B" : "A");

/** A match in a list, as the app's HomeMatchCard: batting order, live badge or date, and how it stands. */
export function MatchListCard({ match }: { match: Match }) {
  const text = useText();
  const { s, p } = text;
  const { statusText, resultText } = useMatchText();
  const battingFirst = match.tossDecision === "BAT" ? match.tossWinner : other(match.tossWinner);
  const order = [battingFirst, other(battingFirst)];
  const innings = match.innings ?? [];
  const current = innings[innings.length - 1]?.battingSide;

  const sides = order.map((side): MatchSide => {
    const inn = innings.find((i) => i.battingSide === side);
    const batting = match.status === "IN_PROGRESS" && side === current;
    return {
      name: (side === "A" ? match.teamA : match.teamB).name,
      score: inn ? `${inn.runs}/${inn.wickets}` : null,
      note: inn ? [s("ds_innings_overs", oversOf(inn.legalBalls)), batting ? s("ds_batting") : null].filter(Boolean).join(" · ") : match.status === "IN_PROGRESS" ? s("ds_yet_to_bat") : undefined,
    };
  });

  const label = (value: string) => <span className="label muted">{value}</span>;
  const status =
    match.status === "COMPLETED"
      ? label(dayLabel(text, match.createdAt))
      : match.status === "SCHEDULED"
        ? label(s("home_section_upcoming"))
        : innings.length === 0
          ? label(s("home_status_not_started"))
          : <LiveBadge />;

  const winner = match.result?.winner;
  return (
    <MatchCard
      to={`/m/${match.id}`}
      status={status}
      meta={p("match_overs", match.oversPerInnings, match.oversPerInnings)}
      teamA={sides[0]!}
      teamB={sides[1]!}
      footer={statusText(match)}
      result={match.result ? { winner: winner == null ? null : winner === order[0] ? "A" : "B", text: resultText(match, match.result) } : undefined}
    />
  );
}
