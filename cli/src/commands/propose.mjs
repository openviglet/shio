/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio propose` (SH379) — replication's convert phase, as a proposal nobody has to trust.
 *
 * ## It reads the tree and nothing else
 *
 * No instance, no network, no browser. The inventory and the markup cache are both on disk after
 * `shio clone --markup`, so this command is reproducible, diffable and free to re-run — and a
 * proposal that re-fetched would change under the reviewer depending on what the source served this
 * morning, which for an artefact whose whole job is to be argued with is disqualifying.
 *
 * ## `--accept` is the appliable unit, and it applies nothing
 *
 * Accepting a candidate writes one entry into `shio/conversion-plan.json`: this block, as this
 * section type. It writes no post, no post-type and no layout — those are the *apply*, and putting
 * them here would make review and mutation the same command, which is the surprise P7 forbids. The
 * plan is the reviewed half of the work; an unaccepted block stays in fidelity mode, which is a
 * decision and not an omission.
 *
 * ## Two halves, one artefact (SH380)
 *
 * The blocks are one question and the **theme** is the other, and they are read together: a
 * candidate's loss line says "3 class names — all styling", and the theme is where that styling went.
 * So the stylesheets the SH378 download put in the tree are scanned here too, and the proposal carries
 * the DTCG token set beside the candidates rather than in a second file nobody would think to open.
 * Either half alone is a proposal worth having, which is why the refusal below is about having
 * **neither** — a capture taken with `--assets` and without `--markup` is a real state, and withholding
 * the theme over a flag that has nothing to do with the theme would be the wrong answer to it.
 */
import { dirname, join } from "node:path";

import {
  INVENTORY_FILE,
  MARKUP_DIR,
  parseAttrs,
  stripNonMarkup,
  tokenize,
} from "../clone.mjs";
import { ASSETS_DIR } from "../content-assets.mjs";
import {
  PLAN_FILE,
  PROPOSAL_FILE,
  buildProposal,
  describeProposal,
  serializePlan,
  serializeProposal,
} from "../propose.mjs";
import { DEFAULT_MIN_REFS, buildTheme } from "../theme.mjs";

/**
 * What `shio propose --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio propose [ids…] — which captured blocks could become sections, and what each loses

Reads the capture and nothing else: no instance, no network, no browser. Writes a proposal
to shio/.shio/conversion-proposal.json; --accept writes the reviewed decision to
shio/conversion-plan.json and applies nothing.

  --accept <id…>       Accept the candidates these ids name, or all. The ids are
                       positionals, so --accept itself is a boolean — one flag whose arity
                       differs per command is how --accept --reason x b1a2 silently accepts
                       a candidate called --reason
  --reason <why>       With --accept: why, recorded beside the entry
  --min-refs <n>       How often a literal must recur to be a palette value (default 3; 2 is
                       the floor — at 1 every colour becomes a token and the list stops
                       meaning anything)
  --inventory <file>   Read a capture from somewhere else
  --out <file>         Proposal path (default shio/.shio/conversion-proposal.json)
  --json               The proposal on stdout instead of the report

Examples:
  shio propose
  shio propose --accept b1a2c3d4 --reason "the hero is the same on every page\"
