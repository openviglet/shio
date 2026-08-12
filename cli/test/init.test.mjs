/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH119 — `shio init --blueprint`. Network-free: a routed fake fetch answers the
 * blueprint detail and the apply.
 *
 * What is worth asserting is the difference between the two sources of the same starter.
 * Files come from the package rather than the bundled template; `{{projectName}}` is
 * substituted on the way to disk, because a package's carried source is the one thing the
 * server does not substitute; an existing file is never overwritten without `--force`; and
 * the content half is applied only when a site is named — with the output *saying* it was
 * not, since a silent front-end-only scaffold is discovered later as an empty site.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

import * as init from "../src/commands/init.mjs";
import { parseArgs } from "../src/args.mjs";
import { endsWithPath, norm } from "./support/paths.mjs";

const DETAIL = {
  manifest: {
    formatVersion: 1,
    name: "nextjs-starter",
    title: "Next.js starter",
    version: "1.0.0",
    params: { type: "object", properties: { projectName: { type: "string" } } },
  },
  postTypes: ["Page"],
  folders: 1,
  posts: 2,
  files: [
    { path: "package.json", text: '{\n  "name": "{{projectName}}"\n}\n' },
    { path: "app/[...slug]/page.tsx", text: "export default function Page() {}\n" },
    { path: ".gitignore", text: "node_modules\n" },
  ],
};

const APPLIED = {
  dryRun: false,
  blueprint: "nextjs-starter",
  version: "1.0.0",
  site: "Acme",
  params: { site: "Acme", projectName: "cool-site" },
  postTypes: [{ name: "Page", action: "create" }],
  content: { dryRun: false, ops: 3, changed: 3, results: [] },
  files: ["package.json"],
  verify: { ok: true, missing: [], errors: 0, warnings: 0, checks: ["content"] },
};

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

function routedFetch(detail = DETAIL) {
  const calls = [];
  const fetchImpl = async (url, requestInit) => {
    const href = String(url);
    calls.push({ url: href, method: requestInit?.method ?? "GET" });
    const body = /\/apply$/.test(href) ? APPLIED : detail;
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(body);
      },
      async json() {
        return body;
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function ctxWith(fetchImpl, { existing = [] } = {}) {
  const written = [];
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    stdin: { isTTY: false },
    fetch: fetchImpl,
    written,
    fs: {
      async readFile(path) {
        if (existing.some((name) => endsWithPath(path, name))) return "old contents";
        const error = new Error("not found");
        error.code = "ENOENT";
        throw error;
      },
      async mkdir() {},
      async writeFile(path, text) {
        written.push({ path: norm(path), text });
      },
    },
  };
}

function flagsFor(argv) {
  return parseArgs(argv);
}

test("scaffolds from a blueprint, substituting the project name into carried source", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "nextjs-starter", "--yes"]);

  const result = await init.run(flags, positionals, ctx);

  // `resolve`, not a literal: the command resolves against cwd, so on Windows this is
  // a real absolute path with the drive letter, which is what it should be (SH198).
  assert.equal(result.targetDir, resolve("/repo", "cool-site"));
  const pkg = ctx.written.find((f) => f.path.endsWith("package.json"));
  assert.ok(pkg, "package.json should be written");
  assert.match(pkg.text, /"name": "cool-site"/);
  assert.doesNotMatch(pkg.text, /\{\{projectName\}\}/, "the placeholder must not reach disk");

  // The catch-all route's dots are a path, not a traversal, all the way to the file system.
  assert.ok(ctx.written.some((f) => endsWithPath(f.path, "app/[...slug]/page.tsx")));
  // Local config is written here, never carried by the package (it holds a token).
  assert.ok(ctx.written.some((f) => f.path.endsWith(".env.local")));
});

test("without --site the content half is not applied, and says so", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "nextjs-starter", "--yes"]);

  const result = await init.run(flags, positionals, ctx);

  assert.equal(result.applied, null);
  assert.match(ctx.stdout.text, /content NOT applied/);
  assert.match(ctx.stdout.text, /shio apply --blueprint nextjs-starter --site/);
  assert.ok(
    !fetchImpl.calls.some((call) => /\/apply$/.test(call.url)),
    "no site means no write to the instance"
  );
});

