import styles from "./Scoring.module.css";

export type BallKind = "Dot" | "Runs" | "Four" | "Six" | "Wicket" | "Extra";

/** One delivery as a circle coloured by its event token. The caller words it ("4", "W", "1wd"). */
export function BallChip({ kind, text, label }: { kind: BallKind; text: string; label?: string }) {
  return (
    <span className={[styles.ball, styles[kind]].join(" ")} aria-label={label}>
      {text}
    </span>
  );
}

/** The balls of an over, in order. */
export function OverStrip({ balls }: { balls: { kind: BallKind; text: string; key: string | number }[] }) {
  return (
    <div className={styles.strip}>
      {balls.map((b) => (
        <BallChip key={b.key} kind={b.kind} text={b.text} />
      ))}
    </div>
  );
}

/** Score on the green bar of a match: big score, a label line, overs and run rates, and the chase. */
export function ScoreHeader({ score, label, data, target }: { score: string; label: string; data: string; target?: string }) {
  return (
    <div className={styles.header}>
      <div className={styles.headerScore}>{score}</div>
      <div className={styles.headerLines}>
        <div className={styles.headerLabel}>{label}</div>
        <div className={styles.headerData}>{data}</div>
        {target && <div className={styles.headerTarget}>{target}</div>}
      </div>
    </div>
  );
}
