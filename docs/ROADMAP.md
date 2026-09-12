# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block AU — The design system's consumer contract, adopted and gated

- 📋 **SH941** (deps: SH940 ✅) **the bento authoring contract and its nine artboards live only in the package's repository, not here** — roadkeep-gui vendors them into .claude/skills/viglet-ds-pages and docs/design by running viglet-ds-page-reference; Shio has neither directory. → §LX.2
- 📋 **SH942** (deps: SH941) **nothing fails when the vendored contract drifts from the package that wrote it** — The bin offers --check, --json and --warn and no Shio gate runs any of them, so a file rewritten upstream stays stale here until somebody reads both copies. → §LX.3
- 📋 **SH943** (deps: DS VDS129) **the package declares three of the seven design-system entries Shio's product source imports** — consumers.json omits ./bento, ./bento.css, ./router and ./i18n, so the parity digest the package's guards read describes a console four entries smaller than the real one. → §LX.4
- 📋 **SH944** (deps: SH941, DS VDS135) **the design system reaches this repository as copied files, never as an enabled Claude Code plugin** — Shio already ships claude-plugin/ with a manifest, commands and skills, so the pattern a design-system plugin would follow is in this tree and unread. → §LX.5
- 📋 **SH945** (deps: SH941) **the surfaces this round invents have no drawing, so their layout gets decided in review** — roadkeep-gui drew two directions and marked one not-chosen before building; Shio's 23 migrated screens need no drawing and its new ones do. → §LX.6
- 📋 **SH946** (deps: SH941, DS VDS131) **six bento pages set their own max-width, gutters and rhythm, at three different widths** — The contract gives the reading column to the shell, but with no shell exported each page invents one, and the defect exists only between screens. → §LX.7
- 📋 **SH947** (deps: SH946, SH942, DS VDS132) **the contract's two named rules have no check inside Shio's own test run** — A page setting its own column and a stylesheet setting --vg-primary directly would both pass pnpm test today, since nothing here spawns a page lint. → §LX.8
- 📋 **SH948** (deps: DS VDS130) **copy-ds.cmd cannot find the design system, so a package change cannot be tried in Shio unpublished** — It forwards to use:local, whose walk from the package's position on disk now reaches a worktree container, and the wrapper adds nothing but that path. → §LX.9
- 📋 **SH949** (deps: —) **Shio publishes a Claude Code marketplace that its own sessions never enable** — The openviglet marketplace ships four skills and an MCP server to strangers while settings.json enables only roadkeep, so a breakage arrives as somebody else's report. → §LX.10

## Block AV — The live defects, fixed before anything is built on them

- 📋 **SH950** (deps: —) **an uploaded SVG or HTML file is served inline and executable from the console's own origin** — Both asset routes set Content-Disposition inline and no response anywhere carries nosniff or a content security policy, so an agent token can plant script on the origin. → §LXI.1
- 📋 **SH951** (deps: SH950) **no upload path has a content policy, so HTML, JavaScript and scripted SVG are accepted like images** — There is no MIME allow-list anywhere in the static-file package and the multipart limit is 1024 MB, so the write path agents use for assets filters nothing. → §LXI.2
- 📋 **SH952** (deps: —) **shio_write drops when, locale, translationOf, url and expect, so a scheduled publish over MCP goes live at once** — ShWriteTool builds each op with the six-field constructor documented for ops that never schedule, while its own schema advertises when for post.publish. → §LXI.3
- 📋 **SH953** (deps: —) **bulk delete swallows every failure and reports the whole selection as deleted** — Each DELETE's rejection is discarded by an empty catch and the toast counts the selection, so a thousand refused deletes still announce a thousand deleted. → §LXI.4
- 📋 **SH954** (deps: —) **the Clear cache row action calls an endpoint whose entire body returns 200 and does nothing** — The page-cache subsystem it drove is gone and the no-op stayed because the console expects a 200, so the button has reported success for a cache that does not exist. → §LXI.5
- 📋 **SH955** (deps: —) **twenty-four console save paths discard the server's problem document and show the word Error** — The server writes fix, allowed, didYouMean and at on every 4xx and the console has no problem-document reader, so a curator sees Error where an agent sees the fix. → §LXI.6
- 📋 **SH956** (deps: —) **the rich-text field stores whatever a paste or an agent write contains, and the renderer emits it unescaped** — The widget is execCommand over contentEditable with no paste handler or sanitizer, and field HTML renders raw by design, so any markup reaches the published page. → §LXI.7

