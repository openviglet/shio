/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH897 — the browser check's own shape, guarded from the gate it does not run in.
 *
 * ## Why this file exists at all
 *
 * `e2e/console-check.mjs` needs a browser, a dev server and a live backend, so it is not part of
 * `pnpm test`. SH897's whole deliverable lived there, which meant it shipped with **no assertion
 * carrying its id** — and `cli/test/assertion-debt.test.mjs` said so, in the run right after.
 * That is SH527's rule doing its job, and this is the payment.
 *
 * ## What it guards, which is the mistake that was actually made
 *
 * SH897's first frost check accepted *either* a `backdrop-filter` **or** a non-zero
 * `border-radius`. The second clause made it vacuous: `rounded-3xl` is a Tailwind class on the
 * component itself, so the radius is 24px whether or not `bento.css` is loaded. Deleting the
 * stylesheet and re-running was the only thing that found it — the page rendered flat, borderless
 * and unstyled, `bento-root.png` showed it plainly, and the probe reported **ok**.
 *
 * A source scan cannot repeat that experiment. What it can do is hold the shape the experiment
 * settled on, so the fallback cannot come back on a tired afternoon as a "flaky in headless"
 * fix — which is exactly how a vacuous check gets written the second time.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CHECK = join(HERE, "..", "e2e", "console-check.mjs")

const source = stripComments(readFileSync(CHECK, "utf8"))

test("SH897: the browser check visits the bento route tree", () => {
  assert.match(source, /bento:\s*true/,
    "SH897: no route in console-check.mjs is marked `bento: true`, so the one script that opens a"
      + " real page never opens a bento one. It would run green through the whole of Block AR"
      + " while never visiting a migrated surface — worse than not running, because a green check"
      + " is counted as evidence.")
  assert.match(source, /path:\s*"\/bento"/,
    "SH897: the /bento route is gone from the probe list")
  assert.match(source, /checkBentoSurface/,
    "SH897: the bento probe no longer runs the bento assertions")
})

test("SH897: the frost check reads backdrop-filter, and accepts nothing in its place", () => {
  // From the element lookup to the close of the `else` branch that inspects it. Anchored on the
  // `const glass` line and bounded by the next line that is exactly four spaces and a brace,
  // which is where that block ends — a lazy `[\s\S]*?` to a bare `}` stops at the first nested
  // one and captured a region with no style read in it at all, which is how the first draft of
  // this test failed against source that was correct.
  const start = source.indexOf("const glass = document.querySelector")
  assert.notEqual(start, -1, "SH897: the frosted-surface check is no longer recognisable")
  const end = source.indexOf("\n    }\n", start)
  assert.notEqual(end, -1, "SH897: could not find the end of the frosted-surface block")
  const frost = [source.slice(start, end)]

  assert.match(frost[0], /backdropFilter/,
    "SH897: the frost check no longer reads backdrop-filter. That property is the only thing on"
      + " the page that distinguishes bento.css being loaded from it being absent — every"
      + " `bento-*` class name is in the markup either way.")

  // The regression, named. `border-radius` comes from Tailwind's own `rounded-3xl`, so accepting
  // it is accepting a value that is there when the stylesheet is not.
  assert.doesNotMatch(frost[0], /borderRadius/,
    "SH897: the frost check consults border-radius again. It was removed because it made the"
      + " check pass on a page with no bento.css at all: `rounded-3xl` is on the component, so"
      + " the radius is 24px regardless. Verified by deleting the import — the probe said ok"
      + " while the screenshot showed flat, unstyled divs.\n"
      + "  If backdrop-filter is genuinely unreliable somewhere, find a property the stylesheet"
      + " alone sets. Do not widen this back into an OR.")
})

test("SH897: a probe with nothing to check reports it rather than passing quietly", () => {
  assert.match(source, /checkSaveBarMorph/,
    "SH897: the save-bar morph check is gone. It is the third of §LIV.12's three facts and the"
      + " one that arrives with Block AR's first migrated editor.")
  assert.match(source, /ran:\s*false/,
    "SH897: checkSaveBarMorph no longer distinguishes 'did not run' from 'passed'. This file's"
      + " own history is why that matters: SH238/SH659 found that a probe which skips keeps"
      + " `ok: true`, counts toward 'N/N probes ok', and leaves the exit code at 0.")
  assert.match(source, /probe\.morph/,
    "SH897: the morph's result is no longer recorded on the probe, so it reaches neither the"
      + " report nor stdout and nobody learns it never ran.")
})

