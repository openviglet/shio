/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio assets prune` (SH1034) — the assets nothing references, and what deleting them frees.
 *
 * ## Why this is a command and not a flag on `verify`
 *
 * `verify` reports `asset-unused` and that is the right place for the *finding*: it is one line
 * per asset in a report a gate already reads. What it cannot answer is the question a curator
 * actually has, which is arithmetic over the whole set — **how much would I get back** — and a
 * checklist that ended in "and now add up the sizes yourself" would be a report nobody acts on.
 *
 * ## `--dry-run` is the default, and that is not timidity
 *
 * P7: never surprise the curator. Deleting content because a report said it was unreferenced is
 * exactly the act that must be typed out in full, so the safe reading is what you get for free and
 * `--delete` is the word you have to say. The dry run is also the more useful half most of the
 * time — the number is the decision, and the deletion is a consequence of having seen it.
 *
 * ## Composed, never re-derived (P10)
 *
 * There is no `/assets/unused` endpoint and there should not be one. The count rides on the media
 * listing that already exists (SH1034 stamps `usageCount` on every row of a page, in one indexed
 * query), so this command is a walk over that listing and a filter. A second server contract for
 * the same fact is how two surfaces come to disagree about what "unused" means.
 *
 * ## Unused means nothing references it AND no page loads it (SH1280)
 *
 * `usageCount` is the reference index, which a theme's `@font-face` never feeds, so Quilhara's
 * three fonts were listed as reclaimable while every page fetched them, and `verify` (SH1268) said
 * the opposite. The listing's `loads=true` adds `loadedBy`, counted the way verify's page check
 * reads a page, and an asset is unused here only when both are zero. That renders every page of
 * the sites involved, which is why the line says how many it rendered.
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("assets");

/** Bytes as a reader sees them, matching the console's own `formatBytes`. */
function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return "?";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${unit === 0 ? value : value.toFixed(1)} ${units[unit]}`;
}

export async function run(flags, positionals, ctx) {
  const verb = positionals[0] ?? "prune";
  if (verb !== "prune") {
    throw new Error(
      `shio assets: unknown sub-command '${verb}'. The only one is \`prune\`. ` +
        "Example: shio assets prune --dry-run",
    );
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const { items, pagesRendered, pagesTotal } = await client.listStaticFilesWithLoads({
    siteId: flags.site ?? undefined,
    folderId: flags.folder ?? undefined,
    loads: true,
  });

  // `usageCount` absent means this instance predates SH1034 and cannot answer. Saying so beats
  // reporting every asset as unused, which is what a `!item.usageCount` test would have done —
  // and the fix it would have invited is a mass deletion.
  const unanswered = items.filter((item) => typeof item?.usageCount !== "number");
  if (items.length && unanswered.length === items.length) {
    throw new Error(
      "shio assets prune: this instance does not report asset usage, so nothing here can tell " +
        "an unused asset from an unasked one. It needs SH1034 (a Shio with `sh_object_reference`); " +
        "until then `GET /api/v2/staticfile/{id}/usage` answers one asset at a time.",
    );
  }

  // SH1280 — a row with no `loadedBy` comes from an instance that does not count page loads; it is
  // judged on references alone, as before, and the run says so rather than passing it off as both.
  const unused = items.filter((item) => item.usageCount === 0 && !(item.loadedBy > 0));
  const loadsUnknown = unused.some((item) => typeof item.loadedBy !== "number");
  const loadsCut = pagesRendered !== null && pagesTotal !== null && pagesRendered < pagesTotal;
  const freed = unused.reduce(
    (total, item) => total + (Number.isFinite(item.size) && item.size > 0 ? item.size : 0),
    0,
  );

  if (flags.json) {
    const payload = {
      scanned: items.length,
      unused: unused.length,
      bytes: freed,
      pagesRendered,
      pagesTotal,
      deleted: false,
      items: unused.map((item) => ({
        id: item.id,
        fileName: item.fileName,
        size: item.size,
        folderName: item.folderName,
        siteName: item.siteName,
      })),
    };
    ctx.stdout.write(`${JSON.stringify(payload)}\n`);
    return payload;
  }

  ctx.stdout.write(
    `assets ${items.length} scanned, ${unused.length} unused, ${formatBytes(freed)} reclaimable` +
      `${pagesRendered === null ? "" : ` (rendered ${pagesRendered} page(s) to know)`}\n`,
  );
  if (loadsUnknown) {
    ctx.stdout.write(
      "note: this instance does not count what rendered pages load (it needs SH1280), so an asset " +
        "a theme or layout loads, such as a font, may be listed. `shio verify --checks content,page` " +
        "withdraws those.\n",
    );
  }
  if (loadsCut) {
    ctx.stdout.write(
      `note: only ${pagesRendered} of ${pagesTotal} page(s) were rendered, so an asset only the ` +
        "rest load may be listed here.\n",
    );
  }
  for (const item of unused) {
    const where = [item.siteName, item.folderName].filter(Boolean).join("/");
    ctx.stdout.write(
      `  ${item.fileName ?? item.id}  ${formatBytes(item.size)}${where ? `  ${where}` : ""}\n`,
    );
  }
  if (unused.length) {
    // The one line that makes this a dry run rather than a report that looks like one. `--delete`
    // is unimplemented on purpose: SH1034's line ships the reading, and a destructive verb is its
    // own task with its own confirmation (P7).
    ctx.stdout.write(
      "nothing was deleted — this is a dry run. Delete with a post.delete op " +
        "(POST /api/v2/agent/batch), or from the media library.\n",
    );
  }
  return {
    scanned: items.length, unused: unused.length, bytes: freed, pagesRendered, pagesTotal, deleted: false,
  };
}
