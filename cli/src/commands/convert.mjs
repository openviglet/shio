/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio convert` (SH417) - replication's apply phase: the accepted plan, as content.
 *
 * ## The verb the pipeline was one short of
 *
 * SH379 stopped at the proposal on purpose, because review and mutation in one command is the
 * surprise P7 forbids. This is the other half, and it is deliberately a **translation into existing
 * verbs rather than a new engine**: it builds `shio apply`'s desired-state document and sends it
 * down the same path, which is idempotent, `--dry-run`-able and the only write contract there is
 * (P10). What is new here is the reading, the two refusals, and the preconditions.
 *
 * ## What it refuses, and why each one is a refusal rather than a guess
 *
 * A **stale acceptance** (SH417's rule): an id names a block's structure, so one the current proposal
 * does not hold means the source changed after the review. Writing it would apply a decision nobody
 * made about the markup that is there now.
 *
 * A **missing field** (SH436): the theme goes into `Theme.TOKENS` and the scripts into
 * `SiteScripts.SCRIPTS`, and an instance whose render types predate those fields has nowhere to put
 * them. The proposal already states its preconditions; this is the first moment there is an instance
 * to check them against, so it asks `GET /api/v2/render/post-types/drift` before it writes and names
 * the reconcile call in the refusal. Skipped on a dry run and on `--out`, which touch no instance.
 *
 * An **absent site** is the third, and until SH439 it was the one refusal a reader could not act on:
 * the resolver's fix is to add a `site.upsert` op, and this command takes no ops. Converting into a
 * *new* site is the normal shape of a replication, so `--create-site` exists — the same flag `apply`
 * has, compiled to the same op — and the refusal now names it.
 */
import { join } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { ASSETS_DIR } from "../content-assets.mjs";
import { resolveAdmin } from "../context.mjs";
import {
  buildConversion, describeConversion, staleMessage, withResolvedFileFields,
} from "../convert.mjs";
import { retellUnknownSite } from "../unknown-site.mjs";
import { proposalFor } from "./propose.mjs";
import { helpText } from "../help.mjs";

/**
 * What `shio convert --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart.
 */
export const HELP = helpText("convert");

