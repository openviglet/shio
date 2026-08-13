/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH448 — scenario two: **a site an agent built is visible**.
 *
 * ## What this asserts that nothing else does
 *
 * `ShPageRenderTest` proves the renderer composes, and it does so by persisting entities through
 * an `EntityManager` inside a transaction — so it is evidence about `ShPageRenderService` and
 * about nothing between an agent and it. Every step below goes over HTTP with the credential the
 * CLI carries, in the order an agent that has just read the manifest would have to discover: the
 * render post-types do not exist on a bare instance, `post.upsert` cannot create a system type,
 * and a layout nobody bound renders nothing. Three preconditions, each of which is a whole session
 * lost if the surface does not carry it — which is what P1 and P2 are about, and what a suite that
 * builds its fixture with an `EntityManager` cannot ever notice.
 *
 * ## Every assertion names the task it would have caught
 *
 * That is the record this file exists to be. `SH327` is the one to read first: `serverRender: true`
 * was in the manifest while the one call that makes a site visible needed a session cookie, and no
 * test failed, because no test was a stateless client.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import { ShioAdminClient } from "../src/admin-client.mjs";
import { ShioAgentClient } from "../src/agent-client.mjs";
import { ConsoleSession, basic, batch, errorsIn, keepsFixtures, requireInstance, scratchSite } from "./support/instance.mjs";

const LAYOUT = "SH448_LAYOUT";
const NAV_REGION = "SH448_NAV";
const THEME = "SH448_THEME";

/**
 * The 2018 shape, on purpose (`docs/agents/renderer.md`): a field is named at the model root
 * (`{{TEXT}}`, not `{{post.TEXT}}`), a theme is **inlined** by the layout through `{{theme.css}}`
 * rather than linked, and a slot is `sh-region="NAME"`. A fixture written in this repo's own idiom
 * instead would assert that the renderer reads what this file happens to write.
 */
const LAYOUT_HTML = `<html>
<head><title>{{post.title}}</title><style>{{theme.css}}</style></head>
<body>
  <div sh-region="${NAV_REGION}"></div>
  <main sh-field="TEXT"><h1>{{post.title}}</h1>{{{TEXT}}}</main>
  <div sh-region="SH448_ABSENT"></div>
</body>
</html>`;

let conn;
let site;
let postType;
let agent;
let admin;
let console_;

