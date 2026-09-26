/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio patterns` (SH1040) — capture a block that works, place it somewhere else.
 *
 * ## Three actions and one verb
 *
 * `save`, `list` and `apply` are one subject and share a help text, which is the shape `shio assets`
 * and `shio content` already take. Three verbs would be three entries in the global help an agent
 * pays for on every session (SH394), for three words that only make sense together.
 *
 * ## Apply is the existing engine, not a second one
 *
 * A pattern application is a desired-state document: the sections it creates, plus the target Page
 * re-declared with its array extended. So it goes through `plan`/`applyConfirmed` like every other
 * document — one batch, one transaction, the same teaching errors — and this file composes rather
 * than writes. A second write path for "the same thing but into a page" is how two surfaces come to
 * disagree about what an upsert means (P10).
 *
 * ## The Page is re-declared whole, and that is SH168 rather than an oversight
 *
 * A document's post upsert declares whole state. Sending only `sections` would therefore *clear*
 * the page's title and body, so the target is read first and written back with one field changed.
 * The read is also what makes the insert position meaningful: `--after` counts the array the page
 * actually has, not the one the caller remembers.
 */
import { createHash } from "node:crypto";
import { dirname, join } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";
import {
  PATTERNS_DIR, fillPattern, parsePattern, patternPathFor, placementDocument, referencesOf,
  renderPattern, siteOf, validPatternName,
} from "../patterns-store.mjs";

export const HELP = helpText("patterns");

const ACTIONS = ["save", "list", "apply", "push"];

export async function run(flags, positionals, ctx) {
  const action = positionals[0];
  if (!ACTIONS.includes(action)) {
    throw new Error(
      `shio patterns: unknown action ${JSON.stringify(action ?? "")}. ` +
        `One of: ${ACTIONS.join(", ")}. Example: shio patterns list`,
    );
  }
  if (action === "list") return listPatterns(flags, ctx);
  const name = positionals[1];
  if (!validPatternName(name)) {
    throw new Error(
      `shio patterns ${action}: needs a name of lowercase letters, digits and hyphens — ` +
        `got ${JSON.stringify(name ?? "")}. A name is refused rather than slugged, because it is ` +
        "how the next command addresses the pattern. Example: shio patterns " +
        `${action} pricing-block`,
    );
  }
  if (action === "push") return pushPattern(name, flags, ctx);
  return action === "save" ? savePattern(name, flags, ctx) : applyPattern(name, flags, ctx);
}

/** `sha256:<hex>` over the file's bytes — what the instance answers for the copy it holds. */
function digestOf(text) {
  return `sha256:${createHash("sha256").update(Buffer.from(text, "utf8")).digest("hex")}`;
}

/**
 * SH1108 — copies a pattern file to the instance, where the console's rail can place it.
 *
 * The file stays the authority. The instance holds a copy, and a copy can go stale: `list --remote`
 * says which, by comparing this file's digest with the one the instance answers.
 */
async function pushPattern(name, flags, ctx) {
  const path = patternPathFor(name);
  const text = await ctx.fs.readFile(join(ctx.cwd, path), "utf8").catch(() => {
    throw new Error(`shio patterns push: no ${path}. List them: shio patterns list`);
  });
  parsePattern(text, path);
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const stored = await client.pushPattern(name, text);
  ctx.stdout.write(`Pushed ${path} to ${baseUrl} — ${stored?.digest ?? digestOf(text)}. `
    + "The visual editor's rail offers it now; the file stays the one you edit.\n");
  return 0;
}

/** Every pattern in the tree, with what it would place — and, with --remote, where each stands. */
async function listPatterns(flags, ctx) {
  const remote = flags.remote ? await remoteDigests(flags, ctx) : null;
  const dir = join(ctx.cwd, PATTERNS_DIR);
  let files = [];
  try {
    files = (await ctx.fs.readdir(dir)).filter((file) => file.endsWith(".json")).sort();
  } catch {
    // An absent directory is "none captured yet", not a failure: it is the state of every project
    // that has not used this, and saying so beats an ENOENT the reader has to interpret.
    ctx.stdout.write(`No patterns in ${PATTERNS_DIR}. Capture one: shio patterns save <name> `
      + "--from post:<site>/<url>\n");
    return 0;
  }
  if (!files.length) {
    ctx.stdout.write(`No patterns in ${PATTERNS_DIR}.\n`);
    return 0;
  }
  for (const file of files) {
    const path = join(dir, file);
    const text = await ctx.fs.readFile(path, "utf8");
    const pattern = parsePattern(text, `${PATTERNS_DIR}/${file}`);
    const types = pattern.sections.map((section) => section.type).join(", ");
    ctx.stdout.write(`${pattern.name}  ${pattern.sections.length} section(s)  ${types}`
      + `${pattern.from ? `  from ${pattern.from}` : ""}`
      + `${remote ? `  ${standing(remote.get(pattern.name), digestOf(text))}` : ""}\n`);
  }
  if (remote) {
    for (const name of [...remote.keys()].filter((key) => !files.includes(`${key}.json`))) {
      ctx.stdout.write(`${name}  pushed, with no file here\n`);
    }
  }
  return 0;
}

/** Where a local file stands against the instance's copy. */
export function standing(pushed, local) {
  if (!pushed) return "not pushed";
  return pushed === local ? "pushed" : "stale — push it again";
}

async function remoteDigests(flags, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  return new Map((await client.patterns()).map((entry) => [entry.name, entry.digest]));
}

/**
 * Captures a page's sections as a pattern.
 *
 * `--section` selects, repeatably and by position, so a curator who got one block right out of six
 * captures that one. Absent, the whole page is taken — which is the honest default for a verb whose
 * argument is a page.
 */
async function savePattern(name, flags, ctx) {
  const from = typeof flags.from === "string" ? flags.from : null;
  if (!from) {
    throw new Error(
      "shio patterns save: --from names the page to capture. " +
        `Example: shio patterns save ${name} --from post:Acme/pricing`,
    );
  }
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const [page] = await client.read([from]);
  if (!page) throw new Error(`shio patterns save: nothing at ${from}.`);
  const references = referencesOf(page.data?.sections);
  if (!references.length) {
    throw new Error(
      `shio patterns save: ${from} names no sections, so there is nothing to capture. ` +
        "A pattern is a set of a page's sections; compose the page first.",
    );
  }
  const wanted = selected(flags.section, references, from);
  const site = siteOf(from);
  const rows = await client.read(wanted.map((url) => `post:${site}${url}`));
  const byAddress = new Map(rows.map((row) => [row.address, row]));

  const sections = wanted.map((url) => {
    const row = byAddress.get(`post:${site}${url}`) ?? rows[wanted.indexOf(url)];
    if (!row) throw new Error(`shio patterns save: ${from} names ${url}, which does not resolve.`);
    return { type: row.type, data: row.data ?? {} };
  });

  const path = patternPathFor(name);
  const file = join(ctx.cwd, path);
  await ctx.fs.mkdir(dirname(file), { recursive: true });
  await ctx.fs.writeFile(file, renderPattern({
    name,
    title: typeof flags.title === "string" ? flags.title : undefined,
    from,
    captured: new Date().toISOString(),
    sections,
  }), "utf8");

  ctx.stdout.write(`Wrote ${path} — ${sections.length} section(s) from ${from}.\n`);
  // Said here rather than left to be discovered: the values are literal, and a pattern is only
  // reusable once somebody decides which of them are the pattern and which were that page's.
  ctx.stdout.write("Values are captured literally. Replace the ones that should vary with "
    + "{{name}} and declare them under `params`, then: shio patterns apply "
    + `${name} --at post:<site>/<url> --param name=<value>\n`);
  return 0;
}

/** Places a pattern into a page, as one document through the ordinary apply engine. */
async function applyPattern(name, flags, ctx) {
  const at = typeof flags.at === "string" ? flags.at : null;
  if (!at) {
    throw new Error(
      "shio patterns apply: --at names the page to place it in. " +
        `Example: shio patterns apply ${name} --at post:Acme/pricing`,
    );
  }
  const path = patternPathFor(name);
  const raw = await ctx.fs.readFile(join(ctx.cwd, path), "utf8").catch(() => {
    throw new Error(`shio patterns apply: no ${path}. List them: shio patterns list`);
  });
  const pattern = fillPattern(parsePattern(raw, path), paramsFrom(flags), path);

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const [page] = await client.read([at]);
  if (!page) throw new Error(`shio patterns apply: nothing at ${at}.`);

  const instance = typeof flags.as === "string" && flags.as ? flags.as : name;
  const { document, urls, where } = placementDocument({ pattern, page, at, instance, after: flags.after });

  const planOnly = Boolean(flags.dryRun);
  const result = await (planOnly
    ? client.plan(document)
    : client.applyConfirmed(document, {}, () => undefined));
  ctx.stdout.write(`${planOnly ? "Would place" : "Placed"} ${pattern.sections.length} section(s) `
    + `from ${name} into ${at} at position ${where + 1}: ${urls.join(", ")}\n`);
  ctx.stdout.write(`${planOnly ? "planned" : "changed"}: ${result?.changed ?? 0}\n`);
  return 0;
}

/** The selection, by position, refusing a number the page does not have. */
function selected(section, references, from) {
  const asked = section === undefined ? [] : [].concat(section);
  if (!asked.length) return references;
  return asked.map((raw) => {
    const index = Number(raw);
    if (!Number.isInteger(index) || index < 0 || index >= references.length) {
      throw new Error(
        `shio patterns save: --section ${raw} — ${from} has ${references.length} section(s), ` +
          `so the positions are 0..${references.length - 1}.`,
      );
    }
    return references[index];
  });
}

function paramsFrom(flags) {
  const out = {};
  for (const pair of [].concat(flags.param ?? [])) {
    const cut = String(pair).indexOf("=");
    if (cut <= 0) {
      throw new Error(`shio patterns apply: --param takes name=value, got ${JSON.stringify(pair)}.`);
    }
    out[String(pair).slice(0, cut)] = String(pair).slice(cut + 1);
  }
  return out;
}