test("SH897: no post editor is opened for a static file", () => {
  assert.match(source, /if \(type === "File"\) continue/,
    "SH897: the probe list generates a post-edit route for post-type `File` again. A static file"
      + " is not a post-unified row — /agent/find returns it, /api/v2/staticfile/{id} serves it,"
      + " and /api/v2/post-unified/{id} answers 404 — so the check fails on a defect in itself."
      + " The bare 404 it drove into is SH921.")
})

test("SH927: the check refuses an origin that is not answering as Shio", () => {
  /*
   * `--base` defaults to :5173, which is the Vite default rather than a Shio address — on a machine
   * holding more than one Viglet front end it is whichever dev server was started last. Measured:
   * :5173 was serving `turing-app/src/App.tsx` under the title "Turing ES Administration Console",
   * and this check would have logged in, screenshotted and reported on it without a word.
   *
   * Red-for-the-environment is what SH927 was filed about and it is the smaller problem. A check
   * that answers confidently about the wrong application is one people believe.
   */
  assert.match(source, /const SHIO_TITLE = "Viglet Shio CMS"/,
    "SH927: the check no longer knows what title this console serves, so it cannot tell its own "
      + "front end from another product's on the same port.")
  assert.match(source, /process\.exitCode = 1/,
    "SH927: the identity check no longer stops the run with a failing status. Reporting the "
      + "mismatch and probing anyway produces a full report about somebody else's console, which "
      + "is the outcome this prevents.")

  // And it must NOT be `process.exit`. Measured: exiting there tears down the sockets the identity
  // requests still hold and libuv aborts on Windows, so the refusal left status 127 - which a CI
  // script reads as "crashed" rather than "said no".
  assert.doesNotMatch(source, /process\.exit\(/,
    "SH927: process.exit() is back. It kills the run while the two identity fetches are still "
      + "open, and Windows aborts with UV_HANDLE_CLOSING and an exit status of 127. Set "
      + "process.exitCode and return.")

  // The marker and the file that serves it, held together: a title edited in index.html alone
  // turns this gate off, and a check that refuses every origin reads exactly like a broken server.
  const indexHtml = readFileSync(join(HERE, "..", "index.html"), "utf8")
  const title = /<title>([^<]*)<\/title>/i.exec(indexHtml)?.[1]?.trim()
  const expected = /const SHIO_TITLE = "([^"]+)"/.exec(source)?.[1]
  assert.equal(title, expected,
    `SH927: index.html serves <title>${title}</title> and the check expects `
      + `${JSON.stringify(expected)}. The two have drifted, so every run now refuses to start.`)
})

test("SH927: which server answered is printed, because the two do not behave the same", () => {
  // Monaco's workers start from the jar and fall back to the main thread under the dev server, so
  // "it was red locally" and "it is broken" are different claims and the log has to separate them.
  assert.match(source, /@vite\/client/,
    "SH927: the check no longer probes for the Vite client shim, which is the one thing that "
      + "distinguishes the dev server from the jar without asking the operator to remember.")
  assert.match(source, /server: \$\{server\.kind\}/,
    "SH927: the run no longer prints which server answered. Two probes behave differently between "
      + "them, so a report that does not say which one it walked cannot be compared with another.")
})

