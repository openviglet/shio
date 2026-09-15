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

## Block AW — One chrome: bento, and nothing left that names another

- 📋 **SH963** (deps: SH958 ✅, DS VDS146) **Shio still has to declare a chrome at all, because the package keeps a switch with a console value** — Until the design system drops SectionChrome the root provider must stay, and while it stays the census cannot reach zero. → §LXII.7

## Block AX — The Viglet assistant: where the console reports, and how the curator reaches the agent

- 📋 **SH964** (deps: DS VDS133) **the console mounts no Viglet assistant, so its 106 toasts reach no mascot and no dock** — VigletAvatar, VigletAssistant and useAssistantNotifications shipped in the design system's Block E for a CMS screen like this one, and no consumer has mounted them. → §LXIII.1
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

## Block AZ — What the content said before

- 📋 **SH988** (deps: SH987 ✅, DS VDS140) **the version dialog compares two whole values and shows nothing at all for a created page** — The review diff is gated on a published twin existing, so a create, the commonest agent output, has no review affordance, and a changed body renders as two walls of text. → §LXV.4
- 📋 **SH1080** (deps: SH989 ✅) **the pushed change feed cannot name the fields each write changed, so shio changes --follow refuses --fields** — A session that follows the stream to watch a run land sees that each page changed and has to poll a second read to learn which fields. → §LXV.6

## Block BA — Releases, scheduling and the publish gate

- 📋 **SH996** (deps: SH995 ✅) **the console's publish has no dry run, no impact preview and no confirm, while the agent's has all three** — Nothing tells a curator which URLs go live, change or stop resolving before the button writes, and include=impact answers template blast radius, not routes. → §LXVI.7
- 📋 **SH997** (deps: SH993 ✅, SH996) **nothing checks a page before it goes live, because verify, audit and the render digest are all opt-in** — Shio's pre-publish instruments run only when somebody remembers, and its webhook events all fire after the commit, so no check stands between content and visitors. → §LXVI.8
- 📋 **SH998** (deps: SH997, SH995 ✅) **a failed gate cannot be waived with a recorded reason, and nothing outside Shio hears it failed** — A check nobody may waive becomes one people disable, and webhooks fire only after commit, so no external approver is told to step in. → §LXVI.9
- 📋 **SH1081** (deps: SH990 ✅) **the TypeScript client passes neither state nor locale on its CDA reads, so a front end cannot preview a draft** — SH990 gave every CDA read a state and the route has taken locale since SH53, but getPost, getPostByUrl, listChildren and query build a fixed query string. → §LXVI.10
- 📋 **SH1082** (deps: SH991 ✅, a calendar component in the viglet design system) **the Scheduled page is a list only, with no month or week calendar and no drag to reschedule** — The console has no calendar component, and one built inside Shio is a copy the duplicate gate refuses. → §LXVI.11
- 📋 **SH1083** (deps: SH992 ✅) **POST /agent/batch accepts an op field it does not know, so a misspelled cancel publishes now** — Measured on a clean instance: an op carrying cancle answered 200 and planned the write, where shio_write since SH992 refuses the same field by name. → §LXVI.12

## Block BB — The curator's return channel, and the conversation with the agent

