import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useParams } from "react-router";
import { api, ApiError } from "@/api/client";
import type { CareerStats, FormatFilter, PlayerPage as Page_ } from "@/api/types";
import {
  Avatar,
  CardGrid,
  EmptyState,
  ErrorState,
  FilterRow,
  Hero,
  HeroChips,
  HeroIdentity,
  HeroStats,
  HeroTitle,
  IconButton,
  ListCard,
  ListRow,
  Loading,
  Page,
  SectionHeader,
  StatGrid,
  Tag,
} from "@/designsystem/components";
import { MatchListCard } from "@/features/match/MatchListCard";
import { format2, oversOf } from "@/features/match/text";
import { useShare } from "@/features/match/useShare";
import { useText } from "@/i18n";
import styles from "./PlayerPage.module.css";
import { badgeLabel, formatFilterLabel, playerRoleLabel } from "./text";

const FORMATS: FormatFilter[] = ["ALL", "SHORT", "T20", "LONG"];

/** A player: who they are, their numbers by match length, their teams and their latest matches. */
export function PlayerPage() {
  const { profileId = "" } = useParams();
  const { s } = useText();
  const query = useQuery({
    queryKey: ["public-player", profileId],
    queryFn: ({ signal }) => api<Page_>(`/public/players/${encodeURIComponent(profileId)}`, { signal }),
  });
  if (query.isPending) return <Loading />;
  if (!query.data) {
    const missing = query.error instanceof ApiError && query.error.isNotFound;
    return (
      <Page>
        <ErrorState
          missing={missing}
          title={s(missing ? "web_player_missing_title" : "error_player_title")}
          body={s(missing ? "web_not_found_body" : "error_connection_body")}
          onRetry={missing ? undefined : () => void query.refetch()}
        />
      </Page>
    );
  }
  return <PlayerView page={query.data} />;
}

function PlayerView({ page }: { page: Page_ }) {
  const { s, p } = useText();
  const { share, toast } = useShare();
  const [format, setFormat] = useState<FormatFilter>("ALL");
  // All time, then this season and the two before, as the app offers.
  const year = new Date().getFullYear();
  const seasons = ["all", String(year), String(year - 1), String(year - 2)];
  const [season, setSeason] = useState("all");
  const player = page.player;
  const stats = useQuery({
    queryKey: ["public-player-stats", player.profileId, format, season],
    queryFn: ({ signal }) =>
      api<CareerStats>(`/public/players/${encodeURIComponent(player.profileId)}/stats?format=${format}${season === "all" ? "" : `&season=${season}`}`, { signal }),
    placeholderData: (previous) => previous,
  });
  const career = stats.data;
  const teams = page.teams ?? [];
  const matches = page.matches ?? [];

  return (
    <Page wide>
      <Hero
        bar={
          <>
            <HeroTitle>{null}</HeroTitle>
            <IconButton icon="share" label={s("web_player_share")} frosted onClick={() => share(player.name)} />
          </>
        }
      >
        <HeroIdentity
          mark={<Avatar name={player.name} photoUrl={player.photoUrl} size={88} ringed />}
          eyebrow={s("player_eyebrow")}
          name={player.name}
          subtitle={page.followers ? p("player_followers", page.followers, page.followers) : undefined}
        />
        <HeroChips
          chips={[
            ...(player.role ? [{ icon: "sports_cricket" as const, text: s(playerRoleLabel[player.role]) }] : []),
            ...(player.city ? [{ icon: "location_on" as const, text: player.city }] : []),
          ]}
        />
        {career && format === "ALL" && season === "all" && (career.matches ?? 0) > 0 && (
          <HeroStats
            stats={[
              { value: String(career.matches ?? 0), label: s("stats_matches") },
              { value: String(career.batting?.runs ?? 0), label: s("stats_runs") },
              { value: String(career.bowling?.wickets ?? 0), label: s("stats_wickets") },
            ]}
          />
        )}
      </Hero>

      <div className={styles.columns}>
        <section>
          <SectionHeader icon="insights" title={s("profile_tab_stats")} />
          <div className={styles.filters}>
            <FilterRow options={seasons.map((y) => ({ value: y, label: y === "all" ? s("stats_season_all") : y }))} selected={season} onSelect={setSeason} />
            <FilterRow options={FORMATS.map((f) => ({ value: f, label: s(formatFilterLabel[f]) }))} selected={format} onSelect={setFormat} />
          </div>
          <div className={styles.stats}>
            {!career ? (
              stats.isPending ? <Loading /> : <ErrorState compact title={s("web_player_stats_failed")} body={s("error_connection_body")} onRetry={() => void stats.refetch()} />
            ) : (career.matches ?? 0) === 0 ? (
              <EmptyState icon="insights" title={s("feed_empty")} />
            ) : (
              <CareerView career={career} />
            )}
          </div>
        </section>
        <section>
          {teams.length > 0 && (
            <>
              <SectionHeader icon="shield" title={s("player_section_teams")} />
              <ListCard>
                {teams.map((t) => (
                  <ListRow key={t.id} to={`/teams/${t.id}`} leading={<Avatar name={t.name} photoUrl={t.logoUrl} />} title={t.name} subtitle={[t.city, p("teams_member_count", t.memberCount, t.memberCount)].filter(Boolean).join(" · ")} />
                ))}
              </ListCard>
            </>
          )}
          <SectionHeader icon="history" title={s("player_section_matches")} />
          {matches.length === 0 ? (
            <EmptyState icon="sports_cricket" title={s("feed_empty")} />
          ) : (
            <CardGrid>
              {matches.map((m) => (
                <MatchListCard key={m.id} match={m} />
              ))}
            </CardGrid>
          )}
        </section>
      </div>
      {toast}
    </Page>
  );
}

