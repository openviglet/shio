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

- 📋 **SH943** (deps: DS VDS129) **the package declares three of the seven design-system entries Shio's product source imports** — consumers.json omits ./bento, ./bento.css, ./router and ./i18n, so the parity digest the package's guards read describes a console four entries smaller than the real one. → §LX.4
- 📋 **SH944** (deps: SH941 ✅, DS VDS135) **the design system reaches this repository as copied files, never as an enabled Claude Code plugin** — Shio already ships claude-plugin/ with a manifest, commands and skills, so the pattern a design-system plugin would follow is in this tree and unread. → §LX.5
- 📋 **SH946** (deps: SH941 ✅, DS VDS131) **six bento pages set their own max-width, gutters and rhythm, at three different widths** — The contract gives the reading column to the shell, but with no shell exported each page invents one, and the defect exists only between screens. → §LX.7
- 📋 **SH947** (deps: SH946, SH942 ✅, DS VDS132) **the contract's two named rules have no check inside Shio's own test run** — A page setting its own column and a stylesheet setting --vg-primary directly would both pass pnpm test today, since nothing here spawns a page lint. → §LX.8
- 📋 **SH948** (deps: DS VDS130) **copy-ds.cmd cannot find the design system, so a package change cannot be tried in Shio unpublished** — It forwards to use:local, whose walk from the package's position on disk now reaches a worktree container, and the wrapper adds nothing but that path. → §LX.9
- 📋 **SH1088** (deps: —) **eight tasks wait on design-system work that is published, because this console holds 2026.3.10** — VDS129 to VDS135 and VDS140 are all inside 2026.3.11, which npm serves and the lockfile does not take, and the same release removes two exports App.tsx imports. → §LX.11

## Block AW — One chrome: bento, and nothing left that names another

- 📋 **SH963** (deps: SH958 ✅, DS VDS146) **Shio still has to declare a chrome at all, because the package keeps a switch with a console value** — Until the design system drops SectionChrome the root provider must stay, and while it stays the census cannot reach zero. → §LXII.7

## Block AX — The Viglet assistant: where the console reports, and how the curator reaches the agent

- 📋 **SH964** (deps: DS VDS133) **the console mounts no Viglet assistant, so its 106 toasts reach no mascot and no dock** — VigletAvatar, VigletAssistant and useAssistantNotifications shipped in the design system's Block E, but 2026.3.10 is installed here and VDS133 gave them the shell's corner after it. → §LXIII.1
- 📋 **SH965** (deps: SH964) **nothing checks that a console write reports an outcome, so a silent save leaves the mascot idle** — useAssistantNotifications reads toasts, so a mutation that resolves without one is invisible to the dock, its announcer and the curator at the same moment. → §LXIII.2
- 📋 **SH966** (deps: SH964, SH955 ✅, DS VDS134) **a refused save reaches the dock as the word Error, with the server's fix left behind** — Once the console reads problem documents, the dock can speak the detail and offer fix, didYouMean and a jump to the failing field instead of a toast. → §LXIII.3
- 📋 **SH968** (deps: SH967 ✅, DS VDS134) **agent runs and review arrivals surface only as a header count the curator cannot open** — SH936 put the pending count beside the account menu, while the contract says a count belongs to the surface that owns it, and the dock is where the system reports. → §LXIII.5
- ⏳ **SH969** (deps: SH967 ✅, SH964) **a scheduled publish that fails becomes an agent incident, and no curator surface ever reads it** — The dock still has to report a failure with open and retry actions, a success naming the page, and an unread failure for a curator who had no screen open. → §LXIII.6
- ⏳ **SH970** (deps: SH967 ✅, SH964) **a form submission lands as an ordinary draft in a folder, and nothing tells anyone it arrived** — The dock still has to report an arrival with tone attention, a caption, open and archive actions, and one report when several arrive together. → §LXIII.7
- 📋 **SH971** (deps: SH968) **a curator cannot choose what the dock reports or quiet the mascot** — An author, a reviewer and an operator need different subsets of outcomes, agent runs, schedules and arrivals, and the only control today is the browser's reduced-motion setting. → §LXIII.8
- 📋 **SH972** (deps: SH964) **the Universal Editor renders outside the bento shell, so its saves would report to no dock** — SH907 moved the visual editor out of the shell to reclaim the viewport, and the dock mounts inside the shell, so the surface saving on every click has no outcome channel. → §LXIII.9

