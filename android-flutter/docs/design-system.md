# ShortLink Design System

The Flutter client is a rebuild of the Kotlin/Compose app with the **same
functional surface and a completely new visual language**. This document is the
contract for that language: every token, every rule and every deliberate
deviation from the original.

The implementation lives in `lib/theme/` (tokens and theme assembly) and
`lib/ui/components/` (the components built from those tokens). The app shell
adds one layer on top of both: `lib/ui/routing/` holds the declarative page
stack and the back-priority contract, and `AppSubPage` is the shared chrome a
pushed sub-page wears (§4.1).

---

## 1. Why neutral chrome

The original app was dark-only with a gold accent. Gold doubled as the brand
colour, the reward colour, the highlight colour and the "premium" colour, so
nothing stood out: every screen was the same hue at the same weight, and there
was no visual hierarchy to tell a poster from a button from a badge.

The new design inverts the priority:

> **The chrome is neutral; the posters carry all the colour.**

Canvas, surfaces, text and separators are near-greyscale. Saturated colour is
spent only where it means something — a primary action, a reward, a success, a
warning, a failure. The result is that the drama artwork is the only richly
coloured thing on screen, which is exactly what a streaming catalogue wants.

Two things follow from this that shape the whole system:

1. **Accent roles are state, not decoration.** There is no "brand gold"
   sprinkled on headings. If something is violet, it is interactive; if it is
   coral, it is a reward; if it is rose, it is dangerous.
2. **Every state must survive greyscale.** Because colour is rationed, no state
   is allowed to be carried by colour alone — see §6.

---

## 2. Colour tokens

Registered as `AppColors extends ThemeExtension<AppColors>`; read with
`Theme.of(context).extension<AppColors>()!`. **No widget may contain a raw hex
literal.** `AppColors.light` and `AppColors.dark` are const; `colorScheme`
projects them onto Material's roles so unmodified Material widgets stay in the
palette.

### Neutral roles

| Role | Light | Dark | Purpose |
| --- | --- | --- | --- |
| `canvas` | `#F7F8FA` | `#0B0D11` | Page background behind every surface |
| `surface` | `#FFFFFF` | `#14171D` | Raised plane: cards, sheets, bars |
| `surfaceAlt` | `#EEF0F4` | `#1C2027` | Recessed plane inside a surface; field fills |
| `textPrimary` | `#14161B` | `#F4F5F7` | Body and headings |
| `textSecondary` | `#5A6070` | `#9AA1AE` | Captions, subtitles, meta rows |
| `hairline` | `#DDE1E8` | `#262B33` | Decorative separators **only** |
| `outlineStrong` | `#7E8492` | `#727886` | Boundaries and indicators that must be perceivable |
| `scrim` | `#0B0D11` | `#000000` | Modal barrier |
| `shadow` | `#5A6070` | `#000000` | Elevation shadow |
| `focusRing` | `#6C4DFF` | `#9C8BFF` | Keyboard focus ring |

`hairline` and `outlineStrong` are **not** interchangeable, and the contrast
audit asserts their difference is real:

* `hairline` measures 1.31:1 (light) / 1.26:1 (dark) against `surface`. That is
  correct for a ledger divider or the decorative scaffold of the episode spine.
  It is **not** enough to identify a control.
* `outlineStrong` measures 3.75:1 (light) / 4.06:1 (dark) against `surface`, and
  ≥3:1 against every neutral surface in its brightness. It is used for control
  boundaries: button outlines, input borders, chip outlines, and the un-watched
  ticks of the episode spine.

### Accent roles

| Role | Light | Dark | Meaning |
| --- | --- | --- | --- |
| `accentPrimary` | `#6C4DFF` | `#9C8BFF` | Brand, primary action, interactive, info |
| `accentReward` | `#C2410C` | `#FF8A72` | Reward ring, `+N pt` toast |
| `accentSuccess` | `#00776C` | `#3AD3C4` | Confirmed, settled |
| `accentWarning` | `#8A6100` | `#FFC24D` | Pending, limited, VIP gate |
| `accentDanger` | `#C62A44` | `#FF6B84` | Destructive, failure, negative movement |

