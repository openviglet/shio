#!/usr/bin/env node
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * create-shio-app — scaffold a Next.js App Router site wired to the Shio CDA.
 *
 *   npm create shio-app@latest my-site
 *   npx create-shio-app my-site --url https://cms.example.com --site <id> --token <key>
 */
import { resolve } from "node:path";
import { createInterface } from "node:readline/promises";
import { stdin, stdout, argv, env, exit } from "node:process";

import { scaffold } from "./scaffold.mjs";

function parseArgs(args) {
  const opts = { yes: false };
  const positionals = [];
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === "--yes" || a === "-y") opts.yes = true;
    else if (a === "--url") opts.url = args[++i];
    else if (a === "--site") opts.site = args[++i];
    else if (a === "--token") opts.token = args[++i];
    else if (a.startsWith("--url=")) opts.url = a.slice(6);
    else if (a.startsWith("--site=")) opts.site = a.slice(7);
    else if (a.startsWith("--token=")) opts.token = a.slice(8);
    else if (!a.startsWith("-")) positionals.push(a);
  }
  opts.projectName = positionals[0];
  return opts;
}

async function prompt(rl, question, fallback) {
  const answer = (await rl.question(`${question}${fallback ? ` (${fallback})` : ""}: `)).trim();
  return answer || fallback || "";
}

async function main() {
  const opts = parseArgs(argv.slice(2));
  const interactive = stdin.isTTY && !opts.yes;

  let { projectName, url, site, token } = opts;

  if (interactive) {
    const rl = createInterface({ input: stdin, output: stdout });
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
  const targetDir = resolve(process.cwd(), projectName);

  try {
    const { files } = await scaffold({ targetDir, projectName, url, site, token });
    const pm = detectPackageManager();
    stdout.write(
      `\n✓ Created ${projectName} (${files.length} files) at ${targetDir}\n\n` +
        `Next steps:\n` +
        `  cd ${projectName}\n` +
        `  ${pm.install}\n` +
        `  ${pm.dev}\n\n` +
        `Edit .env.local to point at your Shio instance.\n` +
        `Open http://localhost:3000 — the catch-all route resolves CDA paths to posts.\n`
    );
  } catch (err) {
    stdout.write(`\n✗ ${err.message}\n`);
    exit(1);
  }
}

function detectPackageManager() {
  const ua = env.npm_config_user_agent || "";
  if (ua.startsWith("pnpm")) return { install: "pnpm install", dev: "pnpm dev" };
  if (ua.startsWith("yarn")) return { install: "yarn", dev: "yarn dev" };
  if (ua.startsWith("bun")) return { install: "bun install", dev: "bun dev" };
  return { install: "npm install", dev: "npm run dev" };
}

main();