## Block AY — What the conformance instruments cannot see

- 📋 **SH1070** (deps: —) **the docs conformance gate is red at HEAD, and 135 commits deep in owed runs** — Two suites fail on drift nobody has seen: a published page states Spring Boot 4.1.0 against a pom declaring 4.1.1, and COVERAGE.md's summary counts 57 clusters over 54 rows. → §LXIV.13
- ⏳ **SH1072** (deps: —) **six shipped ids are unnamed in the published coverage matrix, which is past the ceiling** — the COVERAGE.md edit is in the vigletdocs working tree and uncommitted, so a fresh clone still reads the old matrix. → §LXIV.14
- 📋 **SH1074** (deps: —) **the ARIA-state lint rejects the exact form its own failure message tells you to write** — Its regex refuses any expression not starting with a quote, so the ternary BENTO-AUTHORING gives as correct fails, and nobody saw it because the console had none. → §LXIV.15
- 📋 **SH1076** (deps: viglet-core 2026.3.11 published) **every CI suite fails before it starts, because the viglet-core BOM version is not in Maven Central** — pom.xml imports viglet-core-bom 2026.3.11, which only a local ~/.m2 holds, so Maven cannot read the POM in CI while every local gate passes. → §LXIV.16
- 📋 **SH1089** (deps: —) **a changelog can name a table that does not exist, and only a clean instance ever finds out** — The unit suite builds H2 from entity metadata with Liquibase off, so an addColumn on a misspelled table compiles, passes 2233 tests, and refuses to start a fresh store. → §LXIV.17
- 📋 **SH1115** (deps: —) **the coverage mirror marks ids named from a working copy nobody checked was committed** — It reads COVERAGE.md from a directory rather than a revision, so an uncommitted edit names ids here forever while the published matrix never gains the sentence. → §LXIV.18
- 📋 **SH1118** (deps: SH1049 ✅) **the shared comment stripper silently deletes half a file, and every scan over it passes** — A slash and two stars in any string opens a block comment to it, so one Playwright pattern cut 43,389 characters out of what thirty-one suites read. → §LXIV.19
- 📋 **SH1119** (deps: SH1052 ✅) **two committed regexes hold a literal backspace where a word boundary was meant, so both assert nothing** — A heredoc expanded the escape before the file was written, and the check that internal task ids do not ship has matched nothing since. → §LXIV.20
- 📋 **SH1120** (deps: —) **shio verify --delivery runs fewer checks than shio verify, silently dropping page and a11y** — withDelivery hand-copies the server's default as content,routes,diagnostics, so the flag asking for the stronger proof sends the narrower run. → §LXIV.21
- 📋 **SH1124** (deps: —) **the console lint CI runs has 19 errors no suppression covers, because no local gate runs it** — SH665 suppressed the findings of its day so anything new would be red; nineteen arrived since, unseen, because the three stamped gates never run the lint. → §LXIV.22

## Block AZ — What the content said before

- 📋 **SH988** (deps: SH987 ✅, DS VDS140) **the version dialog compares two whole values and shows nothing at all for a created page** — The review diff is gated on a published twin existing, so a create, the commonest agent output, has no review affordance, and a changed body renders as two walls of text. → §LXV.4
- 📋 **SH1080** (deps: SH989 ✅) **the pushed change feed cannot name the fields each write changed, so shio changes --follow refuses --fields** — A session that follows the stream to watch a run land sees that each page changed and has to poll a second read to learn which fields. → §LXV.6

## Block BA — Releases, scheduling and the publish gate

