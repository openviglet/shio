/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
  "/about": page("About", "Who we are", ['<a href="/deep">Deeper still</a>']),
};

const STYLESHEET = `:root { --ink: #102a43 }
.hero { color: #102a43; background: #f0f4f8 url("/img/hero.png") no-repeat }
.hero h1 { color: #102a43; font-size: 2rem }
.card { color: #102a43; background: #f0f4f8 }`;

/** A one-pixel PNG, as real bytes: a stylesheet's `url()` target has to be downloadable. */
const HERO_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
  "base64",
);

function page(title, lead, links = []) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>${title}</title>
  <link rel="stylesheet" href="/css/site.css">
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
    if (path === "/img/hero.png") return send(res, 200, "image/png", HERO_PNG);
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
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

function send(res, status, type, body) {
  res.writeHead(status, { "content-type": type, "content-length": Buffer.byteLength(body) });
  res.end(body);
}
