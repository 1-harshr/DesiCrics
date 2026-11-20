import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";
import type { TournamentSummary } from "@/api/types";
import { EmptyState, ErrorState, IconTile, ListCard, ListRow, Loading, Page, SectionHeader } from "@/designsystem/components";
import { CityField } from "@/features/browse/CityField";
import { useCity, useDebounced } from "@/features/browse/useCity";
import { formatDayAndTime } from "@/features/match/text";
import { useText } from "@/i18n";
import { formatLabel } from "./text";
import styles from "./TournamentsPage.module.css";

/** Tournaments in a city or everywhere: under way, coming up, and finished. */
export function TournamentsPage() {
  const { s, p } = useText();
  const [city, setCity] = useCity();
  const place = useDebounced(city.trim());
  const query = useQuery({
    queryKey: ["public-tournaments", place.toLowerCase()],
    queryFn: ({ signal }) => api<TournamentSummary[]>(`/public/tournaments?city=${encodeURIComponent(place)}`, { signal }),
    placeholderData: (previous) => previous,
  });

  const all = query.data ?? [];
  const groups: { title: string; icon: "sports_cricket" | "calendar_month" | "emoji_events"; items: TournamentSummary[] }[] = [
    { title: s("web_tournaments_live"), icon: "sports_cricket", items: all.filter((t) => t.started && !t.finished) },
    { title: s("web_tournaments_upcoming"), icon: "calendar_month", items: all.filter((t) => !t.started) },
    { title: s("web_tournaments_finished"), icon: "emoji_events", items: all.filter((t) => t.finished) },
  ];

  const row = (t: TournamentSummary) => (
    <ListRow
      key={t.id}
      to={`/t/${t.id}`}
      leading={<IconTile icon={t.finished ? "emoji_events_filled" : "emoji_events"} />}
      title={t.name}
      subtitle={[
        s(formatLabel[t.format]),
        p("tournaments_team_count", t.teams, t.teams),
        t.city,
        !t.started && t.startsAt ? s("tournaments_starts", formatDayAndTime(t.startsAt)) : null,
      ]
        .filter(Boolean)
        .join(" · ")}
    />
  );

  return (
    <Page wide title={s("tournaments_title")}>
      <div className={styles.filter}>
        <CityField city={city} onChange={setCity} />
      </div>
      {query.isPending ? (
        <Loading />
      ) : !query.data ? (
        <ErrorState title={s("error_tournaments_title")} body={s("error_connection_body")} onRetry={() => void query.refetch()} />
      ) : all.length === 0 ? (
        <EmptyState icon="emoji_events" title={s("tournaments_empty_title")} body={s("web_tournaments_empty_body")} />
      ) : (
        <div className={styles.columns}>
          {groups
            .filter((g) => g.items.length > 0)
            .map((g) => (
              <section key={g.title}>
                <SectionHeader icon={g.icon} title={g.title} />
                <ListCard>{g.items.map(row)}</ListCard>
              </section>
            ))}
        </div>
      )}
    </Page>
  );
}
