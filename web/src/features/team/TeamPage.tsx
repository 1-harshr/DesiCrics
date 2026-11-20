import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router";
import { api, ApiError } from "@/api/client";
import type { Match, PublicTeamPage } from "@/api/types";
import { Avatar, CardGrid, EmptyState, ErrorState, Hero, HeroChips, HeroIdentity, HeroStats, HeroTitle, IconButton, ListCard, ListRow, Loading, Page, SectionHeader, Tag } from "@/designsystem/components";
import { MatchListCard } from "@/features/match/MatchListCard";
import { useShare } from "@/features/match/useShare";
import { playerRoleLabel } from "@/features/player/text";
import { useText } from "@/i18n";
import styles from "./TeamPage.module.css";

/** A saved team: who plays for it and how its latest matches went. */
export function TeamPage() {
  const { teamId = "" } = useParams();
  const { s } = useText();
  const query = useQuery({
    queryKey: ["public-team", teamId],
    queryFn: ({ signal }) => api<PublicTeamPage>(`/public/teams/${encodeURIComponent(teamId)}`, { signal }),
  });
  if (query.isPending) return <Loading />;
  if (!query.data) {
    const missing = query.error instanceof ApiError && query.error.isNotFound;
    return (
      <Page>
        <ErrorState
          missing={missing}
          title={s(missing ? "web_team_missing_title" : "error_team_title")}
          body={s(missing ? "web_not_found_body" : "error_connection_body")}
          onRetry={missing ? undefined : () => void query.refetch()}
        />
      </Page>
    );
  }
  return <TeamView page={query.data} />;
}

/** Won, lost and tied among [matches] that are over, from the team's side. */
function record(teamId: string, matches: Match[]) {
  let won = 0;
  let lost = 0;
  let tied = 0;
  for (const m of matches) {
    if (m.status !== "COMPLETED" || !m.result) continue;
    const side = m.teamA.teamId === teamId ? "A" : "B";
    if (m.result.winner === side) won++;
    else if (m.result.winner) lost++;
    else if (!m.result.ending) tied++;
  }
  return { played: matches.filter((m) => m.status === "COMPLETED").length, won, lost, tied };
}

function TeamView({ page }: { page: PublicTeamPage }) {
  const { s, p } = useText();
  const { share, toast } = useShare();
  const team = page.team;
  const matches = page.matches ?? [];
  const r = record(team.id, matches);
  // Admins first, then by name, as on the app's team page.
  const members = [...team.members].sort((a, b) => (a.role === b.role ? a.player.name.localeCompare(b.player.name) : a.role === "ADMIN" ? -1 : 1));

  return (
    <Page wide>
      <Hero
        bar={
          <>
            <HeroTitle>{null}</HeroTitle>
            <IconButton icon="share" label={s("web_team_share")} frosted onClick={() => share(team.name)} />
          </>
        }
      >
        <HeroIdentity mark={<Avatar name={team.name} photoUrl={team.logoUrl} size={88} ringed />} eyebrow={s("team_eyebrow")} name={team.name} />
        <HeroChips chips={[...(team.city ? [{ icon: "location_on" as const, text: team.city }] : []), { icon: "groups", text: p("teams_member_count", members.length, members.length) }]} />
        {r.played > 0 && (
          <HeroStats
            stats={[
              { value: String(r.played), label: s("stats_matches") },
              { value: String(r.won), label: s("team_stat_won") },
              { value: String(r.lost), label: s("team_stat_lost") },
              ...(r.tied > 0 ? [{ value: String(r.tied), label: s("team_stat_tied") }] : []),
            ]}
          />
        )}
      </Hero>

      <div className={styles.columns}>
        <section>
          <SectionHeader icon="groups" title={s("team_members")} />
          <ListCard>
            {members.map((m) => (
              <ListRow
                key={m.player.profileId}
                to={`/players/${m.player.profileId}`}
                leading={<Avatar name={m.player.name} photoUrl={m.player.photoUrl} />}
                title={m.player.name}
                subtitle={m.player.isPlaceholder ? s("team_placeholder") : m.player.role ? s(playerRoleLabel[m.player.role]) : undefined}
                trailing={m.role === "ADMIN" ? <Tag icon="verified">{s("teams_role_admin")}</Tag> : undefined}
              />
            ))}
          </ListCard>
        </section>
        <section>
          <SectionHeader icon="history" title={s("web_team_recent")} />
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