test("SH897: the reader is not vacuous — it reads the real script", () => {
  assert.ok(source.length > 10_000,
    `SH897: console-check.mjs read as ${source.length} characters, which is not the script — every`
      + " assertion above would be reporting a missing feature as a missing file")
  assert.match(source, /chromium/,
    "SH897: the script no longer launches a browser, so whatever this is checking, it is not the"
      + " one thing in this repository that opens a real page")

  /*
   * SH1049 — and it reads the script to the END, which is the failure that cost this file an hour.
   *
   * `stripComments` is not a parser, and it says so: a slash followed by two stars opens a block
   * comment to it wherever it appears, including inside a template literal. A Playwright wildcard
   * pattern is spelled exactly that way, so adding one silently truncated the stripped view here
   * and every assertion below the truncation began matching an empty string — green, and about
   * nothing. The length check above did not catch it, because half a large file is still large.
   *
   * The last thing the script does is what this anchors on. It is cheaper than a real parser and
   * it fails on the only shape that has ever caused this.
   */
  assert.match(source, /main\(\)\.catch\(/,
    "SH1049: the stripped view of console-check.mjs no longer reaches its final statement, so it "
      + "was cut short — almost certainly by a slash-star-star somewhere above, which the shared "
      + "stripper reads as a comment opener. Every assertion past the cut is now matching an "
      + "empty string and passing for it.")
})

/**
 * SH927 — the exemption that lets the dev server be red about one thing without being red.
 *
 * Monaco's workers die evaluating their own module graph under the dev server, because
 * `monaco-graphql` reaches CommonJS packages Vite serves raw to a worker. Six fixes are refuted by
 * measurement and named in the script's own comment, so the exemption is the earned answer rather
 * than a shrug — but an exemption is exactly the shape that quietly grows into a blanket excuse,
 * and these are the three properties that stop it.
 */
test("SH927: the worker exemption is scoped to the dev server and to the explorer", () => {
  assert.match(source, /function exemptDevWorkerNoise\(probe, dev\)/,
    "SH927: the exemption changed shape. It takes the dev flag as an argument precisely so the"
      + " decision cannot be made without it.")
  assert.match(source, /if \(!dev \|\| !probe\.graphiql\?\.present\) return false/,
    "SH927: the exemption no longer refuses outright unless this is the dev server AND the probe"
      + " actually rendered the explorer. The jar serves a built bundle where this cannot happen,"
      + " so the same output there must still fail — the jar is what ships.")
  assert.match(source, /MONACO_WORKER_WARNING\b/,
    "SH927: the exemption stopped keying on Monaco's own warning, so it now forgives a"
      + " message-less error for any reason at all — which is every reason.")
})

test("SH927: the exemption edits the array the verdict is computed from", () => {
  /*
   * The bug this holds, which shipped in the first draft and was caught by running it: `probe.ok`
   * is computed from the local `errors` array, and `probe.errors` is that same object. Reassigning
   * the property with a filtered copy leaves the verdict reading the unfiltered one — so the
   * probes stayed red while the report they wrote said `errors: []`. A report and a verdict that
   * disagree is worse than either being wrong.
   */
  assert.match(source, /probe\.errors\.splice\(i, 1\)/,
    "SH927: the exemption no longer splices in place. `probe.ok` is computed from the same array"
      + " object, so assigning `probe.errors = …` filters the report and not the verdict, and the"
      + " probe stays red while its own report says it should not be.")
  assert.doesNotMatch(source, /probe\.errors = probe\.errors\.filter/,
    "SH927: that is the reassignment the line above exists to prevent.")
})

test("SH927: the exemption expires by failing, rather than by being remembered", () => {
  assert.match(source, /devWorkerExemptionUnused && !devWorkerExemptionFired/,
    "SH927: nothing notices when the dev server starts the workers again. An exemption with no"
      + " expiry outlives its reason silently — SH919's own expiry is the shape this copies.")
  assert.match(source, /exemption in\"\s*\+ \" exemptDevWorkerNoise\(\) is spent|is spent/,
    "SH927: the expiry fires without saying what to do about it, so whoever meets it has to"
      + " rediscover the whole task to act on one line.")
})

/**
 * SH1046 — the navigation count is in the one file `pnpm test` cannot run, so its shape is held.
 *
 * <p>The cache's own behaviour is asserted in-process by `test/query-cache.test.mjs`. What only a
 * browser can say is what the *console* then does, and that probe lives here — which puts it
 * exactly where SH897's deliverable was when it shipped with no assertion carrying its id. These
 * hold the two things that would make it vacuous.</p>
 */
test("SH1046: the navigation count compares a second navigation, not a first", () => {
  assert.match(source, /seen\.length = 0/,
    "SH1046: the recorded requests are not reset between the two navigations. Counting both makes"
      + " the total include the first load, which has to read each catalogue once — so the number"
      + " the probe reports would never fall however well the cache works.")
  assert.match(source, /hits > 1/,
    "SH1046: the finding is a catalogue read MORE THAN ONCE inside one navigation. Failing on any"
      + " read at all would fail a cold load, which is not a defect and is not avoidable.")
})

test("SH1046: no child folder is a skip that says so, and --strict makes it fail", () => {
  assert.match(source, /probe\.skipped = "this site has no child folder/,
    "SH1046: a missing fixture must report itself. A probe that silently found nothing to measure"
      + " keeps `ok: true` and reads as coverage — SH238/SH659's lesson, one probe over.")
  assert.match(source, /STRICT && navProbe\.skipped/,
    "SH1046: and in CI, where the fixture is provisioned one step earlier, the skip is the failure")
})

/**
 * SH1047 — the race probe's shape, since `pnpm test` cannot run the race.
 *
 * <p>`test/fetch-cancellation.test.mjs` asserts every screen passes a signal, which is a statement
 * about source. Whether the superseded answer actually loses is a statement about a browser, and
 * these hold the two things that would make that probe report success without having raced.</p>
 */
test("SH1047: the race is held open, and a route that stopped matching is a skip not a pass", () => {
  assert.match(source, /await page\.route\(\(url\) => url\.pathname === listing/,
    "SH1047: the first folder's listing is no longer held, so the second navigation is not racing"
      + " anything and the probe passes on a console with no cancellation at all.")
  // A URL predicate rather than a Playwright glob, and this assertion is where that is kept: the
  // obvious `**/api/v2/...` pattern contains `/**`, which the shared stripper above reads as the
  // start of a block comment — it swallows the rest of the file and every assertion after it
  // matches nothing. Found by writing the SH1049 probe and watching this one go quiet (SH740).
  assert.doesNotMatch(source, /page\.route\(`\*\*\//u,
    "SH1049: a Playwright glob starting `**/` ends this file's view of the script at that line, so"
      + " the assertions below it pass over an empty string. Use a `(url) => …` predicate.")
  assert.match(source, /if \(!probe\.held\)/,
    "SH1047: a route pattern that stopped matching must be a skip that says so. Held zero requests"
      + " and passing is the report-zero shape — indistinguishable from a console that is fine.")
  assert.match(source, /release\(\)/,
    "SH1047: the held request must be released, or the probe measures a timeout rather than a race")
})

/**
 * SH1049 — the failed-read probe's shape, since `pnpm test` cannot fail a read in a browser.
 *
 * <p>`loading-state.test.mjs` refuses a page that models no loading state, which is what a source
 * scan can do. Whether a page that *has* one shows anything useful when the read actually fails is
 * a statement about a browser, and these hold the two things that would make that probe pass
 * without having failed anything.</p>
 */
test("SH1049: the failed-read probe actually fails the reads, and only the reads", () => {
  assert.match(source, /status: 500, contentType: "application\/json", body: FAILED_BODY/,
    "SH1049: the probe no longer answers anything with 500, so it drives three healthy pages and"
      + " reports that a broken instance looks fine.")
  assert.match(source, /target\.request\(\)\.method\(\) === "GET"/,
    "SH1049: failing the writes too would make the page's own save paths the subject, which is"
      + " SH955's question and not this one")
  assert.match(source, /probe\.skipped = "none of the sampled routes/,
    "SH1049: the sampled routes are named, so a rename must be a skip that says so rather than a"
      + " loop over an empty list reporting success — the report-zero shape again.")
})

test("SH1170: a request that never connected is named, and a cancelled read is not a failure", () => {
  // A timed-out connection has no response, so only `requestfailed` can name its URL; the console
  // aborts superseded reads on purpose (SH1047), so ERR_ABORTED must stay out of the failures.
  assert.match(source, /page\.on\("requestfailed"/,
    "SH1170: the check no longer listens for failed requests, so a probe can fail on"
      + " ERR_CONNECTION_TIMED_OUT with no URL in the report")
  assert.match(source, /ERR_ABORTED"\)\) return/,
    "SH1170: aborted reads are counted as failures, which turns the console's cancellation into reds")
})