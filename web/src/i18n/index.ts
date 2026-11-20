import i18n from "i18next";
import { useMemo } from "react";
import { initReactI18next, useTranslation } from "react-i18next";
import app from "./app.generated.json";
import web from "./web.json";

/** The app's strings (scripts/strings.mjs) plus web-only ones. Keys are named as in strings.xml. */
const catalogue = { ...app, ...web };
type Catalogue = typeof catalogue;

export type StringKey = { [K in keyof Catalogue]: Catalogue[K] extends string ? K : never }[keyof Catalogue];
/** `team_member_count` for the `team_member_count_one` / `_other` pair. */
export type PluralKey = { [K in keyof Catalogue]: K extends `${infer Base}_other` ? Base : never }[keyof Catalogue];
export type ArrayKey = { [K in keyof Catalogue]: Catalogue[K] extends string[] ? K : never }[keyof Catalogue];

void i18n.use(initReactI18next).init({
  lng: "en",
  fallbackLng: "en",
  resources: { en: { translation: catalogue } },
  keySeparator: false,
  nsSeparator: false,
  returnObjects: true,
  interpolation: { escapeValue: false },
});

type Arg = string | number;
const positional = (args: Arg[]) => Object.fromEntries(args.map((value, i) => [String(i + 1), value]));

/**
 * Text for the UI, as `stringResource` is in the app: `s("home_greeting", name)` fills `{{1}}`,
 * `p("home_notifications", count, count)` picks the plural form for `count`.
 */
export function useText() {
  const { t } = useTranslation();
  return useMemo(
    () => ({
      s: (key: StringKey, ...args: Arg[]): string => t(key, positional(args)),
      p: (key: PluralKey, count: number, ...args: Arg[]): string => t(key, { count, ...positional(args) }),
      list: (key: ArrayKey): string[] => t(key, { returnObjects: true }) as unknown as string[],
    }),
    [t],
  );
}

export default i18n;