`;

export async function run(flags, positionals, ctx) {
  const inventoryPath = typeof flags.inventory === "string" ? flags.inventory : INVENTORY_FILE;
  const inventory = await readJson(join(ctx.cwd, inventoryPath), ctx);
  if (!inventory) {
    throw new Error(
      `shio propose: there is no capture at ${inventoryPath}, so there is nothing to convert.\n` +
        "Capture the source first, and keep its markup — the proposal is computed from it:\n" +
        "  shio clone https://example.com --markup",
    );
  }

  const plan = (await readJson(join(ctx.cwd, PLAN_FILE), ctx)) ?? { accepted: [] };
  const markup = await readMarkup(inventory, ctx);
  const styles = await readStyles(inventory, ctx);
  // Either half is a proposal worth having, so the refusal is about having *neither*. A capture with
  // stylesheets and no markup is a real state — `--assets --site X` without `--markup` — and refusing
  // it would withhold the theme over a flag that has nothing to do with the theme.
  if (!markup.size && !styles.length) {
    throw new Error(
      `shio propose: ${inventoryPath} holds ${inventory.pages?.length ?? 0} page(s), and neither ` +
        `their markup (${MARKUP_DIR}/) nor their stylesheets (${ASSETS_DIR}/) are in the tree.\n` +
        "The capture was taken without the flags that keep them. Re-run it; the markup costs the same\n" +
        "requests it already made, and the bytes are what a theme is:\n" +
        `  shio clone ${inventory.source?.seed ?? "https://example.com"} --markup --assets --site <name>`,
    );
  }

  const theme = styles.length
    ? buildTheme({ styles, assets: assetMap(inventory), minRefs: minRefs(flags) })
    : null;
  const proposal = buildProposal({
    inventory,
    markup,
    html: { tokenize, parseAttrs, stripNonMarkup },
    plan,
    theme,
  });

  const accepting = acceptIds(flags, positionals);
  if (accepting.length) {
    const next = accept(proposal, plan, accepting, flags);
    await write(join(ctx.cwd, PLAN_FILE), serializePlan(next), ctx);
    // Recomputed rather than patched, so what the report says is what the file now holds — a
    // summary written from the pre-accept proposal would be one review out of date at the moment it
    // was most likely to be believed.
    const after = buildProposal({
      inventory,
      markup,
      html: { tokenize, parseAttrs, stripNonMarkup },
      plan: next,
      theme,
    });
    return finish(after, flags, ctx, {
      accepted: accepting,
      out: typeof flags.out === "string" ? flags.out : PROPOSAL_FILE,
    });
  }

  return finish(proposal, flags, ctx, { out: typeof flags.out === "string" ? flags.out : PROPOSAL_FILE });
}

async function finish(proposal, flags, ctx, { out, accepted = [] }) {
  await write(join(ctx.cwd, out), serializeProposal(proposal), ctx);
  if (flags.json) {
    ctx.stdout.write(serializeProposal(proposal));
  } else {
    if (accepted.length) {
      ctx.stdout.write(`shio propose: accepted ${accepted.join(" ")} into ${PLAN_FILE}\n`);
    }
    ctx.stdout.write(describeProposal(proposal, { ...flags, out }));
  }
  // A capture with pages but no candidate block is not a failure — a site of one-off pages is a
  // real site, and the answer "nothing here recurs, keep it all verbatim" is the proposal.
  return { proposal, out, exitCode: 0 };
}

/**
 * The candidates `--accept` names.
 *
 * <p>`--accept` stays the **boolean** it is in `shio verify`, and the ids are positionals. One flag
 * whose arity differs per command is SH365's defect in miniature — a caller who learned `verify
 * --accept` takes nothing would write `shio propose --accept --reason "x" b1a2` and, with a
 * value-taking flag, silently accept a candidate called `--reason`. `all` is spelled out rather than
 * implied by a bare `--accept`, because converting a whole site is a different act from converting
 * one block and the command line should not be able to confuse them by omission.
 */
function acceptIds(flags, positionals = []) {
  if (flags.accept !== true) return [];
  const ids = positionals.map((each) => String(each).trim()).filter(Boolean);
  if (!ids.length) {
    throw new Error(
      "shio propose: --accept needs the candidate to accept, or `all`.\n" +
        "Run shio propose first and read the ids; each one names a block, the fields it would carry\n" +
        "and what it would lose — accepting without reading is accepting a change you have not seen.\n" +
        "  shio propose --accept b1a2c3d4        one block\n" +
        "  shio propose --accept b1a2c3d4 bff01  several\n" +
        "  shio propose --accept all             every candidate the vocabulary matched",
    );
  }
  return ids;
}

/**
 * Folds the accepted ids into the plan.
 *
 * <p>An id the proposal does not hold is refused with what the proposal *does* hold, per P6: the
 * commonest way to get here is a stale id from a run taken before the source changed, and silently
 * writing it would leave an entry naming a block nothing can apply. A block the vocabulary matched
 * no type for is refused too, and separately — its answer is SH379's new-post-type path, and
 * accepting it as a section is the "forced into the nearest wrong shape" outcome the rule forbids.
 */
function accept(proposal, plan, ids, flags) {
  const byId = new Map(proposal.candidates.map((row) => [row.id, row]));
  const wanted = ids.includes("all")
    ? proposal.candidates.filter((row) => row.type).map((row) => row.id)
    : ids;
  const accepted = [...(plan.accepted ?? [])];
  const held = new Set(accepted.map((entry) => entry.block));

  for (const id of wanted) {
    const row = byId.get(id);
    if (!row) {
      throw new Error(
        `shio propose: no candidate ${id} in this proposal.\n` +
          `The ids here are: ${proposal.candidates.map((each) => each.id).slice(0, 12).join(" ")}` +
          `${proposal.candidates.length > 12 ? " …" : ""}\n` +
          "An id is a hash of the block's structure, so one from an older run is gone because the\n" +
          "source's markup changed — re-read the proposal before accepting.",
      );
    }
    if (!row.type) {
      throw new Error(
        `shio propose: candidate ${id} matches no section type, so it cannot be accepted as one.\n` +
          `It needs a post-type of its own — the proposal names it ${row.postType.name} with the\n` +
          "fields it would carry. Author that through @viglet/shio-model and the block maps onto it;\n" +
          "forcing it into the nearest section is how a conversion loses content nobody notices.",
      );
    }
    if (held.has(id)) continue;
    accepted.push({ block: id, type: row.type, ...(flags.reason ? { reason: String(flags.reason) } : {}) });
    held.add(id);
  }
  return { accepted };
}

/**
 * The markup cache, keyed by the page path the inventory records.
 *
 * <p>A page row naming a file that is not there is skipped rather than fatal: the cache is derived
 * and a reviewer may well have deleted it, and `buildProposal` reports the count of pages it could
 * not read. Refusing the whole run for one missing file would make the artefact hostage to the
 * cheapest thing in the tree to lose.
 */
async function readMarkup(inventory, ctx) {
  const out = new Map();
  for (const page of inventory.pages ?? []) {
    if (!page.markup) continue;
    try {
      out.set(page.path, await ctx.fs.readFile(join(ctx.cwd, MARKUP_DIR, page.markup), "utf8"));
    } catch {
      // Counted by the proposal's own note, which says how many pages are missing and why.
    }
  }
  return out;
}

/**
 * The stylesheets SH378 downloaded, in inventory order (SH380).
 *
 * <p>Only the ones with a `file`: an inventory row without one is a stylesheet the download skipped
 * or never ran for, and there are no bytes to read. `url` is carried through because a `url()` inside
 * the file resolves against the **stylesheet**, not against the page that linked it — the single
 * commonest defect in anything that touches CSS urls.
 */
async function readStyles(inventory, ctx) {
  const styles = [];
  for (const asset of inventory.assets ?? []) {
    if (asset.kind !== "stylesheet" || !asset.file || asset.external) continue;
    try {
      styles.push({
        path: asset.path,
        file: asset.file,
        url: absolute(asset.path, inventory.source?.origin),
        css: await ctx.fs.readFile(join(ctx.cwd, ASSETS_DIR, asset.file), "utf8"),
      });
    } catch {
      // Named by the theme's own sheet count against the inventory's, the way a missing markup file
      // is. A run refused for one deleted derived file would be hostage to the cheapest thing to lose.
    }
  }
  return styles;
}

/**
 * Source path → tree path, for the `url()` rewrite.
 *
 * <p>Keyed on `path` — the field the inventory records and the field a resolved `url()` produces —
 * and it must be **one** map shared with whatever else reads it, per SH378's own rule: a second copy
 * would rewrite a reference to a path holding somebody else's bytes.
 */
function assetMap(inventory) {
  const map = new Map();
  for (const asset of inventory.assets ?? []) {
    if (asset.file) map.set(asset.path, asset.file);
  }
  return map;
}

function absolute(path, origin) {
  if (!origin) return null;
  try {
    return new URL(path, origin).href;
  } catch {
    return null;
  }
}

function minRefs(flags) {
  if (flags.minRefs === undefined || flags.minRefs === true) return DEFAULT_MIN_REFS;
  const parsed = Number(flags.minRefs);
  if (!Number.isInteger(parsed) || parsed < 2) {
    throw new Error(
      `shio propose: --min-refs takes a whole number of 2 or more, not "${flags.minRefs}".\n` +
        "It is how many times a literal must appear before the capture calls it a palette value; at 1\n" +
        "every colour on the site becomes a token and the list stops meaning anything.",
    );
  }
  return parsed;
}

async function readJson(path, ctx) {
  let raw;
  try {
    raw = await ctx.fs.readFile(path, "utf8");
  } catch {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(`shio propose: ${path} is not readable JSON (${error?.message ?? error}).`);
  }
}

async function write(path, body, ctx) {
  await ctx.fs.mkdir(dirname(path), { recursive: true });
  await ctx.fs.writeFile(path, body, "utf8");
}
