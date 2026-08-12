/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH588 — the measurement that kept being rewritten.
 *
 * <p>Four rounds hand-wrote *count, do not eyeball*, slightly differently each time, and once it was
 * wrong: a complete page read as **9 of 10** because only `data-shio-prop` was counted, and a
 * regression was nearly filed against SH503 on the strength of it. The two cases that make it hard
 * are both here as fixtures — the attribute form, and the collection row — because they are what a
 * reader gets wrong and therefore what an assertion is worth having for.</p>
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  describeAnnotations,
  joinDeclared,
  scanAnnotations,
} from "../src/annotations.mjs";
import * as annotations from "../src/commands/annotations.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** A page whose grid has two rows, each its own post, the way a real conversion emits one. */
const GRID = `<html><body data-shio-post="page-1" data-shio-post-type="Page">
  <h1 data-shio-prop="title">Acme</h1>
  <div class="grid">
    <article data-shio-post="row-1">
      <h2 data-shio-prop="heading">One</h2>
      <img src="/a.png" data-shio-prop-attr="icon:src,alt:alt">
    </article>
    <article data-shio-post="row-2">
      <h2 data-shio-prop="heading">Two</h2>
    </article>
  </div>
  </body></html>`;

const MODEL = {
  fields: new Map([
    ["Page", ["title", "summary"]],
    ["Feature", ["heading", "icon", "alt"]],
  ]),
  types: new Map([["row-1", "Feature"], ["row-2", "Feature"]]),
};

test("SH588: a field drawn into an attribute counts — this is the 9-of-10 case", () => {
  // `icon` and `alt` are declared by `data-shio-prop-attr` beside the element's own `data-shio-prop`
  // and land on no text node. Counting only the first form is what read a complete page as short.
  const scan = scanAnnotations(GRID);
  const row = scan.posts.find((entry) => entry.post === "row-1");

  assert.deepEqual(row.fields, [
    { field: "heading", form: "text", attribute: null },
    { field: "icon", form: "attr", attribute: "src" },
    { field: "alt", form: "attr", attribute: "alt" },
  ]);

  const report = joinDeclared(scan, MODEL);
  const joined = report.posts.find((entry) => entry.post === "row-1");
  assert.deepEqual(joined.annotated, ["alt", "heading", "icon"]);
  assert.deepEqual(joined.missing, []);
});

test("SH588: a collection row is its own post, and its fields do not fall to the page", () => {
  // The failure this exists to catch: five props over a grid whose cards carry none reads as an
  // annotated page unless ownership is resolved by containment, which is the renderer's own rule.
  const { posts } = scanAnnotations(GRID);

  assert.deepEqual(posts.map((row) => row.post), ["page-1", "row-1", "row-2"]);
  assert.deepEqual(posts[0].fields.map((entry) => entry.field), ["title"]);
  assert.equal(posts[0].page, true);
  assert.equal(posts[0].type, "Page");
  assert.deepEqual(posts[2].fields.map((entry) => entry.field), ["heading"]);
});

test("SH588: a declared field that renders without an annotation is the finding", () => {
  const report = joinDeclared(scanAnnotations(GRID), MODEL);

  // `row-2` draws its heading and never annotates `icon` or `alt` — SH465/SH475's shape exactly:
  // a row that renders and cannot be curated.
  const row = report.posts.find((entry) => entry.post === "row-2");
  assert.deepEqual(row.missing, ["alt", "icon"]);
  // And the page's own `summary` is declared and undrawn.
  assert.deepEqual(report.posts[0].missing, ["summary"]);
  assert.equal(report.missing, 3);

  const text = describeAnnotations(report, { target: "post:Acme/" });
  assert.match(text, /row-2\s+\(Feature\)\s+1\/3\s+missing: alt, icon/);
  assert.match(text, /3 declared field\(s\) are RENDERED and not editable/);
  // Both forms per field, because "which of these is an attribute" is what the hand count missed.
  assert.match(text, /icon\s+@src/);
});

