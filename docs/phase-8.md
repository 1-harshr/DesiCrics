# Phase 8 · Themes that feel like cricket

**Goal:** the app looks and feels like cricket, not like a generic green-and-white utility, and players can pick the look that matches the cricket they play.

Crease got the mechanics right: big scoreboard numerals, a fixed colour code for ball outcomes, the thumb zone, and sunlight contrast. What it lacks is a sense of place. Nothing on screen recalls a ground, a ball, a scoreboard or a gully. Phase 8 keeps Crease's rules and adds **themes**, each a complete look built from cricket's own materials.

## What stays the same in every theme

These are the design system's rules, not its look, so every theme keeps them:

- **Outcome colours keep their meaning:** four, six, wicket, extra and dot each get one colour per theme, used only for that outcome. The shades can change, but they must stay easy to tell apart from each other and from the theme's brand colour.
- **Contrast:** 7:1 for scores and scoring controls, and 4.5:1 for body text, in light and dark.
- **Thumb zone and layout:** spacing, component sizes and the run pad stay put. Themes change how things look, not where they are.
- **Bundled fonts, no downloads:** scoring has to look right offline. Each theme can add at most two font files.
- **Tabular figures** on every score.

## What a theme can change

| Part | Examples |
|---|---|
| **Colour** | Light and dark palettes for every Crease token, including outcome colours |
| **Type** | The display face for scores and titles, and optionally the body face |
| **Shape** | Corner radius (rounded cards or square scoreboard tins) and border weight |
| **Surface** | An optional texture or pattern behind headers: turf stripes, paper grain, a painted board, asphalt |
| **Motifs** | Small cricket art in empty states, loaders and share cards: stumps, seam, bails |
| **Motion** | How a wicket or boundary celebrates, within the reduce-motion setting |

## Directions to explore

Four directions get a quick visual exploration first. The same three screens (scoring, match card feed, profile stats) are drawn in each, in light and dark. The user then picks which ones ship.

1. **Red Ball** (Test cricket). Cream flannel whites, cherry-leather red as the brand colour, willow tan and pavilion green. A serif or slab display face for numerals, like an old honours board. Calm and classic.
2. **Floodlights** (T20 under lights). Dark first: stadium navy-black, an electric accent (fluoro yellow or hot pink), and an LED dot-matrix look for scores on the scoreboard header. Loud and energetic.
3. **Gully** (street and tennis-ball cricket). Taped-ball yellow, chalk-white stumps on asphalt grey, brick red, and a hand-painted Indian signage face for titles. Desi and playful, and the closest to who uses the app.
4. **Scoreboard** (the heritage manual board). Black-green painted boards, white numerals on square tins, and gold lettering. Square corners everywhere, and numbers that flip when they change.

**Crease** stays as **Classic**, so nobody loses the look they know.

## How themes are built

- **A theme is a value.** `DesiThemeSpec` holds the light and dark colours, typography, shapes, an optional surface painter, and motifs. `DesiTheme(spec, darkTheme)` provides it all through composition locals, as colours and type are provided today.
- **The existing API doesn't change.** Screens keep reading `DesiTheme.colors`, `DesiTheme.type` and so on, which picks up the new theme with no edits to them. Nothing in `:app:shared` hard-codes a colour today, and Phase 7 keeps it that way.
- **New tokens** for what themes add: `DesiTheme.shapes`, `DesiTheme.surface` (a texture behind headers and the scoreboard) and `DesiTheme.motifs` (art slots). Components read these in place of hard-coded corner radii and illustrations.
- **Picking a theme:** a Theme row in settings with a live preview of each one, saved per device. It also follows light, dark or system.
- **The share studio** (Phase 7.2) gets a theme picker of its own, so a poster can use any theme whatever the app is set to.
- **Screenshot tests** render the main screens in every theme, light and dark, and a contrast test checks each theme's tokens against the rules above.

## Levels

### Level 1 · Playable
- Theme infrastructure: `DesiThemeSpec`, the shape, surface and motif tokens, and components that read them
- Crease ported as Classic, with no visual change
- One new theme, the first pick from the exploration
- A theme picker in settings

### Level 2 · Complete
- The remaining picked directions as full themes
- The share studio's theme picker
- Screenshot and contrast tests across every theme

### Level 3 · Delight
- **Team colours:** a team's primary colour as the accent on its pages and share cards, adjusted to meet contrast
- **Tournament themes:** an organiser picks a theme and accent for the tournament's pages, posters and overlay
- Theme-specific celebrations for wickets and boundaries

**Exit:** a player switches between at least three themes, light and dark, and every main screen passes the contrast test in each.

## Where it fits with Phase 7

The four explorations are pictures, not code, so they're made while Phase 7's first wave builds. The theme infrastructure only touches `:app:designsystem` and keeps its API, so it can run alongside Phase 7's second wave. The themes themselves land after Phase 7 is merged, so they're checked against every new Phase 7 screen. See the [Gantt chart](phase-7.md#gantt-chart).
