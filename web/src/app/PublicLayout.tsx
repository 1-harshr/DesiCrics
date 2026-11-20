import { useState, type ReactNode } from "react";
import { Link } from "react-router";
import { GhostButton, Icon, PrimaryButton } from "@/designsystem/components";
import { useText } from "@/i18n";
import styles from "./PublicLayout.module.css";

export const playStoreUrl = import.meta.env.VITE_PLAY_STORE_URL || "https://play.google.com/store/apps/details?id=com.harsh.desicrics";
const dismissedKey = "desicrics.getAppDismissed";

function readDismissed(): boolean {
  try {
    return localStorage.getItem(dismissedKey) === "1";
  } catch {
    return false;
  }
}

/** Once per browser: for anyone who wants to do more than watch. */
export function GetAppBanner() {
  const { s } = useText();
  const [dismissed, setDismissed] = useState(readDismissed);
  if (dismissed) return null;
  const dismiss = () => {
    setDismissed(true);
    try {
      localStorage.setItem(dismissedKey, "1");
    } catch {
      // Storage blocked: hidden for this visit only.
    }
  };
  return (
    <aside className={styles.banner}>
      <img src="/icon.svg" alt="" className={styles.bannerIcon} />
      <div className={styles.bannerText}>
        <div className={styles.bannerTitle}>{s("web_get_app_title")}</div>
        <p className={styles.bannerBody}>{s("web_get_app_body")}</p>
        <div className={styles.bannerActions}>
          <PrimaryButton href={playStoreUrl} trailingIcon="north_east">
            {s("web_get_app_action")}
          </PrimaryButton>
          <GhostButton onClick={dismiss}>{s("web_get_app_dismiss")}</GhostButton>
        </div>
      </div>
    </aside>
  );
}

/** Frame for pages opened from a shared link: no tabs, just the brand back to the home page. */
export function PublicLayout({ children }: { children: ReactNode }) {
  const { s } = useText();
  return (
    <div className={styles.frame}>
      <header className={styles.top}>
        <Link to="/" className={styles.brand}>
          <img src="/icon.svg" alt="" />
          <span>{s("web_brand")}</span>
        </Link>
        <a href={playStoreUrl} target="_blank" rel="noreferrer" className={styles.topLink}>
          {s("web_get_app_action")}
          <Icon name="north_east" size={18} />
        </a>
      </header>
      {children}
    </div>
  );
}
