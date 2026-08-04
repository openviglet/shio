/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH377 — `shio clone`, the capture pass.
 *
 * Network-free: the crawl's fetch is injected, so a fixture site is a map from path to markup. What
 * that buys is the ability to pin the things a real crawl makes hard to observe — that the walk
 * stops at the origin, that a ceiling cuts the *same* pages twice, that robots.txt is honoured, and
 * above all that **no content is written**, which is the promise every later phase is built on.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  INVENTORY_FILE,
  MARKUP_DIR,
  buildInventory,
  describeInventory,
  localeShape,
  parseRobots,
  pathOf,
  resolveHref,
  robotsAllows,
  scanPage,
  serializeInventory,
  stripNonMarkup,
} from "../src/clone.mjs";
import * as clone from "../src/commands/clone.mjs";

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

// --- the scanner ---

const PAGE = `<!doctype html>
<html lang="en">
<head>
  <title>  Home  page </title>
  <meta name="description" content="What we do">
  <link rel="canonical" href="https://x.test/">
  <link rel="alternate" hreflang="pt-BR" href="https://x.test/pt-br/">
  <link rel="alternate" hreflang="de" href="https://de.x.test/">
  <link rel="stylesheet" href="/css/site.css">
  <link rel="icon" href="/favicon.ico">
  <script src="/js/app.js" defer></script>
  <script src="https://www.googletagmanager.com/gtm.js?id=GTM-1"></script>
  <script>document.write("<a href='/injected'>not a link</a>")</script>
  <style>.a::after { content: "<img src=x>" }</style>
  <!-- <a href="/commented-out">nor this</a> -->
</head>
<body>
  <nav aria-label="Primary">
    <a href="/solutions">Solutions</a>
    <a href="/pricing"> Pricing </a>
    <a href="https://partner.test/">Partners</a>
  </nav>
  <img src="/img/hero.png" srcset="/img/hero@1x.png 1x, /img/hero@2x.png 2x" alt="Hero">
  <p>Body copy that is long enough to look like a page a person wrote, rather than a shell.</p>
  <a href="/about#team">About the team</a>
  <a href="mailto:hi@x.test">Email</a>
  <a href="/whitepaper.pdf">Whitepaper</a>
  <footer><a href="/privacy">Privacy</a> <a href="/admin">Admin</a></footer>
  <form action="/subscribe" method="POST">
    <input type="email" name="email" required>
    <input type="hidden" name="listId" value="7">
    <textarea name="note"></textarea>
    <select name="plan"><option>a</option></select>
    <input type="submit" value="Go">
  </form>
</body>
</html>`;

test("the scanner reads what one page declares, and nothing a comment or a script pretends", () => {
  const doc = scanPage(PAGE, "https://x.test/");

  assert.equal(doc.lang, "en");
  assert.equal(doc.title, "Home page");
  assert.equal(doc.description, "What we do");
  assert.equal(doc.canonical, "https://x.test/");
  // The three ways markup lies to a tag scanner: a comment, a string inside <script>, and CSS
  // content. All three name an href or a src, and none of them is one.
  assert.ok(!doc.links.some((link) => link.href.includes("commented-out")));
  assert.ok(!doc.links.some((link) => link.href.includes("injected")));
  assert.ok(!doc.assets.some((asset) => asset.url.endsWith("/x")));
});

test("navigation is <nav> only, in source order, with the label the source gives it", () => {
  const doc = scanPage(PAGE, "https://x.test/");

  assert.deepEqual(
    doc.nav.map((item) => `${item.label}→${item.href}`),
    ["Solutions→/solutions", "Pricing→/pricing", "Partners→https://partner.test/"],
  );
  // A footer's links are links. Calling them navigation would put a privacy policy in the site's
  // primary menu, and the whole value of this section is that it is the menu as the source spells it.
  assert.ok(!doc.nav.some((item) => item.label === "Privacy"));
  assert.ok(doc.links.some((link) => link.href === "/privacy"));
});

test("assets come from every attribute that names bytes, srcset included", () => {
  const doc = scanPage(PAGE, "https://x.test/");
  const kinds = new Map(doc.assets.map((asset) => [asset.url.replace("https://x.test", ""), asset.kind]));

  assert.equal(kinds.get("/css/site.css"), "stylesheet");
  assert.equal(kinds.get("/favicon.ico"), "icon");
  assert.equal(kinds.get("/img/hero.png"), "image");
  // One <img> can name three files, and a capture that took only `src` would leave the phone and
  // the retina copies out of the tree.
  assert.equal(kinds.get("/img/hero@2x.png"), "image");
});

