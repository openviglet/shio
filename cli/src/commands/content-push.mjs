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
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import {
  BASE_FILE,
  CONTENT_DIR,
  FOLDERS_DIR,
  readBase,
  readLocal,
  readLocalFolders,
  readRemote,
  sitesOf,
  writeBase,
} from "../content-store.mjs";
import { documentsFor, planPushContent } from "../content-sync.mjs";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.contentDir ?? CONTENT_DIR;
  const site = flags.site ?? null;
  const prune = Boolean(flags.prune);
  const planOnly = Boolean(flags.check || flags.dryRun);

  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
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

  const { docs: remote, truncated, skipped } = await readRemote(client, {
    site,
    // SH173: the tree names the sites this push is about, so the server read is scoped to
    // them. Before this, one un-addressable post in an unrelated site aborted the CI gate.
    sites: sitesOf(local, markers),
    limit: Number(flags.limit ?? 200),
  });
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

  const documents = documentsFor(posts, {
    prune,
    markers,
    moves,
    sites: sitesOf(local, markers),
  });
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

  const changed = results.reduce((total, result) => total + (result.changed ?? 0), 0);
  const ops = results.reduce((total, result) => total + (result.ops ?? 0), 0);
  ctx.stdout.write(
    render({ documents, results, changed, ops, behind, removed, moves, planOnly, prune }),
  );

  if (flags.check) {
    if (changed === 0) {
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
      nextPosts[address] = {
        site: doc.site,
        url: doc.url,
        type: doc.type ?? null,
        folder: doc.folder ?? null,
        data: doc.data ?? {},
      };
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
      },
      flags.baseFile ?? BASE_FILE,
    );
  }
  return { changed, ops, documents, behind, removed, moves };
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
