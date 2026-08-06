/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio init` — scaffold a Next.js site wired to the Shio CDA.
 *
 * Two sources for one starter (SH119). By default the tree comes from the bundled
 * `@viglet/create-shio-app` template, so `shio init` and `npm create @viglet/shio-app` produce identical
 * projects and neither needs a running instance. With `--blueprint <name>` it comes from
 * the instance instead: the package's carried `files` are written into the project and —
 * when a `--site` is named — its content half is applied at the same time, so one command
 * leaves both a front end and the pages it renders.
 *
 * The `nextjs-starter` blueprint *is* the bundled template, generated from it by
 * `@viglet/create-shio-app`'s `emit` with a drift test behind it. So the choice is about where the
 * bytes come from and whether content comes with them — never about which starter you get.
 */
import { join, resolve } from "node:path";
import { createInterface } from "node:readline/promises";

import { scaffold } from "@viglet/create-shio-app/scaffold";
import { unpublished } from "@viglet/create-shio-app/published";

import { ShioAgentClient } from "../agent-client.mjs";
import {
  coerceParams,
  missingParams,
  missingParamsMessage,
  readParams,
} from "../blueprint-params.mjs";
import { resolveAdmin } from "../context.mjs";
import { writeProjectFiles } from "../project-files.mjs";

async function prompt(rl, question, fallback) {
  const answer = (await rl.question(`${question}${fallback ? ` (${fallback})` : ""}: `)).trim();
  return answer || fallback || "";
}

/**
 * What `shio init --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio init [name] — scaffold a Next.js site wired to this instance

  --name <name>        The directory to create, when no positional is given
  --blueprint <name>   Take the starter from the instance instead of the built-in template
  --site <site>        With --blueprint: also apply the package's content into that site
  --param k=v          With --blueprint: one parameter value; repeat for several
  --params <file.json> With --blueprint: values from a file (--param wins over it)
  --create-site        With --blueprint --site: create the site (blank) if it is not there
  --force              Overwrite files that are already there
  --yes                Take every default without asking

Examples:
  shio init my-site
  shio init acme --blueprint marketing-site --site Acme --param brand=Acme