- 📋 **SH999** (deps: —) **agent attribution is on every audit row and dropped by both readers of the audit trail** — The console history response and the agent change feed both report the bare principal, and the feed's javadoc still promises a distinction a later change removed. → §LXVII.1
- 📋 **SH1000** (deps: SH999) **an agent run carries no statement of what it was trying to do** — The review queue renders a count of changes by an actor over an opaque session id, with no intent, plan, ticket or rationale stored anywhere on the run. → §LXVII.2
- 📋 **SH1001** (deps: SH1000, SH995 ✅) **review is approve everything or revert everything, and a rejection has nowhere to record why** — One bad page in a long run forces a curator to accept all of it or undo all of it, while each entry already carries a revertable flag nothing lets them use. → §LXVII.3
- 📋 **SH1002** (deps: SH1001) **an agent cannot read the verdict on its own run, so every correction needs a person to re-explain it** — Approval rightly stays off the agent namespace, and no read-only resource lets a session learn that a change was rejected, held or reverted, or why. → §LXVII.4
- 📋 **SH1003** (deps: SH999) **a curator's correction has nowhere to live, with no note anchored to a page, a field or a revision** — No comment or annotation entity exists in the backend, so the only channel back to the agent is a person re-explaining out of band. → §LXVII.5
- 📋 **SH1004** (deps: SH1003) **notes, constraints, translation metadata and asset metadata land in the file projection with no merge rule** — Only releases state how a concurrent edit merges, so a pull carrying new sidecar fields can silently lose a local edit against content-base.json. → §LXVII.6
- 📋 **SH1005** (deps: SH999) **the activity trail cannot be paged or filtered, and a post has no history panel** — Every history read is page zero capped at five hundred with no cursor or filter, while the agent's change feed already has a keyset cursor and a measured budget. → §LXVII.7
- 📋 **SH1006** (deps: SH1003, SH1000, SH967 ✅) **a curator cannot ask the connected agent anything from the assistant dock** — VigletAssistant opens into a chat only when a product supplies onSend, and Shio hosts no model, so the chat must reach the external agent and nothing relays to it. → §LXVII.8
- 📋 **SH1007** (deps: SH1006, SH1001, DS VDS134) **an agent's answer in the dock cannot carry the change it proposes** — A reply that says it drafted a new hero leaves the curator searching for the draft, while each dock message can offer an action and review already groups changes by run. → §LXVII.9
- 📋 **SH1008** (deps: SH967 ✅, SH999, SH985 ✅) **a curator editing a page cannot see that an agent is working on the same page right now** — Concurrency guards refuse a stale write after the fact, and nothing shows, before anyone types, that another writer is active on the address. → §LXVII.10

## Block BC — The authoring surface, field by field

- 📋 **SH1009** (deps: —) **a required field saves empty from the console, from REST and from the agent batch alike** — The form paints an asterisk and nothing reads required again, and the only enforcement is required-field-empty, raised later if somebody runs verify. → §LXVIII.1
- 📋 **SH1010** (deps: SH1009) **a field can declare only whether it is required, with no length, pattern, range, enum or uniqueness** — The attribute definition carries name, label, widget, required and a few flags and no constraint vocabulary, so every rule beyond presence lives in someone's head. → §LXVIII.2
- 📋 **SH1011** (deps: SH985 ✅) **the post editor has no dirty state, no autosave and no unsaved-change guard** — Closing the tab mid-edit loses everything silently, while the design system ships the unsaved cue and the backend ships the field-level merge verb. → §LXVIII.3
- 📋 **SH1012** (deps: —) **Relator, the only repeatable field, is read-only, while the documentation says it adds and removes items** — The widget takes onChange in its props and never calls it and prints that editing is disabled, while the whole data chain for nested items round-trips. → §LXVIII.4
- 📋 **SH1013** (deps: SH955 ✅) **the editor never calls the content intelligence the backend already computes for the page being edited** — The console makes no request to the agent surface, so dozens of verify rules and a structural page digest stay invisible to the person who could fix what they find. → §LXVIII.5
- 📋 **SH1014** (deps: SH1011) **previewing unsaved edits is impossible, because the preview renders the last saved draft** — The preview token addresses a post id rather than a payload, and no endpoint renders a page from proposed field values, for a curator or an agent. → §LXVIII.6
- 📋 **SH1015** (deps: SH956 ✅) **a page body is one opaque HTML string, so an agent cannot address a paragraph** — The rich-text value is innerHTML with no schema, so Edit lands inside one blob, canonical serialisation is impossible and the digest must parse markup to find structure. → §LXVIII.7
- 📋 **SH1016** (deps: SH1015) **a front end cannot receive structured blocks, only the rendered HTML of a body** — The CDA, GraphQL and both SDKs return a body as a string, so a headless site cannot render a quote or an image block with its own components. → §LXVIII.8
- 📋 **SH1017** (deps: SH1015) **a curator has no editor for block bodies, only the execCommand toolbar over a contentEditable** — Blocks stored and delivered are unusable by a person without a slash menu, drag handles and keyboard moves, so curators would keep choosing the HTML widget. → §LXVIII.9
- 📋 **SH1018** (deps: SH1010) **changing a post type leaves existing content violating it, with no report and no migration** — Constraints are checked on write only, and a renamed or removed field leaves every existing post holding data under a key the model no longer declares. → §LXVIII.10
- 📋 **SH1019** (deps: SH1006, SH1007) **a field cannot ask the connected agent for a suggestion it can apply** — The assistant's reference design offers a suggested title with a button to apply it, and Shio's only route to an answer is a request from the dock. → §LXVIII.11

