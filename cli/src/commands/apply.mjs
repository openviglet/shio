/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio apply <file>` (SH129) — plan or apply a desired-state content document.
 *
 * Why this exists, and why it is not the final shape. Design law P1 sets the order
 * MCP → files/CLI → REST, and SH82's content verbs landed at MCP and REST but not
 * here, so a JS developer with a content document had to reach for `curl` — which
 * also meant losing the SH80 teaching errors to a wall of raw JSON. This closes that
 * gap honestly rather than pretending the order held.
 *
 * It is still a command over a JSON blob, and a JSON blob is a worse authoring
 * surface than a directory of files. SH87's `shio push --content` over
 * `shio/content/**` is the ergonomic form, and it will run on this same plan/apply
 * engine when SH86's projection format lands. Until then this is the verb, and
 * `--check` already makes it usable as a CI drift guard — the same green light
 * `shio push --check` gives for post-types (SH65), now over content.
 *
 * `--blueprint` (SH93) shares this verb rather than getting one of its own, for the same
 * reason the MCP tool does: "converge this site to a document I wrote" and "converge it to
 * a package somebody else wrote" are one decision with two inputs. It is also the only
 * caller that can write a package's front-end files — those belong in the project, and the
 * server has no business writing into a working tree it cannot see.
 */
import { join } from "node:path";

import { ShioAgentClient } from "../agent-client.mjs";
// SH447 — one parser for `--param`/`--params`, shared with `init`, which had neither.
import { coerceParams, readParams } from "../blueprint-params.mjs";
import { resolveAdmin } from "../context.mjs";
import { writeProjectFiles } from "../project-files.mjs";
import { retellUnknownSite } from "../unknown-site.mjs";

/**
 * What `shio apply --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio apply [file] — plan or apply a desired-state content document, or a blueprint

Applying the same document twice changes nothing the second time, so it survives a
compaction. - reads the document from stdin.

  --file <file>          The document, when no positional is given
  --dry-run              Print the plan, write nothing
  --check                Drift guard — plan, and exit non-zero if anything would change
  --ids                  Put object ids on the change rows (off by default)
  --blueprint <name>     Apply a ready-made package instead of a document
  --site <site>          With --blueprint: the site to apply it into (required)
  --param k=v            With --blueprint: one parameter value; repeat for several
  --params <file.json>   With --blueprint: values from a file (--param wins over it)
  --create-site          Create the site (blank) if it does not exist, as the first step
  --scratch              With --create-site: declare the site DISPOSABLE, so
                         shio site rm --scratch sweeps it in one confirmation
  --merge-post-types     With --blueprint: add fields an existing post-type lacks; never
                         changes one
  --force                With --blueprint: overwrite existing front-end files

Examples:
  shio apply site.json --dry-run
  echo '{"site":"Blog","folders":[{"path":"/blog"}]}' | shio apply -
  shio apply --blueprint marketing-site --site Acme --param brand=Acme --create-site
`;

export async function run(flags, positionals, ctx) {
  if (flags.blueprint) {
    return applyBlueprint(flags, positionals, ctx);
  }
  const source = positionals[0] ?? flags.file;
  const document = await readDocument(source, ctx);
  // SH439 — the same flag the blueprint half has always had, on the document half too, so
  // `--create-site` means one thing in this command and in `shio convert`. The flag wins over the
  // document's own key rather than merging: a document that already declares it is unchanged, and
  // a caller who typed the flag asked for it here.
  if (flags.createSite) document.createSite = true;
  // SH591 — and only with it. `--scratch` on a document that creates nothing would be a caller
  // asking to mark somebody else's site disposable, which is the one thing the flag must not do.
  if (flags.scratch && document.createSite) document.scratch = true;
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  // `--check` is a plan by definition: a drift guard that could write would not be a
  // guard. `--dry-run` is the same call under the name the other verbs use.
  const planOnly = Boolean(flags.check || flags.dryRun);
  const result = await (planOnly
    ? client.plan(document, { ids: Boolean(flags.ids) })
    : client.applyConfirmed(document, { ids: Boolean(flags.ids) },
        (plan) => ctx.stdout.write(renderDeletions(plan)))
  ).catch((error) => {
    throw retellUnknownSite(error, { command: "apply", site: document?.site ?? "<name>" });
  });

  ctx.stdout.write(render(result, planOnly));

  if (flags.check && result.changed > 0) {
    // Non-zero on drift, like `push --check`. The plan is already on stdout, so CI
    // shows *what* drifted rather than only that something did.
    ctx.stderr.write(
      `shio apply: ${result.changed} change(s) pending — the instance does not match ` +
        `${source ?? "the document"}.\n`,
    );
    return { ...result, exitCode: 1 };
  }
  return result;
}

/**
 * The deletions an apply is about to make (SH108).
 *
 * Printed before the apply, not after: the confirm token's whole promise is that a human saw
 * this list, and a list printed afterwards would be a receipt rather than a review.
 */