With --site and no --blueprint, a site whose pages are built from sections is refused: the
built-in template renders a page's body and not its sections (SH502).
`;

export async function run(flags, positionals, ctx) {
  let projectName = positionals[0] ?? flags.name;
  let { url, site, token } = flags;

  const interactive = ctx.stdin?.isTTY && !flags.yes;
  if (interactive) {
    const rl = createInterface({ input: ctx.stdin, output: ctx.stdout });
    try {
      projectName = projectName || (await prompt(rl, "Project name", "my-shio-site"));
      url = url ?? (await prompt(rl, "Shio base URL", "http://localhost:2710"));
      site = site ?? (await prompt(rl, "Shio site id", ""));
      token = token ?? (await prompt(rl, "Shio CDA token", ""));
    } finally {
      rl.close();
    }
  }

  projectName = projectName || "my-shio-site";
  const targetDir = resolve(ctx.cwd, projectName);

  if (flags.blueprint) {
    return initFromBlueprint({ flags, projectName, targetDir, url, site, token }, ctx);
  }

  // SH502 — before the write, because the built-in template cannot render this site's pages.
  if (site) {
    await refuseSectionsSite(flags, site, ctx);
  }

  const { files } = await scaffold({ targetDir, projectName, url, site, token });
  ctx.stdout.write(created(projectName, targetDir, files.length));
  return { targetDir, files };
}

/**
 * Scaffold from a blueprint on the instance.
 *
 * `--site` is optional and the difference matters, so it is reported rather than assumed:
 * with a site the content half is applied and the project has pages to render; without one
 * only the front-end files are written, and the output says so and how to finish (P9 — say
 * what was *not* done, rather than leaving a caller to discover an empty site).
 */
async function initFromBlueprint({ flags, projectName, targetDir, url, site, token }, ctx) {
  const name = typeof flags.blueprint === "string" ? flags.blueprint : "nextjs-starter";
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const detail = await client.blueprint(name, { files: true });
  if (!detail?.files?.length) {
    throw new Error(
      `shio init: blueprint "${name}" carries no front-end files, so there is nothing to ` +
        `scaffold from it. Apply its content into a project you already have instead: ` +
        `shio apply --blueprint ${name} --site <site>`,
    );
  }

  // SH447 — the parameters, and the check that makes a refusal true.
  //
  // Both halves were missing and together they made a package with a required parameter
  // unscaffoldable: this sent `{projectName}` and nothing else, the server refused on the parameter
  // it was never given, and the run exited non-zero **having already written the whole Next.js
  // app**. Measured: 15 files and a correct `.env.local`, reported by one line that mentions none
  // of them — and the retry then fails with "Target directory is not empty", which is true and
  // unexplainable from anything the caller was told. P7's surprise, arriving one command later.
  //
  // Validated rather than merely reported, because the two are not equivalent: reporting what
  // landed makes the run honest and still leaves the directory that blocks the retry.
  const schema = detail?.manifest?.params;
  const params = coerceParams(
    { projectName, ...(await readParams(flags, ctx, "init")) },
    schema,
  );
  // Only when a `--site` is given, which is precisely when the content half runs and therefore the
  // only case that was ever refused. Without one this command writes files and calls nothing, so
  // checking here would newly refuse a run that works today — the refusal is being moved earlier,
  // not widened.
  const missing = site ? missingParams(schema, params) : [];
  if (missing.length) {
    throw new Error(missingParamsMessage("init", name, missing));
  }
  // The same defect with a second trigger, found by running the fix against a live instance: with
  // the parameter supplied, `--site` naming a site that does not exist refused *after* the fifteen
  // files, exactly as the parameter had. `apply --blueprint` has `--create-site` for this and `init`
  // did not, so the remedy the server's own refusal names ("set createSite: true") was one this
  // command could not express — SH439's shape, at a third call site. Checked here for the same
  // reason as the parameters: a precondition that is knowable before the write belongs before it.
  if (site && !flags.createSite) {
    await requireSite(client, name, site);
  }

  const { lines, written } = await writeProjectFiles(
    detail.files,
    { force: Boolean(flags.force), params, baseDir: targetDir },
    ctx,
  );
  await ctx.fs.mkdir(targetDir, { recursive: true });
  await ctx.fs.writeFile(join(targetDir, ".env.local"), envFile({ url, site, token }), "utf8");

  const applied = site
    ? await client.applyBlueprint(name, { site, params, createSite: Boolean(flags.createSite) })
    : null;

  const version = detail.manifest?.version ? ` ${detail.manifest.version}` : "";
  ctx.stdout.write(`\n✓ Scaffolded ${projectName} from blueprint ${name}${version}\n`);
  for (const line of lines) ctx.stdout.write(`  ${line}\n`);
  if (applied) {
    ctx.stdout.write(
      `  applied ${applied.content?.changed ?? 0} content change(s) into site ${applied.site}\n`,
    );
  } else {
    ctx.stdout.write(
      `  content NOT applied — no --site given. ` +
        `Run: shio apply --blueprint ${name} --site <site>\n`,
    );
  }
  ctx.stdout.write(created(projectName, targetDir, written.length + 1));
  return { targetDir, files: written, applied };
}

/**
 * Refuse a `--site` that is not there, before anything is written (SH447).
 *
 * <p>A read failure that is <em>not</em> "no such site" is swallowed on purpose: this is a courtesy
 * check ahead of a call that will make the same judgement authoritatively, and refusing a scaffold
 * because a resolve timed out would turn a network blip into a broken command. The server's own
 * refusal still stands behind it — this only decides whether it arrives before or after fifteen
 * files.</p>
 */
async function requireSite(client, blueprint, site) {
  const resolved = await client.resolve(`site:${site}`).catch((error) => error);
  const missing = resolved instanceof Error && Number(resolved?.status) === 404;
  if (!missing) return;
  throw new Error(
    `shio init: there is no site named "${site}" to apply "${blueprint}" into.\n`
      + "A blueprint brings a model and content, not a site of its own, and creating one is the most\n"
      + "visible side effect in the system — so it is requested, never inferred (SH151).\n"
      + "Checked before anything was written, so nothing is on disk to clean up.\n"
      + `  shio init … --blueprint ${blueprint} --site ${site} --create-site\n`
      + "  shio apply --blueprint … --site <an existing site>",
  );
}

/**
 * The built-in template is not offered for a site whose pages are made of sections (SH502).
 *
 * ## Two templates, and neither serves an authorable replica
 *
 * SH446 made the built-in scaffold *installable*: it declares `@viglet/shio-client@^2026.2.1` and
 * `npm install --dry-run` resolves. What it does not do is draw a Page's `sections` — there is
 * nothing under `app/` or `lib/` that reads them — so for a converted site in **authorable** mode it
 * builds, installs, serves, and renders empty pages. The `marketing-site` blueprint does render them
 * and asks for `@viglet/shio-sections`, which has never been published and answers **E404**.
 *
 * So the honest state until that release is that an authorable replica has *no* installable front
 * end, and the wrong one being handed over silently is the part this can fix. A refusal that names
 * the reason costs the caller one command; a scaffold that installs and shows nothing costs them the
 * afternoon it takes to work out which of six things is wrong (P9 — the perception loop closes here,
 * with text, rather than after `npm run dev`).
 *
 * ## Why it is a courtesy check that swallows every read failure
 *
 * Without `--blueprint` this command needs **no running instance** — that is SH119's whole point,
 * since `shio init` and `npm create @viglet/shio-app` must produce identical projects. So an unreachable
 * instance, a missing credential or an older surface leaves the run exactly as it was: the same
 * bargain `requireSite` strikes one path over, for the same reason.
 */
async function refuseSectionsSite(flags, site, ctx) {
  const pages = await sectionCarryingPages(flags, site, ctx);
  if (!pages.length) return;

  throw new Error(
    `shio init: site "${site}" builds its pages from sections, and the built-in template does not\n`
      + `render them. ${pages.length} of its Page(s) carry a sections list, e.g. ${pages[0]}.\n\n`
      + `The template draws a page's body; nothing in it reads a Page's \`sections\`, so this project\n`
      + `would install, build, serve — and show empty pages, which is a failure nothing reports until\n`
      + `somebody opens one.\n\n`
      + sectionsRenderer(site)
      + `\nChecked before anything was written, so nothing is on disk to clean up.`,
  );
}