- 📋 **SH1081** (deps: SH990 ✅) **the TypeScript client passes neither state nor locale on its CDA reads, so a front end cannot preview a draft** — SH990 gave every CDA read a state and the route has taken locale since SH53, but getPost, getPostByUrl, listChildren and query build a fixed query string. → §LXVI.10
- 📋 **SH1082** (deps: SH991 ✅, a calendar component in the viglet design system) **the Scheduled page is a list only, with no month or week calendar and no drag to reschedule** — The console has no calendar component, and one built inside Shio is a copy the duplicate gate refuses. → §LXVI.11
- 📋 **SH1083** (deps: SH992 ✅) **POST /agent/batch accepts an op field it does not know, so a misspelled cancel publishes now** — Measured on a clean instance: an op carrying cancle answered 200 and planned the write, where shio_write since SH992 refuses the same field by name. → §LXVI.12
- 📋 **SH1084** (deps: —) **two publish paths never evaluate a gate: the review queue's Approve and the editor's PATCH with publish** — SH997 says every publish passes a gate and two do not, so a site's checks are enforced on three surfaces and silently skipped on two. → §LXVI.13
- 📋 **SH1085** (deps: —) **nine casings use the JVM's default locale, so a Turkish instance emits publısh and mints different URLs** — P5 asks for byte-identical serialization and these make the wire value, the friendly URL and an import slug depend on where the JVM runs. → §LXVI.14

## Block BB — The curator's return channel, and the conversation with the agent

- 📋 **SH1006** (deps: SH1003 ✅, SH1000 ✅, SH967 ✅, SH964) **a curator cannot ask the connected agent anything from the assistant dock** — VigletAssistant opens into a chat only when a product supplies onSend, and Shio hosts no model, so the chat must reach the external agent and nothing relays to it. → §LXVII.8
- 📋 **SH1007** (deps: SH1006, SH1001 ✅, DS VDS134) **an agent's answer in the dock cannot carry the change it proposes** — A reply that says it drafted a new hero leaves the curator searching for the draft, while each dock message can offer an action and review already groups changes by run. → §LXVII.9
- 📋 **SH1086** (deps: SH1005 ✅) **the trail's four new filters reach REST and the console, and neither MCP nor the CLI** — SH1005 gave /agent/changes action, actor, subject and address; shio_changes and shio changes still offer site, since and by, so P1's own order runs backwards for them. → §LXVII.11
- 📋 **SH1087** (deps: SH1005 ✅) **the console's live-update client is proved by reading its source and never by receiving an event** — A stream that silently never delivers looks exactly like a quiet instance, and SH1005's only assertions about it are that the file says EventSource and withCredentials. → §LXVII.12

## Block BC — The authoring surface, field by field

- 📋 **SH1019** (deps: SH1006, SH1007) **a field cannot ask the connected agent for a suggestion it can apply** — The assistant's reference design offers a suggested title with a button to apply it, and Shio's only route to an answer is a request from the dock. → §LXVIII.11
- 📋 **SH1090** (deps: —) **no console page can block an in-app navigation, because the router is not a data router** — useBlocker and usePrompt both call useDataRouterContext, and main.tsx mounts BrowserRouter, so a form with unsaved work can only catch a tab close. → §LXVIII.12
- 📋 **SH1091** (deps: —) **no stateless caller can change one field of a post without declaring the whole document** — The console's merge verb refuses a cookieless caller, the CDA's has no If-Match, and the agent's only write replaces whole state, so a CLI correction cannot be both partial and safe. → §LXVIII.13
- 📋 **SH1092** (deps: —) **a draft lint reports clean without saying that five markup rules never ran against it** — markup scans published rows only, deliberately, and the notes on a state=draft report describe the indexes instead, so zero errors reads as a verdict it is not. → §LXVIII.14
- 📋 **SH1093** (deps: —) **two suite tests fail intermittently on Windows and pass on an unchanged re-run** — A blueprint install moves a directory across temp dirs and a CLI exit test bounds wall-clock at 100ms, so a held handle or a loaded machine reds a gate nothing is wrong with. → §LXVIII.15
- 📋 **SH1094** (deps: —) **the live preview is a card under the form, so reading it means scrolling away from the fields** — SH1014 left the split view unbuilt because two panes with a remembered ratio change the editor's shape rather than adding to it, which is a design-system question first. → §LXVIII.16
- 📋 **SH1095** (deps: —) **an author editing content as files cannot see the page before pushing it** — Rendering from proposed values reached MCP, REST and the console, and the files tier — the one P4 calls the agent's cheapest — has no verb for it. → §LXVIII.17

