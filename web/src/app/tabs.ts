import type { NavTab } from "@/designsystem/components";
import type { useText } from "@/i18n";

/** The app's four tabs, in its order. */
export function appTabs({ s }: ReturnType<typeof useText>): NavTab[] {
  return [
    { to: "/", label: s("tab_home"), icon: "home", selectedIcon: "home_filled" },
    { to: "/feed", label: s("tab_feed"), icon: "dynamic_feed", selectedIcon: "dynamic_feed_filled" },
    { to: "/tournaments", label: s("home_tournaments"), icon: "emoji_events", selectedIcon: "emoji_events_filled" },
    { to: "/me", label: s("tab_me"), icon: "person", selectedIcon: "person_filled" },
  ];
}