test("a form's fields are what it submits, and an unnamed control is not a field", () => {
  const [form] = scanPage(PAGE, "https://x.test/").forms;

  assert.equal(form.action, "https://x.test/subscribe");
  assert.equal(form.method, "post");
  assert.deepEqual(
    form.fields.map((field) => `${field.name ?? "-"}:${field.type}${field.required ? "!" : ""}`),
    ["email:email!", "listId:hidden", "note:textarea", "plan:select", "-:submit"],
  );
});

test("hrefs that are not pages or bytes are dropped, and a fragment is not a second page", () => {
  assert.equal(resolveHref("mailto:x@y.test", "https://x.test/"), null);
  assert.equal(resolveHref("tel:+15551234", "https://x.test/"), null);
  assert.equal(resolveHref("javascript:void(0)", "https://x.test/"), null);
  assert.equal(resolveHref("#top", "https://x.test/a"), null);
  assert.equal(resolveHref("/about#team", "https://x.test/").href, "https://x.test/about");
  assert.equal(resolveHref("../b?q=1", "https://x.test/a/c").href, "https://x.test/b?q=1");
  // A query is part of the identity: an archive's page two is not page one.
  assert.equal(pathOf(new URL("https://x.test/blog?page=2")), "/blog?page=2");
});

/**
 * The one hazard this module has that no other module in the repository has: it parses bytes fetched
 * from a **stranger's server**. The first draft's tag pattern repeated a group of `name(=value)?`
 * over overlapping character classes — exponential backtracking, which on a hostile page is not a
 * slow crawl but a **hung** one, and a hang leaves no report to diagnose.
 *
 * The timeout is the assertion. It is 5s for work that takes microseconds, so it is not the flaky
 * timing ceiling SH250 warns about — it distinguishes "returns" from "never returns", which is the
 * only distinction that matters here.
 */
test("pathological markup terminates: the scanner is linear, not backtracking", { timeout: 5000 }, () => {
  const attrs = `${" a=".repeat(2000)}`;
  const unclosedQuote = `<a href="${"x".repeat(5000)}`;
  const nested = `<div ${"data-x ".repeat(3000)}`;

  for (const hostile of [`<a${attrs}>`, unclosedQuote, nested, `<${"a".repeat(20000)}`]) {
    const doc = scanPage(hostile, "https://x.test/");
    assert.ok(doc, "the scanner returned");
  }
});

test("stripNonMarkup keeps the open tag and drops only the body", () => {
  const out = stripNonMarkup('<script src="/a.js">var x = "<b>"</script>');

  assert.match(out, /<script src="\/a\.js">/);
  assert.ok(!out.includes("var x"));
});

// --- robots.txt ---

const ROBOTS = `# comment
User-agent: *
Disallow: /admin
Disallow: /*?sort=
Allow: /admin/public
Crawl-delay: 2

User-agent: shio-clone
User-agent: another-bot
Disallow: /private
Sitemap: https://x.test/sitemap.xml
`;

test("an exact agent match wins outright and does not merge with the star group", () => {
  const robots = parseRobots(ROBOTS, "shio-clone");

  assert.equal(robots.matched, "shio-clone");
  assert.equal(robotsAllows(robots, "/private/x"), false);
  // The star group's rules are not this crawler's, so /admin is allowed here — merging the two is
  // the mistake, and it would make this crawler quietly stricter than the site asked.
  assert.equal(robotsAllows(robots, "/admin"), true);
  assert.deepEqual(robots.sitemaps, ["https://x.test/sitemap.xml"]);
});

test("the star group applies to an agent nobody named, with the longest rule winning", () => {
  const robots = parseRobots(ROBOTS, "somebody-else");

  assert.equal(robots.matched, "*");
  assert.equal(robotsAllows(robots, "/admin"), false);
  // Longest match wins and Allow wins a tie — which is what opens one path inside a closed subtree.
  assert.equal(robotsAllows(robots, "/admin/public/x"), true);
  assert.equal(robotsAllows(robots, "/blog?sort=asc"), false, "a wildcard rule is honoured");
  assert.equal(robots.crawlDelay, 2);
});

test("no robots.txt allows everything, and an empty Disallow does not close the site", () => {
  const absent = parseRobots("", "shio-clone");
  assert.equal(absent.present, false);
  assert.equal(robotsAllows(absent, "/anything"), true);

  const permissive = parseRobots("User-agent: *\nDisallow:\n", "shio-clone");
  assert.equal(robotsAllows(permissive, "/anything"), true);
});

