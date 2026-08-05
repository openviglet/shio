/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio pull` — fetch the lossless post-type defs (console Basic auth) and write
 * one deterministic JSON file per type under `shio/post-types/`. The other half
 * of "post-types as code": commit these, edit them, `shio push` them back.
 *
 * With `--content` (SH87) it pulls *content* instead — the same verb over the other
 * resource, projected to `shio/content/**` (SH86). Two resources, one pair of verbs: an
 * agent that learned pull/push for the model does not have to learn a second vocabulary
 * for the pages.
 */
import { join } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { fileNameFor, serializeDef } from "../post-types-io.mjs";
import { defFileCollisions } from "../content-files.mjs";

/**
 * What `shio pull --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio pull — pull the post-types into shio/post-types/*.json

  --content            Pull the content tree (shio/content/**) instead of the model
  --dir <dir>          Post-types directory (default shio/post-types)
  --out <dir>          Same as --dir

With --content (the SH90 projection):
  Content projects to shio/content/<site>/<url>.md — YAML frontmatter is the fields, the
  body is the post's rich-text field, and binaries land in shio/assets/<site>/... so a File
  field reads as a path rather than a UUID. A pull merges three ways (last-synced base,
  server, files): only a field both sides changed is a conflict, and a conflict writes
  nothing and exits non-zero.
  --site <name>        Scope to one site
  --ours / --theirs    Resolve a three-way conflict in favour of one side
  --force              Overwrite local files that differ
  --allow-partial      Keep what was pulled when the range could not be covered whole
  --show-remote        Print the remote side of each conflict
  --limit <n>          Rows to print
  --content-dir <dir>  Content tree (default shio/content)
  --assets-dir <dir>   Asset tree   (default shio/assets)
  --folders-dir <dir>  Folder markers (default shio/folders)
  --base-file <file>   The merge base (default shio/.shio/content-base.json)
  --dry-run            Print what would be written, write nothing

Examples:
  shio pull
  shio pull --content --site Acme
`;

export async function run(flags, positionals, ctx) {
  if (flags.content) {
    const content = await import("./content-pull.mjs");
    return content.run(flags, positionals, ctx);
  }
  return pullPostTypes(flags, ctx);
}

async function pullPostTypes(flags, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.out ?? flags.dir ?? "shio/post-types";

  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const defs = (await client.getPostTypeDefs()) ?? [];

  const target = join(ctx.cwd, dir);
  await ctx.fs.mkdir(target, { recursive: true });

  // SH409 — here, because this is the loop that writes. `Article` and `article` are two types and
  // one `.json` on Windows and macOS; the disk keeps the last write, `diffDefs` then reports the
  // other as removed, and `push --prune` deletes the post type every post of it is validated
  // against. Neither is written — there is no merging two models into one file.
  const clashes = defFileCollisions(defs.map((def) => def.name));
  for (const group of new Set(clashes.values())) {
    ctx.stdout.write(
      `shio pull: post-type ${group.addresses.join(" and ")} project to one file `
        + `(${group.paths.join(", ")}) — neither was written. A checkout on Windows or macOS keeps `
        + "one of them, and `push --prune` would then delete the other's type. Rename one of them "
        + "on the server.\n",
    );
  }

  const written = defs.filter((def) => !clashes.has(def.name));
  for (const def of written) {
    await ctx.fs.writeFile(join(target, fileNameFor(def)), serializeDef(def), "utf8");
  }
  ctx.stdout.write(`shio pull: wrote ${written.length} post-type(s) to ${dir}\n`);
  return written;
}
