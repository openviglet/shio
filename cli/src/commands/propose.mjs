/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
 * candidate's `replaces` line says "3 class name(s) of the source's styling", and the theme is where
 * that styling went.
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
  styleBlocks,
  tokenize,
} from "../clone.mjs";
import { ASSETS_DIR } from "../content-assets.mjs";
import {
  KEEP_FLOOR,
  PLAN_FILE,
  PROPOSAL_FILE,
  buildProposal,
  describeIncomplete,
  describeProposal,
  keptShare,
  serializePlan,
  serializeProposal,
} from "../propose.mjs";
import { DEFAULT_MIN_REFS, buildTheme } from "../theme.mjs";
import {
  describeInstanceProposal,
  proposeFromInstances,
} from "../propose-instances.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

/**
 * What `shio propose --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("propose");

export async function run(flags, positionals, ctx) {
  // SH525 — the same verb pointed inward. `propose` derives a post-type from markup nobody wrote
  // for Shio; `--from <address>...` asks the identical question about content the reader wrote:
  // what post-type are these instances of. A flag on this command rather than a verb of its own,
  // because the capability was already built and only its input was hard-wired to a capture.
  const from = addresses(flags.from);
  if (from.length) {
    return proposeFromContent(from, flags, ctx);
  }

  const { inventory, plan, proposal, markup, theme } = await proposalFor(flags, ctx, "propose");

  const accepting = acceptIds(flags, positionals);
  if (accepting.length) {
    const next = accept(proposal, plan, accepting, flags);
    await write(join(ctx.cwd, PLAN_FILE), serializePlan(next), ctx);
    // Recomputed rather than patched, so what the report says is what the file now holds: a summary
    // written from the pre-accept proposal would be one review out of date at the moment it was most
    // likely to be believed.
    const after = buildProposal({
      inventory,
      markup,
      html: { tokenize, parseAttrs, stripNonMarkup },
      plan: next,
      theme,
    });
    return finish(after, flags, ctx, {
      accepted: accepting,
      // SH582 — computed from the proposal `all` was resolved against, so the count the reader sees
      // and the set the plan holds are the same decision rather than two readings of it.
      declined: accepting.includes("all") ? incomplete(proposal) : [],
      out: typeof flags.out === "string" ? flags.out : PROPOSAL_FILE,
    });
  }

  return finish(proposal, flags, ctx, { out: typeof flags.out === "string" ? flags.out : PROPOSAL_FILE });
}

/**
 * The proposal, computed from the tree: one implementation, two commands (SH417).
 *
 * <p>`shio convert` needs the proposal the capture produces **now**, because that is what makes an
 * acceptance's staleness detectable. An id is a hash of the block's structure, so one the current
 * proposal does not hold names markup that is no longer there. Recomputing it in the applier from a
 * second copy of these readers is how the two would eventually disagree about what a candidate is,
 * so the readers stay here beside the command that owns them and the applier calls this.
 *
 * @param {string} command the caller's name, so a refusal names the command the user actually ran
 */