export async function run(flags, positionals, ctx) {
  const site = typeof flags.site === "string" && flags.site.trim()
    ? flags.site.trim()
    : String(positionals[0] ?? "").trim();
  if (!site) {
    throw new Error(
      "shio convert: pass the site to write into.\n"
        + "A conversion writes posts, and a post lives in a site; guessing which one from a capture\n"
        + "would put somebody else's content in a site nobody named.\n"
        + "  shio convert --site Acme --dry-run",
    );
  }

  // The plan file and the proposal computed from the capture NOW: the pair is the whole point, since
  // an acceptance is only checkable against the candidates the current markup produces.
  const { proposal, plan, markup, inventory } = await proposalFor(flags, ctx, "convert");
  const {
    // SH684 — `kept` and `declined` beside `dropped`, because this list is enumerated by hand and a
    // field the builder returns that nobody names here reaches no reader at all: the decline was
    // being made and printed nowhere, which is the same shape as the defect the task is about.
    document, stale, sections, pages, verbatim, mixed, extras, dropped, kept, declined, home,
    unfollowed,
  } = buildConversion({
    proposal,
    plan,
    site,
    createSite: Boolean(flags.createSite),
    // SH591 — a replica made to answer a question, declared disposable at the moment it is made.
    scratch: Boolean(flags.scratch),
    // SH449 — the markup cache the proposal was computed from. A page whose blocks nobody accepted
    // is written verbatim, and this is where the bytes come from; without it the fidelity half of
    // the two documented modes has no source and the command falls back to SH438's behaviour.
    markup,
    // SH576 — a source with no document at `/` produced a site whose root answered nothing, and
    // the run said so nowhere. `buildConversion` writes a front door either way; this is which.
    home: typeof flags.home === "string" && flags.home.trim() ? flags.home.trim() : null,
    // SH584 — the capture's own `unfollowed` list, joined here against the pages being written so
    // the bound this run inherited is stated by the run rather than by the gate twelve minutes on.
    inventory,
  });
  if (stale.length) {
    throw new Error(staleMessage(stale, typeof flags.out === "string" ? flags.out : null));
  }
  if (!document.posts.length) {
    // SH449 narrowed this. It used to fire on "nothing accepted", which was the wrong question:
    // an unaccepted block stays verbatim and is written, so the empty document now means the
    // capture itself is empty — no page has markup on disk and no theme was found.
    throw new Error(
      "shio convert: this capture holds nothing to write.\n"
        + `The proposal holds ${proposal.candidates.length} candidate(s) and no page with markup on\n`
        + "disk, so there is neither a section to compose nor a page to keep verbatim.\n"
        + "  shio clone <url> --markup --assets",
    );
  }

  if (typeof flags.out === "string" && flags.out) {
    await write(join(ctx.cwd, flags.out), `${JSON.stringify(document, null, 2)}\n`, ctx);
    ctx.stdout.write(describeConversion({ document, sections, pages, verbatim, mixed, extras, requires: proposal.requires, dropped, kept, declined, home, unfollowed },
      flags));
    ctx.stdout.write(`  document: ${flags.out} (nothing was applied)\n`);
    return { document, applied: false, exitCode: 0 };
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const planOnly = Boolean(flags.check || flags.dryRun);

  if (!planOnly) {
    await requirePostTypeFields(proposal.requires ?? [], { baseUrl, user, password }, ctx);
  }

  ctx.stdout.write(describeConversion({ document, sections, pages, verbatim, mixed, extras, requires: proposal.requires, dropped, kept, declined, home, unfollowed },
    flags));

  // SH437/SH471 — the bytes, which are not content and so cannot ride the document. **Before** the
  // apply, not after: a `File` field has to hold the id of the post that holds those bytes, and the
  // post has to exist before the field can name it. It also stopped being conditional on
  // `verbatim` — that guard dated from when only fidelity markup referenced the source's paths, and
  // it is why an authorable conversion uploaded nothing and drew `<img src="">`.
  let assets = null;
  let toApply = document;
  if (!planOnly) {
    assets = await sendAssets(
      { site, inventory, flags, createSite: Boolean(flags.createSite), admin: { baseUrl, user, password } },
      ctx);
    ctx.stdout.write(
      `  assets: ${assets.uploaded} uploaded, ${assets.present} already there`
        + `${assets.missing ? `, ${assets.missing} with no bytes in the tree` : ""}`
        + `${assets.empty ? `, ${assets.empty} the source itself serves empty` : ""}`
        + " - served at the source's own paths\n",
    );
    const pointed = withResolvedFileFields(document, assets.ids);
    toApply = pointed.document;
    if (pointed.resolved.length || pointed.unresolved.length) {
      ctx.stdout.write(
        `  images: ${pointed.resolved.length} File field(s) point at the uploaded post`
          + `${pointed.unresolved.length
            ? `, ${pointed.unresolved.length} left as written (${pointed.unresolved.slice(0, 3).join(", ")})`
            : ""}\n`,
      );
    }
  }

  // SH439 — the write resolver's "add a `site.upsert` op" is un-followable here, because `convert`
  // composes its own document and takes no ops. The flag above is the remedy; this makes the
  // refusal name it, for the caller who has not passed it yet.
  const result = await (planOnly
    ? client.plan(toApply, { ids: Boolean(flags.ids) })
    : client.apply(toApply, { ids: Boolean(flags.ids) })
  ).catch((error) => {
    throw retellUnknownSite(error, { command: "convert", site });
  });

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } else {
    ctx.stdout.write(
      `  ${planOnly ? "would change" : "changed"}: ${result?.changed ?? 0} object(s)`
        + `${planOnly ? " (nothing was written)" : " - in DRAFT"}\n`,
    );
  }
  // `--check` is a drift guard, so a non-empty plan is the failure it exists to report.
  const drifted = Boolean(flags.check) && Number(result?.changed ?? 0) > 0;
  return {
    document, applied: !planOnly, assets, changed: result?.changed ?? 0, exitCode: drifted ? 1 : 0,
  };
}

