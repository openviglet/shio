/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio push --content` (SH87) — make the instance match `shio/content/**`.
 *
 * IT RUNS ON THE SAME ENGINE AS EVERYTHING ELSE
 * The files compile to a desired-state document per site and go through
 * `POST /agent/plan` / `POST /agent/apply` (SH82) — law P10 again: no third write path,
 * so the atomicity, the teaching errors and the prune rules are the ones already tested.
 * `--check` is the CI drift guard, the same green light `shio push --check` gives for
 * post-types (SH65) and `shio apply --check` gives for a document (SH129).
 *
 * IT REFUSES TO OVERWRITE A HUMAN
 * Before writing anything it reads what the server holds and reconciles three ways
 * (SH89). A post that changed on both sides is a conflict and the push stops; a post that
 * changed only on the server is declared **as the server has it**, so a push can never
 * silently revert a console edit it did not know about.
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import {
  ASSETS_DIR,
  assetLocationOf,
  assetSizeDiffers,
  fileFields,
  pathsToIds,
  sha256Of,
} from "../content-assets.mjs";
import {
  BASE_FILE,
  CONTENT_DIR,
  FOLDERS_DIR,
  projectRemote,
  readAssetBytes,
  readBase,
  readLocal,
  readLocalFolders,
  readLocalNotes,
  readRemote,
  readRemoteNotes,
  readRemoteAssets,
  sitesOf,
  writeBase,
} from "../content-store.mjs";
import {
  ancestorsOf,
  baseEntryFor,
  detectFolderMoves,
  documentsFor,
  opsOf,
  planPushContent,
  splitDocuments,
} from "../content-sync.mjs";
import { reconcileNotes } from "../content-notes.mjs";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.contentDir ?? CONTENT_DIR;
  const site = flags.site ?? null;
  const prune = Boolean(flags.prune);
  const planOnly = Boolean(flags.check || flags.dryRun);

  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const admin = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const local = await readLocal(ctx, { dir, site });
  // SH149 — a folder marker is a declaration in its own right, so a tree that holds only
  // markers is a legitimate push: it is the "scaffold the structure, then fill it" step,
  // which was the one step that had to leave the file tree and go through the API.
  const markers = await readLocalFolders(ctx, { dir: flags.foldersDir ?? FOLDERS_DIR, site });
  if (!Object.keys(local).length && !markers.length) {
    // Silence here would be dangerous with --prune: an empty document plus a prune is a
    // request to delete a section, and an empty *directory* is far more often a wrong
    // --content-dir than a deliberate wipe.
    throw new Error(
      `shio push --content: no content files under ${dir}. Run \`shio pull --content\` first, ` +
        "or point at the right tree with --content-dir.",
    );
  }

  const base = await readBase(ctx, flags.baseFile ?? BASE_FILE);
  if (prune && base.partial && !flags.force) {
    throw new Error(
      "shio push --content --prune: the base snapshot is marked partial — it was taken from a " +
        "truncated read, so posts exist on the server that this tree never saw and prune would " +
        "delete them. Take a complete projection first (`shio pull --content`), or pass --force " +
        "if you really mean to delete everything this tree does not declare.",
    );
  }

  const { docs: rawRemote, truncated, skipped } = await readRemote(client, {
    site,
    // SH173: the tree names the sites this push is about, so the server read is scoped to
    // them. Before this, one un-addressable post in an unrelated site aborted the CI gate.
    sites: sitesOf(local, markers),
    limit: Number(flags.limit ?? 200),
  });
  // SH253 — the same projection the pull applies, because this command answers two questions
  // against the server's copy and both are wrong in the server's domain: whether a post changed
  // on both sides (a body whose only difference is CRLF is not a conflict), and what to record
  // as agreed for a post this tree does not declare. Recording the unprojected form there is how
  // a clean `push` left the *next* pull reporting a change nobody made.
  const defs = await admin.getPostTypeDefs();
  const remote = projectRemote(rawRemote, defs);
  if (skipped.length) {
    // A finding, not a stack trace: it names what could not be read and what to do, and it
    // does not stop a push over content that read fine.
    ctx.stdout.write(
      `shio push --content: ${skipped.length} post(s) on the server could not be read by ` +
        "address and were left out of the merge (run `shio verify` for the full report):\n" +
        skipped.slice(0, 5).map((row) => `  ! ${row.address} — ${row.reason}\n`).join(""),
    );
  }
  if (prune && truncated && !flags.force) {
    throw new Error(
      "shio push --content --prune: the server's post list came back truncated, so this run " +
        "cannot tell an undeclared post from an unseen one. Narrow it with --site, raise " +
        "shio.agent.read.max-limit, or drop --prune.",
    );
  }

  const { posts, conflicts, behind, removed, moves, ambiguousMoves, probableMoves } =
    planPushContent({ base: base.posts, remote, local }, { force: Boolean(flags.force) });
  if (ambiguousMoves.length) {
    // A refusal, not a guess (SH150). Either candidate could be the rename and the other the
    // copy, and moving the wrong one takes the wrong post's id and history with it.
    throw new Error(
      `shio push --content: ${ambiguousMoves.length} post(s) could be a rename of more than one ` +
        "post, and moving the wrong one would take the wrong post's history:\n" +
        ambiguousMoves
          .map((row) => `  ! ${row.to} — ${row.reason}\n      candidates: ${row.candidates.join(", ")}\n`)
          .join("") +
        "Say which is which with `movedFrom: <the old url>` in the frontmatter of the file that " +
        "is the rename.",
    );
  }
  for (const row of probableMoves) {
    // Not provable, and that is exactly why it is said out loud: what happens otherwise is a
    // delete plus a create, which destroys the post's history without mentioning it.
    ctx.stdout.write(
      `shio push --content: ${row.to} looks like it could be ${row.from} renamed, but its ` +
        "content differs so this push cannot prove it. As written, this deletes one post and " +
        `creates another — the id, audit trail and version history do not survive.\n` +
        `  If it is a rename, add \`movedFrom: ${row.from.slice(row.from.indexOf("/"))}\` to the file.\n`,
    );
  }
  // SH211 — the folder half, and the more dangerous one: a folder deleted by --prune takes every
  // post beneath it, where a deleted post loses only its own history. Declared folders are the
  // markers plus every ancestor a projected post names, because a folder a post lives in is
  // declared whether or not it has a marker of its own.
  const declaredFolders = new Set();
  for (const marker of markers) {
    for (const ancestor of ancestorsOf(marker.folder ?? "/")) {
      declaredFolders.add(`${marker.site}|${ancestor}`);
    }
  }
  for (const post of posts) {
    for (const ancestor of ancestorsOf(post.folder ?? "/")) {
      declaredFolders.add(`${post.site}|${ancestor}`);
    }
  }
  const folderPlan = detectFolderMoves({
    base: base.folders,
    local: markers,
    declared: declaredFolders,
  });
  if (folderPlan.ambiguous.length) {
    const listed = folderPlan.ambiguous
      .map((row) => `  ! ${row.site}${row.from ?? row.to ?? ""} — ${row.reason}\n`
        + `      candidates: ${row.candidates.join(", ")}\n`)
      .join("");
    const remedy = "Say which is which with `movedFrom: <the old folder path>` in the "
      + "`_folder.md` of the directory that is the rename.\n";
    // The **contradiction** is always a refusal: a marker naming a folder the tree still
    // declares is a tree that cannot be read at all. Everything else is only dangerous when
    // something is about to be deleted, which is exactly `--prune` — without it an unpaired
    // folder is left alone, and refusing a push that destroys nothing would block the
    // legitimate case (one section removed, two added) with no way to say "neither is a rename".
    const contradictory = folderPlan.ambiguous.filter((row) => row.contradictory);
    if (contradictory.length || (prune && !flags.force)) {
      throw new Error(
        `shio push --content: ${folderPlan.ambiguous.length} folder rename(s) cannot be told ` +
          "apart, and moving the wrong section would move the wrong pages:\n" + listed + remedy +
          (contradictory.length
            ? ""
            : "If none of them is a rename, pass --force — with --prune the folders that left " +
              "this tree are deleted, and a folder delete takes every post beneath it.\n"),
      );
    }
    ctx.stdout.write(
      `shio push --content: ${folderPlan.ambiguous.length} folder(s) could be a rename and ` +
        "cannot be proved from the tree, so nothing is being moved:\n" + listed + remedy,
    );
  }
  for (const move of folderPlan.moves) {
    ctx.stdout.write(
      `shio push --content: ${move.site}${move.from} moves to ${move.to}`
        + `${move.explicit ? "" : " (inferred from the tree)"} — the pages inside keep their URLs\n`,
    );
  }

  if (conflicts.length) {
    for (const conflict of conflicts) {
      ctx.stdout.write(`  ! ${conflict.address} (${conflict.fields.join(", ")})\n`);
    }
    throw new Error(
      `shio push --content: ${conflicts.length} post(s) changed both here and on the server — ` +
        "pushing would overwrite someone. Run `shio pull --content` to merge (it resolves " +
        "everything but the fields that truly collide), then push again.",
    );
  }

  // SH145 — assets become ids only here, at the last moment before the wire. Everything
  // upstream of this line speaks paths, which is what keeps a File field from looking changed
  // on every sync.
  const declared = await resolveAssets(ctx, {
    client,
    admin,
    // The defs the SH253 projection already fetched — this used to read them a second time.
    defs,
    posts,
    sites: sitesOf(local, markers),
    assetsDir: flags.assetsDir ?? ASSETS_DIR,
    planOnly,
    stdout: ctx.stdout,
  });

  const maxOps = Number(flags.maxOps ?? 1500);
  const whole = documentsFor(declared.posts, {
    prune,
    markers,
    moves,
    folderMoves: folderPlan.moves,
    sites: sitesOf(local, markers),
  });
  // SH199 — a tree past the server's document ceiling is several documents, not a dead end.
  // A *pruning* push is the exception and stays whole: see `splitDocuments` for why every
  // candidate split changes what prune deletes.
  const oversized = whole.filter((document) => prune && opsOf(document) > maxOps);
  if (oversized.length) {
    throw new Error(
      `shio push --content --prune: ${oversized[0].site} compiles to ${opsOf(oversized[0])} ops, ` +
        `past the ${maxOps}-op ceiling, and a pruning push cannot be split — prune deletes what ` +
        "a document does not declare, so each part of a split would delete what the other parts " +
        "declare. Narrow the run with --site, push the sections separately with --content-dir, " +
        "or drop --prune (a plain push splits automatically).",
    );
  }
  const documents = splitDocuments(whole, { maxOps });
  const results = [];
  for (const document of documents) {
    results.push(
      planOnly
        ? await client.plan(document)
        : await client.applyConfirmed(document, {}, (plan) => {
            // SH108: a `--prune` push deletes, so the deletions are printed before they
            // happen. This is the "rendered for the human" half of the confirm token, and
            // in CI it is the record of what the gate removed.
            const rows = (plan.results ?? []).filter((row) => row.action === "delete");
            ctx.stdout.write(
              `shio push --content: deleting ${rows.length} object(s) (confirm ${plan.confirm}):\n` +
                rows.map((row) => `  - ${row.address}\n`).join(""),
            );
          }),
    );
  }

  // SH161 — the site memory, applied after the content and outside the document.
  //
  // Deliberately *not* compiled into the desired-state document, and this is the load-bearing
  // part: a note is not content. It is read on the hot path, never delivered, and it must
  // survive an SH82 `prune` — folding it into the document would make it exactly the thing
  // prune deletes when a tree stops mentioning it. So notes travel as their own idempotent
  // upserts, and `--check` reports them without writing, the way a plan does.
  const notes = await pushNotes(client, ctx, {
    base: base.notes ?? {},
    sites: sitesOf(local, markers),
    dir,
    planOnly,
  });

  const changed = results.reduce((total, result) => total + (result.changed ?? 0), 0);
  const ops = results.reduce((total, result) => total + (result.ops ?? 0), 0);
  ctx.stdout.write(
    render({ documents, results, changed, ops, behind, removed, moves, planOnly, prune }),
  );
  if (notes.written.length || notes.forgotten.length) {
    ctx.stdout.write(
      `  memory: ${notes.written.length} note(s) ${planOnly ? "would be written" : "written"}` +
        (notes.forgotten.length
          ? `, ${notes.forgotten.length} ${planOnly ? "would be forgotten" : "forgotten"}`
          : "") +
        "\n",
    );
  }

  if (flags.check) {
    // A note that differs is drift too. Reporting `in sync` while the file says one thing and
    // the instance another would make the gate quietly weaker than the thing it guards.
    const noteDrift = notes.written.length + notes.forgotten.length;
    if (changed === 0 && noteDrift === 0) {
      ctx.stdout.write("shio push --content --check: in sync — the file tree matches the server.\n");
      return { changed, ops, check: true, drift: false, exitCode: 0 };
    }
    ctx.stderr.write(
      `shio push --content --check: drift detected — ${changed} change(s) pending (nothing applied).\n`,
    );
    return { changed, ops, check: true, drift: true, exitCode: 1 };
  }

  if (!planOnly) {
    // The snapshot moves to what was just declared: that is now what both sides agree on,
    // and it is what the next pull merges against.
    const nextPosts = {};
    for (const [address, doc] of Object.entries(local)) {
      nextPosts[address] = baseEntryFor(doc);
    }
    // A moved post's *old* address must not come back here (SH150). Without this exclusion the
    // loop below re-adds it from the server read — which was taken before the move — and the
    // next run sees a post the tree does not declare and offers to prune the very post that
    // was just preserved.
    const vacated = new Set(moves.map((move) => `post:${move.site}${move.from}`));
    for (const [address, doc] of Object.entries(remote)) {
      if (!nextPosts[address] && !prune && !vacated.has(address)) nextPosts[address] = doc;
    }
    await writeBase(
      ctx,
      {
        cursor: base.cursor,
        partial: base.partial || truncated,
        posts: nextPosts,
        folders: base.folders,
        notes: notes.agreed,
      },
      flags.baseFile ?? BASE_FILE,
    );
  }
  return { changed, ops, documents, behind, removed, moves, notes };
}

/**
 * Turns every declared `File` path back into an id, uploading the ones the server lacks (SH145).
 *
 * <h3>Idempotent by path, resolved against the server and not a memory</h3>
 *
 * A second push must not upload a second copy, and the resolution is against the instance's own
 * media inventory keyed by (site, folder, file name) — not against a locally remembered id — so
 * a fresh clone with no base snapshot resolves exactly as a long-lived one does. That is SH81's
 * `post.upsert` argument applied to bytes.
 *
 * <h3>Only referenced assets are uploaded</h3>
 *
 * An asset sitting in the tree that no `File` field names is never sent. It keeps the rule
 * simple in both directions: a push carries what the content refers to, so there is no question
 * about what an unreferenced file means and no way for a stale one to resurrect itself.
 *
 * <h3>An asset already on the server is compared, not skipped (SH210)</h3>
 *
 * Idempotence by path used to mean *skip*, which answered the wrong question: it is right that a
 * second push must not upload a second copy, and it is wrong that **editing the bytes at a path
 * the server already has did nothing and reported success** — the projection's one silent no-op,
 * on the most ordinary thing anyone does to an asset. A path in the inventory now has its bytes
 * compared and, if they differ, replaced in place through `POST /staticfile/{id}/replace`.
 *
 * Replaced, not re-uploaded: uploading and repointing the field would put two `File` posts at one
 * (folder, file name) pair, and that pair *is* the asset's address, so the path would stop naming
 * one asset. It would also strand the old post, which SH62's usage list may show as referenced by
 * content this tree does not own.
 *
 * <h3>Two refusals</h3>
 *
 * A declared path whose **bytes are not on disk** stops the push: the alternative is a post
 * published with a File field pointing at nothing. And an upload needs its folder, so a missing
 * one is created with the same `folder.upsert` the document would have used — not guessed at,
 * and never silently redirected to the site root, which is how an asset ends up somewhere
 * nobody looks.
 */