export async function proposalFor(flags, ctx, command = "propose") {
  const inventoryPath = typeof flags.inventory === "string" ? flags.inventory : INVENTORY_FILE;
  const inventory = await readJson(join(ctx.cwd, inventoryPath), ctx);
  if (!inventory) {
    throw new Error(
      `shio ${command}: there is no capture at ${inventoryPath}, so there is nothing to convert.\n` +
        "Capture the source first, and keep its markup — the proposal is computed from it:\n" +
        "  shio clone https://example.com --markup",
    );
  }

  const plan = (await readJson(join(ctx.cwd, PLAN_FILE), ctx)) ?? { accepted: [] };
  const markup = await readMarkup(inventory, ctx);
  // SH427 — the external stylesheets `clone --assets` wrote, *and* the `<style>` blocks in the markup
  // `clone --markup` already stored. The second cost no request and needs no new extractor; leaving it
  // out meant the commonest shape of the page a replication starts from was the shape whose theme was
  // invisible.
  const styles = [...await readStyles(inventory, ctx), ...inlineStyles(inventory, markup)];
  // Either half is a proposal worth having, so the refusal is about having *neither*. A capture with
  // stylesheets and no markup is a real state — `--assets --site X` without `--markup` — and refusing
  // it would withhold the theme over a flag that has nothing to do with the theme.
  if (!markup.size && !styles.length) {
    throw new Error(
      `shio ${command}: ${inventoryPath} holds ${inventory.pages?.length ?? 0} page(s), and neither ` +
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
  return { inventory, plan, proposal, markup, theme };
}

async function finish(proposal, flags, ctx, { out, accepted = [], declined = [] }) {
  await write(join(ctx.cwd, out), serializeProposal(proposal), ctx);
  if (flags.json) {
    ctx.stdout.write(serializeProposal(proposal));
  } else {
    if (accepted.length) {
      ctx.stdout.write(`shio propose: accepted ${accepted.join(" ")} into ${PLAN_FILE}\n`);
      // SH582 — what `all` did NOT take, and why. A shortened set of acceptances reads exactly like
      // a shorter set of candidates (SH138), and the reviewer's next question is whether the block
      // was lost: it was not, it stayed verbatim, which is the sentence this line has to carry.
      if (declined.length) {
        ctx.stdout.write(
          `  ${declined.length} declined — a REQUIRED field the derivation could not fill, so the `
            + "section would be content shio verify refuses. Each stays verbatim markup; accept one "
            + `by id to take it anyway: ${declined.map(describeIncomplete).join("; ")}\n`,
        );
      }
      // SH582 — and the same fact for a candidate somebody named on purpose. Not refused: `--accept
      // <id>` is a reviewer's decision, and P7 asks that it not be a surprise, not that it be denied.
      for (const row of incomplete(proposal).filter((each) => each.mode === "authorable")) {
        ctx.stdout.write(
          `  ${row.id} was accepted with no ${row.missing.join(", ")} — shio verify will report `
            + "required-field-empty on the section it writes\n",
        );
      }
      // SH580 — the acceptance says what it accepted. The proposal already scored every candidate,
      // and `--accept all` is one keystroke that reads none of it: on viglet.org that wrote eleven
      // pages keeping 12 to 35 percent of their text, and the first thing that said so was `shio
      // diff` after the publish. Named rather than refused — a thin block is still a decision a
      // reviewer is entitled to make, and P7 asks only that it not be a surprise.
      for (const row of thin(proposal)) {
        ctx.stdout.write(
          `  ${row.id} keeps ${keptShare(row.keeps)}% of its ${row.keeps.of} characters of text — `
            + `the rest has no field to live in, and the page will read short in shio diff\n`,
        );
      }
    }
    ctx.stdout.write(describeProposal(proposal, { ...flags, out }));
  }
  // A capture with pages but no candidate block is not a failure — a site of one-off pages is a
  // real site, and the answer "nothing here recurs, keep it all verbatim" is the proposal.
  return { proposal, out, exitCode: 0 };
}

/**
 * The accepted candidates whose fields would carry less than half their own text (SH580).
 *
 * <p>Read off `mode` rather than off the ids this run named, and deliberately: `--accept all` passes
 * the literal `all`, and a plan is cumulative — the reviewer's question at this moment is what the
 * plan now holds, not which subset of it today's keystroke added.</p>
 */
function thin(proposal) {
  return proposal.candidates.filter(
    (row) => row.mode === "authorable" && row.keeps?.of && row.keeps.chars < row.keeps.of * KEEP_FLOOR,
  );
}

/**
 * The candidates whose derivation leaves a required field empty (SH582), in id order.
 *
 * <p>Mode-blind on purpose, and the two callers narrow it differently: `--accept all` needs the set
 * it is about to *decline*, which is every one of them, and the acceptance report needs the ones that
 * ended up in the plan anyway, which is the authorable subset. One predicate for "incomplete", so a
 * line that says a block was declined and a line that says it was accepted cannot disagree about
 * which blocks those are.</p>
 */
function incomplete(proposal) {
  return proposal.candidates
    .filter((row) => row.missing?.length)
    .sort((a, b) => (a.id < b.id ? -1 : 1));
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
  // SH582 — `all` declines a candidate whose derivation leaves a required field empty. Measured on
  // `openviglet.github.io`: fifteen of twenty-one accepted, three of them sections with no `heading`,
  // and `shio verify` exiting 1 with three `required-field-empty` errors on a replica that was
  // otherwise the best the chain had produced. An unaccepted block stays verbatim, which is the
  // outcome that keeps the content — so the declined ones are not a smaller conversion, they are the
  // one that does not write what its own gate refuses. Naming an id explicitly still accepts it: that
  // is a reviewer's decision, and P7 asks only that it not be silent.
  const wanted = ids.includes("all")
    ? proposal.candidates.filter((row) => row.type && !row.missing?.length).map((row) => row.id)
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
 * The `<style>` blocks in the captured markup, one entry per page (SH427).
 *
 * <p>All of a page's blocks are joined into one sheet, so the count a reviewer reads is "this page's
 * inline CSS" rather than an arbitrary number of fragments, and the `path` says where the tokens came
 * from. A declared custom property is the token SH380 calls genuinely editable — the source's own CSS
 * already says `var()` on it — so this is the *strong* half of the theme, not a leftover.
 *
 * <p>`url` is the **page**, which is the one thing that differs from an external sheet and the reason
 * it is passed rather than assumed: `url(img/hero.png)` in a `<style>` block resolves against the
 * document, while the same text in `/assets/css/site.css` resolves against that directory. SH380's
 * rule, inverted, and getting it wrong would name a file that does not exist.
 */
function inlineStyles(inventory, markup) {
  const styles = [];
  for (const page of inventory.pages ?? []) {
    const source = markup.get(page.path);
    if (typeof source !== "string") continue;
    const blocks = styleBlocks(source);
    if (!blocks.length) continue;
    styles.push({
      path: `${page.path} <style>`,
      file: null,
      url: absolute(page.path, inventory.source?.origin),
      css: blocks.join("\n"),
      inline: true,
    });
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


/** `--from a --from b` and `--from a,b` are the same list; an agent writes whichever it has. */
function addresses(raw) {
  if (!raw) return [];
  const values = Array.isArray(raw) ? raw : [raw];
  return values
    .flatMap((value) => String(value).split(","))
    .map((value) => value.trim())
    .filter(Boolean);
}

/**
 * Model from instances that already exist (SH525).
 *
 * <p>Reads through the agent surface rather than the database, so what it models from is what an
 * agent would read — a proposal computed from rows nobody could fetch would be a proposal about a
 * different instance. `--name` is the type to propose; without one the report is still worth
 * reading, so it defaults rather than refusing.</p>
 */
async function proposeFromContent(from, flags, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const docs = await client.read(from, {});
  const instances = (Array.isArray(docs) ? docs : [])
    .filter((doc) => doc && !doc.error)
    .map((doc) => ({ address: doc.address, data: doc.data ?? {} }));

  const missing = from.filter((address) => !instances.some((row) => row.address === address));
  if (missing.length) {
    // Named rather than silently modelled around: a proposal computed from two of the three
    // addresses somebody passed is a proposal about a set they did not ask about (P6).
    ctx.stderr.write(`shio propose: could not read ${missing.join(", ")}
`);
  }

  const proposal = proposeFromInstances(instances, typeName(flags, instances));
  ctx.stdout.write(describeInstanceProposal(proposal));
  return proposal ? 0 : 1;
}

/** The proposed type's name: what was asked for, else the shared type of the instances read. */
function typeName(flags, instances) {
  if (typeof flags.name === "string" && flags.name.trim()) return flags.name.trim();
  const types = [...new Set(instances.map((row) => row.type).filter(Boolean))];
  return types.length === 1 ? types[0] : "ProposedType";
}
