/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Pure file-operations core for create-shio-app — no prompts, no process.exit,
 * so it can be unit-tested directly.
 */
import { cp, mkdir, readdir, readFile, rename, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const TEMPLATE_DIR = fileURLToPath(new URL("../template/", import.meta.url));

/**
 * @param {object} options
 * @param {string} options.targetDir   Absolute path of the project directory to create.
 * @param {string} options.projectName Package name written into the generated package.json.
 * @param {string} [options.url]       Shio base URL → SHIO_URL.
 * @param {string} [options.site]      Shio site id → SHIO_SITE_ID.
 * @param {string} [options.token]     CDA token → SHIO_CDA_TOKEN.
 * @returns {Promise<{targetDir: string, files: string[]}>}
 */
export async function scaffold({ targetDir, projectName, url, site, token }) {
  if (!targetDir) throw new Error("scaffold: targetDir is required");
  if (!projectName) throw new Error("scaffold: projectName is required");

  if (existsSync(targetDir)) {
    const entries = await readdir(targetDir);
    if (entries.length > 0) {
      throw new Error(`Target directory is not empty: ${targetDir}`);
    }
  }
  await mkdir(targetDir, { recursive: true });

  // 1. Copy the whole template tree.
  await cp(TEMPLATE_DIR, targetDir, { recursive: true });

  // 2. npm strips/renames dotfiles in published packages, so the template ships
  //    `gitignore`; restore the leading dot in the generated project.
  const shippedGitignore = join(targetDir, "gitignore");
  if (existsSync(shippedGitignore)) {
    await rename(shippedGitignore, join(targetDir, ".gitignore"));
  }

  // 3. Stamp the project name into package.json + README placeholders.
  await replaceInFile(join(targetDir, "package.json"), {
    "{{PROJECT_NAME}}": projectName,
  });
  await replaceInFile(join(targetDir, "README.md"), {
    "{{PROJECT_NAME}}": projectName,
  });

  // 4. Write .env.local with the supplied connection details.
  const env =
    `SHIO_URL=${url ?? "http://localhost:2710"}\n` +
    `SHIO_SITE_ID=${site ?? ""}\n` +
    `SHIO_CDA_TOKEN=${token ?? ""}\n`;
  await writeFile(join(targetDir, ".env.local"), env, "utf8");

  const files = await listFiles(targetDir);
  return { targetDir, files };
}

export { TEMPLATE_DIR };

async function replaceInFile(path, replacements) {
  if (!existsSync(path)) return;
  let content = await readFile(path, "utf8");
  for (const [needle, value] of Object.entries(replacements)) {
    content = content.split(needle).join(value);
  }
  await writeFile(path, content, "utf8");
}

async function listFiles(dir, prefix = "") {
  const out = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      out.push(...(await listFiles(join(dir, entry.name), rel)));
    } else {
      out.push(rel);
    }
  }
  return out;
}
