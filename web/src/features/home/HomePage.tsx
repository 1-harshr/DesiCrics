import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { GetAppBanner } from "@/app/GetApp";
import { api, ApiError } from "@/api/client";
import type { Match, TodayFeed } from "@/api/types";
import { CardGrid, EmptyState, ErrorBanner, ErrorState, GhostButton, Loading, Page, SectionHeader } from "@/designsystem/components";
import { CityField } from "@/features/browse/CityField";
import { useCity } from "@/features/browse/useCity";
import { MatchListCard } from "@/features/match/MatchListCard";
import { useText } from "@/i18n";
import styles from "./HomePage.module.css";

/** Refreshes this often so live scores on the cards keep moving. */
const REFRESH_MS = 30_000;

/** Everyone's live, upcoming and recent matches, in a city or everywhere. */
export function HomePage() {
  const { s } = useText();
  const { text: city, setText: setCity, settled } = useCity();
  const place = settled.trim();
  const query = useQuery({
    queryKey: ["public-feed", place.toLowerCase()],
    queryFn: ({ signal }) => api<TodayFeed>(`/public/feed?city=${encodeURIComponent(place)}`, { signal }),
    refetchInterval: REFRESH_MS,
    placeholderData: (previous) => previous,
  });

  const feed = query.data;
  const live = feed?.live ?? [];
  const upcoming = feed?.upcoming ?? [];
  const recent = feed?.recent ?? [];

  return (
    <Page wide title={s("web_home_title")}>
      <div className={styles.filter}>
        <CityField city={city} onChange={setCity} />
      </div>
      {query.isPending ? (
        <Loading />
      ) : !feed ? (
        <ErrorState title={s("error_feed_title")} body={s("error_connection_body")} onRetry={() => void query.refetch()} />
      ) : (
        <>
          {query.isRefetchError && (
            <div className={styles.banner}>
              <ErrorBanner warning message={s("feed_stale")} action={{ text: s("feed_retry"), onClick: () => void query.refetch() }} />
            </div>
          )}
          {live.length + upcoming.length + recent.length === 0 ? (
            <EmptyState icon="sports_cricket" title={s("feed_empty")} body={s("web_home_empty_body")} />
          ) : (
            <>
              <Section icon="sports_cricket" title={s("home_section_live")} matches={live} />
              <Section icon="calendar_month" title={s("home_section_upcoming")} matches={upcoming} />
              {recent.length > 0 && <RecentResults first={recent} city={place} />}
            </>
          )}
        </>
      )}
      <GetAppBanner />
    </Page>
  );
}

function Section({ icon, title, matches }: { icon: "sports_cricket" | "calendar_month"; title: string; matches: Match[] }) {
  if (matches.length === 0) return null;
  return (
    <section>
      <SectionHeader icon={icon} title={title} />
      <CardGrid>
        {matches.map((m) => (
          <MatchListCard key={m.id} match={m} />
        ))}
      </CardGrid>
    </section>
  );
}

/** The feed's recent results, then older ones a page at a time. */
function RecentResults({ first, city }: { first: Match[]; city: string }) {
  const { s } = useText();
  const more = useInfiniteQuery({
    queryKey: ["public-results", city.toLowerCase(), first[first.length - 1]?.id],
    enabled: false,
    initialPageParam: first[first.length - 1]!,
    queryFn: ({ pageParam, signal }) =>
      api<Match[]>(`/public/matches?status=COMPLETED&city=${encodeURIComponent(city)}&before=${pageParam.createdAt}&id=${encodeURIComponent(pageParam.id)}`, { signal }),
    getNextPageParam: (last) => (last.length > 0 ? last[last.length - 1] : undefined),
  });
  const older = more.data?.pages.flat() ?? [];
  const done = more.data != null && !more.hasNextPage;
  return (
    <section>
      <SectionHeader icon="history" title={s("home_section_recent")} />
      <CardGrid>
        {[...first, ...older].map((m) => (
          <MatchListCard key={m.id} match={m} />
        ))}
      </CardGrid>
      {more.error instanceof ApiError && (
        <div className={styles.banner}>
          <ErrorBanner message={s("error_connection_body")} />
        </div>
      )}
      {!done && (
        <div className={styles.more}>
          <GhostButton icon="expand_more" loading={more.isFetching} onClick={() => void (more.data ? more.fetchNextPage() : more.refetch())}>
            {s("feed_more")}
          </GhostButton>
        </div>
      )}
    </section>
  );
}
