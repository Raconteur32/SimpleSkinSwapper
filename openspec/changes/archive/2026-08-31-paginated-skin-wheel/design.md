# Design — Paginated Skin Wheel

## Context

`SkinWheelScreen` renders a single wheel: `MAX_ENTRIES = 10` caps `loadWheelEntries()`, `hoverAnimFactors` eases per-sector hover intensity, `selectedIndex` is the hovered sector, click applies, key-release/right-click closes. `mouseScrolled` is available (used by the carousel). The main spec now requires live rendering + hover animation with eased settle, and viewport culling discipline comes from the 26.2 scissor-crash lesson (never submit negative-origin/zero-size scissors; clamp partial rects).

## Goals / Non-Goals

**Goals:**
- Whole library reachable through wheels of ten, with circular navigation
- Slide transitions that feel native to the mod's existing exponential-easing animation style
- Strict viewport culling of preview rectangles
- Session-scoped "reopen at last position" behind a config option

**Non-Goals:**
- Baking/caching preview textures (explicitly rejected — live rendering stays)
- Interactive side wheels, drag-to-rotate on the wheel, configurable page size
- Persisting the wheel position across game restarts

## Decisions

- **Continuous position model.** One float `wheelPos` (rendered position) chases an integer `targetPos` with `1 - exp(-k·dt)` easing (k ≈ 10, nano-time delta). Each wheel `k` is placed at wrapped offset `wrap(k - wheelPos)` into `[-1.5, 1.5]`; nothing renders beyond. Slot transform: offset 0 → center/scale 1.0, |offset| 1 → screen-edge center/scale 0.7, interpolated linearly; scale eases the "emerging from the background" feel. Duplicates (N=2 wrap) are simply two slots showing the same wheel — no special casing.
- **Lead-clamped retargeting.** On a scroll notch: `newTarget = targetPos ± 1`; accept only if `|newTarget - wheelPos| ≤ WHEEL_MAX_LEAD (2.0)`, else absorb the notch. Initial design clamped at 1.0 (target within one wheel of the rendered position); playtesting showed the exponential tail kept `wheelPos` far from the target long enough to lock chained scrolling, so the lead allows one active slide plus one queued wheel: continuous scrolling produces a steady glide, stopping lets the position catch up (≤ 2 wheels from the last intent), and reversing mid-slide always works because the clamp is measured from `wheelPos`.
- **Interactivity gate.** Sector hover/click/apply and the hover-animation easing only apply to the slot nearest the center when `|wheelPos - round(wheelPos)| < 0.05` (rest). During slides all factors ease toward 0 naturally via the existing per-sector ease (targets forced to 0 when not at rest). Hover factors become a `FloatArray(10)` per wheel (fixed page size → flat per-wheel arrays, no reallocation).
- **Culling.** Per sector preview: compute projected screen rect (wheel transform applied), skip if entirely outside `[0, width]×[0, height]`, otherwise clamp the rect into the viewport before building the PiP state. Sector meshes need no culling (GPU clip).
- **Pagination feedback.** `wheelCount ≤ 9`: dots (active highlighted) drawn under the wheel; else counter text "i/N". Shown only at rest, same rest predicate as interactivity; hovered-skin name likewise hidden unless at rest.
- **Remember position.** `rememberWheelPosition: Boolean = false` persisted in the JSON config; the position itself lives in a companion-object `var` (session scope, resets on game restart). On open: `wheelPos = targetPos = if (option && session position valid) sessionPosition else 0`.
- **Config placement.** New "Skin wheel" group inside the existing Options category, after "Player models"; tickbox like the others, EN/FR labels ("Remember wheel position" / "Se souvenir de la dernière roue ouverte").

## Risks / Trade-offs

- [Worst case ~30 live PiP renders/frame (3 wheels × 10) during slides] → accepted per product decision; culling removes roughly the outer half of side-wheel previews; GUI framerates suffice.
- [±1 clamp may feel slow for very large libraries] → deliberate precision-over-speed trade-off; switching to free accumulation is a one-line change.
- [Wrapped duplicates during N=2 slides render the same wheel twice] → visually correct circular-carousel behavior, transient, cheap.
- [Scissor/partial-rect crashes on 26.x] → all preview rects clamped before submission, mirroring the existing carousel fix.

## Migration Plan

Single release; no persisted data changes beyond a new config field (absent → default disabled). Rollback = revert of the change commit.

## Open Questions

None.
