import { useState } from "react";
import { applyTheme, savedTheme, type ThemeChoice } from "@/app/theme";
import {
  Avatar,
  BallChip,
  EmptyState,
  ErrorBanner,
  ErrorState,
  FilterRow,
  GhostButton,
  Hero,
  HeroActions,
  HeroButton,
  HeroChips,
  HeroIdentity,
  HeroSecondaryButton,
  HeroStats,
  IconButton,
  IconTile,
  ListCard,
  ListRow,
  LiveBadge,
  MatchCard,
  OverStrip,
  Page,
  PrimaryButton,
  ScoreHeader,
  SectionHeader,
  StatGrid,
  Tabs,
  Tag,
  TonalButton,
} from "@/designsystem/components";
import { useText } from "@/i18n";
import styles from "./CataloguePage.module.css";

/**
 * Every design-system component with sample data, like the app's Catalogue.kt. Names and scores here are
 * sample data, not UI text.
 */
export function CataloguePage() {
  const { s } = useText();
  const [theme, setTheme] = useState<ThemeChoice>(savedTheme);
  const [tab, setTab] = useState<"live" | "scorecard" | "overs">("live");

  const pickTheme = (choice: ThemeChoice) => {
    setTheme(choice);
    applyTheme(choice);
  };

  return (
    <Page wide>
      <Hero bar={<IconButton icon="arrow_back" label={s("ds_back")} frosted />}>
        <HeroIdentity mark={<Avatar name="Andheri Strikers" size={88} ringed />} eyebrow={s("web_brand")} name={s("web_catalogue_title")} subtitle={s("web_catalogue_body")} />
        <HeroChips chips={[{ icon: "location_on", text: "Mumbai" }, { icon: "groups", text: "11" }, { icon: "verified", text: "Admin" }]} />
        <HeroStats stats={[{ value: "24", label: s("stats_matches") }, { value: "612", label: s("stats_runs") }, { value: "31", label: s("stats_wickets") }]} />
        <HeroActions>
          <HeroButton icon="dynamic_feed">{s("feed_all")}</HeroButton>
          <HeroSecondaryButton icon="share">{s("feed_refresh")}</HeroSecondaryButton>
        </HeroActions>
      </Hero>

      <SectionHeader icon="settings" title={s("web_catalogue_theme")} />
      <FilterRow
        options={[
          { value: "system", label: s("web_theme_system") },
          { value: "light", label: s("web_theme_light") },
          { value: "dark", label: s("web_theme_dark") },
        ]}
        selected={theme}
        onSelect={pickTheme}
      />

      <div className={styles.columns}>
        <section>
          <SectionHeader icon="sports_cricket" title={s("web_catalogue_buttons")} />
          <div className={styles.stack}>
            <PrimaryButton full trailingIcon="arrow_forward">{s("feed_all")}</PrimaryButton>
            <PrimaryButton full loading>{s("feed_all")}</PrimaryButton>
            <TonalButton full icon="refresh">{s("feed_refresh")}</TonalButton>
            <GhostButton full icon="share">{s("common_cancel")}</GhostButton>
          </div>

          <SectionHeader icon="sports_score" title={s("web_catalogue_scoring")} />
          <div className={styles.scoreBar}>
            <ScoreHeader score="142/6" label="Andheri Strikers · 1st innings" data="16.4 / 20 ov · CRR 8.52" />
          </div>
          <div className={styles.stack}>
            <OverStrip
              balls={[
                { key: 1, kind: "Four", text: "4" },
                { key: 2, kind: "Runs", text: "1" },
                { key: 3, kind: "Dot", text: "•" },
                { key: 4, kind: "Extra", text: "nb" },
                { key: 5, kind: "Six", text: "6" },
                { key: 6, kind: "Wicket", text: "W" },
              ]}
            />
            <div>
              <BallChip kind="Extra" text="1wd" />
            </div>
            <Tabs
              label={s("match_tab_live")}
              tabs={[
                { value: "live", label: s("match_tab_live") },
                { value: "scorecard", label: s("match_tab_scorecard") },
                { value: "overs", label: s("match_tab_overs") },
              ]}
              selected={tab}
              onSelect={setTab}
            />
          </div>

          <SectionHeader icon="leaderboard" title={s("web_catalogue_stats")} />
          <StatGrid
            featured={2}
            stats={[
              { value: "612", label: s("stats_runs") },
              { value: "31", label: s("stats_wickets") },
              { value: "24", label: s("stats_matches") },
              { value: "142.3", label: "SR" },
            ]}
          />
        </section>

        <section>
          <SectionHeader icon="dynamic_feed" title={s("web_catalogue_matches")} action={{ text: s("feed_all"), to: "/catalogue" }} />
          <div className={styles.stack}>
            <MatchCard
              status={<LiveBadge />}
              meta="5 overs · Powai Gymkhana"
              teamA={{ name: "Andheri Strikers", score: "14/0", note: "0.5 ov · Batting" }}
              teamB={{ name: "Powai XI", note: "Yet to bat" }}
              footer="Andheri Strikers won the toss and chose to bat"
            />
            <MatchCard
              meta="20 overs · Sun 17 Nov"
              teamA={{ name: "Kothrud Kings", score: "168/5", note: "20.0 ov" }}
              teamB={{ name: "Baner Blasters", score: "142/9", note: "20.0 ov" }}
              result={{ winner: "A", text: "Kothrud Kings won by 26 runs" }}
            />
          </div>

          <SectionHeader icon="groups" title={s("web_catalogue_lists")} />
          <ListCard>
            <ListRow leading={<Avatar name="Rohan Iyer" />} title="Rohan Iyer" subtitle="All-rounder" trailing={<Tag icon="verified">Admin</Tag>} to="/catalogue" />
            <ListRow leading={<Avatar name="Kabir" />} title="Kabir" subtitle="Not on the app yet" trailing={<Tag tone="warn">Invited</Tag>} onClick={() => {}} />
            <ListRow leading={<IconTile icon="logout" tint="var(--event-wicket)" />} title="Leave team" danger />
          </ListCard>

          <SectionHeader icon="error" title={s("web_catalogue_feedback")} />
          <div className={styles.stack}>
            <ErrorBanner warning title={s("home_error_title")} message={s("home_error_body")} />
            <ErrorBanner message={s("feed_stale")} action={{ text: s("feed_retry"), onClick: () => {} }} />
            <ErrorState compact title={s("error_match_title")} body={s("error_connection_body")} onRetry={() => {}} />
            <EmptyState icon="sports_cricket" title={s("home_empty_title")} body={s("home_empty_body")} />
          </div>
        </section>
      </div>
    </Page>
  );
}
