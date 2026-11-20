import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useParams } from "react-router";
import { GetAppBanner } from "@/app/GetApp";
import { api, ApiError } from "@/api/client";
import type { Match, PointsRow, StatLeader, TournamentDetails } from "@/api/types";
import {
  Avatar,
  Card,
  EmptyState,
  ErrorState,
  Hero,
  HeroChips,
  HeroIdentity,
  HeroStats,
  HeroTitle,
  Icon,
  IconButton,
  ListCard,
  ListRow,
  Loading,
  Page,
  SectionHeader,
  Tabs,
} from "@/designsystem/components";
import { MatchListCard } from "@/features/match/MatchListCard";
import { format2 } from "@/features/match/text";
import { useShare } from "@/features/match/useShare";
import { useMediaQuery, WIDE } from "@/designsystem/useMediaQuery";
import { useText, type StringKey } from "@/i18n";
import { formatLabel, groupName, stageText } from "./text";
import styles from "./TournamentPage.module.css";

/** Refreshes this often while a fixture is live, so tables and results keep up. */
const LIVE_REFRESH_MS = 30_000;


type Tab = "fixtures" | "table" | "teams" | "stats";

/** A tournament for anyone with the link: champion, fixtures, points tables, teams and top performers. */
export function TournamentPage() {
  const { tournamentId = "" } = useParams();
  const { s } = useText();
  const query = useQuery({
    queryKey: ["public-tournament", tournamentId],
    queryFn: ({ signal }) => api<TournamentDetails>(`/public/tournaments/${encodeURIComponent(tournamentId)}`, { signal }),
    refetchInterval: (q) => (q.state.data?.fixtures?.some((m) => m.status === "IN_PROGRESS") ? LIVE_REFRESH_MS : false),
  });

  if (query.isPending) {
    return (
      <>
        <Loading />
      </>
    );
  }
  if (!query.data) {
    const missing = query.error instanceof ApiError && query.error.isNotFound;
    return (
      <>
        <Page>
          <ErrorState
            missing={missing}
            title={s(missing ? "web_tournament_missing_title" : "error_tournament_title")}
            body={s(missing ? "web_not_found_body" : "error_connection_body")}
            onRetry={missing ? undefined : () => void query.refetch()}
          />
        </Page>
      </>
    );
  }
  return <TournamentView details={query.data} />;
}

