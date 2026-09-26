/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH448 — the source site, served over a real socket.
 *
 * `replication-chain.test.mjs` (SH422) hands `clone` an injected `fetch` that answers from an
 * object literal, which is the right harness for a chain of pure phases and the wrong one for
 * this suite: an injected fetch decides the response's shape, so it can never disagree with
 * what the crawler assumes. Everything between `clone.mjs` and a web server — the `Response`
 * object's real API, header casing, `content-type` with a charset, a 404 that is a status and
 * not a thrown error, a redirect — is exactly the layer the injection removes.
 *
 * So this is a `node:http` server on an ephemeral port, and the conformance run passes the CLI
 * the platform's own `fetch`. Same fixture content as SH422's, deliberately: the two files
 * should differ in *where the bytes come from* and nowhere else, so a divergence between them
 * is a statement about the boundary rather than about the fixture.
 */
import { createServer } from "node:http";

/**
 * `/deep` is linked from `/about` alone, so `--depth 1` is what stops the crawl reaching it, and
 * `/admin` is linked from every page and stopped by robots.txt. Two `unfollowed` rows, cut for two
 * different reasons — the pair SH387's rule is about.
 */
const PAGES = {
  "/": page("Home", "Acme", ['<a href="/about">About</a>']),
  "/about": page("About", "Who we are", [
    '<a href="/deep">Deeper still</a>',
    // SH528 - depth 2, so `--depth 1` never reaches the traits and its counts are unchanged.
    '<a href="/traits">Traits</a>',
  ]),
};

/**
 * The webfont is referenced **only from here**, which is the SH452 case: `clone` builds its asset
 * list from the markup, so a font, a background image and an icon sprite are invisible to it — and
 * a fidelity replica whose fonts 404 renders in a serif fallback while every test passes.
 */
const STYLESHEET = `:root { --ink: #102a43 }
@font-face { font-family: "Fixture"; src: url("../fonts/fixture.woff2") format("woff2") }
.hero { color: #102a43; background: #f0f4f8 url("/img/hero.png") no-repeat }
.hero h1 { color: #102a43; font-size: 2rem; font-family: "Fixture", serif }
.card { color: #102a43; background: #f0f4f8 }`;

/** `wOF2` — the magic a real WOFF2 starts with, which is all this fixture needs to be one. */
const WOFF2 = Buffer.concat([Buffer.from("wOF2", "ascii"), Buffer.alloc(60)]);

/**
 * The logo, at the **root** of the URL space — the one thing this fixture was missing (SH458).
 *
 * <p>`<link rel="icon" href="/favicon.ico">` is what every site has and what the measured capture
 * actually downloaded ("wrote 1 asset"), and its path has no folder segment. That makes it the only
 * asset here whose upload takes `convert`'s root-folder branch, which is the branch SH456 is about:
 * every other asset in this fixture lives under `/img/` or `/fonts/` and never reaches it. A fixture
 * where nothing is at the root is a fixture that cannot see a defect in the root case.</p>
 */
const FAVICON = Buffer.concat([
  // A 1x1 ICO header, which is enough for the storage layer to probe it as an image.
  Buffer.from("00000100010001010000010020003000000016000000", "hex"),
  Buffer.alloc(48),
]);

/** A one-pixel PNG, as real bytes: a stylesheet's `url()` target has to be downloadable. */
const HERO_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);

/**
 * SH528 — the traits that broke a real replica, kept where they can be reproduced offline.
 *
 * <h2>Why the fixture needed them</h2>
 *
 * <p>Every defect this work found came from a real site and none could be reproduced without one.
 * A real site is how a defect is <em>found</em>; it is a bad way to keep one, because the site
 * changes, the network fails, and a red run then means nothing until somebody works out which.
 * Each entry below exists because a task needed it — never because a site happened to have it.</p>
 *
 * <h2>They sit at depth 2, deliberately</h2>
 *
 * <p>Reached only from `/about`, with a stylesheet of their own. The conformance run crawls
 * `--depth 1`, so it meets none of this and its asset list, theme and counts are exactly what they
 * were. A trait is a thing a test opts into, not a thing every other assertion has to absorb.</p>
 */