/**
 * The captured assets, uploaded at the source's own paths (SH437).
 *
 * <h3>Why this is here and not in `push --content`</h3>
 *
 * <p>SH145's rule is that an asset reaches the instance because a `File` **field** names it — the
 * bytes alone are never uploaded, which is right, because an asset nothing references is one
 * nothing serves. A page kept in fidelity mode breaks that premise: it has no fields at all, its
 * markup references the bytes directly by the source's path, and `source-asset-stranded` is the
 * finding that has been saying so since SH429. So the reference exists; it is in the markup rather
 * than in a field, and `push` cannot see it.</p>
 *
 * <h3>Addressed by the source's path, which is the whole feature</h3>
 *
 * <p>The folder chain is the asset's source directory and the file name is its source file name, so
 * `/img/hero.png` uploads into `folder:<site>/img` as `hero.png` — the pair SH145 addresses an asset
 * by, and therefore the pair `ShSiteAssets` walks when the renderer is asked for that path. The
 * replica's markup is never edited: it still says `src="/img/hero.png"`, and now that resolves.</p>
 *
 * <h3>Idempotent, and quiet about what it cannot do</h3>
 *
 * <p>An asset already in its folder is left alone rather than replaced — a second `convert` must not
 * re-upload a site's images, and replacing is a decision about bytes a curator may have changed. An
 * asset the capture named but never downloaded has no bytes to send and is counted, not raised:
 * `verify --checks replication` already reports it as `source-asset-missing` with the `clone` that
 * fixes it, and a refusal here would fail a conversion over an image.</p>
 */
async function sendAssets({ site, inventory, flags, createSite = false, admin }, ctx) {
  const rows = (inventory?.assets ?? []).filter((asset) => asset && !asset.external && asset.file);
  // SH471 — source path → the `File` post's id, so a section's `File` field can name the post that
  // holds those bytes rather than the path they came from. Free: the listing and the upload response
  // both already carry the id, so this costs no request that was not being made.
  const summary = { uploaded: 0, present: 0, missing: 0, empty: 0, ids: new Map() };
  if (!rows.length) return summary;

  const assetsDir = typeof flags.assetsDir === "string" ? flags.assetsDir : ASSETS_DIR;
  const client = new ShioAgentClient({ ...admin, fetch: ctx.fetch });
  const adminClient = new ShioAdminClient({ ...admin, fetch: ctx.fetch });
  const folders = new Map();

  for (const asset of rows) {
    const at = locationOf(site, asset.path);
    if (!at) continue;
    let bytes;
    try {
      bytes = await ctx.fs.readFile(join(ctx.cwd, assetsDir, asset.file));
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
      summary.missing += 1;
      continue;
    }

    let folderId = folders.get(at.folder);
    if (!folderId) {
      // The folder, through the one write path there is (P10) — `apply` is idempotent, so a
      // second run creates nothing and a first run creates the chain the source implied.
      // SH471 — `createSite` rides this apply now that the upload runs *before* the document's own.
      // Without it the first thing a `--create-site` run does is write a folder into a site that
      // does not exist yet, which is the one ordering this move could have broken.
      await client.apply({
        site, ...(createSite ? { createSite: true } : {}), folders: [{ path: at.folder }], posts: [],
      });
      // SH456 — `folder:<site>/<chain>`, and the root folder is `folder:<site>/`. This used to
      // strip the slash for the root case (`at.folder === "/" ? "" : at.folder`), which is the one
      // form that needs it: `folder:Publico` has no path at all, and `ShAddress.parse` refuses it
      // with exactly the shape it wanted. So the branch written *for* the root case was the only
      // one that produced an address the resolver rejects, and every other folder worked. It fires
      // on the main path — a single-page capture converts into the site's root folder, so every
      // fidelity conversion of a home page hit it — and it fired *after* the writes, so `convert`
      // printed its whole report and then exited 1 on work that had succeeded.
      const resolved = await client.resolve(`folder:${site}${at.folder}`);
      folderId = resolved?.id;
      if (!folderId) {
        summary.missing += 1;
        continue;
      }
      folders.set(at.folder, folderId);
    }

    const existing = await adminClient.listStaticFiles({ folderId });
    const already = (existing ?? []).find((item) => item.fileName === at.fileName);
    if (already) {
      summary.present += 1;
      // Recorded on the re-run path too, or a second `convert` — the idempotent case, which is the
      // one an agent runs most — would resolve nothing and quietly leave the paths in place.
      if (already.id) summary.ids.set(asset.path, already.id);
      continue;
    }
    const created = await adminClient.uploadStaticFile({
      folderId,
      fileName: at.fileName,
      bytes,
      contentType: asset.contentType || "application/octet-stream",
    });
    if (created?.id) summary.ids.set(asset.path, created.id);
    summary.uploaded += 1;
    // SH514 — counted apart, and it is not the same fact as `missing`. An empty file is a file:
    // `vilt-group.com` serves `/css/en.css?v=cf8427e` as 200 with 0 bytes, the capture wrote it
    // faithfully, and the upload refused it with Spring's default 400 — killing a 64-asset
    // conversion over an empty stylesheet. It uploads now, and the count is what tells a reader
    // that an asset which looks absent on the replica is absent at the source too.
    if (!bytes.length) summary.empty += 1;
  }
  return summary;
}

