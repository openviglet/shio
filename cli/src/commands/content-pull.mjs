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
import { join } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { ASSETS_DIR, fileFields, idsToPaths } from "../content-assets.mjs";
import {
  BASE_FILE,
  CONTENT_DIR,
  FOLDERS_DIR,
  bodyFields,
  deleteDoc,
  deleteFolderMarker,
  deleteLocalNotes,
  readBase,
  readLocal,
  readLocalFolders,
  readLocalNotes,
  readRemoteNotes,
  sidecarFields,
  sitesOf,
  readRemote,
  readRemoteAssets,
  readRemoteFolders,
  writeAssetBytes,
  writeBase,
  writeDoc,
  writeFolderMarker,
  writeLocalNotes,
  writeRemoteSidecar,
} from "../content-store.mjs";
import { reconcileNotes } from "../content-notes.mjs";
import {
  ancestorsOf,
  baseEntryFor,
  reconcile,
  reconcileFolders,
  resolveConflict,
} from "../content-sync.mjs";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.contentDir ?? CONTENT_DIR;
  const site = flags.site ?? null;
  const side = flags.theirs ? "theirs" : flags.ours ? "ours" : null;
  const dryRun = Boolean(flags.dryRun);

  const agent = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const admin = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  const local = await readLocal(ctx, { dir, site });
  const foldersDir = flags.foldersDir ?? FOLDERS_DIR;
  const localFolders = await readLocalFolders(ctx, { dir: foldersDir, site });
  const { docs: remote, truncated, count, skipped } = await readRemote(agent, {
    site,
    // SH173 — scope the server read to the sites this tree holds. A first pull into an empty
    // tree has no sites to name and reads the instance, which is right: there is nothing to
    // scope to yet, and that is the one case where reading everything is the intent.
    sites: sitesOf(local, localFolders),
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

  const defs = await admin.getPostTypeDefs();
  const bodyField = bodyFields(defs);
  // SH191 — a layout's HTML and a region's JavaScript are programs, and a program in escaped
  // JSON inside YAML frontmatter is the one place law P4 fails on the artifact an iterative
  // loop edits most. They project beside the `.md` instead.
  const sidecarField = sidecarFields(defs);

  // SH145 — the assets, before the posts. Every `File` field on the way in is rewritten from
  // an id to an asset path, so the base, the server side and the file side all speak paths and
  // a File field does not read as changed on every sync.
  const assetsDir = flags.assetsDir ?? ASSETS_DIR;
  const fileFieldMap = fileFields(defs);
  const assets = await pullAssets(ctx, { agent, admin, local, localFolders, assetsDir, dryRun });
  const unresolvedAssets = new Set();
  for (const [address, doc] of Object.entries(remote)) {
    const { doc: mapped, unresolved } = idsToPaths(doc, fileFieldMap, assets.byId);
    remote[address] = mapped;
    for (const id of unresolved) unresolvedAssets.add(id);
  }
  if (unresolvedAssets.size) {
    // Left as ids on purpose: the File post is in a site this run did not read, or it is gone.
    // Inventing a path would put a broken one in the tree, which pushes back as a failed
    // upload instead of the dangling reference `shio verify` already reports.
    ctx.stdout.write(
      `shio pull --content: ${unresolvedAssets.size} File reference(s) name no asset this run ` +
        "could see and stay as ids (run `shio verify` for the dangling-reference report).\n",
    );
  }

  const base = await readBase(ctx, flags.baseFile ?? BASE_FILE);

  const report = {
    written: [],
    deleted: [],
    unchanged: [],
    conflicts: [],
    kept: [],
    folders: { written: [], deleted: [], kept: [] },
    assets: assets.written,
    notes: { written: [], deleted: [], conflicts: [] },
  };
  const nextPosts = { ...base.posts };
  // Collected from the documents rather than read back off `nextPosts` (SH148): a base entry is
  // fingerprints now, so it has no `folder` to read, and a post the file tree alone changed
  // never gets a fresh entry. Reading the folder there would miss it and pull would write a
  // redundant `_folder.md` for a folder a post already declares.
  const declaredFolders = new Set();

  for (const address of addresses(base.posts, remote, local)) {
    const versions = {
      base: base.posts[address] ?? null,
      remote: remote[address] ?? null,
      local: local[address] ?? null,
    };
    let result = reconcile(versions);
    if (result.kind === "conflict" && side) result = resolveConflict(versions, side);
    for (const doc of [result.doc, versions.local, versions.remote]) {
      if (!doc?.site) continue;
      for (const ancestor of ancestorsOf(doc.folder ?? "/")) declaredFolders.add(`${doc.site}|${ancestor}`);
    }

    switch (result.kind) {
      case "remote-added":
      case "remote-changed":
      case "merged":
        if (!dryRun) {
          await writeDoc(ctx, result.doc, fieldFor(bodyField, result.doc), {
            dir,
            sidecars: sidecarsFor(sidecarField, result.doc),
          });
        }
        report.written.push(`${address}${result.kind === "merged" ? " (merged)" : ""}`);
        nextPosts[address] = baseEntryFor(result.doc);
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
        nextPosts[address] = baseEntryFor(result.doc);
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

  // SH149 — the folder half. It runs after the posts because "which folders does a post
  // already declare" is the input that keeps the marker set minimal.
  const { folders: remoteFolders, truncated: foldersTruncated } = await readRemoteFolders(agent, {
    sites: sitesOf(local, localFolders).length
      ? sitesOf(local, localFolders)
      : [...new Set(Object.values(remote).map((doc) => doc.site).filter(Boolean))],
  });
  const folderPlan = reconcileFolders({
    base: base.folders,
    remote: remoteFolders,
    local: localFolders,
    implied: declaredFolders,
  });
  for (const marker of folderPlan.write) {
    if (!dryRun) await writeFolderMarker(ctx, marker, { dir: foldersDir });
    report.folders.written.push(`folder:${marker.site}${marker.folder}`);
  }
  for (const marker of folderPlan.delete) {
    if (!dryRun) await deleteFolderMarker(ctx, marker, { dir: foldersDir });
    report.folders.deleted.push(`folder:${marker.site}${marker.folder}`);
  }
  for (const marker of folderPlan.keep) {
    report.folders.kept.push(`folder:${marker.site}${marker.folder}`);
  }

  // SH161 — the site memory. Last, because it is per-site and the site list is only settled
  // once the posts and folders have been walked. A note is not content: it is never a
  // desired-state document and `--prune` can never act on it.
  const noteSites = sitesOf(local, localFolders).length
    ? sitesOf(local, localFolders)
    : [...new Set(Object.values(remote).map((doc) => doc.site).filter(Boolean))];
  const nextNotes = { ...(base.notes ?? {}) };
  for (const noteSite of noteSites) {
    const localNotes = await readLocalNotes(ctx, noteSite, { dir });
    const plan = reconcileNotes({
      base: (base.notes ?? {})[noteSite] ?? {},
      remote: await readRemoteNotes(agent, noteSite),
      // No file yet is not "every note was deleted" — it is a tree that has never projected
      // this site's memory, so the server's notes are all additions.
      local: localNotes ?? [],
    });
    // A locally deleted note is honoured by `push --content`, not here: a pull writes files,
    // and forgetting a note on the server as a side effect of reading would be the same
    // surprise a pull that published a draft would be.
    const keep = [
      ...plan.write,
      ...Object.keys(plan.agreed)
        .filter((key) => !plan.write.some((entry) => entry.key === key))
        .map((key) => ({
          key,
          note: plan.agreed[key],
          author: (localNotes ?? []).find((entry) => entry.key === key)?.author ?? null,
        })),
    ];
    for (const conflict of plan.conflicts) {
      report.notes.conflicts.push(`${noteSite}:${conflict.key}`);
    }
    if (plan.conflicts.length) continue;

    if (!keep.length) {
      if (localNotes !== null && !dryRun) await deleteLocalNotes(ctx, noteSite, { dir });
      if (localNotes !== null) report.notes.deleted.push(noteSite);
      delete nextNotes[noteSite];
      continue;
    }
    if (plan.write.length || plan.deleteLocal.length || localNotes === null) {
      if (!dryRun) await writeLocalNotes(ctx, { site: noteSite, notes: keep }, { dir });
      report.notes.written.push(noteSite);
    }
    nextNotes[noteSite] = Object.fromEntries(keep.map((entry) => [entry.key, entry.note]));
  }

  // The cursor is taken *after* the read, so anything that landed during it is reported
  // by the next pull rather than assumed to be included in this one.
  const cursor = await currentCursor(agent, site, base.cursor);
  if (!dryRun) {
    await writeBase(
      ctx,
      {
        cursor,
        partial: base.partial || truncated || foldersTruncated,
        posts: nextPosts,
        folders: folderPlan.agreed,
        notes: nextNotes,
      },
      flags.baseFile ?? BASE_FILE,
    );
  }

  ctx.stdout.write(render(report, { dir, dryRun, truncated: truncated || foldersTruncated }));
  if (report.notes.conflicts.length) {
    // Reported, and the file is left exactly as it was — the same rule the post merge
    // follows. A note is one sentence somebody meant; picking a side would discard the
    // other one with nothing to recover it from.
    ctx.stderr.write(
      `shio pull --content: ${report.notes.conflicts.length} memory note(s) changed on both ` +
        `sides and were not written (${report.notes.conflicts.join(", ")}). Edit the note in ` +
        "shio/content/<site>/.memory.yaml, or run `shio remember <key> \"<note>\"` to settle " +
        "it on the server, then pull again.\n",
    );
  }
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

/**
 * Downloads the assets this tree does not have yet, and returns the id → path index (SH145).
 *
 * **A path that already exists on disk is not re-downloaded.** A `File` post's bytes are
 * immutable — there is no replace-the-bytes operation, an upload always creates a new
 * id-keyed post — so the same path is the same asset, and re-fetching it would spend a
 * request per image on every pull to rewrite identical bytes.
 */
async function pullAssets(ctx, { agent, admin, local, localFolders, assetsDir, dryRun }) {
  const sites = sitesOf(local, localFolders);
  const assets = await readRemoteAssets(agent, admin, {
    sites: sites.length ? sites : [],
  });
  const written = [];
  for (const [path, item] of assets.byPath) {
    if (await hasAsset(ctx, path, assetsDir)) continue;
    if (!dryRun) await writeAssetBytes(ctx, path, await admin.downloadStaticFile(item.id), { dir: assetsDir });
    written.push(path);
  }
  return { ...assets, written };
}

async function hasAsset(ctx, path, dir) {
  try {
    await ctx.fs.readFile(join(ctx.cwd, dir, path));
    return true;
  } catch (error) {
    if (error?.code === "ENOENT") return false;
    throw error;
  }
}

/** Every address any of the three versions knows about, in a stable order. */
function addresses(base, remote, local) {
  return [...new Set([...Object.keys(base), ...Object.keys(remote), ...Object.keys(local)])].sort();
}

function fieldFor(bodyField, doc) {
  return doc?.bodyField ?? bodyField.get(doc?.type) ?? null;
}

/**
 * The code fields of the post-type this document has (SH191).
 *
 * A type this run could not read the def for gets **none**, which keeps the values in
 * `fields:` — the pre-SH191 shape. Ugly, and it is the only reading that cannot lose content:
 * inventing a sidecar for a field whose widget is unknown would put prose in a `.html`.
 */
function sidecarsFor(sidecarField, doc) {
  return sidecarField.get(doc?.type) ?? [];
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
  if (report.assets?.length) {
    lines[0] += ` · ${report.assets.length} asset(s) downloaded`;
  }
  const folders = report.folders ?? { written: [], deleted: [], kept: [] };
  if (folders.written.length || folders.deleted.length || folders.kept.length) {
    // Counted separately because a folder is not a post: a reader scanning for "how much
    // content moved" must not have structure padding the number (SH149).
    lines[0] += ` · folders: ${folders.written.length} written, ${folders.deleted.length} ` +
      `deleted, ${folders.kept.length} local-only`;
  }
  for (const address of report.written) lines.push(`  + ${address}`);
  for (const address of folders.written) lines.push(`  + ${address}`);
  for (const address of report.deleted) lines.push(`  - ${address}`);
  for (const address of folders.deleted) lines.push(`  - ${address}`);
  for (const address of report.kept) lines.push(`  = ${address}`);
  for (const address of folders.kept) lines.push(`  = ${address}`);
  for (const conflict of report.conflicts) {
    lines.push(`  ! ${conflict.address} (${conflict.fields.join(", ")})`);
  }
  if (truncated) {
    lines.push("  (partial projection — the server's post list was truncated)");
  }
  return `${lines.join("\n")}\n`;
}
