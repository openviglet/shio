---
name: shio-replication-test
description: Drive and judge a replication run against a live instance — capture an external site, convert it, publish it, and prove the replica. Read before validating a Block Q or Block P change by hand, before answering "does the replica work", and whenever a conformance run is red. Triggers on shio clone, propose, convert, /sites/** delivery, --render, --scripts, fidelity vs authorable mode, the Universal Editor on a replica, snapshot --against, pnpm run conformance, a stale instance, and any question about whether a replicated page looks or behaves like its source.
---

# Judging a replication run

The conformance suite (`pnpm -C cli run conformance`) is the gate. This file is for the
half a suite cannot do: driving a real external site through the chain and deciding
whether what came out is a replica. Every rule below was paid for by a defect that shipped
green.

## Before anything: is the instance the code you are testing?

```
curl -s -u admin:viglet localhost:2710/api/v2/agent/manifest?include=identity
```

`commit` and `builtAt` (SH460) against `git rev-parse --short HEAD`. **A stale instance
produces failures indistinguishable from defects** — it once cost a session and nearly two
tasks filed for bugs that did not exist. `version` reads `dev` and proves nothing. Do this
first, always, and do not diagnose a red run before it passes.

**Read `commit` first and stop there when it is HEAD (SH500).** `builtAt` is *earlier* than
its own commit on every normal build — compile, test, then commit — so comparing the clocks
after the hash already matched calls a current instance stale, which is how the suite came to
cancel four tests against HEAD. The timestamp answers only what the hash cannot: a commit that
is not HEAD, or a tree with tracked changes. `SHIO_CONFORMANCE_ALLOW_STALE=1` is still the
hatch for an instance you did not build.

## The chain, and what each step must produce

```
clone --depth N --assets --markup [--render] [--scripts] --site X
propose [--accept all]            # accept nothing = fidelity, accept = authorable
convert --site X [--create-site]  # must exit 0 — SH456 made it lie about that
post.publish every object          # find returns `truncated`; READ IT (it caught me out)
```

Then judge on the **public** route, never the preview: `/sites/<furl>/default/en-us/`.
The preview's banner shifts every pixel, so a comparison there can never reach zero, and
`/preview` renders DRAFT so an unpublished asset looks fine and 404s in public (SH462).

## Three scenarios, and a run must say which one it is

**A validation round is not complete until all three have run.** They fail differently, they
are proved differently, and a number quoted without its scenario is meaningless.

This rule is written down because it was broken twice, the same way both times: a round
validated fidelity plus the conformance suite, reported green, and never touched authoring —
so the task that had just shipped *for* the authorable path went unexercised, and the human
had to ask. Fidelity is the pleasant one to test (it reaches 0.00% and looks finished), which
is exactly why it is not evidence about the other two. Say which scenarios ran; if one did
not, say that instead of reporting a round as done.

### 1. Fidelity — the site replicated inside Shio

`propose` and accept **nothing**. `convert` writes Theme + PageLayout + Page from the
source's verbatim markup; Shio's own renderer serves it. No front end, no build.

*Proof:* `/sites/<furl>/default/en-us/` is 200 anonymously, every reference resolves, and
`snapshot --against` reaches **0.00%**. That has been achieved — it is the bar, not an
aspiration.
*Known limit:* zero `data-shio-prop`. Verbatim markup has no `sh-field`, so the editor loads
over a page with nothing to edit. This is the documented property, not a defect.

### 2. Authorable — the same page as content a human can curate

`propose --accept all`, then `convert`. Blocks become SH95 sections; the generated layout
annotates each field.

*Proof:* `/preview/<site>/?editor=true` is 200 **and** carries `data-shio-prop` per field,
`data-shio-post` per section, `_shio/editor.js` injected and `data-shio-editor-origin` set.

**Count, do not eyeball.** Take the props the page emits and compare them against the fields
each section type declares — **including the fields of every collection row**. Four of seven
types are collection-shaped (`features`, `questions`, `logos`, `quotes`), so a page that looks
annotated can be five props over a grid whose two cards carry none. Rendering is not
annotation: the rows once did not draw at all (SH465), then drew with no `sh-post` and no
`sh-field` (SH475), and the visible page looked reasonable at every stage.

Read one extracted string against the source, too. An entity survives extraction as
characters and is escaped again on render, so `&` becomes `&amp;` in the page *and in the
editor field* (SH476) — invisible unless you compare.

*Known limit:* unstyled. The captured classes are dropped, so the theme's CSS matches
nothing. `propose.mjs` calls this "the pixels have moved".

### 3. Headless — the front end over the CDA

`shio init <dir> --site <site>` scaffolds the Next.js app; the sections are delivered through
the CDA and `@viglet/shio-sections` renders them.

*Proof:* `npm install --dry-run` in the scaffold **resolves** — it once failed ETARGET because
the versions written were unpublished (SH446), and no test installed from the registry. Then
the app runs and the site's `url` points at it, which is what the console frames instead of
the preview.
*Known limit:* this is the path `renderer.md` calls production delivery (P10), and the one
whose failure is invisible from inside the instance.

**For a site whose pages are sections, this scenario cannot be run at all yet (SH502).** Measure
per template, because they answer differently and either one alone reads as "the scaffold
installs": the **built-in** template exits 0 and contains no sections renderer (nothing under
`app/` or `lib/` reads a Page's `sections`), and **`marketing-site`** — the only scaffold that
draws them — answers **E404 on `@viglet/shio-sections`**, never published. So `init --site` now
*refuses* the built-in template for a section-carrying site, and the honest answer for an
authorable replica's delivery is Shio's own renderer at `/sites/<site>/default/en-us/`. Do not
report scenario 3 as covered by a scaffold that installs; ask which template, and what it renders.

## The seven checks, in the order that finds things

1. **Every reference the page makes resolves.** Fetch each same-origin `src`, `<link href>`
   and CSS `url()` and expect 200. This is the check that finds what a screenshot cannot:
   it caught the fonts (SH455), the escaped tree path (SH461), and the unpublishable
   assets (SH462).
2. **No href carries the source's base.** Every same-origin link must start with the
   serving base. A verbatim href is a hardcoded base and always points off-site (SH466).
3. **Both spellings of a directory path answer** — `/shio` and `/shio/`, one canonical and
   one redirecting, the way the source does (SH467).
4. **`convert` exits 0.** Read the process's code, not the module's return (SH456).
5. **`verify --site X`** — and read the *whole* first run after any lint fix. Two rules
   disagreed once and the louder one was wrong, hiding the correct one entirely (SH470 →
   SH474). **A finding on an asset is the one to distrust first**: an uploaded `File` has no
   friendly URL and is reached by path, and rules written for content posts have now been
   applied to it twice — as an address (SH470) and as a missing furl (SH501). Never act on a
   fix that would give a `File` a furl; it corrupts the content model to clear the message.
   The bar is **a replica whose every reference answers 200 verifies clean**.
6. **`snapshot --against <source> --width 1280`** on the public URL. Open the PNG. A number
   is not a result: 100% once meant a photograph of `{"error":"Unauthorized"}` (SH450), and
   once meant a font substitution on an otherwise perfect page.
7. **Click something.** A replica can be 0.33% from its source and completely inert —
   viglet.org's dark/light toggle and consent modal are React handlers, and the bundle is
   not captured (SH469, SH473). Pixels prove appearance and nothing else.

## Reading the tool's own notes

`clone` teaches the next flag and it is usually right. "almost no text with scripts present
… re-run with `--render`" turned a blank capture of a React SPA into six pages with
navigation. Follow the note before diagnosing.

## The failure shape to distrust

**The report ships ahead of the behaviour it reports on.** Three times: SH452 fixed a note
and not the fetch (→ SH455), SH469 added `--scripts` and the third-party list and not the
first-party download (→ SH473), and SH429 made stranded bytes visible and left delivery
unbuilt (→ SH437). When a phase *describes* an omission, check that it also closed it.

The sibling shape: **a fix measured against a harness rather than the failing command.**
SH434 → SH440 → SH445 was one abort fixed three times because nobody ran the command whose
exit code was the product.

## Cleaning up

`site.delete` exists since SH463 — use it. Before that, teardown meant reading
`GET /api/v2/csrf`, keeping the cookie and echoing the token, which is a browser's dance an
agent should never need. A failed conformance test never reaches its teardown, so
`ShioConf*` sites accumulate; sweep them.

Fixtures worth keeping: one fidelity replica and one native-render site. Delete the rest.

## Where the detail lives

[docs/agents/renderer.md](../../../docs/agents/renderer.md) for the render model and the
SH185 editor protocol · [docs/agents/content-files.md](../../../docs/agents/content-files.md)
for the capture and the CLI · `cli/conformance/` for the suite this file surrounds.