## Block BD — Finding, selecting and acting on content

- 📋 **SH1024** (deps: SH1022 ✅, SH1023 ✅, DS VDS139) **the console has no filter bar, so the query vocabulary is reachable only by typing parameters** — BD3 gives four surfaces one query grammar and a person has no control for it, so drafts of one type in one locale is a question only a URL can ask. → §LXIX.5
- 📋 **SH1026** (deps: SH1025 ✅, DS VDS138) **three view-mode buttons highlight when pressed and render the identical list** — viewMode is read only to colour the button that set it, so the grid and table views a curator is offered do not exist, and the list below is one unconditional map. → §LXIX.7
- 📋 **SH1097** (deps: SH1028 ✅) **the shortcuts sheet advertises a key nothing binds and hides the one the shell does bind** — BentoShortcutsDialog renders a fixed list naming a global question mark, which Shio has never bound, and omits the slash SH889 added. → §LXIX.10

## Block BE — Media: what the bytes mean

- 📋 **SH1099** (deps: —) **on PostgreSQL no SQL predicate over post data matches: search finds no body text, each post leaks a large object** — Hibernate writes the @Lob data column through pgjdbc as a large object, so the column holds an OID and only the entity read resolves it. → §LXX.8
- 📋 **SH1100** (deps: —) **two reference tables nothing writes are still backfilled and asserted, and readers find them first** — ShReference joins ShObject and a post is not one, so no writer can use it, yet it sits in TENANT_TABLES and in two schema ITs. → §LXX.9

## Block BF — The visual editor becomes a composer

- 📋 **SH1101** (deps: SH1038 ✅) **a copied post comes back with no id, so duplicating a section can only place the same one twice** — `copyto` returns only the folders it made, so nothing can address a post it just copied, and the editor's Duplicate repeats the reference instead. → §LXXI.8
- 📋 **SH1105** (deps: SH1039 ✅) **the inserter rail describes a section in a sentence, which is the worst way to show a shape** — A curator picks between a hero and a rich-text block by looking, and the rail has no thumbnail and no drop target — so placement is two buttons and nothing can be dragged. → §LXXI.9
- 📋 **SH1106** (deps: SH1039 ✅) **a page can name a section of a type its own template says does not belong there, and nothing says so** — `sh-accepts` is read by the console and by nothing else, so any write past that screen composes a page against a list no instrument consults. → §LXXI.10
- 📋 **SH1108** (deps: SH1040 ✅) **the console cannot offer patterns, because it cannot read the project file they live in** — A pattern is a file in the tree and the console is a browser, so the inserter rail's Patterns tab has nothing to list and Save as pattern has nowhere to write. → §LXXI.11
- 📋 **SH1109** (deps: SH1041 ✅) **a save in the visual editor proves nothing — it returns a toast and the curator has to look** — The render digests, the change log and the reporting dock all exist and nothing joins them, so the one surface where a person edits by hand closes its loop with the word saved. → §LXXI.12
- 📋 **SH1110** (deps: SH1042 ✅) **a share link for one page lets its reader see every draft on that site until it expires** — The credential is bounded to a site because the token row has no target, and narrowing it needs an answer about how a shared page's own images and stylesheet are reached. → §LXXI.13
- 📋 **SH1111** (deps: SH1042 ✅) **a share link is sent and then nobody can see it, because the token screen hides ephemeral rows** — Revocation works and the reading does not: nothing lists the live links, and an agent that just built a site has no verb that prints one. → §LXXI.14

