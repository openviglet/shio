# Console review checklist (SH1321)

Fixed on purpose: two iterations that judge against the same list produce comparable findings,
and a finding cites the item it breaks (`C3`, `P7`). Add an item when a filed finding fits none,
and never edit one to excuse a page.

## C1 Purpose
- The page says what it is for in its hero, in the curator's words, not the API's (`post-unified`,
  `shObject`, raw ids and UUIDs are findings).
- The primary action is visible without scrolling and there is exactly one (SH1313).

## C2 Hierarchy
- One `h1`; sections are titled; no box inside a box inside a box (SH1308).
- Density matches the job: a list scans, a form reads top to bottom, nothing is centred in a
  1600px void.

## C3 States
- Loading, empty, error and "no permission" each render something that says what happened and
  what to do next. A blank region, a bare spinner that never ends, or a raw error string is a finding.
- An empty state offers the action that fills it.

## C4 Consistency
- Uses the bento shell and the shared components (hero, frosted panels, save bar), not a
  hand-rolled variant. Same verb for the same action across pages (Delete vs Remove vs Trash).
- Accent and status colours come from tokens, not literals.

## C5 Language
- Both locales: no untranslated key, no English inside Portuguese, no truncation in the longer one.
- Labels, toasts and errors say what happened in the user's terms; P6 applies to the console too.

## C6 Responsiveness and access
- 390px: nothing overflows horizontally, the primary action is still reachable.
- Dark theme: no text below contrast, no white flash, no invisible icon.
- Every icon-only button has a name; focus is visible.

## C7 The curator's next step
- After the page's main action, the curator knows it worked and where to go next.
- Destructive or publishing actions confirm, draft by default, and can be undone or reviewed (P7).

## C8 Agent parity
- What this page does, an agent can do through MCP/REST (P1). A console-only capability is a
  finding for the agent surface, filed alongside the UI one.