/** A source path as the folder chain and file name SH145 addresses an asset by. */
function locationOf(site, path) {
  const parts = String(path ?? "").split("?")[0].split("#")[0].split("/").filter(Boolean);
  if (!parts.length || parts.some((part) => part === "." || part === "..")) return null;
  const fileName = parts.pop();
  return { site, folder: parts.length ? `/${parts.join("/")}` : "/", fileName };
}

/**
 * SH436's precondition, checked at the one moment there is an instance to ask.
 *
 * <p>`GET /api/v2/render/post-types/drift` is the read that answers "does this instance declare the
 * fields the shipped model has" without writing anything, and the refusal names `reconcile`, which is
 * the one call that fixes it and changes nothing else. A theme written into a `Theme` that has no
 * `TOKENS` is the failure this exists to prevent: it would either fail somewhere unhelpful or write
 * the theme without its tokens.
 */
async function requirePostTypeFields(requires, admin, ctx) {
  if (!requires.length) return;
  const url = `${admin.baseUrl.replace(/\/+$/, "")}/api/v2/render/post-types/drift`;
  let drift;
  try {
    const response = await ctx.fetch(url, {
      headers: { Authorization: basic(admin), Accept: "application/json" },
    });
    if (!response.ok) return;
    drift = await response.json();
  } catch {
    // A drift read that could not run is not a reason to refuse a write: the apply itself will fail
    // with the server's own message, which is better than a guess made from a failed GET.
    return;
  }
  const missing = [];
  for (const row of requires) {
    const drifted = (drift ?? []).find((each) => each.postType === row.postType);
    if (drifted?.missingFields?.includes(row.field)) missing.push(row);
  }
  if (!missing.length) return;
  throw new Error(
    `shio convert: this instance's render post-types are missing `
      + `${missing.map((row) => `${row.postType}.${row.field}`).join(", ")}, so the conversion has\n`
      + "nowhere to put half of what it holds.\n"
      + `${missing.map((row) => `  ${row.postType}.${row.field} - ${row.why}`).join("\n")}\n`
      + "The repair is one call and changes nothing else:\n"
      + "  POST /api/v2/render/post-types/reconcile\n"
      + "GET /api/v2/render/post-types/drift answers the same question without writing.",
  );
}

function basic({ user, password }) {
  const raw = `${user ?? ""}:${password ?? ""}`;
  return `Basic ${Buffer.from(raw, "utf8").toString("base64")}`;
}

async function write(path, body, ctx) {
  await ctx.fs.mkdir(dirOf(path), { recursive: true });
  await ctx.fs.writeFile(path, body);
}

function dirOf(path) {
  const at = String(path).replace(/\\/g, "/").lastIndexOf("/");
  return at <= 0 ? "." : String(path).slice(0, at);
}