/** SH505 — a fixed overlay, which is what put a consent banner into three sections' `intro`. */
const TRAITS_CSS = `.consent { position: fixed; bottom: 0; left: 0; right: 0; background: #102a43 }
.consent p { color: #fff }
.tile { color: #102a43; background: #f0f4f8 }`;

/**
 * One page carrying the traits a scanner has to survive, each named with the task that needed it.
 *
 * <p>SH476's double escape is the one worth reading twice: the source says `&amp;amp;`, so a reader
 * that decodes once produces `&amp;` and one that decodes twice produces `&`. The page's own title
 * carries it, because SH565 found the title travelling through a path that decoded neither.</p>
 */
const TRAITS_PAGE = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>Terms &amp;amp; Conditions</title>
  <link rel="stylesheet" href="/css/traits.css">
</head>
<body>
  <!-- SH505: fixed, and first in the source - the position every scanner reads as the lead. -->
  <div class="consent"><p>We use cookies. <a href="/en-us/privacy">Privacy</a></p></div>
  <main>
    <h1>Terms &amp;amp; Conditions</h1>
    <!-- SH507: a query string on an asset path, which the file name must not swallow. -->
    <img src="/img/hero.png?v=2" alt="Cache-busted">
    <!-- SH514: zero bytes, which the upload refused rather than reported. -->
    <img src="/img/empty.png" alt="Empty">
    <div class="tile"><h3>One</h3><p>Body copy that reads like a page a person wrote.</p></div>
    <div class="tile"><h3>Two</h3><p>Body copy that reads like a page a person wrote.</p></div>
    <p><a href="/docs/guide/intro/detail/">Four deep</a> · <a href="/gone">A link that is wrong</a></p>
  </main>
  <!-- SH517: the canvas exists only after this runs, so --render bakes it and a plain fetch does not. -->
  <script>document.body.appendChild(document.createElement("canvas"));</script>
</body>
</html>`;

/**
 * SH512 — a reference resolved four segments below its source.
 *
 * <p>`./../../logo.svg` from `/docs/guide/intro/detail/` is `/docs/guide/logo.svg`. Against the
 * origin it would be `/logo.svg`, and against the page's own directory `/docs/guide/intro/logo.svg`
 * — three different files, only one of which exists, which is why the depth is four and not one.</p>
 */
const DEEP_PAGE = `<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><title>Four deep</title></head>
<body><main><h1>Four deep</h1>
  <img src="./../../logo.svg" alt="Logo">
  <p>Body copy that reads like a page a person wrote.</p>
</main></body>
</html>`;

/** SH433/SH576 — a site whose real home is under a locale prefix, so `/` is not the front door. */
const LOCALE_PAGE = `<!doctype html>
<html lang="en-US">
<head><meta charset="utf-8"><title>Privacy</title></head>
<body><main><h1>Privacy</h1>
  <p>Body copy that reads like a page a person wrote.</p>
</main></body>
</html>`;

const LOGO_SVG = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8"><rect width="8" height="8"/></svg>';

function page(title, lead, links = []) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>${title}</title>
  <link rel="stylesheet" href="/css/site.css">
  <link rel="icon" href="/favicon.ico">
</head>
<body>
  <main>
    <section class="hero">
      <h1>${title}</h1>
      <p>${lead}. ${"Body copy that reads like a page a person wrote. ".repeat(4)}</p>
      <a href="/admin">Start</a>
    </section>
    <section class="features">
      <h2>Features</h2>
      <img src="/img/hero.png" alt="Hero">
      <div class="card"><h3>Fast</h3><p>Measured, not asserted.</p></div>
      <div class="card"><h3>Safe</h3><p>Draft by default.</p></div>
    </section>
    ${links.join("\n    ")}
  </main>
</body>
</html>`;
}

