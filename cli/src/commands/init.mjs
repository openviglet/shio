/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio init` — scaffold a Next.js site wired to the Shio CDA.
 *
 * Two sources for one starter (SH119). By default the tree comes from the bundled
 * `create-shio-app` template, so `shio init` and `npm create shio-app` produce identical
 * projects and neither needs a running instance. With `--blueprint <name>` it comes from
 * the instance instead: the package's carried `files` are written into the project and —
 * when a `--site` is named — its content half is applied at the same time, so one command
 * leaves both a front end and the pages it renders.
 *
 * The `nextjs-starter` blueprint *is* the bundled template, generated from it by
 * `create-shio-app`'s `emit` with a drift test behind it. So the choice is about where the
 * bytes come from and whether content comes with them — never about which starter you get.
 */
import { join, resolve } from "node:path";
import { createInterface } from "node:readline/promises";

import { scaffold } from "create-shio-app/scaffold";

import { ShioAgentClient } from "../agent-client.mjs";
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
  --force              Overwrite files that are already there
  --yes                Take every default without asking

Examples:
  shio init my-site
  shio init acme --blueprint marketing-site --site Acme
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

  const params = { projectName };
  const { lines, written } = await writeProjectFiles(
    detail.files,
    { force: Boolean(flags.force), params, baseDir: targetDir },
    ctx,
  );
  await ctx.fs.mkdir(targetDir, { recursive: true });
  await ctx.fs.writeFile(join(targetDir, ".env.local"), envFile({ url, site, token }), "utf8");

  const applied = site ? await client.applyBlueprint(name, { site, params }) : null;

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