## Block AW — One chrome: bento, and nothing left that names another

- 📋 **SH957** (deps: —) **nothing counts the two-chrome vocabulary left in the console, so its removal cannot say when it is done** — 108 source lines and 180 test and script lines still name a chrome, and a figure grepped once by hand is the measurement SH886 found wrong twice. → §LXII.1
- 📋 **SH958** (deps: SH957) **about twenty-five routes each wrap themselves in a bento chrome provider to escape a console default** — The design system's context defaults to console, so App.tsx repeats one wrapper per route, and a route added without it silently renders the console look. → §LXII.2
- 📋 **SH959** (deps: SH958) **EmptyState, Panel, NotFound and the static-file list still carry a console rendering beside the bento one** — Each branches on useSectionChrome, and the console half is what renders wherever no provider is mounted, so login, setup and error pages still show the retired look. → §LXII.3
- 📋 **SH960** (deps: SH957) **the surface digest and the chrome census still classify every surface into a console era nothing renders** — surface-digest.mjs and console-chrome-census.test.mjs were built to measure a migration that finished, so every run spends assertions on an era with no members. → §LXII.4
- 📋 **SH961** (deps: SH959) **twenty-three bento pages live under src/app/console, beside a src/app/bento tree, so a path names a retired era** — The two page trees record where each page was migrated from, not what it renders, so a reader choosing where a new page goes is choosing between eras. → §LXII.5
- 📋 **SH962** (deps: SH960, SH961) **the console-ui area doc and eighteen source files narrate a two-chrome console that no longer exists** — Comments explain code by the migration that produced it, so a reader learns a console chrome exists and a new page copies an adapter that chooses nothing. → §LXII.6
- 📋 **SH963** (deps: SH958, DS VDS146) **Shio still has to declare a chrome at all, because the package keeps a switch with a console value** — Until the design system drops SectionChrome the root provider must stay, and while it stays the census cannot reach zero. → §LXII.7

## Block AX — The Viglet assistant: where the console reports, and how the curator reaches the agent

- 📋 **SH964** (deps: DS VDS133) **the console mounts no Viglet assistant, so its 106 toasts reach no mascot and no dock** — VigletAvatar, VigletAssistant and useAssistantNotifications shipped in the design system's Block E for a CMS screen like this one, and no consumer has mounted them. → §LXIII.1
- 📋 **SH965** (deps: SH964) **nothing checks that a console write reports an outcome, so a silent save leaves the mascot idle** — useAssistantNotifications reads toasts, so a mutation that resolves without one is invisible to the dock, its announcer and the curator at the same moment. → §LXIII.2
- 📋 **SH966** (deps: SH964, SH955, DS VDS134) **a refused save reaches the dock as the word Error, with the server's fix left behind** — Once the console reads problem documents, the dock can speak the detail and offer fix, didYouMean and a jump to the failing field instead of a toast. → §LXIII.3
- 📋 **SH967** (deps: —) **the console learns nothing until it reloads, so an agent's writes and a failed schedule reach no open screen** — The agent reads a keyset change feed and nothing streams it, so every ambient signal the dock could report needs a push channel that does not exist. → §LXIII.4
- 📋 **SH968** (deps: SH967, DS VDS134) **agent runs and review arrivals surface only as a header count the curator cannot open** — SH936 put the pending count beside the account menu, while the contract says a count belongs to the surface that owns it, and the dock is where the system reports. → §LXIII.5
- 📋 **SH969** (deps: SH967) **a scheduled publish that fails becomes an agent incident, and no curator surface ever reads it** — ShPostScheduler records failures in the agent incident log and a success nowhere a person looks, so the launch a curator scheduled can silently not happen. → §LXIII.6
- 📋 **SH970** (deps: SH967) **a form submission lands as an ordinary draft in a folder, and nothing tells anyone it arrived** — ShFormSubmissionAPI writes a draft into a configured folder behind a rate limit and a honeypot, and no surface reports the arrival the assistant's reference design was drawn for. → §LXIII.7
- 📋 **SH971** (deps: SH968) **a curator cannot choose what the dock reports or quiet the mascot** — An author, a reviewer and an operator need different subsets of outcomes, agent runs, schedules and arrivals, and the only control today is the browser's reduced-motion setting. → §LXIII.8
- 📋 **SH972** (deps: SH964) **the Universal Editor renders outside the bento shell, so its saves would report to no dock** — SH907 moved the visual editor out of the shell to reclaim the viewport, and the dock mounts inside the shell, so the surface saving on every click has no outcome channel. → §LXIII.9