test("with --site the content half is applied in the same command", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor([
    "cool-site", "--blueprint", "nextjs-starter", "--site", "Acme", "--yes",
  ]);

  const result = await init.run(flags, positionals, ctx);

  assert.equal(result.applied.site, "Acme");
  assert.match(ctx.stdout.text, /applied 3 content change\(s\) into site Acme/);
  assert.ok(fetchImpl.calls.some((call) => /\/apply$/.test(call.url)));
});

test("an existing file is skipped unless --force", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl, { existing: ["package.json"] });
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "nextjs-starter", "--yes"]);

  await init.run(flags, positionals, ctx);

  assert.match(ctx.stdout.text, /skipped package\.json \(exists — pass --force to overwrite\)/);
  assert.ok(!ctx.written.some((f) => endsWithPath(f.path, "cool-site/package.json")));

  const forcedCtx = ctxWith(routedFetch(), { existing: ["package.json"] });
  const forced = flagsFor([
    "cool-site", "--blueprint", "nextjs-starter", "--force", "--yes",
  ]);
  await init.run(forced.flags, forced.positionals, forcedCtx);
  assert.match(forcedCtx.stdout.text, /overwrote package\.json/);
});

test("a blueprint with no front-end half is a teaching refusal, not an empty project", async () => {
  const contentOnly = { ...DETAIL, files: null };
  const ctx = ctxWith(routedFetch(contentOnly));
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "blog", "--yes"]);

  await assert.rejects(() => init.run(flags, positionals, ctx), /carries no front-end files/);
});

// --- SH447: a refusal that already did half the job ---

/** A package with a parameter that has no default — `landing`'s and `docs`' actual shape. */
const NEEDS_BRAND = {
  ...DETAIL,
  manifest: {
    ...DETAIL.manifest,
    name: "landing",
    params: {
      type: "object",
      required: ["brand"],
      properties: {
        brand: { type: "string", description: "The company or product name." },
        headline: { type: "string", default: "Start in an afternoon" },
        projectName: { type: "string", default: "my-shio-site" },
      },
    },
  },
};

test("SH447: a required parameter is refused before a single file is written", async () => {
  // Measured before this: `shio init viglet3-web --blueprint marketing-site --site Viglet3` printed
  // one line — the server's refusal on `brand` — and exited non-zero having already written 15
  // files, `app/[...slug]/page.tsx` and a correct `.env.local`. The retry then failed with "Target
  // directory is not empty", which is true and unexplainable from anything the caller was told.
  const fetchImpl = routedFetch(NEEDS_BRAND);
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor([
    "viglet3-web", "--blueprint", "landing", "--site", "Viglet3", "--yes",
  ]);

  await assert.rejects(() => init.run(flags, positionals, ctx), (error) => {
    assert.match(error.message, /requires 1 parameter\(s\)/);
    assert.match(error.message, /brand — The company or product name\./,
      "the package's own description, because the schema is the authority");
    assert.match(error.message, /nothing is on disk to clean up/);
    assert.match(error.message, /--param brand=<value>/, "P6: the flag that fixes it");
    assert.match(error.message, /shio blueprints landing/, "and the read that states the rest");
    return true;
  });

  assert.deepEqual(ctx.written, [],
    "THE assertion: reporting what landed would make the run honest and still leave the directory "
      + "that blocks the retry, so the check has to come first rather than the report");
  assert.ok(
    !fetchImpl.calls.some((call) => /\/apply$/.test(call.url)),
    "and nothing was applied either — the run stops at the read that told us the schema",
  );
});

test("SH447: --param and --params reach the blueprint, which is what makes it scaffoldable at all", async () => {
  // The other half of the same defect: `init --blueprint` had neither flag, so it always sent
  // `{projectName}` and a package with a required parameter could not be scaffolded through it by
  // any invocation. One parser with `apply`, so `--param` means one thing in both (SH245).
  const fetchImpl = routedFetch(NEEDS_BRAND);
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor([
    "viglet3-web", "--blueprint", "landing", "--site", "Viglet3", "--param", "brand=Viglet", "--yes",
  ]);

  const result = await init.run(flags, positionals, ctx);

  assert.ok(result.files.length, "the scaffold was written");
  const applied = fetchImpl.calls.find((call) => /\/apply$/.test(call.url));
  assert.ok(applied, "and the content half ran");
  assert.ok(
    ctx.written.some((file) => file.path.includes("viglet3-web")),
    "into the project the caller named",
  );
});