function renderDeletions(plan) {
  const rows = (plan.results ?? []).filter((row) => row.action === "delete");
  const lines = rows.map((row) => `  - ${row.address}\n`).join("");
  return `shio apply: deleting ${rows.length} object(s) (confirm ${plan.confirm}):\n${lines}`;
}

/**
 * Reads the document from a path, or from stdin when the path is absent or `-`.
 *
 * Stdin matters more than it looks: it is what lets a document be generated by the
 * step before it (`... | shio apply -`) instead of having to exist on disk first.
 */
async function readDocument(source, ctx) {
  const raw = !source || source === "-" ? await readStdin(ctx) : await readFile(source, ctx);
  if (!raw || !raw.trim()) {
    throw new Error(
      "shio apply: the document is empty. Pass a JSON file, or pipe one in. " +
        'Example: shio apply site.json  |  echo \'{"site":"Blog"}\' | shio apply -',
    );
  }
  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(
      `shio apply: ${source ?? "stdin"} is not valid JSON (${error.message}). ` +
        'A document looks like: {"site":"Blog","folders":[{"path":"/blog"}],' +
        '"posts":[{"url":"/blog/hello","type":"Article","folder":"/blog",' +
        '"data":{"headline":"Hello"},"publish":true}]}',
    );
  }
}

async function readFile(source, ctx) {
  try {
    return await ctx.fs.readFile(join(ctx.cwd, source), "utf8");
  } catch (error) {
    if (error?.code === "ENOENT") {
      throw new Error(`shio apply: no such file "${source}".`);
    }
    throw error;
  }
}

async function readStdin(ctx) {
  if (!ctx.stdin || typeof ctx.stdin[Symbol.asyncIterator] !== "function") return "";
  let raw = "";
  for await (const chunk of ctx.stdin) raw += chunk;
  return raw;
}

/**
 * One line per change plus a summary, in the same terse register as the MCP tools'
 * output — a caller reading this in a terminal and a caller reading it in a model
 * context want the same thing, which is the fewest tokens that still say what moved.
 * No-ops are omitted from the listing but still counted, because on a converged
 * document they are every line.
 */
function render(result, planOnly) {
  const verb = planOnly ? "plan" : "apply";
  const lines = (result.results ?? [])
    .filter((change) => change.action !== "noop")
    .flatMap((change) => {
      const detail = change.detail ? ` (${change.detail})` : "";
      const id = change.id ? ` ${change.id}` : "";
      return [`  ${change.action} ${change.address}${id}${detail}`, ...broken(change)];
    });
  const header =
    `shio ${verb}: ${result.changed} change(s) of ${result.ops} op(s)` +
    (planOnly ? " — nothing written" : "");
  return lines.length ? `${header}\n${lines.join("\n")}\n` : `${header}\n`;
}

/**
 * The pages a move broke (SH140), indented under the move that broke them.
 *
 * A row nobody prints is a row that does not exist: the whole point of reporting inbound links
 * on the write is that the caller learns without asking, and a caller in a terminal is a caller.
 * Absent on every other row and on a clean move, so a converged apply is unchanged.
 */
function broken(change) {
  const inbound = change.inboundLinks;
  if (!inbound?.addresses?.length) return [];
  const more = inbound.found > inbound.addresses.length ? ` of ${inbound.found}` : "";
  return [
    `    links to ${inbound.from} still in ${inbound.addresses.length}${more} page(s):`,
    ...inbound.addresses.map((address) => `      ${address}`),
  ];
}

/* -------------------------------------------------------------------------- *
 * Blueprints (SH93)                                                          *
 * -------------------------------------------------------------------------- */

/**
 * Plans or applies a blueprint into a site.
 *
 * The parameter values are coerced against the package's **declared** schema before
 * being sent, because a terminal has only strings: `--param columns=3` has to arrive as
 * the number 3 for a package that declared an integer, and guessing from the shape of the
 * text ("looks numeric, send a number") would silently disagree with the package for a
 * string parameter whose value happens to be digits — a version, a SKU, a postcode.
 */