/**
 * Start the fixture on 127.0.0.1 and an ephemeral port.
 *
 * @returns {Promise<{ origin: string, requested: string[], close: () => Promise<void> }>}
 *   `requested` is every path the crawler actually asked for — the assertion that the walk
 *   was bounded is about the requests, not about the artefact it produced.
 */
export async function serveFixture() {
  const requested = [];
  const server = createServer((req, res) => {
    const path = new URL(req.url, "http://127.0.0.1").pathname;
    requested.push(path);
    if (path === "/robots.txt") return send(res, 200, "text/plain", "User-agent: *\nDisallow: /admin\n");
    if (path === "/css/site.css") return send(res, 200, "text/css", STYLESHEET);
    // `../fonts/` from `/css/site.css` — resolved against the *stylesheet*, so this is `/fonts/`.
    if (path === "/fonts/fixture.woff2") return send(res, 200, "font/woff2", WOFF2);
    if (path === "/img/hero.png") return send(res, 200, "image/png", HERO_PNG);
    if (path === "/favicon.ico") return send(res, 200, "image/x-icon", FAVICON);
    // --- SH528: the catalogue, all of it at depth 2 or below ---
    if (path === "/css/traits.css") return send(res, 200, "text/css", TRAITS_CSS);
    if (path === "/traits") return send(res, 200, "text/html; charset=utf-8", TRAITS_PAGE);
    if (path === "/docs/guide/intro/detail/") {
      return send(res, 200, "text/html; charset=utf-8", DEEP_PAGE);
    }
    if (path === "/docs/guide/logo.svg") return send(res, 200, "image/svg+xml", LOGO_SVG);
    if (path === "/en-us/privacy") return send(res, 200, "text/html; charset=utf-8", LOCALE_PAGE);
    // SH514 - zero bytes, served with a 200 and an image content-type, which is exactly how the
    // real one arrived: nothing about the response says it is empty.
    if (path === "/img/empty.png") return send(res, 200, "image/png", Buffer.alloc(0));
    // /deep exists and is linked, and `--depth 1` is what stops the crawl reaching it: an
    // `unfollowed` row has to mean "not visited", never "not there" (SH387).
    if (path === "/deep") return send(res, 200, "text/html; charset=utf-8", page("Deep", "Never fetched"));
    if (path === "/admin") return send(res, 200, "text/html; charset=utf-8", page("Admin", "Disallowed"));
    const html = PAGES[path.replace(/\/$/, "") || "/"];
    if (!html) return send(res, 404, "text/plain", "not found");
    return send(res, 200, "text/html; charset=utf-8", html);
  });

  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  return {
    origin: `http://127.0.0.1:${port}`,
    requested,
    close: () => closeFixture(server),
  };
}

/** How long a teardown may take before it is a finding rather than a wait (SH698). */
export const CLOSE_DEADLINE_MS = 2000;

/**
 * Where a server's in-flight close is remembered, so a second call joins it (SH780).
 *
 * <p>A symbol rather than a module-level `WeakSet`, so the state travels with the server a caller
 * holds — the conformance harness and the trait suite each make their own, and a shared registry
 * would be one more thing to reason about at teardown, which is where this defect lives.</p>
 */
const CLOSING = Symbol("shio.fixture.closing");