## Block BD — Finding, selecting and acting on content

- 📋 **SH1020** (deps: —) **the content browser's rows have no keyboard path, and eight row actions exist only under a hovering pointer** — The row action block is hidden group-hover:flex with no onKeyDown, tabIndex or selected state, so a keyboard user can open a post and do nothing else to it. → §LXIX.1
- 📋 **SH1021** (deps: SH1020, SH995 ✅) **there is no bulk publish or unpublish on REST or in the console** — The console's batch menu is copy, move and delete, and REST has only single-id publish, so a curator cannot publish a selection that an agent publishes atomically. → §LXIX.2
- 📋 **SH1022** (deps: SH977 ✅) **no surface can filter content by type, state, locale, date or author, including the agent's own find** — The console listing takes page, size, sort and q, and /agent/find filters in memory after loading matches, so drafts changed this week by one person cannot be asked for. → §LXIX.3
- 📋 **SH1023** (deps: —) **no list in the console is deep-linkable, so the same address never reproduces the view on screen** — Search, sort, folder filter and view mode are component state on every list page, so there is no URL for a filtered view a curator could share or an agent could hand over. → §LXIX.4
- 📋 **SH1024** (deps: SH1022, SH1023, DS VDS139) **the console has no filter bar, so the query vocabulary is reachable only by typing parameters** — BD3 gives four surfaces one query grammar and a person has no control for it, so drafts of one type in one locale is a question only a URL can ask. → §LXIX.5
- 📋 **SH1025** (deps: SH1022) **the browser loads every child folder on every page and never virtualises, and nothing measures what that costs** — The navigation service fetches all child folders by design and the browser sorts them client-side, so a large folder's cost is argued about rather than known. → §LXIX.6
- 📋 **SH1026** (deps: SH1025, DS VDS138) **three view-mode buttons highlight when pressed and render the identical list** — viewMode is read only to colour the button that set it, so the grid and table views a curator is offered do not exist, and the list below is one unconditional map. → §LXIX.7
- 📋 **SH1027** (deps: SH1022) **console search reads the published-only delivery API, so a reviewer cannot find an agent's drafts** — The search page calls the CDA, whose state is PUBLISHED and whose preview branch reads a token the console never sends, while the agent's own search reads both states. → §LXIX.8
- 📋 **SH1028** (deps: SH1027, SH979 ✅) **the command palette lists seventeen routes and can neither find a post nor run an action** — Shio supplies only its nav array, so reaching a page takes two searches, while the verbs a curator wants exist as named operations the palette never shows. → §LXIX.9

## Block BE — Media: what the bytes mean