/**
 * Converges each site's memory notes (SH161).
 *
 * <h3>Why a note is not part of the document</h3>
 *
 * A desired-state document is content, and `--prune` deletes what one does not declare. A
 * note is deliberately not content — it is read on the hot path, never delivered, and SH114
 * made it a table precisely so it survives a prune. Compiling it into the document would
 * hand prune the power to erase the instance's memory the first time somebody pushed a tree
 * that had not projected it yet.
 *
 * <h3>Why absence is not deletion</h3>
 *
 * No `.memory.yaml` for a site means this tree has never projected that site's memory, which
 * is the state of every project that predates SH161 and every fresh clone before its first
 * pull. Reading that as "forget everything" would destroy the memory of a site on the first
 * push from a checkout. A note is only forgotten when it was in the **base** — the two sides
 * agreed on it once — and the file has since dropped it. That is a deletion somebody made.
 */
async function pushNotes(client, ctx, { base, sites, dir, planOnly }) {
  const written = [];
  const forgotten = [];
  const agreed = { ...base };

  for (const site of sites) {
    const local = await readLocalNotes(ctx, site, { dir });
    if (local === null) {
      // Never projected here. Leave the server's memory exactly as it is.
      continue;
    }
    const remote = await readRemoteNotes(client, site);
    const plan = reconcileNotes({ base: base[site] ?? {}, remote, local });

    for (const entry of local) {
      const current = remote.find((row) => row.key === entry.key);
      if (current && current.note === entry.note) continue;
      if (plan.conflicts.some((conflict) => conflict.key === entry.key)) continue;
      if (!planOnly) await client.remember({ scope: site, key: entry.key, note: entry.note });
      written.push(`${site}:${entry.key}`);
    }
    for (const key of plan.forgetRemote) {
      if (!planOnly) await client.forget({ scope: site, key });
      forgotten.push(`${site}:${key}`);
    }
    if (plan.conflicts.length) {
      ctx.stdout.write(
        `shio push --content: ${plan.conflicts.length} memory note(s) for ${site} changed on ` +
          "both sides and were left alone — run `shio pull --content` to see them.\n",
      );
    }
    agreed[site] = Object.fromEntries(local.map((entry) => [entry.key, entry.note]));
  }
  return { written, forgotten, agreed };
}

