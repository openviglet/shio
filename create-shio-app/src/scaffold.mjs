/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Pure file-operations core for create-shio-app — no prompts, no process.exit,
 * so it can be unit-tested directly.
 *
 * SH119 folded the starter into the blueprint format, which split this module in
 * two. `readTemplateFiles()` reads the tree that ships in this package; `writeProject()`
 * writes *a* tree — the local one, or the `files` a blueprint carried — substitutes
 * parameters and writes `.env.local`. `scaffold()` is the first composed with the second,
 * and `shio init --blueprint` is the second on its own.
 *
 * The template tree here stays the single source of truth: `npm run emit` generates the
 * `nextjs-starter` blueprint package from it, and `test/emit.test.mjs` fails the build
 * when the two drift. One starter, two ways to get it.
 */
import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const TEMPLATE_DIR = fileURLToPath(new URL("../template/", import.meta.url));

/**
 * npm strips or renames dotfiles in published packages, so the template ships
 * `gitignore` and every consumer restores the leading dot. Keeping the mapping here
 * means the blueprint package and the local scaffold cannot disagree about it.
 */
const DOTFILE_RENAMES = { gitignore: ".gitignore" };

/**
 * `{{name}}` and nothing else — character-for-character the rule `ShBlueprintParams`
 * applies server-side, so a package cannot have a parameter one half substitutes and the
 * other leaves standing. Narrow enough that JSX (`style={{ color: "#666" }}`) is not a
 * placeholder, which is what lets a starter tree carry both.
 */
const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_.\-]+)\s*\}\}/g;

/**
 * Substitute `{{name}}` placeholders from `params`. An unknown placeholder is left
 * alone rather than blanked: a starter file is source the developer will read, and a
 * silently emptied string is harder to notice than a visible `{{typo}}`.
 *
 * <p>Deliberately the *client's* job for carried files, even though the server
 * substitutes a blueprint's content. The server never writes a project file (it cannot
 * see the working tree), and the one parameter a starter needs — the project's own name —
 * is local identity the server has no business being told.</p>
 *
 * @param {string} text
 * @param {Record<string, string>} params
 */
export function substituteParams(text, params) {
  if (!text || !params) return text;
  return text.replace(PLACEHOLDER, (match, name) =>
    Object.hasOwn(params, name) && params[name] != null ? String(params[name]) : match
  );
}

/**
 * The starter tree that ships in this package, as `{ path, text }` rows — the same
 * shape a blueprint's `files` come back in, so both paths write through one function.
 *
 * @returns {Promise<Array<{path: string, text: string}>>}
 */
export async function readTemplateFiles() {
  const rows = [];
  for (const rel of await listFiles(TEMPLATE_DIR)) {
    rows.push({
      path: DOTFILE_RENAMES[rel] ?? rel,
      text: await readFile(join(TEMPLATE_DIR, rel), "utf8"),
    });
  }
  return rows.sort((a, b) => a.path.localeCompare(b.path));
}

/**
 * Write a starter tree into `targetDir`.
 *
 * @param {object} options
 * @param {string} options.targetDir  Absolute path of the project directory.
 * @param {Array<{path: string, text: string}>} options.files The tree to write.
 * @param {Record<string, string>} [options.params] `{{name}}` substitutions.
 * @param {object} [options.env] Connection details for `.env.local`; omit to skip it.
 * @param {boolean} [options.force] Overwrite existing files instead of skipping them.
 * @param {boolean} [options.requireEmpty] Refuse a non-empty target (the `create` default).
 * @returns {Promise<{targetDir: string, files: string[], skipped: string[]}>}
 */
export async function writeProject({
  targetDir,
  files,
  params = {},
  env,
  force = false,
  requireEmpty = false,
}) {
  if (!targetDir) throw new Error("writeProject: targetDir is required");
  if (!Array.isArray(files) || files.length === 0) {
    throw new Error("writeProject: files is required and must not be empty");
  }

  if (requireEmpty && existsSync(targetDir)) {
    const entries = await readdir(targetDir);
    if (entries.length > 0) {
      throw new Error(`Target directory is not empty: ${targetDir}`);
    }
  }
  await mkdir(targetDir, { recursive: true });

  const written = [];
  const skipped = [];
  for (const file of files) {
    const target = join(targetDir, file.path);
    if (!force && existsSync(target)) {
      // A starter written over a project somebody has since edited is the one
      // irreversible thing this module could do (P7), so it is opt-in.
      skipped.push(file.path);
      continue;
    }
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, substituteParams(file.text, params), "utf8");
    written.push(file.path);
  }

  if (env) {
    await writeFile(join(targetDir, ".env.local"), envFile(env), "utf8");
    written.push(".env.local");
  }
  return { targetDir, files: written.sort(), skipped: skipped.sort() };
}

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

  const { files } = await writeProject({
    targetDir,
    files: await readTemplateFiles(),
    params: { projectName },
    env: { url, site, token },
    requireEmpty: true,
  });
  return { targetDir, files };
}

export { TEMPLATE_DIR, DOTFILE_RENAMES };

/**
 * `.env.local`. The preview / webhook keys (SH9) are optional and start empty — see
 * `.env.example`. Never carried by a blueprint: it holds a token, and a token has no
 * business making a round trip through the CMS to get back to the machine that typed it.
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
