import { useState } from "react";
import type { Match, Team } from "@/api/types";
import type { InningsView, ScorecardView } from "@/core";
import { Avatar, Card, EmptyState, Icon, ListCard, ListRow, OverStrip, SectionLabel, Tag } from "@/designsystem/components";
import { useText, type StringKey } from "@/i18n";
import styles from "./MatchPage.module.css";
import { format2, useMatchText } from "./text";

const inningsName = (number: number): StringKey => (number === 1 ? "match_innings_first" : "match_innings_second");

/** Who's batting and bowling now, and the over in progress. */
export function LiveTab({ innings }: { innings: InningsView }) {
  const { s } = useText();
  const { ballFor } = useMatchText();
  const atCrease = [innings.strikerId, innings.nonStrikerId]
    .map((id) => innings.batters.find((b) => b.player.id === id))
    .filter((b) => b != null);
  const bowler = innings.bowlers.find((b) => b.player.id === innings.bowlerId);

  return (
    <div className={styles.stack}>
      <Card>
        <SectionLabel>{s("match_section_crease")}</SectionLabel>
        <div className={styles.crease}>
          {atCrease.map((b) => (
            <div key={b.player.id} className={styles.creaseRow}>
              <span className={[styles.creaseIcon, b.player.id === innings.strikerId && styles.creaseIconOn].filter(Boolean).join(" ")}>
                <Icon name="cricket_bat" size={20} />
              </span>
              <div className={styles.creaseName}>
                <div className="body-strong">{b.player.name}</div>
                {b.player.id === innings.strikerId && <div className={styles.onStrike}>{s("match_on_strike")}</div>}
              </div>
              <div className="score-md">{s("match_batter_score", b.runs, b.balls)}</div>
            </div>
          ))}
          {bowler && (
            <div className={[styles.creaseRow, styles.creaseBowler].join(" ")}>
              <span className={styles.creaseIcon}>
                <Icon name="sports_baseball" size={20} />
              </span>
              <div className={styles.creaseName}>
                <div className="body-strong">{bowler.player.name}</div>
              </div>
              <div className="score-md">{`${bowler.oversText}-${bowler.maidens}-${bowler.runs}-${bowler.wickets}`}</div>
            </div>
          )}
        </div>
      </Card>
      <Card>
        <div className={styles.overHead}>
          <SectionLabel>{s("match_section_this_over")}</SectionLabel>
          {innings.isFreeHit && <Tag tone="warn">{s("match_free_hit")}</Tag>}
        </div>
        <div className={styles.overBalls}>
          {innings.currentOver.length === 0 ? (
            <p className="muted">{s("match_over_empty")}</p>
          ) : (
            <OverStrip balls={innings.currentOver.map((d) => ({ key: d.seq, ...ballFor(d) }))} />
          )}
        </div>
      </Card>
    </div>
  );
}