/**
 * The local bytes when they differ from the server's, or `null` when nothing needs replacing
 * (SH210).
 *
 * <h3>No bytes on disk is not a refusal here</h3>
 *
 * The missing-asset refusal exists so a push cannot publish a post whose `File` field points at
 * nothing — and a path the server already holds points at something. A tree that names an
 * existing asset it never pulled is legitimate (nothing obliges a hand-written document to carry
 * bytes it does not change), so `ENOENT` on this branch means "unchanged", not "broken". Reading
 * it as a refusal would turn every such tree's next push into an error, which is a worse defect
 * than the one being fixed.
 */
async function replacementFor(ctx, { admin, path, item, assetsDir }) {
  let bytes;
  try {
    bytes = await readAssetBytes(ctx, path, { dir: assetsDir });
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
    return null;
  }
  if (assetSizeDiffers(bytes, item?.size) === true) return bytes;
  // Same length, so only content can decide — and the server hashes its own copy rather than
  // shipping it here, because the length matching is the *common* case and downloading on it
  // would mean fetching every referenced asset on every push.
  const remote = await admin.staticFileDigest(item.id);
  // A server that cannot say what it holds gets the tree's copy: the tree is the declared state,
  // and the outcome this task exists to remove is the one where nothing happens and it looks fine.
  if (!remote?.sha256) return bytes;
  return remote.sha256 === sha256Of(bytes) ? null : bytes;
}

