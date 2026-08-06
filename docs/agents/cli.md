# The shio CLI

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### `shio` CLI (`@viglet/shio`, in `cli/`)
The "JS dev, zero Java" command-line tool: `init` (scaffold via `create-shio-app`; **`--blueprint <name>` takes the starter from the instance instead, and with `--site` applies its content half in the same command** — SH119; **`--site` with no `--blueprint` refuses a site whose Pages carry `sections`, because the built-in template renders a body and not sections** — SH502, and `blueprints.md` has the measurement), `types` (SH11 codegen), `pull`/`push` (post-types as code → `shio/post-types/*.json`, `--dry-run`/`--prune`; **`--content` switches both verbs to content**, see below), `context` (`--as agents-md` renders the pack as project agent instructions — SH115),
`resolve`, `apply` (SH129 — a desired-state content document; `--check` is the CI drift guard, `-` reads stdin; **`--blueprint <name>` applies a package instead**, with `--param k=v` repeatable, `--params <file>`, `--site`, `--force`), `publish` (SH546 — **the only call that changes what visitors see, on the tier that could not make it**: addresses as arguments or `--address`, `--site X --all` to name every post from a `find` first, `--type` to narrow, `--unpublish` for the inverse, `--dry-run`, and the batch ceiling is read from the refusal rather than copied out of the server's configuration — see below), `blueprints` (SH92 — the catalogue; `blueprints <name>` for one package's parameters), `verify` (SH99/SH100 — the content lint; **exits 1 on an error**, `--strict` also on warnings, `--folder`/`--address`/`--since` scope it per SH143, `--baseline`/`--accept` subtract accepted findings per SH142, **`--delivery` adds SH141's route proof** — it appends the group rather than replacing `--checks`, since a caller wanting the stronger proof still wants the lint — and the report's `notes` are printed, because an unreported cut reads as a clean pass), `digest` (SH101 — one page's structure), `snapshot` (SH102 — **a PNG for a human**, plus whether the page looks different; baselines in `shio/snapshots/`, `--accept`/`--fail-on-change`/`--width`. `playwright` is an **optional** dynamic import with a teaching error naming the install, so `@viglet/shio` never pulls a browser; with `pixelmatch` + `pngjs` the diff is per-pixel, without them it is byte-level and the output says which. **A change exits 0** — a page looking different is usually the change you just made, and a gate here would be the one people wrap in `|| true`. The manifest still reports `visualSnapshot: false`, correctly: there is no endpoint and no MCP tool, and an agent that read `true` would hunt for a call that does not exist. **A site with no front end is photographed through the instance's own preview (SH265)** — `resolveUrl` used to refuse with "there is no page to photograph", which Block P made false: SH183 renders the page out of content the instance already holds and SH184 stopped the *server* saying the same thing, so the CLI was the same premise left standing one process along. `--preview` forces it even for a site that has a front end, which is the only way to photograph a **draft**. The capture carries the instance's Basic credentials **only for that origin** — the preview is authenticated since SH359, and handing a site's public host an admin password because one command photographs both would be a leak nobody asked for — and the output says the picture is a DRAFT with the banner on it, because the image cannot say so itself), `changes` (SH88 — the feed; `--since last` reads the last content pull's position, `--cursor` prints only the position), `report` (SH104 — **the human handoff**: what changed with a preview link each, plus the warnings as open questions, as Markdown for a PR body; `--since last`/`content`, `--mark`, `--out`, `--title`, `--no-verify`, `--json`), `remember` (SH114 — the instance memory; bare lists it, `<key> "<note>"` writes one, `--forget` drops one, `--site` scopes it), `mcp`, `dev` (`--content` runs the SH90 sync loop instead of the site's dev server), `deploy`. `pull`/`push` use console Basic auth (`SHIO_USER`/`SHIO_PASSWORD`); `types` uses a CDA token (`SHIO_CDA_TOKEN`).
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
  sitting 60% above the measurement is not a budget**. Two intermediate drafts were measured and
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

**`shio diff` — both sides were held and nothing compared them (SH522, `--against capture` only).**
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
(content-tree drift) → `shio apply --check` (document drift) → `shio verify` (content defects).
The first one is new and is first for a reason: it needs no credentials, no network and nothing
stood up, so a defect the files alone prove should never wait for a gate that does. **`projection`
is in `verify`'s default set** and is stripped from what the server is asked for (its vocabulary
does not contain it, and SH320 makes an unrecognised `checks` value a teaching 400) — so the last
gate runs it too, and naming it *alone* is the server-free one. It cannot newly fail a tree that
was passing: the same defect already stopped the pipeline at `push --content`, as an exception on
the first offender rather than a report of all of them. `--delivery` with `projection` alone is
refused, because one asks for a proof that leaves the process and the other for the group that
cannot.
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