async function applyBlueprint(flags, positionals, ctx) {
  const name = typeof flags.blueprint === "string" ? flags.blueprint : positionals[0];
  if (!name) {
    throw new Error(
      "shio apply: --blueprint needs a name. List them with `shio blueprints`. " +
        "Example: shio apply --blueprint blog --site Acme --dry-run",
    );
  }
  const site = typeof flags.site === "string" ? flags.site : undefined;
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const detail = await client.blueprint(name, { files: true });
  const params = coerceParams(await readParams(flags, ctx, "apply"), detail?.manifest?.params);
  const planOnly = Boolean(flags.check || flags.dryRun);
  const result = await client.applyBlueprint(name, {
    site,
    params,
    dryRun: planOnly,
    // SH151/SH152 — both opt-in, and both named in the plan before they happen. `--create-site`
    // makes scaffolding and seeding one command; `--merge-post-types` adds the fields an
    // existing post-type lacks, which is what the bootstrap `Page` versus a section
    // vocabulary's `Page` collision needs.
    createSite: Boolean(flags.createSite),
    mergePostTypes: Boolean(flags.mergePostTypes),
  });

  ctx.stdout.write(renderApplied(result));

  const files = planOnly ? [] : await writeFiles(detail, flags, ctx, result?.params ?? params);
  if (planOnly && detail?.files?.length) {
    ctx.stdout.write(
      `  ${detail.files.length} front-end file(s) would be written into this project\n`,
    );
  }
  for (const line of files) ctx.stdout.write(`  ${line}\n`);

  if (flags.check && result.content?.changed > 0) {
    ctx.stderr.write(
      `shio apply: ${result.content.changed} change(s) pending — the site does not match ` +
        `blueprint ${name}.\n`,
    );
    return { ...result, exitCode: 1 };
  }
  if (!planOnly && result.verify && result.verify.ok === false) {
    // The package's own promise, unmet. Exit non-zero: an unattended apply that reported
    // success here would be the "it applied fine" that SH94 exists to make impossible.
    const missing = result.verify.missing?.length
      ? ` Missing: ${result.verify.missing.join(", ")}.`
      : "";
    ctx.stderr.write(
      `shio apply: ${name} applied, but verification failed (${result.verify.errors} error(s)).` +
        `${missing} Run \`shio verify --site ${result.site}\` for the findings.\n`,
    );
    return { ...result, exitCode: 1 };
  }
  return result;
}


/**
 * Writes the package's front-end files into the project — see
 * {@link module:project-files}, shared with `shio init --blueprint` (SH119).
 *
 * The resolved parameters go in too: a carried file with `{{projectName}}` in it must not
 * land on disk still saying `{{projectName}}` when the same run substituted it throughout
 * the content.
 */
async function writeFiles(detail, flags, ctx, params) {
  const { lines } = await writeProjectFiles(
    detail?.files ?? [],
    { force: Boolean(flags.force), params },
    ctx,
  );
  return lines;
}

/**
 * The applied/plan report: the header, the model rows, the content change set, then the
 * verification. Same register as the document form — one line per thing that moved.
 */
function renderApplied(result) {
  const lines = [
    `shio apply: blueprint ${result.blueprint}${result.version ? ` ${result.version}` : ""}` +
      ` → site ${result.site}${result.dryRun ? " (plan — nothing written)" : ""}`,
  ];
  for (const [name, value] of Object.entries(result.params ?? {})) {
    if (name !== "site") lines.push(`  param ${name}=${value}`);
  }
  for (const postType of result.postTypes ?? []) {
    const missing = postType.missing?.length
      ? ` — the existing type has no ${postType.missing.join(", ")}`
      : "";
    lines.push(`  post-type ${postType.action} ${postType.name}${missing}`);
  }
  const content = result.content ?? { ops: 0, changed: 0, results: [] };
  lines.push(`  content: ${content.changed} change(s) of ${content.ops} op(s)`);
  for (const change of content.results ?? []) {
    if (change.action === "noop") continue;
    lines.push(`    ${change.action} ${change.address}${change.detail ? ` (${change.detail})` : ""}`);
    lines.push(...broken(change).map((line) => `  ${line}`));
  }
  if (result.verify) {
    lines.push(
      `  verify: ${result.verify.ok ? "ok" : "failed"} — ${result.verify.errors} error(s), ` +
        `${result.verify.warnings} warning(s) [${(result.verify.checks ?? []).join(",")}]`,
    );
    for (const missing of result.verify.missing ?? []) {
      lines.push(`    missing ${missing}`);
    }
  }
  // SH155 — printed because the record is the point: the next session finds it in the
  // context pack, and the operator who ran this should know it is there to be found.
  if (result.recordedAs) {
    lines.push(`  recorded: ${result.recordedAs} (shio remember --site ${result.site})`);
  }
  return `${lines.join("\n")}\n`;
}