- 📋 **SH1029** (deps: —) **the media listing parses each asset's JSON several times and stats the disk for every row before filtering** — Every File post is materialised through a mapper that re-reads the same data string per key, and only then are the filters applied, so the cost grows with the tenant. → §LXX.1
- 📋 **SH1030** (deps: SH1029) **the media listing has no paging and the console filters and counts the whole tenant in the browser** — The one content read nobody paged is fetched unfiltered, so a large library loads in full before a single card renders and an agent's asset list silently truncates. → §LXX.2
- 📋 **SH1031** (deps: —) **alt text is optional and the image helper guarantees the accessibility rule can never fire** — The helper writes an empty alt when ALT is null and the verify rule fires only on a missing attribute, so on a site using the recommended helper image-no-alt is unreachable. → §LXX.3
- 📋 **SH1032** (deps: —) **asset metadata and usage are reachable only from the console and REST, and the P1 census cannot see them** — No MCP op, no CLI verb and no manifest feature key name them, so the conformance test that exists to catch exactly this has nothing to classify. → §LXX.4
- 📋 **SH1033** (deps: —) **where-is-this-used runs an unindexed LIKE over every post twice per dialog, and nobody has measured it** — The usage query wraps the file id in wildcards over the whole data column once per state, and the analysis argued a reverse index without a number to justify one. → §LXX.5
- 📋 **SH1034** (deps: SH1033) **no surface can list the assets nothing references, so storage is reclaimed by guesswork** — Usage is answerable only from an asset toward its posts, so finding unused assets means one full scan per asset, and verify has orphan rules for posts and folders but none for assets. → §LXX.6
- 📋 **SH1035** (deps: SH1032) **replacing an asset's bytes exists in the API and not in the console, and the File widget dead-ends when unconfigured** — The console's only route to new bytes is delete and re-upload, which mints a new id and breaks every field pointing at the old one. → §LXX.7

## Block BF — The visual editor becomes a composer

- 📋 **SH1036** (deps: —) **edit mode does not survive a click inside the preview, while the badge still reads connected** — Both link rewriters drop the renderer's editor and state parameters, so the frame lands on a page with no bridge while the console keys its handshake on an unchanged src. → §LXXI.1
- 📋 **SH1037** (deps: SH1036, SH956 ✅) **the bridge has no protocol version, no capability negotiation and no idea of a field's kind** — Ready carries a post id and a URL, so the console cannot tell what a front end supports, and every field becomes contenteditable HTML whatever widget declared it. → §LXXI.2
- 📋 **SH1038** (deps: SH1037) **a section is not a selectable object, so the editor can change a hero's headline and never the hero** — The bridge binds only the two prop attributes and reports text or attr, so a section cannot be selected, moved, duplicated or removed, though the agent can do all four. → §LXXI.3
- 📋 **SH1039** (deps: SH1038) **nothing tells the editor what may be inserted into a page, or where** — The editable response returns fields and regions and no insertable set, so the section types reach the console only as post-type names in a picker, never as something to place. → §LXXI.4
- 📋 **SH1040** (deps: SH1039) **a well-built block on one page cannot be captured and reused as a pattern** — Blueprints have a fixed target, no insert mode and no capture, so the section a curator perfected must be rebuilt by hand or by an agent every time it is wanted. → §LXXI.5
- 📋 **SH1041** (deps: SH1036) **the visual editor has no undo or discard, and a refresh leaves pending changes counted that are no longer visible** — The pending set supports set and clear-all only, refresh reloads the frame without touching it, and leaving the route drops edits without asking. → §LXXI.6
- 📋 **SH1042** (deps: —) **a stakeholder cannot be sent an expiring link to a draft rendered by Shio itself** — The preview route is console-authenticated and the token mint returns a null token for an internal site, so only sites with an external front end can be previewed by anyone else. → §LXXI.7

## Block BG — The console as an application: cost, resilience and access