function TournamentView({ details }: { details: TournamentDetails }) {
  const text = useText();
  const { s, p } = text;
  const { share, toast } = useShare();
  const wide = useMediaQuery(WIDE);
  const t = details.tournament;
  const fixtures = details.fixtures ?? [];
  const tables = details.tables ?? [];
  const confirmed = (t.teams ?? []).filter((team) => (team.status ?? "CONFIRMED") === "CONFIRMED");
  const champion = details.championId ? (t.teams ?? []).find((team) => team.teamId === details.championId)?.name : undefined;
  const organisers = (t.organisers ?? []).map((o) => o.name).filter(Boolean);
  const entriesOpen = entryOpen(details);

  const tabs: { value: Tab; label: string }[] = [
    { value: "fixtures", label: s("tournament_tab_fixtures") },
    ...(tables.length > 0 ? [{ value: "table" as const, label: s("tournament_tab_table") }] : []),
    { value: "teams", label: s("tournament_tab_teams") },
    { value: "stats", label: s("tournament_tab_stats") },
  ];
  const [tab, setTab] = useState<Tab>("fixtures");
  const shown = tabs.some((x) => x.value === tab) ? tab : "fixtures";

  const fixturesView = <Fixtures details={details} />;
  const tablesView = tables.map((table, group) => <PointsTable key={group} table={table} group={tables.length > 1 ? group : null} advance={t.advance ?? 0} />);
  const teamsView = (
    <ListCard>
      {confirmed.length === 0 ? (
        <EmptyState icon="groups" title={s("new_match_no_teams")} />
      ) : (
        confirmed.map((team) => <ListRow key={team.teamId} to={`/teams/${team.teamId}`} leading={<Avatar name={team.name} size={40} />} title={team.name} subtitle={team.group != null && (t.groups ?? 1) > 1 ? s("tournament_group", groupName(team.group)) : undefined} />)
      )}
    </ListCard>
  );
  const statsView = <Stats details={details} />;

  return (
    <>
      <Page wide>
        <Hero
          bar={
            <>
              <HeroTitle>{null}</HeroTitle>
              <IconButton icon="share" label={s("tournament_share")} frosted onClick={() => share(t.name)} />
            </>
          }
        >
          <HeroIdentity
            mark={
              <span className={styles.trophy}>
                <Icon name="emoji_events_filled" size={40} />
              </span>
            }
            eyebrow={s("tournament_eyebrow")}
            name={t.name}
          />
          <HeroChips
            chips={[
              { icon: "emoji_events", text: s(formatLabel[t.format]) },
              { icon: "schedule", text: p("match_overs", t.oversPerInnings, t.oversPerInnings) },
              ...(t.city ? [{ icon: "location_on" as const, text: t.city }] : []),
              ...(champion ? [{ icon: "emoji_events_filled" as const, text: s("tournament_champion", champion) }] : []),
            ]}
          />
          {t.started && (
            <HeroStats
              stats={[
                { value: String(confirmed.length), label: s("tournament_tab_teams") },
                { value: String(fixtures.length), label: s("tournament_tab_fixtures") },
                { value: String(fixtures.filter((m) => m.status === "COMPLETED").length), label: s("tournament_stat_played") },
              ]}
            />
          )}
        </Hero>

        {organisers.length > 0 && (
          <p className={styles.organisers}>
            <Icon name="sports" size={18} />
            {s("tournament_organisers", organisers.join(", "))}
          </p>
        )}

        {entriesOpen && (
          <Card className={styles.entry}>
            <div className="body-strong">{s("entry_card_open")}</div>
            <p className="muted">{s(entriesOpen === "both" ? "entry_card_open_both" : entriesOpen === "teams" ? "entry_card_open_teams" : "entry_card_open_players")}</p>
            <p className="caption muted">{s("web_tournament_enter_in_app")}</p>
          </Card>
        )}

        {/* Tabs on phones and tablets; from 1100px fixtures on the left, table and stats on the right. */}
        {!wide ? (
          <div className={styles.tabsOnly}>
            <Tabs label={t.name} tabs={tabs} selected={shown} onSelect={setTab} />
            <div className={styles.tabBody}>
              {shown === "fixtures" && fixturesView}
              {shown === "table" && tablesView}
              {shown === "teams" && teamsView}
              {shown === "stats" && statsView}
            </div>
          </div>
        ) : (
          <div className={styles.columns}>
            <div>{fixturesView}</div>
            <div>
              {tables.length > 0 && (
                <>
                  <SectionHeader icon="leaderboard" title={s("tournament_tab_table")} />
                  {tablesView}
                </>
              )}
              <SectionHeader icon="star" title={s("tournament_tab_stats")} />
              {statsView}
              <SectionHeader icon="groups" title={s("tournament_tab_teams")} />
              {teamsView}
            </div>
          </div>
        )}

        <GetAppBanner />
      </Page>
      {toast}
    </>
  );
}

/** Whether teams, players or both can still enter, as Tournament.takesTeams / takesPlayers in core. */
function entryOpen({ tournament: t }: TournamentDetails): "teams" | "players" | "both" | null {
  const now = Date.now();
  if (t.started || (t.entryClosesAt != null && now >= t.entryClosesAt)) return null;
  const takenPlaces = (t.teams ?? []).filter((team) => team.status !== "WAITLISTED").length;
  const full = t.format === "BEST_OF_THREE" && takenPlaces >= 2;
  const teams = !full && (t.teamEntry ?? "ORGANISERS") !== "ORGANISERS";
  const players = (t.playerEntry ?? "ORGANISERS") !== "ORGANISERS";
  return teams && players ? "both" : teams ? "teams" : players ? "players" : null;
}

/** Latest round first, since that's what's being played. */
function Fixtures({ details }: { details: TournamentDetails }) {
  const text = useText();
  const { s } = text;
  const fixtures = details.fixtures ?? [];
  if (!details.tournament.started) return <EmptyState icon="calendar_month" title={s("web_tournament_not_started")} />;
  if (fixtures.length === 0) return <EmptyState icon="calendar_month" title={s("tournament_no_fixtures")} />;

  const rounds = new Map<number, Match[]>();
  for (const match of fixtures) {
    const round = match.fixture?.round ?? 0;
    rounds.set(round, [...(rounds.get(round) ?? []), match]);
  }
  return (
    <>
      {[...rounds.entries()]
        .sort(([a], [b]) => b - a)
        .map(([round, matches]) => (
          <section key={round}>
            <SectionHeader icon="calendar_month" title={matches[0]!.fixture ? stageText(text, matches[0]!.fixture) : s("tournament_tab_fixtures")} />
            <div className={styles.stack}>
              {matches.map((match) => (
                <MatchListCard key={match.id} match={match} />
              ))}
            </div>
          </section>
        ))}
    </>
  );
}

