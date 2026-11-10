# iOS 27 · Liquid Glass Launcher (prototype)

A single-file, offline prototype of an iOS-style launcher: glass layers, a 4-column
squircle grid, an interactive 2×4 widget, jiggle mode and parallax paging.

Open `index.html` in any modern browser (Chrome/Safari). No build step, no assets.

## What's implemented

| Spec | Where |
| --- | --- |
| Liquid Glass layers (`backdrop-filter: blur(30px) saturate(170%)`, 0.5 px light border, inset highlight) | `.glass`, `--blur`, `--sat` |
| Soft 3D shadows | `box-shadow` on `.glass`, `.app-icon`, `.widget` |
| 4-column grid, Apple squircle icons (`border-radius: 23%`) | `.grid`, `.app-icon` |
| Minimal dark mode (auto-desaturates icons + wallpaper) | `body.tinted`, `#appearance` toggle |
| Invisible icons = empty slots | `EMPTY_SLOTS`, `.slot-empty` |
| Interactive 2×4 widget (call / URL / Wi-Fi) with press animation | `.widget`, `.quick`, `data-action` |
| Jiggle mode on long-press + `−` badges | `enterJiggle()`, `.grid.jiggle`, `.remove` |
| Page swipe + wallpaper parallax | `setPage()`, `updateParallax()`, pointer handlers |
| Dynamic Island + status bar chrome | `.island`, `.statusbar` |

## Interactions

- **Tap an icon** — press-down scale.
- **Long-press (480 ms) an icon** — jiggle mode; `−` removes it from the page.
- **Tap empty space** — leaves jiggle mode.
- **Swipe horizontally** — page 1 ⇄ page 2, wallpaper drifts at 0.06× (parallax).
- **Aa button** — toggles the minimalist dark tint.
- **Widget** — each shortcut flashes, then acts (tel:, opens GitHub, toggles Wi-Fi state).

## Structure

```
index.html      everything: tokens, components, data, interactions
README.md       this file
```

Data lives at the top of the `<script>`: `APPS_PAGE_0`, `APPS_PAGE_1`, `DOCK`, `EMPTY_SLOTS`.
Add an app with `{ name, glyph, c:[topColor, bottomColor] }`; empties are slot indices.

## Known limits (prototype honesty)

- Wi-Fi is simulated — a browser can't toggle radios.
- Icons are gradient squircles with an emoji glyph, not real app artwork.
- No drag-to-rearrange, folders, stacks or widgets picker.
- `backdrop-filter` costs GPU on low-end devices; reduce `--blur` if needed.

## Porting

The same tokens map cleanly to SwiftUI (`.ultraThinMaterial`, `RoundedRectangle(cornerRadius:style:.continuous)`)
or Compose (`Modifier.blur`, `RoundedCornerShape(23)`).
