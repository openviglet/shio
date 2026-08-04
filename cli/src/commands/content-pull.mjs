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
 *
 * Since SH373 that includes an **asset's bytes**, which were the last thing outside the rule: a
 * path already on disk used to be skipped, so a curator swapping an image produced a change no
 * pull would ever project. Bytes have no field-level merge, so a conflict is left on disk and
 * named — see `pullAssets`.
 */
import { join } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import {
  ASSETS_DIR,
  fileFields,
  idsToPaths,
  reconcileBytes,
  sha256Of,
} from "../content-assets.mjs";
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
  projectRemote,
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
import {
  assetPathCollisions,
  defFileCollisions,
  filePathFor,
  folderMarkerPathFor,
  markerPathCollisions,
  postPathCollisions,
  siteDirCollisions,
} from "../content-files.mjs";
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

  // SH402 — a URL that navigates instead of naming cannot become a file, and `filePathFor` says
  // so. Dropped into SH173's channel rather than thrown: the projection is short by exactly this
  // post and the rest of the pull is fine, which is the shape SH212 and SH374 both settled on.
  for (const [address, doc] of Object.entries(remote)) {
    try {
      filePathFor(doc);
    } catch (error) {
      skipped.push({ address, reason: error.message });
      delete remote[address];
    }
  }

  // SH373 — read before the assets, because the asset merge needs the agreed hashes. It used to
  // sit below them, which was fine while a pull only ever *added* an asset it did not have.
  const base = await readBase(ctx, flags.baseFile ?? BASE_FILE);

  const defs = await admin.getPostTypeDefs();
  // SH408 — `Article.json` and `article.json` are one file, and `push --prune` then reports the
  // loser as removed and deletes the post type. Named here rather than left to the disk.
  for (const group of new Set(defFileCollisions(defs.map((def) => def.name)).values())) {
    skipped.push({
      address: `post-type:${group.addresses.join(" and post-type:")}`,
      reason: `they project to one file (${group.paths.join(", ")}), so a checkout keeps one and `
        + "`push --prune` would delete the other's type. Rename one of them on the server",
    });
  }
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
  // SH406 — the sites every per-site read scopes to, computed once. The tree names them, and when
  // it names none the posts just read do: a first pull has nothing to scope to yet, which is the
  // one case where reading everything is the intent (SH173's own argument). This was written out
  // three times — the folder read, the note list, and the asset read — and the asset copy said
  // `sites.length ? sites : []`, whose branches are the same value. So a first pull walked no
  // sites for assets, downloaded nothing, left every `File` field as the id it arrived as, and
  // reported success. One binding now, because three copies of a fallback is how one loses it.
  const siteScope = sitesOf(local, localFolders).length
    ? sitesOf(local, localFolders)
    : [...new Set(Object.values(remote).map((doc) => doc.site).filter(Boolean))];
  // SH408 — `Blog` and `blog` are one content directory, one folder directory and one memory
  // file. Detected before any of the three is written, because the first write is what loses it.
  const siteClashes = siteDirCollisions(siteScope);
  const clashingSites = new Set(siteClashes.keys());
  for (const group of new Set(siteClashes.values())) {
    skipped.push({
      address: `site:${group.addresses.join(" and site:")}`,
      reason: `they project to one directory (${group.paths.join(", ")}), so a Windows or macOS `
        + "checkout holds one of them. Rename one of the sites on the server",
    });
  }
  const assets = await pullAssets(ctx, {
    agent, admin, sites: siteScope.filter((name) => !clashingSites.has(name)),
    assetsDir, dryRun, base: base.assets ?? {},
  });
  const unresolvedAssets = new Set();
  for (const [address, doc] of Object.entries(remote)) {
    const { doc: mapped, unresolved } = idsToPaths(doc, fileFieldMap, assets.byId);
    remote[address] = mapped;
    for (const id of unresolved) unresolvedAssets.add(id);
  }
  // SH253 — and then through the projection itself, for the same reason SH145 rewrites the ids
  // above: the merge has to compare documents that all speak the file tree's language. Without
  // it a value the format normalizes (a body's CRLF) is fingerprinted in the server's form and
  // read back in the file's, so every later pull reports a change nobody made. After the asset
  // rewrite, because a path is what the file will hold.
  Object.assign(remote, projectRemote(remote, defs));
  if (assets.unprojectable?.length) {
    // SH256 — named, because the `File` field pointing at one of these stays an id and a silent
    // skip would read as "this site has no such asset". The server holds bytes the tree cannot
    // represent, which is a rename on the server, not something a pull can fix.
    ctx.stdout.write(
      `shio pull --content: ${assets.unprojectable.length} asset(s) have a file name that cannot ` +
        "be a path and were not projected (rename them on the server):\n" +
        assets.unprojectable
          .slice(0, 5)
          .map((row) => `  ! ${row.site}${row.folder === "/" ? "" : row.folder}/ — ${JSON.stringify(row.fileName)}\n`)
          .join(""),
    );
  }
  if (assets.truncated) {
    // SH405 — the same sitemap walk the post list treats as fatal, and the same `truncated` back
    // from it. It was computed, returned, and read by nothing: the dangerous half of a short
    // inventory was already handled (the pull's own folder read marks the snapshot partial), so
    // what was left was a run that knew why it came up short and did not say. Reported before the
    // unresolved references below, because when both fire this one is their cause.
    ctx.stdout.write(
      "shio pull --content: the asset inventory is short — the server's sitemap walk came back " +
        "truncated, so assets in the folders it did not reach were not downloaded. Raise " +
        "shio.agent.read.max-limit on the server or scope the run with --site, then pull again. " +
        "Any dangling File reference reported below or by `shio verify` may be this rather than " +
        "a defect in the tree.\n",
    );
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

  const report = {
    written: [],
    deleted: [],
    unchanged: [],
    conflicts: [],
    kept: [],
    folders: { written: [], deleted: [], kept: [], conflicts: [] },
    assets: assets.written,
    // SH373 — asset bytes are in the merge now, so they have the same three outcomes everything
    // else does, and a conflict has to be reported rather than resolved.
    assetConflicts: assets.conflicts ?? [],
    assetsKept: assets.kept ?? [],
    notes: { written: [], deleted: [], conflicts: [] },
  };
  const nextPosts = { ...base.posts };
  // Collected from the documents rather than read back off `nextPosts` (SH148): a base entry is
  // fingerprints now, so it has no `folder` to read, and a post the file tree alone changed
  // never gets a fresh entry. Reading the folder there would miss it and pull would write a
  // redundant `_folder.md` for a folder a post already declares.
  const declaredFolders = new Set();

  const planned = addresses(base.posts, remote, local);
  // SH401 — computed before the first write, because after it one of the two is already gone.
  const collisions = postPathCollisions(planned);
  const sharedPaths = new Set();

  for (const address of planned) {
    const collision = collisions.get(address);
    if (collision) {
      // Neither is written: there is no merging two posts into one file, and picking by
      // whichever the walk reached last is how the loss became silent. Same outcome as an
      // asset conflict, for the same reason — name it and let a human settle it.
      if (!sharedPaths.has(collision.key)) {
        sharedPaths.add(collision.key);
        report.conflicts.push({
          address: collision.addresses[0],
          addresses: collision.addresses,
          paths: collision.paths.map((path) => `${dir}/${path}`),
          fields: [],
        });
      }
      continue;
    }
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
        report.conflicts.push({
          address,
          fields: result.conflicts,
          // SH215 — a collision is a conflict with a different cause, and the remedy is different
          // too: the base cannot describe the value it indexes, so it has to be rebuilt.
          ...(result.collisions ? { collisions: result.collisions } : {}),
        });
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
    sites: siteScope,
  });
  // SH403 — the same filter the posts get one screen up, for the same reason: a chain that
  // navigates cannot become a marker, and one bad row in the sitemap must not take the run down.
  const projectableFolders = [];
  for (const marker of remoteFolders) {
    try {
      folderMarkerPathFor(marker);
      projectableFolders.push(marker);
    } catch (error) {
      skipped.push({ address: `folder:${marker.site}${marker.folder}`, reason: error.message });
    }
  }
  // SH407 — and then the pair that *can* both be built and still cannot both exist. Computed on
  // the projectable set, so a chain skipped above is not also reported as half a collision.
  const markerClashes = markerPathCollisions(
    projectableFolders.map((marker) => `folder:${marker.site}${marker.folder}`),
  );
  const clashed = new Set();
  const writableFolders = projectableFolders.filter((marker) => {
    const collision = markerClashes.get(`folder:${marker.site}${marker.folder}`);
    if (!collision) return true;
    if (!clashed.has(collision.key)) {
      clashed.add(collision.key);
      report.folders.conflicts.push({
        addresses: collision.addresses,
        paths: collision.paths.map((path) => `${foldersDir}/${path}`),
      });
    }
    return false;
  });
  if (skipped.length) {
    // SH173: what could not be read by address, named — the projection is short by exactly
    // these and saying so beats letting a later diff look like a deletion. Printed here rather
    // than at the read (SH403): the folder filter above adds to the same channel, and a notice
    // that ran before half its rows existed reported the posts and hid the folders.
    ctx.stdout.write(
      `shio pull --content: ${skipped.length} row(s) could not be projected and are not in this ` +
        "projection (run `shio verify` for the full report):\n" +
        skipped.slice(0, 5).map((row) => `  ! ${row.address} — ${row.reason}\n`).join(""),
    );
  }

  const folderPlan = reconcileFolders({
    base: base.folders,
    remote: writableFolders,
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
  const noteSites = siteScope;
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
        // SH401 — a refused write leaves the server holding a post the tree does not, which is
        // exactly what this flag exists to stop `push --prune` acting on. SH402 adds `skipped`
        // for the same reason, and it covers SH173's rows too: a post that could not be read is
        // missing from the tree exactly like one that could not be written, and a later `--prune`
        // read that gap as a deletion. Reported since SH173, but only to the screen.
        //
        // SH404 — **inherited only when this run was scoped**. `base.partial ||` made the flag a
        // latch: once set, nothing but deleting the snapshot cleared it, so the push's own advice
        // ("take a complete projection first") could not resolve what the push refused, and the
        // only branch left was `--force` — delete everything this tree does not declare, the
        // opposite of what that message steers toward. A `--site` run still inherits, because it
        // proves nothing about the sites it did not read; an unscoped run already covers every
        // site the tree holds (SH173), which is the scope `--prune` acts on, so its own facts are
        // the whole answer.
        partial: (site ? base.partial : false) || truncated || foldersTruncated
          || sharedPaths.size > 0 || skipped.length > 0,
        posts: nextPosts,
        folders: folderPlan.agreed,
        notes: nextNotes,
        // SH373 — the assets the two sides now agree on. Merged over what the base already held,
        // so a path this run did not look at (another site, a scoped run) keeps its agreement
        // instead of being forgotten into "never agreed".
        assets: { ...(base.assets ?? {}), ...assets.agreed },
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
  if (report.assetConflicts.length) {
    // There is no merging two images, so the only honest outcome is to write nothing and name
    // them. Unlike a field, the remedy cannot be `--theirs` on the value — the operator has to
    // decide which copy survives, and deleting the local file is how they say "the server's".
    ctx.stderr.write(
      `shio pull --content: ${report.assetConflicts.length} asset(s) changed both here and on the ` +
        "server — nothing was written for them:\n" +
        report.assetConflicts.map((path) => `  ! ${path}\n`).join("") +
        "Delete the local file and pull again to take the server's copy, or push yours with " +
        "`shio push --content --force`.\n",
    );
  }
  // SH215 — named before the ordinary conflicts, because it is the one whose cause is not "you
  // both edited this" and whose remedy is not "pick a side": the base's fingerprint for that field
  // hashes the same as the value and cannot be describing it, so the base is what has to be
  // rebuilt. Before this the hash match alone read as *unchanged* and the edit was dropped in
  // silence, which is the only outcome a sync must never have.
  const collided = report.conflicts.filter((conflict) => conflict.collisions?.length);
  if (collided.length) {
    ctx.stderr.write(
      `shio pull --content: ${collided.length} post(s) hit a base-fingerprint collision — two ` +
        "different values hash the same, so this run cannot tell whether the field changed:\n" +
        collided
          .map((conflict) => `  ! ${conflict.address} · ${conflict.collisions.join(", ")}\n`)
          .join("") +
        "Nothing was written for them. Delete shio/.shio/content-base.json and pull again to " +
        "rebuild the base from the server, or settle the field with --theirs / --ours.\n",
    );
  }
  // SH407 — the folder half of SH401's collision, with the same outcome for the same reason: two
  // markers cannot share one file, and picking by whichever the walk wrote last is how the loss
  // became silent. Named separately because the remedy is a folder rename, not a post one.
  if (report.folders.conflicts.length) {
    ctx.stderr.write(
      `shio pull --content: ${report.folders.conflicts.length} pair(s) of folders project to one `
        + "file — no marker was written for them:\n"
        + report.folders.conflicts
            .map((c) => `  ! ${c.addresses.join(" and ")} → ${c.paths.join(", ")}\n`)
            .join("")
        + "Two folder chains differing only in case are one file on Windows and macOS. Rename one "
        + "of them on the server (`folder.move`) and pull again. An empty folder whose marker this "
        + "run could not write is undeclared, so `push --prune` would delete it.\n",
    );
  }
  // SH401 — named before the ordinary conflicts, because neither its cause nor its remedy is
  // "you both edited this": nobody edited anything, two posts simply cannot share one file, and
  // no local edit settles it. The rename has to happen on the server.
  const shared = report.conflicts.filter((conflict) => conflict.paths);
  if (shared.length) {
    ctx.stderr.write(
      `shio pull --content: ${shared.length} pair(s) of posts project to one file — nothing was ` +
        "written for them:\n" +
        shared
          .map((c) => `  ! ${c.addresses.join(" and ")} → ${c.paths.join(", ")}\n`)
          .join("") +
        "Two URLs differing only in case are one file on Windows and macOS. Rename one of the " +
        "posts on the server (`post.move`) and pull again. The snapshot is marked partial, so " +
        "`push --prune` will not delete the one this run could not write.\n",
    );
  }
  if (report.conflicts.length) {
    ctx.stderr.write(
      `shio pull --content: ${report.conflicts.length} post(s) in conflict — nothing was written ` +
        "for them. Edit the file and pull again, or re-run with --theirs (take the server's " +
        "values) or --ours (keep the file's). --show-remote writes the server's copy beside " +
        "each conflicted file as <file>.remote.\n",
    );
    return { ...report, cursor, collisions: collided, exitCode: 1 };
  }
  return { ...report, cursor };
}

/**
 * Downloads the assets this tree does not have yet, and returns the id → path index (SH145).
 *
 * **A path on disk is merged three ways now, not skipped (SH373).** SH145 could skip it because a
 * `File` post's bytes were immutable — an upload always created a new id-keyed post, so the same
 * path was necessarily the same asset. SH210 removed that premise, and a curator swapping an image
 * in the Media Library then produced a change no pull would ever project.
 *
 * Downloading whenever the hashes differ would have been the wrong fix and the reason this waited:
 * it cannot tell a server edit from a local one, so it overwrites the tree's copy whenever both
 * moved. So the bytes join the SH89 merge on the same three values every other field uses — the
 * base's agreed hash, the server's and the tree's — and `reconcileBytes` decides. A conflict is
 * left **on disk**: there is no merging two images, so the honest outcome is to write nothing and
 * name the asset.
 *
 * <h3>What it costs, since the digest is a request</h3>
 *
 * One `GET /staticfile/{id}/digest` per asset **already on disk**. A path the tree does not have
 * needs no comparison — there is nothing to lose — so it is downloaded on the strength of the
 * listing alone, which keeps a first pull exactly as cheap as it was. The number is asserted in
 * `asset-inventory-cost.test.mjs` rather than argued, and it is what a stamped digest on the SH62
 * listing would remove (SH210 deferred that on purpose; it is still the obvious optimisation).
 */
async function pullAssets(ctx, {
  agent, admin, sites = [], assetsDir, dryRun, base = {},
}) {
  // SH406 — the scope arrives resolved. It used to be recomputed here from the *tree* alone, which
  // is empty on a first pull, so the walk covered no sites at all.
  const assets = await readRemoteAssets(agent, admin, { sites });
  const written = [];
  const conflicts = [];
  const kept = [];
  const agreed = {};
  // SH408 — two assets in one folder differing only in case are one file on Windows and macOS,
  // and the byte merge would then compare the survivor against both rows. Neither is written.
  const assetClashes = assetPathCollisions([...assets.byPath.keys()]);
  const assetPairs = new Map();
  for (const [path, item] of assets.byPath) {
    if (assetClashes.has(path)) {
      const group = assetClashes.get(path);
      if (!assetPairs.has(group.key)) assetPairs.set(group.key, group.addresses);
      continue;
    }
    const localBytes = await readAssetOrNull(ctx, path, assetsDir);
    if (localBytes === null) {
      // Nothing on disk: no comparison can lose anything, and no digest request is needed to know
      // it. The agreed hash comes from the bytes that were just downloaded rather than from
      // `/digest` — asking the server to hash what this process is holding doubled a first pull's
      // request count, which the SH213 benchmark caught the moment it was measured.
      if (!dryRun) {
        const bytes = await admin.downloadStaticFile(item.id);
        await writeAssetBytes(ctx, path, bytes, { dir: assetsDir });
        agreed[path] = sha256Of(bytes);
      }
      written.push(path);
      continue;
    }
    const localHash = sha256Of(localBytes);
    // SH398 — the listing carries the digest for anything written since it shipped, so the common
    // case costs no request at all. The endpoint is the fallback for rows stamped before it, which
    // is why it still exists: a stamp covers only what was written after it.
    const remoteHash = item.sha256 ?? (await admin.staticFileDigest(item.id))?.sha256 ?? null;
    const verdict = reconcileBytes({ base: base[path] ?? null, remote: remoteHash, local: localHash });
    if (verdict === "remote-changed" || verdict === "remote-added") {
      if (!dryRun) {
        await writeAssetBytes(ctx, path, await admin.downloadStaticFile(item.id), { dir: assetsDir });
      }
      written.push(path);
      if (remoteHash) agreed[path] = remoteHash;
      continue;
    }
    if (verdict === "conflict") {
      conflicts.push(path);
      // Deliberately not agreed: recording a hash here would make the next pull read the conflict
      // as settled, which is how a sync forgets that it never resolved anything.
      continue;
    }
    if (verdict === "unchanged" && remoteHash) agreed[path] = remoteHash;
    // `local-changed` / `local-added` keep the tree's copy and stay unagreed until a push sends
    // them, which is what makes `push --content` the thing that settles them.
    if (verdict === "local-changed") kept.push(path);
  }
  return { ...assets, written, conflicts, kept, agreed, pairs: [...assetPairs.values()] };
}

/** An asset's bytes, or `null` when the tree does not have that path. */
async function readAssetOrNull(ctx, path, dir) {
  try {
    return await ctx.fs.readFile(join(ctx.cwd, dir, path));
  } catch (error) {
    if (error?.code === "ENOENT") return null;
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
    // SH401 — a collision has no conflicted fields to name; the pair of paths is the finding.
    lines.push(conflict.paths
      ? `  ! ${conflict.addresses.join(" and ")} → one file (${conflict.paths.join(", ")})`
      : `  ! ${conflict.address} (${conflict.fields.join(", ")})`);
  }
  // SH407 — the folder pair reads the same way, in the summary as well as on stderr.
  for (const conflict of folders.conflicts ?? []) {
    lines.push(`  ! ${conflict.addresses.join(" and ")} → one file (${conflict.paths.join(", ")})`);
  }
  if (truncated) {
    lines.push("  (partial projection — the server's post list was truncated)");
  }
  return `${lines.join("\n")}\n`;
}