Each accent has a matching `on*` foreground (`onAccent`, `onReward`,
`onSuccess`, `onWarning`, `onDanger`). White in light mode, `#0B0D11` in dark
mode.

`premiumSurface` is a derived, **translucent** tint: `accentPrimary` at 8 % alpha
(light) / 14 % (dark). It is used for membership blocks, the selected chip and
`ListTile.selectedTileColor`. It never carries text without being composited over
a plane first.

### Measured contrast

Every value below is asserted by the contrast audit (see §7), so these numbers
cannot silently drift:

| Pair | Light | Dark |
| --- | --- | --- |
| `textPrimary` on `surface` | 18.10 | 16.45 |
| `textPrimary` on `canvas` | 17.03 | 17.83 |
| `textPrimary` on `surfaceAlt` | 15.86 | 14.98 |
| `textSecondary` on `surface` | 6.28 | 6.91 |
| `textSecondary` on `canvas` | 5.91 | 7.49 |
| `textSecondary` on `surfaceAlt` | 5.51 | 6.29 |
| `accentPrimary` on `surface` | 5.07 | 6.45 |
| `accentReward` on `surface` | 5.18 | 7.80 |
| `accentSuccess` on `surface` | 5.45 | 9.65 |
| `accentWarning` on `surface` | 5.54 | 11.18 |
| `accentDanger` on `surface` | 5.50 | 6.57 |
| `onAccent` on `accentPrimary` | 5.07 | 6.99 |
| `onReward` on `accentReward` | 5.18 | 8.45 |
| `onSuccess` on `accentSuccess` | 5.45 | 10.46 |
| `onWarning` on `accentWarning` | 5.54 | 12.11 |
| `onDanger` on `accentDanger` | 5.50 | 7.12 |
| `outlineStrong` on `surface` | 3.75 | 4.06 |
| `textSecondary` on `premiumSurface` over `surface` | 5.64 | 6.04 |

**Targets.** 4.5:1 for all text and for accents used as text; 4.5:1 in both
directions for an accent that must also work as a fill (accent-as-text *and*
white/dark text on the accent); 3:1 for boundaries, focus rings and non-text
state indicators.

**Why the light accents are deeper than the dark ones.** On a near-white canvas a
bright teal or amber cannot clear both requirements at once. `#0FB5A6` as text on
white is 2.57:1, and white text on it is also 2.57:1 — it fails as text, as a
fill label and as an icon. Deeper accents satisfy both directions simultaneously
(`#00776C` is 5.45:1 each way), and they also read better against a white
canvas, where the bright versions looked washed out. Dark mode keeps the brighter
values because there the canvas does the work.

---

## 3. Typography

Registered as `AppTypography extends ThemeExtension<AppTypography>`. There are
**no bundled font files and no runtime font downloads**: the system uses the
platform UI family (Roboto on Android) and makes the four roles visually
distinct through weight, size, letter-spacing, case and line height.

| Role | Size | Weight | Letter spacing | Line height | Used for |
| --- | --- | --- | --- | --- | --- |
| `display` | 13 | w700 | 1.0 | 1.2 | All-caps section headers, then `toUpperCase()` |
| `displayLarge` | 26 | w800 | −0.6 | 1.2 | Screen titles, hero figures |
| `body` | 15 | w400 | 0.1 | 1.5 | All running copy |
| `numeric` | 15 | w600 | 0.0 | 1.3 | Money, points, counts, timers |

`display` is tight-tracked and heavy; `displayLarge` is the only negative
tracking in the system, which is what makes a title read as a title without a
second font. `body` is deliberately loose (1.5 line height) for long
descriptions.

**Tabular figures are mandatory for numbers.** `numeric` carries
`FontFeature.tabularFigures()`, and every figure rendered outside it
(`AppLedgerRow` values, spine percentages) adds the same feature explicitly. This
is what makes a column of balances line up digit for digit instead of jittering
as the values change.

`buildAppTextTheme(brightness)` derives the complete 15-slot Material `TextTheme`
from these roles. Because Material merges its 2021 defaults underneath and the
platform typography supplies a colour, **every derived style sets an explicit
colour**; the contrast and theme tests assert this so a future edit cannot leave
a style inheriting `black54`.