test("SH447: with no --site the check does not run, because the refusal never did either", async () => {
  // The refusal is being moved earlier, not widened. Without `--site` this command writes files and
  // calls nothing, so a run that works today must keep working — checking here would be a new
  // refusal wearing a bug fix's name.
  const ctx = ctxWith(routedFetch(NEEDS_BRAND));
  const { flags, positionals } = flagsFor(["viglet3-web", "--blueprint", "landing", "--yes"]);

  const result = await init.run(flags, positionals, ctx);

  assert.ok(result.files.length);
  assert.match(ctx.stdout.text, /content NOT applied/);
});

test("SH447: a --site that is not there is refused before the write too, and names --create-site", async () => {
  // The same defect with a second trigger, found by running the parameter fix against a live
  // instance: with `brand` supplied, a `--site` naming a site that does not exist refused *after*
  // the fifteen files, exactly as the parameter had. `apply --blueprint` has `--create-site` and
  // `init` did not, so the remedy the server's own refusal names was one this command could not
  // express — SH439's shape at a third call site.
  const notFound = async (url) => {
    if (/\/agent\/resolve/.test(String(url))) {
      return {
        ok: false,
        status: 404,
        headers: { get: () => null },
        async text() {
          return JSON.stringify({ title: "No site" });
        },
        async json() {
          return { title: "No site" };
        },
      };
    }
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(DETAIL);
      },
      async json() {
        return DETAIL;
      },
    };
  };
  const ctx = ctxWith(notFound);
  const { flags, positionals } = flagsFor([
    "sh447-web", "--blueprint", "nextjs-starter", "--site", "Absent", "--yes",
  ]);

  await assert.rejects(() => init.run(flags, positionals, ctx), (error) => {
    assert.match(error.message, /no site named "Absent"/);
    assert.match(error.message, /nothing is on disk to clean up/);
    assert.match(error.message, /--create-site/, "P6: the flag that resolves it, which now exists");
    return true;
  });
  assert.deepEqual(ctx.written, []);
});

// --- SH502: the built-in template does not render sections ---

/**
 * A stub instance that answers `find` with one Page and `read` with whatever that Page holds.
 *
 * Both calls, because the check is two: `find` names the Pages of the site and one `read` says
 * whether any of them is made of sections. A stub that answered only the first would let a
 * refusal keyed on the wrong field pass.
 */
