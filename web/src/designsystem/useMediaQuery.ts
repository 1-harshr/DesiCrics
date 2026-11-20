import { useSyncExternalStore } from "react";

/** Whether a CSS media query matches now, kept up to date as the window changes. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}

/** The width from which pages with two things to show put them side by side (docs/web.md). */
export const WIDE = "(min-width: 1100px)";