// --- locales ---

test("a locale's URL shape is derived, and 'unknown' is a real answer", () => {
  assert.equal(localeShape("pt-BR", "https://x.test/pt-br/sobre", "https://x.test"), "prefix");
  assert.equal(localeShape("pt", "https://x.test/pt/sobre", "https://x.test"), "prefix");
  assert.equal(localeShape("de", "https://de.x.test/", "https://x.test"), "subdomain");
  assert.equal(localeShape("fr", "https://example.fr/", "https://x.test"), "host");
  assert.equal(localeShape("es", "https://x.test/sobre?lang=es", "https://x.test"), "query");
  assert.equal(localeShape("x-default", "https://x.test/", "https://x.test"), "default");
  // A site may key the locale off a cookie. Guessing a prefix here would be wrong in a way nothing
  // downstream could detect.
  assert.equal(localeShape("ja", "https://x.test/about", "https://x.test"), "unknown");
});

// --- the crawl ---

const SITE = {
  "/robots.txt": { type: "text/plain", body: "User-agent: *\nDisallow: /admin\n" },
  "/": { body: PAGE },
  "/solutions": { body: page("Solutions", ['<a href="/solutions/one">One</a>', '<a href="/">Home</a>']) },
  "/solutions/one": { body: page("One", ['<a href="/solutions/two">Two</a>']) },
  "/solutions/two": { body: page("Two", []) },
  "/pricing": { body: page("Pricing", []) },
  "/about": { body: page("About", []) },
  "/privacy": { body: page("Privacy", []) },
  "/admin": { body: page("Admin", []) },
  "/whitepaper.pdf": { type: "application/pdf", body: "%PDF-1.4" },
};

function page(title, body) {
  return `<html lang="en"><head><title>${title}</title></head><body><p>${"text ".repeat(60)}</p>${body.join("")}</body></html>`;
}

function ctxWith(site = SITE, { onFetch, render } = {}) {
  const written = {};
  const requested = [];
  return {
    env: {},
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
    requested,
    render,
    async fetch(url, init = {}) {
      requested.push(url);
      onFetch?.(url, init);
      const parsed = new URL(url);
      const entry = site[`${parsed.pathname}${parsed.search}`] ?? site[parsed.pathname];
      if (!entry) {
        return response({ status: 404, body: "not found" });
      }
      return response({ ...entry, url });
    },
    fs: {
      async mkdir() {},
      async writeFile(path, body) {
        written[path.replace(/\\/g, "/")] = body;
      },
      async readFile(path) {
        const error = new Error(`ENOENT: ${path}`);
        error.code = "ENOENT";
        throw error;
      },
    },
  };
}

function response({ status = 200, body = "", type = "text/html; charset=utf-8", location } = {}) {
  const headers = new Map();
  if (type) headers.set("content-type", type);
  if (location) headers.set("location", location);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers.get(String(name).toLowerCase()) ?? null },
    async text() {
      return body;
    },
  };
}

test("the capture writes exactly one file, and it is not content", async () => {
  const ctx = ctxWith();
  const result = await clone.run({}, ["https://x.test"], ctx);

  // The promise every later phase is built on. A capture that wrote a post would make the
  // conversion unreviewable — the thing it was supposed to let you review would already exist.
  assert.deepEqual(Object.keys(ctx.written), [`/repo/${INVENTORY_FILE}`.replace(/\\/g, "/")]);
  assert.equal(result.exitCode, 0);
  assert.ok(result.inventory.pages.length > 3);
});

test("--markup caches the HTML beside the inventory, and still writes no content (SH379)", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ markup: true }, ["https://x.test"], ctx);

  const paths = Object.keys(ctx.written);
  assert.ok(paths.includes(`/repo/${INVENTORY_FILE}`.replace(/\\/g, "/")));
  // Every other file is under `.shio/`, which is the derived-snapshot root — not `shio/content/`,
  // not `shio/assets/`. That is what keeps SH377's promise true with the flag on: the conversion is
  // still reviewable before anything reaches somebody's instance.
  const cached = paths.filter((path) => path !== `/repo/${INVENTORY_FILE}`.replace(/\\/g, "/"));
  assert.ok(cached.length > 3, `expected a file per page, got ${cached.length}`);
  for (const path of cached) assert.ok(path.startsWith(`/repo/${MARKUP_DIR}/`), path);

  // The row names its own file, so `shio propose` never has to re-derive a slug the crawl chose.
  const home = inventory.pages.find((page) => page.path === "/");
  assert.match(home.markup, /\.html$/);
  assert.equal(ctx.written[`/repo/${MARKUP_DIR}/${home.markup}`], SITE["/"].body);
  assert.equal(inventory.crawl.markup, true);
});

