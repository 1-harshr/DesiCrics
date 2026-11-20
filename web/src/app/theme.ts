export type ThemeChoice = "system" | "light" | "dark";

const key = "desicrics.theme";

/** The viewer's theme choice, kept in this browser only. "system" follows the device. */
export function savedTheme(): ThemeChoice {
  try {
    const value = localStorage.getItem(key);
    return value === "light" || value === "dark" ? value : "system";
  } catch {
    return "system";
  }
}

export function applyTheme(choice: ThemeChoice) {
  const root = document.documentElement;
  if (choice === "system") delete root.dataset.theme;
  else root.dataset.theme = choice;
  try {
    if (choice === "system") localStorage.removeItem(key);
    else localStorage.setItem(key, choice);
  } catch {
    // Storage blocked: the choice lasts for this visit.
  }
}
