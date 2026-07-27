/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio pull --content` (SH87) — project the instance's content into `shio/content/**`,
 * reconciling with what is already there (SH89).
 *
 * WHAT IT COSTS, AND WHY THAT IS THE POINT
 * One `find` plus one `read` per batch of posts, and after that every edit an agent makes
 * is `Edit`/`Grep` against a text tree instead of a read-modify-write over HTTP. The API
 * cost of authoring collapses to one pull and one push (law P4).
 *
 * IT NEVER CLOBBERS
 * A pull is not a download. If the file tree has changes the server has not seen, they
 * survive: a post only one side changed takes that side, a post both sides changed merges
 * per field, and a field both sides changed is a **conflict** — reported, with nothing
 * written for that post, until a human picks `--ours` or `--theirs`.
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import {
  BASE_FILE,
  CONTENT_DIR,
  bodyFields,
  deleteDoc,
  readBase,
  readLocal,
  sitesOf,
  readRemote,
  writeBase,
  writeDoc,
  writeRemoteSidecar,
} from "../content-store.mjs";
import { reconcile, resolveConflict } from "../content-sync.mjs";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.contentDir ?? CONTENT_DIR;
  const site = flags.site ?? null;
  const side = flags.theirs ? "theirs" : flags.ours ? "ours" : null;
  const dryRun = Boolean(flags.dryRun);

  const agent = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const admin = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  const local = await readLocal(ctx, { dir, site });
  const { docs: remote, truncated, count, skipped } = await readRemote(agent, {
    site,
    // SH173 — scope the server read to the sites this tree holds. A first pull into an empty
    // tree has no sites to name and reads the instance, which is right: there is nothing to
    // scope to yet, and that is the one case where reading everything is the intent.
    sites: sitesOf(local),
    limit: Number(flags.limit ?? 200),
  });
  if (truncated && !flags.allowPartial) {
    // A partial projection that a later `--prune` reads as complete would delete the
    // posts it never saw. Refusing is cheaper than that (SH138's rule).
    throw new Error(
      `shio pull --content: the server returned a truncated post list (${count} rows), so this ` +
        "projection would be incomplete — and a later `push --content --prune` would read the " +
        "gaps as deletions. Narrow it with --site/--limit, raise shio.agent.read.max-limit on " +
        "the server, or accept an explicitly partial projection with --allow-partial.",
    );
  }

  if (skipped.length) {
    // SH173: what could not be read by address, named — the projection is short by exactly
    // these and saying so beats letting a later diff look like a deletion.
    ctx.stdout.write(
      `shio pull --content: ${skipped.length} post(s) could not be read by address and are not ` +
        "in this projection (run `shio verify` for the full report):\n" +
        skipped.slice(0, 5).map((row) => `  ! ${row.address} — ${row.reason}\n`).join(""),
    );
  }

  const bodyField = bodyFields(await admin.getPostTypeDefs());
  const base = await readBase(ctx, flags.baseFile ?? BASE_FILE);

  const report = { written: [], deleted: [], unchanged: [], conflicts: [], kept: [] };
  const nextPosts = { ...base.posts };

  for (const address of addresses(base.posts, remote, local)) {
    const versions = {
      base: base.posts[address] ?? null,
      remote: remote[address] ?? null,
      local: local[address] ?? null,
    };
    let result = reconcile(versions);
    if (result.kind === "conflict" && side) result = resolveConflict(versions, side);

    switch (result.kind) {
      case "remote-added":
      case "remote-changed":
      case "merged":
        if (!dryRun) await writeDoc(ctx, result.doc, fieldFor(bodyField, result.doc), { dir });
        report.written.push(`${address}${result.kind === "merged" ? " (merged)" : ""}`);
        nextPosts[address] = snapshot(result.doc);
        break;
      case "remote-deleted":
        if (!dryRun) await deleteDoc(ctx, versions.base ?? versions.local, { dir });
        report.deleted.push(address);
        delete nextPosts[address];
        break;
      case "local-added":
      case "local-changed":
        // The file tree is ahead here; a pull has nothing to bring down and must not
        // undo it. `push --content` is the other half.
        report.kept.push(address);
        break;
      case "local-deleted":
        // The file was deleted and the server still has the post. Resurrecting the file
        // would undo a deliberate deletion, so it stays deleted and stays reported —
        // `push --content --prune` is what makes it real on the server.
        report.kept.push(`${address} (deleted locally, still on the server)`);
        break;
      case "unchanged":
        report.unchanged.push(address);
        nextPosts[address] = snapshot(result.doc);
        break;
      case "conflict":
        report.conflicts.push({ address, fields: result.conflicts });
        if (flags.showRemote && versions.remote && !dryRun) {
          await writeRemoteSidecar(ctx, versions.remote, fieldFor(bodyField, versions.remote), { dir });
        }
        break;
      default:
        break;
    }
  }

  // The cursor is taken *after* the read, so anything that landed during it is reported
  // by the next pull rather than assumed to be included in this one.
  const cursor = await currentCursor(agent, site, base.cursor);
  if (!dryRun) {
    await writeBase(
      ctx,
      { cursor, partial: base.partial || truncated, posts: nextPosts },
      flags.baseFile ?? BASE_FILE,
    );
  }

  ctx.stdout.write(render(report, { dir, dryRun, truncated }));
  if (report.conflicts.length) {
    ctx.stderr.write(
      `shio pull --content: ${report.conflicts.length} post(s) in conflict — nothing was written ` +
        "for them. Edit the file and pull again, or re-run with --theirs (take the server's " +
        "values) or --ours (keep the file's). --show-remote writes the server's copy beside " +
        "each conflicted file as <file>.remote.\n",
    );
    return { ...report, cursor, exitCode: 1 };
  }
  return { ...report, cursor };
}

/** Every address any of the three versions knows about, in a stable order. */
function addresses(base, remote, local) {
  return [...new Set([...Object.keys(base), ...Object.keys(remote), ...Object.keys(local)])].sort();
}

function fieldFor(bodyField, doc) {
  return doc?.bodyField ?? bodyField.get(doc?.type) ?? null;
}

function snapshot(doc) {
  return { site: doc.site, url: doc.url, type: doc.type ?? null, folder: doc.folder ?? null, data: doc.data ?? {} };
}

/**
 * The position to resume from next time. Asking for one entry is enough: the response's
 * cursor is the newest position regardless of how many rows come with it.
 */
async function currentCursor(agent, site, fallback) {
  try {
    const changes = await agent.changes({ site, limit: 1 });
    return changes.cursor ?? fallback ?? null;
  } catch {
    // An instance without SH88 (or a credential that cannot read the feed) still gets a
    // working projection; it just cannot do an incremental pull.
    return fallback ?? null;
  }
}

function render(report, { dir, dryRun, truncated }) {
  const lines = [
    `shio pull --content: ${report.written.length} written, ${report.deleted.length} deleted, ` +
      `${report.unchanged.length} unchanged, ${report.kept.length} local-only, ` +
      `${report.conflicts.length} conflicted → ${dir}${dryRun ? " (dry run — nothing written)" : ""}`,
  ];
  for (const address of report.written) lines.push(`  + ${address}`);
  for (const address of report.deleted) lines.push(`  - ${address}`);
  for (const address of report.kept) lines.push(`  = ${address}`);
  for (const conflict of report.conflicts) {
    lines.push(`  ! ${conflict.address} (${conflict.fields.join(", ")})`);
  }
  if (truncated) {
    lines.push("  (partial projection — the server's post list was truncated)");
  }
  return `${lines.join("\n")}\n`;
}
