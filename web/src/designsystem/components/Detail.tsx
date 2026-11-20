import type { KeyboardEvent as ReactKeyboardEvent, ReactNode } from "react";
import { Link } from "react-router";
import type { IconName } from "../icons.generated";
import { LiveDot } from "./Content";
import styles from "./Detail.module.css";
import { Icon } from "./Icon";

/** The green top of a page about one thing: a bar with back and actions, then whatever identifies it. */
export function Hero({ bar, children }: { bar?: ReactNode; children: ReactNode }) {
  return (
    <header className={styles.hero}>
      {bar && <div className={styles.heroBar}>{bar}</div>}
      {children}
    </header>
  );
}

export function HeroTitle({ children }: { children: ReactNode }) {
  return <div className={styles.heroTitle}>{children}</div>;
}

export function HeroIdentity({ mark, eyebrow, name, subtitle }: { mark?: ReactNode; eyebrow?: string; name: string; subtitle?: string }) {
  return (
    <div className={styles.identity}>
      {mark}
      <div>
        {eyebrow && <div className={styles.eyebrow}>{eyebrow}</div>}
        <h1 className={styles.name}>{name}</h1>
        {subtitle && <div className={styles.subtitle}>{subtitle}</div>}
      </div>
    </div>
  );
}

export function HeroChips({ chips }: { chips: { icon: IconName; text: string }[] }) {
  return (
    <div className={styles.chips}>
      {chips.map((c) => (
        <span key={c.text} className={styles.chip}>
          <Icon name={c.icon} size={20} />
          {c.text}
        </span>
      ))}
    </div>
  );
}

export function HeroActions({ children }: { children: ReactNode }) {
  return <div className={styles.heroActions}>{children}</div>;
}

export function HeroStats({ stats }: { stats: { value: string; label: string }[] }) {
  return (
    <div className={styles.heroStats}>
      {stats.map((s) => (
        <div key={s.label}>
          <div className={styles.heroStatValue}>{s.value}</div>
          <div className={styles.heroStatLabel}>{s.label}</div>
        </div>
      ))}
    </div>
  );
}

/** Starts a block on an inside page: brand icon, bold title and an optional "See all" link. */
export function SectionHeader({ icon, title, action }: { icon: IconName; title: string; action?: { text: string; to: string } }) {
  return (
    <div className={styles.sectionHeader}>
      <Icon name={icon} />
      <h2 className={styles.sectionTitle}>{title}</h2>
      {action && (
        <Link to={action.to} className={styles.seeAll}>
          {action.text}
          <Icon name="chevron_right" size={20} />
        </Link>
      )}
    </div>
  );
}

/** A grouped list on one surface. */
export function ListCard({ children }: { children: ReactNode }) {
  return <div className={styles.listCard}>{children}</div>;
}

/** A row with a leading mark (avatar or IconTile), title, subtitle, trailing content and a chevron when it links. */
export function ListRow({
  leading,
  title,
  subtitle,
  trailing,
  to,
  onClick,
  danger,
}: {
  leading?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
  trailing?: ReactNode;
  to?: string;
  onClick?: () => void;
  danger?: boolean;
}) {
  const classes = [styles.row, danger && styles.danger].filter(Boolean).join(" ");
  const content = (
    <>
      {leading}
      <div className={styles.rowText}>
        <div className={styles.rowTitle}>{title}</div>
        {subtitle && <div className={styles.rowSubtitle}>{subtitle}</div>}
      </div>
      {(trailing || to || onClick) && (
        <div className={styles.rowTrailing}>
          {trailing}
          {(to || onClick) && <Icon name="chevron_right" className={styles.chevron} />}
        </div>
      )}
    </>
  );
  if (to) {
    return (
      <Link to={to} className={classes}>
        {content}
      </Link>
    );
  }
  if (onClick) {
    return (
      <button type="button" className={classes} onClick={onClick}>
        {content}
      </button>
    );
  }
  return <div className={classes}>{content}</div>;
}

/** An icon on a soft rounded square; [tint] is a colour token for kinds of news. */
export function IconTile({ icon, tint }: { icon: IconName; tint?: string }) {
  return (
    <span className={styles.iconTile} style={tint ? { color: tint, background: `color-mix(in srgb, ${tint} 14%, var(--surface))` } : undefined}>
      <Icon name={icon} />
    </span>
  );
}

export function Tag({ children, tone = "brand", icon }: { children: ReactNode; tone?: "brand" | "neutral" | "warn" | "danger"; icon?: IconName }) {
  const toneClass = { brand: styles.brand, neutral: styles.neutral, warn: styles.warn, danger: styles.dangerTone }[tone];
  return (
    <span className={[styles.tag, toneClass].join(" ")}>
      {icon && <Icon name={icon} size={16} />}
      {children}
    </span>
  );
}

/** Figures as equal tiles; the first [featured] sit on the brand tint with larger numbers. */
export function StatGrid({ stats, featured = 0 }: { stats: { value: string; label: string }[]; featured?: number }) {
  return (
    <div className={styles.statGrid}>
      {stats.map((s, i) => (
        <div key={s.label} className={[styles.stat, i < featured && styles.featured].filter(Boolean).join(" ")}>
          <div className={styles.statValue}>{s.value}</div>
          <div className={styles.statLabel}>{s.label}</div>
        </div>
      ))}
    </div>
  );
}

export function EmptyState({ icon, title, body, action }: { icon: IconName; title: string; body?: string; action?: ReactNode }) {
  return (
    <div className={styles.empty}>
      <IconTile icon={icon} />
      <div className={styles.emptyTitle}>{title}</div>
      {body && <p className={styles.emptyBody}>{body}</p>}
      {action}
    </div>
  );
}

/** Filters on one sideways-scrolling line; the selected one filled in ink. */
export function FilterRow<T extends string>({
  options,
  selected,
  onSelect,
}: {
  options: { value: T; label: string; icon?: IconName | "live" }[];
  selected: T;
  onSelect: (value: T) => void;
}) {
  return (
    <div className={styles.filters} role="group">
      {options.map((o) => (
        <button key={o.value} type="button" className={styles.filter} aria-pressed={o.value === selected} onClick={() => onSelect(o.value)}>
          {o.icon === "live" ? <LiveDot /> : o.icon && <Icon name={o.icon} size={20} />}
          {o.label}
        </button>
      ))}
    </div>
  );
}

/**
 * Two to four views of one page, in a pill (Live / Scorecard / Overs). One tab stop for the group; the arrow
 * keys, Home and End move between tabs, as in the ARIA tabs pattern.
 */
export function Tabs<T extends string>({ tabs, selected, onSelect, label }: { tabs: { value: T; label: string }[]; selected: T; onSelect: (value: T) => void; label: string }) {
  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    const index = tabs.findIndex((t) => t.value === selected);
    const next = { ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: tabs.length - 1 }[event.key];
    if (next == null) return;
    event.preventDefault();
    const target = tabs[(next + tabs.length) % tabs.length]!;
    onSelect(target.value);
    event.currentTarget.querySelector<HTMLButtonElement>(`[data-tab="${target.value}"]`)?.focus();
  };
  return (
    <div className={styles.tabs} role="tablist" aria-label={label} onKeyDown={onKeyDown}>
      {tabs.map((t) => (
        <button
          key={t.value}
          type="button"
          role="tab"
          data-tab={t.value}
          className={styles.tab}
          aria-selected={t.value === selected}
          tabIndex={t.value === selected ? 0 : -1}
          onClick={() => onSelect(t.value)}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
