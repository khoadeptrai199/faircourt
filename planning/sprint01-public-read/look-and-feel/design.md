# F20 design — Look and feel ("Court lines")

Status: `APPROVED` (2026-11-18, by owner: look, D-2, D-3, D-4 as in the mockup; D-1 = (a) `@fontsource/inter`).

Mockup (private to the owner): https://claude.ai/artifact/BHjynzvtfSmWp3NhMa2AQY — six artboards: public game page phone (open, closed), Game not found, error + Try again, home phone, public game page desktop.

## 1. Idea

One recognisable motif and three habits, nothing more:

1. **Court lines**: a badminton court seen from above, drawn in thin white lines over court green, on the hero card of each page; the not-found page shows an empty court with a shuttle that landed outside.
2. **Scoreboard numbers**: the facts people scan for (time, courts, minimum sets) are big, heavy numbers, like a score board.
3. **Heavy, tight headings**: Inter 800–900 with slightly negative letter spacing; body text Inter 400–600 at 16px.
4. **Calm surfaces**: off-white background, white cards, one mint price card; shuttle yellow only for small highlights (Open dot, "Coming soon", "Keep playing.").

## 2. Colors

Blueprint §6 tokens unchanged (`background #F7F8F4`, `surface #FFFFFF`, `ink #10202A`, `action #157B63`, `highlight #DDF7E8`, `accent #F9C94D`, `error #B64243`). Proposed additions (D-2):

| Token | Value | Use |
|---|---|---|
| `muted` | `#33424B` | Closed game hero card, secondary text |
| `subtle` | `#4A5A63` | Labels, disabled text |
| `disabled` | `#E4E9E5` | Disabled button, loading skeleton |
| `action-dark` | `#0F5E4B` | Link hover, small headings on mint |
| `error-soft` | `#FBE9E9` | Error icon background |

Contrast (computed from the hex values, WCAG formula): white on `#157B63` ≈ 5.2:1; `#157B63` on `#DDF7E8` ≈ 4.6:1; `#3E4F58` on `#E4E9E5` ≈ 6:1; `#B64243` on white ≈ 5.5:1. Not measured in a browser.

## 3. Layout

- Phone: one column, cards 16px apart, 16–20px padding, radius 18–28px. Join button in a bar fixed to the bottom of the screen so it is always one thumb away.
- Desktop (≥ 768px): max width 1080px; 12-column grid; hero card 7 columns (title, venue, big time), facts + price + Join 5 columns.
- Page states: closed = slate hero + dashed "This game is closed." note, no Join; not found = court illustration + "Back to home"; error = red-bordered alert with "Try again" over a faded skeleton of the page.

## 4. Decisions for the owner

| ID | Question | Options | Recommendation |
|---|---|---|---|
| D-1 | Font | (a) Inter, self-hosted through the npm package `@fontsource/inter` (new dependency; version checked on npm before adding) · (b) Inter from Google Fonts (browser calls a Google server on every visit) · (c) keep the system font (no change, looks less distinctive) | (a): same font everywhere, no third-party request — **approved 2026-11-18** |
| D-2 | New color tokens in §2 | approve / change | approve — **approved 2026-11-18** |
| D-3 | Copy: the minimum-sets tile shows "6+" big and "sets per player" small, instead of the approved line "At least 6 sets per player" | approve the tile text / keep the old line | approve: the number is what people scan for — **approved 2026-11-18** |
| D-4 | Join bar fixed to the bottom on phones | approve / keep the button inside the page | approve — **approved 2026-11-18** |

## 4b. LATER artboards (not part of this feature)

`HomeNearby` (phone) and `HomeNearbyDesktop` show where navigation (L-20: Home, Map, My games, Chat, Profile) and "Open games near you" within 3 km + "Find courts near you" (L-21, L-22) would sit. Group and venue names there are placeholders. Each piece is built only with its own feature.

## 5. Out of scope

- Dark mode, animations, illustrations beyond the court line motif.
- Host board and player pages: they reuse these tokens when they are built.
