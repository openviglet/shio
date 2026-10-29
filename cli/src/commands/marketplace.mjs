/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio marketplace <list|show|install|uninstall>` (SH677) — the package catalogue from a terminal.
 *
 * ## Why this exists rather than "the console has a page for it"
 *
 * The non-goals say it in one line: **no feature whose only interface is the console**. A
 * marketplace is the kind of feature that invites the breach, because browsing a catalogue is such
 * an obviously visual act that the CLI half reads as ceremony — right up to the moment an instance
 * has to be set up with nobody at a keyboard.
 *
 * What it actually buys is the pairing, which is unavailable today because the first half does not
 * exist anywhere but a browser:
 *
 *     shio marketplace install shio-blueprint-blog
 *     shio apply --blueprint shio-blueprint-blog --param blogTitle="Field notes"
 *
 * Two commands, no console, no session — which is the shape P2 is about.
 *
 * SH1251 makes it one when that is what the operator wants: `install <id> --apply --site <name>`
 * installs (or skips that half for a package already here) and then runs the very apply
 * `shio apply --blueprint` runs, with the same flags. Still two operations on the server, so the
 * package survives an apply that fails and the next apply can use it.
 *
 * ## Install is not apply, here too
 *
 * `install` puts the package where the registry can see it and stops. Applying it to a site stays
 * `shio apply --blueprint`, unchanged. The separation is the same one the REST surface keeps, for
 * the same reason: an operator should be able to read what they installed before anything writes
 * to their content.
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";
import { applyBlueprint } from "./apply.mjs";

export const HELP = helpText("marketplace");

/** The verbs, so an unknown one can be refused with the list rather than a stack trace. */
const ACTIONS = ["list", "show", "install", "uninstall"];

export async function run(flags, positionals, ctx) {
  const action = positionals[0] ?? "list";
  if (!ACTIONS.includes(action)) {
    throw new Error(
      `shio marketplace: unknown action "${action}". Use one of: ${ACTIONS.join(", ")}.`,
    );
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  if (action === "list") return list(client, flags, ctx);
  if (action === "show") return show(client, flags, positionals, ctx);
  if (action === "install") return install(client, flags, positionals, ctx);
  return uninstall(client, positionals, ctx);
}

/** Every entry, optionally narrowed, with where the list came from printed beneath it. */
async function list(client, flags, ctx) {
  const catalogue = await client.marketplace();
  const items = filtered(catalogue.items ?? [], flags);

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ ...catalogue, items }, null, 2)}\n`);
    return { ...catalogue, items };
  }

  if (!items.length) {
    ctx.stdout.write("shio marketplace: nothing to list.\n");
  } else {
    const width = Math.max(...items.map((item) => item.id.length));
    for (const item of items) {
      const state = item.installed ? "installed" : "available";
      const origin = item.builtIn ? "built-in" : item.category ?? "";
      ctx.stdout.write(`  ${item.id.padEnd(width)}  ${(item.version ?? "-").padEnd(8)}`
        + `  ${state.padEnd(9)}  ${origin}\n`);
    }
  }
  ctx.stdout.write(`\n${sourceLine(catalogue.source)}\n`);
  return { ...catalogue, items };
}

/**
 * One entry in full, with its README.
 *
 * The README is fetched through the instance rather than directly, which is not only about CORS:
 * the server only proxies a URL its own catalogue declares, so `show` cannot be talked into
 * fetching something the operator's catalogue never offered.
 */
