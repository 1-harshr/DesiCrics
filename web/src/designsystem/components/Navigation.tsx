import type { ReactNode } from "react";
import { Link, NavLink } from "react-router";
import type { IconName } from "../icons.generated";
import { Icon } from "./Icon";
import styles from "./Navigation.module.css";

export interface NavTab {
  to: string;
  label: string;
  icon: IconName;
  selectedIcon: IconName;
}

/**
 * The app's frame: bottom tabs on a phone, a side rail from 720px. Outlined icons, filled and green on a
 * soft pill when selected, as DesiNavigationBar.
 */
export function AppShell({ tabs, homeLabel, children }: { tabs: NavTab[]; homeLabel: string; children: ReactNode }) {
  return (
    <div className={styles.shell}>
      <nav className={styles.bar}>
        <Link to="/" className={styles.brand} aria-label={homeLabel}>
          <img src={`${import.meta.env.BASE_URL}icon.svg`} alt="" />
        </Link>
        {tabs.map((tab) => (
          <NavLink key={tab.to} to={tab.to} end={tab.to === "/"} className={({ isActive }) => [styles.tab, isActive && styles.active].filter(Boolean).join(" ")}>
            {({ isActive }) => (
              <>
                <span className={styles.pill}>
                  <Icon name={isActive ? tab.selectedIcon : tab.icon} />
                </span>
                {tab.label}
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <main className={styles.main}>{children}</main>
    </div>
  );
}

/** A page's column. [wide] lets two-column layouts spread to 1120px. */
export function Page({ title, wide, children }: { title?: string; wide?: boolean; children: ReactNode }) {
  return (
    <div className={[styles.page, wide && styles.wide].filter(Boolean).join(" ")}>
      {title && <h1 className={styles.pageTitle}>{title}</h1>}
      {children}
    </div>
  );
}