`sectionHeaderTransform(text)` is the single place that decides section headers
are upper-cased, so the convention cannot leak into one screen and not another.

---

## 4. Shape, spacing and elevation

Registered as `AppShapes extends ThemeExtension<AppShapes>`. Identical in light
and dark, because shape is not a brightness concern.

| Token | Value | Applies to |
| --- | --- | --- |
| `radiusInline` | 4 | Pills, chips, progress track, tooltips, counters |
| `radiusControl` | 10 | Buttons, text fields, cards in a list, sheets |
| `radiusContainer` | 16 | Content cards, posters |
| `radiusMedia` | 20 | Full-bleed media: player surface, hero art |
| `strokeHairline` | 1.0 | Default border weight |
| `strokeEmphasis` | 1.5 | Focus rings and the current episode tick |
| `elevationFlat` | 0 | Cards that sit on the canvas |
| `elevationSticky` | 1 | App bar, message bar |
| `elevationFloating` | 6 | Sheets, dialogs, snack bars |

`AppShapes` also exposes `containerBorder({sideColor})` so the
"radius + hairline" pair is never re-typed by hand, and the getters
`inlineRadius` / `controlRadius` / `containerRadius` / `mediaRadius` return
`BorderRadius` rather than doubles.

The rule of thumb: **radius grows with the size of the thing and shrinks with its
interactivity.** A 4 px pill is a chip; a 20 px corner is artwork.

### 4.1 Sub-page chrome

`AppSubPage` is the one place a pushed sub-page gets its frame. Before it, three
screens carried three private `_pagePadding` constants, two different title
roles (`displayLarge` on one screen, `titleLarge` on another) and one page with
no header at all.

| Part | Token |
| --- | --- |
| Page inset | The `pageHorizontal` / `pageTopInset` / `pageBottomInset` roles, composed into one `EdgeInsets` (`pageBottomInset + viewInsets.bottom` when the host does not resize for the keyboard) |
| Title | `AppTypography.screenTitle` — the single page-title role, also what `appBarTheme.titleTextStyle` uses |
| Back affordance | 44 px target, `AppUiCopy.back` as both tooltip and semantic label |
| Top safe area | Handled once by the chrome, so no screen needs its own `SafeArea` |
| Keyboard | `MediaQuery.viewInsets.bottom` added to the bottom inset when the host does not resize |

It deliberately builds no `Scaffold`: the shell owns the canvas and the bottom
navigation bar, and a nested scaffold would paint a second background and answer
the keyboard with its own resize. `pageBottomInset` is reserved inside the
scroll view, which is what keeps a section heading from ending up half-hidden
behind the navigation bar.

---

## 5. The Episode Spine

The signature element, and the one piece of pure invention in the system. It
replaces the original app's episode grid.

> A thin vertical rail carries **one tick per episode**. The rail is the series;
> each tick is one episode; a tick's fill is that episode's watch state.

```
 │  ▬▬▬▬▬▬▬▬▬   watched       (solid accent fill, 60 % tick width)
 │  ╔════════╗   current       (full width, accent outline)
 │  ▬▬▬▬      in progress   (partial accent fill from the left)
 ├╌╌╌╌╌╌╌╌╌╌   VIP gate      (wider + taller, warning marker below)
 │  ▬▬▬▬▬▬      untouched     (outlineStrong, 60 % tick width)
```

### Anatomy

* **Rail** — `hairline`, 2 px (1.5 px at mini). Spans the whole series; it is
  scaffold, never state.
* **Tick** — one per episode. Width and height encode state, so the spine reads
  in greyscale:
  * *untouched*: 60 % width, thin, `outlineStrong`
  * *watched*: 60 % width, thin, solid `accentPrimary`
  * *in progress*: watched geometry, `accentPrimary` filled to `progress` %
  * *current*: **full width, taller, accent outline** — the only tick that grows
  * *VIP-locked*: any of the above at 70 % opacity
* **Gate marker** — at the `episodeSpineGateEpisode` boundary (**episode 8**,
  matching `defaultVipFreeEpisodes = 7`: episodes 1–7 free, 8+ need VIP). Drawn
  wider and taller than any other tick with an `accentWarning` notch beneath it,
  so the paywall is a visible landmark rather than a surprise tap.