async function resolveAssets(ctx, { client, admin, defs, posts, sites, assetsDir, planOnly, stdout }) {
  const fileFieldMap = fileFields(defs);
  const needed = new Set();
  for (const post of posts) {
    for (const name of fileFieldMap.get(post.type) ?? []) {
      const value = post.data?.[name];
      // A value that is not a path is an id already — a reference this tree never projected,
      // which stays exactly as it is (SH145's read side leaves those alone too).
      if (typeof value === "string" && value && assetLocationOf(value)) needed.add(value);
    }
  }
  if (!needed.size) return { posts, uploaded: [], replaced: [] };

  const { byPath, folderIds } = await readRemoteAssets(client, admin, { sites });
  const uploaded = [];
  const replaced = [];
  const missing = [];

  for (const path of [...needed].sort((a, b) => a.localeCompare(b))) {
    const known = byPath.get(path);
    if (known) {
      const change = await replacementFor(ctx, { admin, path, item: known, assetsDir });
      if (change) {
        if (planOnly) {
          replaced.push(`${path} (would replace)`);
        } else {
          await admin.replaceStaticFile({
            id: known.id,
            fileName: known.fileName,
            bytes: change,
            contentType: contentTypeFor(known.fileName),
          });
          replaced.push(path);
        }
      }
      continue;
    }
    const at = assetLocationOf(path);
    let bytes;
    try {
      bytes = await readAssetBytes(ctx, path, { dir: assetsDir });
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
      missing.push(path);
      continue;
    }
    if (planOnly) {
      uploaded.push(`${path} (would upload)`);
      // Planned, not performed — but the path still has to resolve to *something* or the
      // document below would carry a path where the server expects an id. A plan writes
      // nothing, so leaving it unresolved is honest and the row says so.
      continue;
    }
    let folderId = folderIds.get(`${at.site}|${at.folder}`);
    if (!folderId) {
      await client.apply({
        site: at.site,
        folders: [{ path: at.folder }],
        posts: [],
      });
      const refreshed = await readRemoteAssets(client, admin, { sites });
      folderId = refreshed.folderIds.get(`${at.site}|${at.folder}`);
    }
    if (!folderId) {
      missing.push(`${path} (its folder ${at.folder} could not be created)`);
      continue;
    }
    const created = await admin.uploadStaticFile({
      folderId,
      fileName: at.fileName,
      bytes,
      contentType: contentTypeFor(at.fileName),
    });
    byPath.set(path, { id: created.id, fileName: at.fileName });
    uploaded.push(path);
  }

  if (missing.length) {
    throw new Error(
      `shio push --content: ${missing.length} File reference(s) name an asset that is not in ` +
        `${assetsDir}:\n${missing.map((path) => `  ! ${path}\n`).join("")}` +
        "Pushing would publish a post whose File field points at nothing. Run `shio pull " +
        "--content` to fetch the assets, or fix the reference in the file.\n" +
        // SH212 — the same defect is a finding, in the gate that needs no instance. This refusal
        // stays as the last net, but reaching it means the cheap check was never run.
        "`shio verify --checks projection` finds all of them at once, with no server.",
    );
  }

  const mapped = [];
  for (const post of posts) {
    mapped.push(pathsToIds(post, fileFieldMap, byPath).doc);
  }
  if (uploaded.length) {
    stdout.write(
      `shio push --content: ${uploaded.length} asset(s)${planOnly ? " to upload" : " uploaded"}:\n` +
        uploaded.map((path) => `  + ${path}\n`).join(""),
    );
  }
  if (replaced.length) {
    stdout.write(
      `shio push --content: ${replaced.length} asset(s)${planOnly ? " to replace" : " replaced"}:\n` +
        replaced.map((path) => `  ~ ${path}\n`).join(""),
    );
  }
  return { posts: mapped, uploaded, replaced };
}

