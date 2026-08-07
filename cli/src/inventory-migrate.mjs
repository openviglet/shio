/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH592 — a version bump on the inventory is a migration or a refusal, and a refusal is honest only
 * when the new field genuinely cannot be computed from what is on disk.
 *
 * <h2>The asymmetry this is about</h2>
 *
 * <p>`shio clone` over `vilt-group.com` with `--render`: 198 pages, 403 assets, 52.7 MB, about ten
 * minutes. It is by far the most expensive step in the chain and the only one whose output was
 * immutable — everything after it reads the inventory off disk and runs in seconds. Twice in this
 * work that cost a full recapture for a reason that had nothing to do with the source. SH578
 * changed the inventory's asset shape and `diff` refused every capture written before it, with an
 * excellent error naming the version and the remedy — which is the right refusal and does not make
 * the remedy cheaper. The other time the source had not changed at all; a downstream one-line fix
 * needed a capture the new reader could parse.</p>
 *
 * <h2>The information was already in the file</h2>
 *
 * <p>SH578 split `assets` into `{own, style}` and SH581 added `head`, and <b>all three are derivable
 * from the stored markup</b> — which `shio clone --markup` keeps beside the inventory precisely so
 * `propose` can read it. So the refusal was never about missing information; it was about nobody
 * having written the derivation. That is the cost of a reader's change being paid in somebody
 * else's ten minutes.</p>
 *
 * <h2>What is deliberately still refused</h2>
 *
 * <p>Version 1 → 2 added `unfollowed` and the `problems` list, and neither is derivable: the crawl
 * did not record what it declined to follow, and an absent list read as "nothing was left
 * uncrawled" is exactly the wrong way round for a coverage report. A migration that guessed there
 * would turn a bounded sample into a report that reads as complete — which is the failure SH431 is
 * about. So this migrates what it can compute and refuses the rest <i>by name</i>, and the refusal
 * says which field and why, rather than naming a version number and stopping.</p>
 *
 * <p>Pure: the caller supplies the markup, because reading it is IO and the derivation is not.</p>
 */
import { countAssets, INVENTORY_VERSION, scanPage } from "./clone.mjs";

/**
 * The first version whose rows can be brought forward.
 *
 * <p>Three, because version 3 is where `assets` became a number that the current readers need split
 * — and the split is computable. Below that the missing facts are about the *crawl* rather than
 * about a page, and a crawl is not on disk.</p>
 */
export const MIGRATABLE_FROM = 3;

/**
 * Brings an inventory forward to {@link INVENTORY_VERSION}, or says exactly why it cannot.
 *
 * @param {object} inventory the parsed capture
 * @param {Map<string, string>} markup path → the markup this capture stored for that page. A map
 *   rather than a reader, because reading is IO and the derivation is not: keeping this pure is
 *   what lets the rule be asserted without a disk.
 * @returns {{inventory: object, migrated: string[], from: number, blocked: string[]}}
 *   `migrated` names the pages brought forward; `blocked` names the pages whose markup is not on
 *   disk, which is a page this cannot derive and never a page it quietly scores.
 */
export function migrateInventory(inventory, markup = new Map()) {
  const from = Number(inventory?.version ?? 0);
  if (!Number.isFinite(from) || from >= INVENTORY_VERSION) {
    return { inventory, migrated: [], from, blocked: [] };
  }
  if (from < MIGRATABLE_FROM) {
    throw new Error(unmigratable(from));
  }

  const migrated = [];
  const blocked = [];
  const pages = (inventory.pages ?? []).map((page) => {
    if (typeof page?.assets?.own === "number" && typeof page.assets?.head === "number") {
      return page;
    }
    const html = markup.get(page?.path);
    if (!html) {
      blocked.push(page?.path ?? "(unnamed)");
      return page;
    }
    // The same functions the capture ran, against the same bytes the capture stored. Nothing is
    // estimated: `countAssets` is where the three-way split is defined, so a migrated row and a
    // freshly captured one are computed by one rule and cannot disagree (P5, SH245's lesson).
    const doc = scanPage(html, page.url ?? page.path ?? "/");
    migrated.push(page.path);
    return { ...page, assets: countAssets(doc.assets) };
  });

  return {
    inventory: { ...inventory, version: INVENTORY_VERSION, pages, migratedFrom: from },
    migrated,
    from,
    blocked,
  };
}

/** What the caller is told when a page's markup is not on disk — a fact, never a score. */
export function describeMigration({ from, migrated, blocked }) {
  if (!migrated.length && !blocked.length) return "";
  const lines = [
    `shio: migrated this capture from version ${from} to ${INVENTORY_VERSION} — `
      + `${migrated.length} page(s) re-read from the markup it already stored, not re-crawled`,
  ];
  if (blocked.length) {
    lines.push(
      `  ${blocked.length} page(s) have no stored markup, so their asset counts could not be `
        + "derived and are left as they were: " + blocked.slice(0, 5).join(", ")
        + (blocked.length > 5 ? ` … +${blocked.length - 5}` : ""),
      "  a capture taken without --markup keeps no bytes to re-read. `shio clone <url> --markup`"
        + " is what makes a capture migratable at all",
    );
  }
  return `${lines.join("\n")}\n`;
}

/**
 * The refusal for a version whose gap is genuinely not on disk.
 *
 * <p>It names the *field* rather than the version, because "your file is version 2" tells a reader
 * nothing they can act on beyond re-running a ten-minute crawl, while "the crawl did not record
 * what it declined to follow" tells them why no amount of cleverness here would help.</p>
 */
function unmigratable(from) {
  return (
    `shio: this capture is version ${from}, and the facts it is missing are about the CRAWL rather `
      + "than about a page (SH592).\n"
      + "  missing: `unfollowed` — which links the crawl declined to follow — and the `problems`\n"
      + "           list. Neither is in the stored markup, because neither is a property of a page.\n"
      + "  why not derive them: an absent `unfollowed` read as an empty one says `nothing was left\n"
      + "           uncrawled`, which is the wrong way round for a coverage report — it turns a\n"
      + "           bounded sample into a report that reads as complete.\n"
      + "  fix: shio clone <url> --markup\n"
      + `  note: a version ${MIGRATABLE_FROM}+ capture IS migrated in place, from the markup it\n`
      + "        already stored. This refusal is the case where that is not possible, not a policy."
  );
}