- 📋 **SH1043** (deps: —) **nothing measures what the console costs to load, in a project where a token regression fails the build** — There is no size budget for the console while the agent's cost is governed per endpoint, which is how an eager multi-megabyte chunk arrived with nobody noticing. → §LXXII.1
- 📋 **SH1044** (deps: SH1043) **the lazy GraphiQL split is defeated, so the editor's chunk loads on every console route** — The manual-chunk rule claims every module id containing codemirror, and the widget map imports CodeMirror eagerly, so the entry statically imports the GraphiQL vendor chunk. → §LXXII.2
- 📋 **SH1045** (deps: SH1043) **the console declares dependencies that nothing imports, and nothing would notice another** — The table library, class utilities, a Radix package and others appear in the manifest and in no import, and no lint checks that a declared dependency is reachable. → §LXXII.3
- 📋 **SH1046** (deps: SH1043) **the console has no query cache, so every mount refetches whole collections from scratch** — Seventeen call sites fetch whole collections and the browser refetches the entire post-type catalogue on every folder navigation, while one hand-rolled store shows the fix. → §LXXII.4
- 📋 **SH1047** (deps: SH1046) **no console fetch can be cancelled, so a fast navigation renders whichever response arrives last** — AbortController appears nowhere, and the browser's page fetch is recreated on each folder, sort or query change with nothing cancelling the request before it. → §LXXII.5
- 📋 **SH1048** (deps: SH1046) **the console API sends no ETag and no Cache-Control, so an unchanged read costs its full body every time** — Both cache filters are registered for the delivery and agent paths only, so every endpoint the console calls answers a full 200 with no validator. → §LXXII.6
- 📋 **SH1049** (deps: SH1046, SH955 ✅) **one error boundary guards the whole console, and a failed fetch leaves a spinner that never ends** — A render throw blanks the application, an unhandled load settles into a permanent spinner, and the design system's load provider and skeleton are used by nothing. → §LXXII.7
- 📋 **SH1050** (deps: SH1048, SH991 ✅, SH1003) **the dashboard downloads two whole collections to show two numbers** — Home fetches every site and every post type to use their length, and in an agent-first CMS its question should be what the agent did that nobody has looked at. → §LXXII.8
- 📋 **SH1051** (deps: DS VDS137) **Shio's accent fails AA contrast on both grounds, and its primary token is never claimed** — Shio's accent gate asserts the tokens exist and never what they measure, and the primary inputs are unset, so the rail's you-are-here marker renders the preset's neutral. → §LXXII.9
- 📋 **SH1052** (deps: —) **every post-editor field is a label with no htmlFor over a widget that cannot take an id** — The widget contract is attr, onChange and postId, so a screen reader meets each field of every post type as an unnamed edit box and a label click focuses nothing. → §LXXII.10
- 📋 **SH1053** (deps: DS VDS141, SH964) **the console has no skip link, most pages have no main landmark, and navigation never moves focus** — A keyboard reader tabs through the rail and header on every page, and after a route change stays where they were, because no shell owns a main region or focus. → §LXXII.11
- 📋 **SH1054** (deps: SH1052, SH1053) **no accessibility check runs against a rendered console page** — The existing rules scan source for four patterns, there is no axe run, no jsx-a11y plugin and one viewport, so a violation composed across components ships unseen. → §LXXII.12
- 📋 **SH1055** (deps: SH1031) **the content Shio delivers is checked for one accessibility rule, image-no-alt, and nothing else** — Verify has dozens of rules and one about accessibility, although the render path already builds the heading outline, landmarks and link inventory the rest would need. → §LXXII.13
- 📋 **SH1075** (deps: —) **the console lint fails in CI on an error the suppressions file does not hold, and no local gate runs it** — react-hooks/set-state-in-effect in bento-tenant-link-notice.tsx has failed pnpm lint since SH931, while pnpm test and the baseline test stay green. → §LXXII.14
- 📋 **SH1077** (deps: SH967 ✅) **an open change stream re-walks each entry's folder chain for every stream, so its cost grows as entries × streams** — SH967 measured 7.05 statements per entry per stream, and canRead's chain and principals do not change in a pass, so a per-pass memo can answer them once. → §LXXII.15
- 📋 **SH1078** (deps: —) **a move changes which URLs a visitor can reach and its answer names only the objects it moved** — Copy and move return the touched objects, so no caller learns which delivery addresses changed without a second verify --delivery run. → §LXXII.16
- 📋 **SH1079** (deps: —) **an import creates a whole site and answers with the site alone, so what arrived is only knowable by re-reading** — POST /api/v2/import returns ShSite and no tally of what landed, so the write whose effect a caller can least predict carries the least proof. → §LXXII.17

