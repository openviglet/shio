# The shio CLI

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### `shio` CLI (`@viglet/shio`, in `cli/`)
The "JS dev, zero Java" command-line tool: `init` (scaffold via `create-shio-app`; **`--blueprint <name>` takes the starter from the instance instead, and with `--site` applies its content half in the same command** — SH119), `types` (SH11 codegen), `pull`/`push` (post-types as code → `shio/post-types/*.json`, `--dry-run`/`--prune`; **`--content` switches both verbs to content**, see below), `context` (`--as agents-md` renders the pack as project agent instructions — SH115),
`resolve`, `apply` (SH129 — a desired-state content document; `--check` is the CI drift guard, `-` reads stdin; **`--blueprint <name>` applies a package instead**, with `--param k=v` repeatable, `--params <file>`, `--site`, `--force`), `blueprints` (SH92 — the catalogue; `blueprints <name>` for one package's parameters), `verify` (SH99/SH100 — the content lint; **exits 1 on an error**, `--strict` also on warnings, `--folder`/`--address`/`--since` scope it per SH143, `--baseline`/`--accept` subtract accepted findings per SH142, **`--delivery` adds SH141's route proof** — it appends the group rather than replacing `--checks`, since a caller wanting the stronger proof still wants the lint — and the report's `notes` are printed, because an unreported cut reads as a clean pass), `digest` (SH101 — one page's structure), `snapshot` (SH102 — **a PNG for a human**, plus whether the page looks different; baselines in `shio/snapshots/`, `--accept`/`--fail-on-change`/`--width`. `playwright` is an **optional** dynamic import with a teaching error naming the install, so `@viglet/shio` never pulls a browser; with `pixelmatch` + `pngjs` the diff is per-pixel, without them it is byte-level and the output says which. **A change exits 0** — a page looking different is usually the change you just made, and a gate here would be the one people wrap in `|| true`. The manifest still reports `visualSnapshot: false`, correctly: there is no endpoint and no MCP tool, and an agent that read `true` would hunt for a call that does not exist. **A site with no front end is photographed through the instance's own preview (SH265)** — `resolveUrl` used to refuse with "there is no page to photograph", which Block P made false: SH183 renders the page out of content the instance already holds and SH184 stopped the *server* saying the same thing, so the CLI was the same premise left standing one process along. `--preview` forces it even for a site that has a front end, which is the only way to photograph a **draft**. The capture carries the instance's Basic credentials **only for that origin** — the preview is authenticated since SH359, and handing a site's public host an admin password because one command photographs both would be a leak nobody asked for — and the output says the picture is a DRAFT with the banner on it, because the image cannot say so itself), `changes` (SH88 — the feed; `--since last` reads the last content pull's position, `--cursor` prints only the position), `report` (SH104 — **the human handoff**: what changed with a preview link each, plus the warnings as open questions, as Markdown for a PR body; `--since last`/`content`, `--mark`, `--out`, `--title`, `--no-verify`, `--json`), `remember` (SH114 — the instance memory; bare lists it, `<key> "<note>"` writes one, `--forget` drops one, `--site` scopes it), `mcp`, `dev` (`--content` runs the SH90 sync loop instead of the site's dev server), `deploy`. `pull`/`push` use console Basic auth (`SHIO_USER`/`SHIO_PASSWORD`); `types` uses a CDA token (`SHIO_CDA_TOKEN`).
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
later phase rests on** (`clone.test.mjs` asserts the file count): the thing a conversion is supposed
to let you review would otherwise already exist. Four rules the walk keeps. **One origin** — every
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
**A soft 404 is caught here or nowhere**: a response of `text/html` where an image was expected would
land in the media library under a `.png` name that every later check reads as a present asset.
`--max-assets` and `--max-asset-bytes` bound it, the latter checked against `content-length` *and*
against what actually arrived, since a server that under-declares would otherwise walk straight past
the ceiling whose whole job is not filling a disk.

**`shio propose` is the convert phase, and what it ships is the *proposal* (SH379).** A captured
block has two honest futures — verbatim markup in a `PageLayout` (renders identically, **nothing is
authorable**: a curator editing "the hero headline" is editing an HTML file) or an SH95 section
(authorable, and the pixels have moved) — so the deliverable is neither, it is the artefact between
them. Per candidate: the pages it recurs on, the section type it looks like **and why**, the fields it
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
The exception is a value holding a `url()`: the kept CSS has been rewritten to the tree path and the
token has not, so those stay declared and stay **named in a note** — a token that cannot be edited is
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
tree (no new request), and lands in the **same** proposal file: a candidate's loss line says "3 class
names — all styling", and this is where that styling went. Six decisions:
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
- **Every `url()` is rewritten to its SH378 tree path**, resolved against the **stylesheet** and not
  the page — `url(../fonts/x.woff2)` in `/assets/css/site.css` is `/assets/fonts/x.woff2`, and the
  page-relative reading names a file the download never wrote. That is what makes the theme portable
  instead of a set of links to somebody else's origin. One the asset map does not hold is left exactly
  as it was and named: rewriting it to a path with no bytes turns SH378's named licensing gap into a
  broken replica.
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
SH365 was a hard dependency rather than a courtesy: `--url` was already two arguments wearing one name,
and a third meaning would have made the flag that says where Shio is also decide what it is compared
to. No endpoint and no MCP tool — SH268's rule plus the standing no-browser non-goal — so
`visualSnapshot: false` stays correct and `elsewhere.visualSnapshot` keeps pointing at the command.

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

**One test drives the whole pipeline, and it is the only one that can (SH422).** Block Q shipped as
eleven tasks, each thoroughly tested in isolation — and every phase hands an artefact to the next, so
each handoff is a shape agreed in two places with a unit test pinning one side and *constructing the
other by hand*. That is the shape that passes while the real producer writes something else.
`replication-chain.test.mjs` therefore runs `clone --markup --assets` → `propose` → `propose --accept`
→ `verify --checks projection,replication` over one served fixture site, asserting at each step that
what the last phase **wrote** is what this one reads: the inventory row naming a markup file that holds
that page, a candidate whose `pages` are the crawl's own paths, a `url()` rewritten onto the tree path
the capture chose (so the asset map is *the* map and not a copy), the accepted id coming back
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
**Accepted findings (SH142) live in `shio/verify-baseline.json`** — `(rule, address, field)` triples that `shio verify` subtracts, written by `--accept` (`--reason` optional) and merged, never replaced. The half that matters: **an accepted finding the run no longer produces fails the run**, so the file cannot rot into a blanket suppression that hides the next defect at the same address; a *truncated* report reports the unmatched entries without failing, because a finding that did not fit is not a finding that is gone. **Staleness is a claim about coverage, not about findings (SH410)** — "nothing matched this entry" only implies "the defect is gone" when the run *could* have produced it, and truncation was the first way to lose that guarantee and the only one guarded. Group selection and site scoping lose it identically and are the normal case: `--checks projection` sets `callRemote: false`, so every acceptance for a server rule matched nothing, was called stale, and **failed the build** — the server-free CI gate broken on any project holding a baseline, with `--checks content` breaking it in reverse and `--site Blog` condemning every other site. `applyBaseline` now takes the run's coverage and judges only what it could have produced; the rest is `staleUnknown`, which already meant "unproven" and already said so. A rule is **projection or the server's** — a third "unknown" bucket counting as covered would reinstate the defect exactly, since `required-field-empty` is not a projection rule. **SH411 then split the two claims apart**, because SH410 fixed a false positive by creating a false negative: it folded coverage into `staleUnknown`, which `verify` reads all-or-nothing (that flag used to mean *truncated*, a property of the whole report). One unproven entry therefore silenced every proven one — measured, a baseline holding `asset-missing` and `required-field-empty` run tree-only computed `stale: [asset-missing]` correctly and reported nothing — so the file could rot in exactly the way SH142 exists to prevent. `unproven` is now its own list on its own line and never fails the build; `stale` still does; `staleUnknown` is truncation's again. And **`PROJECTION_RULES` is checked against the rule literals in `verify-projection.mjs`** rather than hand-kept — it was enumeration where `path-safety.test.mjs` had already established discovery, and every rule in it was added by a task in the same run. It is deliberately a project file and **not** an instance setting — `GET /agent/verify` keeps reporting everything, and only the gate subtracts. **Since SH214 the same file carries a second list, `acceptedMoves`** — `(from, to)` pairs written by `shio push --content --accept`, for a delete-plus-create that is legitimate and would otherwise print an unprovable-rename warning for ever. Both addresses are the key (a one-sided one would silence the warning for a different arrival, which is the case it exists for), and the entry is **spent** by the push that honours it rather than checked for staleness: a probable move is transient, so SH142's rule would fail the *next* run for doing what was asked, and consuming it means an acceptance cannot outlive its event. Each command reads only its own list and writes the other's through untouched — without that, `verify --accept` would silently un-accept every rename somebody reviewed. The content gates read **only the sites the tree holds** (SH173, derived from `shio/content/<site>/**`), a post they cannot read by address is a named finding rather than a fatal 404, and a remote failure prints the SH80 body — the request URL is behind `SHIO_DEBUG`. The CLI test suite needs `@viglet/shio-client` built first (`pnpm -C shio-client run build`), or every command test fails on a missing module rather than on its own logic; `shio-sections`' suite needs `@viglet/shio-model` built (`pnpm -C shio-model run build`) for the same reason.

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