### The three sizes

| Size | Orientation | Interactive | Where |
| --- | --- | --- | --- |
| `full` | Vertical; 16 px visual rows, **44 px pitch** | Yes — each tick is its own merged `Semantics` node with a tap action, and its target is a 44 × 44 square (`episodeSpineMinTargetExtent`) | Detail page, player episode drawer |
| `mini` | Vertical, 12 px rows | No | Inside a poster card |
| `inline` | Horizontal dash run, wraps | No | Dense ledger/history rows |

**The full-size target, not the tick, is 44 px.** The visual tick keeps its own
geometry (60 % / full width, 4–5 px tall) and is centred inside a 44 px row, so
the row pitch equals the target extent and two neighbouring targets share an edge
without ever overlapping. Widening the row to 44 px is unavoidable: the old
interactive row was ~23 × 16 px, which is below the platform minimum in both
directions. A tick without an `onTap` keeps the 16 px row, so an informational
spine does not grow.

`mini` takes an `availableHeight` and scales itself with `FittedBox` rather than
overflowing the artwork it annotates. `PosterCard` measures its own inner height
and passes it down, so a 200-episode series simply renders smaller instead of
painting outside the card.

`PosterCard` also **windows** the mini spine: beyond 30 episodes it shows the 30
ticks around the current episode instead of every episode, because at that point
a tick is thinner than a physical pixel. The window is a compression of the
series, not a claim about it — the card's accessible name still states the real
episode count.

### Describing it

`EpisodeSpine.describe(ticks, language)` builds the summary announced to screen
readers (for example `12 eps, 2 Watched, Current EP 03, 1 in progress`) using
**only** strings that already exist in `AppStrings` plus the redesign-only
`AppUiCopy` entries (the in-progress word, which `AppStrings` never had), so the
description stays localisable and the component introduces no new user-visible
wording of its own.

See §6 for how `full` keeps its ticks reachable while still announcing that
summary.

---

## 6. Motion

Registered as `AppMotion extends ThemeExtension<AppMotion>`.

| Token | Duration | Curve | Used for |
| --- | --- | --- | --- |
| `reelChange` | 220 ms | `Cubic(0.22, 1, 0.36, 1)` | Screen and sub-page transitions |
| `rewardRingFill` | 900 ms | `Cubic(0.18, 1.32, 0.4, 1)` | Reward ring sweep (spring-filled) |
| `rewardToast` | 1600 ms | `easeOutCubic` | Rising `+N pt` toast |
| `controlState` | 120 ms | `easeOut` | Hover/focus/press washes |
| `shimmerCycle` | 1400 ms | — | Skeleton shimmer sweep |
| `messageAutoDismiss` | 4000 ms | — | Top message bar lifetime |

### The "reel change"

Screen and sub-page transitions use `ReelChangePageTransitionsBuilder`, installed
for **all six** `TargetPlatform` values. It composes a clip, a fade (0.88 → 1)
and two slides: the incoming route travels 6 % of the viewport, the outgoing
route 2.5 %. The effect is a short vertical wipe — the way a film reel changes —
rather than Material's default horizontal slide. It is deliberately small: a
large travel on a content-dense screen reads as "slow", not as "cinematic".

### Reduced motion

> **Every animation must be skipped when `MediaQuery.of(context).disableAnimations`
> is true.**

`AppMotion.resolve(context, duration)` returns `Duration.zero` in that case, and
`resolveAppMotion(context, duration)` is the top-level shorthand. Components call
it rather than reading a duration directly:

* `AppSkeleton` stops its shimmer and renders a flat `surfaceAlt` plate — still
  legible as "not yet loaded", but motionless.
* `AppTopProgressBar` stops the indeterminate sweep and renders a static accent
  hairline.
* `AppMessageBar` renders its entrance in place and skips the countdown strip.
* `ReelChangePageTransitionsBuilder` drops the clip, fade and both slides, so a
  pushed route simply appears. A route's own animation is driven by the
  framework, so collapsing a duration cannot work here — the transitions
  themselves have to be omitted.