/**
 * Close the fixture, and say what happened if it will not (SH698).
 *
 * <h2>Why a deadline at all</h2>
 *
 * <p>`fixture-traits.test.mjs` was seen to fail <b>at file level with every assertion green</b>,
 * once in five `pnpm test` runs, printing `'test failed'` and naming nothing. A teardown that never
 * settles is one of the few shapes that produces exactly that, and a plain `server.close(resolve)`
 * is silent about it forever: it waits on open connections with no timeout and no report.</p>
 *
 * <p><b>This is instrumentation, not a fix, and the difference is deliberate.</b> The two mechanisms
 * that section proposed were measured and neither holds — `close` resolves in under a millisecond
 * with eight connections outstanding, and the port has been ephemeral since SH448. Eighteen
 * consecutive runs (twelve ordinary, six at `--test-concurrency=32`) did not reproduce it. So
 * nothing here claims to have removed the cause. What it removes is the *silence*: if this ever
 * hangs again, the run says which server, for how long, and how many sockets were still open,
 * which is the difference between a second data point and a second mystery.</p>
 *
 * @param server       the listening `node:http` server
 * @param deadlineMs   how long to wait before reporting (default {@link CLOSE_DEADLINE_MS})
 * @returns resolves when closed; rejects, naming the state, when the deadline passes
 */
export function closeFixture(server, deadlineMs = CLOSE_DEADLINE_MS) {
  // SH780 — close once, and leave nothing for the forced exit to trip over.
  //
  // The failure this fixes is not a test failure at all. Under `--test-force-exit` the process was
  // aborting inside libuv — `Assertion failed: !(handle->flags & UV_HANDLE_CLOSING), src\win\async.c`
  // — after every assertion in the file had passed, so `node --test` marked the FILE red with no
  // sub-test to name. An abort also skips `process.on("exit")`, which is why SH778's instrument
  // stayed silent through some hundred and twenty runs and read as evidence against its own
  // hypothesis.
  //
  // Two things left work pending for that exit to race. `fetch` keeps its connections alive, so
  // `server.close()` waits on **idle keep-alive sockets** that no test is using any more; and
  // nothing stopped this being called twice, so a second close could arrive on a handle already
  // closing. Both are addressed here rather than by dropping `--test-force-exit`, which is what
  // holds the gate to a bounded run.
  if (server[CLOSING]) {
    return server[CLOSING];
  }
  // **Idle only, never all.** `closeAllConnections()` also destroys a socket somebody is holding on
  // purpose, and reaching for it once made SH698's own deadline test go red — this defect's shape a
  // second time: an instrument that can no longer fire. An idle keep-alive socket is by definition
  // one no test is using, so closing those removes the wait without removing the thing being waited
  // for.
  //
  // **What this line does NOT leave holdable, which is SH1146.** An *accepted socket that has sent
  // nothing* is idle by this definition too, so this call reclaims it — measured on Node 26 Linux,
  // `close` then completes in 0ms with the socket outstanding. SH698's deadline test used to hold
  // exactly that and passed only on Windows, reporting on every Linux runner the silence it exists
  // to catch. A socket that holds this close open has to be **active**: a request in flight, which
  // is what that test now holds and what `SH1146:` beside it asserts an idle one is not.
  //
  // Node 18.2+. Optional-called because this module is also imported by the conformance harness,
  // and a fixture helper that throws on an older runtime would turn a teardown into the failure.
  server.closeIdleConnections?.();
  const closing = new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      // `getConnections` is the only thing that can say *why* it is still waiting, and it is worth
      // the extra tick: "2 sockets open" and "0 sockets open" point at completely different bugs.
      server.getConnections((err, count) => {
        const open = err ? "an unreadable number of" : String(count);
        reject(new Error(
          `SH698: the fixture server on ${JSON.stringify(server.address())} did not close within `
          + `${deadlineMs}ms, with ${open} connection(s) still open. This is the teardown that made `
          + "the whole file red with every assertion in it green — if you are reading this, the "
          + "intermittent failure finally named itself. A non-zero count means a request outlived "
          + "its test; zero means `close` is waiting on something that is not a socket.",
        ));
      });
    }, deadlineMs);
    // Unref'd so a fixture that closes normally never holds the process open for the deadline.
    timer.unref?.();
    server.close((err) => {
      clearTimeout(timer);
      if (err) reject(err);
      else resolve();
    });
  });
  server[CLOSING] = closing;
  return closing;
}

function send(res, status, type, body) {
  res.writeHead(status, { "content-type": type, "content-length": Buffer.byteLength(body) });
  res.end(body);
}