## Block BH — One content model, many languages

- 📋 **SH1056** (deps: SH952 ✅) **no site can declare its default locale, its enabled locales or a fallback order** — The render service says there is no per-site default in this model, a site's languages are inferred from its content, and the translations menu offers every seeded locale on every post. → §LXXIII.1
- 📋 **SH1057** (deps: SH1056) **the public locale segment never selects content, and most of delivery cannot ask for a locale** — The segment reaches only the validator and the link prefix, and CDA list, query and search, GraphQL and the TypeScript client take no locale at all. → §LXXIII.2
- 📋 **SH1058** (deps: SH1056) **nothing knows a translation is out of date, so editing the source leaves every translation claiming to be current** — Translate copies the source's data and records only the group id, with no source revision or digest, and the only translation rule asks about absence. → §LXXIII.3
- 📋 **SH1059** (deps: —) **the content browser discards the locale the server sends, so two languages of one page look identical** — The row type declares locale and the mapper keeps id, title, date, type and status, so a page and its translation render as two indistinguishable rows. → §LXXIII.4
- 📋 **SH1060** (deps: SH1058, SH1022) **no surface shows translation coverage, so what is missing in pt-br is a full scan nobody runs** — shio_find cannot filter by locale though every row carries one, and the only coverage report is a warning that returns early on a scoped scan. → §LXXIII.5
- 📋 **SH1061** (deps: SH1056) **translating means leaving the source, so the two languages are never visible together** — The whole translation interface is a menu that navigates to the sibling post, so a translator works from memory or a second browser tab. → §LXXIII.6
- 📋 **SH1062** (deps: SH1060, SH1006) **a curator cannot ask the connected agent to translate a page or fill a locale's gaps** — Shio hosts no model, and the assistant's request relay has no translation form, so the coverage board's missing cells can be read and not handed to the agent. → §LXXIII.7

## Block BI — The areas this round did not reach

- 📋 **SH1063** (deps: —) **nothing in the console, the CLI or MCP can say whether a published page reached the search index** — searchIndexing is a live manifest feature whose only surfaces are properties, a listener and an incident log entry, and no controller or console page reads index state. → §LXXIV.1
- 📋 **SH1064** (deps: SH996) **changing a page's friendly URL breaks every inbound link, and no writer ever creates the redirect Shio models** — REDIRECT is a system post type the renderer serves with a permanent flag choosing 301 or 302, and no rename, move, unpublish or delete path writes one. → §LXXIV.2
- 📋 **SH1065** (deps: —) **site export is an id-keyed zip with no CLI verb, no MCP reach and no console button** — Import has REST, a CLI verb and an example while export is reachable only by building a URL from a resolved uuid, so no surface moves a site between instances. → §LXXIV.3
- 📋 **SH1066** (deps: SH970 ⏳, SH1022) **form submissions have no inbox and a site's form destination has no settings screen** — A submission is written as a draft into a folder a developer configured, the nav has no Forms entry, and a curator must guess the folder to find them. → §LXXIV.4
- 📋 **SH1067** (deps: —) **replication lives only in the CLI, with no manifest feature key the P1 census could classify** — shio clone, convert and propose are a differentiator with no MCP reach and no console entry, and without a key ShAgentP1ConformanceTest cannot see the gap at all. → §LXXIV.5
- 📋 **SH1068** (deps: —) **the conformance gate knows none of the vocabulary this round adds, so its docs checks stay green over new names** — pnpm conformance pins documented tools, ops, properties and verbs against the code, and the round adds dozens with no line updating its vocabulary sources. → §LXXIV.6
- 📋 **SH1069** (deps: —) **no task carries the documentation sweep for this round, so the public docs will describe a console that has changed** — The block-completion sweep of docs-shio, the coverage matrix and the public site is required when a block's last task ships, and no block here declares one. → §LXXIV.7

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