function siteWithPage(data) {
  const answers = (href) => {
    if (/\/agent\/find/.test(href)) {
      return { count: 1, truncated: false, results: [{ address: "post:Replica/about", type: "Page" }] };
    }
    if (/\/agent\/read/.test(href)) {
      return [{ address: "post:Replica/about", type: "Page", data }];
    }
    return DETAIL;
  };
  const calls = [];
  const fetchImpl = async (url, requestInit) => {
    const href = String(url);
    calls.push({ url: href, method: requestInit?.method ?? "GET" });
    const body = answers(href);
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(body);
      },
      async json() {
        return body;
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

test("SH502: a site whose pages are sections is refused the template that cannot draw them", async () => {
  // Measured: the built-in template installs (SH446) and carries no sections renderer, so it builds,
  // installs, serves and shows empty pages — a failure nothing reports until somebody opens one.
  // The refusal itself is unchanged by the release; what changed is the way OUT of it, below.
  const fetchImpl = siteWithPage({ sections: ["/sections/hero", "/sections/features"] });
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor(["replica-web", "--site", "Replica", "--yes"]);

  await assert.rejects(() => init.run(flags, positionals, ctx), (error) => {
    assert.match(error.message, /builds its pages from sections/);
    assert.match(error.message, /post:Replica\/about/, "the page it read, so the claim is checkable");
    assert.match(error.message, /marketing-site/, "P6: where a sections renderer comes from");
    assert.match(error.message, /nothing is on disk to clean up/);
    return true;
  });

  assert.deepEqual(ctx.written, [],
    "refused before the write, for SH447's reason: a directory left behind blocks the retry");
});

test("SH502: with the vocabulary published, the refusal names the blueprint as the way out", async () => {
  // The reversal, and the reason `sectionsRenderer` reads `published.mjs` instead of writing the
  // sentence: while `@viglet/shio-sections` answered E404 this message had to say the recommended
  // blueprint could NOT be installed either, and point at Shio's own renderer as the honest answer.
  // The release landed 2026-08-12 at 2026.3.2, so it now recommends the blueprint outright — a
  // refusal recommending a scaffold whose `npm install` fails would be the same defect one command
  // along, and this is the assertion that the message moved with the table rather than being edited.
  const ctx = ctxWith(siteWithPage({ sections: ["/sections/hero"] }));
  const { flags, positionals } = flagsFor(["replica-web", "--site", "Replica", "--yes"]);

  await assert.rejects(() => init.run(flags, positionals, ctx), (error) => {
    assert.match(
      error.message,
      /Scaffold the one that does render them:\s+shio init <name> --blueprint marketing-site --site Replica/,
      "one command, complete, with the site already substituted (P6)",
    );
    assert.doesNotMatch(
      error.message,
      /unpublished on the npm registry|cannot be\ninstalled yet/,
      "and it no longer says the blueprint cannot be installed — that was true for two blocks and "
        + "is now the stalest thing this command could tell a caller",
    );
    return true;
  });
});

/**
 * A real temporary cwd, because the built-in template does **not** write through `ctx.fs`.
 *
 * The blueprint path takes every byte from `ctx.fs`, so the tests above assert on `ctx.written` and
 * touch no disk. `create-shio-app`'s `scaffold` uses `node:fs` directly, so a test that reaches it
 * with `cwd: "/repo"` creates `D:\repo\<name>` for real — which is how this was found, on the second
 * run: "Target directory is not empty", from the first run's leftovers.
 */
async function scratchCtx(fetchImpl, t) {
  const dir = await mkdtemp(join(tmpdir(), "shio-init-"));
  t.after(() => rm(dir, { recursive: true, force: true }));
  return { ...ctxWith(fetchImpl), cwd: dir };
}

test("SH502: a site whose pages carry a body is scaffolded, because the template renders that", async (t) => {
  // The assertion that keeps this from being a blanket refusal on `--site`. The template is the right
  // answer for a site the CDA serves as fields — which is every site SH119 was written for — so a
  // check that refused those would have replaced a silent failure with a loud one.
  const ctx = await scratchCtx(siteWithPage({ body: "<p>Hello</p>" }), t);
  const { flags, positionals } = flagsFor(["fields-web", "--site", "Replica", "--yes"]);

  const result = await init.run(flags, positionals, ctx);

  assert.ok(result.files.length, "the built-in template was written");
});

test("SH502: an empty sections list is not a sections site — the field exists on every Page", async (t) => {
  const ctx = await scratchCtx(siteWithPage({ sections: [], body: "<p>Hello</p>" }), t);
  const { flags, positionals } = flagsFor(["empty-web", "--site", "Replica", "--yes"]);

  assert.ok((await init.run(flags, positionals, ctx)).files.length);
});

test("SH502: with no --site nothing is read, because the check has no site to ask about", async (t) => {
  const fetchImpl = siteWithPage({ sections: ["/sections/hero"] });
  const ctx = await scratchCtx(fetchImpl, t);
  const { flags, positionals } = flagsFor(["plain-web", "--yes"]);

  const result = await init.run(flags, positionals, ctx);

  assert.ok(result.files.length);
  assert.deepEqual(fetchImpl.calls, [], "`shio init` needs no instance and must keep needing none");
});

test("SH502: an unreachable instance leaves the command exactly as it was (SH119)", async (t) => {
  // The courtesy-check bargain `requireSite` strikes one path over. Without `--blueprint` this
  // command must produce the same project as `npm create shio-app`, which cannot reach an instance
  // at all — so a refusal that depended on one would be a new failure mode wearing a fix's name.
  const ctx = await scratchCtx(async () => {
    throw new Error("ECONNREFUSED");
  }, t);
  const { flags, positionals } = flagsFor(["offline-web", "--site", "Replica", "--yes"]);

  assert.ok((await init.run(flags, positionals, ctx)).files.length);
});

test("SH447: --create-site skips the check and travels to the server, as it does on apply", async () => {
  // Not merely skipped locally: the flag has to reach the request, or the check would be the only
  // thing it did and the server would refuse the run this command just let through.
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor([
    "sh447-web", "--blueprint", "nextjs-starter", "--site", "Fresh", "--create-site", "--yes",
  ]);

  await init.run(flags, positionals, ctx);

  assert.ok(
    !fetchImpl.calls.some((call) => /\/agent\/resolve/.test(call.url)),
    "the check is skipped — the caller already answered the question it asks",
  );
  assert.ok(fetchImpl.calls.some((call) => /\/apply$/.test(call.url)));
});