test("SH588: more annotations than declared fields is expected, not a finding", () => {
  // An empty slot is annotated so a curator can fill it, and one field is drawn twice — its text
  // and its href. A count that called either of those damage would fire on every good page.
  const html = `<html><body data-shio-post="p" data-shio-post-type="Link">
    <a data-shio-prop="label" data-shio-prop-attr="target:href">Go</a>
    <span data-shio-prop="note"></span></body></html>`;
  const report = joinDeclared(scanAnnotations(html), {
    fields: new Map([["Link", ["label", "target"]]]),
  });

  assert.equal(report.missing, 0);
  assert.equal(report.posts[0].forms.length, 3, "three declarations over two declared fields");
  assert.match(describeAnnotations(report), /more annotations than declared fields is expected/);
});

test("SH588: a type this cannot resolve is NOT checked, and says so", () => {
  // "We could not check this" and "this is fine" are different answers, and collapsing them is how
  // a clean report gets read off a page nobody measured.
  const html = `<html><body data-shio-post="p"><h1 data-shio-prop="title">x</h1></body></html>`;
  const report = joinDeclared(scanAnnotations(html), { fields: new Map() });

  assert.equal(report.unresolved, 1);
  assert.equal(report.missing, 0);
  assert.match(describeAnnotations(report), /type not resolved, NOT checked/);
});

test("SH588: a prop with no enclosing post is kept — the renderer annotates it on purpose", () => {
  // SH169: a `sh-field` that resolves to no post is annotated and left unowned, so the editor can
  // render it read-only and say why. Dropping it here would hide the case the renderer surfaced.
  const { orphans } = scanAnnotations(`<div data-shio-prop="stray">x</div>`);

  assert.deepEqual(orphans, [{ field: "stray", form: "text", attribute: null }]);
});

test("SH588: a void element does not swallow the elements after it", () => {
  // `<img>` has no close tag. Pushing it onto the stack would make every later field a child of it
  // and attribute them to whatever post it sat under — a scanner bug that reads as a content bug.
  const html = `<body data-shio-post="page">
    <img data-shio-prop-attr="hero:src">
    <div data-shio-post="row"><p data-shio-prop="body">x</p></div>
    <p data-shio-prop="footer">y</p></body>`;
  const { posts } = scanAnnotations(html);

  assert.deepEqual(posts.map((row) => [row.post, row.fields.map((f) => f.field)]), [
    ["page", ["hero", "footer"]],
    ["row", ["body"]],
  ]);
});

test("SH588: the command exits 1 on a field that renders and is not editable", async () => {
  const stdout = sink();
  const calls = [];
  const ctx = {
    stdout,
    env: {},
    fetch: async (url) => {
      calls.push(String(url));
      const at = String(url);
      if (at.includes("/agent/context")) {
        return json({ sites: [{ name: "Acme", url: "" }] });
      }
      if (at.includes("/api/v2/post-type")) {
        return json([{ name: "Page", fields: [{ name: "title" }, { name: "summary" }] }]);
      }
      if (at.includes("/agent/read")) {
        return json([]);
      }
      return {
        ok: true,
        status: 200,
        async text() {
          return `<body data-shio-post="p" data-shio-post-type="Page">`
            + `<h1 data-shio-prop="title">x</h1></body>`;
        },
      };
    },
  };

  const result = await annotations.run({ url: "http://shio.test" }, ["post:Acme/about"], ctx);

  assert.equal(result.exitCode, 1);
  assert.equal(result.missing, 1);
  // The preview, with the editor on — the annotations are a property of this instance's render and
  // a public front end has no reason to carry them.
  assert.ok(calls.some((at) => at.includes("editor=true")), `no editor=true in ${calls.join(" ")}`);
  assert.match(stdout.text, /1 NOT editable/);
});