function InningsCard({ innings }: { innings: InningsView }) {
  const { s } = useText();
  const { dismissalText } = useMatchText();
  const e = innings.extras;
  return (
    <section className={styles.inningsCard}>
      <header className={styles.inningsHead}>
        <div>
          <div className="label muted">{s("scorecard_innings", innings.battingTeam)}</div>
          <h3 className="title-md">{innings.battingTeam}</h3>
        </div>
        <div className={styles.inningsScore}>
          <span className="score-lg">{innings.scoreText}</span>
          <span className="data muted">{s("ds_innings_overs", innings.oversText)}</span>
          {innings.declared && <Tag tone="neutral">{s("scorecard_declared")}</Tag>}
        </div>
      </header>

      {innings.batters.length === 0 ? (
        <p className={["muted", styles.notStarted].join(" ")}>{s("scorecard_not_started")}</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>{s("scorecard_batter")}</th>
                <th>{s("scorecard_runs")}</th>
                <th>{s("scorecard_balls")}</th>
                <th>{s("scorecard_fours")}</th>
                <th>{s("scorecard_sixes")}</th>
                <th>{s("scorecard_strike_rate")}</th>
              </tr>
            </thead>
            <tbody>
              {innings.batters.map((b) => (
                <tr key={b.player.id}>
                  <td>
                    <div className={b.isOut ? undefined : styles.notOut}>{b.player.name}</div>
                    <div className={styles.howOut}>{dismissalText(b)}</div>
                  </td>
                  <td className={styles.strong}>{b.runs}</td>
                  <td>{b.balls}</td>
                  <td>{b.fours}</td>
                  <td>{b.sixes}</td>
                  <td>{format2(b.strikeRate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className={styles.inningsFoot}>
        <p>{s("scorecard_extras", e.total, e.wides, e.noBalls, e.byes, e.legByes, e.penalties)}</p>
        {innings.yetToBat.length > 0 && <p className="muted">{s("scorecard_yet_to_bat", innings.yetToBat.map((p) => p.name).join(", "))}</p>}
      </div>

      {innings.bowlers.length > 0 && (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>{s("scorecard_bowler")}</th>
                <th>{s("scorecard_overs")}</th>
                <th>{s("scorecard_maidens")}</th>
                <th>{s("scorecard_runs")}</th>
                <th>{s("scorecard_wickets")}</th>
                <th>{s("scorecard_economy")}</th>
              </tr>
            </thead>
            <tbody>
              {innings.bowlers.map((b) => (
                <tr key={b.player.id}>
                  <td>{b.player.name}</td>
                  <td>{b.oversText}</td>
                  <td>{b.maidens}</td>
                  <td>{b.runs}</td>
                  <td className={styles.strong}>{b.wickets}</td>
                  <td>{format2(b.economy)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function ScorecardTab({ card }: { card: ScorecardView }) {
  return (
    <div className={styles.stack}>
      {card.innings.map((innings) => (
        <InningsCard key={innings.number} innings={innings} />
      ))}
    </div>
  );
}

/** Every over, newest first, with its balls; the commentary opens underneath. */
export function OversTab({ card }: { card: ScorecardView }) {
  const { s, p } = useText();
  const { ballFor, commentaryText } = useMatchText();
  const [open, setOpen] = useState<Set<string>>(new Set());
  const toggle = (key: string) =>
    setOpen((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const innings = [...card.innings].reverse().filter((i) => i.overs.length > 0);
  if (innings.length === 0) return <EmptyState icon="sports_cricket" title={s("overs_empty")} />;

  return (
    <div className={styles.stack}>
      {innings.map((inn) => (
        <section key={inn.number}>
          <SectionLabel>{s("web_match_overs_innings", inn.battingTeam, s(inningsName(inn.number)))}</SectionLabel>
          <ListCard>
            {[...inn.overs].reverse().map((over) => {
              const key = `${inn.number}-${over.number}`;
              const expanded = open.has(key);
              return (
                <div key={key} className={styles.over}>
                  <div className={styles.overTop}>
                    <div>
                      <div className="body-strong">{s("overs_over", over.number)}</div>
                      <div className="caption muted">
                        {s("overs_summary", over.bowler, [p("overs_runs", over.runs, over.runs), over.wickets > 0 ? p("overs_wickets", over.wickets, over.wickets) : null].filter(Boolean).join(" · "))}
                      </div>
                    </div>
                    <div className="score-md">{over.scoreAfter}</div>
                  </div>
                  <OverStrip balls={over.balls.map((b) => ({ key: b.delivery.seq, ...ballFor(b.delivery) }))} />
                  <button type="button" className={styles.overToggle} aria-expanded={expanded} onClick={() => toggle(key)}>
                    {s(expanded ? "hide_deliveries" : "show_deliveries")}
                    <Icon name={expanded ? "expand_less" : "expand_more"} size={20} />
                  </button>
                  {expanded && (
                    <ol className={styles.commentary}>
                      {over.balls.map((b) => (
                        <li key={b.delivery.seq}>
                          <span className="data muted">{b.ballNumber}</span>
                          <span>{commentaryText(b)}</span>
                        </li>
                      ))}
                    </ol>
                  )}
                </div>
              );
            })}
          </ListCard>
        </section>
      ))}
    </div>
  );
}

/** Before a ball is bowled: both sides' players. */
export function Squads({ match }: { match: Match }) {
  const { s } = useText();
  const side = (team: Team) => (
    <section key={team.name}>
      <SectionLabel>{team.name}</SectionLabel>
      <ListCard>
        {team.players.map((player) => (
          <ListRow
            key={player.id}
            leading={<Avatar name={player.name} size={40} />}
            title={player.name}
            trailing={
              <>
                {player.id === team.captainId && <Tag tone="neutral">{s("new_match_captain_short")}</Tag>}
                {player.id === team.keeperId && <Tag tone="neutral">{s("new_match_keeper_short")}</Tag>}
              </>
            }
          />
        ))}
      </ListCard>
    </section>
  );
  return <div className={styles.squads}>{[match.teamA, match.teamB].filter((t) => t.players.length > 0).map(side)}</div>;
}
