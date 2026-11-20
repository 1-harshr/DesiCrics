import { useState } from "react";
import { Link, useParams } from "react-router";
import { GetAppBanner } from "@/app/GetApp";
import { ApiError } from "@/api/client";
import type { Match } from "@/api/types";
import type { ScorecardView } from "@/core";
import { ErrorBanner, ErrorState, Hero, HeroTitle, Icon, IconButton, LiveBadge, Loading, Page, ScoreHeader, SectionHeader, Tabs } from "@/designsystem/components";
import { stageText } from "@/features/tournament/text";
import { useText } from "@/i18n";
import { LiveTab, OversTab, ScorecardTab, Squads } from "./MatchParts";
import styles from "./MatchPage.module.css";
import { format2, oversOf, useMatchText } from "./text";
import { useLiveMatch } from "./useLiveMatch";
import { useShare } from "./useShare";

type Tab = "live" | "scorecard" | "overs";

/** A match for anyone with the link: the live score, scorecard and over-by-over, kept up to date. */
export function MatchPage() {
  const { matchId = "" } = useParams();
  const { s } = useText();
  const live = useLiveMatch(matchId);

  if (live.isLoading) {
    return (
      <>
        <Loading />
      </>
    );
  }
  if (!live.details || !live.scorecard) {
    const missing = live.error instanceof ApiError && live.error.isNotFound;
    return (
      <>
        <Page>
          <ErrorState
            missing={missing}
            title={s(missing ? "error_match_missing_title" : "error_match_title")}
            body={s(missing ? "match_not_found" : "error_connection_body")}
            onRetry={missing ? undefined : live.retry}
          />
        </Page>
      </>
    );
  }
  return <MatchView match={live.details.match} card={live.scorecard} isStale={live.isStale} reconnecting={live.reconnecting} onRetry={live.retry} />;
}

function MatchView({ match, card, isStale, reconnecting, onRetry }: { match: Match; card: ScorecardView; isStale: boolean; reconnecting: boolean; onRetry: () => void }) {
  const text = useText();
  const { s, p } = text;
  const { statusText } = useMatchText();
  const { share, toast } = useShare();
  const title = s("match_title", match.teamA.name, match.teamB.name);
  const started = match.status !== "SCHEDULED" && card.innings.some((i) => i.legalBalls > 0 || i.runs > 0 || i.batters.length > 0);
  const inPlay = started && !card.isComplete && match.status !== "COMPLETED";
  const [tab, setTab] = useState<Tab>(inPlay ? "live" : "scorecard");
  const current = card.innings[card.innings.length - 1];

  const tabs: { value: Tab; label: string }[] = [
    ...(inPlay ? [{ value: "live" as const, label: s("match_tab_live") }] : []),
    { value: "scorecard", label: s("match_tab_scorecard") },
    { value: "overs", label: s("match_tab_overs") },
  ];
  const shown = tabs.some((t) => t.value === tab) ? tab : tabs[0]!.value;

  const details = current
    ? [
        s("match_overs_of", current.oversText, oversOf(current.maxBalls)),
        s("match_run_rate", format2(current.runRate)),
        current.isPowerplay ? s("match_powerplay") : null,
        current.isRevised ? s("match_revised") : null,
      ]
        .filter(Boolean)
        .join(" · ")
    : "";
  const chase = current && current.runsNeeded != null && !card.isComplete ? p("match_need", current.ballsRemaining, current.runsNeeded, current.ballsRemaining) : undefined;
  const firstInnings = card.innings.length > 1 ? card.innings[0] : undefined;

  return (
    <>
      <Page wide>
        <Hero
          bar={
            <>
              <HeroTitle>{inPlay ? <span className={styles.heroLive}><LiveBadge /></span> : started ? title : null}</HeroTitle>
              <IconButton icon="share" label={s("match_share")} frosted onClick={() => share(title)} />
            </>
          }
        >
          {inPlay && <h1 className={styles.heroTitle}>{title}</h1>}
          {started && current ? (
            <div className={styles.heroScore}>
              <ScoreHeader
                score={current.scoreText}
                label={s("match_innings_label", current.battingTeam, s(current.number === 1 ? "match_innings_first" : "match_innings_second"))}
                data={details}
                target={chase}
              />
              {firstInnings && <p className={styles.heroLine}>{s("match_first_innings_score", firstInnings.battingTeam, firstInnings.scoreText, firstInnings.oversText)}</p>}
            </div>
          ) : (
            !inPlay && <h1 className={styles.heroTitle}>{title}</h1>
          )}
          {(!inPlay || !started) && <p className={styles.heroStatus}>{statusText(match)}</p>}
          {match.venue && <p className={styles.heroLine}>{match.venue}</p>}
          {match.fixture && (
            <Link to={`/t/${match.fixture.tournamentId}`} className={styles.heroLink}>
              <Icon name="emoji_events" size={20} />
              {stageText(text, match.fixture)} · {s("web_match_tournament_link")}
              <Icon name="chevron_right" size={20} />
            </Link>
          )}
        </Hero>

        {inPlay && isStale && (
          <div className={styles.banner}>
            <ErrorBanner warning title={s("match_offline_title")} message={s("match_offline")} action={{ text: s("ds_try_again"), onClick: onRetry }} />
          </div>
        )}
        {inPlay && !isStale && reconnecting && <p className={["caption", "muted", styles.liveNote].join(" ")}>{s("web_match_reconnecting")}</p>}

        {!started ? (
          (match.teamA.players.length > 0 || match.teamB.players.length > 0) && (
            <>
              <SectionHeader icon="groups" title={s("fixture_squads")} />
              <Squads match={match} />
            </>
          )
        ) : (
          <>
            {/* One column of tabs on phones; side by side from 1100px. */}
            <div className={styles.tabsOnly}>
              <Tabs label={title} tabs={tabs} selected={shown} onSelect={setTab} />
              <div className={styles.tabBody}>
                {shown === "live" && current && <LiveTab innings={current} />}
                {shown === "scorecard" && <ScorecardTab card={card} />}
                {shown === "overs" && <OversTab card={card} />}
              </div>
            </div>
            <div className={styles.columns}>
              <div>
                {inPlay && current && (
                  <>
                    <SectionHeader icon="sports_cricket" title={s("match_tab_live")} />
                    <LiveTab innings={current} />
                  </>
                )}
                <SectionHeader icon="sports_score" title={s("match_tab_scorecard")} />
                <ScorecardTab card={card} />
              </div>
              <div>
                <SectionHeader icon="history" title={s("match_tab_overs")} />
                <OversTab card={card} />
              </div>
            </div>
          </>
        )}
        <GetAppBanner />
      </Page>
      {toast}
    </>
  );
}