/** Enough of a content type for the server to record; it re-sniffs images itself. */
function contentTypeFor(fileName) {
  const ext = String(fileName).toLowerCase().split(".").pop();
  return {
    png: "image/png",
    jpg: "image/jpeg",
    jpeg: "image/jpeg",
    gif: "image/gif",
    webp: "image/webp",
    svg: "image/svg+xml",
    pdf: "application/pdf",
    json: "application/json",
    txt: "text/plain",
  }[ext] ?? "application/octet-stream";
}

function render({ documents, results, changed, ops, behind, removed, moves = [], planOnly, prune }) {
  const verb = planOnly ? "plan" : "apply";
  const lines = [
    `shio push --content: ${verb} over ${documents.length} site(s) — ${changed} change(s) of ` +
      `${ops} op(s)${planOnly ? " — nothing written" : ""}`,
  ];
  for (const move of moves) {
    // Said before the server's own rows, because "this was a move" is the fact that explains
    // why there is no delete in the list below — and a reader who does not see it will look
    // for one (SH150).
    const what = move.to
      ? `post:${move.site}${move.from} → ${move.to}${move.folder ? ` in ${move.folder}` : ""}`
      : `post:${move.site}${move.from} → folder ${move.folder}`;
    lines.push(`  move ${what}${move.explicit ? "" : " (matched by content)"}`);
  }
  for (const result of results) {
    for (const change of result.results ?? []) {
      if (change.action === "noop") continue;
      lines.push(`  ${change.action} ${change.address}${change.detail ? ` (${change.detail})` : ""}`);
    }
  }
  for (const address of behind) {
    // Not an error: the push declared the server's own values for these, so the edit
    // survives. Saying so is what stops the next pull from looking like a surprise.
    lines.push(`  = ${address} (server is ahead — left as it is; run \`shio pull --content\`)`);
  }
  if (!prune && removed.length) {
    lines.push(
      `  (${removed.length} post(s) exist on the server but not in the file tree — ` +
        "add --prune to delete them)",
    );
  }
  return `${lines.join("\n")}\n`;
}