const or = (value: number | null | undefined, fallback: string) => (value == null ? fallback : String(value));

function CareerView({ career }: { career: CareerStats }) {
  const { s, p } = useText();
  const bat = career.batting ?? {};
  const bowl = career.bowling ?? {};
  const dash = s("common_empty_value");
  const outs = (bat.innings ?? 0) - (bat.notOuts ?? 0);
  const average = outs > 0 ? format2((bat.runs ?? 0) / outs) : dash;
  const strikeRate = bat.balls ? format2(((bat.runs ?? 0) * 100) / bat.balls) : dash;
  const economy = bowl.balls ? format2(((bowl.runs ?? 0) * 6) / bowl.balls) : dash;
  const form = career.form ?? [];
  const best = Math.max(1, ...form.map((f) => f.runs));

  return (
    <div className={styles.career}>
      <h3 className={styles.statTitle}>{s("stats_batting")}</h3>
      <StatGrid
        featured={2}
        stats={[
          { value: or(bat.runs, "0"), label: s("stats_runs") },
          { value: average, label: s("stats_average") },
          { value: or(bat.innings, "0"), label: s("stats_innings") },
          { value: bat.innings ? `${bat.highest ?? 0}${bat.highestNotOut ? "*" : ""}` : dash, label: s("stats_highest") },
          { value: strikeRate, label: s("stats_strike_rate") },
          { value: or(bat.fifties, "0"), label: s("stats_fifties") },
          { value: or(bat.hundreds, "0"), label: s("stats_hundreds") },
          { value: or(bat.fours, "0"), label: s("stats_fours") },
          { value: or(bat.sixes, "0"), label: s("stats_sixes") },
        ]}
      />
      <h3 className={styles.statTitle}>{s("stats_bowling")}</h3>
      <StatGrid
        featured={2}
        stats={[
          { value: or(bowl.wickets, "0"), label: s("stats_wickets") },
          { value: economy, label: s("stats_economy") },
          { value: bowl.balls ? oversOf(bowl.balls) : "0", label: s("stats_overs") },
          { value: bowl.bestWickets ? `${bowl.bestWickets}/${bowl.bestRuns ?? 0}` : dash, label: s("stats_best") },
        ]}
      />
      <h3 className={styles.statTitle}>{s("stats_fielding")}</h3>
      <StatGrid stats={[{ value: or(career.fielding?.catches, "0"), label: s("stats_catches") }]} />

      {form.length > 0 && (
        <>
          <h3 className={styles.statTitle}>{p("stats_form", form.length, form.length)}</h3>
          <div className={styles.form} role="img" aria-label={s("stats_form_description", form.length)}>
            {form.map((f) => (
              <div key={f.matchId} className={styles.formBar}>
                <span className={styles.formValue}>{f.batted ? `${f.runs}${f.notOut ? "*" : ""}` : dash}</span>
                <span className={styles.formFill} style={{ height: `${Math.max(4, (f.runs / best) * 100)}%` }} />
              </div>
            ))}
          </div>
        </>
      )}

      {(career.badges ?? []).length > 0 && (
        <div className={styles.badges}>
          {career.badges!.map((b) => (
            <Tag key={b} icon="military_tech">
              {s(badgeLabel[b])}
            </Tag>
          ))}
        </div>
      )}
    </div>
  );
}