## Block AY — What the conformance instruments cannot see

- 📋 **SH973** (deps: —) **GET-only controller roots are classified by neither P1 instrument** — The reachability lint matches only mutating mappings and the conformance test walks manifest feature keys, so a read-only console surface is invisible to both. → §LXIV.1
- 📋 **SH974** (deps: SH973) **agents.md states reachability figures that no run reproduces** — It says twenty-five mutating paths and eight writable while the scan finds twenty-eight, and nothing reads a numeric claim out of prose to check it. → §LXIV.2
- 📋 **SH975** (deps: —) **the manifest points the review queue at the word console, and Reach has no category for operator-only** — Every other elsewhere value is a runnable command or route and the assertion checks only non-blankness, while review is writable to HTTP Basic and refused to an agent token. → §LXIV.3
- 📋 **SH976** (deps: —) **a green MCP budget run prints per-tool cost and never the total, the headroom or the tool count** — tools/list sits near its ceiling and resources/list and prompts/list carry no budget, so the number deciding whether a capability can land is visible only in a failure. → §LXIV.4
- 📋 **SH977** (deps: SH976) **the round's new tool arguments each budget themselves alone against headroom none of them can share** — Revisions, perspectives, schedules, filters and locale each add schema to existing tools, and no per-tool reservation divides the room between them. → §LXIV.5
- 📋 **SH978** (deps: SH973) **nothing joins the console's route tree to the agent surface** — The console's surfaces are a TypeScript array the JVM cannot read, and the P1 test is keyed on manifest features, so a console-first page is invisible to every instrument. → §LXIV.6
- 📋 **SH979** (deps: SH978) **the surface declaration has no reader beyond its gate, so the rail, the palette and an agent still keep separate lists** — A declaration only one test reads is a second source of truth, and bento-nav.ts, the manifest and MCP would each go on describing surfaces their own way. → §LXIV.7
- 📋 **SH980** (deps: SH978) **the round's console-only surfaces have no exception kind a gate can read, only paragraphs arguing why** — The surface gate fails on an unclassified page, and chrome, accessibility, caching and preference tasks argue their P1 position in prose the test cannot parse. → §LXIV.8
- 📋 **SH981** (deps: SH978) **no agent response carries a console URL for the address it just wrote** — An agent's closing move is a sentence rather than a link, and console routes live only in TypeScript the backend cannot read without the surface declaration. → §LXIV.9
- 📋 **SH982** (deps: SH973) **P7 is the round's most-cited law and no census asks which mutating paths offer its guards** — Dry-run, a confirm token, attribution and a review entry are asserted task by task, so the class is never swept and the next unguarded write is invisible. → §LXIV.10
- 📋 **SH983** (deps: SH982) **no census asks which writes return proof, so cheap textual evidence stays a per-author choice** — Digest, verify and impact are opt-in per endpoint, so a new write can ship returning a bare 200 and nothing notices the perception loop was left open. → §LXIV.11
- 📋 **SH984** (deps: —) **lines filed against a design-system release have no probe in the waits list, so a release can end their wait unnoticed** — SH937 holds every line waiting on the package as a named probe, and SH946, SH947, SH948, SH963, SH964, SH966 and SH968 were filed without one. → §LXIV.12

## Block AZ — What the content said before

