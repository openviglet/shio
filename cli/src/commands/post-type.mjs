/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio post-type` (SH768) — list the model this instance holds, and sweep the types nobody meant
 * to keep.
 *
 * <p>`shio pull` on a working instance wrote 22 post-types and two of them were debris:
 * `ShioConfRendermsgmgaup2qjcArticle` and `ShioConfRendermsgmgrcoqdq2Article`, named by the
 * generated run ids of two conformance runs. Ordinary non-system types, sitting in the model beside
 * `Page` and the seven `Section*` types with nothing marking them as scratch.</p>
 *
 * <h2>The cost is paid by every reader, on every cold start</h2>
 *
 * <p>`shio context` is the one call P2 promises replaces a session, and the model is most of its
 * payload. Two invented types with a field named `TEXT` read as <em>this instance has a convention
 * I should follow</em>; deciding they are junk takes a second call and knowing what a conformance
 * run leaves behind, which is knowledge that was nowhere in the pack. So the flag is delivered in
 * the pack (`ShAgentContext.Type#scratch`) and named in `agents.md`'s notes column, and this
 * command is what acts on it.</p>
 *
 * <h2>Why a flag and not a teardown</h2>
 *
 * <p>The suite could delete what it created, and §XVII.109 argues the flag is better and smaller:
 * <b>a run that dies mid-way still leaves its types</b>, so the sweep has to be possible after the
 * fact and not only inside a passing run's `after`. A crashed run is exactly the run that leaves
 * debris, and it is the one a teardown cannot reach.</p>
 *
 * <h2>What keeps it from becoming a fast way to delete a real model</h2>
 *
 * <p>Exactly SH591's argument, and deliberately the same shape so there is one rule to learn:
 * `--scratch` is not a filter the caller can widen, there is no bare `rm --all`, a type without the
 * flag is untouched at any count, and the flag can only be set by the write that <em>created</em>
 * the type — `ShPostTypeAdminService#upsert` ignores it on update. So every type this can remove
 * was declared disposable before anything was written against it. P7 satisfied rather than waived:
 * the declaration preceded the content.</p>
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("post-type");

export async function run(flags, positionals, ctx) {
  const verb = String(positionals[0] ?? "ls").trim().toLowerCase();
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  // Before the listing, and deliberately: `check` reads one type by name, so paying for every
  // type's definition first would make a missing name fail as a connection error.
  if (verb === "check") {
    return check(client, positionals[1], flags, ctx);
  }

  const types = await listTypes(client);
  if (verb === "ls" || verb === "list") {
    return list(types, flags, ctx);
  }
  if (verb !== "rm" && verb !== "delete") {
    throw new Error(
      `shio post-type: "${positionals[0]}" is not a verb this takes.\n`
        + "  allowed: ls, check, rm\n"
        + "  example: shio post-type check Article",
    );
  }
  return remove(client, types, flags, ctx);
}

/**
 * SH1018 — what this type's stored content does not satisfy.
 *
 * <p>Constraints refuse a bad write and say nothing about the rows already there, so a type given a
 * required field has an instance full of content that violates it — found one publish at a time.
 * The same is true one level down and is older: a post's data is keyed by field name, so renaming
 * or removing a field leaves values under a key nothing declares.</p>
 *
 * <p>SH322's rule is that the measurement is the deliverable. A migration argued for by "some posts
 * probably violate this" is one nobody can size, review, or check afterwards — so this prints the
 * number first, and exits non-zero when there is something to act on, which is what lets it sit in
 * a gate.</p>
 */
/** Named rather than nested in the line above: the repo's lint refuses a nested template. */
function unreadableNote(report) {
  return report.unreadable ? `, ${report.unreadable} unreadable` : "";
}

async function check(client, name, flags, ctx) {
  if (!name) {
    throw new Error(
      "shio post-type check: name the type.\n"
        + "  example: shio post-type check Article",
    );
  }
  const report = await client.checkPostType(name);
  if (!report) {
    throw new Error(`shio post-type check: no post-type named ${name}.`);
  }
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
    return report.violating > 0 || report.undeclared.length > 0 ? 1 : 0;
  }

  ctx.stdout.write(
    `${report.postType}: ${report.posts} post(s), ${report.violating} violating`
      // Printed only when there are any, and never omitted when there are: a row whose document
      // will not parse is one the check could not judge, so leaving it out would understate the
      // number this command exists to produce.
      + `${unreadableNote(report)}\n`,
  );
  for (const rule of report.rules) {
    ctx.stdout.write(`  ${rule.posts} × ${rule.field} ${rule.rule} — ${rule.says}\n`);
  }
  for (const orphan of report.undeclared) {
    // Named separately because the fix is different: a violation is content to correct, an
    // undeclared key is a field the model lost — a rename that only happened on one side.
    ctx.stdout.write(
      `  ${orphan.posts} × ${orphan.field} — stored, and this type declares no such field\n`,
    );
  }
  if (!report.rules.length && !report.undeclared.length) {
    ctx.stdout.write("  nothing to reconcile\n");
  }
  return report.violating > 0 || report.undeclared.length > 0 ? 1 : 0;
}

