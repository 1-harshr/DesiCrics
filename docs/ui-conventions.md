# UI conventions

Rules for all Android UI code. Reviews check these.

## Text: always use string resources

No user-visible text is written as a literal in Kotlin. Every label, title, button, message, error, content description and cricket abbreviation comes from `strings.xml`.

```kotlin
// Wrong
Text("Start match")
Button(onClick = onUndo) { Text("Undo") }

// Right
Text(stringResource(Res.string.match_start))
Button(onClick = onUndo) { Text(stringResource(Res.string.scoring_undo)) }
```

### Where strings live

| Module | File | What goes there |
|---|---|---|
| `:app:shared` | `src/commonMain/composeResources/values/strings.xml` | All feature text |
| `:app:designsystem` | `src/commonMain/composeResources/values/strings.xml` | Only text built into a component (e.g. LIVE badge) |
| Translations | `values-hi/strings.xml` next to each | Hindi, when we add it |

Use Compose Multiplatform resources (`stringResource`, `pluralStringResource`, `Res.string.*`), not Android `R.string`.

### Naming

`<feature>_<screen or element>_<meaning>`, lowercase with underscores:

- `auth_phone_title`, `auth_otp_resend`
- `match_create_overs_label`, `match_toss_choose_bat`
- `scoring_extra_wide_short` ("Wd"), `scoring_wicket_run_out`
- `common_cancel`, `common_retry` for text shared across features

### Formatting

- **Use placeholders, never concatenation.** Word order changes in other languages.
  ```xml
  <string name="scoring_need_runs">Need %1$d off %2$d balls</string>
  ```
  ```kotlin
  stringResource(Res.string.scoring_need_runs, runsNeeded, ballsLeft)
  ```
- **Use plurals for counts:** `pluralStringResource(Res.plurals.team_member_count, count, count)`.
- **Cricket abbreviations are strings too:** Wd, Nb, B, Lb, ov, CRR, RRR, LBW.
- **Numbers that are pure data** (scores like `142/6`, overs like `16.4`) are formatted by a formatter in code, not stored as strings. Any words around them come from resources.
- **Content descriptions** for icons and images also come from resources.

### ViewModels and errors

- ViewModels and repositories never build display text. They expose state (enums, numbers, IDs) or a `StringResource` reference, and the composable resolves it.
- Errors map to a typed error (`NetworkError.Offline`, `ScoringError.InvalidDelivery`), and the UI picks the string for it.
- Server error messages are never shown to users as-is.

### What may stay literal

- User data: player names, team names, venue names.
- `@Preview` sample data, such as a fake player name. Labels in previews still use resources.
- Log messages and analytics event names, which users never see.

## Styling: always use design tokens

- Colours, text styles, spacing, radii and motion come from `DesiTheme` ([design-system.md](design-system.md)). No hex values, raw `dp` numbers or `TextStyle(...)` in feature code.
- Reusable UI goes in `:app:designsystem`, not in a feature package.

## Icons and assets

- Icons are downloaded one at a time from Google Fonts Icons (Material Symbols Rounded, Android XML) into `:app:designsystem`, and used through `DesiIcons`. No icon libraries. See [design-system.md › Icons](design-system.md#icons).
- Custom icons and artwork are made only when a screen needs them, following [design-system.md › Custom assets](design-system.md#custom-assets).

## Review checklist

- [ ] No literal strings in `Text(...)`, `contentDescription`, `label`, `placeholder` or snackbar calls
- [ ] New strings follow the naming pattern and use placeholders or plurals where needed
- [ ] No colour, size or text style literals outside `:app:designsystem`
- [ ] New shared UI lives in `:app:designsystem`, with light and dark previews
- [ ] New icons are Material Symbols Rounded XML (or custom on the same grid), named `ic_*`, untinted, and added to `DesiIcons`
- [ ] No `material-icons-extended` or other icon library dependency