- 📋 **SH985** (deps: SH952) **console writes carry no version, so a curator silently overwrites fields an agent just wrote** — The agent surface refuses a stale write with expect and a 409, while the console's PUT and PATCH take no digest and no If-Match, and nothing in the backend uses @Version. → §LXV.1
- 📋 **SH986** (deps: —) **a post has only a draft row and a published row, so every earlier version is gone** — versions() returns the two rows of one composite key, publishing copies the draft over the live row, and no table holds a snapshot of anything a visitor once read. → §LXV.2
- 📋 **SH987** (deps: SH986) **no surface can read a past revision of an address, and no verb restores one** — The state parameter reaches draft and published only, no revisions list exists on REST or MCP, and shio diff compares a replica with its capture rather than two revisions. → §LXV.3
- 📋 **SH988** (deps: SH987, DS VDS140) **the version dialog compares two whole values and shows nothing at all for a created page** — The review diff is gated on a published twin existing, so a create, the commonest agent output, has no review affordance, and a changed body renders as two walls of text. → §LXV.4
- 📋 **SH989** (deps: SH986) **audit rows say something changed and never which fields, so an approved run cannot be described afterwards** — The history row is a bounded sentence, and after an approve the draft-against-published diff is empty, so the run just accepted can no longer be shown. → §LXV.5

## Block BA — Releases, scheduling and the publish gate

- 📋 **SH990** (deps: SH986) **state= is honoured on two read paths and absent from every other** — The agent read and the preview renderer each parse it themselves, while render, editable, verify, find and the whole CDA cannot read a draft or a revision at all. → §LXVI.1
- 📋 **SH991** (deps: —) **nothing lists what is scheduled, so an agent cannot confirm a schedule it set and a curator has no agenda** — The only queries over the schedule columns are the sweep's due-only finders, and neither find nor read exposes a schedule, so the only view is a single post's dialog. → §LXVI.2
- 📋 **SH992** (deps: SH952) **shio_publish cannot schedule, and post.unpublish accepts a when it never reads** — The publish tool builds an op with no when, and only postPublish calls scheduleInstant, so an embargoed takedown dated for December takes the page down now and reports success. → §LXVI.3
- 📋 **SH993** (deps: SH990, SH986) **no release groups a launch, so forty related pages go live as forty separate events in sweep order** — One batch call is atomic and nothing persists a set across calls, so a coordinated launch has no unit to stage, preview, approve, schedule or undo. → §LXVI.4
- 📋 **SH994** (deps: SH993, SH987) **a release that went live and should not have has no verb to undo it as one unit** — The release stores a rollback anchor per item and a rolled-back state, and none of its six operations reaches either, which leaves atomicity with no reverse. → §LXVI.5
- 📋 **SH995** (deps: —) **there is no publish permission for a person, so any user who may write may also put content live** — mayPublish exists only on an API token and falls back to true for every console session, so an agent can be denied publishing and a curator cannot. → §LXVI.6
- 📋 **SH996** (deps: SH995) **the console's publish has no dry run, no impact preview and no confirm, while the agent's has all three** — Nothing tells a curator which URLs go live, change or stop resolving before the button writes, and include=impact answers template blast radius, not routes. → §LXVI.7
- 📋 **SH997** (deps: SH993, SH996) **nothing checks a page before it goes live, because verify, audit and the render digest are all opt-in** — Shio's pre-publish instruments run only when somebody remembers, and its webhook events all fire after the commit, so no check stands between content and visitors. → §LXVI.8
- 📋 **SH998** (deps: SH997, SH995) **a failed gate cannot be waived with a recorded reason, and nothing outside Shio hears it failed** — A check nobody may waive becomes one people disable, and webhooks fire only after commit, so no external approver is told to step in. → §LXVI.9

## Block BB — The curator's return channel, and the conversation with the agent

