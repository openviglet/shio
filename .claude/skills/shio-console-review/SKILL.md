---
name: shio-console-review
description: Review Shio's React console as a curator would, one iteration at a time, and file every finding as a roadkeep task. Boots a clean instance, runs the console check, asks e2e/ui-review-pick.mjs which route families to judge next, judges their screenshots against a fixed checklist (purpose, hierarchy, states, consistency, language, the curator's next step, P1/P7), files what it finds after searching the roadmap, and records the review so the next run moves on. Use when asked to review, audit, critique or improve the console's UI or UX, to look for interface problems, or to run a continuous or recurring console review under /loop or a scheduled headless run.
---

# Reviewing the console, continuously (SH1321)

`console-check.mjs` is the mechanical half: it walks every route, screenshots each one, runs axe,
records failed reads and gates the cost. **This skill is the half that judges**, and its whole
design is that one invocation is one bounded iteration, so a loop can repeat it without the
review drifting toward whatever page came to mind.

Read [docs/agents/console-ui.md](../../../docs/agents/console-ui.md) §"Reviewing the console" for
the reasoning; this file is the procedure.

## One iteration

1. **A clean instance, not the dev box.** The dev instance on `:2710` carries every earlier
   session's content and hides empty states. Build and start a fresh store on `:2711`:

   ```bash
   ./mvnw -pl shio-app -am package -DskipTests -Dskip.npm=true
   rm -rf /d/tmp/shio-review && mkdir -p /d/tmp/shio-review
   cd /d/tmp/shio-review && java -jar <repo>/shio-app/target/viglet-shio.jar --server.port=2711   # background
   ```

   Skip the build when the jar is newer than the last commit and an instance is already up.

2. **Provision and run the check against the jar** (the production build, the one the cost
   budget was measured on):

   ```bash
   export SHIO_URL=http://localhost:2711 SHIO_USER=admin SHIO_PASSWORD=shio-conformance
   node shio-react/e2e/provision.mjs --api $SHIO_URL
   node shio-react/e2e/console-check.mjs --base $SHIO_URL --api $SHIO_URL > /d/tmp/shio-review/check.log
   ```

   A red check is **not** a reason to stop the review: failing families are exactly what the
   picker puts first. Redirect, then read: never pipe the check into `grep`.

3. **Ask what to look at.** Never choose routes yourself:

   ```bash
   node shio-react/e2e/ui-review-pick.mjs --count 3
   ```

   It prints, per family, the reason it was picked, the paths, the screenshots and the check's
   own problems for them. Three families per iteration is the default budget; raise `--count`
   only when asked.

4. **Judge each picked family** against [checklist.md](checklist.md): read every screenshot, and
   open the route in a real browser (Playwright, as `console-check.mjs` does) for what a
   screenshot cannot show: the English locale, a narrow viewport, the dark theme, hover and
   focus, an empty or error state. Look for the cause in `shio-react/src/app/**` before writing
   anything down: a finding names the component, not only the pixel.

5. **File each finding before moving on**, never batch it into a summary:
   - search first: `mcp__roadkeep__list` / `section_find` with the page's name or the symptom.
     An open line that already says it gets nothing new; a closed one that regressed is a new task
     that cites it.
   - then `mcp__roadkeep__add` under the block holding open console work (`block_list`; Block BG
     today), symptom as what the curator meets, never as the fix. One finding, one line.
   - a defect the check could have caught mechanically gets a second line: the probe or lint that
     would have caught it (SH322: the instrument is the deliverable).

6. **Record the review**, findings or not, so the rotation advances:

   ```bash
   node shio-react/e2e/ui-review-pick.mjs --record <family>,<family> --findings SH1400,SH1401
   ```

7. **Commit** the ledger with the filed lines: `run-commit.cmd -m "docs(roadmap): file the console
   review findings for <families> (SH…)"`, from the repo root, and only when the working tree holds
   nothing but this iteration's files. With nothing found: `chore(console): record the review of
   <families>`.

## What it does not do

- **It does not fix.** Review and repair in one commit hides which finding a change answers. The
  filed lines are drained by `execute-roadkeep-tasks`, or by a later iteration the user asks for.
- **It does not judge taste without a rule.** Every finding cites a checklist item or a design law;
  "I would have done it differently" is not filed.
- **It does not re-review a family the ledger shows was reviewed at this commit**, unless the check
  now fails it.

## Running it continuously

- **In a session:** `/loop /shio-console-review`, self-paced. Each firing is one iteration; stop
  when the picker offers only families reviewed at the current commit.
- **Unattended:** a Windows scheduled task running
  `claude -p "/shio-console-review" --permission-mode acceptEdits` from the repo root. It needs the
  local instance, so a cloud routine (`/schedule`) would have to build the jar each time.
- **After a console change:** the iteration is the cheapest check that the change reads right to a
  person. Run it with `--count` covering the families the change touched.
