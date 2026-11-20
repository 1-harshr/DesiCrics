import type { HTMLAttributes, ReactNode } from "react";
import { Link } from "react-router";
import { useText } from "@/i18n";
import styles from "./Content.module.css";
import { Icon } from "./Icon";

/** Flat bordered surface. Pass [to] to make the whole card a link. */
export function Card({ to, className, children, ...rest }: HTMLAttributes<HTMLDivElement> & { to?: string }) {
  const classes = [styles.card, className].filter(Boolean).join(" ");
  if (to) {
    return (
      <Link to={to} className={classes}>
        {children}
      </Link>
    );
  }
  return (
    <div className={classes} {...rest}>
      {children}
    </div>
  );
}

/** Uppercase label for dense spots inside cards. Inside pages use SectionHeader. */
export function SectionLabel({ children }: { children: ReactNode }) {
  return <h3 className={styles.sectionLabel}>{children}</h3>;
}

/** The sign for "happening now": a red dot with a pulsing ring. */
export function LiveDot() {
  return <span className={styles.liveDot} aria-hidden />;
}

export function LiveBadge() {
  const { s } = useText();
  return (
    <span className={styles.liveBadge}>
      <LiveDot />
      {s("ds_live")}
    </span>
  );
}

/** Up to two initials, as the app draws them: "Rohan Iyer" → "RI", "Kabir" → "K". */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return words
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join("");
}

export function Avatar({ name, photoUrl, size = 48, ringed }: { name: string; photoUrl?: string | null; size?: number; ringed?: boolean }) {
  return (
    <span className={[styles.avatar, ringed && styles.ringed].filter(Boolean).join(" ")} style={{ width: size, height: size, fontSize: size * 0.42 }} aria-hidden>
      {photoUrl ? <img src={photoUrl} alt="" /> : initials(name) || <Icon name="person" size={size * 0.5} />}
    </span>
  );
}

export interface MatchSide {
  name: string;
  /** "142/6", or null before the side has batted. */
  score?: string | null;
  /** The line under the score: "16.4 ov · Batting", "Yet to bat". */
  note?: string;
}

export interface CardResult {
  /** Null for a tie or no result, which gets a neutral strip. */
  winner: "A" | "B" | null;
  text: string;
}

/**
 * A match in a feed: status slot, meta, both teams side by side and a footer line. A finished match passes
 * [result]: the winner gets a trophy, the loser dims, and the result sits in a strip across the foot.
 */
export function MatchCard({
  to,
  status,
  meta,
  teamA,
  teamB,
  footer,
  result,
}: {
  to?: string;
  status?: ReactNode;
  meta?: string;
  teamA: MatchSide;
  teamB: MatchSide;
  footer?: string;
  result?: CardResult;
}) {
  const side = (team: MatchSide, key: "A" | "B") => {
    const won = result?.winner === key;
    const lost = result?.winner != null && !won;
    return (
      <div className={[styles.side, lost && styles.dim].filter(Boolean).join(" ")}>
        <div className={styles.sideName}>
          {team.name}
          {won && <Icon name="emoji_events_filled" size={18} />}
        </div>
        {team.score && <div className={styles.sideScore}>{team.score}</div>}
        {team.note && <div className={styles.sideNote}>{team.note}</div>}
      </div>
    );
  };
  return (
    <Card to={to}>
      {(status || meta) && (
        <div className={styles.matchTop}>
          <span>{status}</span>
          {meta && <span className={styles.matchMeta}>{meta}</span>}
        </div>
      )}
      <div className={styles.teams}>
        {side(teamA, "A")}
        {side(teamB, "B")}
      </div>
      {result ? (
        <div className={[styles.resultStrip, result.winner == null && styles.resultNeutral].filter(Boolean).join(" ")}>{result.text}</div>
      ) : (
        footer && <div className={styles.footer}>{footer}</div>
      )}
    </Card>
  );
}
