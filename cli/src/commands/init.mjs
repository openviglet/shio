/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio init` — scaffold a Next.js site wired to the Shio CDA. Delegates to the
 * `create-shio-app` scaffolder (the single source of truth for the template), so
 * `shio init` and `npm create shio-app` produce identical projects.
 */
import { resolve } from "node:path";
import { createInterface } from "node:readline/promises";

import { scaffold } from "create-shio-app/scaffold";

async function prompt(rl, question, fallback) {
  const answer = (await rl.question(`${question}${fallback ? ` (${fallback})` : ""}: `)).trim();
  return answer || fallback || "";
}

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

  const { files } = await scaffold({ targetDir, projectName, url, site, token });
  ctx.stdout.write(
    `\n✓ Created ${projectName} (${files.length} files) at ${targetDir}\n\n` +
      `Next steps:\n` +
      `  cd ${projectName}\n` +
      `  npm install\n` +
      `  npm run dev\n\n` +
      `Edit .env.local to point at your Shio instance, then "shio types" for typed content.\n`
  );
  return { targetDir, files };
}
