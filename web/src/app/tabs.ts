import type { NavTab } from "@/designsystem/components";
import type { useText } from "@/i18n";

/** The web app's tabs: everything to watch and look up. There's no "Me", since nobody signs in. */
export function appTabs({ s }: ReturnType<typeof useText>): NavTab[] {
  return [
    { to: "/", label: s("tab_home"), icon: "home", selectedIcon: "home_filled" },
    { to: "/tournaments", label: s("home_tournaments"), icon: "emoji_events", selectedIcon: "emoji_events_filled" },
    { to: "/leaderboards", label: s("home_leaderboards"), icon: "leaderboard", selectedIcon: "leaderboard" },
    { to: "/search", label: s("home_search"), icon: "search", selectedIcon: "search" },
  ];
}
