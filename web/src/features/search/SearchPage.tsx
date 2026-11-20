import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router";
import { api } from "@/api/client";
import type { SearchResults } from "@/api/types";
import { Avatar, CardGrid, EmptyState, ErrorState, ListCard, ListRow, Loading, Page, SectionHeader, TextField } from "@/designsystem/components";
import { useDebounced } from "@/features/browse/useCity";
import { MatchListCard } from "@/features/match/MatchListCard";
import { playerRoleLabel } from "@/features/player/text";
import { useText } from "@/i18n";
import styles from "./SearchPage.module.css";

/** Teams, players and matches by name. The words stay in the address, so a search can be shared. */
export function SearchPage() {
  const { s, p } = useText();
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const term = useDebounced(q.trim());
  const ready = term.length >= 2;
  const query = useQuery({
    queryKey: ["public-search", term.toLowerCase()],
    queryFn: ({ signal }) => api<SearchResults>(`/public/search?q=${encodeURIComponent(term)}`, { signal }),
    enabled: ready,
    placeholderData: (previous) => previous,
  });
  const results = query.data;
  const teams = results?.teams ?? [];
  const players = results?.players ?? [];
  const matches = results?.matches ?? [];

  return (
    <Page wide title={s("search_title")}>
      <div className={styles.field}>
        <TextField
          type="search"
          icon="search"
          value={q}
          onChange={(value) => setParams(value ? { q: value } : {}, { replace: true })}
          placeholder={s("search_hint")}
          clearLabel={s("web_search_clear")}
          aria-label={s("search_title")}
          autoFocus
          enterKeyHint="search"
        />
      </div>
      {!ready ? (
        <EmptyState icon="search" title={s("web_search_prompt")} />
      ) : query.isPending ? (
        <Loading />
      ) : !results ? (
        <ErrorState compact title={s("error_list_title")} body={s("error_connection_body")} onRetry={() => void query.refetch()} />
      ) : teams.length + players.length + matches.length === 0 ? (
        <EmptyState icon="search_off" title={s("search_empty")} />
      ) : (
        <>
          <div className={styles.columns}>
            {teams.length > 0 && (
              <section>
                <SectionHeader icon="shield" title={s("search_section_teams")} />
                <ListCard>
                  {teams.map((t) => (
                    <ListRow key={t.id} to={`/teams/${t.id}`} leading={<Avatar name={t.name} photoUrl={t.logoUrl} />} title={t.name} subtitle={[t.city, p("teams_member_count", t.memberCount, t.memberCount)].filter(Boolean).join(" · ")} />
                  ))}
                </ListCard>
              </section>
            )}
            {players.length > 0 && (
              <section>
                <SectionHeader icon="person" title={s("search_section_players")} />
                <ListCard>
                  {players.map((pl) => (
                    <ListRow
                      key={pl.profileId}
                      to={`/players/${pl.profileId}`}
                      leading={<Avatar name={pl.name} photoUrl={pl.photoUrl} />}
                      title={pl.name}
                      subtitle={[pl.role ? s(playerRoleLabel[pl.role]) : null, pl.city].filter(Boolean).join(" · ")}
                    />
                  ))}
                </ListCard>
              </section>
            )}
          </div>
          {matches.length > 0 && (
            <section>
              <SectionHeader icon="sports_cricket" title={s("search_section_matches")} />
              <CardGrid>
                {matches.map((m) => (
                  <MatchListCard key={m.id} match={m} />
                ))}
              </CardGrid>
            </section>
          )}
        </>
      )}
    </Page>
  );
}