## Block BG — The console as an application: cost, resilience and access

- 📋 **SH1051** (deps: DS VDS137) **Shio's accent fails AA contrast on both grounds, and its primary token is never claimed** — Shio's accent gate asserts the tokens exist and never what they measure, and the primary inputs are unset, so the rail's you-are-here marker renders the preset's neutral. → §LXXII.9
- 📋 **SH1053** (deps: DS VDS141, SH964) **the console has no skip link, most pages have no main landmark, and navigation never moves focus** — A keyboard reader tabs through the rail and header on every page, and after a route change stays where they were, because no shell owns a main region or focus. → §LXXII.11
- 📋 **SH1054** (deps: SH1052 ✅, SH1053) **no accessibility check runs against a rendered console page** — The existing rules scan source for four patterns, there is no axe run, no jsx-a11y plugin and one viewport, so a violation composed across components ships unseen. → §LXXII.12
- 📋 **SH1075** (deps: —) **the console lint fails in CI on an error the suppressions file does not hold, and no local gate runs it** — Re-measured at twenty errors over seventeen files, red in CI for weeks while pnpm test and the baseline test stay green, and red-suites.json has never held an entry for it. → §LXXII.14
- 📋 **SH1077** (deps: SH967 ✅) **an open change stream re-walks each entry's folder chain for every stream, so its cost grows as entries × streams** — SH967 measured 7.05 statements per entry per stream, and canRead's chain and principals do not change in a pass, so a per-pass memo can answer them once. → §LXXII.15
- 📋 **SH1078** (deps: —) **a move changes which URLs a visitor can reach and its answer names only the objects it moved** — Copy and move return the touched objects, so no caller learns which delivery addresses changed without a second verify --delivery run. → §LXXII.16
- 📋 **SH1079** (deps: —) **an import creates a whole site and answers with the site alone, so what arrived is only knowable by re-reading** — POST /api/v2/import returns ShSite and no tally of what landed, so the write whose effect a caller can least predict carries the least proof. → §LXXII.17
- 📋 **SH1112** (deps: SH1043 ✅) **an agent cannot ask a running instance what its console costs to load** — The budget's two readers are a pnpm script and a build gate, so checking a console change needs this checkout, its toolchain and a build — which is P1 on the surface P1 is about. → §LXXII.18
- 📋 **SH1113** (deps: SH1043 ✅) **nothing measures what one console route costs, so a screen's own fetches hide inside a total** — SH1043 counts the bytes every route pays before the first row, which is blind to a page that then fetches three collections to draw two figures. → §LXXII.19
- 📋 **SH1114** (deps: SH1045 ✅) **the reachability walk reads one manifest of eleven, and four of the ten it skips are published** — SH1045 found eighteen dead declarations in the console and nothing asks the same question of the CLI, the SDKs or the starter, where a consumer pays for the answer. → §LXXII.20
- 📋 **SH1116** (deps: SH1046 ✅, SH1047 ✅) **thirty-five console screens still read inside an effect and cancel nothing** — SH1047 migrated the five its symptom named and declared the rest, so the same race is recorded rather than fixed on every other screen that fetches. → §LXXII.21
- 📋 **SH1117** (deps: SH1048 ✅) **the CLI never sends If-None-Match to Shio, so every run pays the full body of an unchanged read** — SH1048 made the console's reads answer 304, which a browser takes for free and Node does not — the CLI sends a conditional GET only to external assets it clones. → §LXXII.22

## Block BH — One content model, many languages

