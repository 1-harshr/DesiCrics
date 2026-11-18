# Design system: Crease

Crease is built around the scoreboard: big condensed numerals, one brand green, and a fixed colour code for ball outcomes that means the same thing on every screen.

## Principles

- **Numbers lead.** Scores use a condensed display face with tabular figures, so digits don't jump as they change.
- **Colour carries meaning.** Blue is a four, violet a six, red a wicket, amber an extra. Nothing else uses those colours.
- **Thumb zone.** Scoring controls sit in the bottom 40% of the screen.
- **Sunlight first.** Scoring screens have 7:1 contrast or better. Dark theme is designed as its own theme, not an inversion.
- **Calm.** One primary action per screen. No ads or upsells on scoring or match screens.

## Colour

### Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#F5F7F4` | `#0C110E` | Screen background |
| `surface` | `#FFFFFF` | `#141B17` | Cards, sheets |
| `sunk` | `#ECF0EC` | `#1A231E` | Inputs, table headers, pressed states |
| `ink` | `#111A15` | `#E6EDE8` | Primary text, scores |
| `muted` | `#56635B` | `#95A39A` | Secondary text, captions |
| `line` | `#D8DFD9` | `#27322C` | Dividers, borders |
| `brand` | `#0B7A4B` | `#3CC788` | Primary actions, selection |
| `brandSoft` | `#DDF1E6` | `#143325` | Selected backgrounds, callouts |
| `onBrand` | `#FFFFFF` | `#06140D` | Text and icons on `brand` |
| `brandStrong` | `#0B7A4B` | `#0E5A3A` | Full-bleed brand areas (sign-in header); dark in both themes |
| `onBrandStrong` | `#FFFFFF` | `#F2F7F4` | Text and art on `brandStrong` |
| `event.four` | `#2459D6` | `#6D9BFF` | Boundary 4 |
| `event.six` | `#6D3BD8` | `#A68BFF` | Boundary 6 |
| `event.wicket` | `#D22F3B` | `#FF6B74` | Wicket, LIVE badge, destructive actions |
| `event.extra` | `#A86B12` | `#E3AE4C` | Wd, Nb, B, Lb, penalty |
| `event.dot` | `#8A958E` | `#6E7A73` | Dot ball |

### Rules

- Neutrals carry a slight green bias. Never use pure grey.
- `event.*` colours are only for ball outcomes (and `event.wicket` for LIVE and destructive actions). Don't use them for decoration.
- Text on `event.four`, `event.six` and `event.wicket` fills is white in both themes.
- Body text on `bg` and `surface` must reach 4.5:1; scores and scoring controls 7:1.

## Fonts

### Families

| Family | Role | Files to bundle | Why |
|---|---|---|---|
| Barlow Condensed | Display: scores, titles, run pad digits | `barlow_condensed_semibold.ttf` (600), `barlow_condensed_bold.ttf` (700) | Narrow figures fit big scores on small screens and read like a scoreboard |
| Figtree | Body and UI text | `figtree_variable.ttf` (variable, 400–700) | Clear at small sizes, friendly, one file covers every weight |
| JetBrains Mono | Data: overs, run rates, figures in tables | `jetbrains_mono_variable.ttf` (variable, used at 500) | Fixed-width digits line up in scorecards |

All three are from Google Fonts under the SIL Open Font License (OFL).

### Loading

- **Bundle the files in the app.** Don't use downloadable fonts: scoring must look right offline and on first launch.
- **Location:** `app/designsystem/src/commonMain/composeResources/font/`, loaded with `Font(Res.font.…)` from Compose resources. Fonts are never added to feature modules.
- **Weights:** only the four files above. Adding a weight means adding a file, so ask first.
- **Fallback:** if a font fails to load, fall back to the system sans (`FontFamily.SansSerif`) and system mono (`FontFamily.Monospace`).
- **Tabular figures:** set `fontFeatureSettings = "tnum"` on every style that shows numbers that change or line up (`score.*`, `data`, table cells).
- **Licence:** ship the OFL text and list the fonts on the open-source licences screen.
- **Font scaling:** text follows the system font size. Scores cap at 1.3× so the score header never wraps; everything else scales freely.

### Type scale

Sizes in sp. Line heights are fixed so lists keep their rhythm.