/**
 * The listing, and half of what this task is for is that it says which is which.
 *
 * <p>A reader who cannot tell a fixture from a decision is the whole defect; a sweep that works over
 * a listing that cannot distinguish them would leave them where they started.</p>
 */
function list(types, flags, ctx) {
  const scratch = types.filter((type) => type.scratch);
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ types, scratch: scratch.length }, null, 2)}\n`);
    return { types, scratch: scratch.length };
  }
  ctx.stdout.write(
    `${types.length} post-type(s)`
      + `${scratch.length ? `, ${scratch.length} declared disposable` : ""}\n`,
  );
  for (const type of types) {
    ctx.stdout.write(
      `  ${type.name}`
        + `${type.system ? "  system" : ""}`
        + `${type.managed ? "  managed" : ""}`
        + `${type.scratch ? "  SCRATCH" : ""}\n`,
    );
  }
  if (scratch.length) {
    ctx.stdout.write(`  shio post-type rm --scratch removes the ${scratch.length} marked SCRATCH\n`);
  }
  return { types, scratch: scratch.length };
}

async function remove(client, types, flags, ctx) {
  if (!flags.scratch) {
    throw new Error(
      "shio post-type rm: --scratch is required.\n"
        + "  why: this deletes post-types in bulk, and the only set it will ever act on is the one\n"
        + "       declared disposable by whoever created it. There is no `--all`, and a type\n"
        + "       without the flag is untouched at any count.\n"
        + "  example: shio post-type rm --scratch --dry-run",
    );
  }
  const doomed = types.filter((type) => type.scratch);
  if (!doomed.length) {
    ctx.stdout.write(
      `shio post-type rm: nothing to sweep — none of the ${types.length} type(s) is marked scratch. `
        + "Mark one when you make it: PUT /api/v2/post-type/<name> with \"scratch\": true\n",
    );
    return { deleted: [], planned: [] };
  }

  const names = doomed.map((type) => type.name);
  if (flags.dryRun) {
    ctx.stdout.write(
      `shio post-type rm: would delete ${names.length} scratch post-type(s) — ${names.join(", ")}\n`,
    );
    return { planned: names, deleted: [], exitCode: 0 };
  }

  // One call per type, unlike `site rm`: there is no post-type op on the batch surface, so there is
  // no plan to digest and no token to spend. Deliberately not invented here — a third contract for
  // one sweep is what P10 refuses, and the safety this needs is already carried by the flag rather
  // than by a confirmation.
  const deleted = [];
  const refused = [];
  for (const name of names) {
    try {
      await client.deletePostTypeDef(name);
      deleted.push(name);
    } catch (cause) {
      // A type with posts still in it refuses, and that refusal is the useful half: it means the
      // fixture was not as disposable as its creator thought. Reported per type and never swallowed
      // — a sweep that says "deleted 2" having deleted one is worse than one that failed.
      refused.push(`${name} — ${cause?.message ?? cause}`);
    }
  }

  ctx.stdout.write(
    `shio post-type rm: deleted ${deleted.length} scratch post-type(s)`
      + `${deleted.length ? ` — ${deleted.join(", ")}` : ""}\n`
      + (refused.length
        ? `  ${refused.length} refused:\n${refused.map((line) => `    ${line}`).join("\n")}\n`
        : ""),
  );
  return { deleted, planned: names, refused, ...(refused.length ? { exitCode: 1 } : {}) };
}

/** Every type the admin surface holds, with SH66's flag and SH768's. */
async function listTypes(client) {
  const defs = (await client.getPostTypeDefs()) ?? [];
  return defs
    .filter((def) => def?.name)
    .map((def) => ({
      name: def.name,
      title: def.title ?? "",
      system: Boolean(def.system),
      managed: Boolean(def.managed),
      scratch: Boolean(def.scratch),
    }))
    .sort((a, b) => (a.name < b.name ? -1 : 1));
}
