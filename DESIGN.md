# Design

## Source of truth
- Status: Active
- Last refreshed: 2026-06-29
- Primary product surfaces: Native Android Priority Todo main screen, task entry flow, task rows, reminder controls.
- Evidence reviewed:
  - `.omx/context/thumb-reachability-20260629T160648Z.md`
  - `.omx/plans/prd-thumb-reachability-20260629T160648Z.md`
  - `.omx/plans/test-spec-thumb-reachability-20260629T160648Z.md`
  - `.omx/plans/ralplan-handoff-thumb-reachability-20260629T160648Z.json`
  - `app/src/main/java/com/tj90/prioritytodo/MainActivity.java`
  - `README.md`

## Brand
- Personality: Minimal, decisive, high-contrast, utility-first.
- Trust signals: Clear priority math, visible MIT, predictable controls, no hidden core gestures.
- Avoid: Decorative complexity, dense equal-weight actions, gesture-only critical actions, accidental destructive controls.

## Product goals
- Goals:
  - Make the app usable one-handed with either thumb.
  - Use the top of the screen for information and the bottom for action.
  - Let a user start a task in roughly 1.5-2 seconds with a title and defaults.
  - Preserve priority score transparency for users who open details.
- Non-goals:
  - Pixel-perfect redesign against a third-party reference.
  - New design-system dependency or Material component dependency.
  - Gesture-heavy interaction model.
- Success signals:
  - One dominant primary action is visible in the fixed bottom action bar.
  - First viewport communicates MIT/status without requiring interaction at the top.
  - Advanced priority/reminder controls are discoverable through a visible Details reveal.

## Personas and jobs
- Primary personas:
  - Mobile-first user capturing tasks quickly with one hand.
  - Planner reviewing the highest-priority MIT and completing tasks throughout the day.
- User jobs:
  - Add a task quickly.
  - Understand what matters most now.
  - Complete/restore/edit tasks without hunting through controls.
  - Add priority/reminder details when needed.
- Key contexts of use:
  - Walking, commuting, or holding the phone one-handed.
  - Short attention windows where the next action must be obvious.

## Information architecture
- Primary navigation: Single-screen task capture and priority list.
- Core routes/screens: Main Activity only.
- Content hierarchy:
  1. Fixed top information zone: app identity, subtitle, MIT/status.
  2. Scrollable middle zone: quick-add title, Details reveal, task list, secondary preferences.
  3. Fixed bottom thumb action bar: one dominant CTA plus at most one quiet secondary action.

## Design principles
- Principle 1: Thumb zone owns action. Frequent actions belong in bottom/reachable space.
- Principle 2: Top zone informs, not demands. No primary CRUD controls in the header/top zone.
- Principle 3: One glance, one primary action. A mode has exactly one dominant CTA.
- Principle 4: Visible taps before gestures. Gestures may supplement, never replace, core actions.
- Principle 5: Safety near thumbs. Destructive actions are tertiary and guarded.
- Tradeoffs:
  - Quick-add-first improves speed but can hide scoring details; the visible Details reveal preserves transparency.
  - A fixed bottom bar improves reachability but reduces vertical content space; keep it capped to two controls.

## Visual language
- Color: Existing black/white/grey with red reserved for the dominant CTA, urgent/immediate accents, validation errors, and small MIT emphasis.
- Typography: Bold title/MIT, compact explanatory text, no long instruction blocks.
- Spacing/layout rhythm: Clear separation between top info, scroll content, and bottom action. Generous spacing near destructive controls.
- Shape/radius/elevation: Preserve simple rounded rectangles and subtle dividers.
- Motion: None required. Details may reveal instantly without animation.
- Imagery/iconography: No new imagery required.

## Components
- Existing components to reuse:
  - Programmatic Android `LinearLayout`, `ScrollView`, `TextView`, `EditText`, `Button`, `RadioGroup`, `Spinner`, `CheckBox`.
  - Existing palette and rounded background helpers.
- New/changed components:
  - Fixed top information container.
  - Scrollable middle content container.
  - Fixed bottom thumb action bar.
  - Details reveal section for advanced task fields.
  - Guarded Delete confirmation.
- Variants and states:
  - Add mode: bottom CTA = `Add task`.
  - Edit mode: bottom CTA = `Update task`; quiet secondary = `Cancel`/reset.
  - Row mode: row primary = checkbox complete/restore; secondary = `Edit`; tertiary = `Delete` with confirmation.
- Token/component ownership: Keep token-like values in `MainActivity.Palette` and helper methods until a larger design-system extraction is warranted.

## Accessibility
- Target standard: Android 48dp effective touch targets; WCAG 2.2 target-size principles as supporting floor.
- Keyboard/focus behavior: Text entry must remain usable with fixed bottom action bar and soft keyboard.
- Contrast/readability: Preserve high-contrast black/white/red treatment.
- Screen-reader semantics: Use clear visible labels; no icon-only critical controls.
- Reduced motion and sensory considerations: No required animation or flashing.

## Responsive behavior
- Supported breakpoints/devices: Compact and tall Android phones, portrait-first.
- Layout adaptations:
  - Top info remains fixed and compact.
  - Middle content scrolls.
  - Bottom action bar remains fixed/reachable and capped.
- Touch/hover differences: Touch-first; no hover-only affordances.

## Interaction states
- Loading: Not applicable for local-only app state.
- Empty: Top MIT card explains no primary MIT; middle list shows empty state.
- Error: Inline validation for required task title and invalid reminder settings.
- Success: Task appears sorted; MIT updates immediately.
- Disabled: Advanced repeat interval disabled when no repeat is selected.
- Offline/slow network: Not applicable; local persistence only.

## Content voice
- Tone: Short, direct, plain.
- Terminology: Use `MIT`, `Score`, `Details`, `+ Add`, `Add task`, `Update task`, and checkbox-based completion/restoration.
- Microcopy rules:
  - Prefer verbs over explanations.
  - Do not make users read instructions to discover core actions.
  - Keep destructive copy explicit: `Delete task?`.

## Implementation constraints
- Framework/styling system: Native Android Java Views; no external UI dependencies.
- Design-token constraints: Existing palette and dp helper.
- Performance constraints: Keep UI synchronous and lightweight; no expensive custom drawing.
- Compatibility constraints: Min SDK 26, target SDK 36.
- Test/screenshot expectations:
  - Build debug APK.
  - Capture or inspect compact/tall viewport if available.
  - Verify keyboard/IME and large-font behavior manually where tooling allows.

## Open questions
- [ ] Should future versions add a handedness preference, or is centered bottom action sufficient? / owner: product / impact: optional ergonomics refinement.
- [ ] Should Delete use undo instead of confirmation after implementation evidence? / owner: product/engineering / impact: destructive-action speed vs safety.

## 2026-06-30 Jony-inspired thumb-first refinement

- Default home is list-first: the MIT and task list/empty state appear before any add/details form.
- The fixed bottom dock owns the primary action. At rest it shows one `+ Add` affordance; when the add/edit panel is open it switches to one primary save/update action plus quiet `Cancel`.
- Add/edit is an anchored panel above the dock, not a permanent form above the list. Title comes first; advanced fields stay behind `Details`.
- Completion and restore use a checkbox in each task row. Do not reintroduce large `Complete` or `Restore` row buttons.
- Visual restraint is now stricter: black/white/grey are the interface; red is reserved for the main confirmation CTA, urgent/immediate accent, validation errors, and small MIT emphasis.
- The MIT card is neutral by default and must not be a full red block.
- Delete remains quiet and confirmed; destructive emphasis belongs in the confirmation moment, not as an equal-weight card action.