| Style | Family | Size / line height | Weight | Extra | Example |
|---|---|---|---|---|---|
| `score.xl` | Barlow Condensed | 56 / 56 | 700 | tnum | 187/4 on the score header |
| `score.lg` | Barlow Condensed | 40 / 40 | 700 | tnum | Innings totals on the scorecard |
| `score.md` | Barlow Condensed | 24 / 28 | 700 | tnum | Match cards, run pad digits |
| `title.lg` | Barlow Condensed | 32 / 36 | 700 | | Screen titles |
| `title.md` | Barlow Condensed | 22 / 26 | 600 | | Section and card titles |
| `body.lg` | Figtree | 17 / 26 | 400 | | Commentary |
| `body` | Figtree | 15 / 22 | 400 | | Default text |
| `body.strong` | Figtree | 15 / 22 | 600 | | Player names, emphasis |
| `button` | Figtree | 15 / 20 | 600 | | Button labels |
| `label` | Figtree | 12 / 16 | 600 | caps, +0.1em tracking | CURRENT RUN RATE |
| `caption` | Figtree | 13 / 18 | 400 | | Timestamps, helper text |
| `data` | JetBrains Mono | 13 / 18 | 500 | tnum | 14.3 ov · CRR 9.21 |

Rules:
- Barlow Condensed only at 22sp and up. Below that, use Figtree.
- Never use more than three styles in one card.
- Uppercase is only for `label`.

## Spacing

A 4dp base grid. Every margin, padding and gap uses a token.

| Token | dp | Typical use |
|---|---|---|
| `space.1` | 4 | Icon to label, chip inner gap |
| `space.2` | 8 | Gap between balls in an over strip, between run pad keys |
| `space.3` | 12 | Gap between rows inside a card |
| `space.4` | 16 | Screen side margin, card padding, gap between cards |
| `space.5` | 20 | Sheet padding |
| `space.6` | 24 | Gap between sections |
| `space.8` | 32 | Top of screen to first section |
| `space.12` | 48 | Empty-state padding |

Rules:
- Use `Arrangement.spacedBy(...)` for gaps between siblings, not per-item padding.
- Screen side margins are always `space.4` (16dp), plus system bar insets (the app is edge-to-edge).

## Layout

- **Phone first.** Design at 360dp wide; check at 320dp and 412dp.
- **Content width:** at most 600dp, centred on tablets and foldables.
- **Scoring screen:** score header on top, current over and batters in the middle, run pad anchored to the bottom inside the navigation-bar inset.
- **Lists:** rows are 56dp minimum (72dp with an avatar).

## Touch targets

- 48dp minimum for any tappable element.
- 64dp minimum for run pad keys and the wicket button.
- `space.2` (8dp) minimum between adjacent targets on the scoring screen.

## Shape

| Token | Radius | Use |
|---|---|---|
| `radius.sm` | 6dp | Chips in tables, small badges |
| `radius.md` | 12dp | Cards, inputs |
| `radius.lg` | 20dp | Bottom sheets, run pad keys |
| `radius.full` | 50% | Buttons, ball chips, avatars, pills |

## Elevation

- Cards are flat: 1dp `line` border, no shadow.
- Bottom sheets and the floating scoring bar use one shadow level (Material level 2).
- Dialogs use Material level 3. Nothing else is raised.

## Icons

### Source: Google Material Symbols

