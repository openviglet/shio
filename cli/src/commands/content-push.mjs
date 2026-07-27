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
import { BASE_FILE, CONTENT_DIR, readBase, readLocal, readRemote, writeBase } from "../content-store.mjs";
import { documentsFor, planPushContent } from "../content-sync.mjs";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.contentDir ?? CONTENT_DIR;
  const site = flags.site ?? null;
  const prune = Boolean(flags.prune);
  const planOnly = Boolean(flags.check || flags.dryRun);

  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const local = await readLocal(ctx, { dir, site });
  if (!Object.keys(local).length) {
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

  const { docs: remote, truncated } = await readRemote(client, {
    site,
    limit: Number(flags.limit ?? 200),
  });
  if (prune && truncated && !flags.force) {
    throw new Error(
      "shio push --content --prune: the server's post list came back truncated, so this run " +
        "cannot tell an undeclared post from an unseen one. Narrow it with --site, raise " +
        "shio.agent.read.max-limit, or drop --prune.",
    );
  }

  const { posts, conflicts, behind, removed } = planPushContent(
    { base: base.posts, remote, local },
    { force: Boolean(flags.force) },
  );
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

  const documents = documentsFor(posts, { prune });
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
  ctx.stdout.write(render({ documents, results, changed, ops, behind, removed, planOnly, prune }));

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
    for (const [address, doc] of Object.entries(remote)) {
      if (!nextPosts[address] && !prune) nextPosts[address] = doc;
    }
    await writeBase(
      ctx,
      { cursor: base.cursor, partial: base.partial || truncated, posts: nextPosts },
      flags.baseFile ?? BASE_FILE,
    );
  }
  return { changed, ops, documents, behind, removed };
}

function render({ documents, results, changed, ops, behind, removed, planOnly, prune }) {
  const verb = planOnly ? "plan" : "apply";
  const lines = [
    `shio push --content: ${verb} over ${documents.length} site(s) — ${changed} change(s) of ` +
      `${ops} op(s)${planOnly ? " — nothing written" : ""}`,
  ];
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