**One documented exception:** the indeterminate spinners in `AppInlineLoading`
and the button loading state keep rotating. They are the *only* signal that a
background action is in flight; a frozen spinner reads as a hung app, which is a
worse outcome than the motion. The rule this respects is that reduced motion
removes decorative movement, not the minimum motion required to convey state.

---

## 7. Accessibility commitments

These are enforced by the test suite in §8, not merely intended.

1. **Contrast.** 4.5:1 for text and text-like accents, 4.5:1 both ways for
   accent fills, 3:1 for boundaries, focus rings and state indicators. Measured in
   §2. `hairline` is asserted to stay *below* 3:1 so it can never be
   repurposed as a control boundary without the test failing.
2. **No colour-only signalling.** Every tone and state has a second channel:
   * `AppMessageBar` shows a per-tone glyph **and** a per-tone word
     ("Success" / "Error" / "Info") beside the message.
   * Episode ticks differ in **width, height and fill** as well as colour.
   * A field's error state is carried by the message itself — explicit prose such
     as "Password must be at least 8 characters" — alongside an `accentDanger`
     border that also changes weight when focused. Nothing hinges on noticing a
     hue, and Material exposes the error text as a live region so it is announced
     when it appears.
   * A selected chip carries a checkmark, not just a tint.
3. **Merged semantics, one announcement per object.** A `PosterCard` is a single
   node labelled from `posterCardContentDescription(title, episodeCount,
   language)`; the cover image is marked decorative via
   `posterImageIsDecorativeInsideCard()`. A screen reader says
   *"Drama, 12 eps"* once, not "image", "title" and "12".
4. **Actions survive `excludeSemantics`.** `Semantics(excludeSemantics: true)`
   *replaces* descendant semantics, which would silently drop an `InkWell`'s tap
   action. Every component that merges its children therefore puts the
   activation action on the merging node itself (`onTap:`), and a disabled
   control passes `onTap: null` so it exposes no action at all. This is asserted
   for buttons, poster cards, spine ticks and the message bar.
5. **Live regions.** `AppMessageBar` is `liveRegion: true` and gated by
   `topMessageUsesLiveRegion()`; `AppInlineLoading` announces what is loading;
   field errors are surfaced through `InputDecoration.errorText`, which Material
   already exposes as live.
6. **Keyboard reachability and focus.** Focusable components wear a focus ring in
   `focusRing` at `strokeEmphasis`. `AppButton` activates exactly once per
   gesture *or* key press (`Enter`, numpad `Enter`, `Space`) because the pointer
   path goes to the `InkWell` while the keyboard path goes to the ancestor
   `FocusableActionDetector`'s `ActivateIntent`, and the `InkWell` never requests
   focus itself.
7. **Target size.** `AppButton` guarantees a 44 px minimum height itself, so a
   compact toolbar cannot shrink it below the touch target. The theme sets
   `MaterialTapTargetSize.padded` for Material controls. Four components carry
   their own minimum, because their visual geometry is smaller than a finger:
   * `EpisodeSpine.full` ticks: 44 × 44, row pitch included (§5);
   * `AppSubPage` back affordance: `AppSubPage.backHitSize` (44);
   * `AppTextField` password reveal toggle: 44 × 44 while the glyph stays small;
   * `AppMessageBar` close button: 44 × 44 while the icon stays 18 px.
   Each of these is asserted by a `tester.getRect`/`getSize` assertion rather
   than by eye.
8. **Responsive from 320 px to 1440 px, at any text scale.** Components either
   grow, wrap or restructure — they never clip:
   * `AppLedgerRow` stacks its label above its value below 260 px or above 1.6×
     text scale.
   * `AppEmptyState` bounds its copy to 420 px and becomes vertically scrollable.
   * `PosterCard` limits its overlaid title to
     `posterOverlayTitleMaxLines(fontScale)` lines (2, dropping to 1 at 1.3×).
   * `EpisodeSpine.mini` scales down instead of overflowing.
   * `AppButton` labels wrap to two lines then ellipsise.
   * `AppSkeleton` with no explicit `width` fills the incoming constraints, so the
     placeholder covers the same box the real content will: a skeleton needs a
     **bounded** width parent (a panel, a list, a sliver), and an unbounded one
     (a bare `Row`, a horizontal `ListView`) must pass `width` explicitly.
     `AppSkeletonList` rows therefore measure `parent width − padding`, which is
     asserted rather than assumed — a zero-width plate is invisible, and that is
     exactly the failure this contract exists to prevent.