async function show(client, flags, positionals, ctx) {
  const id = requireId(positionals, "show");
  const catalogue = await client.marketplace();
  const item = (catalogue.items ?? []).find((each) => each.id === id);
  if (!item) {
    throw new Error(`shio marketplace show: no entry with id "${id}". `
      + `Run \`shio marketplace list\` to see what this instance offers.`);
  }

  let readme = null;
  if (item.readmeUrl) {
    try {
      readme = await client.marketplaceReadme(item.readmeUrl);
    } catch {
      // A missing README is not a reason to withhold the entry — which is the whole answer the
      // caller asked for. Reported as absent rather than raised.
      readme = null;
    }
  }

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ ...item, readme }, null, 2)}\n`);
    return { ...item, readme };
  }

  ctx.stdout.write(`${item.id}  ${item.version ?? "-"}\n`);
  if (item.title) ctx.stdout.write(`  ${item.title}\n`);
  if (item.description) ctx.stdout.write(`\n  ${item.description}\n`);
  ctx.stdout.write(`\n  state:    ${item.installed ? "installed" : "available"}`
    + `${item.builtIn ? " (built-in)" : ""}\n`);
  if (item.author) ctx.stdout.write(`  author:   ${item.author}\n`);
  if (item.requires?.length) ctx.stdout.write(`  requires: ${item.requires.join(", ")}\n`);
  const params = Object.keys(item.params?.properties ?? {});
  if (params.length) ctx.stdout.write(`  params:   ${params.join(", ")}\n`);
  // SH1253 — a terminal cannot draw them, so it names them.
  for (const url of item.screenshots ?? []) ctx.stdout.write(`  screenshot: ${url}\n`);
  if (readme) ctx.stdout.write(`\n${readme}\n`);
  return { ...item, readme };
}

/** Download and install, or — with `--dry-run` — say what would happen and write nothing. */
async function install(client, flags, positionals, ctx) {
  const id = requireId(positionals, "install");
  if (flags.apply) return installAndApply(client, id, flags, ctx);
  const dryRun = Boolean(flags["dry-run"] ?? flags.dryRun);
  const result = await client.installPackage(id, { dryRun });

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    return result;
  }
  ctx.stdout.write(dryRun
    ? `shio marketplace: ${id} ${result.version ?? ""} would install into ${result.origin}`
      + ` — nothing was written.\n`
    : `shio marketplace: installed ${id} ${result.version ?? ""} into ${result.origin}`
      + ` (${result.loaded} package(s) now).\n`);
  if (!dryRun) {
    ctx.stdout.write(`Apply it with: shio apply --blueprint ${id} --site <site>\n`);
  }
  return result;
}

/**
 * SH1251 — install if the package is not here yet, then apply it to a site.
 *
 * <p>The catalogue is read first because the install half is not always there: a built-in package
 * and one already installed have nothing to download, and the server refuses both (409). Skipping
 * the call is the honest version of "install if needed".</p>
 *
 * <p>A dry run plans the install, and plans the apply only when the package is already in the
 * registry: the apply plan is computed by the server from the package, and a package that has not
 * been downloaded is one the server has not read. Saying so beats a plan of nothing.</p>
 */
async function installAndApply(client, id, flags, ctx) {
  if (typeof flags.site !== "string") {
    throw new Error(`shio marketplace install ${id} --apply: needs --site <name>, the site to `
      + `apply it to. Add --create-site to make a new one.`);
  }
  const catalogue = await client.marketplace();
  const item = (catalogue.items ?? []).find((each) => each.id === id);
  if (!item) {
    throw new Error(`shio marketplace install: no entry with id "${id}". `
      + `Run \`shio marketplace list\` to see what this instance offers.`);
  }
  const dryRun = Boolean(flags["dry-run"] ?? flags.dryRun);
  const needsInstall = !item.builtIn && !item.installed;

  let installed = null;
  if (needsInstall) {
    installed = await client.installPackage(id, { dryRun });
    ctx.stdout.write(dryRun
      ? `shio marketplace: ${id} ${installed.version ?? ""} would install into ${installed.origin}.\n`
      : `shio marketplace: installed ${id} ${installed.version ?? ""} into ${installed.origin}.\n`);
  } else {
    ctx.stdout.write(`shio marketplace: ${id} is ${item.builtIn ? "built in" : "already installed"}`
      + `, so there is nothing to install.\n`);
  }

  if (dryRun && needsInstall) {
    ctx.stdout.write(`shio marketplace: the apply plan needs the package installed, so it is not `
      + `shown — nothing was written. Install it, then run `
      + `\`shio apply --blueprint ${id} --site ${flags.site} --dry-run\`.\n`);
    return { install: installed, apply: null };
  }

  try {
    const applied = await applyBlueprint({ ...flags, blueprint: id }, [], ctx);
    return { install: installed, apply: applied, exitCode: applied?.exitCode };
  } catch (error) {
    if (installed && !dryRun) {
      // The package is valid and stays where the registry sees it: say so, or the operator
      // reinstalls and meets the 409 for a package that never left.
      error.message = `${error.message}\nshio marketplace: ${id} stays installed; `
        + `retry with \`shio apply --blueprint ${id} --site ${flags.site}\`.`;
    }
    throw error;
  }
}

async function uninstall(client, positionals, ctx) {
  const id = requireId(positionals, "uninstall");
  const result = await client.uninstallPackage(id);
  ctx.stdout.write(
    `shio marketplace: removed ${id} (${result.loaded} package(s) now).\n`,
  );
  return result;
}

function requireId(positionals, action) {
  const id = positionals[1];
  if (!id) {
    throw new Error(`shio marketplace ${action}: needs an id. `
      + `Run \`shio marketplace list\` to see what this instance offers.`);
  }
  return id;
}

/** `--category` and `--tag` narrow; neither is a search, because an id is what install takes. */
function filtered(items, flags) {
  let out = items;
  if (typeof flags.category === "string") {
    out = out.filter((item) => item.category === flags.category);
  }
  if (typeof flags.tag === "string") {
    out = out.filter((item) => (item.tags ?? []).includes(flags.tag));
  }
  if (flags.installed) out = out.filter((item) => item.installed);
  if (flags.available) out = out.filter((item) => !item.installed);
  return out;
}

/**
 * Where the entries came from, in one sentence.
 *
 * Printed on every listing rather than only when something went wrong: a catalogue that is
 * configured and unreachable produces the same *shape* of answer as one that was read, and the
 * difference is exactly what a reader needs. A listing that silently shrank is indistinguishable
 * from a catalogue that shrank.
 */
function sourceLine(source) {
  if (!source) return "source: unknown";
  if (source.state === "builtin-only") {
    return "source: the packages that ship with this instance. The catalogue is turned off "
      + "(shio.marketplace.enabled=false).";
  }
  if (source.state === "unreachable") {
    return `source: ${source.url} could not be read — ${source.reason}\n`
      + "        The packages above are the ones that ship with this instance.";
  }
  const note = source.reason ? `\n        ${source.reason}` : "";
  return `source: ${source.url}${note}`;
}
