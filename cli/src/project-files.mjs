/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Writing a blueprint's front-end files into the project (SH93, SH119).
 *
 * The server never writes these — it cannot see the working tree, and a CMS that could
 * write into one would be a much larger thing than a CMS. So the package returns them and
 * this is the only place that puts them on disk, shared by `shio apply --blueprint` (which
 * writes them alongside the content) and `shio init --blueprint` (which writes them
 * instead of copying the bundled template).
 *
 * Parameter substitution happens *here* rather than server-side, on purpose. A carried
 * file's placeholders are about the project — its npm name, the directory it lives in —
 * and the machine that knows those is this one. The rule itself is imported from
 * `@viglet/create-shio-app` rather than re-implemented, because a package whose `{{param}}` the
 * server fills in content and the client leaves standing in source would be the worst of
 * both.
 */
import { dirname, join } from "node:path";

import { substituteParams } from "@viglet/create-shio-app/scaffold";

/**
 * Write `files` into `ctx.cwd`.
 *
 * Never over an existing file unless `force`: a blueprint applied twice must not silently
 * revert a page somebody has since edited (P7). Each outcome is reported, so "skipped" is
 * a line the caller reads rather than a silence they interpret.
 *
 * @param {Array<{path: string, text: string}>} files
 * @param {{ force?: boolean, params?: Record<string, string>, baseDir?: string }} options
 * @param {object} ctx
 * @returns {Promise<{lines: string[], written: string[], skipped: string[]}>}
 */
export async function writeProjectFiles(files, { force = false, params = {}, baseDir } = {}, ctx) {
  const root = baseDir ?? ctx.cwd;
  const lines = [];
  const written = [];
  const skipped = [];
  for (const file of files ?? []) {
    const target = join(root, file.path);
    const exists = await ctx.fs
      .readFile(target, "utf8")
      .then(() => true)
      .catch(() => false);
    if (exists && !force) {
      lines.push(`skipped ${file.path} (exists — pass --force to overwrite)`);
      skipped.push(file.path);
      continue;
    }
    await ctx.fs.mkdir(dirname(target), { recursive: true });
    await ctx.fs.writeFile(target, substituteParams(file.text, params), "utf8");
    lines.push(`${exists ? "overwrote" : "wrote"} ${file.path}`);
    written.push(file.path);
  }
  return { lines, written, skipped };
}