9. **Non-interactive means non-interactive.** `AppEmptyState` renders a real
   `AppButton` when an action callback exists, and a **non-interactive pill** with
   no tap action, no focus node and no ink response when it does not — so a
   screen reader is never invited to activate something that does nothing.

---

## 8. Verifying the system

The design system is covered by a widget test suite that exercises:

* the theme in both brightnesses, including that all four extensions are
  registered and every text style carries an explicit colour;
* `copyWith`/`lerp` totality for all four extensions;
* a render matrix of every component in light **and** dark, at 320 px **and**
  1440 px, at 1×, 2× and 3.2× text scale, asserting **zero** layout exceptions
  (this is what caught the `PosterCard` mini-spine overflow);
* the WCAG audit of §2, over every role in both brightnesses;
* merged-semantics and screen-reader-activation paths for every interactive
  component (this is what caught the dropped tap actions);
* the empty state's pill contract: priority of action over pill label, that a
  callback without a label never becomes a dead button, and that the pill
  exposes no tap action and no focus;
* the text field's IME contract: reveal/mask without losing text, caret or
  focus, outside taps closing the keyboard for touch and mouse alike, and
  `next` still walking straight to the following field;
* the skeleton variants flattening to a static `surfaceAlt` plate under reduced
  motion, the poster-grid skeleton deriving its column count from the width, and
  every skeleton filling its bounded parent instead of collapsing to zero width
  (measured with `tester.getSize`, in both motion modes, including the real
  `FundsScreen` loading state);
* the measured hit targets of §7.7 (spine ticks, reveal toggle, message-bar
  close), including that neighbouring spine targets never overlap;
* reduced-motion collapse, the episode-spine description contract, the VIP gate
  boundary, and that the reel-change builder is installed on all six platforms.

The shell's navigation contract lives next to these suites, because it protects
the same token-level guarantees at the app level:

* `test/routing_contract_test.dart` — the pure `buildRouteStack` /
  `resolveBackOutcome` / `shellExitForPoppedDestination` functions, with no
  widget pumped;
* `test/navigation_shell_test.dart` — the wired shell: back priority (sheet,
  then pushed page, then `FUNDS` → `ACCOUNT`, then exit), the crossfade on a tab
  switch, the sub-page chrome, and that the global message bar stays reachable
  above a modal sheet.

---

## 9. Intentional deviations from the original dark-gold theme

| Original (Compose, dark-only) | New (Flutter, light + dark) | Why |
| --- | --- | --- |
| Dark only | Full light and dark | The platform setting is a user preference, not a brand decision. The palette is defined per brightness rather than filtered from one set. |
| Gold accent used for brand, reward, highlight and premium | Neutral chrome with five separate accent roles | One colour cannot mean four things. Gold now appears nowhere; `accentReward` is coral, so a reward reads as a reward on both canvases. |
| Episode **grid** | Episode **spine** | A grid of equal cells makes progress, the current episode and the paywall boundary look identical. The spine encodes all three in geometry, and survives inside a poster card. |
| Blocking centre progress dialog | Skeletons in the shape of the arriving content, plus a thin non-blocking top bar | A modal spinner removes the user's context and makes the layout jump on completion. Skeletons reserve the final layout, so the swap is invisible. |
| Bright accents on a dark canvas | Deeper accents in light mode, brighter in dark | A bright accent cannot be both legible text on a white canvas and a fill that carries white text. See §2. |
| Colour as the primary state signal | Shape, fill, glyph and text carry state; colour reinforces | Contrast alone is not a state channel — it fails in greyscale, in bright sunlight, and for a meaningful share of users. |
| Material default page transitions | "Reel change" 220 ms vertical wipe on all platforms | The transition is the one place the product can feel like film rather than a form; Material's default slide is horizontal and platform-inconsistent. |
| Fixed dark elevation overlays | Three explicit elevations with `applyElevationOverlayColor: false` | Material's automatic dark-mode overlay tints surfaces unpredictably, which fights a hand-tuned neutral ramp. |