/**
 * Where a section-rendering front end comes from — which depends on whether it can be installed.
 *
 * <p>Read from `PUBLISHED` rather than written here, so the day `@viglet/shio-sections` ships this
 * message changes with it. A refusal that recommends a blueprint whose `npm install` answers E404
 * would be the same defect one command along: honest about the first template and wrong about the
 * second.</p>
 */
function sectionsRenderer(site) {
  const missing = unpublished(["@viglet/shio-sections", "@viglet/shio-model"]);
  if (!missing.length) {
    return `Scaffold the one that does render them:\n`
      + `  shio init <name> --blueprint marketing-site --site ${site}\n`;
  }
  return `The scaffold that does render them is the \`marketing-site\` blueprint, and it cannot be\n`
    + `installed yet: it imports ${missing.join(" and ")}, unpublished on the npm registry (E404).\n`
    + `Until that release lands, this site's front end is Shio's own renderer, which needs none:\n`
    + `  /sites/${site}/default/en-us/\n`;
}

/**
 * The addresses of this site's Pages that carry a non-empty `sections` list.
 *
 * <p>Two calls and a cap: `find` names the Pages and one `read` answers for all of them. The cap is
 * deliberate and does not weaken the check — the question is "does this site use sections at all",
 * and one page answering yes is the whole finding. A site-wide walk would make a command that needs
 * no instance pay for a projection it does not use.</p>
 */
async function sectionCarryingPages(flags, site, ctx) {
  try {
    const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
    const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
    const found = await client.find({ site, type: "Page", limit: 25 });
    const addresses = (found?.results ?? []).map((row) => row?.address).filter(Boolean);
    if (!addresses.length) return [];
    const docs = await client.read(addresses);
    return (Array.isArray(docs) ? docs : [])
      .filter((doc) => hasSections(doc))
      .map((doc) => doc.address);
  } catch {
    // A command that works without an instance must keep working without one.
    return [];
  }
}

/** A relator reads back as a list; a single value and a comma-joined string both count as one. */
function hasSections(doc) {
  const sections = doc?.data?.sections;
  if (Array.isArray(sections)) return sections.length > 0;
  return typeof sections === "string" && sections.trim() !== "";
}

function created(projectName, targetDir, fileCount) {
  return (
    `\n✓ Created ${projectName} (${fileCount} files) at ${targetDir}\n\n` +
    `Next steps:\n` +
    `  cd ${projectName}\n` +
    `  npm install\n` +
    `  npm run dev\n\n` +
    `Edit .env.local to point at your Shio instance, then "shio types" for typed content.\n`
  );
}

/**
 * `.env.local`. Written here rather than carried by the package for one reason: it holds a
 * CDA token, and a token has no business travelling to the CMS and back to reach the
 * machine that typed it.
 */
function envFile({ url, site, token } = {}) {
  return (
    `SHIO_URL=${url ?? "http://localhost:2710"}\n` +
    `SHIO_SITE_ID=${site ?? ""}\n` +
    `SHIO_CDA_TOKEN=${token ?? ""}\n` +
    `SHIO_PREVIEW_TOKEN=\n` +
    `SHIO_PREVIEW_SECRET=\n` +
    `SHIO_WEBHOOK_SECRET=\n`
  );
}
