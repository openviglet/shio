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
