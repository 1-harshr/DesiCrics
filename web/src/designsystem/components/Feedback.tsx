import type { ReactNode } from "react";
import { useText } from "@/i18n";
import { TonalButton } from "./Buttons";
import styles from "./Feedback.module.css";
import { Icon } from "./Icon";

/**
 * In place of content that didn't load. [missing] draws a crossed-out search for something that doesn't
 * exist; otherwise a cloud for connection trouble. Always offers Try again when [onRetry] is given.
 */
export function ErrorState({
  title,
  body,
  onRetry,
  missing,
  compact,
  action,
}: {
  title: string;
  body?: string;
  onRetry?: () => void;
  missing?: boolean;
  compact?: boolean;
  action?: ReactNode;
}) {
  const { s } = useText();
  return (
    <div className={[styles.state, compact ? styles.compact : styles.full].join(" ")} role="alert">
      <span className={styles.mark}>
        <Icon name={missing ? "search_off" : "cloud_off"} size={32} />
      </span>
      {/* Full screen it's the page's title; on a card it heads a section. */}
      {compact ? <h2 className={styles.title}>{title}</h2> : <h1 className={styles.title}>{title}</h1>}
      {body && <p className={styles.body}>{body}</p>}
      {(onRetry || action) && (
        <div className={styles.actions}>
          {onRetry && (
            <TonalButton icon="refresh" onClick={onRetry}>
              {s("ds_try_again")}
            </TonalButton>
          )}
          {action}
        </div>
      )}
    </div>
  );
}

/** A problem that doesn't replace the page. Red by default; [warning] is amber, for being offline. */
export function ErrorBanner({ title, message, warning, action }: { title?: string; message: string; warning?: boolean; action?: { text: string; onClick: () => void } }) {
  return (
    <div className={[styles.banner, warning && styles.warning].filter(Boolean).join(" ")} role="status">
      <Icon name={warning ? "cloud_off" : "error"} size={20} />
      <div className={styles.bannerText}>
        {title && <div className={styles.bannerTitle}>{title}</div>}
        <div>{message}</div>
      </div>
      {action && (
        <button type="button" className={styles.bannerAction} onClick={action.onClick}>
          {action.text}
        </button>
      )}
    </div>
  );
}

export function Loading({ label }: { label?: string }) {
  const { s } = useText();
  return (
    <div className={styles.loading} role="progressbar" aria-label={label ?? s("ds_loading")}>
      <span className={styles.spinner} />
    </div>
  );
}

/** A short message at the foot of the screen that goes away on its own. */
export function Snackbar({ message }: { message: string }) {
  return (
    <div className={styles.snackbar} role="status">
      {message}
    </div>
  );
}