Download each icon from [Google Fonts Icons](https://fonts.google.com/icons) as it's needed. Don't add the `material-icons-extended` library: it's large and only has the older Material Icons set.

Download settings (the same for every icon):

| Setting | Value |
|---|---|
| Style | Material Symbols **Rounded** |
| Fill | 0 (outlined); also download Fill 1 for icons used as a selected tab |
| Weight | 400 |
| Grade | 0 |
| Optical size | 24 |
| Format | **Android** (vector drawable XML) |

### Adding an icon

1. Search for it on Google Fonts Icons, set the options above, and download the Android XML.
2. Rename it to `ic_<name>.xml` (snake_case, using Google's icon name), or `ic_<name>_filled.xml` for the filled version, e.g. `ic_sports_cricket.xml`, `ic_home_filled.xml`.
3. Put it in `app/designsystem/src/commonMain/composeResources/drawable/`. Icons never go in feature modules.
4. Delete any hardcoded `android:tint` and set `android:fillColor` to `#FF000000`, so the colour comes from code.
5. Expose it through `DesiIcons` in `designsystem/icon/` (e.g. `DesiIcons.Cricket`) and use it as `Icon(painterResource(DesiIcons.Cricket), contentDescription = stringResource(...))`.
6. Only download what a screen uses now; don't bulk-import.

### Usage

- 24dp by default; 20dp inside chips and dense rows; 32dp for empty states.
- Icon colour follows the text next to it (`ink` or `muted`), or `onBrand` on brand buttons. Never use `event.*` colours on icons except in scoring.
- Filled icons only for the selected bottom-navigation tab.
- Every icon that means something gets a `contentDescription` from string resources; decorative icons pass `null`.

## Custom assets

Some things have no Google icon (cricket bat, stumps, gloves, a ball with seam) or need artwork (empty states, onboarding, share cards, app icon). These get made when a screen needs them, not up front.

### When to make one

- No Material Symbol communicates the idea clearly. Check first; `sports_cricket` and `sports_baseball` cover some cases.
- It is listed as a needed asset in the phase being built (see the table below).

### Rules for custom icons

- Draw on a 24×24 grid with a 2dp live-area padding, matching Material Symbols Rounded: 2dp strokes, rounded caps and joins, 2dp corner radius.
- Single colour, exported as Android vector XML with no tint. Same naming (`ic_<name>.xml`) and same folder as Google icons.
- Source SVG kept in `design/icons/` at the repo root, so it can be edited later.

### Rules for illustrations and other artwork

- Two colours at most from the palette (`brand` plus `ink` or `muted`), so they work in light and dark. If a version per theme is needed, add `drawable-night/`.
- Vector XML when possible; WebP (not PNG) for anything raster, exported at 1x, 2x, 3x.
- Naming: `ill_<name>` for illustrations, `img_<name>` for raster images, `bg_<name>` for backgrounds.
- Source files (Figma or SVG) kept in `design/` at the repo root.

### App icon

A white ball with a turf-green seam, moving across a pitch strip, on `brand` green.

- Sources: `design/icons/app_icon_background.svg`, `app_icon_foreground.svg`, `app_icon_monochrome.svg` (108×108, content inside the 66dp safe zone).
- Android: `app/androidApp/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, generated from the SVGs, wired up in `mipmap-anydpi-v26/`. No PNG mipmaps, since min SDK is 26.
- Play Store listing: `design/icons/play_store_icon_512.png`.
- The monochrome layer (Android 13+ themed icons) outlines the ball so the seam stays visible in one colour.

### Assets by phase

| Phase | Asset | Type |
|---|---|---|
| 0 | App icon (adaptive, plus monochrome for themed icons) ✅ | Custom |
| 0 | Sign-in screen artwork | Illustration |
| 1 | Bat, ball, stumps, gloves icons | Custom icon |
| 1 | Empty state: no matches yet | Illustration |
| 1 | Wagon wheel field graphic | Custom vector |
| 2 | Empty state: no teams yet | Illustration |
| 2 | Default team logo | Custom vector |
| 3 | Share card templates (scorecard, milestone) | Artwork |
| 4 | Trophy / tournament header artwork | Illustration |
| 5 | Milestone badges (50, 100, 5-wicket haul, hat-trick) | Custom vector |

## Motion

| Token | Duration | Easing | Use |
|---|---|---|---|
| `motion.quick` | 100ms | standard | Button press, chip select |
| `motion.base` | 200ms | standard | Sheet open, tab change |
| `motion.score` | 300ms | emphasized decelerate | Score counting up, new ball chip sliding in |

- Every scoring tap gets haptic feedback: light for runs, heavy for a wicket.
- When the system "remove animations" setting is on, change values instantly.

## Components (first set)

All in `designsystem/component/`. Light and dark previews are in `Catalogue.kt`.

| Component | File | What it is |
|---|---|---|
| `BallChip` / `OverStrip` | `Scoring.kt` | One delivery as a 32dp circle coloured by event token; a row of them for the current over |
| `ScoreHeader` | `Scoring.kt` | Label, score (`score.xl`), overs (`data`) and an optional target / run-rate line |
| `RunPad` | `Scoring.kt` | 4-column grid of 64dp keys; the caller defines the keys and what they do |
| `MatchCard` | `Content.kt` | A match in a feed: status slot (e.g. `LiveBadge`), meta, team lines, footer |
| `LiveBadge` | `Content.kt` | Red dot plus LIVE |
| `PlayerRow` / `Avatar` | `Content.kt` | Avatar (photo slot, initial or person icon), name, secondary line |
| `SyncIndicator` | `Content.kt` | Count of deliveries waiting to sync; hidden at zero |
| `DesiCard` / `SectionLabel` | `Content.kt` | Flat bordered surface; uppercase section label |
| `PrimaryButton` / `TonalButton` / `GhostButton` | `Buttons.kt` | 52dp pills. Primary: brand fill, optional trailing arrow, spinner while loading, faded brand when disabled. Tonal: soft `sunk` fill with a brand-tinted leading icon, for secondary options. Ghost: outline |
| `DesiScaffold` / `FullScreenLoading` | `Structure.kt` | Screen frame with title and back icon; centred spinner |
| `DesiTextField` | `Structure.kt` | Labelled field with helper / error line and optional password masking |
| `ChoiceChips` / `ChoiceSheet` | `Structure.kt` | Single-choice chips; bottom sheet of options |
| `PhoneNumberField` | `PhoneNumberField.kt` | Label above; country pill, divider, then digits in 26sp display type grouped "98765 43210"; tick when complete |
| `OtpField` | `OtpField.kt` | One-time code in separate boxes; one text field underneath, so paste and SMS autofill work |
| `SegmentedControl` | `Selection.kt` | 2–4 exclusive options in one pill with a sliding raised thumb (batting hand, pace or spin) |
| `OptionCard` | `Selection.kt` | Large choice with icon, title and one-line description, laid out in a grid; brand outline and tick when selected (playing role) |
| `BrandHeader` | `Forms.kt` | Green header for form screens: back, large title, subtitle, and an optional card that hangs below the green |
| `BrandStatusBar` | `Forms.kt` | Fixed green strip behind the status bar, so its light icons stay readable while a `BrandHeader` scrolls away |
| `NumberStepper` | `Forms.kt` | − and + around a big number with a caption (overs per innings) |
| `RemovableChip` | `Forms.kt` | Brand-soft chip with a remove button, for items added to a list (players in a squad) |
| `DesiSheet` | `Structure.kt` | Bottom sheet with a title and hint, for quick decisions in a flow (pick a player, runs off an extra, how out) |
| `PlayerCard` | `Selection.kt` | Avatar with initials, name, role line and style tags; the live preview on profile setup |

Illustrations live in `DesiIllustrations` (drawn with `Image`, not tinted). `BallMark` is the app icon's ball and speed lines, used in the sign-in header.

The wicket sheet (dismissal type, then only the fields that type needs) is built in the scoring feature on top of `ChoiceSheet`, since it needs cricket rules from `core`.

## The `:app:designsystem` module

Crease lives in its own Gradle module, `:app:designsystem`. It is the only place tokens, fonts and shared components are defined. Feature code never defines its own colours, text styles or reusable components.

### Setup

1. Create `app/designsystem/` and add `include(":app:designsystem")` to `settings.gradle.kts`.
2. Give it a `build.gradle.kts` with the same plugins as `:app:shared` (`kotlinMultiplatform`, `androidMultiplatformLibrary`, `composeMultiplatform`, `composeCompiler`), an Android target only, and namespace `com.harsh.desicrics.designsystem`.
3. Dependencies: `compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui`, `compose.components.resources`, `compose.uiToolingPreview`. Nothing else.
4. In `:app:shared`, add `implementation(project(":app:designsystem"))`.
5. Make the module's Compose resources public so features can use them:
   ```kotlin
   compose.resources {
       publicResClass = true
       packageOfResClass = "com.harsh.desicrics.designsystem.resources"
   }
   ```

### Layout

```
app/designsystem/src/commonMain/
├── kotlin/com/harsh/desicrics/designsystem/
│   ├── theme/
│   │   ├── DesiTheme.kt        DesiTheme { } + CompositionLocals
│   │   ├── Color.kt            light + dark token sets
│   │   ├── Type.kt             font families + type scale
│   │   ├── Spacing.kt          space tokens
│   │   ├── Shape.kt            radius tokens
│   │   └── Motion.kt           durations + easings
│   ├── component/              BallChip, OverStrip, ScoreHeader, RunPad, buttons, …
│   └── icon/                   DesiIcons: one entry per drawable icon
└── composeResources/
    ├── font/                   the four font files
    ├── drawable/               ic_* (Material Symbols + custom), ill_*, img_*
    └── values/strings.xml      strings used inside components (e.g. "LIVE", "Undo")
```

### Rules

- **Dependency direction:** features depend on `:app:designsystem`; it never depends on `:app:shared`, `:core` or any feature. It knows nothing about matches, players or the API.
- **Stateless components:** components take plain values and callbacks (`BallChip(outcome: BallOutcome, …)`), never ViewModels or repositories.
- **Text comes in from the caller:** components take `String` parameters for their content, so the feature resolves its own string resources. The module only keeps strings for text that is part of the component itself, such as the LIVE badge.
- **Every component has previews** in light and dark, and a screenshot test.
- **New shared components go here.** If two screens need the same piece of UI, move it into this module rather than copying it.

### Theme API

- `DesiTheme { }` wraps `MaterialTheme` (mapping `brand` to `primary`, `surface` to `surface`, and so on) and exposes the extra tokens through `CompositionLocal`:
  - `DesiTheme.colors.event.four`
  - `DesiTheme.type.scoreXl`
  - `DesiTheme.space.s4`
  - `DesiTheme.radius.md`
- Screens never use raw hex values, dp numbers or `TextStyle(...)` directly; they use tokens.
- Screenshot tests for every component in light and dark, and at 1.3× font scale.
