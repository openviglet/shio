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

**CI gates, in the order they should run:** `shio verify --checks projection` (tree defects — **no
instance**, SH212/SH374: a broken asset path, a `files:` entry naming nothing, a dangling
`translationOf`) → `shio push --check` (model drift) → `shio push --content --check`
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