- 🛠 **SH1056** (deps: SH952 ✅) **no site can declare its default locale, its enabled locales or a fallback order** — The console has no Languages card and SH53's translations menu still offers every seeded tag, so a declared policy changes nothing a curator sees. → §LXXIII.1
- 📋 **SH1057** (deps: SH1056 🛠) **the public locale segment never selects content, and most of delivery cannot ask for a locale** — The segment reaches only the validator and the link prefix, and CDA list, query and search, GraphQL and the TypeScript client take no locale at all. → §LXXIII.2
- 📋 **SH1058** (deps: SH1056 🛠) **nothing knows a translation is out of date, so editing the source leaves every translation claiming to be current** — Translate copies the source's data and records only the group id, with no source revision or digest, and the only translation rule asks about absence. → §LXXIII.3
- 📋 **SH1060** (deps: SH1058, SH1022 ✅) **no surface shows translation coverage, so what is missing in pt-br is a full scan nobody runs** — shio_find cannot filter by locale though every row carries one, and the only coverage report is a warning that returns early on a scoped scan. → §LXXIII.5
- 📋 **SH1061** (deps: SH1056 🛠) **translating means leaving the source, so the two languages are never visible together** — The whole translation interface is a menu that navigates to the sibling post, so a translator works from memory or a second browser tab. → §LXXIII.6
- 📋 **SH1062** (deps: SH1060, SH1006) **a curator cannot ask the connected agent to translate a page or fill a locale's gaps** — Shio hosts no model, and the assistant's request relay has no translation form, so the coverage board's missing cells can be read and not handed to the agent. → §LXXIII.7

## Block BI — The areas this round did not reach

- 📋 **SH1063** (deps: —) **nothing in the console, the CLI or MCP can say whether a published page reached the search index** — searchIndexing is a live manifest feature whose only surfaces are properties, a listener and an incident log entry, and no controller or console page reads index state. → §LXXIV.1
- 📋 **SH1064** (deps: SH996 ✅) **changing a page's friendly URL breaks every inbound link, and no writer ever creates the redirect Shio models** — REDIRECT is a system post type the renderer serves with a permanent flag choosing 301 or 302, and no rename, move, unpublish or delete path writes one. → §LXXIV.2
- 📋 **SH1065** (deps: —) **site export is an id-keyed zip with no CLI verb, no MCP reach and no console button** — Import has REST, a CLI verb and an example while export is reachable only by building a URL from a resolved uuid, so no surface moves a site between instances. → §LXXIV.3
- 📋 **SH1066** (deps: SH970 ⏳, SH1022 ✅) **form submissions have no inbox and a site's form destination has no settings screen** — A submission is written as a draft into a folder a developer configured, the nav has no Forms entry, and a curator must guess the folder to find them. → §LXXIV.4
- 📋 **SH1067** (deps: —) **replication lives only in the CLI, with no manifest feature key the P1 census could classify** — shio clone, convert and propose are a differentiator with no MCP reach and no console entry, and without a key ShAgentP1ConformanceTest cannot see the gap at all. → §LXXIV.5
- 📋 **SH1068** (deps: —) **the conformance gate knows none of the vocabulary this round adds, so its docs checks stay green over new names** — pnpm conformance pins documented tools, ops, properties and verbs against the code, and the round adds dozens with no line updating its vocabulary sources. → §LXXIV.6
- 📋 **SH1069** (deps: —) **no task carries the documentation sweep for this round, so the public docs will describe a console that has changed** — The block-completion sweep of docs-shio, the coverage matrix and the public site is required when a block's last task ships, and no block here declares one. → §LXXIV.7
- 📋 **SH1102** (deps: —) **roadkeep lint has six findings that were never drift, so the docs gate cannot come back clean** — `code.renamed` reads a browser global and a filename as symbols this repository should define, so six ledger entries that were correct when written keep the gate permanently red. → §LXXIV.8
- 📋 **SH1103** (deps: —) **a gate stamp stays fresh across the ship that changes what it certified, so a red commit lands unseen** — Staleness is the HEAD sha and `ship` writes the governed docs at that same HEAD — which the JS suite reads, so a ship turns `pnpm test` red after its last green run. → §LXXIV.9
- 📋 **SH1104** (deps: SH1039 ✅) **the editable read is on no response budget, and it just grew a section that scales with the catalogue** — Sixteen responses are measured and `/agent/editable` is not one, so `include=insertable` has no ceiling and the budget fixture renders no page to measure it against. → §LXXIV.10
- 📋 **SH1121** (deps: SH1055 ✅) **the landing blueprint builds pages with no main region and no h1, which the a11y check reports on each** — marketing-site was repaired because a benchmark caught it; landing has the same defect and no assertion holds any blueprint to our own gate. → §LXXIV.11
- 📋 **SH1122** (deps: SH1056 🛠) **a site's own settings are projected to no file, so url, bindings and the locale policy cannot be authored or diffed** — A site root's _folder.md says nothing about the site, so the one object that decides whether a page renders is reachable only through the API. → §LXXIV.12