- 📋 **SH999** (deps: —) **agent attribution is on every audit row and dropped by both readers of the audit trail** — The console history response and the agent change feed both report the bare principal, and the feed's javadoc still promises a distinction a later change removed. → §LXVII.1
- 📋 **SH1000** (deps: SH999) **an agent run carries no statement of what it was trying to do** — The review queue renders a count of changes by an actor over an opaque session id, with no intent, plan, ticket or rationale stored anywhere on the run. → §LXVII.2
- 📋 **SH1001** (deps: SH1000, SH995) **review is approve everything or revert everything, and a rejection has nowhere to record why** — One bad page in a long run forces a curator to accept all of it or undo all of it, while each entry already carries a revertable flag nothing lets them use. → §LXVII.3
- 📋 **SH1002** (deps: SH1001) **an agent cannot read the verdict on its own run, so every correction needs a person to re-explain it** — Approval rightly stays off the agent namespace, and no read-only resource lets a session learn that a change was rejected, held or reverted, or why. → §LXVII.4
- 📋 **SH1003** (deps: SH999) **a curator's correction has nowhere to live, with no note anchored to a page, a field or a revision** — No comment or annotation entity exists in the backend, so the only channel back to the agent is a person re-explaining out of band. → §LXVII.5
- 📋 **SH1004** (deps: SH1003) **notes, constraints, translation metadata and asset metadata land in the file projection with no merge rule** — Only releases state how a concurrent edit merges, so a pull carrying new sidecar fields can silently lose a local edit against content-base.json. → §LXVII.6
- 📋 **SH1005** (deps: SH999) **the activity trail cannot be paged or filtered, and a post has no history panel** — Every history read is page zero capped at five hundred with no cursor or filter, while the agent's change feed already has a keyset cursor and a measured budget. → §LXVII.7
- 📋 **SH1006** (deps: SH1003, SH1000, SH967) **a curator cannot ask the connected agent anything from the assistant dock** — VigletAssistant opens into a chat only when a product supplies onSend, and Shio hosts no model, so the chat must reach the external agent and nothing relays to it. → §LXVII.8
- 📋 **SH1007** (deps: SH1006, SH1001, DS VDS134) **an agent's answer in the dock cannot carry the change it proposes** — A reply that says it drafted a new hero leaves the curator searching for the draft, while each dock message can offer an action and review already groups changes by run. → §LXVII.9
- 📋 **SH1008** (deps: SH967, SH999, SH985) **a curator editing a page cannot see that an agent is working on the same page right now** — Concurrency guards refuse a stale write after the fact, and nothing shows, before anyone types, that another writer is active on the address. → §LXVII.10

## Priority

## Non-goals (do NOT add as tasks)

Binding constraints — rationale in [IMPROVEMENTS.md](IMPROVEMENTS.md) §0.3–§0.4, §0.7 and
[specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md) §11.

- **Shio does not host a model.** No bundled LLM, no prompt runner, no provider API
  keys in the CMS, no "generate this page for me" button. Shio is the substrate an
  external agent drives; adding inference trades a defensible protocol position for
  a commodity feature and puts someone else's rate limits inside the content engine.
- **Do not rewrite the backend in Node/Next/Go/PHP.** Java/Spring Boot is the
  content engine and the glue with Turing + Dumont; a rewrite is high-cost,
  high-risk, zero user-visible value (§0.3).
- **No third API contract.** MCP is a *shape* over the CDA (SH1) + console services,
  never a parallel semantic (law P10).
- **The agent path is not a privilege escalation.** Tenant isolation (SH22),
  content ACL (SH54) and token scopes apply identically on every agent surface.
- **No feature whose only interface is the console** (law P1) — every capability
  needs an MCP/CLI/file path.
- **No GraalJS server-side JS template engine** (the former Block D, SH17/SH18 —
  dropped). An agent writes Next.js; an in-JVM script engine is sandboxing
  liability with no agent-side benefit. Groovy is likewise not the answer (§0.4).
- **Don't couple the JS SDK to React** — the framework-agnostic core (SH6) is the
  base; React is one consumer.
- **Don't optimize a benchmark by degrading correctness** — a terse response that
  omits something the agent must then ask for twice is a regression, not a win.
- **The instance does not run a browser.** Raster stays in the CLI — SH102's shio
  snapshot, Playwright an optional dynamic import — because that is where the agent
  already has a shell (P4) and where the human opens the PNG. Chromium per request is a
  different operational thing, and the diff ratio is a number the CLI already computes.