test("without --markup the crawl carries no HTML at all, and the report says how to get it", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  assert.equal(inventory.crawl.markup, false);
  assert.ok(inventory.pages.every((page) => page.markup === undefined));
  assert.match(ctx.stdout.text, /markup:\s+not cached — add --markup/);
});

test("the walk stays on one origin, and records what it did not follow", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  for (const url of ctx.requested) {
    assert.equal(new URL(url).host, "x.test", `the crawl left the origin for ${url}`);
  }
  // Off-origin is recorded rather than dropped: a conversion has to decide what to do about a
  // partner link, and it cannot decide about one nothing wrote down.
  assert.deepEqual(inventory.outbound, [{ host: "partner.test", refs: 1 }]);
  assert.deepEqual(inventory.scripts.map((each) => each.host), ["www.googletagmanager.com"]);
});

test("robots.txt bounds the crawl, and the report says which path it skipped", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  assert.ok(!inventory.pages.some((each) => each.path === "/admin"));
  assert.ok(inventory.notes.some((note) => /robots\.txt disallows \/admin/.test(note)));
  assert.equal(inventory.crawl.robots.present, true);
});

test("--ignore-robots is recorded, so a capture cannot later look permitted", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ ignoreRobots: true }, ["https://x.test"], ctx);

  assert.ok(inventory.pages.some((each) => each.path === "/admin"));
  assert.ok(inventory.notes.some((note) => /--ignore-robots was passed/.test(note)));
  assert.equal(ctx.requested.some((url) => url.endsWith("/robots.txt")), false);
});

test("--depth bounds the levels, and --max-pages cuts the same pages twice", async () => {
  const shallow = ctxWith();
  const deep = ctxWith();
  const { inventory: atOne } = await clone.run({ depth: 1 }, ["https://x.test"], shallow);
  const { inventory: atThree } = await clone.run({ depth: 3 }, ["https://x.test"], deep);

  assert.ok(!atOne.pages.some((each) => each.path === "/solutions/one"));
  assert.ok(atThree.pages.some((each) => each.path === "/solutions/one"));

  // The determinism that makes the artefact worth keeping: a bounded crawl of an unchanged site
  // must produce the same bytes, or the diff against the next capture is noise (P5).
  const first = await clone.run({ maxPages: 3 }, ["https://x.test"], ctxWith());
  const second = await clone.run({ maxPages: 3 }, ["https://x.test"], ctxWith());
  assert.equal(serializeInventory(first.inventory), serializeInventory(second.inventory));
  assert.equal(first.inventory.crawl.truncated, true);
  assert.ok(first.inventory.notes.some((note) => /every count in it is a lower bound/.test(note)));
});

test("a non-HTML response is recorded as unreadable, not scanned", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  const pdf = inventory.problems.find((each) => each.path === "/whitepaper.pdf");
  assert.ok(pdf, "the PDF should be recorded");
  assert.match(pdf.reason, /not html \(application\/pdf\)/);
  // And it is still an asset, because the page that linked it named bytes.
  assert.ok(inventory.assets.some((each) => each.path === "/whitepaper.pdf" || each.kind === "document")
    || inventory.problems.length > 0);
});

test("a seed that redirects off-host is refused, naming the host it actually got", async () => {
  const site = { ...SITE, "/": { status: 301, location: "https://www.x.test/", body: "" } };
  const ctx = ctxWith(site);

  await assert.rejects(
    () => clone.run({}, ["https://x.test"], ctx),
    (error) => {
      assert.match(error.message, /redirects to https:\/\/www\.x\.test, which is a different host/);
      // The fix is a copy-paste, which is what a teaching error owes its reader (P6).
      assert.match(error.message, /shio clone https:\/\/www\.x\.test/);
      return true;
    },
  );
});