/**
 * SH685 — the instrument could not read the type it had just drawn.
 *
 * <p>Measured on the home page of the `www.viglet.org` replica: `9 post(s) — 67 of 4 declared
 * field(s) annotated, 4 NOT editable, 8 whose type this could not resolve`. The eight are the
 * sections, and `GET /agent/read?address=id:<uuid>` answers `SectionFaq` / `SectionFeatureGrid` for
 * those very ids one call away. So the comparison this command exists to make was made for one post
 * of nine, and `67 of 4` counted annotations against the declared fields of the only type it had.</p>
 *
 * <p>The read never failed. `/agent/read` answers with the <b>canonical</b> address whatever address
 * was asked for, and carries no `id` unless `ids=true`, so the map was keyed by
 * `post:VigletOrg/sections/…` and looked up by a raw uuid off the markup — every entry written, none
 * ever found. This fixture is that response shape exactly, which is the part a unit test can hold:
 * an `address` that is not the one asked for.</p>
 */
test("SH685: a type resolves even though the read answers with the canonical address", async () => {
  const stdout = sink();
  const asked = [];
  const ctx = {
    stdout,
    env: {},
    fetch: async (url) => {
      const at = String(url);
      if (at.includes("/agent/context")) return json({ sites: [{ name: "Acme", url: "" }] });
      if (at.includes("/api/v2/post-type")) {
        return json([{ name: "SectionCta", fields: [{ name: "heading" }, { name: "ctaHref" }] }]);
      }
      if (at.includes("/agent/read")) {
        asked.push(at);
        // The real shape: asked by `id:`, answered by `post:` — and `id` present only because
        // `ids=true` was passed. A fixture echoing the requested address would pass with the defect.
        return json([{ address: "post:Acme/sections/b1a2", type: "SectionCta", id: "sec-1" }]);
      }
      return {
        ok: true,
        status: 200,
        async text() {
          return `<body data-shio-post="sec-1"><h1 data-shio-prop="heading">x</h1>`
            + `<a data-shio-prop-attr="ctaHref:href" href="/x">go</a></body>`;
        },
      };
    },
  };

  const result = await annotations.run({ url: "http://shio.test" }, ["post:Acme/about"], ctx);

  assert.ok(
    asked.some((at) => at.includes("ids=true")),
    `the read has to ask for the id, or the answer cannot be joined to the question: ${asked.join(" ")}`,
  );
  assert.equal(result.unresolved, 0, "the type resolves — it always did, on the wire");
  assert.equal(result.declared, 2, "and the declared count is the type's, not the empty set's");
  assert.equal(result.exitCode, 0, "nothing is missing, so nothing gates");
  assert.match(stdout.text, /\(SectionCta\)/);
});

test("SH685: a post whose type genuinely does not resolve gates, instead of being narrated", async () => {
  // The reversal. "We could not check this" is not the same claim as "this is wrong" — true, and it
  // is not the same claim as "this is right" either, and only one of those two was being acted on.
  // The run exited 1 on a Page's 0/4 while eight of nine posts went unchecked, so the red read as a
  // finding about the page and hid the blind spot: worse than either answer on its own.
  const stdout = sink();
  const ctx = {
    stdout,
    env: {},
    fetch: async (url) => {
      const at = String(url);
      if (at.includes("/agent/context")) return json({ sites: [{ name: "Acme", url: "" }] });
      if (at.includes("/api/v2/post-type")) return json([{ name: "Page", fields: [{ name: "title" }] }]);
      // The address resolves to nothing — which `/agent/read` answers in place, echoing the request
      // with an `error` rather than omitting it.
      if (at.includes("/agent/read")) {
        return json([{ address: "id:ghost", error: "no post at this address" }]);
      }
      return {
        ok: true,
        status: 200,
        async text() {
          return `<body data-shio-post="ghost"><h1 data-shio-prop="title">x</h1></body>`;
        },
      };
    },
  };

  const result = await annotations.run({ url: "http://shio.test" }, ["post:Acme/about"], ctx);

  assert.equal(result.unresolved, 1);
  assert.equal(result.missing, 0, "nothing is known to be missing — that is the whole point");
  assert.equal(
    result.exitCode,
    1,
    "and it still gates: the page renders sh-post for a post this instance cannot read, which is a "
      + "dangling annotation and a finding in its own right (SH685)",
  );
  assert.match(stdout.text, /NOT checked: the page renders sh-post for a post this instance cannot read/);
});

function json(body) {
  return {
    ok: true,
    status: 200,
    async json() {
      return body;
    },
    async text() {
      return JSON.stringify(body);
    },
  };
}