## Priority

## Done when — SH1072

- **A fresh clone of vigletdocs reads a matrix that names the six ids** The COVERAGE.md
  edit is committed in vigletdocs. Until then it exists only in that working tree, so
  the green JS gate here rests on an uncommitted file and any other checkout still reads
  the old matrix.

## Done when — Block AW

- **The two-chrome census reads zero** shio-react/test/chrome-vocabulary-census.test.mjs
  prints src and test/scripts/e2e line counts on every pnpm test; STILL_TWO_CHROMES is
  empty and the run prints 0 lines on both sides.

## Done when — SH969

- **A planted failing schedule becomes a dock report whose retry publishes** A console
  test drives a schedule incident with a retry through the stream and reads a report
  with tone error and open and retry actions. Pressing retry publishes the post. A
  success reports the page, and a failure raised while no screen was open is unread at
  the next login.

## Done when — SH970

- **An arriving submission becomes a dock report a curator can open** A console test
  pushes a submission's arrival through the stream and reads a report with tone
  attention, a caption from the title and summary, and actions to open and to archive
  it. Several arrivals in one moment are one report rather than a row each.

## Done when — SH1056

- **A front end can build a switcher from the CDA alone** GET /api/v2/cda/site/{id} and
  the listing both carry the resolved default and enabled set, so a headless front end
  has something to negotiate against. Batched, because the listing answers for every
  readable site. Shipped in part one: the policy, site.upsert, the context pack and the
  render frame.
- **The console stops offering forty-five locales on every post** SH53's translations
  menu filters to the site's enabled set, and the site page gets a Languages card that
  can set the policy — the only surface that may change one, since site.upsert
  deliberately fills and never replaces. A site with no declared policy keeps today's
  menu.
- **The policy is authorable as a file, or SH1122 owns that half** The design asked for
  a locales block in the site marker file, and there is no site marker file — a site's
  row is projected nowhere. Either SH1122 lands the file and a locales block goes in it,
  or this line records that the file half moved there and closes without it.

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
- **The publish gate is a checklist, not an orchestrator** No workflow triggers,
  branches, waits or flow builder inside Shio: a gate is a declared list of checks
  evaluated on publish and answering a teaching 409, and GATE_FAILED is the edge an
  external orchestrator acts on, because an engine here would be a third contract (P10).
- **Awareness, not co-editing: no CRDT or shared-document runtime** The second editor on
  a page is usually a machine. If-Match, a teaching 409, a short-lived working mark and
  a presence line solve a lost update, and a presence signal must never become a lock
  that blocks a write.
- **The agent never approves, holds or rejects its own work** Review decisions stay off
  the agent namespace and the manifest keeps the approve route out of its index. An
  agent may read the verdict on its own runs through a read-only resource, and nothing
  more.
- **The assistant is answered by the connected agent, never by a model**
  VigletAssistant's composer relays a curator's request to the external agent as a note.
  Shio adds no endpoint, key, prompt or model id for it, and with no agent connected the
  dock shows no composer and stays a status surface.
- **A new capability does not arrive as a thirteenth MCP tool** tools/list is resent
  every turn and sits near its ceiling. The ladder is an op name, then a parameter, then
  a resource, then an endpoint, and a tool is added only in a commit that names the tool
  that leaves.