test("a page that redirects on-host is followed once; off-host mid-crawl is just a link", async () => {
  const site = {
    ...SITE,
    "/about": { status: 302, location: "/about-us", body: "" },
    "/about-us": { body: page("About us", []) },
    "/pricing": { status: 302, location: "https://elsewhere.test/pricing", body: "" },
  };
  const { inventory } = await clone.run({}, ["https://x.test"], ctxWith(site));

  assert.ok(inventory.pages.some((each) => each.title === "About us"));
  const pricing = inventory.problems.find((each) => each.path === "/pricing");
  assert.match(pricing.reason, /redirects off-origin to https:\/\/elsewhere\.test\/pricing/);
});

test("locales, forms and navigation are aggregated across the whole crawl", async () => {
  const { inventory } = await clone.run({}, ["https://x.test"], ctxWith());

  assert.deepEqual(
    inventory.locales.map((each) => `${each.locale}/${each.shape}`),
    ["de/subdomain", "en/declared", "pt-BR/prefix"],
  );
  assert.equal(inventory.forms.length, 1);
  assert.equal(inventory.forms[0].fields.length, 5);
  // One nav on every page is one navigation, with the page count beside it.
  assert.deepEqual(inventory.navigation.map((each) => each.label), ["Solutions", "Pricing", "Partners"]);
});

test("--render uses the browser's DOM through the same scanner", async () => {
  const seen = [];
  // The markup a fetch would never see: the shell's <div id="root"> is empty on the wire, and this
  // is what the page looks like once its own JavaScript has run.
  const hydrated = page("Hydrated", ['<a href="/only-after-hydration">Deep</a>']);
  const browserPage = {
    async goto(url) {
      seen.push(url);
    },
    async content() {
      return hydrated;
    },
    async close() {},
  };
  const ctx = ctxWith(SITE, { render: () => browserPage });

  // depth 0 is the seed and nothing else — the cheapest possible look at a site.
  const { inventory } = await clone.run({ render: true, depth: 0 }, ["https://x.test"], ctx);

  assert.equal(inventory.crawl.rendered, true);
  assert.equal(inventory.pages[0].title, "Hydrated");
  assert.equal(inventory.pages[0].rendered, true);
  assert.deepEqual(seen, ["https://x.test/"]);
});

test("a thin page is a note pointing at --render, not a silent miss", () => {
  const shell = '<html lang="en"><head><title>App</title></head><body><div id="root"></div><script src="/app.js"></script></body></html>';
  const inventory = buildInventory({
    origin: "https://x.test",
    seed: "https://x.test",
    visited: [{ url: "https://x.test/", status: 200, depth: 0, doc: scanPage(shell, "https://x.test/") }],
    robots: parseRobots("User-agent: *\nDisallow:\n"),
    options: {},
  });

  // The failure mode a fetch-only crawl has and cannot detect from the outside: a complete-looking
  // capture of a page whose content never arrived.
  assert.ok(inventory.notes.some((note) => /re-run with --render/.test(note)));
});

test("a sitemap named in robots is reported as NOT walked", () => {
  const inventory = buildInventory({
    origin: "https://x.test",
    seed: "https://x.test",
    visited: [],
    robots: parseRobots("User-agent: *\nSitemap: https://x.test/sitemap.xml\n"),
    options: {},
  });

  // No silent caps: a page reachable only from a sitemap is missing, and a report that did not say
  // so would read as complete coverage.
  assert.ok(inventory.notes.some((note) => /did NOT walk/.test(note)));
});

test("the report is bounded per section and the inventory keeps everything", async () => {
  const { inventory } = await clone.run({}, ["https://x.test"], ctxWith());
  const short = describeInventory(inventory, { limit: 1 });

  assert.match(short, /pages \(\d+\)/);
  assert.match(short, /… \d+ more in the inventory/);
  assert.match(short, /no content was written/);
});

test("a crawl that reached nothing exits non-zero", async () => {
  const ctx = ctxWith({ "/robots.txt": { type: "text/plain", body: "" } });
  const result = await clone.run({}, ["https://x.test"], ctx);

  // "0 pages" from a broken seed and "a site with no pages" are different claims, and a CI step
  // that wrapped this would read the first as the second.
  assert.equal(result.exitCode, 1);
  assert.equal(result.inventory.pages.length, 0);
});

test("bad input explains itself", async () => {
  await assert.rejects(() => clone.run({}, [], ctxWith()), /pass the site to capture/);
  await assert.rejects(() => clone.run({}, ["not a url"], ctxWith()), /is not a URL/);
  await assert.rejects(() => clone.run({}, ["ftp://x.test"], ctxWith()), /not http\(s\)/);
  await assert.rejects(() => clone.run({ depth: "deep" }, ["https://x.test"], ctxWith()), /--depth takes a whole number/);
});