describe("SH448 conformance: the native render path, end to end over HTTP", () => {
  before(async () => {
    conn = await requireInstance();
    site = scratchSite("Render");
    postType = `${site}Article`;
    agent = new ShioAgentClient(conn);
    admin = new ShioAdminClient(conn);
    console_ = new ConsoleSession(conn);
  });

  after(async () => {
    if (keepsFixtures()) return;
    // Both directions, and neither may throw: a teardown that fails after a failed assertion
    // replaces the diagnosis with its own noise.
    await console_?.deleteSite(site).catch(() => {});
    await admin?.deletePostTypeDef(postType).catch(() => {});
  });

  test("provision → model → post → /preview answers 200 with the editor's markers", async () => {
    // ── the manifest says it can, before anything relies on it ────────────────────────────
    // P2: one call tells an agent what this instance does. If `serverRender` is not advertised
    // there is no discoverable path to a visible site, whatever the code can do.
    const features = await agent.getManifest(["features"]);
    assert.equal(
      features?.features?.serverRender,
      true,
      "SH327: the manifest advertises server-side rendering. A capability an agent has to guess "
        + "is not a capability — and this flag was true while the endpoint below needed a cookie",
    );

    // ── precondition one: the render post-types exist ─────────────────────────────────────
    // Addressless, and it has to be: it acts on the instance's model, not on content (SH186).
    const provisioned = await batch(conn, [{ op: "render.provision", data: { reconcile: true } }]);
    assert.ok(
      !errorsIn(provisioned),
      "SH325: render.provision succeeds on a live instance. It answered 500 (\"Widget '…' is not "
        + "seeded\") on any instance older than SH252, because the widget catalogue was seeded per "
        + "catalogue and inside a first-run gate — two guards where the task named one, and no unit "
        + "test could see it because none of them had an instance with history. Got: "
        + JSON.stringify(provisioned).slice(0, 400),
    );

    // ── precondition two: a post-type of our own, over the one name-keyed surface ─────────
    await admin.putPostTypeDef(postType, {
      name: postType,
      namePlural: `${postType}s`,
      title: "SH448 Article",
      description: "Seeded by the conformance suite; deleted when it ends.",
      fields: [
        { name: "TITLE", label: "Title", description: "Title", ordinal: 1,
          widget: "Text", isTitle: true, required: true },
        { name: "TEXT", label: "Text", description: "Text", ordinal: 2,
          widget: "Text Area", required: false },
      ],
    });
    const readBack = await admin.getPostTypeDef(postType);
    assert.equal(
      readBack?.name,
      postType,
      "SH117: one name-keyed post-type surface, reachable by a stateless caller. The CLI writes "
        + "here and so does the console; the CSRF exemption is narrowed to callers with no session "
        + "cookie, so this call proves the narrow half rather than the blanket one",
    );

    // ── the site, and the binding that is the difference between a model and a page ───────
    // **One** `site.upsert` op, which is the assertion. This was two — the first thing this suite
    // found: the create branch read `description`/`url`/`furl` and **dropped `data.postTypeLayout`
    // silently**, so a caller that created and bound in one op got `CREATE — a blank site`, no
    // binding, and a 400 from `/preview` several steps later, because the binding was written by
    // the *fill* branch and only a site that already exists reaches it. SH453 made both branches
    // read it, so the workaround becomes the guard: one op, with the read below as the proof.
    const built = await batch(conn, [
      { op: "site.upsert", address: `site:${site}`, data: { postTypeLayout: { [postType]: LAYOUT } } },
      { op: "post.upsert", address: `post:${site}/theme`, type: "Theme", folder: "/",
        data: { TITLE: THEME, CSS: "body{font-family:system-ui}" } },
      { op: "post.upsert", address: `post:${site}/layout`, type: "PageLayout", folder: "/",
        data: { TITLE: LAYOUT, HTML: LAYOUT_HTML, THEME } },
      { op: "post.upsert", address: `post:${site}/region-nav`, type: "Region", folder: "/",
        data: { TITLE: NAV_REGION, HTML: "<nav data-nav>site nav</nav>" } },
      // The home page. `post:<site>/` is the one address form the SH79 grammar does not spell,
      // and it is what `/preview/<site>/` resolves to (SH433).
      { op: "post.upsert", address: `post:${site}/`, type: postType, folder: "/",
        data: { TITLE: "Conformance home", TEXT: "<p>Rendered by the instance.</p>" } },
    ]);
    assert.ok(
      !errorsIn(built),
      `SH328: site.upsert writes postTypeLayout — the one edge that makes a site render, and the `
        + `only op that can write it. Got: ${JSON.stringify(built).slice(0, 600)}`,
    );
    const bound = await console_.site(site);
    assert.match(
      bound?.postTypeLayout ?? "",
      new RegExp(`"${postType}"\\s*:\\s*"${LAYOUT}"`),
      "SH453: and the binding is on the site afterwards, from the op that created it. Asserted as "
        + "a read rather than inferred from the op's own report, because the version that dropped "
        + "the binding still answered CREATE — which is exactly why nothing caught it",
    );

    // ── the assertion the whole scenario is for ──────────────────────────────────────────
    const res = await fetch(`${conn.baseUrl}/preview/${encodeURIComponent(site)}/`, {
      headers: { Authorization: basic(conn.user, conn.password) },
    });
    const html = await res.text();
    assert.equal(
      res.status,
      200,
      `SH183/SH433: /preview/<site>/ renders the site's home page. Everything above went through `
        + `the agent surface, so a 404 here means the model an agent can build and the model the `
        + `renderer reads are two different things. Body: ${html.slice(0, 500)}`,
    );

    // ── and it is a *preview*, which is a guard, not a detail ─────────────────────────────
    assert.match(
      res.headers.get("cache-control") ?? "",
      /no-store/,
      "SH183: a freshness lifetime is how a preview quietly becomes infrastructure",
    );
    assert.match(
      res.headers.get("x-robots-tag") ?? "",
      /noindex/,
      "SH183: somebody else's draft must not end up in an index",
    );

    // ── the rendered DOM is a contract (SH300) ────────────────────────────────────────────
    assert.match(html, /<h1>Conformance home<\/h1>/, "the page's own field, at the model root");
    assert.match(html, /Rendered by the instance\./, "and its markup, unescaped by {{{TEXT}}}");
    assert.match(html, /font-family:system-ui/, "SH183: {{theme.css}} inlines the Theme post's CSS");
    assert.match(html, /<nav data-nav>/, "the Region post filled the slot that named it");

    assert.match(
      html,
      new RegExp(`sh-region="${NAV_REGION}"`),
      "SH185: sh-region survives the render. It is a contract the content declares and has been "
        + "in it since 2018; stripping it is how the Universal Editor loses the node→post map",
    );
    assert.match(
      html,
      new RegExp(`data-shio-region="${NAV_REGION}"`),
      "SH183: and the data-* twin the browser consumes joins it",
    );
    assert.match(html, /data-shio-region-post="/, "SH183: naming which post filled the slot");
    assert.match(
      html,
      /data-shio-region-missing/,
      "SH169: the empty slot is *marked*. A missing region and an empty one are different "
        + "mistakes, and a renderer that silently drops both reports neither",
    );
    assert.match(
      html,
      /data-shio-prop="TEXT"/,
      "SH185/SH303: sh-field=\"TEXT\" becomes data-shio-prop. The console resolves the label from "
        + "the post-type, so the attribute carries the field name and nothing else",
    );
    assert.match(
      html,
      /data-shio-post="/,
      "SH185: the page's own post is stamped on the document, so the common case needs no sh-post",
    );

    // ── and the perception loop closes without a browser (P9) ─────────────────────────────
    const digest = await agent.digest({ address: `post:${site}/` });
    assert.ok(
      digest?.digest || digest?.structure || digest?.hash,
      `SH101: /agent/render answers a structural digest for the page just built — the cheap `
        + `textual proof that replaces a human round trip. Got: ${JSON.stringify(digest).slice(0, 300)}`,
    );

    // ── SH450: the preview's 401 is answerable by a browser ───────────────────────────────
    // No browser here — the suite deliberately starts none (IX.23) — but the header is the whole
    // mechanism, and it is a fact about a live response that no unit test observed for two tasks.
    // Playwright's `httpCredentials` answers a *challenge*; with none sent, `--against` photographed
    // the 401 and scored it 100% against the real site.
    const unauthenticated = await fetch(`${conn.baseUrl}/preview/${encodeURIComponent(site)}/`);
    assert.equal(unauthenticated.status, 401, "SH359: the preview is closed, which is the premise");
    assert.match(
      unauthenticated.headers.get("www-authenticate") ?? "",
      /^Basic realm=/,
      "SH450: and it says so in a way a browser can act on. Absent, a headless browser handed "
        + "credentials never sends them and the visual gate compares a login error to a website",
    );

    // ── SH451: and a visitor with no credential can read it, once it is published ─────────
    // The claim nothing else in this repository can make. Every guard below is asserted from a
    // request that carries no Authorization header at all, because "anonymous" is not a property
    // of the code — it is a property of what a stranger's HTTP client gets back.
    const anonymousBefore = await fetch(`${conn.baseUrl}/sites/${site}/default/en-us/`);
    assert.equal(
      anonymousBefore.status,
      404,
      "SH451: PUBLISHED and never DRAFT. The page exists as a draft — the whole model above was "
        + "written by post.upsert, which drafts (P7) — and a delivery route that served it would "
        + "publish somebody's unfinished edit by rendering it",
    );

    // Every level, not just the page. SH278: a PUBLISHED render reads only published rows at every
    // level, so an unpublished layout cannot be used — what a visitor gets must not depend on an
    // edit nobody has published. The 409 that says so is one of the better errors in the product,
    // and it is the reason this list is four ops rather than one.
    const published = await batch(conn, [
      { op: "post.publish", address: `post:${site}/theme` },
      { op: "post.publish", address: `post:${site}/layout` },
      { op: "post.publish", address: `post:${site}/region-nav` },
      { op: "post.publish", address: `post:${site}/` },
    ]);
    assert.ok(!errorsIn(published), `publishing the model: ${JSON.stringify(published).slice(0, 400)}`);

    const anonymous = await fetch(`${conn.baseUrl}/sites/${site}/default/en-us/`);
    const publicHtml = await anonymous.text();
    assert.equal(
      anonymous.status,
      200,
      `SH451: a site built in Shio has a public URL. /preview/** answers 401 anonymously by design `
        + `and the Next.js path cannot install (SH446), so until this route existed the renderer `
        + `composed a correct page nobody outside the console could read. Body: ${publicHtml.slice(0, 300)}`,
    );
    assert.match(publicHtml, /<h1>Conformance home<\/h1>/, "the same render, the same content");
    assert.ok(
      !/data-shio-preview|Preview/i.test(publicHtml),
      "SH451: no banner. A preview says what it is because nobody should have to work out whether "
        + "they are looking at the live site; a delivery route saying so would be a lie",
    );
    assert.match(
      anonymous.headers.get("cache-control") ?? "",
      /public/,
      "SH451: cacheable, which is the guard inverted rather than dropped — `no-store` on a preview "
        + "is what stops it quietly becoming infrastructure, and this route *is* the infrastructure",
    );
    assert.equal(
      anonymous.headers.get("x-robots-tag"),
      null,
      "SH451: and indexable. A published page a crawler is told to ignore is not published",
    );

    // ── SH458: and the same route's negative cases, which nothing asked about ─────────────
    //
    // The second missing assertion. Everything above is the happy path, so the route that lies is
    // the one no test ever addresses: `/sites/<site>/<a>/<b>/` accepts **any** `<a>` — the format
    // segment is vestigial and was implemented as accepts-anything — and with nothing after `<b>`
    // the content path is empty, which resolves to the home page. Measured on the running instance:
    // `/sites/publico/img/missing.png` answers **200 text/html**, the site's home page, for a
    // request a browser made for an image. That is the shape of every root-relative asset a
    // captured page names, so a broken image and a missing webfont both arrive as a rendered page
    // with a 200 — the status a caller checks first says the opposite of what happened (SH457).
    const wrongFormat = await fetch(`${conn.baseUrl}/sites/${site}/bogus/en-us/`);
    assert.equal(
      wrongFormat.status,
      404,
      "SH457: the format segment accepts `default` and nothing else. Vestigial is a reason to fix "
        + "its value, not a reason to accept every value — and accepting every value is what makes "
        + "the two segments after the site name unusable for telling a real request from a typo",
    );

    const missingAsset = await fetch(`${conn.baseUrl}/sites/${site}/img/missing.png`);
    const missingBody = await missingAsset.text();
    assert.equal(
      missingAsset.status,
      404,
      `SH457: a path that names no content is a 404, not the home page. This is the one that costs `
        + `— an <img src="/img/missing.png"> on a replica gets 200 and a full HTML document, so `
        + `every check that reads a status sees a working asset and the page still has a broken `
        + `image on it. Got ${missingAsset.status} ${missingAsset.headers.get("content-type")}: `
        + `${missingBody.slice(0, 200)}`,
    );

    // The header variant, which is how one host serves one site: no site in the URL at all.
    const headed = await fetch(`${conn.baseUrl}/sites/`, { headers: { "x-sh-site": site } });
    assert.equal(
      headed.status,
      200,
      "SH451: `x-sh-site` names the site so the URL carries only the content path — 0.3.8's own "
        + "variant, recovered with the route rather than invented beside it",
    );

    // ── the content lint agrees, on the instance, about the site we just made ─────────────
    const report = await agent.verify({ site });
    const missingTheme = (report?.findings ?? []).filter((row) => row.rule === "missing-theme");
    assert.deepEqual(
      missingTheme,
      [],
      "SH168: a layout naming a Theme that does not exist is an error, and this one names a Theme "
        + "that does — asserted here because the lint and the renderer resolve a theme by title "
        + "in two places, which is the shape that agrees in the suite and disagrees on an instance",
    );
  });
});

