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
 */
import { dirname, join } from "node:path";

import {
  INVENTORY_FILE,
  MARKUP_DIR,
  parseAttrs,
  stripNonMarkup,
  tokenize,
} from "../clone.mjs";
import {
  PLAN_FILE,
  PROPOSAL_FILE,
  buildProposal,
  describeProposal,
  serializePlan,
  serializeProposal,
} from "../propose.mjs";

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
  if (!markup.size) {
    throw new Error(
      `shio propose: ${inventoryPath} holds ${inventory.pages?.length ?? 0} page(s) and none of ` +
        `them has markup in ${MARKUP_DIR}/.\n` +
        "The capture was taken without --markup, which is the flag that keeps the HTML a mapping is\n" +
        "derived from. Re-run the capture; it costs the same requests:\n" +
        `  shio clone ${inventory.source?.seed ?? "https://example.com"} --markup`,
    );
  }

  const proposal = buildProposal({
    inventory,
    markup,
    html: { tokenize, parseAttrs, stripNonMarkup },
    plan,
  });

  const accepting = acceptIds(flags, positionals);
  if (accepting.length) {
    const next = accept(proposal, plan, accepting, flags);
    await write(join(ctx.cwd, PLAN_FILE), serializePlan(next), ctx);
    // Recomputed rather than patched, so what the report says is what the file now holds — a
    // summary written from the pre-accept proposal would be one review out of date at the moment it
    // was most likely to be believed.
    const after = buildProposal({ inventory, markup, html: { tokenize, parseAttrs, stripNonMarkup }, plan: next });
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
