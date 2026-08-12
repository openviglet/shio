# The shio CLI

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### `shio` CLI (`@viglet/shio`, in `cli/`)
The "JS dev, zero Java" command-line tool: `init` (scaffold via `create-shio-app`; **`--blueprint <name>` takes the starter from the instance instead, and with `--site` applies its content half in the same command** — SH119; **`--site` with no `--blueprint` refuses a site whose Pages carry `sections`, because the built-in template renders a body and not sections** — SH502, and `blueprints.md` has the measurement), `types` (SH11 codegen), `pull`/`push` (post-types as code → `shio/post-types/*.json`, `--dry-run`/`--prune`; **`--content` switches both verbs to content**, see below), `context` (`--as agents-md` renders the pack as project agent instructions — SH115),
`resolve`, `apply` (SH129 — a desired-state content document; `--check` is the CI drift guard, `-` reads stdin; **`--blueprint <name>` applies a package instead**, with `--param k=v` repeatable, `--params <file>`, `--site`, `--force`), `publish` (SH546 — **the only call that changes what visitors see, on the tier that could not make it**: addresses as arguments or `--address`, `--site X --all` to name every post from a `find` first, `--type` to narrow, `--unpublish` for the inverse, `--dry-run`, and the batch ceiling is read from the refusal rather than copied out of the server's configuration — see below), `blueprints` (SH92 — the catalogue; `blueprints <name>` for one package's parameters), `verify` (SH99/SH100 — the content lint; **exits 1 on an error**, `--strict` also on warnings, `--folder`/`--address`/`--since` scope it per SH143, `--baseline`/`--accept` subtract accepted findings per SH142, **`--delivery` adds SH141's route proof** — it appends the group rather than replacing `--checks`, since a caller wanting the stronger proof still wants the lint — and the report's `notes` are printed, because an unreported cut reads as a clean pass), `digest` (SH101 — one page's structure), `snapshot` (SH102 — **a PNG for a human**, plus whether the page looks different; baselines in `shio/snapshots/`, `--accept`/`--fail-on-change`/`--width`. **`--site <name>` photographs every published page instead (SH209)** — the set is the artifact a handoff wants, because "these three pages changed" is a sentence and "this page changed" is a fact the person who changed it already knew. Three properties make the loop worth having: **one browser** for the sweep (it used to launch and close one per picture, so a two-width `--against` launched four), a **per-page timeout** so one page that never settles cannot consume the run, and **an unreachable page is never an unchanged one** — SH141's lesson in a second place. A page the browser could not open is named with its reason, is never scored, and exits 1 on its own, independently of `--fail-on-change`: a visual change is usually intended, and a page nobody could open is not a change at all. A draft is skipped rather than previewed, since photographing the preview would silently answer a different question. `playwright` is an **optional** dynamic import with a teaching error naming the install, so `@viglet/shio` never pulls a browser; with `pixelmatch` + `pngjs` the diff is per-pixel, without them it is byte-level and the output says which. **A change exits 0** — a page looking different is usually the change you just made, and a gate here would be the one people wrap in `|| true`. The manifest still reports `visualSnapshot: false`, correctly: there is no endpoint and no MCP tool, and an agent that read `true` would hunt for a call that does not exist. **A site with no front end is photographed through the instance's own preview (SH265)** — `resolveUrl` used to refuse with "there is no page to photograph", which Block P made false: SH183 renders the page out of content the instance already holds and SH184 stopped the *server* saying the same thing, so the CLI was the same premise left standing one process along. `--preview` forces it even for a site that has a front end, which is the only way to photograph a **draft**. The capture carries the instance's Basic credentials **only for that origin** — the preview is authenticated since SH359, and handing a site's public host an admin password because one command photographs both would be a leak nobody asked for — and the output says the picture is a DRAFT with the banner on it, because the image cannot say so itself), `changes` (SH88 — the feed; `--since last` reads the last content pull's position, `--cursor` prints only the position), `report` (SH104 — **the human handoff**: what changed with a preview link each, plus the warnings as open questions, as Markdown for a PR body; `--since last`/`content`, `--mark`, `--out`, `--title`, `--no-verify`, `--json`), `remember` (SH114 — the instance memory; bare lists it, `<key> "<note>"` writes one, `--forget` drops one, `--site` scopes it), `mcp`, `dev` (`--content` runs the SH90 sync loop instead of the site's dev server), `deploy`, `doctor` (SH397 — **can I work here**, in one call: the instance and *which build*, the credentials, SH295's render-model drift, the sites, and whether a local content tree exists. Three outcomes and not two — `ok`, `FAIL`, and `??` for a fact that could not be established, because an instance that does not answer leaves four checks unanswerable and reporting those as failures would invent four defects out of one, which is SH141's rule for a page it could not fetch and SH209's for one it could not photograph arriving in a third place. Exit 1 on a `FAIL` only: running it with no instance is how somebody checks their own tree. **The build stamp is the point** — `version` reads `dev` on any instance not run from a packaged jar, the same string before and after a rebuild, which is exactly the mode where the running instance can lag the tree being edited and where a change that is not there reads as a change that does not work. SH460's `builtAt` is the comparable fact, and its *absence* is reported rather than left to read as current. Deliberately does **not** run `push --content --check`: that needs the server, a full projection and a plan, inside a command whose promise is one quick answer — it reports that a tree exists and names the expensive check as the next thing to run). `pull`/`push` use console Basic auth (`SHIO_USER`/`SHIO_PASSWORD`); `types` uses a CDA token (`SHIO_CDA_TOKEN`).
**`shio audit <page>` is the browser used as an instrument rather than as a camera (SH391).**
Playwright was already a dependency twice and neither output was for the agent — `shio-react/e2e`
drives the console, `snapshot` hands a *human* a PNG — so the failure class no digest reaches was
still closed by asking somebody to look at a picture, which is the round trip P9 exists to remove.
`audit` renders the page at each `--width` (default `1280,375`) and prints **text**: one line per
finding, each naming the **element**, so the next edit is targeted rather than exploratory. Seven
rules — `overflow`, `contrast`, `zero-size`, `collapsed`, `font-fallback`, `request-failed`,
`console-error` — and each definition is a decision, all of them in `cli/src/audit.mjs`'s header.
The three that matter most: **overflow is horizontal and is reported where it is *introduced*** (the
element past both its own parent's box and the viewport — reporting every ancestor it drags would
name `html` and bury the cause); **a hidden element is not a defect** while an element the CSS calls
visible whose box has no size is, which is why `display:none` and `0×0` are separate tests; and **the
preview banner is excluded** — `snapshot` deliberately keeps it so a baseline and a capture agree,
but a finding is an instruction to fix, and nobody can fix Shio's injected banner from their content.
**And the ninth and tenth rules are the shape, which the other seven could not see (SH526).** Four
instruments looked at a page that had lost its hero, its product grid and its footer and *all four
passed it*: `verify` said **ok**, `/agent/render` counted `links: 0` and said nothing, `audit` found
**one** warning (a font fallback), and `snapshot` had no baseline to compare. One blind spot rather
than four — **every rule measures what is there**, and an almost-empty page has no overflow, perfect
contrast, no collapsed box and nothing that fails to load. The accessibility tree is the cheapest
thing that would have caught it and the browser already builds one: the intact page is a `main` with
three headings, a `nav`, a `contentinfo` and eleven links, and the emptied one is two headings and
nothing else. `readShape` reads landmarks (by tag and by explicit `role`, an explicit one winning,
and a `section`/`form` counting only when it is **named** — that is the platform mapping, without
which every card is a region) plus the headings; `no-landmark` and `no-heading` are warnings, and
**the shape prints whether or not it produced a finding**, because a fact nobody prints is a fact
nobody joins. It is **its own `page.evaluate`** and not a line inside `probe`: a function handed to
`evaluate` is serialized to source and runs with no module scope, so `probe` cannot call it — and
`snapshot --against` wants the shape without the layout measurement. The shape is read once, not
merged across widths, since a landmark is a landmark at every viewport. **`snapshot --against` gained
the same comparison beside its percentage** (`compareShapes`): a pixel diff answers "identical to a
baseline", which cannot tell an emptied page from a re-cropped hero and has no answer at all for a
blueprint-built page with no baseline, while a lost `contentinfo` is a lost footer whatever the
number says. One-directional, like `shio diff`'s counts — a landmark the replica **gained** is a
`nav` the layout composed, not damage.
**The eleventh rule is the one field whose value is a program (SH392).** `Region.JAVASCRIPT` is
shipped to the browser and never evaluated in the JVM — SH248, and the correct resolution of the
Block D non-goal — which left it the **only** part of the render model nothing in this repository
ever executed: `ShRenderNoScriptEngineTest` asserts the opposite property (that the server *cannot*
run it), the composer's test asserts the emission, and SH336's click interception is asserted as a
*string*. A region whose script does not even parse renders, hashes, lints and verifies clean,
because the browser runs none of it and the DOM is exactly what the server sent. `region-script`
compiles every `script[data-shio-region]` on the page and reports one that will not, naming the
region — free attribution, since `ShRegionComposer` already annotates each tag it emits. Four
decisions in it: **`new Function`, not `eval`**, because that parses without running and re-running a
region's initialisation is the exact bug the composer emits each script once to avoid; **an `error`,
not a warning**, which fits `audit.test.mjs`'s standing rule that only *observed facts* gate — the
parser said so, where every `warn` is a threshold this command picked; **judged once, not per
viewport**, a script being the same bytes at every width; and **a runtime throw stays
`console-error`**, since `pageerror` already reports it and one defect in two rules is two findings
that can disagree. The line prints even at zero (`0 region scripts on this page`) — the complaint
was that this field is delivered and never run, so silence would be indistinguishable from health.
**Forwarding it found that SH526's shape line had never reached a reader**: `classify` returned a
`shape`, the command destructured three of its keys and not that one, so neither the text report nor
`--json` ever carried it. Both halves were right in isolation, which is why every unit test passed —
each calls `classify` or `describe` directly, and the defect lived only where they meet. That is the
gap `run`-level assertions now cover.

**Errors are what a machine observed and they exit 1** (a 404, a thrown exception); **overflow,
contrast, a collapsed box and a fallback font are warnings that exit 0**, because each is design
intent often enough — a 0×0 tracking pixel, a signed-off brand colour — that gating on them makes
this the exit code people wrap in `|| true`, which is the trap `--fail-on-change` was kept opt-in to
avoid. `--strict` is there for a project that decided otherwise. Same optional `playwright` import
and the same teaching error as `snapshot`; **no endpoint and no MCP tool** (`tools/list` is capped at
12 with 11 used and is paid every turn, and an endpoint would put Chromium in the JVM, which is a
standing non-goal) — so the manifest reports `pageAudit: false` with `elsewhere.pageAudit = "shio
audit <page>"`, which is SH393's convention and the only way a client learns the capability exists.
**The target flag is `--page`, and `--url` is the instance** — SH365's defect, not inherited.
**The cost is the argument and it is measured, not asserted** (`audit-cost.test.mjs`, printing on
every run): a clean page ≈57 tokens, ~20 findings ≈643 against a full-page PNG's ~1600, marginal
≈29 per finding — most of which is the *selector*, whose length is the audited page's choice. It is
honest about the other end too: a page with 200 findings costs ~5900 tokens unbounded, which is
genuinely more than a photograph, and `--limit` is the answer while the header's count still covers
the whole run. **Two defects that only running the probe against a real browser found**, and both
are now pinned: Chromium logs its own console error for every failed response, so two broken
references produced five error findings for three defects (`NETWORK_ECHO` drops the echo — the two
listeners are on the same page, so it is deduplication, not suppression); and a `console-error`'s
message *is* the finding, so its line had a dangling ` — `. The probe runs **inside** the page and
is therefore one long self-contained function on purpose — a complexity warning on it is a false
positive, and `audit.test.mjs` lints from the other side that it closes over nothing.

**One flag, one meaning: the page is `--page` and `--url` is the instance (SH365).** `snapshot` read
its target as `flags.address ?? flags.url ?? positionals[0]` while `resolveAdmin` two lines up read
`flags.url` as the **instance** — one flag naming the page in one line and the server in the next. It
never bit, because the two readings only meet when a caller passes both and that is the *correct*
usage, which resolves as anybody would expect. What made it worth fixing is **SH265**: `--url` also
decides `previewBase`, the origin a front-endless site is photographed from, so the flag could
silently change which *server* an artifact came from — the one failure `resolveUrl` refuses to risk
when it declines to guess an origin. **A rename with a deprecation, not a break**: `--page` (or
`--address`, or a positional) is the target, and `--url` still names the page when nothing else does,
printing on **stderr** which flag to use instead — stdout stays the artifact's own report.
**And SH365 named one command while a grep found two** — `digest` had the identical expression and
the identical collision, and nothing had said so. So per SH322 the instrument landed first:
`flag-meaning.test.mjs` asserts that **no command reads `flags.url` and resolves an instance**
(the colliding set must be empty) and that every command giving `--url` a private meaning is declared
with a reason (exactly one: `clone`, the only command that never talks to an instance at all). The
shared reader is `pageTarget` in `snapshot.mjs`, and **passing its `warn` callback is how a command
opts into the legacy reading** — that is the mechanism rather than a convenience. Only `snapshot` and
`digest` ever documented `--url` as a page, so only they owe backward compatibility; `audit` calls it
without `warn` and gets the strict read, because accepting `--url` as a page in a command that never
did would *create* the hazard for free — `shio audit --url http://localhost:2710` would quietly audit
the instance root instead of saying no page was named.

**`shio clone <url>` is replication's capture phase, and it writes no content (SH377).** A
replication is capture → convert → prove, and the first phase did not exist, so the work could not
be scoped. `clone` walks **one origin** and writes exactly two things: the inventory at
`shio/.shio/source-inventory.json` and a report — the pages with titles and paths, the assets they
reference, the locales *and how each shows up in a URL*, the forms and their fields, the navigation
as the source spells it, and the third-party scripts. **Writing no content is the promise every
later phase rests on**: the thing a conversion is supposed to let you review would otherwise already
exist. That promise is asserted as a **path prefix and not a file count** — nothing may land outside
`shio/.shio/` — because SH543 added two files there and a count would have made a true invariant
look broken while an untrue one went unstated. Four rules the walk keeps. **One origin** — every
off-host link is recorded and never followed. **A seed that redirects off-host is refused**, naming
the host it actually served, because `example.com → www.example.com` followed silently files every
path under an origin nobody named; a *page* redirecting off-host mid-crawl is just an outbound link.
**A same-origin hop is followed by hand and then *recorded*, on the row of the path that redirected
(SH563)**: the row keeps its own `path`, the body of the hop's target, and `redirectedTo`. `status`
is not the substitute — by the time the row is built the 301 is resolved, so it holds the 200 and a
redirected path is indistinguishable from a served one; without the field `/docs/guia` and
`/docs/guia/` are two pages with the same body and no statement that one is the other's canonical,
so a conversion writes the body twice and rebuilds neither the choice nor the 301 every external
link to the old spelling needs.
**robots.txt bounds it** — an exact agent group beats `*` outright rather than merging with it, the
longest rule wins, `Allow` wins a tie, and `--ignore-robots` is recorded *in the inventory* so a
capture taken without permission cannot later look like one taken with it. **Sequential over a
sorted frontier**, which is what makes `--max-pages 50` cut the same fifty pages every run — the
inventory exists to be diffed against the next crawl, and a set that moves on its own makes every
diff noise. For the same reason there is **no timestamp in the file**; when the crawl ran is in
`git log`. `--render` drives the browser for a page whose markup arrives after hydration, and it
adds **no second extractor** — only a different source of markup for the same scanner, since two
implementations of "what does this page contain" would diverge into a fidelity gap nobody could
locate; the inventory records which one it got, and a page with almost no text and scripts present
is a **note pointing at `--render`** rather than a silent miss. A sitemap named in robots is
reported as *not walked*, and a ceiling marks the inventory PARTIAL — no silent caps, because a
bounded capture read as a complete one is the failure the whole file exists to prevent.
**The tag scanner is deliberately linear, and that is a security property, not a style choice**:
this is the only module in the repository that parses bytes fetched from a stranger's server, so the
obvious precise regex — a repeated `name(=value)?` group over overlapping classes — is exponential
backtracking, and on a hostile page the crawl would **hang** rather than fail. `[^>]*` plus a
lookahead after the tag name removes both the exponential and the quadratic case; the price is one
bounded limitation (a `>` inside a quoted value ends that tag early), and a test feeds it 20k of
exactly the pathological shape with a timeout as the assertion. It is CLI-shaped for SH268's
settled reason and the manifest says so: `siteCapture: false` with
`elsewhere.siteCapture = "shio clone <url>"`.

**A long capture is followed, not watched (SH543).** `clone` writes its inventory once, at the end,
and under `--render` a crawl can hold a 200-page ceiling with the per-page timeout spent on every
one of them — so it was silent for the better part of two hours, and the silence read the same
whether it was working or wedged. It was told apart once by sampling the browser's CPU with
`Get-Process`, which is process forensics standing in for a status line. The channel is a **file and
not stderr**, because the caller who starts a two-hour run backgrounds it and is not holding its
pipe — and `| tail` buffers both channels until EOF anyway, which is how the silence was produced in
the first place. Same mechanism `shio dev --content` already settled: append beside the artefact,
and a lost log never stops the run it describes. Two files, because there are two questions and they
cost differently. `shio/.shio/clone-progress.jsonl` grows one line per page or asset and answers
**what failed**, by grep, keeping the *cost* of each — which the inventory does not, and which is
the finding that ends a doomed run early. `shio/.shio/clone-status.json` is rewritten in place and
answers **where is it** in one Read whose size never changes however long the crawl runs (P3);
nothing in it accumulates. Its `startedAt`/`updatedAt` pair is the point — a count says how far a
run got, only a heartbeat says whether it is still getting anywhere — and it carries `pid` and the
**ceilings in force**, since `13 of 200 at 30 s each` is a decision to make now rather than in
ninety minutes. `--no-progress` suppresses both. Two details are load-bearing and were both found by
reading the first real run's own log: the timestamp field is `at`, so a step's path is `path` (they
collided once, and the clock silently lost), and a message written for a terminal is **stripped of
ANSI and folded to one line**, or Playwright's timeout spans four lines of a format whose premise is
one line per unit and defeats the grep that would have found it.

**`--assets` is the bytes, and it is a second opt-in pass so SH377's promise survives (SH378).**
SH145 made an asset a path in the tree and `push --content` uploads whatever a `File` field names,
so the job is narrow: download each referenced image, stylesheet, font, icon and media file into
`shio/assets/<site>/<the source's own folder chain>/<name>` and fold the source-URL → tree-path map
back into **the same inventory**, because SH379's markup rewrite reads that map and a second copy of
it would rewrite an `<img src>` to a path holding somebody else's bytes. `--site` is **required and
validated before the first request** — an asset path starts with the site name, a crawl cannot know
what the replica will be called, and deriving it from the hostname would put a site called
`www.example.com` in an instance. Four rules, each a consequence of an existing one. **Same-origin
only**, with the hosts left behind *named* — mirroring a CDN's bytes into a customer's media library
is a licensing decision and this tool is not entitled to make it, but somebody has to be able to.
**Deduped by sha-256** (SH210: a path names one thing, so one logo served from three URLs is one
asset and three paths would mean a curator replacing "the logo" replaces a third of it) — and the
plan is ordered **most-referenced first**, which decides *which* of two identical files is canonical:
sorted by path, an incidental `/img/hero-again.png` became the name for a logo referenced forty
times as `/img/hero.png`, because `-` sorts before `.`. A test found that. **A name a path cannot
hold is refused, never trimmed** — and the hazard is not the one you would guess: a URL parser
resolves `%2e%2e` before anyone sees it (measured; the first draft of the comment claimed otherwise
and the SH403 lint refused the claim), while it *deliberately preserves* `%2F` and `%5C`, because
decoding a slash would change the path's structure. So the separator is what a decode-for-readability
re-creates, which is why the order is decode-then-validate and why `capturedAssetPathFor` is
classified in `path-safety.test.mjs` sharing `assetPathFor`'s collision detector — one root, one
answer. Also refused: a Windows device name, a trailing dot, a colon or pipe (legal on POSIX, and the
tree's whole promise is that it moves), leading or trailing whitespace, and a segment past 255 bytes.

**And a fifth rule since SH452: what a *stylesheet* references is not markup.** The pass built its
list from the markup alone, so a webfont, a background image and an icon sprite were invisible to it
— and for a **fidelity** replica, whose whole claim is that it renders identically, the stylesheet is
where most of the remaining bytes live. Measured on a real run: `--assets` wrote exactly one file
(a `<link rel="icon">` logo), the replica rendered in a serif fallback because both webfonts 404'd,
and `propose` said so in a note nobody had to act on ("2 url() reference(s) are not in the asset
map"). So a stylesheet that was **written** has its `url()` calls read (`assetsReferencedBy`) and the
same-origin ones appended to the plan, which the loop walks **by index** — an `@import` chain is
therefore followed by the same pass rather than needing a second one. Resolved against the
**stylesheet** and never against the page (`url(../fonts/x.woff2)` in `/assets/css/site.css` is
`/assets/fonts/x.woff2`), which is the same key `theme.mjs`' `rewriteUrls` computes, or the rewrite
would not find the bytes this pass downloaded. `data:` is already portable and off-origin lands in
the *same* external report, so the hosts a run left behind stay one list. A `url()` carries no
evidence of its kind, so `kindForUrl` reads the extension and answers `image` for anything it does
not recognise — refusing what it cannot classify would drop the sprite sheet nobody gave an
extension. `--max-assets` still bounds the whole thing, discoveries included. The `URL_CALL` regex is
deliberately a **second copy** of `theme.mjs`': the two run at different times and importing across
them would make a capture depend on the theme builder — what would be a real defect is the two
disagreeing, which is what the tests hold.

**SH455 closed the half of that a critical-CSS page falls through.** SH452 reads a stylesheet that was
*written*, which is right for a `<link rel="stylesheet">` and reaches nothing on a page whose CSS is an
inline `<style>` block — there is no file to download and therefore none to walk. `theme.mjs` sees
those blocks (`styleBlocks`, SH427) and the scanner did not, so the fonts were named in a note whose
remedy was *re-run this capture* and fetched by nobody: measured on `openviglet.github.io`, `wrote 1
asset` on the first run and on a second after `propose` had named both fonts, replica in a serif
fallback. **A remedy that cannot work is the same defect P6 exists to remove**, and worse than the
silence SH452 replaced, because the reader cannot tell a no-op from a failure. So `scanPage` now walks
`styleBlocks(html)` with the same `URL_CALL` — **imported** from `clone-assets.mjs`, not copied a
third time; that import runs capture→capture, which is the direction the paragraph above forbids only
in reverse — and pushes each resolved reference onto `doc.assets`, where `buildInventory` and the
downloader already handle it. `kindForUrl` rather than `assetKind`: a `url()` has no tag and no `rel`,
so the extension is the only evidence, and the unrecognised default has to be `image` (downloads) and
not `assetKind`'s `other` (does not).

**The note that excused the leftovers was wrong in the common case, and that was the other half.**
It read: unresolved references are "left as they are — the theme still fetches them from the source's
origin". True of an **absolute** URL. These were **root-relative**, so they resolve against the
*replica's* origin where nothing serves them — the note told the reader the fonts still worked at the
moment they stopped working, which is SH443's defect one surface along: a message asserting an outcome
nobody measured. `buildTheme` now splits the list and only the absolute half is described as still
working; the root-relative half is reported as 404-ing, with the `clone` that fixes it.
**A soft 404 is caught here or nowhere**: a response of `text/html` where an image was expected would
land in the media library under a `.png` name that every later check reads as a present asset.
`--max-assets` and `--max-asset-bytes` bound it, the latter checked against `content-length` *and*
against what actually arrived, since a server that under-declares would otherwise walk straight past
the ceiling whose whole job is not filling a disk.

**The authorable mode presents with a Shio theme, and stops promising the source's appearance
(SH507).** Measured on an authorable replica of `www.viglet.org`: the Universal Editor showed a wall
of unstyled text over a page of **1,066,436 bytes, 99% of it the inlined captured theme** — ~2,894
rules meeting 19 class attributes on 8 `shio-*` names, with no source class surviving. Two cheaper
repairs were measured and **rejected**. Emitting the source's classes per field recovers 7 of the
~22 that style one heading; the other 15 sit on wrapper `div`s the generated layout does not have,
so it buys typography and no layout. Translating them onto a `shio-*` contract is not a repair but a
design system, one per replicated site. So the *promise* changes rather than the implementation
being patched: an authorable conversion yields **structured, editable content presented by a Shio
theme** — deliberate, not a ruin of somebody else's. The source's appearance is what **fidelity** is
for, and fidelity reaches 0.00%. The reviewer's choice becomes "their look, or curate this" — two
good answers instead of one mode half-doing both. Four things follow: a structural layout contract
for the vocabulary (SH508), the theme written against it (SH509), the captured theme no longer
inlined on an authorable page (SH510), and a proposal that reports the replaced presentation as the
mode's contract rather than as a loss (SH511).

**`shio propose` is the convert phase, and what it ships is the *proposal* (SH379).** A captured
block has two honest futures — verbatim markup in a `PageLayout` (renders identically, **nothing is
authorable**: a curator editing "the hero headline" is editing an HTML file) or an SH95 section
(authorable: the fields become editable, and the presentation becomes Shio's — SH507) — so the
deliverable is neither, it is the artefact between them. Per candidate: the pages it recurs on, the section type it looks like **and why**, the fields it
would carry, and **what accepting it would lose**. `--accept <ids…>` writes one entry into
`shio/conversion-plan.json` and **applies nothing** — no post, no post-type, no layout; an unaccepted
block stays in fidelity mode, which is a decision rather than an omission (P7, P8). Six decisions,
each of which a test pins:
- **A candidate is a *signature*, not a block**, and that is what makes the report affordable rather
  than a nicety: the same hero on sixty pages is **one** decision. Measured, and it prints on every
  run (`propose-cost.test.mjs`): 4 pages of four blocks ≈294 tokens, **60 pages ≈295** — the growth is
  a path per page, not a candidate per page, where the per-block implementation an author naturally
  writes would be 240 candidates. ≈74 tokens per candidate, loss report included.
- **The loss is the complement of what the fields consumed**, never a list of known-lossy cases —
  that is the only definition that cannot *under*-report, and under-reporting is the one failure this
  artefact must not have. Three categories because they are three conversations: **elements** the
  section cannot hold (an `<iframe>`, a `<video>`), **behaviour** (an `onclick`, a `data-*`, an `id` an
  anchor targets — the page still looks right and stops working), and **styling** as a count, because
  the section renders with the vocabulary's own markup so *all* of it goes.
- **And two of those three are not the same kind of thing, which is what the report got wrong
  (SH511).** `loses: 311 class name(s) — all styling` framed the appearance as **damage a reviewer
  weighs**, and 311 dropped classes always sounds worse than none — so the line read as an argument
  for fidelity on *every* candidate, which is how a session accepted candidates and then went looking
  for the styling as though it should have survived. Under SH507 it is the mode's contract. So the one
  list is **split in two, not deleted**: `discards:` is the elements and the behaviour — an icon set
  is not presentation, and a reviewer genuinely should weigh `114×<circle> 81×<path> 48×<svg>` — and
  `replaces: 311 class name(s) of the source's styling` is the count, which stays because a reviewer
  is entitled to know how much is being set aside. **The label carries the meaning and the header
  carries the sentence**, once: *fidelity keeps the source's appearance and offers no fields;
  authorable gives these fields, presented by the site's own theme.* That placement is P2 and P3
  rather than style, and the cost test is what settled it — `propose-cost.test.mjs` reads ≈99 tokens
  per candidate against a baseline of ≈74, and **the ceiling moved to 105 with it, because a budget
  sitting 60% above the measurement is not a budget** (SH580 moved both again — see below; the test
  is the authority on the current number). Two intermediate drafts were measured and
  rejected on the way: repeating the sentence per candidate cost ≈120 and landed exactly on the old
  ceiling, and printing an empty half as `nothing` cost ≈109 to say twice that there was nothing to
  say — so an empty half now prints no line, and a candidate that loses nothing at all still says so
  on one. The **proposal data is unchanged** — `loss` always held `elements`, `behaviour` and
  `classes` as separate keys, and only the presentation conflated them — so no version moves and no
  plan file is invalidated. The two other documents that carried the old promise change with it:
  `propose.mjs`' header no longer says "the pixels have moved", and the `shio-replication-test` skill
  no longer calls an authorable page's appearance a *known limit* — it is the contract, and what is
  now a defect there is finding the captured theme on the page.
- **A candidate's id is a hash of its structure.** A positional id renumbers the moment the source
  adds a block, and every entry in the plan then names a candidate nobody reviewed — silently, since
  a plan file cannot tell. An id from an older run is *gone*, and the refusal says why.
- **A block the vocabulary has no type for is classified as `null`, and that is a real answer**: the
  proposal names the post-type it needs instead (fields derived from what it contains, named from its
  own strongest signal). Accepting one *as a section* is refused — forcing a block into the nearest
  wrong shape is how a conversion loses content nobody notices.
- **It reads only the tree** — no instance, no network, no browser — which is why `clone --markup` had
  to exist: the captured HTML lands in `shio/.shio/source-markup/`, so `--render`'s choice is made
  once at capture and every proposal is reproducible from the tree. A command that re-fetched would
  change under the reviewer depending on what the source served this morning, and for an artefact
  whose whole job is to be argued with that is disqualifying. `--markup` costs **no extra request**
  (the bytes were already in hand) and stays opt-in anyway, because keeping a stranger's markup in the
  tree is a decision; the file name carries an unconditional digest suffix, so it never depends on
  which *other* pages were captured, and a collision is reported rather than resolved by renaming.
- **`--accept` stays the boolean it is in `shio verify`, with the ids as positionals.** One flag whose
  arity differs per command is SH365's defect in miniature: a caller who learned `verify --accept`
  takes nothing would write `propose --accept --reason "x" b1a2` and, with a value-taking flag,
  silently accept a candidate called `--reason`.
Four things the implementation got wrong first and a test caught, all of the same shape — **a rule
that is right in one direction and silently wrong in the other**: descending through single-child
wrappers is necessary for `<div class=container><div class=row>` and **catastrophic for a page whose
content root holds one `<section>`** (the candidate list still looks plausible, it is just a list of
headings), so only *generic* containers are walked past; a **hero is positional and structurally
identical to a CTA**, so it is tested first and only when the caller says the block opened the page,
or every homepage's hero files as a call to action; **three consecutive `<p>`s are three children
with one signature**, so a repeated unit must be a *container* or an About page becomes a feature grid
whose features are its sentences; and the **block's own class list** is the styling a reviewer is
agreeing to lose (`class="hero dark"` on the wrapper, nothing on the children) — every draft walked
only the descendants. This is also the one place a **real parser** earns its keep: `scanPage` is a
scanner and structure is the question here, so the tree is built over `tokenize`'s stream rather than
a second tokenizer — one answer to "what is a tag on this page", and SH377's linear-scan property
over a stranger's bytes is inherited rather than re-argued. It is CLI-shaped and the manifest says so
under its own key: `conversionProposal: false` with
`elsewhere.conversionProposal = "shio propose"` — folded into `siteCapture` it would be invisible,
since `--assets` is a phase of the same act while a conversion is a different act on a different
artefact.

**And a fifth of that shape, which cost the command its whole answer on the simplest page there is
(SH425).** Measured against `openviglet.github.io`: one page, a hero and a two-card grid, and
`shio propose` reported **0 candidate block(s)** — while the same command found 36 on that site's
model-catalog app. The proposal was emptiest exactly where it should be richest, and two mechanisms
compounded. The descent walked `main > div.wrap > div.cards` and stopped *at* `div.cards`, returning
its children — but `div.cards` **is** the block, a container whose repeated units are the cards; the
docstring's rule was that two children mean nothing was skipped, and the case it missed is a node
whose two children *are* the units. So a node is tested for repeated units **before** being descended
through, and the test is deliberately shallow (a subtree-wide one stops at the outermost wrapper and
yields one candidate whose signature is the whole page) and restricted to **generic** units, or a
theme's `<div class=row>` holding two identically-built `<section>`s would collapse two blocks into
one. Second, `INLINE` held `a`, and the whole-card-is-a-link pattern wraps `<h3>` and `<p>` in
`<a class="card">` — so even without the first defect both children filtered to nothing. "Inline by
HTML default" and "cannot be a block" are not the same claim: `isInline` now asks the **node**, so a
link around a heading is a block and a link inside a sentence is still prose. The same function
answers what a conversion *loses*, where a block-wrapping anchor is structure a reviewer should see
dropped. **The regression test is that page**, because the failure has no symptom other than a number
and a candidate list that looks plausible is what let it ship.

**A page's own `<header>` is content, and `contentRoot` was skipping it (SH426).** Narrowing to
`<main>` is right for the **site** chrome — a header repeated on every page would propose sixty copies
of one nav — and that reason does not describe a page's introductory header. On
`openviglet.github.io` the chrome is `div.topbar` and the hero is `header.hero-wrap` (an eyebrow, an
`<h1>`, a lead and two CTA links) sitting just before `<main>`, so the single most convertible block on
the page was discarded — while `classify` carries a *positional* hero test, tried first and only when
the caller says the block opened the page, which `contentRoot` guaranteed could never fire. The two
decisions were made apart, and that is the whole defect. `blocksOf` now prepends the **closest**
`<header>` preceding `<main>`, and deliberately does not try to tell a hero from chrome structurally:
from a single page there is no evidence, and the evidence that exists is **repetition**, which the
capture already computes. A site whose chrome *is* `<header>` therefore yields one candidate (signatures
are deduped — one decision on N pages, not N decisions), and the proposal now says so in a note when a
candidate appears on **all** the pages read, with three as the floor: on two pages a hero is on both as
well, and a hint that fires on every small capture is one a reviewer learns to ignore.

**`shio diff` — both sides were held and nothing compared them (SH522, `--against capture` and `proposal`).**
Shio holds both sides of every comparison it does not make: a capture declares the **source** (six
pages, eleven links, one image, this much text), a proposal declares the **intent**, a document
declares the **write**, and the artefact each produces is compared with none of them. So *is this
what it was supposed to be?* had no answer in the product and was assembled by hand every round —
wrongly twice, once reporting **zero broken references** on a page with a missing logo and once
**106 editable fields** on a page that had lost most of its content. Both times the declaration was
on disk, the artefact was on the wire, and the join was forty lines of Python written from memory.
What makes it cheap is that **every artefact carries the identity of what declared it** — the
inventory names the capture, the proposal names the inventory, the document names the proposal — so
nothing is guessed or re-fetched; the chain was already written down and nobody had walked it.
**`--against proposal` is the second link, and it reads documents rather than the wire.** The
capture comparison asks what a *reader* gets; this asks whether the write did what the plan said —
the accepted blocks became sections carrying the fields they promised, and each page composes the
ones it was supposed to. Both sides are declarations, so it is a set difference and not a scan, and
it needs no capture at all (the branch is taken before the inventory is read). Keeping the two
apart is the point: a page that composes the right sections and serves them badly is the capture's
finding, and merging them would leave a reader unable to tell **"the conversion did not do what it
promised"** from **"it did, and the layout is wrong"**. Four outcomes per row — `ok`, `absent`
(declared, never made), `incomplete` (made, missing a promised field — the shape the 106-field
hand-count got wrong), and `undeclared` (the plan accepts a block the proposal never had, which is
two declarations disagreeing before the artefact is involved). A field the artefact **gained** is
reported and never counted against the run, for the same reason a gained link is not: the
post-type declares fields the proposal never promised.
**The other two declarations are settled rather than pending, and that is SH522's closing finding.**
The **document** is `shio apply --check` under another name — it plans a desired-state document
against the instance and exits non-zero if anything would change, which is the whole question. A
second reader for it would be a second contract over one question, which P10 refuses, so it is
retired rather than left as an unbuilt quarter somebody re-proposes. An **earlier moment** is
blocked on a *read*, not on this verb: `GET /api/v2/agent/read` is DRAFT-preferred and takes no
`state`, so the live row's fields are unreachable and a fact-level draft-against-live comparison
cannot be assembled at all — SH545's `published` answers *whether*, not *what*. Filed as **SH619**,
which is a read before it is a diff. The refusal prints both of these instead of implying the flag
is merely unfinished.
`diff` walks the capture's pages, asks the instance for each at `/preview/<site><path>`, and reads
the served bytes with **`scanPage`, the same function that wrote the declared counts** — SH245's
rule, and the reason the comparison is arithmetic rather than a second opinion. Not pixels
(`snapshot` is that and says so): internal links, external links, assets, forms, and prose against a
**proportional** floor, since whitespace does not survive a conversion and an exact text comparison
would report every page as lost. **A fact the replica *gained* is never a failure** — a Shio layout
adds links, and a gate that called that damage would fire on every working conversion — and a path
the replica does not serve is *one absence* rather than four lost facts, because
`source-page-missing` is already that finding. Exits 1 on a loss. `--against proposal|document|<an
earlier moment>` are the three §IX.34 also names and **none is built**: the refusal says so rather
than answering approximately, and the capture is the one both hand-errors were about.

**A page the target cannot represent is not a page the replica lost (SH557).** vilt-group.com
paginates case studies as `/en/case-study?p=2` … `?p=24` in five locales. The crawl followed them,
`convert` wrote 204 objects, and the replica serves none — a post is addressed by path and
`post:<site>/<friendly-url>` has no room for a `?`. `diff` was the only command that said anything:
*107 page(s) compared, 91 the replica does not serve* — reported as a **loss**, which is the wrong
shape. A loss is something that should have arrived and did not; this is a **trait of the source**,
and it was decidable at capture from a regex over paths the inventory already held. The cost of
learning it last is that everything before it is wasted and looks fine: `clone` reported 198 pages
and six notes, none of them this, `propose` counted candidates over pages that would never exist,
and `convert` published them. Both halves shipped because they fail differently: `addressablePath`
(`clone.mjs`, the SH79 grammar as one regex) drives a **capture note** that says how much of the
source can be replicated *before* a proposal is built, and `compareCapture` splits `unaddressable`
out of `missing` so the gate reads only real damage — `diff` prints them apart, in their own words,
and **does not request them at all** (91 round trips to be told 404 ninety-one times). The note is
the advice; the split is what the gate reads, which is why the split matters more.

**A bound the run chose is not damage the replica caused (SH584).** SH557's shape, second instance,
which is what makes it a rule rather than a fix: `shio clone --depth 2` over `openviglet.github.io`
recorded **`unfollowed: 22`** — the model catalogue's vendor pages, one level past the ceiling, each
with its path and reference count, written down before anything was converted. Twelve minutes and
three commands later `shio verify` exited 1 with **22 `page-link-broken` errors**, one per
unfollowed path, on a replica whose only other error was SH467's. Twenty-three errors read as a
broken conversion; it took opening `source-inventory.json` and matching the lists one to one to
learn that twenty-two were a flag somebody chose. The instance cannot know this — it has never seen
the capture — so the phase that says it is the one already holding both halves.
`buildConversion` joins `inventory.unfollowed` against the pages it is writing and returns
`{paths, refs, first}`; `describeConversion` prints it naming **the rule `verify` will use**, so the
two runs join by the reader's own grep. **A join and not a copy of `unfollowed`**: a target this run
does write — `--home` redirects one, a deeper re-crawl covers others — is subtracted, because the
number printed has to be the number the gate will produce or it is worse than silence. Phrased as a
prediction rather than a finding, and with both answers named (`--depth`, or
`shio verify --accept`), because nothing is wrong yet and either is legitimate.

**A replica whose links go home (SH586).** `openviglet.github.io/model-catalog/models/azure/` writes
three of its own links as **absolute URLs on its own origin**. A fidelity replica serves the markup
verbatim, so clicking *model catalog* on the copy leaves for the original. Across the whole replica:
twenty-two pages, exactly three each, identical on every one — `internal links: 9 → 6 (-3)` beside
`external links: 3 → 6 (+3)`. **The total is conserved, which is what made it invisible.** Nothing
is missing, so no shortfall is damage; a gain is never a failure; `shio verify` exits **0**. It
survived eleven review rounds, because *no href carries the source's base* is check 2 of a checklist
a **person** runs and not a rule any gate holds. Two halves, and the second is why the first went
unfound.
**The rewrite is in the capture, not the render, and that is forced.** SH466 rebases a root-relative
href at render and deliberately leaves an absolute one alone — right for a genuinely off-site link,
wrong for the one case where the absolute URL *is* the source's own origin. The render cannot tell
them apart: nothing on the instance records what the source was (`ShSite.url` is the replica's own
front-end URL, which is what SH-delivery probing uses). The capture knows it exactly — it is the
crawl's own `origin` — so `planMarkup` takes it and `rebaseOwnOrigin` writes the source's link the
short way. **Not an edit that points the markup at the replica**: the markup still carries the
source's own paths, which SH437's asset pass and `snapshot --against` both depend on. It is SH512's
rule one shape along — record what the source meant, and let SH466 rebase it. Both spellings, since
`https://host/x` and `//host/x` are one claim, and a bare origin becomes `/` rather than the empty
href a naive strip leaves. A URL on the source's own origin *is* the source's own path by
definition, so there is no heuristic here and nothing to tune.
**And `diff` names the flip (`wentHome`).** Equal and opposite, deliberately narrow: that is the
signature of a link that *moved*, where "lost 3, gained 1" is two true facts and calling it a flip
would name a coincidence. Printed on the headline beside `byDesign` for the same reason — a reader
who stops at `nothing lost` should not be told only the true half — and per page, with both rows
still printed, because a fact removed from the report is a fact nobody can find. It also breaks the
`as declared` shortcut, which is the line the whole defect used to appear as.

**A lint that scolded the generator's own output (SH593).** Every replica this work produced ended
with the same two lines against `post:<site>/theme/layout` — `head-no-canonical` and
`head-no-description`. Both **correct**: SH337 put `post.canonical` on the model precisely so a
template can emit it, and the generated layout did not. But that layout is written by `shio convert`,
so the finding was Shio telling itself off, and it was **unclearable by construction** — the next
conversion regenerates the same file and reports the same two. The cost was never the two lines: a
warning list is read for as long as it is actionable, and these headed every list for twelve rounds,
including the round whose whole report was `0 error(s), 2 warning(s)` — the first fully green
`verify` on a real replica, whose entire remaining output was noise nobody could act on. A reader
who learns the top of the list is always noise stops reading the list, which is where SH421's
warning about a legitimately script-free layout also lives. So `pageLayout` emits
`<meta name="description" content="{{post.summary}}">` and
`<link rel="canonical" href="{{post.canonical}}">`. **`{{post.summary}}` rather than
`{{description}}`**, though the `Page` this chain writes against declares a `description` field with
`isSummary: true` and the words *"used as the page summary and the meta description"* in its own
definition: `post.summary` is a key of the render frame, present whatever the type declares, so the
tag is empty on a hand-built `Page` instead of the render failing — and on the standard type it *is*
that field. **The other half is what makes the tag carry something:** `composePages` now writes the
`description` the capture already scanned off the source's own head and dropped, so the meta tag is
the source's, not an empty string. Emitting a tag that can only ever be blank would be
lint-satisfying theatre, which one level up is the same defect as the warning. **The assertion reads
the generator's bytes**, not a copy: the rule lives in the JVM and the layout is written in
JavaScript, so `HEAD_CONVENTIONS_HTML` is its own no-interpolation export (SH464's seam) and
`ShHeadConventionsTest` runs the real lint over it — and checks both halves are interpolated, since a
literal description satisfies the absence check while being the sixty-pages-one-description defect.

**A constant is not a coincidence (SH590).** The paragraph above says *twenty-two pages, exactly
three each, identical on every one* — and that sentence is the finding. It was on screen in round
eight, read as two ordinary shortfalls, and again in round twelve before anybody fetched a page and
looked at the hrefs. The same shape produced SH578: `assets +30` on every page of a fidelity
replica, uniform to the unit, which was the inlined theme's own `url()` counted on one side only.
**Both times the give-away was not the number but its invariance** — a real page-by-page loss varies
with the page, so a delta that does not vary is a property of the pipeline rather than of any page.
So `diff` groups every non-zero delta by `(fact, value)` and reports a group of
`INVARIANT_FLOOR` = **3** pages or more once, as a candidate cause, with the pages folded behind the
count and three named so a reader can go and look at one. Three because two is one page agreeing
with another; the floor is deliberately low, since a candidate cause that turns out to be a
coincidence costs one line and the alternative cost four rounds. It prints **above** the per-page
table — the fold is the cause, the table is the evidence — and the per-page rows are unchanged,
because the cause is a candidate rather than a verdict. **A by-design group is labelled, not
filtered:** an authorable replica drops the source's chrome on every page, so its shortfall is
invariant by construction, which is an argument for saying it *once* rather than for dropping it.
It is arithmetic over output the command already computes — no new measurement, no browser, no
instance — and it rides on `comparison.invariant`, so a `--json` caller gets the cause too. Twelve
rounds of this work found defects three ways: two instruments disagreeing (SH589's subject), a
declaration nobody read (SH560's group K), and a number that was uniform. Only the third is one pass
over a single command's own output.

**And one function was not enough (SH554).** "Both sides come out of `scanPage`" was true and
insufficient: `scanPage`'s `links` is an array of raw hrefs, an inventory row's is
`{internal, external}`, and the split between them lived inline in `buildInventory` — so it ran on
the declared side and nowhere else. `FACTS` papered over the gap with
`page.links?.internal ?? page.links?.length`, which does not crash on the wrong shape; it *scores*
it, reading the total as internal and zero as external. Measured on a fidelity replica of
vilt-group.com: nine absolute external links served, nine in the source, `external links: 9 → 0`
reported on that page and on every other, with the internal count reading 67 against a declared 44
— 67 being exactly every `href` on the page. Every replica reported a loss it had not suffered and
hid a real internal-link loss behind an apparent gain, and because SH548 had just put this command
in the CI gate, the conformance suite was red at HEAD on its own fixture. **An instrument that cries
wolf in the gate is worse than one outside it**, because the next red run is the one nobody reads.
The split is now `classifyLinks`/`countLinks` in `clone.mjs`, called by `buildInventory` *and* by
`shio diff` on the served bytes — against the **serving** origin, since internal on a replica means
the instance that serves it. The fallback is gone and `comparePage` refuses an unclassified side
naming SH554, because the failure mode was never a crash but four believable numbers. One caveat
the fixture makes explicit: a replica that kept the source's *absolute same-host* spelling really is
serving a link that leaves the site, so counting that as an internal link lost is the correct
reading and not a regression of this fix.

**One standard, three modes — `--mode` (SH577).** SH554 fixed one half of the count and the gate
stayed red for two more rounds, because the remainder was not a counting defect: those facts really
were gone, and **an authorable page is not supposed to have them.** Composing a page out of the SH95
vocabulary drops the source's nav, its favicon link and its stylesheet tag by design, the captured
theme is deliberately not on it (SH510), and only what a reviewer *accepted* is composed at all
(SH520). The chain runs `propose --accept all` and then asserted `lost === 0`, so the gate was
asking the fidelity question of an authorable replica — the shape SH554's own paragraph warns
about, one round later and in the other direction.

The standard does not change: **an artefact is judged against what its mode claims.** What changes
is the claim. `MODES` in `diff.mjs` declares three — `fidelity` (nothing accepted on this page;
every captured fact is a claim), `mixed` and `authorable` — and each fact in `FACTS` carries
`verbatimOnly` with the reason beside it. What **every** mode claims is *the content*: the
proportional text floor, and that the route serves the page at all. So an authorable page that
dropped its body copy still exits 1, and one that dropped the source's nav does not.

Three things about the shape, each of which is the defect it avoids:

- **The mode is read, never inferred.** It comes from `shio/conversion-plan.json` (which blocks a
  reviewer accepted) joined with the proposal's composition (which blocks each page is made of) —
  the same join `composePages` does in `convert`. Deciding it from the served bytes would be the
  second opinion SH522's design exists to refuse.
- **Per page, because a page is not one mode (SH520).** Accepting one block keeps the rest of the
  page verbatim, so a run is routinely a mix; a whole-run mode would hold a fidelity page to the
  authorable standard, which is this same defect mirrored.
- **A by-design shortfall is reclassified, never removed.** It is counted (`byDesign`), printed with
  its reason (`internal links: 16 → 0 by design — …`) and named on the headline. That line is how
  SH575's missing navigation was found, and a fact dropped from the report is a fact nobody can
  find. `--mode fidelity` forces the strict reading; `headless` is refused by name, since those
  pages are served from an origin this command never asks.

**The one fact where a loss can hide inside a gain — `INVENTORY_VERSION` is 4 (SH578).** SH554 made
both sides come out of one function; the **asset** fact still asked a different *question* at each
end. The declared side is a source page's own references. The produced side is a replica page **plus
the theme inlined into it**, whose every `url()` `scanPage` correctly finds (SH455) and which was
then counted as if the page had referenced it. Measured on a fidelity replica of vilt-group.com:
every page reported exactly **+30** — 32 references of its own and 60 `url()` in its `<style>`,
against a captured theme holding 59.

**A gain is never a failure, and that is what made this the fact where a real loss disappears.** A
page that dropped twenty of its own images and picked up thirty theme `url()` reports `+10`, and the
only instrument in the chain that holds an expectation says the page is fine. So the repair is a
**boundary, not a formula**: `scanPage` tags each reference with `from` (`markup` / `style` /
`script`), `countAssets` — beside `countLinks`, same shape and same argument — splits them into
`{own, style}`, and a page row records that pair instead of one total.

`diff` then compares **`own` per page** and reports the theme **once at the site level**
(`comparison.theme`, printed as `theme: 0 → 59 url() inside a stylesheet, across 13 page(s)`, never
scored). The site-level number is the **max**, not a sum — one artefact multiplied by the page count
is a number no page has — and `uniform: false` says when the pages disagree, which a single theme
cannot explain. `comparePage` **refuses** an unsplit `assets` naming SH578, the same way it refuses
an unclassified `links`: a version-3 row read as `own` reproduces the +30 exactly, and the failure
mode here has never been a crash. `READS_VERSION` in `verify-replication.mjs` moves to 4 with it
even though that group reads neither half — a reader left behind answers "this capture is newer than
I read" on every capture `clone` now writes.

**And the same fact on the other axis — `INVENTORY_VERSION` is 5 (SH581).** SH578 cut a page's own
references from its stylesheet's and was right; a second cut was still uncut, one axis along. The
declared side is the **whole captured document** and the produced side is the **body span a Page
stores** under a layout that owns the head. Measured on `/partner/`, the fidelity page of an otherwise
authorable conversion: the capture declares `own: 33`, the replica serves 5, `diff` calls it **-28**
and exits 1 — and the captured markup is 155,881 characters against a stored body of 60,322. What
actually went missing is nine `<link>` elements in the source's head, of which the stylesheet is
inlined by the layout, leaving favicons and a manifest.

**Wrong in both directions at once**, which is what earns it a number rather than an exemption: it
overstates a loss of about four as 28, and it would hide a real body-side loss behind a head-side
count that happened to cover it. `diff` had already said as much in its own footer — *the pages
disagree about this number, which a single theme cannot explain* — an instrument reporting an
inconsistency in its own reading. So `scanPage` tags a markup reference with `where: "head"` while the
scan is inside `<head>` (closed by `</head>` **or** by `<body>`, because real markup omits the closer,
and false to begin with so a fragment counts as body — the strict reading, which keeps a reference
scored rather than excused). `countAssets` returns `{own, head, style}`, the fact `diff` scores is
**`own - head`** and is named `body assets`, and the head is reported once at the site level on
exactly SH578's terms — `head: 9 → 1 reference(s) in the document head`, max not sum, `uniform` when
the pages disagree. `assertComparable` gets its **own** refusal naming SH581 rather than folding into
SH578's: a version-4 row passes the `own` check, `own - undefined` is `NaN`, and every comparison
against `NaN` is false — so a page that lost every image would report nothing at all, which is the
worst of the three readings this fact has had. `READS_VERSION` moves to 5 with it, tracked and not
acted on, for the reason 4 was.

**Baking content is right, baking machinery is not (SH517).** The source has
`<div id="particles"></div>` — empty, filled at runtime by a particles library — and the replica had
`<div id="particles" style="margin-left: -740px; margin-top: -385px;"><canvas width="1480"
height="770">`: `--render` baked the canvas the script had just created, sized for the capture's
viewport, with nothing painting on it and the library itself not among the captured scripts. **The
value of `--render` and its cost are the same mechanism** — baking a script's DOM is right when that
DOM is *content* (it is what turned a blank SPA capture into six readable pages) and wrong when it is
*machinery*, since a canvas is meaningless without the loop that draws on it and baking one also
prevents the library from creating its own. The same freeze was quieter elsewhere: three
`uk-scrollspy-inview`, two empty `style=""` and **fifty-nine** `uk-active`, so the replica opened
with its animations finished and its first slide chosen. `settleRendered` gives three things back,
and each is **evidence rather than a guess**: a `<canvas>` *the response body did not have* (free —
`page.goto` returns the untransformed bytes the navigation already transferred, so an authored
`<canvas>` with its fallback children is kept and only the surplus goes); an **empty** `style=""`,
which no author writes and a script that set and cleared a property leaves (a style attribute with a
declaration in it stays — there is no way to tell one a script wrote from one an author did, and
SH515 has just made the references inside it reachable); and a framework's own state class
(`STATE_CLASSES`, the arguable half, a list because there is no structural way to tell `uk-active`
from a class somebody wrote — every entry names a framework's documented convention and a wrong one
costs a class attribute, not content). **The counts go in a note**, because a capture that silently
gave a canvas back is the same undeclared cut as a truncated crawl.

**And an overlay is chrome that is neither header nor footer (SH505).** Three candidates on three
different pages were proposed with the same `intro` — **"We respect your privacy"**, the cookie
consent banner, which sits in `<div aria-live="polite" class="fixed inset-x-0 bottom-0 z-50 …">` at
the bottom of every page. Its first paragraph became a `SectionFaq`'s intro on `/` and a
`SectionFeatureGrid`'s on `/dumont/`, `/turing/` and `/shio/`, so a curator opening the FAQ to edit
"the intro" would have been editing a consent notice — and the same string was content in three
places at once, *which is also the tell*. `contentRoot`'s reasoning transfers exactly (an overlay
appears on every page, belongs to none, and is transient); it simply is neither header nor footer, so
the existing exclusion could not reach it. This is the authorable path's version of SH441: there a
chrome **asset** belonged to no block and stranded, here an overlay's **text** belonged to no block
and was adopted by three. `isOverlay` is four signals, any one of which is enough — out-of-flow
positioning as a class (`fixed`, Tailwind's, and the hand-written spellings) or as an inline
declaration, `aria-live`, `role=dialog|alertdialog`, `aria-modal` — and `--render` is what puts them
in the markup at all. **`sticky` is deliberately absent**: it is in flow and scrolls with the page
until it pins, so a sticky sub-nav is content in a way a banner is not. The predicate excludes what
this pass can recognise; a **note** reports the shape whatever produced it — a field value carried by
candidates that *share no page*, which is the `everywhere` note one level down, at the unit a curator
would actually edit.

**A disclosure list converted without its labels (SH551).** `SectionFaq` is proposed on the evidence
*two or more `<details>`, which is a disclosure list* — and a `<details>` is exactly two parts. The
extraction took the body and dropped the `<summary>`: `question` read `heading && text(heading)`, and a
`<details>` has no heading. viglet.org's home page yielded **seven rows, each with an `answer` and no
`question`**, so the section rendered seven answers to nothing. `question` now falls back to the
summary — heading first, because a hand-written FAQ uses one and a `<details>` may carry both.

**Nothing reported it, and the reason is the template being careful.** `sh-field="questions.N.question"`
sits inside `{{#if question}}`, which is right — an empty field must not draw an empty heading — and the
effect is that a field that is *never* filled is indistinguishable from one a curator has not filled
yet. The editor count agreed: ten annotated props on that page, seven of them `questions.N.answer`, and
the absence of seven questions read as somebody's to-do. **So the check is the part worth keeping**, and
it is the first half of a pair whose other half (SH524, which asks which declared field no template
renders) has not shipped: this one asks about a field the template *does* render and the extraction
never supplies, which no instrument asked about at all.

Two things make it honest rather than noisy, and both were found by running it:

- **Empty in every instance, not in some.** A FAQ whose third entry has no answer is content — somebody
  wrote it that way. A FAQ where *no* entry has a question is the extractor, because a source that
  repeated one shape seven times did not omit the same part seven times by accident.
- **And only where the markup offered something.** The first version reported a feature grid's `icon` on
  a site whose cards hold no `<img>` — true, useless, and a note that fires on every healthy proposal,
  which is the noise SH505's own reasoning calls chrome. So `unitFields` declares an **`offers`** map
  beside the `pick` rules that read the same nodes, and the predicate is the pair. That is also what
  gives the check teeth as a regression guard: SH551's defect *was* a `question` rule that looked at
  headings and not summaries, so dropping the summary from either map makes this fire. `role` offers
  nothing deliberately — no element in ordinary markup means "this person's job title", and a guess in a
  field a curator reads as fact is worse than a blank — so it is never reported, and that comment is the
  honest statement about it. **`offered` is working state and is dropped before serialization**, beside
  `index`: one note reads it, and a list per candidate would be bytes every reader of the artefact pays
  for (`propose-cost.test.mjs` measures exactly that, and caught the first version at ≈120 tokens per
  candidate against a ceiling of 105).

**And a note is not a reader, which is the seventh time (SH552).** SH505's two halves both ran: the
overlay was excluded, and the note named the string exactly — `intro "We respect your privacy"`, across
five candidate ids. Then `convert` copied `row.fields` verbatim and the replica's home page introduced
itself with a cookie banner. **The report was not wrong and the exclusion was not wrong**; what was
missing is that the second command reads the first one's finding. This is the failure shape
`shio-replication-test` names — *the report ships ahead of the behaviour it reports on* — and SH452
(a note, not the fetch), SH469 (the list, not the download) and SH429 (stranded bytes visible, delivery
unbuilt) are the same one: each time the diagnosis shipped as a sentence somebody had to act on, and
each time the somebody was a **command**. So the fix is not another exclusion rule:

- `adoptedValues` returns **rows** — `{field, value, candidates}` with the **full** value, never
  `preview`'s 40-character truncation, because the consumer matches on it — and `notes` is *composed*
  from those rows, so the sentence a reviewer approves and the cut that happens are one fact. The
  proposal carries them as **`adopted`**, which is why `PROPOSAL_VERSION` is 3: in a version-2 file
  the finding exists only as prose, so a reader seeing no `adopted` would take it for "nothing was
  adopted" while the note beside it names five candidates.
- `convert` **reads** that list and drops the pair, and reads is the operative word — the rule needs
  every candidate's page set, a fact about the whole capture, so re-deriving the predicate in the
  applier would be a second implementation of one judgement free to disagree with the note somebody
  approved. Keyed on the **`(field, value)` pair** and not on the field name: a `heading` that is
  chrome on one value is ordinary content on another, and dropping the field would take the section's
  own heading with it. **Absent rather than empty**, because `{{#if}}` guards every field in the
  layout (SH465) so absence already renders nothing, while `""` is a value a curator has to notice.
- And **`convert` prints the count** — the same cut, stated by the command that performed it. A drop
  nobody states reads as a capture that never held the thing, which is how a fix of this shape becomes
  the eighth instance instead of the last one. The end-to-end assertion is deliberately end-to-end for
  the same reason: a unit test of the drop passes with the two commands still disconnected, which is
  precisely the state the three tasks above shipped in.

**One block a page, and the rest of the page (SH580).** `propose` offered **exactly one** candidate
per page on viglet.org — the span was 84,252 characters, the whole document body, because the source
is a React SPA whose body is one root `<div>` and `blocksOf`'s SH425 rule ("a node whose children are
repeated generic units *is* the block") is right about it. That one block matched one vocabulary type,
so every word its fields did not name had nowhere to live: measured with SH577's mode-aware `diff`,
**eleven of twelve authorable pages fell below the text floor** — `/turing/release-notes/` kept 1,408
characters of 11,436, `/` kept 2,463 of 7,068. Nothing was broken, which is why nothing reported it:
the block matched, the fields filled, and `loss` counts **elements** (`114×<circle>`), a unit that
says nothing about how much of a page's *reading* survives.

- **The score is the first deliverable, and it is what makes the descent arguable.** Every candidate
  now carries `keeps: {chars, of}` — how much of the block's own visible text the fields would hold,
  computed from the `fields` already in hand rather than from a second extraction, so the number and
  the values printed above it cannot disagree. `URL_FIELDS` is excluded (`ctaHref`, `image`, `icon`,
  `avatar`, `href`): `text()` never sees an `href`, so counting one would put characters in the
  numerator that are not in the denominator and score a hero above 100%.
- **The walk descends only when the split keeps strictly more.** A threshold alone would be
  over-descent in the direction nobody notices — a candidate per paragraph, and a longer list reads as
  a more thorough one, which is `GENERIC`'s own argument inverted. So `descend` compares: below
  `KEEP_FLOOR` (0.5), it asks what the children's fields would carry of the *same* text, and takes the
  step only if that is more. Bounded at `MAX_DESCENT` (4), because "keeps more" is satisfiable by
  ever-smaller improvements and a proposal is something somebody reads. A `NON_CONTENT` child is
  pushed to `excluded` **only if the cut was taken** — an exclusion that changed nothing is a note
  about a block that stayed whole.
- **What the walk cannot improve becomes a note, and `--accept all` says it out loud.** The residue is
  candidates the vocabulary matches and mostly cannot hold; the proposal names them before anything is
  written, and `shio propose --accept` prints each thin candidate it left in the plan. Named, never
  refused — a thin block is still a decision a reviewer is entitled to make, and P7 asks only that it
  not be a surprise. `PROPOSAL_VERSION` is **4** for the sharpest of the three version reasons so far:
  a consumer scoring an acceptance on `keeps` reads an absent field as `undefined`, and every
  comparison against a floor then answers "below it" for every candidate in a version-3 file.
- **The report cost moved ≈99 → ≈108 tokens per candidate and the ceiling moved with it**, as SH511's
  did. Only ≈3 of that is per-candidate (`keeps N%` on the line the reader is already on); the ≈14-token
  legend is printed **once** (P2) and amortizes to nothing on a real site. The rejected draft — a
  `keeps: 34% of this block's text (1408 of 4102 chars)` line per candidate — cost ≈13 each and said
  the same thing.

**An acceptance that writes what the gate will refuse (SH582).** `propose --accept all` on
`openviglet.github.io` accepted **fifteen of twenty-one** candidates, and three of them became
sections whose `heading` — `required: true` on their post-type — is empty, so `shio verify` exits 1
with three `required-field-empty` errors on a replica that is otherwise the best this chain has
produced: one fact lost, both other pages keeping all their text, navigation, sixteen annotated
fields. **The proposal already knew**: it lists the fields each candidate would fill, so whether a
derived set covers the type's required ones is a comparison it holds every fact for. SH551 is the
same shape one level in — a field no *row* fills is a finding, this is a field no *instance* fills —
and the difference is who it costs: SH551's empty label rendered as nothing, this one makes the gate
red.

- **`SECTION_REQUIRED` is a third deliberate copy of the model**, beside `SECTION_TYPES` (what a
  block maps into) and `SECTION_FILE_FIELDS` (which of those the blueprint stores as a file). Same
  reason as the first: `propose` runs before the model exists. `missingRequired` reads it rather than
  the keys `fieldsFor` produced — a required field the extraction could not fill is **absent** from
  the object, not empty in it, so asking the object would find nothing (SH551's rule, one level up).
- **`--accept all` declines them; naming an id still takes it.** An unaccepted block stays verbatim
  markup, so declining is not a smaller conversion — it is the one that keeps the content instead of
  writing a section the gate refuses. An explicit `--accept <id>` is a reviewer's decision and P7 asks
  only that it not be silent, so it accepts and prints `was accepted with no heading — shio verify
  will report required-field-empty`. What `all` left behind prints too, with the reason and the fact
  that the block is still there: a shortened set of acceptances reads exactly like a shorter set of
  candidates (SH138).
- **`missing` is on the candidate and absent when empty**, so a healthy proposal pays nothing — the
  note, the per-candidate `missing (required):` line and the declined line all fire only where there
  is a finding, which is what keeps `propose-cost.test.mjs` unmoved by this task.
- **The duplication is closed by a check with teeth, in both directions.**
  `ShConvertedLayoutRenderTest.theCliKnowsWhichFieldsAreRequired` **parses** the export into a map and
  compares sets, rather than searching it as text the way the `File` check does — a substring check
  can only notice an entry that is missing, and the other drift (a list requiring what the blueprint
  no longer does) silently declines candidates that were appliable. Both reads share one `exportBody`
  reader, so there is one answer to "where does the CLI's copy of the model live".

**And a candidate that is only a `<script>` (SH506).** One of six candidates on a real site read
`"signature": "script"`, `"fields": {}`, `"loss": {"elements":[{"name":"script","count":1}]}` — the
loss line naming the whole of the block — and proposed the post-type `{"name":"Block1","fields":[]}`.
It recurred on all six pages, *which is what promoted it*: a block seen everywhere looks structural.
A script is behaviour and SH469 gave behaviour its own list, so this is the block walker reaching a
node the phase beside it already owns. Two rules, and **the second is the general one**: a block
whose own tag is in `NON_CONTENT` (`script`, `style`, `link`, `meta`, `noscript`, `template`) is not
a candidate, and `proposePostType` returns **`null`** rather than an empty-field proposal whatever
the tag — a fieldless post-type is not a conversion of anything (nothing can be authored in it,
`verify` would report an empty type, and `Block1`/`Block2` is a counter that says nothing). The key
is then **absent** on the candidate, because `{fields: []}` reads as an offer, and the report prints
`no type` instead of `NEW undefined`. Cost is the reason it is worth a task: six candidates is a
short enough list to read carefully, and one noise row makes a reader doubt the other five. Both
exclusions — this and SH505's — are counted in a **note**, since a shortened list of candidates reads
exactly like a shorter list of them.

**Bytes in the tree that nothing references are their own finding (SH429).** Three right rules compose
into a broken replica: SH378 downloads each referenced image into `shio/assets/<site>/…`, SH145 uploads
an asset **only** when a `File` field names its path, and SH379 makes *fidelity* the default — where a
block stays verbatim markup, which has no fields. So on a real replica the logo sat on disk with
correct bytes, the layout's `<img src="/viglet-logo.png">` resolved to nothing, and
`verify --checks projection,replication` **passed**: `source-asset-missing` asks whether the bytes were
downloaded, and they were. `source-asset-stranded` is the second state — downloaded, in the tree, and
referenced by no post — and the two never both fire on one asset, because two findings over one fact
are two reports that can disagree. The referenced set is read from post **data** (a projected `File`
field holds the asset's path, SH90) and a value that is not path-shaped is an id this tree never
projected, left out rather than guessed at, so the set is a lower bound and the rule under-reports
rather than inventing a gap. **The delivery half is deliberately not here (SH437).** SH429 offered a
choice — make the captured asset a static file so the source's own path keeps resolving, or rewrite
the markup's `src` — and the first turns out not to be available: a static file's bytes are public at
`/file_source/<postId>/<fileName>`, keyed by the post's id, and nothing maps a source path to a `File`
post. That is a route the instance does not have, so it is a delivery feature rather than a CLI change,
and this shipped the half that is provable from the tree.

**A chrome asset is site-level, and the rule that catches it is its own (SH441).** SH429's fix said to
point a `File` field at the stranded bytes, and for `/viglet-logo.png` there was no field to point:
the logo is in `div.topbar` and in `<link rel="icon">`, both chrome, and `contentRoot` excludes chrome
on purpose — so a favicon or a wordmark is stranded **by construction** and the advice read as though
the operator had forgotten something, which is P6's failure and not theirs. Two answers were open and
**SH437 had already closed one**: a static file keeping the source's own path is not available, so
choosing it would mean building a delivery route the instance does not have. It is therefore the
site-level one, which is where this product already puts the other two kinds of chrome — SH389's
scripts, SH382's menu. `source-chrome-stranded` carries that: declare a site-level post-type with a
`File` field and point it at the path, or decide the replica does not need the bytes and delete them,
and *not* the original path — the fix says so outright, because it is the answer an operator reaches
for first and SH437's note is not somewhere they would look. **The classifier is `kind`, and it
deliberately under-reports.** An `icon` or a `manifest` is a `<link>` in `<head>`, outside every
content root, so no block will ever declare a field for one — a certainty, not a heuristic. A header
logo is chrome by the same argument and arrives as `image`, because assets dedupe by URL keeping the
first `kind` seen: the wordmark that is *also* a favicon lands as `icon` and the one that is not does
not. So `source-asset-stranded` keeps its SH429 advice **and** names the site-level case, rather than
leaving the operator it still catches with the one instruction that may not apply. The new rule is in
`replicationRules()` and in the registry lint's own fixture — a rule the lint never emits is a rule
the lint cannot check, and one the SH410/SH411 coverage split silently attributes to nobody.

**`shio publish` is the rung of the ladder that could not publish (SH546).** P1 orders every
capability MCP → files/CLI → REST → console, and publication was at three of the four: `shio_publish`
at MCP, `post.publish` on the batch surface, a button in the console. `grep post.publish cli/src/`
returned nothing. The gap was visible **from inside the product** — `shio convert` ends by printing
*every post lands in DRAFT — publishing is a separate, explicit call (P7)*, and the binary that
printed it could not make the call, so the step after the longest command in the CLI dropped into raw
HTTP: one real round published a conversion by reading `/agent/find`, composing a batch document in
Python and posting it with curl. That line now names the command, which is the loop closing. Worth
recording as a **P1 defect and not a missing flag**, because the shape recurs: a capability at MCP and
REST looks complete from both ends — the tool list has it, the API has it, and the tier in between is
the one nobody reads to check. SH244 is the same shape for copy.

Three decisions in it are worth knowing before touching either half:

- **`--all` is a client-side enumeration, not a server-side publish-everything.** `shio_publish`
  refuses a call with no address (*there is deliberately no publish-everything form*) and that
  refusal is right where it is — it is about a **tool** that would otherwise act on content the
  caller never saw. `--all` does what the curl round did by hand: `find` the site's posts, name every
  one of them in the ops, print the count first. The server still receives an explicit list. It
  requires `--site`, because "publish everything readable" would make what a run sweeps depend on
  which sites the credential happens to reach, and it refuses to be **mixed** with named addresses
  rather than merging them — two different requests.
- **It does not filter on SH545's `published`.** The count of already-live pages is *reported* from
  the `find` rows, which is what tells a caller whether this is the publish or a re-run of it, but
  every address is still sent: a live page whose draft has drifted ahead of it still needs
  publishing, and skipping it would drop that edit silently — SH331's class of failure. The server's
  `noop` is the authority on what did not need doing.
- **The batch ceiling is discovered from the refusal, never copied here.** One batch is one
  transaction, capped at `shio.agent.batch.max-ops` (200), which is exactly why a **201**-page
  conversion cannot be one call. The run is sent whole — that is the atomic outcome and the one worth
  having, and a refusal costs one request and no writes because the size is validated before any op
  runs — and `batch-too-large` carries the ceiling as its `example`, a number rather than prose about
  one, so the split is sized by the instance. P6 used as a protocol rather than as a message. A split
  run is atomic per chunk and not across them, which the report says; publish is idempotent, so
  re-running to finish is cheap and safe, and that is what makes the trade acceptable.

**`shio convert` is the verb the pipeline was one short of (SH417).** SH379 stopped at the proposal on
purpose — review and mutation in one command is the surprise P7 forbids — which left
`shio/conversion-plan.json` a file whose only reader was the report that produced it. The applier is
deliberately **a translation into existing verbs, not a new engine**: it builds `shio apply`'s
desired-state document and sends it down the same path, which is idempotent, `--dry-run`-able and the
only write contract there is (P10). One post per **candidate** rather than per page — a candidate *is*
a signature, so the hero that recurs on twelve pages is one section reused, which is the whole reason
the proposal groups by structure — at `/sections/<id>`, plus the captured theme (`Theme`: `TITLE`,
`CSS`, `TOKENS`) and scripts (`SiteScripts`) under `/theme`. Section fields carry the blueprint's
lowercase vocabulary and the render types carry their own uppercase one; this is where the two meet.
**Every post lands in DRAFT**, and `publish` is *absent* rather than `false`, so re-running cannot
unpublish a page a curator has since published. Three refusals, each a refusal rather than a guess: a
**stale acceptance** (an id is a hash of the block's structure, so one the current proposal does not
hold means the source changed after the review — all or nothing, since a half-applied conversion is
the worst outcome available); **nothing accepted**, naming the two commands that fix it; and
**SH436's precondition**, checked against `GET /api/v2/render/post-types/drift` at the one moment
there is an instance to ask, refusing with the `reconcile` call *before* the write and skipping the
check entirely on `--dry-run`/`--out`, which touch no instance. The proposal is recomputed from the
capture rather than read from its file, because staleness is only checkable against the candidates the
current markup produces — `proposalFor` is exported from `propose` so there is one implementation of
what a candidate is. `--out <file>` writes the document and applies nothing, which is what lets the
SH422 chain test cover this phase without an instance.

**It composes the pages too, because a section no page composes is not a replica (SH438).** SH417's
scope was honest and it delivered it — sections, a theme, scripts — and nothing that *ordered* them.
Measured: three posts created, `/preview/viglet2/` answered **404**, and `verify --checks replication`
reported `source-page-missing` for `/`, which is the gate's own statement that the replication is not
done. The replica of a one-page site ended as two orphans, and the step that turns them into a site
was the one step still done by hand — the manual work this phase existed to remove, moved up a level
from the block to the page. Everything needed was already held, so this is a translation into an
existing shape rather than a new decision: the `marketing-site` blueprint writes it down as `url:
"/"`, `folder: "/"`, `data.sections` as an ordered list of section friendly URLs, and those are the
names `composePages` uses. It inherits three rules rather than deciding them — **the source's URL**
(SH385's rule that a replica keeps the original's URL space, which for a home page is `/`, the post
`post:<site>/` names since SH433), **DRAFT** like every other row, and **only what was accepted**: a
candidate left in `fidelity` mode is verbatim markup, not a section, so a captured page whose blocks
were all left verbatim composes nothing and gets no `Page` — an empty one would report success for a
page that serves nothing, which is this same defect one level down. The enabling change is in the
**proposal**, which gained `pages: [{path, title, blocks}]`: `candidates` is keyed by signature —
that is what makes a block one decision on N pages instead of N — so it can say *which* pages a block
is on and structurally cannot say in what order. Without the second axis the sections a page composes
could only be ordered by id, an ordering the source never had. A pre-SH438 proposal file carries no
`pages`, and converts as it did: it composes nothing rather than throwing, because re-running `shio
propose` is what produces the order and a crash is a worse way to say so.

**And a block nobody accepted is now *written*, which is what fidelity always meant (SH449).** The
paragraph above ends with SH438's rule that a page whose blocks were all left verbatim gets no
`Page`. The reasoning was right about an *empty* page and wrong about which page this is:
`propose.mjs`' header documents two modes, and fidelity — "writes its markup verbatim… it renders
identically and nothing about it is authorable" — had no writer at all. `mode: accepted.has(id) ?
"authorable" : "fidelity"` was the whole of it, a string on a proposal entry that the report counted
and `verify-replication.mjs` reasoned about. Measured: accept nothing, `convert --create-site`, and
the document held **the Theme alone** — every captured path 404-ing and the gate reporting
`source-page-missing` for all of them. An unaccepted block was not kept verbatim, it was dropped.

So a page with no accepted section carries its captured **`<body>` inner HTML** in `Page.content`,
and one `PageLayout` per site — `<site> (fidelity)`, at `/theme/layout` — is the shell that renders
it. Four decisions worth keeping:

- **One layout per site, not per page**, and that is forced: the renderer resolves a layout through
  `site.postTypeLayout`, which is keyed by **post-type**, so every `Page` of a site renders through
  one template. What differs per page therefore has to live *on* the page, which is what `content`
  (an `HTML Editor` field the `Page` type already declares) is for.
- **The `<head>` is rebuilt and the `<body>` is verbatim.** The one thing a replica must change is
  where its CSS comes from — the source links a stylesheet at the source's origin, the replica
  inlines the captured `Theme` through `{{theme.css}}`, which is what makes SH424's token edits reach
  the page. `lang` is carried over only when every captured page agreed on one.
- **One layout, both modes, and every conversion with a page gets it (SH464).** A layout is resolved
  through `site.postTypeLayout`, which is keyed by **post-type**, so there is exactly one per site
  and "a layout per mode" is not a shape that exists. SH449 wrote it only for a verbatim page, and
  what that left is what Block Q is named after: the authorable path wrote sections, a Page and a
  Theme and **no PageLayout**, so `/preview/<site>/` answered **400 — binds no layout for post-type
  `Page`**, and the console (which frames the site's front end or, failing that, that same preview)
  had nothing to frame either. A *mixed* conversion was worse than either — the fidelity layout
  renders `{{{content}}}` and a section page has none, so it served a blank body and reported
  success. The template now branches: `{{{content}}}` when the page has it, and
  `{{#getRelation sections}}` when it has sections.
- **The authorable half is `sh-field` per field of the closed SH95 vocabulary (SH464).**
  `{{#getRelation sections}}` walks `Page.sections` in order — SH247's helper takes the field's
  *value* rather than its name, so it needs no post-type — and each row carries the section's fields
  plus its `id` and `type`. `sh-post="{{id}}"` binds the block to the **section** post rather than to
  the page (without it, editing a heading writes the field onto the Page, which loses content
  silently) and `sh-field` is what `ShRegionComposer` turns into `data-shio-prop`. That pairing is
  the whole of the task: fidelity mode already proved the renderer annotates whatever the template
  declares, and verbatim markup declares nothing — so it rendered with **zero** `data-shio-prop` and
  the editor loaded over a page with nothing to offer. The fields are a **union with a guard each**
  rather than a branch on `{{type}}`: the vocabulary is closed, the engine registers no equality
  helper, and a union means a new section type reusing these names renders without a template
  change. `variant`, `align` and `columns` are presentation choices, so they ride as `data-`
  attributes for the theme's CSS instead of being printed as text.
- **Text stops being markup when it becomes a field, so the entity is decoded there (SH476).** The
  source spelled `rerank &amp; media models`; the authorable replica stored those five characters and
  the renderer escaped the `&` again — correctly, it emits text into HTML — so the page read
  `rerank &amp;amp; media models`. Neither half is wrong and the missing one is the decode at the
  boundary, in `propose.mjs`' `text()` plus the one attribute stored as prose (`alt`); URLs are left
  alone, because unescaping a query string belongs where the URL is parsed. **It compounds** — every
  re-capture escapes what the last one stored — and it is worse than a display bug, because the
  editor field holds the entity too: a curator who types the correction stores `&` and the next
  capture overwrites it. **Fidelity mode never had it**, which is why it survived: verbatim markup
  keeps the entity and renders it once, so only the path nobody had finished testing carried it.
  `decodeEntities` is in `clone.mjs` — the module that owns reading a stranger's bytes — and is one
  pass left to right, so `&amp;lt;` stays the text `&lt;`; the named table is the marks a page
  actually spells rather than HTML5's 2500, and **an unknown name is left verbatim**, because
  `&thetasym;` surviving is a report a reader can file and `&thetasym;` vanishing is not.
  **The assertion is the round trip, on both sides of the seam**: the CLI test re-escapes the stored
  field and requires the source's own bytes back, and `ShConvertedLayoutRenderTest` renders a field
  holding a literal `&` and requires `&amp;` once — one language could only check a copy of itself.
- **And SH476's "belongs where the URL is parsed" was the right instinct at the wrong address
  (SH550).** One shape settles it: `style="background-image: url(&quot;../img/11.png&quot;)"`. By the
  time anything resolves a URL, `URL_CALL` has run — and its unquoted branch excludes literal quotes
  but not `&quot;` — so it captures the entity-laden string whole and yields the asset path
  `/br/&quot;../img/introducing-vilt/11.png&quot;`, reported as *no safe path for this name* and never
  fetched. **A decode after that point cannot recover it**: the delimiters the CSS grammar needed are
  inside the token. Six references in one capture, four of them one image under four locale prefixes
  and two of them `&amp;` in a query string on a font URL — and **that second form is the dangerous
  one**, because a URL joined with a literal `&amp;` is a *different request* and nothing about it
  looks wrong in a listing. So the decode moved into **`parseAttrs`**, which is where markup stops
  being markup for an attribute and the only place that gives exactly **one pass** over each value
  (decoding both there and at the URL would turn `&amp;quot;` into `"` and lose the distinction the
  author drew — so `propose.mjs`' second decode of `alt` is gone). Raw-text bodies — `<style>`,
  `<script>` — are decoded **nowhere**, because entities are not recognised inside them; the page that
  produced this carried the unescaped spelling in a `<style>` block and the escaped one in an
  attribute, which is how one reference became one download and one failure. **The asymmetry was
  one-sided and the shared fixture proves it**: the rewriter reads attributes through Jsoup, which
  decodes them and re-escapes on output, so `references-page.html` gained the escaped shape and
  `ShReferenceParityTest` went green unchanged while `clone.test.mjs` went red until the fix — the
  direction that fixture exists to catch. **And the report says whose defect it is**: `unavailable`
  covers off-origin, an unwanted kind, a limit, an HTTP failure and this, and only this one is ours,
  so a reference that still arrives escaped gets `ENTITY_IN_REFERENCE` plus a top-level note naming
  it a capture defect — what still reaches there is an entity the curated table does not know or a
  double escape, both of which are still ours.
- **A link's label is a field too, so it is decoded with the runs (SH670).** SH565 decoded
  `<title>`, SH550 attribute values, SH476 text as it becomes content — and a link's label was
  reached by none of them, so **one document gave two answers for the same bytes**:
  `theme-heavy`'s `<title>Northwind &amp; Co</title>` arrived as `Northwind & Co` and the brand
  link's identical text as `Northwind &amp; Co`. It is a field by SH432's own argument — the label
  is what `shio report` prints and what a curator is asked to keep — so `decodeEntities` now runs
  on each text run as it is appended to the anchor, which is before any `drops` offset is recorded
  and therefore leaves the mark arithmetic intact. Not in `pushLink`: **attribute values have been
  decoded at the tokenizer since SH550**, so SH477's self-label test was comparing a decoded
  `aria-label` against raw text, and a mark spelled `aria-label="A&amp;B"` over the same drawn
  letters could never match — the rule silently skipped every mark carrying an entity, which looks
  exactly like a page that has none. Decoding the runs puts both sides on the same side of the
  boundary. The label inherits the decoder's deliberate limits rather than a second spelling of
  them: numeric forms and the curated names, and **an unknown name left verbatim**, because
  `&thetasym;` surviving is a rendering imperfection a reader can report and an empty string is
  content that vanished.
- **`PAGE_BODY_HTML` is an export with no interpolation, and that is a test seam (SH464).** The
  layout is written here in JavaScript and annotated by `ShRegionComposer` in the JVM, so a Java
  test with its own copy of the template would go green while the shipped one was broken — which is
  what SH449's CLI tests did: they asserted the document's *shape* and could not see that the
  authorable branch wrote no layout at all. `ShConvertedLayoutRenderTest` slices those bytes out of
  this file and renders them through the real engine, so there is no second copy to drift (SH245).
- **A collection is `{{#each}}`, never `{{#getRelation}}` (SH465).** SH464's union drew six *scalar*
  fields and no collection, and four of the seven section types are collection-shaped:
  `SectionFeatureGrid.features`, `SectionFaq.questions`, `SectionLogoWall.logos` and
  `SectionTestimonial.quotes`. So those four rendered their heading and stopped, and
  `SectionLogoWall` — which declares only `heading` and `logos` — rendered as a bare `<h2>`. The
  distinction that decides the helper is the one place the vocabulary's own shape has to be read
  rather than assumed: an SH95 `Relator` declares **nested fields**, so its value is an inline array
  of row objects in the section's own `data`, *not* a list of references to other posts.
  `getRelation` resolves ids and friendly URLs and would stringify a row map into nothing, while
  `ShRenderModel.fieldsOf` already hands Handlebars the `List<Map>` that `{{#each}}` walks. One
  `{{#each}}` per collection, wrapped in `{{#if}}` so an absent one renders no empty container.
- **A row is addressed by a path, because it is not a post (SH465 stated the problem, SH475 is the
  address).** SH185 addresses a field as a flat `prop` on the post the nearest `sh-post` ancestor
  names, and a row sits inside `<section sh-post="{{id}}">` — so `sh-field="body"` on a feature row
  would write `data.body` onto a `SectionFeatureGrid`, which declares no such field, and
  `sh-field="title"` would rename the post. SH465 concluded "no `sh-field`, then", which left five
  editable fields on a page whose two cards hold the content. **An `sh-post` was never available**:
  a nested `Relator`'s rows are inline maps in the section's own `data` with no id. So
  `sh-field="features.{{@index}}.title"` — `annotateEditable` passes a prop through unparsed, and
  `ShPostDraftMergeService` resolves `<collection>.<index>.<leaf>` into the row. **Three segments,
  the middle one digits, and it never creates**: the array and the row must already be there, so a
  mistyped prop falls through to the flat behaviour and lands as an SH168 `unknown-field` rather than
  inventing a shape the type does not declare. `data-shio-row` stays — the path repeats it, a
  stylesheet cannot read an `sh-field`. What a row still has no address for is being **added,
  removed or reordered**; that is the console form's job.
- **The coverage check reads the vocabulary, and that is why it works (SH465).** SH464's tests seeded
  two section types by hand — "chosen because between them they cover every branch the union
  renders" — and both are scalar-only, so this class was green while four types drew a heading and
  nothing else. `ShConvertedLayoutRenderTest` now composes one section per `Section*` post-type **the
  shipped blueprints declare**, with a sentinel in every declared field, renders the page and reports
  the fields that are not on it. A section type or a field the model gains and this template never
  draws fails by name, with nobody having to remember to come back (SH338). `File` fields are the one
  exemption, and it is named in the test: `file_url` resolves a post id, so a sentinel is not a value
  it can render.
- **And it counts what is *addressable*, not only what is drawn (SH475).** The same read of the
  vocabulary, one level down: every nested field of every declared type must appear as
  `data-shio-prop="<collection>.0.<leaf>"` on the rendered page, and every `sh-field` inside an
  `{{#each}}` block must be that block's own indexed path — asked of the blocks rather than of the
  four names, so the next collection inherits the rule.
- **One element declares one field, and a link is two (SH503).** `sh-field` says what an element's
  *content* is, so `<a sh-field="ctaLabel" href="{{ctaHref}}">` annotated the label and left the
  destination with nowhere to go: `ctaHref` was captured, stored on the section and rendered into the
  page with **no declaration**, and a curator could rename the button and not repoint it.
  `sh-field-attr="FIELD:ATTRIBUTE"` is the second declaration — the CTA's `href`, a logo row's
  `href`, and a feature row's `icon`, which is a `data-` attribute with no text node at all. The
  rejected alternative was wrapping the anchor so text and href each own an element; a link is one
  thing to a curator. `image` never had the problem only because its element *is* the `<img>`.
- **The instrument, and why its exemption is a predicate (SH503).** SH465 counted what the page
  **draws** and SH475 what it **addresses**, and `ctaHref` fell between them — drawn into an `href`,
  so its sentinel was on the page, and skipped by SH475's loop, which only looks at dotted paths. The
  count that mattered had never been taken: **9 of the vocabulary's 10 filled fields**. It is taken
  now, over scalars and rows together, and what it exempts is asked of the *declaration*: a
  `Relator` is the list rather than a value, a `Combo Box` (`align`, `variant`, `columns`) is a
  closed set of choices while the bridge's edit surface is a text box — and everything else must be
  addressable in one of the two forms. That replaces a hand-kept
  `Set.of("features.icon", "logos.href")`, which named the two fields somebody had noticed and could
  not name the scalar nobody had (SH142).
- **`{{{content}}}`, not one `Region` per block.** SH191's sidecar shape needs each block's own
  bytes and this pipeline cannot produce them: `buildTree` records no source offsets, so the only
  route back to markup is re-serializing a tree built from `stripNonMarkup`'s output, which has
  already dropped the comments and the raw-text bodies. *Verbatim* and *re-serialized* are different
  claims, and making the second while the docs promise the first is the defect being fixed.
- **A structural layer under the fields, named by role (SH508).** SH507's decision needs a tree
  before it needs a theme, and there was none: the layout emitted `section > h2 > ul > li` and eight
  `shio-*` names, with **no container to bound a measure, no stack to space siblings, no grid to put
  cards beside each other**. The source needed five nested wrappers to place one heading
  (`max-w-5xl mx-auto`, a flex row, a `shrink-0`, a `flex-1 text-center md:text-left`) and the
  layout had none of those elements. `LAYOUT_ROLES` is the contract — `shio-page` on `<body>` (the
  rhythm *between* sections, which needs no wrapper because the sections are body's own children),
  `shio-container` (the outer bound, so a section can still go full bleed), `shio-stack` (the rhythm
  *inside* one), `shio-grid`, `shio-card`, `shio-media`, `shio-actions`, `shio-button`. **Roles, so a
  theme survives the next section type**: `shio-section-<Type>` is the per-type hook and stays, and a
  structural class that named a type would make the theme a list of types. The **column count is not
  a role** — it is `SectionFeatureGrid.columns`, already on the section as `data-shio-columns`, so a
  theme reads `[data-shio-columns="3"] .shio-grid` and the template writes the number once.
  `WRAPPER_ROLES` is the three that are pure wrappers, and SH185 imposes two properties on them that
  `convert.test.mjs` holds: **no `sh-post`**, because ownership resolves to the nearest annotated
  ancestor and a wrapper that declared one would silently reassign every field under it, and **no
  `sh-field`**, because a wrapper is presentation and a curator has nothing to edit in it. The rule
  names three roles rather than eight *and the test asserts why*: `shio-card` and `shio-media` land
  on elements that do declare fields — a feature row is a card and draws its `icon` into an
  attribute — so a rule written over all eight would be false rather than strict.
- **And the JVM checks it on the bytes now, not on two declarations agreeing (SH562).** SH555 and
  SH556 were both found by a hand pass on a real site, and both are `ShPageRenderService` composing
  a layout over content an instance holds — a place `cli/` cannot reach. SH555 got its Java
  assertion when it shipped; SH556 got one only in `convert.test.mjs`, which reads the declared
  union against the declared templates. That is two declarations agreeing with each other, and
  **neither of them renders**: a template can draw a class the union forgot, and the CLI check still
  passes. `ShConvertedLayoutRenderTest` now reads `VOCABULARY_THEME_CSS` out of
  `cli/src/vocabulary-theme.mjs` — the same cross-language read it already does for `convert.mjs`,
  for SH245's reason — renders the vocabulary page and asserts every `shio-` class on an annotated
  element is defined. Scoped to elements carrying a `data-shio-` handle **on purpose**: a converted
  page also carries the source's own classes on markup it kept verbatim (SH575), and a check that
  demanded the theme style those would fail on every working replica. Verified red by removing one
  rule, which named `shio-heading` and nothing else. It costs no context slot — the class already
  has an isolated one, which mattered with the SH423 gauge at 62 of 64.
- **Then the templates broke that rule sixteen times, and one of the two lists had to move (SH556).**
  The theme defined thirteen selectors; the templates emitted those **and** `shio-section-<Type>`,
  `shio-features`, `shio-feature`, `shio-feature-title`, `shio-feature-body`, `shio-questions`,
  `shio-question`, `shio-question-text`, `shio-answer`, `shio-quotes`, `shio-quote`,
  `shio-quote-text`, `shio-quote-avatar`, `shio-quote-author`, `shio-quote-role` — none defined
  anywhere — so SH521's lint fired on **all eleven authorable pages** of a converted site and was
  right: *this page draws with 5 class name(s) its own stylesheet does not define, and it loads no
  other*. The site was 8 KB a page instead of a megabyte, which SH549 achieved, and the classes
  carrying the shape of a feature grid still resolved to nothing. Which list should move looked like
  a genuine choice — emit only roles and lose the per-type hook, or grow the theme by sixteen and
  make it a component stylesheet — and it was not one, because **the hook was already on the same
  element**: `data-shio-section="{{type}}"` sat beside `class="shio-section-{{type}}"`, and
  `data-shio-row="features"` beside `class="shio-feature"`. The per-type handle was spelled twice —
  exactly what the `data-shio-columns` rule one bullet up forbids — so the classes went and nothing
  became unaddressable: `[data-shio-section="SectionHero"]` and `[data-shio-row="features"] h3` are
  what they were. The leaves were already styled *by element* (`.shio-card h3`, `.shio-card p`,
  `.shio-card cite`), so their names carried no shape either. `shio-section` joins `LAYOUT_ROLES`,
  the new `TEXT_ROLES` names the five text roles that were in the theme and in no list, and
  `VOCABULARY_CLASSES` is the union — **the complete set a template may draw**. The instrument is
  the deliverable (SH322): SH509's test asserted every role is styled and nothing asserted the
  converse, which is how sixteen names accumulated, so `convert.test.mjs` now holds both directions
  and reads the layout **constant** rather than a rendered page — every `{{#if}}` branch is in the
  string, so a class only a testimonial draws is still counted.
- **And the theme that lays it out, as a `Theme` post (SH509).** `vocabulary-theme.mjs` is the
  stylesheet SH507's decision promised: type scale, measure, vertical rhythm, a card, a button, and
  two colour roles — **surface** and **ink** — each with the one companion a card and a caption need,
  plus the accent a button is and the line a card is bounded by. **Content, not a file**: it is
  written into the conversion document exactly the way the captured theme is (`/theme/shio`,
  `<site> (Shio)`), so a curator changes a colour without touching the CLI and a site with its own
  theme overrides it by naming that one on the layout — a stylesheet in the jar would be the one part
  of a converted site nobody can change, the shape SH183 rejected when it made the render model
  content. *SH509's rationale names a **blueprint** as the vehicle and this is a post instead*:
  `convert` writes a desired-state document and has no way to apply a blueprint, so a converted site
  would render themeless until somebody applied one by hand, and a package **plus** the constant
  `convert` needs would be two copies of one stylesheet. **Written only when the layout names it**
  (SH520 tightened this from "sections and a layout") — the roles are SH508's, and a page that is
  verbatim in whole or in part has none of them, so anywhere else it would be a post nobody renders. The tokens ride it as DTCG, because `ShThemeTokens`
  compiles `color.ink` into `--color-ink` and prepends the `:root` block, which is what makes a colour
  a field edit. Three closures the test holds, each of which fails the way an unstyled page fails —
  silently: every role in `LAYOUT_ROLES` is styled, every `var()` the stylesheet reads is a token the
  theme declares, and **every token it declares is read** — a token nothing uses is a field a curator
  can edit to no effect. And nothing in it names a section type.
- **And the layout names the theme the page can apply, which is where the megabyte goes (SH510).**
  An authorable page inlined the captured theme and could not apply it: **1,062,141 of the page's
  1,066,436 bytes** were a stylesheet whose ~2,894 rules met 8 `shio-*` names — served on the public
  route with `max-age=300` — and with SH509's theme in the document that would have become *two*
  stylesheets on one page whose order decides the outcome, the accident SH424 removed, reintroduced
  by inclusion rather than by cascade. **The mechanism is the name, not a second `<style>`:** the
  layout still emits exactly one `{{theme.css}}`, and `layoutTheme` decides which post resolves it —
  `<site> (Shio)` for an authorable conversion, `<site> (captured)` for a fidelity one. No template
  branch, no second handle, and the vocabulary theme keeps its token compilation, which a literal
  copy of the CSS in the layout would lose along with being a second copy of one stylesheet.
  **`authorable` is `verbatim === 0`, not "has any section"** — a conversion that left a page verbatim
  still names the captured theme, because fidelity's claim is a pixel comparison and cannot be made
  without those bytes; SH520 added `&& mixed === 0` for the same reason one page down. The capture
  is written byte for byte as before. The test measures rather than asserts a name: against a
  ~1.04 MB captured stylesheet the named theme is **under 1%** of it, there is exactly one
  `{{theme.css}}`, and `convert`'s report names the theme, because under SH507 which presentation a
  reviewer is getting is the mode's contract and not an implementation detail.
- **And the site-wide switch was the regression, not the theme (SH549).** `authorable` was
  `verbatim === 0 && mixed === 0`, so **one** block the vocabulary could not name flipped it for the
  whole site. Measured on viglet.org: `propose --accept all` took six of seven candidates, the seventh
  reported honestly as *nothing in the vocabulary fits this block*, and the twelve pages then shipped
  1,062,146 characters of the source's CSS each — defining none of the `shio-*` classes the section
  templates draw, `page-unstyled` on ten of them. A megabyte of CSS that styles nothing, on every
  page, per request. **SH510's reasoning was right and its frequency estimate was not**: a real site
  always has a block the vocabulary cannot name, this was the first attempt on the first site, so
  `verbatim === 0` is the normal case rather than the rare one. So `authorable` is now simply "any
  page composes sections" — **and the trade SH510 refused is still not made**, because the captured
  CSS reaches the pages that kept verbatim markup through a **per-page** condition in the layout's
  head: `{{#if content}}{{#getRelation "/theme/captured"}}<style>{{CSS}}</style>{{/getRelation}}{{/if}}`.
  `content` holds the source's own markup, so an authorable page still receives exactly one stylesheet
  and is byte-identical to what SH510 produced; only a page carrying the source's class names gets the
  source's CSS. **By reference, so there is still one copy** — the captured `Theme` post remains the
  single one and SH424's token edits still reach the page, which the literal copy SH510 rejected would
  have broken in this direction too. Second in the head, so the source's rules win the collisions a
  reset would otherwise decide against the page's own markup, and a **mixed** page gets both and needs
  both — that page is the one the old switch left half-unstyled whichever way it went. **The one check
  neither language can make alone is in the JVM**: `CAPTURED_STYLE_HTML` is a no-interpolation export
  (SH464's seam) and `ShConvertedLayoutRenderTest` reads it, seeds a layout naming **no** `THEME`, and
  asserts the captured rule reaches the verbatim page and not the authorable one — because whether a
  **literal** first argument to `{{#getRelation}}` resolves at all, when the helper's declared shape is
  a field *path*, is exactly the thing a silent `{{else}}` would hide behind a green JS suite.
- **A page is not one mode, and accepting one block used to discard the rest of it (SH520).**
  `composePages` wrote `content` **or** `sections`, so a page with six blocks and one acceptance
  became a `Page` holding a 6-byte `title` and one section — measured on `viglet.org`, where the same
  page in fidelity holds 42,371 bytes: the hero, the product grid and the footer gone, with
  **1,062,261 bytes of captured CSS** still served around a 3,486-byte body carrying zero source
  classes. `propose` prints the opposite in the same run — *an unaccepted block stays verbatim
  markup* — and that promise is what makes accepting one block incremental rather than an
  all-or-nothing rewrite. Mixing is the normal case, not an edge: six candidates and four vocabulary
  matches is a good hit rate, and every page then loses most of itself. So a mixed page now carries
  **both**: `sections` for what was accepted, `content` holding the page's own bytes with exactly the
  accepted blocks cut out. **Verbatim in SH449's sense — a slice, not a re-serialization** — and that
  is what the mechanism buys: `stripNonMarkup` **blanks what it removes with the same number of
  spaces** instead of deleting it, so every offset into its output is an offset into the source;
  `tokenize` yields each token's `[start, end)`; `buildTree` records the span on every node; and the
  proposal's `pages[].spans` is the array parallel to `blocks`. (The one concession, and it is
  SH468's safe side: `foo<!--x-->bar` now reads as two words rather than one.) The two older shapes
  are byte-identical — accept nothing and the whole body is the content, accept every block on a page
  and there is no remainder — and a proposal written before the spans composes sections alone rather
  than shipping a second copy of the accepted markup. **Ordering is what it does not buy**: a layout
  is resolved per post-type and holds one `{{{content}}}`, so the remainder keeps the source's order
  among itself and the sections render after it.
- **A site of pages with no way between them (SH575).** An authorable conversion keeps what the
  vocabulary matched and drops the rest, which is the design — and the source's navigation lives in
  *the rest*. Nothing put one back, so a replica of a twelve-page site served twelve pages with no
  link between any of them. Measured on viglet.org with `--accept all`: `shio diff` reported
  `internal links: 16 → 0` on **all twelve**, `audit` reported `no-landmark`, `verify` exited **0**,
  and the corpus then held it across four archetypes — it is a property of every authorable
  conversion, not of one source. Eight rounds missed it because **each instrument measures the page
  it is given, and a page with no links is a valid page.**
  The layout now emits `<nav class="shio-nav" aria-label="Site">` over
  `{{#query type="Page" sort="title" limit="50"}}`, inside `PAGE_BODY_HTML` so the cross-language
  test renders it. Three decisions, each of which is a thing that would otherwise be wrong:
  - **`{{#query}}`, not `{{#navigation}}`** — which XVII.80 named as the cheapest and is the wrong
    helper for this tree. `{{#navigation}}` yields the site's top-level **folders**, and a conversion
    writes pages into the root (`PAGES_FOLDER` is `/`) with `sections` and `theme` beside them, so
    the nav would read *sections, theme* and link to neither: a folder row's `url` is its index post
    (SH381) and neither has one. With no `folder=`, `{{#query}}` resolves the **rendering post's own**
    folder, which is right for a flat replica and for a locale-prefixed one alike.
  - **`{{#unless content}}`, which is SH549's per-page condition** and not a site-wide switch.
    `content` holds the source's own markup, so the guard separates a page that *lost* its navigation
    from one that still has the source's. Fidelity is judged at 0.00% by `snapshot --against` — that
    bar has been reached and is the mode's whole claim — and a Shio nav above the source's own would
    put it out of reach for the one mode with nothing wrong with it. A **mixed** page is on the same
    side of the line, since SH520 keeps the source's bytes around what was accepted.
  - **`sort="title"`, and no `sh-field`.** Alphabetical because a menu whose items move when somebody
    edits a page is a control people misclick — `{{#translations}}`' argument for ordering a switcher
    by locale, one helper along. The nav declares no owner and nothing editable: it is derived from
    the site's own tree, so a curator edits it by renaming or moving a page, and an `sh-field` here
    would offer a text box that writes to nothing.

  `shio-nav` joins `LAYOUT_ROLES` and is styled in `VOCABULARY_THEME_CSS`, which SH556's two-way
  check enforces in both directions. **The half only the JVM can assert** is that those rows *exist*:
  a template that compiled and listed nothing would pass every check in JavaScript, so
  `ShConvertedLayoutRenderTest` renders the seeded replica and reads the sibling pages' friendly URLs
  off the page — and reads the verbatim and mixed pages to confirm they get none.
- **The one address the grammar does not spell, unwritten (SH576).** `post:<site>/` is a site's home
  page — the address form SH433 exists for *because* SH79 does not spell it. A source that answers
  `/` gives the conversion one for free; a source that does not gave it **nothing, and nothing is
  what was written**. The `locale-prefixed` archetype is the case and it is ordinary rather than
  exotic: every page under `/en-us/` or `/pt-br/`, `/` a 404 at the source, and the document held
  pages at both prefixes and none at `/`. The replica's root — the first URL anybody opens and the
  one the console frames — answered nothing, while the run reported every page it wrote and every
  one of them was correct.
  XVII.81 named three defensible answers (redirect to whatever the source's root redirected to;
  write a home page listing what is there; refuse and say which page to make the front door) and
  one that is not, which is what shipped: **neither a page nor a note.** The first is *unavailable*
  rather than rejected — `buildInventory` drops the `redirectedTo` that `visit` returns, its own
  open task — so "the locale the source redirected to" is not a fact the run holds, and deriving it
  from the first path would be a guess wearing a redirect's clothes. So the default is the second
  and `--home` is the third, **turned from a refusal into an instruction** (P6): a refusal would
  stop a conversion in which every page is right, over a page the source never had.
  - **The written home page carries a title and nothing else**, which is not a stub: no `content`
    and no `sections` is the layout's `{{#unless content}}` branch, and since SH575 that branch *is*
    the site's own navigation. So the front door lists every page of the replica with no words
    invented on the source's behalf (P7), and a curator edits it like any other page.
  - **`--home <path>` writes a `Redirect` at `/`** — SH385's post-type, resolved on the read that
    already ran, so a front door needs no route and no rewrite rule. Not permanent: the first answer
    to "which page is the front door" is one a reviewer may revise, and a 301 is the redirect a
    browser caches past the correction. `en-us`, `/en-us` and `/en-us/` are one request and the
    **page's own** spelling is what gets written; a path the conversion does not write is refused
    with the list, because a redirect to nothing is a 404 with an extra hop.
  - **The note is the deliverable as much as the page is.** A run that says only what it wrote
    describes a complete success and produces a site with no root, so `convert` now names the one
    page here that the source did not have — and `buildConversion` returns `home` rather than only
    printing it, since the corpus is a reader too (P9).

  One scoping change came with it: SH560's fidelity group asserted every converted `Page` carries
  `content`, and now runs over the pages the **source declared**. A front door has no verbatim
  markup because the source served a 404 there, and demanding a pixel comparison against a page that
  does not exist is the rule read past its own meaning — with a guard that the narrowed filter still
  matches something, since a scoping change that silently matches nothing is the failure it invites.
- **The binding rides the document** (`postTypeLayout`, new on `ShAgentDesiredState`), because a
  layout nobody bound renders nothing and no `post.upsert` can express that edge — without it
  `--out` would emit an un-appliable file. It compiles to a **second** `site.upsert` after
  `createSite` rather than folded into it. That began as a workaround — the create branch dropped
  `data.postTypeLayout` silently, on exactly the run that needs it — and **SH453 fixed the branch**,
  so it is now a choice: `postTypeLayout` is settable without `createSite`, so the separate op is the
  one path that serves both cases, and folding it would buy one op on one of them and cost a branch
  on every run.

**And the bytes go up with it (SH437).** A fidelity page references its images by the source's own
path and has no `File` field, so SH145's rule — an asset reaches the instance because a *field* names
it — leaves them in the tree, which is what `source-asset-stranded` has been reporting since SH429.
`convert` therefore uploads the captured assets itself, after the apply and only when there is
verbatim markup: each one into the folder chain its source path implies, under its source file name,
which is exactly the pair `ShSiteAssets` walks back at render time. Idempotent (an asset already in
its folder is left alone — replacing is a decision about bytes a curator may have changed) and quiet
about what it cannot do (an asset the capture never downloaded is counted, not raised: the gate
already reports it as `source-asset-missing` with the `clone` that fixes it, and failing a whole
conversion over an image would be worse). `--assets-dir` moves where it reads from.

**And the branch written for the root folder was the only one that malformed its address (SH456).**
The upload resolved `folder:${site}${at.folder === "/" ? "" : at.folder}` — the ternary strips the
slash for the root folder, and the slash is exactly what the root form needs. `folder:Publico` has no
path at all, which `ShAddress.parse` refuses, printing the shape it wanted; `folder:Publico/` is the
root. So every *other* folder worked and the one case the branch existed for did not. **It is the main
path, not an edge**: a single-page capture converts into the site's root folder, and any site with a
`<link rel="icon">` has an asset there, so every fidelity conversion of a home page hit it. **The exit
code is the damage** — `convert` printed `changed: 7 object(s) - in DRAFT`, left three posts on the
instance, and exited **1**. SH447's shape one command along: work done, refusal reported, except that
here nothing is half-written, only half-said, and `convert` is idempotent so a caller who trusts the 1
re-runs it and sees the same 1 forever. The guard is in the harness rather than beside the fix: the
`convert.test.mjs` stub for `/agent/resolve` answered `{ id }` to *anything*, so it could not disagree
with its caller — it now returns 400 for a `folder:` target with no `/`, the way the instance does,
which makes the whole file a guard against the class rather than the instance. SH458's conformance
run asserts the process's exit code, which is the observable a module call cannot have.

**One thing this needed from the backend, and it was a P1 gap the SH339 inventory had mis-classified**:
`POST /api/v2/staticfile/upload` was reachable only with a session cookie, so *no* stateless caller
could put bytes into an instance — `shio push --content` included, since SH145, with every test of it
injecting a `fetch`. The inventory listed the path as `AGENT_EQUIVALENT` on the reasoning that MCP's
`shio_assets` covers it; that is false in the direction that matters, because a multipart upload is not
a shape JSON-RPC has. `/api/v2/staticfile/**` now carries SH117's *narrowed* exemption (session-less
callers only, so the console stays CSRF-checked) and the inventory row is gone —
`ShStatelessReachabilityLintTest` fails if it comes back.

Both keys are written only when there is verbatim markup, so an all-accepted conversion is the same
bytes it was before (P5). SH417's *"nothing has been accepted"* refusal is gone with the defect it
described; a capture with no markup is refused one step earlier by `proposalFor`, which knows the
origin and prints the exact `clone` to re-run.

**`--create-site`, because the fourth refusal named a remedy this surface cannot express (SH439).**
`shio convert --site Viglet2` against an absent site printed the plan and then refused with *"add a
`site.upsert` op for it earlier in the same batch"* — the shared write resolver's fix, right on the
batch surface and un-followable here, since `convert` composes its own document and takes no ops.
P6 makes an instruction naming something that does not exist worse than none, and it landed at the
worst moment: converting into a **new** site is the normal shape of a replication, so the first real
run of the phase hit it, and closing it by hand meant a `site.upsert` through `/agent/batch` — a
surface a caller holding the CLI has no reason to be holding. So the flag, on `convert` *and* on
`apply`'s document mode, where it had been blueprint-only: one flag, one compiled `site.upsert`, one
meaning. It rides the **document** rather than the request, so an `--out` file means what the run
meant and `shio apply conversion.json` is the same act — and the key is written only when asked, so
every document produced before this is the same bytes (P5). Opt-in rather than default, because
creating a site nobody named is the surprise P7 forbids; `--dry-run` shows what would happen, and
works now, which it could not before (the plan resolves the site off its own first op — SH329).
`unknown-site.mjs` is the other half: on `convert` and `apply` the refusal's `fix` and `example` are
rewritten to name the flag, and *only* for the `unknown-site` problem — offering `--create-site` at
some other 404 would be this same defect pointed the other way, which is what the test pins.

**The proposal states its own preconditions, because the field it writes into may not exist (SH436).**
On a real instance `Theme` declared `TITLE`, `CSS` and `JAVASCRIPT` and **no `TOKENS`**: the post-type
predates SH195, `ensure` deliberately never touches an existing one, and SH289's reconcile is additive
*and* opt-in. Every one of those is right — a widget change is a different decision from a missing
field — and the repair is one call (`POST /api/v2/render/post-types/reconcile`, with
`GET /api/v2/render/post-types/drift` answering without writing). The gap was **when** anybody learns:
`clone` and `propose` never talk to an instance by design, so the first moment a missing field matters
is when a token is written, and a project pays for a capture, a conversion and a review before finding
out the target cannot hold half of it. So the artefact carries `requires` — `Theme.TOKENS` when it
holds a theme, `SiteScripts.SCRIPTS` when it holds scripts — **derived from what the proposal actually
holds** (a requirement that is always there is one nobody reads), each row naming the repair, since a
precondition a reader cannot satisfy is a complaint. `shio verify` still carries the finding: that is
the right home for the *check*, and this is the same fact said early enough to act on. `PROPOSAL_VERSION`
is **2** for it, because a consumer of a version-1 file sees no preconditions and would take that for
"there are none" — an applier that skips the check writes a theme without its tokens.

**A lifted declaration comes out of the kept CSS, or the tokens are dead on arrival (SH424).**
Measured on a real replica, not argued: `shio propose` wrote nine declared tokens into `Theme.TOKENS`,
and the rendered page carried **two** `:root` blocks — the token-compiled one at char 1794 and the
source's own at 2735, still inside the `CSS` field SH380 keeps verbatim. Equal specificity, later wins,
so all nine were inert and a curator editing `brand` saw no change. **Both halves were individually
right**, which is why nothing caught it: SH380 refuses substitution because search-and-replacing
`#ea580c` across a stranger's stylesheet breaks a replica in a way no digest catches, and
`ShThemeTokens` *prepends* so a theme's own CSS can override the token block — correct when one author
wrote both. Composed, the artefact's editability claim inverted while the report stated it out loud.
The fix is narrower than what SH380 forbids and is not the same act: a lifted property is re-emitted
**byte-identically** by the compiled `:root`, so dropping that one declaration preserves the computed
value, where rewriting a literal into a `var()` would change it. **Only lifted declarations, never the
block** — the source's `:root` also holds `color-scheme: light` — and a recurring token is untouched,
since nothing references it yet. Removing every occurrence of a lifted name is safe because
`declaredTokens` already guarantees one is declared *only* under a root selector and with *one* value.
The exception is a value holding a `url()`: the kept CSS has been rewritten to the replica's own path
(SH461) and the token has not, so those stay declared and stay **named in a note** — a token that cannot be edited is
the claim this task exists to stop making silently. The guard is stated over the whole artefact rather
than per fixture: *nothing the theme keeps re-declares a token the theme writes*, which is exactly the
condition that makes an edit reach the page. Invisible to `snapshot --against`, `digest` and `audit` —
the replica looks perfect either way.

**The DTCG path collision was self-inflicted, and the flat name resolves it (SH428).** Over
`openviglet.github.io`'s palette, `dtcgFor` wrote **nine of sixteen** declared tokens and dropped seven
— `bg.alt`, `border.strong`, `brand.2`, `brand.3`, `brand.ink`, `brand.wash`, `brand.font` — with
*"a group and a token cannot share one path"*. Detecting that is right (DTCG forbids it, and writing
anyway loses a token silently); **creating** it was the defect: `pathForCustomProperty` splits on every
hyphen, so `--bg` becomes `bg` and `--bg-alt` becomes `bg.alt`, and the first then has to be both. The
round trip is the only hard rule here and a **single-segment path passes it** — `customPropertyFor("bg-alt")`
is `--bg-alt`, the property the source wrote — so a colliding row falls back to its whole name as one
segment, accepted only when it compiles to the *same* custom property the nested path would have.
Nothing guessed, nothing lost. Rows are taken **shortest path first**, which is what keeps the flat
name free at the moment it is needed (in the other order `bg` finds a group already there and has
nowhere to go); the emitted JSON is unaffected, since every key is sorted on the way out. `--x` plus
`--x-y` is the commonest scheme in hand-written CSS, and here it cost the accent ramp — `--brand-2`
and `--brand-3` are the hero gradient and the primary button — which is SH210's hazard: a curator
replacing "the brand colour" replaces a quarter of it. **Second, smaller, same run:** the note said
"16 declared token(s) are the editable half" while its own list showed nine written and seven not,
because the count was taken *before* the drop. The counts now come from the rows the file holds, and a
row that took a flat name carries the nested one it would have had.

**An inline stylesheet is a stylesheet (SH427).** `openviglet.github.io` declares its whole palette —
sixteen custom properties — inside one `<style>` block and links **zero** external stylesheets, and
`shio propose` over that capture emitted `theme: null`, `notes: []` and printed no theme section at
all. That is the failure mode SH380 named and guarded one case of (a variant-only palette is *reported*
rather than left silent, because silence reads as "this site declares no tokens"), reproduced with no
guard and for the **strong** case: a declared custom property is the one SH380 calls genuinely
editable, since the source's own CSS already says `var()` on it. Critical CSS in a `<style>` block is
what a single-page marketing site does, so the commonest shape of the page a replication starts from was
the shape whose theme was invisible. `stripNonMarkup` removes those blocks — right for building a tree,
and exactly why the theme phase could not see them — so `styleBlocks` reads them from the markup
`clone --markup` already stored: no request, no new extractor, one `styles` entry per page labelled
`"/ <style>"`. **`url()` is the one thing that differs and is passed rather than assumed**: an inline
block resolves against the *document*, while `/assets/css/site.css` resolves against its own directory
— SH380's rule inverted, and getting it wrong names a file that does not exist. A `<style type>` that
is not CSS is skipped rather than parsed as CSS, and when every sheet read was inline the theme says so:
sixteen tokens with no stylesheet in the tree is a state a reader would otherwise try to explain, and
there is no file to diff a change against.

**The same command emits the theme as tokens *plus* CSS, never as CSS (SH380).** SH195 gave a `Theme`
a `TOKENS` field so the commonest change — the accent colour, the type scale, the density — is a field
edit rather than a stylesheet rewrite; a capture runs the other direction on the same argument, so
concatenating the source's stylesheet into `CSS` throws away the palette it *contains* and the replica
then looks right and cannot be changed. It reads the stylesheets `clone --assets` already put in the
tree (no new request), and lands in the **same** proposal file: a candidate's `replaces` line names
"3 class name(s) of the source's styling", and this is where that styling went. Six decisions:
- **Two kinds of token, and they are not equally powerful — which the report says out loud.** A
  **declared** custom property is the strong case: the source's own CSS already says
  `var(--brand-primary)`, so lifting it makes it genuinely editable. A **recurring** literal (`#0b5533`
  written twenty-three times *is* the accent colour) only names the palette and changes nothing until
  the CSS is rewired. A merged list implying both were editable is the only way this artefact can
  mislead somebody, so the two are printed separately with a note each.
- **Substitution is refused, deliberately.** Rewriting `#0b5533` → `var(--color-text-1)` across the
  stylesheet is the obvious next step and is SH379's "silently restructured somebody's homepage" hazard
  in a different hat: this module *scans* CSS, so a search-and-replace over a stranger's bytes — one hit
  inside a `content:` string or a data URI — breaks a replica in a way no digest catches.
- **The DTCG path is the inverse of a documented mapping, not a guess.** `--brand-primary` →
  `brand.primary`, because `ShThemeTokens` compiles `brand.primary` back to `--brand-primary` and the
  captured CSS references that name. **The round trip is the only hard rule here**: a candidate whose
  path does not compile back to the property the source wrote is *reported, not written* — SH195 drops
  such a declaration silently and can take the whole `:root` block with it. `customPropertyFor` mirrors
  the Java and `theme.test.mjs` reads `ShThemeTokens.java` to assert the two stay the same, because the
  alternative is this module writing a name whose runtime spelling it cannot predict.
- **A token the capture is unsure about is reported.** Three real cases, each a real stylesheet: a
  **variant** (`--bg` under `:root` and again under `@media (prefers-color-scheme: dark)` — writing
  either picks a theme nobody asked for), a **conflict** (declared twice under one root, which is a
  cascade question and this is a scanner), and a **name** that fails the round trip. Plus
  *variant-only*, where the palette lives entirely under `.dark` and there is no default to lift —
  reported because silence there reads as "this site declares no tokens".
- **A recurring token carries the count that argued for it**, and is named group + role + rank ranked
  by frequency, ties broken on the value. An invented name (`color.brand`, `spacing.comfortable`) reads
  better and is exactly the "token nobody can trace back to the source" the rule forbids. `--min-refs`
  (default 3) is what stops every colour on the site becoming a token; a **shorthand is not a length**,
  so `padding: 8px 16px` becomes two tokens or none, and none is the answer a scanner is entitled to.
- **Every `url()` is rewritten to the path the source used**, resolved against the **stylesheet** and
  not the page — `url(../fonts/x.woff2)` in `/assets/css/site.css` is `/assets/fonts/x.woff2`, and the
  page-relative reading names a file the download never wrote. That is what makes the theme portable
  instead of a set of links to somebody else's origin. One the asset map does not hold is left exactly
  as it was and named: rewriting it to a path with no bytes turns SH378's named licensing gap into a
  broken replica.
  **It wrote the SH378 *tree* path (`/<site>/<chain>/<name>`) until SH461**, and that is why a
  replica's fonts 404'd on a route that serves them: the tree path is addressable in `shio/assets/`
  and nowhere a browser can ask for, while the instance resolves a **site-relative** path — which the
  verbatim markup on the same page had been using correctly since SH437. The map is still the gate;
  only what gets written changed. The delivery half is `ShPreviewLinks` walking `<style>`
  ([renderer.md](renderer.md)).
Three things a smoke run against a real stylesheet found that the unit tests had not asked:
**a custom property keeps its case and an ordinary property does not** — CSS folds `COLOR` and treats
`--Foo`/`--foo` as two properties, so lowercasing everything both merges two declarations and *defeats
the round trip*, since `--brandPrimary` lowercased is `--brandprimary`, which survives it and would be
written under a name the source never references; **two quantifiers over one digit class**
(`\d+\.?\d*`) is the SH377 backtracking defect one module along, and this reader is also fed a
stranger's bytes, so a 20k-digit value would hang the scan (pinned with a timeout as the assertion);
and a role-less rank now joins its stem with `.` (`radius.1`, not `radius-1`) so it nests *inside* the
group holding the source's own `--radius-md` instead of sitting beside it — free, because both compile
to `--radius-1`. One suggestion to refuse if a linter offers it: `localeCompare` in `sortKeys` would
make the serialized bytes depend on the machine's locale, which is P5 undone by something that reads
like a fix. And `propose` now refuses only when there is **neither** markup nor a stylesheet: a capture
taken with `--assets` and without `--markup` is a real state, and withholding the theme over a flag
that has nothing to do with the theme would be the wrong answer to it.

**`shio snapshot --against <url>` compares the replica with the original, and it is the one command
here whose gate is on by default (SH386).** SH102 answers *"did this page change since I last
looked"*; a replication asks *"does this page look like somebody else's"*, and the appearance digest
cannot answer that either — SH187 recorded why, and SH265 restated it: there is no layout engine, so a
digest covers the failure an **agent** creates and not the one a **human** sees. A replica's whole risk
is the second kind. Six decisions:
- **Both sides in the same run**, at each width, with the same viewport, animation suppression and
  timezone. Comparing a picture taken now against one a different browser build took last month
  measures the browser, which is the failure mode that makes a visual gate get ignored.
- **The source image is written to the tree and kept.** That is what makes the number reviewable
  rather than merely produced: a pull request shows what the replica was compared against. It is
  **re-captured every run** rather than reused, because the source is *somebody else's site* and may
  have changed — the kept file is what *this* run compared against, which is the honest artefact even
  though it means the number is not reproducible against a moving original.
- **A difference exits 1, and this is the only place SH102's rule inverts.** There is no
  `--fail-on-change` to remember: nobody runs this except to find out whether the replica matches, so
  a mismatch is the defect rather than the change you just made — the reasoning that keeps the gate
  opt-in everywhere else does not apply where the command *is* the gate.
- **`--width` takes a list here** (`1280,768,375`), one number and one diff image per width, because a
  replica that matches at 1280 and breaks at 375 is the ordinary outcome and one averaged number would
  hide exactly that. **The baseline mode refuses a list** and says `--against` is what several widths
  are for: `<slug>.png` is a file every project already holds, so a second width there would orphan it
  or need a conditional name — SH379's rule about a file name never depending on what else the run was
  asked to do. One parser (`widthsOf`), so the flag has one type and only its *capacity* differs.
- **Names are always width-suffixed in this mode** (`<slug>@<width>.source.png` / `.replica.png` /
  `.diff.png`) for that same rule, and they are *names* rather than paths — the command joins them
  with `node:path`, per SH198.
- **The byte-level answer is nearly useless here and the run says so.** Without `pixelmatch` + `pngjs`
  there is no magnitude, and two browsers photographing two different origins almost never produce
  identical bytes — so a difference at byte resolution is *unmeasured*, not small or large, and a
  reader is told that rather than left to read it as a finding. `describe`'s rule about naming the
  resolution, at the one place the weaker answer stops meaning anything.
- **And the summary line says it too (SH443).** It used to read `1 differing, worst 100.00% at 1280px`
  four lines above its own `1280px  differs (bytes only — no magnitude)`, with the note above saying
  it a third time: the file knew, and one line did not. The reducer read a null ratio as `1` for
  anything not `same`, so the header printed a percentage nothing had computed. Worse than cosmetic
  because of *which* line — the summary a reader takes away and an agent greps, where `worst 100.00%`
  reads as a total mismatch and is the number that ends a replication early, about a replica that may
  be all but identical. The same defect class as a count taken before a drop (SH428's note claimed
  sixteen editable tokens beside its own list of nine). Now `N differing (unmeasured)` and **no**
  percentage, with three cases rather than two: nothing measured it, measured and non-zero, measured
  and zero — only the first is unmeasured.
- **The comparator has real coverage now, and did not before (SH444).** `pixelmatch` and `pngjs` were
  in no lockfile, so every test here injected `compare` or ran the byte path *by absence* — the wiring
  between this command and the two helpers was never executed. Declaring them at the root turned
  eleven snapshot tests red with a pngjs stack trace, because their fixtures capture strings rather
  than PNGs and nothing had ever parsed them. That is the real defect the dependency hid: a test whose
  branch depends on what happens to be installed is a test of neither branch (P5). `ctx.compare` now
  has three states — a function, **`null` for "there is no comparator"**, and `undefined` for "resolve
  what this checkout has" — so the degraded path is *declared* by the tests that pin it, and one test
  builds real PNGs and runs the actual comparator.
- **A screenshot of an error is not a comparison, and the gate now refuses to score one (SH450).**
  The first `--against` run that reached a browser reported `100.00% of pixels` on a replica whose
  markup is the source's, *verbatim*. The PNG was a white page reading `{"error":"Unauthorized"}`.
  The credentials were computed and handed to Playwright, and that was never the problem:
  **`httpCredentials` answers a challenge and does not send `Authorization` unprompted**, and the
  preview's 401 carried `Content-Type: application/json` and no `WWW-Authenticate`, so there was
  nothing to answer and the browser never retried. `curl -u` worked against the same URL for the one
  reason that matters — it can be told to send the header first. Two ends, and both were worth
  having: the server now challenges on `/preview/**` (see `security.md`), and `capture` reads
  `page.goto`'s response and **refuses any non-2xx** rather than photographing it. The status, not
  the pixels: an error page is small, plain and stable, so it compares against itself perfectly and
  against a real site at exactly 100% — a failure to *capture* must read as a failure and never as a
  difference, because the two have nothing in common but the digit. The 401 gets its own sentence
  naming the challenge, since credentials-in-hand plus a 401 is a fact about the instance and "check
  your password" would send a reader nowhere.
  **And the test that let this ship is the SH448 shape exactly**: `snapshot.test.mjs` asserted
  `seen[0].credentials` equals the admin pair — that they were handed to the *injected* capture —
  and its comment even names the failure it was written to prevent ("without this the capture would
  be a photograph of the login page"). It was true, it was about this command's plumbing, and no
  test started a browser. The comment now says what it does not cover.
SH365 was a hard dependency rather than a courtesy: `--url` was already two arguments wearing one name,
and a third meaning would have made the flag that says where Shio is also decide what it is compared
to. No endpoint and no MCP tool — SH268's rule plus the standing no-browser non-goal — so
`visualSnapshot: false` stays correct and `elsewhere.visualSnapshot` keeps pointing at the command.

**A slug is not a front end, and a site's URL field has three states (SH442).** `snapshot --page
post:Viglet2/ --against <source>` failed with playwright's own `Cannot navigate to invalid URL`, call
log `navigating to "viglet2/"` — no scheme, no host — and `--preview` made the same run work, which
located it. `resolveUrl` chose front end or preview on `if (options.preview || !origin)`: the
**truthiness** of the site's URL field rather than whether it is a URL. The field holds *absent*,
*absolute*, and *a slug* — the last being the default for any site created without a public URL — and
a slug is truthy, so the front-end branch handed it to the browser as an absolute address.
`Bootstrap` holds `https://getbootstrap.com/` and worked; `Cafeteria` holds `cafeteria` and did not.
SH265's intent was never ambiguous and this predicate was the one place not implementing it, so the
test is the one `resolveUrl` already applied to its *target*, now `isFrontEnd` in its own module.
**The same predicate was in `report.mjs`**, where the output is worse rather than better: `!origin` is
false for `cafeteria`, so the handoff composed `cafeteria/blog/hello` and printed it as a preview
link. A broken link presented as a working one beats no link only until somebody clicks it. Both now
refuse with a sentence that names the slug — "no front-end URL configured" is simply false of a site
whose field holds one, and a reader who believes it goes and sets the field it already has. **The
fixtures are the fix as much as the predicate is**: the SH265 tests encoded two states while sites
have three, and nothing else could have caught the gap — captures are injected, so no test hands a
URL to a browser, and a real run needs a `playwright` the checkout cannot resolve (SH444), so every
one of them refused earlier for that instead.

**`--checks replication` is the second server-free group, and the gate that says when a replication is
*done* (SH387).** SH386 answers "does this page look right" one page at a time; the other half of done
is arithmetic — of the pages, links, assets, form fields and locales the source had, which ones exist
here. It is a **group** and not five more `projection` rules because every rule needs a *second
document*, the SH377 capture, and asks a coverage question rather than a structural one; both groups
still skip the server entirely, which is what makes them affordable on every push. Six rules, and the
one that matters most is not a coverage rule:
- **`replication-unchecked`** — a warning that names itself, in four shapes: the capture was
  **truncated** (so every count is a lower bound, and a clean run means "complete among the pages we
  saw"), a source page the crawl **could not read at all** (a 500 is the source's defect, not the
  replica's), a locale whose selection the capture could not place in a URL, and an inventory written
  by a **newer** `shio clone` than this reader understands. An unchecked thing counted as a clean one
  is how a coverage report starts lying, and a coverage report nobody trusts ends the replication with
  a number instead of with the work (SH302's rule). **SH431 removed the fourth shape's largest false
  positive, and it lied the other way**: capturing `openviglet.github.io` reported *"could not be read
  (24)"*, every row a `200` with `not html (application/json)` — `catalog-v1.json`, `badge.json` and
  twenty-two more, the site's data surface, linked on purpose and served correctly. They are not pages
  and the crawl did not fail on them, so a complete capture came back with two dozen phantom gaps and
  every count downgraded to a lower bound on the strength of them; two dozen unfixable warnings on a
  first run is how that trust goes. A served non-HTML `200` is now its own list — `data`, with the
  content type as a **field** rather than buried in a `reason` string, and its own heading in the
  report — reported because a data endpoint may well deserve replicating, never counted as an unread
  page, and still in the visited set so a link to one is not an unresolved link either. `problems`
  keeps the failures, which is the case the rule exists for. `INVENTORY_VERSION` is **3** for it, and a
  version-2 capture gets a note naming *which* count is the blunter one — the version messages are now
  per version (`unfollowed` arrived in 2, the split in 3) rather than one message keyed to the newest,
  because a reader told only "this file is older" has a fact it cannot act on.
- **`source-page-missing`** — SH385's `source-url-abandoned`, **moved into the group that owns it and
  renamed**. It was the right check in the wrong place: a coverage question about a source path is this
  group's whole subject, and leaving it in `projection` while adding a second page rule here would have
  been one defect in two reports that can disagree. A `Redirect` still needs no special case, since it
  *is* a post whose `url` is the path it answers (SH385) — "served or redirected" is one lookup.
- **`source-link-unresolved`** — and it required a **change to the capture**: `INVENTORY_VERSION` is 2,
  with an `unfollowed` list of internal targets the walk saw and never crawled. Every *followed* link
  is already a `pages` row, so reporting it here too would be the two-reports defect again; what was in
  neither list is a link cut by `--depth`, `--max-pages` or robots — and before SH387 a fidelity report
  counted that as absent from the **source** rather than absent from the **capture**, which are
  opposite claims. The version moves rather than the field being optional, because a version-1 file
  read as "nothing was left uncrawled" is exactly the wrong way round for a coverage report; an older
  capture gets a note saying which rule checked nothing.
- **`source-asset-missing`** — never downloaded, or downloaded and then lost, as two messages under one
  rule because the fixes are different commands. **Off-origin assets are not reported**: SH378 settled
  that mirroring another host's bytes is a licensing decision, so their absence is the design.
- **`source-form-field-lost`** — and the claim is bounded *in the fix text*, because it is easy to
  over-read: there is no form storage in this product (SH247 renders a declaration), so "the field
  arrived" can only mean some post-type **declares** a field of that name.
- **`source-locale-missing`** — where a smoke run caught the defect worth keeping: the projection writes
  `locale:` only for a **non-default** locale (SH348), so a monolingual English tree states its locale
  by *omission*, and reading omission as absence reported `en` missing on a tree whose only page was
  English. Which locale the default is cannot be known server-free, so **the capture answers it** —
  `declared` is the source's own `<html lang>` — and the inference is put in the notes rather than made
  silently. With no primary locale in the capture, the note says the language is unproven instead.
Every finding hangs on `source:<origin>` rather than an invented `post:` address, because the subject
of every rule is something that does *not* exist in the replica and a fabricated address is one
`shio resolve` cannot resolve. Rows are **capped at 20 with the total in every message** — not in a
note three screens down, which a reader has not been told by. The group is **silent on a scoped run**:
the findings are absences, and SH143's rule bites hardest for those.
**And one bug the smoke run found, worth the sentence:** `splitChecks` took the local-group list as a
*parameter with a default*, and the default was the one group that existed before this task — so
`--checks replication` filtered nothing out, decided the server had been asked for, and failed with
`fetch failed` on the gate whose whole promise is that it needs no instance. It is a constant now,
beside the two names it is made of.

**A row's `on` is distinct pages, and `occurrences` is every sighting (SH432).** It used to be one
counter incremented per sighting and printed as pages: a capture of **one** page reported
`navigation (3)` with `on 2 page(s)` against all three items, because the source repeats its links in
a topbar `<nav>` and again in a footer `<nav>`. A navigation item's reach is the whole argument for
curating it as content — a link on every page is a menu, a link on one page is a link — so doubling it
wherever a site repeats its nav (most sites) makes the number say the opposite of what a reviewer
needs, and unlike a truncated count it cannot be read as a lower bound: a count above the pages
captured is not conservative, it is wrong. The pages are a **set** now, `occurrences` is kept because
"this link appears twice on every page" is itself a fact about a menu, and the report prints the second
number only when it differs. **`forms` had the identical defect** one field along — its own comment
said "how many pages carry it" — and is fixed in the same pass. No `INVENTORY_VERSION` bump, unlike
SH431, and the reason is the test of whether a bump is owed: nothing *consumes* `on` (the coverage gate
reads `forms` for its fields, never for its counts), so no reader can misread an older file through it.

**A `File` field holds an id, and the measurement is what chose that (SH471).** `propose` writes
`fields.image = <img src>` — the source's own path — and the authorable path stored that string in a
`File` field, which the layout draws with `{{file_url image}}`. That helper resolves a **post id** and
answers `""` for anything else, so a converted `SectionHero` rendered `<img src="">` and SH465's two
row fields inherited it. The task offered two fixes and asked for a measurement first; the measurement
settled it on an axis neither option was drawn on. **The authorable path was uploading no bytes at
all** — `sendAssets` ran under `if (!planOnly && verbatim)`, an SH437-era guard from when only
fidelity markup referenced the source's paths — so teaching `file_url` to pass a non-id through would
have emitted `src="/img/hero.png"` against a site that serves nothing there: a broken image that
*looks* like a fix. Three more facts finished it: a non-id in a `File` field is exactly the
`dangling-reference` shape SH470 had just removed; `{{#image}}` (SH390) reads `WIDTH`/`HEIGHT` off the
`File` post, so a path leaves `srcset` permanently empty on the replicas SH386 grades for fidelity;
and the console's File widget resolves ids. **So the bytes go up on both paths and the id is stored.**
`sendAssets` now runs **before** the apply — the post must exist before a field can name it — carries
`createSite` on its own folder-apply so a `--create-site` run keeps working, and returns a
`source path → File post id` map built from the listing and the upload response, which were already
being read. `withResolvedFileFields` rewrites only the fields `SECTION_FILE_FIELDS` declares: matching
any string equal to an uploaded path would be shorter and wrong on a case this vocabulary has — a
`SectionLogoWall` row carries both `image` and `href`, and a logo linking to its own artwork would have
had its **link** turned into a post id. A reference the capture never fetched is left as written and
**counted in the report**, and a fidelity page's markup is untouched (SH429's option A). The duplicate
list is closed by a check rather than a comment: `ShConvertedLayoutRenderTest` reads the blueprints and
requires every `File` field to be named in the CLI's map — which is owed, because that same test
*exempts* `File` fields from its coverage assertion and would otherwise never notice.

**A replica that photographs identically and does not work (SH469).** The replica of `www.viglet.org`
is 0.33% from the original by pixels and **inert**: the dark/light toggle does nothing and the consent
modal will not close, because both are React handlers and the bundle is gone. Measured — the source
carries four `<script>` tags, one of them `<script type="module" src="/assets/index-LNwgBwpz.js">`; the
replica carries one inline script and no `src` at all, and no `.js` exists in the asset tree.
`DOWNLOADABLE` is the list a replica needs to **render**, and `script` was never on it; `SiteScripts`
did capture two scripts and both were **third-party**, so the site's own bundle was the one thing
dropped. Nothing said so: the clone reported `rendered`, the proposal was silent, `verify` was silent,
the snapshot said 0.33% — every instrument agreed, and every one of them measures the photograph.
**Two halves, and only one of them is a flag.** `BEHAVIOUR = {"script"}` is a second list beside
`DOWNLOADABLE`, taken by **`clone --assets --scripts`** and off by default — because `--render` bakes
the post-hydration DOM into the captured markup, so handing that page its own bundle back asks the
framework to hydrate markup it did not just produce. That is a decision, like off-origin bytes are
(SH378 is untouched: `--scripts` never mirrors another host's JavaScript). **What is not optional is
saying it.** A skipped script gets its own reason — `behaviour, not captured without --scripts`, never
`not a renderable asset`, which is true of a `.docx` and says the wrong thing about a bundle — and
`withDownloads` writes a note in behaviour's terms: *N script(s) were NOT captured, so the replica will
render and not respond*, with how many are the site's own and the flag that takes them. The counting
was always there; the crawler saw the tags in order to skip them.

**And none of it ran, because no capture could produce the row it acted on (SH473).** `planAssets`
walks `inventory.assets`; `scanPage` put a `<script src>` only in `doc.scripts`, which
`buildInventory` filters to **off-origin hosts** because that is SH389's third-party list. So the
site's own bundle was recorded nowhere a downloader reads: `--scripts` had nothing to take, and the
`withDownloads` note — which fires on a *skipped* script — had nothing to name either. The fix is one
`addAsset` in the `script` case, unconditional: the inventory records what the page references and
`planAssets` is the one place that decides what is fetched. Measured on `--depth 1 --render --assets
--scripts` of `www.viglet.org`: **19 files, 1.7 MB, `/assets/index-LNwgBwpz.js → Viglet/assets/…`**,
where the same run without the flag writes 18 and says *1 of them belong to this site* — a sentence
that had never been printed. One consequence, on the gate: a same-origin script left behind by
decision now carries `unavailable: BEHAVIOUR_SKIPPED`, and `assetFindings` skips it, or the operator
reads the same fact twice with a `--assets` instruction on the second copy.
**The instrument is coverage between the two halves**, because this was the third task of its shape
(SH452's note before its download, SH455 to finish it, SH469's report before its fetch):
`clone.test.mjs` scans one page referencing one of everything and asserts every kind in
`DOWNLOADABLE ∪ BEHAVIOUR` is a kind the scanner produced. A kind added to a fetch list with no path
through `scanPage` fails there, naming itself — which is exactly what SH469's own tests could not
catch, having hand-written an inventory holding a `script` row no capture could make.

**And the coverage that mattered was against the *other* half, not against a list (SH515).** That
lint asks whether the scanner can produce every kind the downloader fetches; the defect was that the
**rewriter** could rebase references the scanner had never heard of. Measured: three 404s and a
crash on a replica — `insightsData.find is not a function`, because `content/home-insights.json` was
served rebased and never downloaded, so the page parsed a 404 body. Both missing shapes were
markup-adjacent rather than markup: a **`style` attribute**'s `url()` (`background-image` is how a
hero reaches a page with no `<img>`) and a **`fetch()`** literal in an inline script, which
`stripNonMarkup` blanks — hence `scriptBlocks`, `styleBlocks`' twin, inline and same-page only. A
`fetch()`ed file is `kind: "data"` and joins **`BEHAVIOUR`**, not `DOWNLOADABLE`: a script requests
it and nothing else does, so without `--scripts` there is no bundle to ask for it. The instrument is
one fixture read from both languages — `cli/test/fixtures/references-page.html` holds the page and
its expected resolved set, `clone.test.mjs` asserts the collector produces it, and Java's
`ShReferenceParityTest` asserts the rewriter resolves the same set. It failed on its first run, on a
disagreement neither side would have reported: a `<link rel=stylesheet>` was an asset here and a
navigation there.

**An element boundary inside a link is a separator, and it used to be nothing (SH468).** A captured
navigation carried `DuDumont DEP`, `ShShio CMS` and `TuTuring ES` — the label with a two-letter prefix
fused onto its first word. The markup explains it exactly: the anchor holds a 28×28 brand tile
(`<div aria-label="Du">…<span>Du</span></div>`) followed by the text `Dumont DEP`, and `scanPage`
accumulated `anchor.text += token.text` with no separator — `collapse()` then normalised whitespace
that was never inserted. It is not about badges: the tokenizer emits one text token per run, so
**every** append after the first crosses an element boundary, and any inline element adjacent to text
fused the same way. The nav label is where it surfaced because a label is short enough to read; a
heading carries the same corruption unnoticed, and those become post titles. **A word that was never
in the source is worse than a dropped one** — `Dumont DEP` and `DuDumont DEP` both look like content,
so nothing downstream can tell which is the source's: not `verify`, not a digest, not a reviewer
skimming a menu, and the capture is supposed to be evidence about somebody else's bytes.
SH468 separated **unconditionally** and named the alternative — a list of inline tag names — as a
heuristic CSS could contradict per page. **The instrument is an independent oracle**, not four
assertions: `clone.test.mjs` strips the fixture's tags and asserts that every word of every extracted
label is a word the page actually spelled — a different mechanism from accumulating text tokens, so it
fails on the next fusion whatever causes it.

**The stated trade was the bug, and the narrower rule is the one the DOM already implies (SH472).**
`DuDumont DEP` became `D u Dumont DEP`: the mark is not `<span>Du</span>` but
`<span><span>D</span><span>u</span></span>`, one span per letter because each carries its own glow
delay, and two sibling runs inside one inline element are one word. So `INLINE_TAGS` —
`span em strong b i a code sup sub small` — and `anchor.gap`, a flag set by any open **or** close tag
outside that set and cleared by the append it separates. A capture has no computed style, so the tag
name is the only evidence there is; the set is fixed, so the same bytes still yield the same record
(P5), and the determinism SH468 was protecting is not what a tag list costs. What it does concede is
`<em>Open</em>Source` → `OpenSource`, which is the point rather than the price: one word to a reader,
one word to `innerText`, one word here. **The oracle moved with it** — "a word of the page" now means a
run between two boundaries a reader sees, so it deletes inline tags rather than replacing them, with
`a` the exception, because the accumulator never carries text across `</a>`. Measured on a rendered
`www.viglet.org`: the nav reads `Dumont DEP`, and the product cards read `Du Dumont DEP …`.
Left open on purpose, as the task framed it: whether a mark whose `aria-label` equals its own visible
text should be in a label at all is a decorative-content question, not a boundary one — SH477.

**And the answer is that it is not a label, on a signal in the markup rather than a heuristic
(SH477).** `Du` is a 48×48 tile whose letters are *drawn*; it carries `aria-label="Du"` over visible
text that is also `Du`, and an accessible name repeating what is on screen is the author saying the
element is decorative. So inside an anchor, an element whose collapsed text equals its own
`aria-label` contributes nothing to the link's label, and `Du Dumont DEP` is `Dumont DEP`. Three
things fix the width of that rule. The comparison is against the element's **whole** text, not one
run, because the mark is two spans and neither of them spells `Du`. `DEP` sits in the same string —
same length, same case — so "drop short runs" and "drop uppercase runs" are refuted by the fixture,
and neither is the rule. And an anchor whose *only* text names itself keeps it: there the visible
words **are** the label, and an empty label is indistinguishable from a link that never had one.
**The count is the other half of the deliverable.** A rule read off one site's markup is a rule that
fires on nobody else's, so `scanPage` reports `selfLabelled` per page and `buildInventory` turns it
into a note — every capture says how often the rule was right about *its own* source, and a total of
zero is as much a finding as a total of forty. The field is optional and not an
`INVENTORY_VERSION` bump, on SH592's reading: nothing consumes it. `VOID_TAGS` moved to `clone.mjs`
and `propose.mjs` imports it, for the reason `tokenize` is injected — a tree builder and a depth
counter reading one stream must agree on which tags open anything.

**One test drives the whole pipeline, and it is the only one that can (SH422).** Block Q shipped as
eleven tasks, each thoroughly tested in isolation — and every phase hands an artefact to the next, so
each handoff is a shape agreed in two places with a unit test pinning one side and *constructing the
other by hand*. That is the shape that passes while the real producer writes something else.
`replication-chain.test.mjs` therefore runs `clone --markup --assets` → `propose` → `propose --accept`
→ `verify --checks projection,replication` over one served fixture site, asserting at each step that
what the last phase **wrote** is what this one reads: the inventory row naming a markup file that holds
that page, a candidate whose `pages` are the crawl's own paths, a `url()` rewritten onto the source path
the capture downloaded (so the asset map is *the* map and not a copy), the accepted id coming back
`authorable` from the plan on disk, `INVENTORY_VERSION` equal to `READS_VERSION` across the two
modules, and the `unfollowed` rows — one cut by robots, one by `--depth` — becoming
`source-link-unresolved`. Two harness choices are the point rather than convenience: a **real temp
directory**, because a fake filesystem is right for a phase and wrong for a chain (the artefact is the
assertion, so the test must not be what decides it), and still **no instance and no browser**, which is
a property of the pipeline — `snapshot --against` is the one step that needs one, and its comparison is
already driven through an injected capture.

**`shio <command> --help` prints that command's own flags, and the global help stopped describing
them (SH435).** It used to print the same global text for every command — `main` answered
`flags.help` *before* `loader()` ran, so no command was ever consulted about its own flags, and
learning that `clone` takes `--markup`, `--assets`, `--max-asset-bytes` and eleven more meant
grepping the source for `flags.` after paying for a help text that answered something else. That is
a P3 cost, not a polish one: a six-command pipeline was six greps plus six wasted outputs. Every
command module now exports `HELP` — beside the `flags.` reads it describes, so a flag and its meaning
are one edit apart — and `printCommandHelp` loads the module to print it, with **no fallback to the
global text**, because a silent fallback is what hid this. `shio help <command>` is the same answer.
The instrument is the second test rather than the first: `help.test.mjs` derives each command's flags
from its **source** and fails the build for any the help does not name (122 flags across 23 commands
today, printed on every run), which is the check a hand-kept list cannot do — and it is what finally
documented `clone --url`, the one command where `--url` means the *target* rather than the instance, a
semantic that until now was declared in a test rather than anywhere a caller looks. The global help
fell from **357 lines to 58**: it names the commands, points at `shio <command> --help`, and keeps only
what is genuinely cross-command (the connection flags). Both halves of that are the same rule — the
same interface described in two places is the shape that drifts.

**The text lives in `src/help/<command>.txt`, not in the JavaScript (SH208).** Each module still
exports `HELP`, now as `helpText("<command>")` — a synchronous read at module load, and commands are
imported lazily, so `shio verify` reads one file. The reason is that help text is prose *about a CLI*,
prose about a CLI names commands, and command names attract backticks — which inside a template
literal is a syntax error that stops **the whole binary** loading, not the command that was edited.
It happened three times in three sessions, and each time the only tests that noticed were the three
that spawn the CLI as a subprocess; every other CLI test imports the command modules directly, so a
green suite ran against an unparseable entry point. **The pair case is worse and is why an escape
rule was not enough (SH377):** two backticks parse fine and silently *truncate* the help — 149 lines
instead of 200, `Common options` and everything below it gone, no error anywhere. `String.raw` is not
a fix and `help-source.test.mjs` says so in an assertion: it changes how backslashes are read and has
no effect on the delimiter. That lint refuses any `HELP` that is a template literal again, and checks
every `helpText(...)` has a file behind it — a missing one throws at load, which is the same "the
binary does not run" failure in a different costume.

**The optional browser is resolved before anything else, and one module owns the refusal (SH434).**
With `playwright` absent, `snapshot`, `snapshot --against` and `audit` printed the intended teaching
error and then emitted `Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)` and exit
**-1073740791** — `0xC0000409`, `abort()`. `main().catch` calls `exit(1)`, so the intent was right and
something live was still armed: the import sat *behind* work that had already run — the instance had
been asked for its sites, and `clone --render` had fetched robots.txt from somebody else's server. A
refusal that crashes is not a refusal. `--against` is the one command whose gate is on by default
(SH386), so a caller cannot tell "the replica differs" from "no browser installed" when both are a
crash, and a native assertion under a Shio command reads as a Shio defect. So `browser.mjs`'s
`requireBrowser` is called **first** in all three — and in `clone --render`, which had the same defect
one grep away and whose own SH378 comment already stated the rule ("before any request at all,
including robots.txt") one line above the import that broke it. Three copies of the message became
one; each command still supplies its own browser-free alternative, which is *why* the dependency is
optional. What it deliberately does **not** pre-empt is a package installed without the browser: only
`chromium.launch()` knows, and refusing from a path would risk telling a working install it is broken.
`browser-refusal.test.mjs` pins the property that is checkable — **zero requests before the refusal**,
with a `post:` target so the old order would show — and the real exit code from a child process,
skipped where `playwright` resolves rather than passing on a different path.

**Every non-zero exit goes through `exit.mjs`, which disarms the request layer first (SH440).**
SH434 closed one route to that abort and left the exit alone, so every *other* thrown remote error
still took it: `convert --site <absent>`, `verify --site <absent>` and `resolve post:<absent>/x` each
printed their teaching error and then `0xC0000409`. The boundary named the cause — a malformed
address, rejected locally with no request, exited 1 cleanly; `push --check` with real drift, which
*returns* a non-zero `exitCode` and unwinds normally, was fine too. What crashed was an error
**thrown** from a path that had already made an HTTP request, because `main().catch` called
`process.exit(1)` with a fetch handle still live. It matters most where the exit code *is* the
product: `convert --check`, `apply --check` and `push --content --check` are drift guards whose whole
output is a number a pipeline branches on, and a crash code cannot be told apart from a runner
killing the process — non-zero being the safe direction is why it sat unnoticed. `finish(code)` now
closes every versioned `undici.globalDispatcher` slot on `globalThis` (Node 26 carries `.1` **and**
`.2`; the sweep matches the prefix, so a renumbering cannot silently leave the layer armed) and then
exits. Two measurements shaped it, both on the idle keep-alive socket a thrown HTTP error leaves
behind: `process.exitCode = 1` and return is **not** the fix — it exits 1 after **4082 ms**, undici's
`keepAliveTimeout`, and a request the server never answers holds the loop with no timeout at all;
disarming first took **79 ms**. `exit()` stays because it is what guarantees termination when
something other than the request layer holds the loop, and after the disarm it is safe. The success
path deliberately does not come through here — `process.exit(0)` truncates a pending stdout write on
a pipe, and `shio report --json` is a large one. `exit-path.test.mjs` pins the ordering against fake
dispatchers, the real binary's code *and a two-second deadline* (the deadline is what fails the naive
fix, which returns the right number four seconds late), and — the instrument, per SH322/SH338 —
that **no second `process.exit` exists anywhere under `cli/src/`**: the defect was one call site
reached from many commands, and the next one added beside a command would be invisible until somebody
read an exit code outside a shell, which is exactly how it survived SH434.

**And then it did not call `process.exit` at all, which is what actually fixed it (SH445).** SH440
shipped and the crash was still there: measured against a live instance at the shipping revision,
`resolve post:<absent>/x` and `verify --site <absent>` each printed the teaching error, then the
libuv assertion, then **-1073740791**. The disarm was not the problem and was not useless —
`getActiveResourcesInfo()` goes from `["PipeWrap","TCPSocketWrap","Immediate"]` to `["PipeWrap"]`, so
the socket and the dispatcher really are gone. **`PipeWrap` is stdio**, and on Windows a
stdout/stderr connected to a pipe is *asynchronous*: `process.exit` can run with a write still in
flight, and libuv then signals that write's completion on a handle it is closing. That is why it
reproduces under a pipe and under redirection but not in a terminal, and why "write to stderr and
exit" is clean on its own — it takes a request **and** a pending write, the one combination no
harness had. So `finish` now disarms, sets `process.exitCode`, and **returns**; the disarm is what
makes returning affordable (SH440 measured `exitCode` alone waiting out undici's 4 s
`keepAliveTimeout`, and there is nothing left to wait for once the layer is down), and returning is
what removes the abort. An **`unref`'d 5 s watchdog** covers the case SH440 kept `process.exit` for —
a loop held by something that is not the request layer — without delaying a healthy exit, because an
unref'd timer does not hold the loop. Measured after: exit **1** in ~130 ms against the stub and
~160 ms against the live instance. **The test is the deliverable here.** SH440's stub answers with
two headers and a short body, and the binary exits 1 against it *at the broken revision too* — which
is why SH440 shipped believing it was done. Bisected deterministically over three runs: two headers
exits 1, `set-cookie` added exits 1, and only the full live header set reproduces, so the trigger is
a threshold rather than any header's meaning and `exit-path.test.mjs` sends what the instance sends
without trimming it. Three tasks in a row — SH434, SH440, this — were measured against a harness
rather than against the failing command; the rule that falls out is that a fix for a crash owes a
harness that **fails first**.

**The instance already knew both of them, and no command said so (SH587).** `GET
/api/v2/agent/diagnostics` answers with the exact incident SH544 was filed from — the column, the
truncated value, `count: 2`, `firstSeen`/`lastSeen`, a `fix`, and the frames — and that run was
diagnosed instead by locating `store/logs/shio.log` and grepping a stack trace. `shio remember` held
four notes on the same instance, one naming the `site.postTypeLayout` binding, and twelve
replication rounds read none of them. Neither is a missing capability, which is what makes it **P2**
rather than a feature request: *discovery is a product feature, not a docs problem*, broken by the
product that declares it. So `format-error.mjs` adds one line to a **5xx** — `the instance recorded
this: GET /api/v2/agent/diagnostics` — and only to a 5xx, because a 4xx carries `fix`/`allowed`
under P6 and has already said what to do, while a 500's response body is gone by definition and the
endpoint is the only place its cause still exists. The second half is `instance-hints.mjs`: before
any instance-facing command runs, the **count and the keys** of this instance's notes go to stderr
(stdout is somebody's `--json` pipe), never the prose — a key is enough to decide, and P3 owns the
rest. Silent when there are none, when the instance is down, and when the credentials are wrong: a
command that opens by complaining about a request it made on its own initiative is worse than one
that says nothing, and its own error a moment later is the honest report. **The notice lives at the
dispatcher, and the classification is the deliverable.** Eighteen modules construct a
`ShioAgentClient`; wiring a notice into each is eighteen edits and eighteen chances for the
nineteenth command to be written without one, which is SH322's shape exactly. `index.mjs` is the one
place every command passes through, so `NOTES_COMMANDS` and `NOTES_EXEMPT` — the latter carrying a
*reason* per entry, because "why does `clone` not print notes" is not derivable from the code — are
the whole decision, and `instance-hints.test.mjs` checks both against `index.mjs`'s own registry in
both directions. A new command cannot be registered without taking a side, and a side taken for a
command that no longer exists fails too.

**A version bump on the inventory is a migration or a refusal (SH592).** `shio clone` over
`vilt-group.com` with `--render`: 198 pages, 403 assets, 52.7 MB, about ten minutes — by far the
most expensive step in the chain, and the only one whose output was **immutable**, since everything
after it reads the inventory off disk and runs in seconds. Twice in this work that asymmetry cost a
full recapture for a reason that had nothing to do with the source: SH578 changed the asset shape
and `diff` refused every capture written before it (an excellent refusal, naming the version and the
remedy, which does not make the remedy cheaper), and once the source had not changed at all and a
downstream one-line fix needed a capture the new reader could parse. **The information was already
in the file.** SH578's `{own, style}` split and SH581's `head` are all derivable from the stored
markup, which `--markup` keeps beside the inventory precisely so `propose` can read it — so the
refusal was never about missing information, it was about nobody having written the derivation, and
the cost of a reader's change was paid in somebody else's ten minutes. `inventory-migrate.mjs`
brings a version-3-or-later capture forward **from the bytes it already stored**, using
`scanPage` + `countAssets` — the same functions the capture runs — so a migrated row and a fresh one
are computed by one rule and cannot disagree. **What it still refuses, and why that is the honest
half:** version 1 → 2 added `unfollowed` and `problems`, and neither is derivable, because the crawl
did not record what it declined to follow and an absent list read as an empty one says *nothing was
left uncrawled* — a bounded sample reading as complete, which is SH431's failure. So the refusal
names the **field** and why it cannot be computed, rather than a version number. A page whose markup
is not on disk is reported as one the migration could not derive and is **left as it was**, never
guessed. The migrator is pure and takes a `Map` of markup: the derivation is not IO, and the command
reads through `ctx.fs` like everything else in this tree, because a `node:fs` call inside a command
is a seam no fixture can reach (SH198).

**`shio clone --refresh` is the other half, and it needed no version bump after all.** The note that
stood here predicted one: `--refresh` has to record an ETag or a Last-Modified per page, and that
looked like a shape change. It is not — the fields are **additive and optional**, no reader wants
them, so SH592's own rule settles it in the other direction: a bump is a migration or a refusal, and
this is neither. A capture written before they existed simply carries none, its first refresh is
unconditional, and every later one is cheap. Requiring a migration to *gain* a field nothing reads
would have been the rule applied without its reason.

What makes a refresh cheap is not a digest. Comparing hashes still costs the download, and under
`--render` the download was never the expense — the browser is, at up to `--timeout` a page. So the
saving comes from the server declining to send anything: a conditional `GET` carrying the recorded
validators, answered **304**, is a page that is never rendered, never re-scanned and never
rewritten. A 304 has no body, so the row is rebuilt from the **markup already on disk** — the same
insight one level along, which is why `--markup` is a hard precondition and why the refusal for a
capture without it names the flag. Passing no URL is deliberate: the capture says where the site is,
so a mistyped origin cannot refresh against a different host. The two halves compose — a capture one
version behind is migrated from the markup `--refresh` has just read, so refreshing is also the
cheapest way to pay off a bump.

**One thing it does not do, reported rather than hidden.** It does **not walk for new pages**: a link
that appeared on a changed page is counted and named in the notes and never followed, because
following it is a crawl and a crawl is what is being avoided — and a refreshed inventory that
silently omitted new pages would read as a complete capture, which is SH431's failure exactly.

**And the assets are re-checked by byte, which is the larger half (SH611).** This paragraph used to
name a second gap: an asset was re-fetched only when the page referencing it changed, so a
stylesheet, a font or a hero image that moved under a still page was silently the old one, and the
report's only answer was *run `shio clone`* — the ten minutes the flag exists to avoid. By volume it
was the bigger of the two: 198 pages and **403 assets**, and the assets are where the 52.7 MB is.
An asset row now carries the same optional `etag`/`lastModified` a page row does and is asked about
on **its own** validators, so a 304 means the file already in the tree is still right. Strictly
simpler than the page case, which had to rebuild a row from the markup cache — here nothing is
rebuilt, because the bytes never left.

Four decisions, each one a way it could have lied.

- **`unchanged` is its own action and its own count.** The vocabulary was `wrote`, `deduped`,
  `failed`, `collision`, `skipped`, and it is read in three places — `OUTCOME_OF_ACTION`,
  `withDownloads`, the report. Folding a 304 into `wrote` would report bytes that never moved; into
  `skipped`, which means the plan declined to fetch it at all. On the capture that motivated this,
  *"371 unchanged"* **is** the report.
- **The placement is carried, never recomputed.** A 304 asset has no digest this run, so
  `placeAsset`'s dedupe state is seeded from the prior row — otherwise the next asset carrying those
  bytes writes a second copy under its own name and SH210's *a path names one thing* is broken by the
  cheap path.
- **A 304'd stylesheet is re-read from the tree.** The inventory's asset rows are rebuilt from the
  pages on every refresh, so SH452's `url()` walk has to happen or the fonts a still stylesheet
  references quietly leave the capture — a capture that shrank because nothing changed.
- **A 304 is about the server's copy, not ours.** It is honoured only if the file the last capture
  placed is still under `shio/assets/`; otherwise the asset is downloaded unconditionally and the run
  says why it paid. That is the one thing that can make a carried row a lie, and an inventory naming
  a file that is not there is worse than one that re-downloaded it.

A prior row is asked about conditionally only when it has a **placement**, a **validator**, and no
`unavailable` — three conditions ruling out three different lies, the third being that asking about
a row the last run recorded as failed would turn a known gap into a silent one. Validators are
recorded on every 200 whether or not the run is a refresh: the run that makes the next one cheap is
the *ordinary* capture, so a validator nobody stored is a request nobody can avoid.

**`shio judge --site X` — one verdict, and the disagreements that produced it (SH589).** Judging a
replica meant running `verify`, `diff`, `audit` and `snapshot`, reading four reports in four
vocabularies, and knowing which answers which question. Round eight: `verify` 0, `audit` 0, `diff`
1. Round twelve: `verify` 0, `diff` 1. Both ended with a real defect and neither ended with a number
anybody could act on. **The composition is the easy half.** The half worth building is that it
prints *where they disagreed*, because that is where every finding of this work came from — SH521
(the gate said `ok` on a page `/agent/render` had already scored at nine unstyled classes), SH554
(`diff` reported a loss `verify` could not see, and `diff` was wrong), SH577 (`diff` red, `verify`
green, and the mode was the reason), SH586 (`diff` red where `verify` was clean, and the links were
leaving the site). Four for four. A disagreement is not noise to be reconciled; it is the
highest-yield signal the chain produces, and it reached a reader only if they happened to run both
and notice. **Three rules the implementation is:** it never averages — a green from three and a red
from one is a **red**, and the report names which and on what; an instrument that did not run is
**unproven and never green**, printed with what it *would* have answered, because "we could not
check this" and "this is fine" are different answers (SH410's rule, SH588's again); and an
instrument only disagrees about a subject it **covered**, since silence from a run that never
looked at a page would invent a disagreement out of its own scope. The two vocabularies —
`verify`'s SH79 address and the other three's served path — normalise to the path, including
`post:<site>/` → `/`, or a disagreement is keyed on two strings for one page and nobody sees it. It
**composes** rather than forks (P10): each instrument is its own command's `run` with a captured
`stdout`, so a rule that changes in `verify` changes here, and `--verbose` prints the four reports
underneath because a disagreement is a pointer at two of them. `verify` and `diff` always run
(browser-free); `audit` is `--browser` and `snapshot` is `--against <origin>`, both bounded by
`--pages` with the bound **declared**, because choosing a baseline for the caller would be inventing
the thing whose absence is the question. `judge.mjs` is pure — the reconciliation is what has to be
asserted, and a browser must not be needed to test it.

**`shio annotations <page>` — the measurement that kept being rewritten (SH588).** `SKILL.md`
carries seven checks for judging a replica. Two are verbs, `verify` and `diff`; the other five are
paragraphs, and the most-used is *count, do not eyeball*: take the props a page emits and compare
them against the fields each section type declares, **including every collection row**, and **in
both declaration forms**. That procedure was hand-written four times across four rounds, slightly
differently each time, and **once it was wrong** — a complete page read as *9 of 10* because only
`data-shio-prop` was counted, and a regression was nearly filed against SH503 on the strength of it.
The rule about counting both forms exists in `SKILL.md` *because* of that, which is the definition
of a procedure that cannot be left to a reader. So: props by field, both forms, rows expanded, each
attributed to the post that owns it, joined against what that post's type declares, exit **1** when
a declared field is rendered without an annotation. **It is SH524's question asked at the page
rather than at the template**, and the two are worth having apart — SH524 finds a field no template
draws, this finds a field a template draws and does not make editable, which is how SH465 and SH475
both shipped looking reasonable. Three properties the implementation turns on: ownership is by
**containment** (the renderer's own rule, and getting it wrong turns "the grid's two cards carry
none" into "the page has plenty"); **more annotations than declared fields is expected**, since an
empty slot is annotated so a curator can fill it and one field may be drawn twice; and a type that
cannot be resolved is reported **`NOT checked`** rather than as complete, because "we could not
check this" and "this is fine" are different answers (SH410's rule, one surface along). **No
browser**, unlike `audit` — the annotations come off `ShRegionComposer`, so they are in the bytes
`?editor=true` already returns, and paying for Chromium to answer a question settled before any
script runs would make the cheapest check in the chain the most expensive to run.

**`shio impact <address>` exists and `shio read` deliberately does not (SH288).** The task was
filed as "the CLI cannot show one post", and only half of that is a gap. A post's **content** is on
disk after `pull --content`, where `Grep` and `Edit` are the cheapest tools an agent has (P4), so a
`read` command would be a worse version of a file: it costs a request, cannot be diffed, and is stale
the moment it prints. The **blast radius** is the half no file covers — it is computed from the site's
`postTypeLayout` bindings and the slots in the layout's HTML (SH193), never stored in the post — so
`include=impact` (SH262) had no terminal path at all. One command, one question: it prints kind,
title, the layouts, the post-types and the draft/published page counts, or says *"is not a template"*
for an ordinary post rather than failing, since a caller sweeping a mixed list should not have to know
which addresses are templates first. `--json` prints the section (`null` for a non-template) so a
pipeline can branch. An unused template prints `layouts: none` and exits 0 — that is `verify`'s
finding to make, and a mid-build state is not an error here.

**The handoff report (SH104) is `shio report`, and it is deliberately CLI-only.** It composes
three existing reads — `changes` (what moved), `context?include=sites` (each site's public URL,
the only field it needs) and `verify` (what is unresolved) — into Markdown for a PR body. **No
`GET /agent/report` and no MCP tool**: a fourth contract restating three would put *presentation*
on the server (P10), and the artifact's consumer is a **human**, so per-turn tool schema would be
paid for something the agent hands to someone else. Four rules it must keep: a **deletion is a
row** (no address, no link — the row an implementation that builds the list out of links drops,
and the one a reviewer most needs); a **preview link is never invented** (it names which half is
missing — the site's URL or the post's friendly URL — because a plausible link that 404s costs
the reader their trust in the rest of the document); the **warnings are the open questions**
while errors are only *counted* (they belong to the agent's own loop, and restating them makes a
second copy of the lint report that drifts); and **`--mark` refuses to advance its cursor past a
range the report could not cover whole** — a read must not move a position as a side effect, since
running it twice would answer "nothing changed", and for a review artifact an empty report reads
as approval.

**CI gates, in the order they should run:** `shio verify --checks projection,replication` (tree defects
— **no instance**, SH212/SH374: a broken asset path, a `files:` entry naming nothing, a dangling
`translationOf`; plus SH387's coverage against the capture, on a project that has one) → `shio push
--check` (model drift) → `shio push --content --check`
(content-tree drift) → `shio apply --check` (document drift) → `shio verify` (content defects)
→ **`shio diff --site <name>` wherever a capture exists** (SH548 — the replica against the
declaration it came from).
The first one is new and is first for a reason: it needs no credentials, no network and nothing
stood up, so a defect the files alone prove should never wait for a gate that does. **`projection`
is in `verify`'s default set** and is stripped from what the server is asked for (its vocabulary
does not contain it, and SH320 makes an unrecognised `checks` value a teaching 400) — so the last
gate runs it too, and naming it *alone* is the server-free one. It cannot newly fail a tree that
was passing: the same defect already stopped the pipeline at `push --content`, as an exception on
the first offender rather than a report of all of them. `--delivery` with `projection` alone is
refused, because one asks for a proof that leaves the process and the other for the group that
cannot.
**And `diff` is last and is in the list at all because of SH548: the one command that caught the loss
was the one no gate ran.** On its first real run it worked — twelve pages against the capture that
declared them, **35 facts lost**, internal links 16 → 0 on eleven of them, text 7073 → 1819 on the
home page, exit 1. On the same site `verify` exited **0** having warned `page-unstyled` on ten pages
and `audit` exited **0** having warned `no-landmark`; both warnings are true and neither is the
finding. The order named neither `diff` nor a replica, so the only red signal on a gutted replica was
one a person had to remember to ask for — which is what last round's four-instruments-pass finding
becomes when the instrument that *does* hold an expectation is outside the order that runs it. Its
condition is `wherever a capture exists`, which is the condition `diff` already tests for: with no
`shio/.shio/source-inventory.json` there is no declaration to judge against and the command says so.
**The severity question it raises is deliberately answered the other way.** A site whose every page
draws with classes no stylesheet defines is not warning-shaped, and the temptation is to promote
`page-unstyled` to an error — but the severity split is the *server's* and this CLI does not
second-guess it (the rule `verify`'s own header states), and SH521 could not tell a missing theme from
a deliberate one. `diff` can, because it knows what the source had. So the two reads together decide
what one could not: the warning stays a warning, and the run goes red on the instrument holding the
expectation. **`cli/test/gate-order.test.mjs` asserts both halves** — that the order above
names `diff`, and that the conformance run against a live replica actually invokes it — because an
order that exists only as prose is exactly how this one came to omit it.
**Accepted findings (SH142) live in `shio/verify-baseline.json`** — `(rule, address, field)` triples that `shio verify` subtracts, written by `--accept` (`--reason` optional) and merged, never replaced. The half that matters: **an accepted finding the run no longer produces fails the run**, so the file cannot rot into a blanket suppression that hides the next defect at the same address; a *truncated* report reports the unmatched entries without failing, because a finding that did not fit is not a finding that is gone. **Staleness is a claim about coverage, not about findings (SH410)** — "nothing matched this entry" only implies "the defect is gone" when the run *could* have produced it, and truncation was the first way to lose that guarantee and the only one guarded. Group selection and site scoping lose it identically and are the normal case: `--checks projection` sets `callRemote: false`, so every acceptance for a server rule matched nothing, was called stale, and **failed the build** — the server-free CI gate broken on any project holding a baseline, with `--checks content` breaking it in reverse and `--site Blog` condemning every other site. `applyBaseline` now takes the run's coverage and judges only what it could have produced; the rest is `staleUnknown`, which already meant "unproven" and already said so. A rule is **projection or the server's** — a third "unknown" bucket counting as covered would reinstate the defect exactly, since `required-field-empty` is not a projection rule. **SH411 then split the two claims apart**, because SH410 fixed a false positive by creating a false negative: it folded coverage into `staleUnknown`, which `verify` reads all-or-nothing (that flag used to mean *truncated*, a property of the whole report). One unproven entry therefore silenced every proven one — measured, a baseline holding `asset-missing` and `required-field-empty` run tree-only computed `stale: [asset-missing]` correctly and reported nothing — so the file could rot in exactly the way SH142 exists to prevent. `unproven` is now its own list on its own line and never fails the build; `stale` still does; `staleUnknown` is truncation's again. And **`PROJECTION_RULES` is checked against the rule literals in `verify-projection.mjs`** rather than hand-kept — it was enumeration where `path-safety.test.mjs` had already established discovery, and every rule in it was added by a task in the same run. It is deliberately a project file and **not** an instance setting — `GET /agent/verify` keeps reporting everything, and only the gate subtracts. **Since SH214 the same file carries a second list, `acceptedMoves`** — `(from, to)` pairs written by `shio push --content --accept`, for a delete-plus-create that is legitimate and would otherwise print an unprovable-rename warning for ever. Both addresses are the key (a one-sided one would silence the warning for a different arrival, which is the case it exists for), and the entry is **spent** by the push that honours it rather than checked for staleness: a probable move is transient, so SH142's rule would fail the *next* run for doing what was asked, and consuming it means an acceptance cannot outlive its event. Each command reads only its own list and writes the other's through untouched — without that, `verify --accept` would silently un-accept every rename somebody reviewed. The content gates read **only the sites the tree holds** (SH173, derived from `shio/content/<site>/**`), a post they cannot read by address is a named finding rather than a fatal 404, and a remote failure prints the SH80 body — the request URL is behind `SHIO_DEBUG`. **And a body that parses and teaches nothing is still what the server said (SH513):** `shio convert` died with one line — `shio: the server answered 400` — because the endpoint answered with Spring's default document, which is valid JSON carrying none of the six SH80 keys, so it parsed, took the `title ?? …` branch, and skipped every line after it; the raw-body fallback fires only when the body does *not* parse. `path` and `error` were on the wire and nobody saw them, and diagnosing it took a log a 400 never reaches, a probe against an endpoint whose contract I guessed wrong, and a byte-size listing. So a non-teaching body now prints its scalar fields in the order the server sent them, capped with the cut declared — and a body carrying *any* teaching key is unchanged, since `status` and `path` ride along on a real SH80 problem too. It is one choke point, so it reaches every write the CLI makes. The CLI test suite needs `@viglet/shio-client` built first (`pnpm -C shio-client run build`), or every command test fails on a missing module rather than on its own logic; `shio-sections`' suite needs `@viglet/shio-model` built (`pnpm -C shio-model run build`) for the same reason.

**Two rules the CLI suite earned the hard way (SH198).** A fake filesystem **normalizes its
keys** — `test/support/paths.mjs`' `norm`/`endsWithPath`, one copy for all three of them. The
commands join with `node:path` because they write to a real disk, so on Windows a fake fs keyed
on the POSIX literal the author typed never matches, `readFile` throws `ENOENT`, and the command
takes its "no such file" branch: the test then fails on a *message*, several assertions away
from the cause. That is why one mistake read as seven unrelated defects across four areas. Do
**not** "fix" it by making a command emit forward slashes. And a test that arms a live handle
releases it in **`t.after`**, never only on the happy path — the watch-loop test's `stop()` sat
after its assertions, so one wrong expectation left a `setInterval` holding the event loop and
`node --test` printed all 199 results and then never exited. That is what "the suite hangs" was;
stdin had nothing to do with it. `--test-force-exit` is in the `test` script so the next leak
reports and exits instead of burning a CI timeout with no diagnostic.


**The fixture is a catalogue of what breaks a replica (SH528).** It was 134 lines — a page, a
stylesheet, a webfont, an image, a favicon — and every defect this work found came from a real
site that had traits it did not. A live source is how a defect is *found*; it is a bad way to
*keep* one, because the site changes, the network fails, and a red run then means nothing until
somebody works out which of the three it was. So the traits are now in the fixture, each named
with the task that needed it: a **query string** on an asset path, a `./../../` reference served
**four segments deeper** than its source (SH512 — against the origin it is `/logo.svg`, against
the page's directory `/docs/guide/intro/`, and only one of the three exists), a **fixed overlay**
whose text became three sections' `intro` (SH505), a **`&` escaped twice** (SH476/SH565), a
**zero-byte file** behind a 200 and an image content-type (SH514 — nothing in the response says
it is empty), a **`<canvas>` a script creates** so a plain fetch and `--render` disagree (SH517),
a page under a **locale prefix**, and a **link that is wrong**.
**They sit at depth 2, behind `/about`, with a stylesheet of their own** — the conformance run
crawls `--depth 1`, so it meets none of them and its asset list, theme and counts are exactly
what they were. A trait is something a test opts into, not something every other assertion has to
absorb. `cli/test/fixture-traits.test.mjs` asserts each one is *present*, because a catalogue
whose entries can vanish without a failure is a list; it also pins the two fixtures in step,
which their shared header always claimed and nothing enforced. One entry deliberately pins a
**defect rather than its cure**: the double-escaped title arrives whole, which is SH565 open and
now reproducible with no network — asserting the repair there would make the catalogue fail for
the thing it exists to preserve.

**The source corpus (SH560) — `fixtures/source-sites/`, at the repo root and not under `cli/`.**
One directory per **source archetype**: bytes under `site/`, plus an `expectations.json` that
declares what a capture of it should contain and the task ids the archetype exists to catch.
`corpus.mjs` builds one route table per archetype and serves it two ways — `injectedFetch` for the
fast tier, `serve` (a real `node:http`) for the live one — so a divergence between the tiers is a
statement about the boundary rather than about the fixture. `cli/test/source-corpus.test.mjs`
iterates the corpus and runs the offline chain (`clone` → `propose --accept all` → `convert --out`)
once per archetype, reading a declaration where the other two suites read a constant.

**An archetype models what an author would not think to write, and `theme-heavy` is where that is
now literal (SH658).** SH477's rule — a mark whose `aria-label` repeats its own visible text is
decorative and is not part of a link's label — was read off one real site and then exercised by
literals in `clone.test.mjs` alone: measured across every `.html` the repository holds, **19 files,
30 anchors, zero** carried an `aria-label` inside a link. So `theme-heavy` gained a masthead whose
brand link encloses `<span class="mark" aria-label="Nw"><span>N</span><span>w</span></span>`, which
is how a site spells a drawn two-letter logo, and the declaration holds **both** ends of the rule:
`expect.notes` that the count fired, and `expect.navigation` — `keeps` existential, `drops`
universal — that the label still says the source's words and never the graphic's letters. The
asymmetry is not style: one `href` legitimately carries several labels (`/` is both the brand and
the about page's "Home"), so a universal `keeps` would fail on the link that is simply not the
brand. Removing the attribute makes the label read `Nw Northwind &amp; Co`, which is the injection
that proves the fixture exercises the rule rather than merely coexisting with it.

**And it found a defect on arrival, which is the corpus working as designed.** The captured label is
`Northwind &amp; Co` while the same document's `title` is `Northwind & Co` — extraction decodes an
entity once for a title and not at all for a link label. `keeps`/`drops` are substrings rather than
one expected string precisely so this case does not fail for a reason that is not its subject; the
entity is its own task, and `awkward`'s `doubleEscaped` is where that question already lives.

**Both drivers now have a caller, and that was the half that was missing.** `serve` shipped with the
loader and was called by nothing, so the sentence above — *a divergence between the tiers is a
statement about the boundary* — was unfalsifiable: one driver ran, and every claim the corpus made
was a claim about an injected `fetch`. `cli/conformance/source-corpus.conformance.mjs` is the second
caller, and it asserts the three things a fake structurally cannot:

- **what was asked for.** `served.requested` is the server's own log. `unfollowed` says the capture
  did not *keep* a page; only the socket says nobody ever *asked* — opposite claims (SH387), and a
  crawl that fetched a page and discarded it has still spent a stranger's bandwidth.
- **what the platform's `fetch` does with the answer**, which is not what the fake does: the fake
  resolves a 301 as a 301, undici follows it. That is the boundary SH563 lives on.
- **that `spa` is refused rather than skipped.** `--render` is always attempted; the only two
  acceptable outcomes are a capture that reached `unreachableWithoutBrowser`, or a refusal carrying
  `browser.mjs`'s two-command install line (SH434). A `skip` would be the corpus reporting its most
  expensive archetype as covered.

**The held-findings ledger is the corpus's, not the fast tier's (SH671).** `open-findings.json` is
how an assertion stays in the gate while the defect it names is open — red-and-held is *reported*,
red-and-unheld **fails**, green-and-held fails too — and that machinery lived inside
`source-corpus.test.mjs`, so the live tier had no `holding()` call at all. Measured consequence: the
live tier was red at HEAD on `malformed`, `red-suites.json` held `exceptions: []`, and there was no
third place to declare it, so one of the three gates sat in the state SH579 says does not exist —
undeclared, and invisible because the instrument that would have reported it was the one that was
red. `fixtures/source-sites/open-findings.mjs` is now the one implementation both tiers read, and an
entry carries **`tier`** (`fast` | `live`, absent meaning `fast`) so each tier checks its own entries
in both directions. Without the scope they would report each other's keys as unexercised, which
means only "the other driver did not run".

**The red itself was the instrument contradicting itself across two tests.** `malformed` declares
`expect.problems: ["/deep/page"]` — SH544's `<base href>` resolved against the page rather than the
base — so one test asserted that path *is* recorded as a problem while the next counted the request
for it as a path "this archetype does not serve". A declared problem is a request the archetype
exists to provoke, so it is now exempt; the route table stays the rule, which keeps an invented URL
a failure. Removing the declaration puts both tests back to red, which is how that exemption is
known to be load-bearing rather than a blanket pass.

It is the one file here that does **not** call `requireInstance`: its boundary is the socket, and
demanding a Shio it never speaks to would blame a red run on an instance with no part in it.

Two consequences worth knowing. `INERT_KEYS` in the fast tier is now **empty** — `scenarios.headless`
was never inert, it was *misfiled*, a claim about a browser capture held by a tier that declares it
runs without one, and group K reads the conformance file precisely so a key can be covered by the
tier that can honestly cover it. And the first live run flipped `spa`'s `scenarios.headless` to
`true`: the check is written in both directions, so a machine where `--render` completes fails until
the declaration admits it.

Why a corpus and not a bigger `fixture-site.mjs`: **a trait can be a property of the site rather
than of a page**, and those do not compose into one server. A source with no home at `/` cannot
also be the one that answers `/`; a source that is client-rendered end to end cannot also be the
one whose markup is complete; a source whose every page is a query string cannot also be the one
whose paths address cleanly. SH528's per-page catalogue stays in the single fixture; only site
traits earn a directory here, and `trait: "site"|"page"` is asserted so the split does not drift.

**Its red cases are a named list, not a suppression.** `open-findings.json` holds
`(archetype, assertion) → task id`, and `holding()` reports those instead of failing — the reason
being the rule two paragraphs up: *an instrument that cries wolf in the gate is worse than one
outside it*, while SH338 requires the assertion to land before the fix. Read **both** directions:
a held case that goes green **fails**, naming the id to delete, and a held key no assertion
exercises fails too. Every run prints the count and the owners, the way
`assertion-debt.test.mjs` does, and for the same reason — a ceiling lets one task pay the debt and
the next task spend it. Its first run held six red cases owned by five open tasks, three of which
(SH563, SH564, SH565) it found: a redirect `buildInventory` drops, an absolute href stored in a
section field, and a `<title>` nobody decodes.

**And an entry must name a behaviour, never a mechanism — the both-directions check is what makes
that binding (SH568/SH606).** `layout-raw-field-CSS` read *the generated layout does not emit
`{{CSS}}` with two braces*: a statement about the template, presuming the cure was a third brace.
SH461 had already decided otherwise and written down why — the fix belongs where the value is built,
"or every hand-written layout in the product keeps the defect and only the generated one is cured" —
so SH555 marked the row's raw-text fields `Handlebars.SafeString`. The defect closed and the entry
could never go green, because *an assertion phrased as "the code does not do X" can only go green the
one way its author imagined*, and a list that fails on a green entry then converts a **correct
repair** into a permanent red. That is the general finding, and it is now the `rule` field in
`open-findings.json` so the next author reads it while writing the entry.

**The particular one was answered by deleting a reader, not by fixing it (SH606).** The behaviour —
a captured stylesheet reaching the page with its `url()` quotes intact — is asserted where the
renderer is, by `ShConvertedLayoutRenderTest`, and no brace count can stand in for it: the same two
braces are right or wrong depending on `RAW_TEXT_FIELDS`. Two readers of one fact that agree only by
accident is SH583's shape and this was its third sighting, so the fast-tier proxy went and the
archetype's now-unread `rawFields` key went with it. **A wrong id in a label is the same defect one
level down**, and one was sitting in the same file: group J's navigation case named SH568 and belongs
to SH575. SH527's reader counts an id named in an assertion message as *asserted*, so a mislabel pays
off the wrong task's debt — the false-payment shape `assertion-debt.test.mjs` has recorded twice.

**The published pages are executed, by two instruments and not one (SH499).** Block R wrote a
documentation set full of copyable invocations and nothing ran one, which is exactly how the 2018
pages became wrong: written once, product moved, never compared. The task's first deliverable was
the count — `cli/conformance/docs-examples.mjs`, an extractor that classifies the fenced blocks of
`docs-shio` and prints what it found (**57 executable blocks over 18 pages, 65 assertable
subjects**, against 32 illustrative ones). That count is what decided the shape, because it splits
in two halves that answer different questions at very different prices:

- **`docs-vocabulary.conformance.mjs` — the names.** Every documented `shio` verb, `@RequestMapping`
  path, `shio_*` tool and `shio.*` key is held against what the *product* declares, read by
  `support/product-vocabulary.mjs` from `COMMANDS` in `index.mjs`, the Spring annotations, the MCP
  tools' `name()` and the `@ConfigurationProperties` classes — and **the vocabulary it checks
  against has to be true, which is a separate job with its own lint (SH656)**: a bound properties
  class nothing injects declares keys to this check, so a page documenting one passes while the
  setting does nothing. `ShBoundPropertiesReachabilityLintTest` is that lint. **No instance,
  milliseconds.** This is
  what catches a renamed verb. Nothing is listed here — a checked-in list of "the verbs as of today"
  would rot by the same mechanism as the docs, one directory closer.
  **And it reads tables, because the page it exists for is one (SH663).** The extractor took fenced
  blocks tagged `properties`, and `configuration-reference.md` has none — its whole content is a
  table, one row per key. So the page whose entire subject is the property vocabulary contributed
  **nothing** to the count that checks it, which is how three keys binding nothing shipped as
  published rows and stayed. Independent of SH656: with the vocabulary correct, a page naming a key
  that was never bound would still have passed, because the row was not read. The rule is narrow
  because a cell is prose — only the **first** cell of a row, in a table whose header names a
  property column, and only a **dotted** key. A citation in a Description column is the page
  explaining itself; a bare `max-width` is a field named relative to a prefix the page spells in a
  fence three lines above, which this reader cannot join. Both are **declined and counted** —
  `tables past` and `rows relative` in the printout — because a check that silently covered some
  tables would be worse than one covering none. Measured: 14 property blocks became **22**, 77 keys
  became **121 subjects**, 7 tables past, 4 rows relative. Proved by injecting `shio.invented.key`
  as a table row and watching the gate name it.
  **And since SH652 it also asks the opposite question, which nothing here was asking.** Every
  check above walks the *documentation* and holds the product to it, so it catches a page naming
  something renamed or invented — and structurally cannot catch a name the **product** emits that
  no page describes. `shio audit` reported ten rules for two blocks while the published `cli` page
  listed the verb and defined none, so learning what `collapsed` meant needed `cli/src/audit.mjs`
  open, which is the human round trip an audit exists to remove (P9). The check reads
  `AUDIT_RULES` rather than a list kept beside it, so an eleventh rule fails the day it is added
  rather than the day a reader hits it; it also holds the two lines the report always prints
  (`shape:`, `scripts:`), which are not rules and so cannot come from that map. Proved in both
  directions by unspelling one rule's name on the page and then the `shape:` line, and watching
  each fail alone.
- **`docs-coverage.conformance.mjs` — the matrix's own arithmetic (SH653).** `COVERAGE.md` ends with
  the one sentence anybody scanning it reads — *"Of N clusters, M are still not documented
  correctly"* — and every figure in it was typed. It drifted the way a typed number does, **in the
  direction that hides work**: the `shio audit` row was 🔴 in the tables from the day the file
  shipped and absent from that count for just as long. The file recorded the disagreement in a note
  rather than resolving it, because resolving it needed a decision first. **A cluster is one table
  row.** Measured before choosing: 48 rows, 33 distinct block labels, and the 42 the summary
  claimed — *neither* reading reproduced the typed figure, so the note's "48 against 40" was not the
  only gap. The row wins because it already carries its own verdict; grouping by label would need an
  aggregation rule nobody has written, since `Block H` spans five rows and `Block K` two that
  disagree. It **asserts and prints the sentence to write** rather than rewriting: a generated
  summary is a section nobody writes and no reviewer reads, and the point is to take the arithmetic
  out of a person's hands without taking the sentence out of the diff. The table reader is
  header-anchored, which is the whole of its correctness — the file also holds a two-column *"why no
  page"* table whose prose says things like *"folded into the ⚠️ rows above"*, and a glyph-anywhere
  match counted those and came to 50. Proved by widening the matcher and watching both the tally and
  the guard fail; the guard's first draft named one of that table's rows and **passed while a
  different one leaked**, which is this file's own defect one level down.
- **`docs-examples.conformance.mjs` — the requests.** The 15 read-only documented requests are
  issued against a live instance. It catches the three things a name check structurally cannot: an
  endpoint that is mapped and 500s, one whose response lost the key the page shows, and one a page
  presents as open that now needs a credential it does not mention.

**Both live in `cli/conformance/` because their input is another repository.** `docs-shio` is not a
checkout `cli/test/` can see, so a gate there would have to *skip* when it is absent — the failure
mode `support/instance.mjs` exists to refuse. Here the bargain is already opt-in, so an absent
checkout is a failed run naming the clone command, exactly as an unreachable instance is.
`SHIO_DOCS_DIR` overrides the path, because the one certainty about a sibling checkout is that it is
somewhere else on the next machine.

**And on a CI runner it was nowhere, which meant these had never run outside one laptop (SH655).**
The default is a drive letter, `suites.yml` cloned only this repository, and absent is a hard
failure by design — so the docs half of `pnpm conformance` could not pass and could not fail
anywhere it would be read. The instrument SH628 landed was in that state from the day it shipped:
not a check that passed, a check nobody was running. Both jobs now check out
`vigletdocs/vigletdocs.github.io` (public, so no token) and set `SHIO_DOCS_DIR` at it, and
`ci-gates.test.mjs` holds both halves — a clone nothing points at is the same absent directory with
an extra step.

**They are split by cost, and that split was the decision rather than the wiring.** Two of the three
touch no instance: `docs-vocabulary` reads source and `docs-stack` reads build files, so
`pnpm conformance:docs` runs them in its own job beside `pnpm test`, on every push, in about a
second — 12 assertions over 36 executable blocks. Only `docs-examples` calls `requireInstance`, so
it stays in the conformance job behind the boot. Putting the pair behind a JVM would make the
cheapest question anyone asks about the published pages — *do they still name this product's verbs*
— wait on the most expensive gate, and `ci-gates.test.mjs` asserts the script excludes
`docs-examples` for the same reason: a job without an instance running it would fail on
`requireInstance` and read as the documentation being wrong.

**The 404 problem is why the execution assertion is shaped the way it is.** "A documented request
must not 404" is wrong and quietly so: half these examples carry a placeholder site (`mysite`) no
instance has, so a *correct* server 404s for a reason that has nothing to do with the docs. P6
resolves it — every 4xx here carries `fix`, so a domain 404 is an RFC 9457 document and a routing
404 is Spring's bare body. The assertion is **"reached a handler"**, decided by reading the body,
which makes this suite a second reader of P6 as a side effect.

That side effect found all three of its first run's failures, and they are held in
`docs-open-findings.json` — the **fifth** ledger `known-debt.test.mjs` joins, added the way that
file said one should be (a row in `LEDGERS`, not a fifth reader; the hardcoded count is asserted so
a sixth cannot arrive unjoined). `GET /api/v2/post-type/Article` answers **404 with zero bytes**
while the same miss on `/api/v2/agent/read` returns `type`/`title`/`fix`/`allowed` — P6 implemented
on the agent surface only, on the one name-keyed surface where a typo is likeliest (**SH621**). And
two CDA reads answer **403 from the security chain**, which runs before any `@ControllerAdvice`, so
*every* endpoint here has one 4xx that carries no instructions and it is the one a caller meets
first (**SH622**). Mutating examples are **declared unexecuted** and printed with the reason rather
than dropped: a suite silently covering half its subjects reads exactly like one covering all of
them.

**The generated layout is `<site> (layout)`, and the rename is the interesting part (SH573).**
`convert` called it `<site> (fidelity)`, which was accurate when one layout served one mode and has
been decoration since **SH464 made a single layout serve all three** — decoration shaped like a
fact, which is the worst kind. SH559 fixed the reader's half by computing the mode from the page's
own fields for the preview banner, and had to *quote* this title rather than print it, because
somebody looking at `layout <site> (fidelity)` beside an unstyled page reasonably concluded the
conversion had picked the wrong mode. A quoted wrong name is a smaller defect, not a closed one.

**Why a rename is safe here, which is what the task was actually about.** The string is both the key
in `site.postTypeLayout` *and* the layout post's `TITLE`, and they are written from **one call in
one document**. So a new conversion is self-consistent the moment the constant changes; a site
converted before and never re-run stays self-consistent too — its binding and its post both carry
the old title, and it renders correctly forever, the two vintages differing by a word; and a re-run
**migrates it and cannot half-migrate it**, because the layout post is addressed by *url*
(`/theme/layout`) rather than by title, so the re-run updates that same post and writes the matching
binding in the same document. No orphan, and no window where the binding names a title no post has.

So existing sites are carried across **on their next conversion and never behind anybody's back** —
a re-run is a write the operator asked for and already rewrites this post's `HTML`; what SH573
deliberately does not do is reach into a site nobody re-converted, which would be a write nobody
asked for on a site somebody may have edited since. `convert.test.mjs` asserts the two are one
string, and that the title names no mode at all — they are two literals in two places in the output,
and an edit that changed one would produce a site whose every page renders empty.

`ShConvertedLayoutRenderTest` keeps a fixture titled `Sh464 (fidelity)` **on purpose**: the property
under test is that nothing downstream reads meaning out of this title, and a layout named after a
mode it does not serve is the adversarial case for exactly that.

**`shio propose --from <address>…` is the same verb pointed inward (SH525).** `propose` derives a
post-type from markup nobody wrote for Shio — on `viglet.org` it offered `BlockBuildTheFuture` with
`heading:text, body:htmlEditor, image:file, items:relator`, inferred from one page's structure.
That is data modelling by example, it works, and it existed only inside conversion. `--from` asks
the identical question about content you wrote: *what post-type are these instances of.* A flag and
not a verb, because the task's own diagnosis was right — the capability was built and only its input
was hard-wired to a capture.

Why it is worth having: writing a post-type from nothing means naming fields before knowing what
they hold, which is the wrong order. A model is easier to see once three instances exist and
disagree, and the route before this was to author blind, discover the shape, then rewrite the type
and migrate what already used it.

**Two things the conversion version does not need, and they are where the value is.**

- **Which instances disagree.** A field two of three share is a different proposal from one all
  three do, so every field carries `in/of` and the addresses that lack it. That is the difference
  between a required field, an optional one, a data gap and a second type — and none of those is
  visible from a count.
- **A minority field is reported and not proposed.** One instance in three carrying `ISBN` is more
  likely another type's field than an optional one, and sweeping it in produces exactly the
  over-wide type this exists to avoid. It lands in `outliers`, because "these instances carry
  something the others do not" is a finding about the model and hiding it makes the proposal look
  more settled than the evidence is.

Two smaller rules that change the answer: **an empty value is absent**, so a field every instance
declares and none fills is not evidence of a field — counting it would make the disagreement read
as agreement; and **an absent value does not vote on the kind**, or every partly-filled field would
infer as `text`. The kinds are `@viglet/shio-model`'s own vocabulary, so a type proposed from
instances and one proposed from a capture are the same artefact and can be compared.

The report ends by saying what it is: *a proposal, not a conclusion — N instances are evidence about
those N.* That property is the one most easily lost when the input stops being a stranger's site and
starts being content the reader wrote, which is why it is asserted rather than merely written.

**An absolute href in a section field is rebased where the origin is known (SH564).** `propose`
extracted `ctaHref = "https://awkward.test/docs/guia"` from an anchor and `convert` wrote it into
`SectionHero` unchanged, so the accepted hero's button pointed at the original site. SH466 is not
the fix and is not wrong: it rebases **root-relative** hrefs, at render, over the page's `content`.
Neither half reaches this — `ctaHref` is a field rather than content, and an absolute URL is at
render time indistinguishable from a link a curator meant to send off-site. The phase that can tell
is the one that knows what the origin was.

Stored as the **source-relative path**, which is how the theme already stores one (SH461: the
rewritten CSS names `/img/x`, not the tree path, because that is what the instance resolves).

**"Same host" is the obvious rule and the corpus was built to defeat it.** The `awkward` archetype
declares its base as one origin and is served from another — which is what generated HTML does all
the time, and what a staging host, a preview URL or an injected fixture produces. Under a same-host
rule every unit test passed and its `ctaHref` stayed absolute. So an href is internal when its host
matches the crawl origin **or when the capture holds that path**: a capture knows its own page set,
and that fact survives the host being one the crawl never visited.

**Only absolute references are touched**, and that was the second correction. Resolving every value
against the origin turned `#top` into `/#top` — a same-page anchor repointed at the site root — and
percent-encoded `{{ cta_url }}`. A relative value is already what a replica serves.

Two smaller decisions: the host comparison is case-insensitive and default-port-blind, because
`HTTPS://Acme.test` and `https://acme.test:443` are one origin to a browser and reading them as
foreign would leave the very link this is about pointing off-site; and `clone` records `source` as
`{origin, seed}`, so reading the object where a string is wanted is **silent** — `new URL({…})`
throws, the catch returns the href untouched, and the fix looks applied while every assertion stays
exactly as red as it was.

**A captured `<title>` is decoded where it is read (SH565).** Measured:
`scanPage("<title>Northwind &amp; Co</title>")` yielded the five characters `&amp;` while
`decodeEntities` on the same string yielded `&`. The decoder was right and **nothing called it
here** — a `<title>` is RCDATA, and both earlier entity fixes routed around it. SH476 decodes text
*"when it becomes a field"* (`propose.mjs`'s `text()` and `alt`); SH550 decodes attribute *values*
and states the exclusion in its own words: raw-text bodies are not decoded.

It reached further than either of them. The title does not travel through `propose` — `scanPage`
puts it on `doc.title`, `buildInventory` copies it to the page row, `convert` writes it into
`Page.data.title` — so **fidelity and authorable were both wrong**, where SH476's defect was
authorable only. And it compounded on re-capture: each pass escaped what the last one stored.

**Decoded once**, which is the part worth stating. `&amp;amp;` is an author escaping an
ampersand-entity deliberately, so the correct reading is the literal text `&amp;`; a second pass
would yield `&` and lose the distinction the author was drawing. `decodeEntities` is single-pass for
exactly that, and the fix must not smuggle a second one in.

`cli/test/fixture-traits.test.mjs` pinned this trait **as the defect**, on purpose — "so the fix has
something offline to turn green". It now pins the cure, and the fixture's source bytes are
unchanged: a trait catalogue's job is to keep the awkward input, not the wrong output.

**A source that answers a request badly, which no fixture had ever been (SH569).** The shared
fixture says why it is not this and is right about itself — *nothing 404s; an unreadable page is its
own finding, and one in the fixture would blur the rule being asserted*. Correct for a seam test,
and exactly the hole: every source this work was driven against answered some request badly, and the
two that cost most were both fetch-layer. SH431 filed twenty-four linked JSON documents as
`problems`. SH544 ended a 201-page conversion **after 403 assets had been uploaded**.

The `hostile` archetype serves six: a dead internal link, a 500, a redirect that returns to itself,
a linked JSON document, HTML served as `text/plain`, and an oversized asset. The subject is not that
a request fails — it is that **the run does not die and the report is honest about what it got**.

**The assertion SH569 said to write first is the SH431 one**, and the corpus now holds it: `data`
and `problems` are different claims. `data` is what was never a page; `problems` is what could not
be read. Collapsing them is not cosmetic — the coverage gate reads a `problems` row as a page it
could not read and downgrades **every count in the report** to a lower bound, so a bounded sample
starts reading as a complete one. The archetype asserts both the membership and the disjointness,
because either alone would pass while the other collapsed.

**It found a real defect on its first run, which is the argument for it.** `visit` followed
redirects with a comment saying *"one hop, followed by hand"* and **no counter** — it recursed. The
`/loop` route spun until the process died, and the suite went from a fast run to a 100-second
timeout. There is now a budget of five: a real source chains two or three legitimately (`/a` →
`/a/` → the canonical host), so a budget tight enough to refuse those would report a working page as
broken; what has to be stopped is the cycle, and a cycle exceeds any budget. Exceeding it is a
reported row — never a crash, never a silent omission.

**Markup a browser repairs, and a scanner does not (SH570).** SH569's archetype fails at the *fetch*
layer; this one is a **200 whose body is wrong**, and the two are separate because a scanner cannot
report what a fetch already refused. Nothing else in the corpus is malformed — every page was typed
by somebody who closed their tags, so `tokenize`, `scanPage` and `stripNonMarkup` had only ever been
measured against markup a browser would not have had to repair, and a real source is repaired
constantly and silently.

The `malformed` archetype serves six: an unclosed `<div>`/`<section>` so a block's extent is a
guess; **no `<title>`**, which `Page` requires; a **265-character** title, which is SH544 as a
fixture rather than as a story; a **nested form**, which the parser must not read as two; a
`<base href>` that disagrees with the serving path; and a declared **charset the bytes contradict**.

**The four scanner behaviours were already right and are now asserted** — an unclosed block does not
swallow what follows, an absent title records `null` and not `""` (a source that had none is not a
source with a blank one), a long title is captured **whole** because truncating would hide the fact
the conversion must refuse on, and a nested form counts once.

**The base-href trait asserts a report, not a repair.** `clone` resolves relative URLs against the
page rather than the declared base — and *says so* in a note. That is the honest behaviour to hold:
the disagreement is stated where a reader can act on it instead of being silently recorded as one of
the two possible resolutions.

**The encoding assertion is judged on bytes, never on what a terminal prints**, which the checklist
records getting wrong from the other direction. The fixture serves UTF-8 under a
`charset=iso-8859-1` header on purpose — an archetype whose own encoding is ambiguous cannot be the
one that settles an encoding question — and the assertion compares the stored title against the
source file this run served. It was verified to **fail** when pointed at a different file: it runs
inside `holding(...)` and is `async`, which is exactly the shape that can pass while never executing.

## `shio diff --against published` — the draft against what is live (SH640)

SH522 named four declarations `shio diff` should judge an artefact against. Three were settled; the
fourth — **the same address at an earlier moment** — was refused because `/api/v2/agent/read` was
draft-preferred and could not return the live row's fields at all. SH619 removed that block, and this
is the verb.

It is a verb rather than an endpoint because SH619 already decided the shape: `state` is a parameter
on the read, so **the comparison is two reads of one address** and composes out of the existing
surface (P10) instead of becoming a third contract with its own tool and budget.

### The three questions the read deliberately left here

**Rich text compares verbatim.** Publishing copies the draft's value across unchanged, so any
difference is one a curator made — and a normalising comparison would hide a whitespace-only edit
that the CDA then serves differently. It also stays on the right side of a non-goal: a semantic HTML
comparison is the first step toward an instance that runs a browser. What is *reported* is that the
field changed and by how many characters, never the text (P3 — a rich-text body is the most
expensive thing this could print, and the values are one `shio read` away).

**A missing field and an empty one are different findings.** `added` is present in the draft and
absent live, `removed` is the reverse, `changed` is present in both and different. An empty string is
a **value**, so `""` against absent is added/removed and `""` against `"x"` is changed. Deleting a
value and never declaring the field are different acts; folding them tells a curator that something
differs and nothing about which act produced it.

**The answer is per site, listed per post**, because "what will change when I publish" is asked
before a *batch* — an agent has just written several pages. `--address` (repeatable) narrows it to
one.

### Two things that fall out of the design

- **A post with no live row is `new`, counted apart.** Every field of a never-published page would
  otherwise read as `added` — true, and useless: it buries the three fields that changed on a page
  somebody is revising under two hundred that did not.
- **Only posts `find` already reported as live are read twice.** `find` carries `published` per row
  (SH545), so a post that has never been live is known to be new without a second read. On a site
  being built most posts are in that state, and reading each twice would double the most expensive
  call in the CLI to learn what the first call already said.

Exits non-zero when anything would change, the same contract `apply --check` carries, so a caller can
gate a publish on it without parsing anything.

**One lint caught a real slip**: the first version read a `--addresses` flag that does not exist —
the parser already collects repeats under the *same* key when the flag is declared in `REPEATABLE`,
which `address` now is. `SH435`'s help lint named it, which is the case that rule exists for: a flag
a caller can only find by reading the source.

## The clean room, and what is actually in the box (SH605)

Every measurement this project has was taken with the source tree on disk. The reader had
`agents.md`, `docs/agents/**`, `docs/specs/**` and a repo-local skill, and used them **without
noticing** — which is what using them looks like. So "is Shio discoverable" had never actually been
asked; what was measured is "is Shio discoverable to a reader holding its repository".

`cli/test/clean-room.test.mjs` asks the real question. **The box comes from `npm pack --dry-run
--json`**, not from a list, so it cannot drift from the `files` field, an `.npmignore`, or a
directory somebody added; the plugin ships as a directory, so its box is that directory minus its
own tests.

Five assertions, each about a different way the repository can leak into the product:

1. No reader-facing file names a repo-only path (`agents.md`, `docs/agents/**`, `.claude/skills/**`,
   …). Scoped to markdown and help texts: a javadoc naming the test that guards a rule is a note to
   whoever maintains the code, while sending a stranger to `docs/agents/renderer.md` is a different
   act.
2. Every relative link in published markdown resolves **inside** the box — a link is a promise the
   file is there.
3. The box names a **first move** on each of the three surfaces a stranger can arrive through:
   `shio context`, `shio_context`, `shio://manifest`. A box that stranded nobody and taught nothing
   would pass 1 and 2 and still leave a reader with no way in.
4. The walk is not empty, because a packer answering with no files would pass everything above.
5. Nothing tells a reader to run a command only a checkout has — `./mvnw`, a workspace filter, the
   commit script, roadkeep. This is the leak a path check cannot see: **a build command reads as
   ordinary advice.**

### It came back green, which the section did not expect

§XI.19 said "expect it to be red on the first run, and expect the missing files to be interesting".
It was green. The reason is worth recording rather than treating as luck: **the nine repo-local
skills are contributor-facing** — they route to `docs/agents/**` for people changing Shio — and the
plugin's four are usage-facing and self-contained. The split was already right; nobody had checked
that it was.

Green on a first run is also the state where an instrument is least trustworthy, so it was proved
by **planting a leak**: a README line with a `docs/agents` link and a `./mvnw` command fails three
of the five, naming the file and line. The tree was restored immediately.

What this does **not** assert is that the teaching in the box is sufficient — only that it is
self-contained and has an entry point. That is SH603's job, and this is its enforcement half.

## The help surface is measured, so the two discovery paths can be compared (SH394)

`cli/test/cli-help-budget.test.mjs` asserts three ceilings that live in
`shio-app/src/test/resources/token-budgets.properties`, six lines below the MCP ones and
on the same 4-chars-per-token ratio:

| Key | Ceiling | Measured 2026.3 |
|---|---|---|
| `cli.help.global` | 1300 | 1215 (`shio --help`) |
| `cli.help.max-per-command` | 1200 | 1146 (`clone`) |
| `cli.help.all` | 9800 | 9535 (all 31 texts) |

**Re-measuring corrected the task that asked for it.** SH394 was filed quoting
`shio --help` at 14,363 bytes / ~3,600 tokens. It is 4,858 / ~1,215 — the old figure
predates SH208, which moved every command's text out of `help.mjs` into its own `.txt`, so
it measured the *source file* rather than the output. The task's conclusion survives (the
surface really was unmeasured, and that was the defect); its arithmetic against MCP did
not, and was wrong by 3× in the CLI's disfavour. Measure before you fix applies to the
number a task hands you, not only to the ones you produce.

**The two costs are shaped differently, which is why only three lines are asserted.**

- MCP's is **per turn and bounded** — every session pays `mcp.tools.list.total` on every
  turn, written or not.
- The CLI's is **per session, on demand, and unbounded** — an agent pays for the help it
  reads, once, and nothing stops that text growing.

A ceiling alone is the wrong instrument for the second shape, so the per-session
comparison is **printed on every run and asserted nowhere**: it depends on how many turns
a session runs, which is not a build's to declare, and the ranking is a product decision
rather than an invariant. Reading it as a winner is the mistake — `tools/list` also buys
schema validation that prose cannot.

**A missing key is a failure, not a skip.** `need()` asserts the key exists, because a
budget test that silently checks nothing is worse than none: it reports green. That is the
same reason the ceilings live in the properties file rather than in the test — one place
for a number, which is the reasoning that file already carries, and the same coupling
`shio-site/scripts/token-budgets.mjs` makes on purpose.