const points = (row: PointsRow) => (row.won ?? 0) * 2 + (row.tied ?? 0) + (row.noResult ?? 0);

/** Net run rate, as PointsRow.netRunRate in core. */
function netRunRate(row: PointsRow): number {
  const scored = row.ballsFaced ? ((row.runsFor ?? 0) * 6) / row.ballsFaced : 0;
  const conceded = row.ballsBowled ? ((row.runsAgainst ?? 0) * 6) / row.ballsBowled : 0;
  return scored - conceded;
}

/** A league table, with the places that go through tinted. */
function PointsTable({ table, group, advance }: { table: PointsRow[]; group: number | null; advance: number }) {
  const { s } = useText();
  return (
    <section className={styles.table}>
      {group != null && <SectionHeader icon="leaderboard" title={s("tournament_group", groupName(group))} />}
      {table.length === 0 ? (
        <EmptyState icon="leaderboard" title={s("tournament_no_fixtures")} />
      ) : (
        <div className={styles.tableCard}>
          <table>
            <thead>
              <tr>
                <th aria-hidden />
                <th>{s("table_team")}</th>
                <th>{s("table_played")}</th>
                <th>{s("table_won")}</th>
                <th>{s("table_lost")}</th>
                <th>{s("table_points")}</th>
                <th>{s("table_nrr")}</th>
              </tr>
            </thead>
            <tbody>
              {table.map((row, index) => {
                const nrr = netRunRate(row);
                return (
                  <tr key={row.teamId} className={index < advance ? styles.qualifies : undefined}>
                    <td>
                      <span className={[styles.rank, index === 0 && styles.rankFirst, index > 0 && index < 3 && styles.rankPodium].filter(Boolean).join(" ")}>{index + 1}</span>
                    </td>
                    <td>{row.name}</td>
                    <td>{row.played ?? 0}</td>
                    <td>{row.won ?? 0}</td>
                    <td>{row.lost ?? 0}</td>
                    <td className={styles.strong}>{points(row)}</td>
                    <td>{(nrr >= 0 ? "+" : "-") + format2(Math.abs(nrr))}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {advance > 0 && table.length > advance && (
        <p className={styles.qualifyNote}>
          <span className={styles.qualifyDot} />
          {s("table_qualify")}
        </p>
      )}
    </section>
  );
}

/** "12.5" from tenths of a point, as the app's mvpText. */
const mvpText = (tenths: number) => `${Math.trunc(tenths / 10)}.${Math.abs(tenths % 10)}`;

function Stats({ details }: { details: TournamentDetails }) {
  const { s } = useText();
  const stats = details.keyStats ?? {};
  const lists: [StringKey, StatLeader[]][] = [
    ["tournament_most_runs", stats.mostRuns ?? []],
    ["tournament_most_wickets", stats.mostWickets ?? []],
    ["tournament_most_sixes", stats.mostSixes ?? []],
  ];
  const best = details.playerOfTournament;
  if (!best && lists.every(([, leaders]) => leaders.length === 0)) return <EmptyState icon="leaderboard" title={s("tournament_no_stats")} />;

  return (
    <div className={styles.stack}>
      {best && (
        <div className={styles.best}>
          <span className={styles.bestMark}>
            <Icon name="star" />
          </span>
          <div className={styles.bestText}>
            <div className={styles.bestLabel}>{s("tournament_player_of_tournament_title")}</div>
            <div className="title-md">{best.name}</div>
            {best.team && <div className="caption muted">{best.team}</div>}
          </div>
          <div className={styles.bestValue}>
            <div className="score-md">{mvpText(best.value)}</div>
            <div className="caption muted">{s("stats_mvp_points")}</div>
          </div>
        </div>
      )}
      {lists
        .filter(([, leaders]) => leaders.length > 0)
        .map(([title, leaders]) => (
          <section key={title}>
            <h3 className={styles.statTitle}>{s(title)}</h3>
            <ListCard>
              {leaders.map((leader, index) => (
                <ListRow
                  key={`${leader.name}-${index}`}
                  to={leader.profileId ? `/players/${leader.profileId}` : undefined}
                  leading={<Avatar name={leader.name} size={40} />}
                  title={leader.name}
                  subtitle={leader.team}
                  trailing={<span className="score-md">{leader.value}</span>}
                />
              ))}
            </ListCard>
          </section>
        ))}
    </div>
  );
}
