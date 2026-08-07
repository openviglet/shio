/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH387 — the gate that says when a replication is **done**.
 *
 * WHY THIS IS A GROUP AND NOT FIVE MORE PROJECTION RULES
 * SH212's `projection` group answers questions about the tree *alone*: is the asset this field names
 * on disk, does this `translationOf` resolve. Every rule here needs a **second document** — the SH377
 * capture — and the question it answers is arithmetic rather than structural: of the pages, links,
 * assets, forms and locales the source had, which ones exist here. That is a different claim about a
 * different subject, so `--checks replication` selects it and `--checks projection` does not. Naming
 * either one alone still skips the server entirely, which is the property that makes both affordable
 * on every push.
 *
 * WHY IT IS THE CHEAPEST GATE IN THE PIPELINE
 * The inventory is a file and the content is a file. No instance, no credentials, no network — so a
 * replication's completeness is provable before anything is stood up, which is where a check this
 * cheap belongs (P9).
 *
 * THE ONE RULE THAT MATTERS MOST, AND IT IS NOT A COVERAGE RULE
 * A page, asset or locale the group could **not** check is a warning that names itself. An unchecked
 * thing counted as a clean one is how a coverage report starts lying, and a coverage report nobody can
 * trust is worse than none — it ends the replication early with a number instead of with the work.
 * SH302's rule, and the reason `replication-unchecked` exists.
 *
 * WHAT MOVED HERE FROM SH385
 * `source-url-abandoned` was the right check filed in the wrong group: it is a coverage question about
 * a source path, which is this group's whole subject. It is `source-page-missing` now. One defect, one
 * rule, one report — the alternative was two rules over one fact, which is two reports that can
 * disagree.
 *
 * WHERE A CHROME ASSET LANDS, WHICH SH441 HAD TO SETTLE BEFORE IT COULD ADVISE ANYTHING
 * SH429 fired correctly on `/viglet-logo.png` and told the operator to point a `File` field at it.
 * There was no field to point: the logo is in `div.topbar` and in `<link rel="icon">`, both chrome,
 * and `contentRoot` excludes chrome on purpose — so a conversion strands a favicon or a wordmark **by
 * construction** and the fix read as though the operator had forgotten something. Two answers were
 * open, and **SH437 had already closed one of them**: a static file keeping the source's own path is
 * not available, because Shio serves a File post's bytes from `/api/v2/staticfile/{id}` and
 * `/file_source/<postId>/<fileName>` — both id-keyed — and nothing maps a source path to a `File`
 * post (`ShStaticFileService.upload` never assigns a friendly URL, which is why SH172 needed the
 * `id:` fallback). Choosing it would mean building a delivery route the instance does not have: a
 * feature, not a correction to a sentence. So it is **the site-level one**, which is also where this
 * product already puts the other two kinds of chrome: SH389 made a site's third-party scripts one
 * ordered list the site owns, SH382 made the curated menu content. `source-chrome-stranded` says
 * that, and says what will *not* work — because "serve it at the original path" is the answer an
 * operator reaches for first, and SH437's finding is not somewhere they would think to look.
 */
import { BEHAVIOUR_SKIPPED } from "./clone-assets.mjs";

/** The group name, in `--checks` and in the report's own `checks` list. */
export const REPLICATION_CHECK = "replication";

/** Enough rows to act on, few enough to read. Every cut is declared in the message that carries it. */
const LIMIT = 20;

/**
 * The inventory shape this group understands. A `version` past it is reported, never guessed at.
 *
 * <p>**3 since SH431**: a non-HTML 200 is its own list in the capture rather than a row in
 * `problems`. Reading a version-2 file with this reader is safe and under-informed in one specific
 * way, which the note below states rather than leaving to be inferred — the data files are still in
 * `problems` there, so `replication-unchecked` over-reports on exactly the sites that have a data
 * surface.
 *
 * <p>**4 since SH578**, and it adds no rule here — which is why there is no `version < 4` note
 * below to pair with the two that are. That bump split a page row's `assets` into `{own, style}`
 * for `shio diff`, and this group reads a page row's `path` and the capture's top-level `assets`
 * array and nothing else. It is tracked anyway because the alternative is worse than silence: a
 * reader left at 3 answers `version > READS_VERSION` on every capture `shio clone` now writes, and
 * warns that the run is under-informed about a field it never reads.</p>
 */
export const READS_VERSION = 4;

/**
 * Lints a replication's coverage against the capture it came from.
 *
 * @param {object} input
 * @param {object[]} input.posts   the projected posts (`readLocal`'s values)
 * @param {object[]} input.defs    local post-type defs (`readLocalDefs`)
 * @param {Iterable<string>} input.assets asset paths on disk
 * @param {object} [input.inventory] the SH377 capture; absent means there is nothing to compare
 * @param {boolean} [input.scoped] whether the run covers only part of the tree
 * @returns {{ findings: object[], checked: number, note: string|null }}
 */
export function lintReplication({ posts = [], defs = [], assets = [], inventory = null, scoped = false }) {
  if (!inventory || typeof inventory !== "object") {
    // Not a finding and not silence: a project that never cloned anything has no replication to be
    // complete, and saying "0 findings" would be a claim about a comparison that never happened.
    return { findings: [], checked: 0, note: `${REPLICATION_CHECK}: no capture in this tree, so there `
      + "is nothing to compare — run `shio clone <url>` first" };
  }
  if (scoped) {
    // Every rule here is an **absence**, and SH143's rule bites hardest for those: a run that read
    // part of the tree cannot tell a page the replica lacks from a page it did not look at.
    return { findings: [], checked: 0, note: `${REPLICATION_CHECK}: skipped — a scoped run cannot `
      + "prove an absence, and every rule in this group is one" };
  }

  const findings = [];
  const notes = [];
  const served = servedPaths(posts);
  const declared = declaredFields(defs);
  const present = new Set(assets);

  findings.push(
    ...pageFindings(inventory, served),
    ...linkFindings(inventory, served),
    ...assetFindings(inventory, present, referencedAssets(posts)),
    ...formFindings(inventory, declared),
    ...localeFindings(inventory, posts, notes),
    ...uncheckedFindings(inventory, notes),
  );

  const checked = (inventory.pages?.length ?? 0) + (inventory.assets?.length ?? 0)
    + (inventory.forms?.length ?? 0) + (inventory.locales?.length ?? 0);
  return {
    findings: sortFindings(findings),
    checked,
    note: notes.length ? `${REPLICATION_CHECK}: ${notes.join("; ")}` : null,
  };
}

/**
 * Every path the replica answers — a page's own URL, or a `Redirect` posted at it.
 *
 * <p>One set for both, because a redirect *is* a post whose `url` is the path it answers (SH385): the
 * design decision not to give a redirect a `from` field is what makes "served or redirected" a single
 * lookup here rather than two lists to keep in step.
 */
function servedPaths(posts) {
  const served = new Set();
  for (const post of posts) {
    if (typeof post?.url === "string" && post.url) served.add(normalizePath(post.url));
  }
  return served;
}

/**
 * A source page the replica neither serves nor redirects — the rule SH385 filed as
 * `source-url-abandoned` before this group existed to hold it.
 *
 * <p>A **warning**: deciding an old path does not deserve a page or a redirect is legitimate, and a
 * replica may genuinely drop a section. This says "you have not said what happens to these", which
 * only the operator can answer.
 */
function pageFindings(inventory, served) {
  const missing = pathsOf(inventory.pages).filter((path) => !served.has(normalizePath(path)));
  return capped(missing, "source-page-missing", inventory, (path, cut) => ({
    message: `the capture served \`${path}\` and this tree neither holds a page there nor redirects it${cut}`,
    fix: "Give the replica a page at that URL, or a Redirect post whose friendly URL is that path "
      + "and whose TO names where it went. Deciding a path does not deserve either is a real answer "
      + "— this is a warning because only you can give it."
      // SH433 — the root is the one path whose address nobody can spell from what is written down,
      // and it is the first row this rule reports on every replication. A fix that says "give the
      // replica a page at that URL" and leaves the caller to discover `post:<site>/` by probing a
      // live instance is the session P2 exists to remove.
      + (normalizePath(path) === "/"
        ? " The home page is addressed `post:<site>/` — a post whose friendly URL is `/`, which is "
          + "what the renderer looks for and what /preview/<site>/ then serves."
        : ""),
  }));
}

/**
 * An internal link the source served that the capture never crawled and the replica does not hold.
 *
 * <h3>Why it is not "every internal link"</h3>
 *
 * Every link the crawl <b>followed</b> is a row in `pages`, so `source-page-missing` already asks
 * about it — reporting it here as well would put one gap in two rules, which is two reports that can
 * disagree. What is left is the set neither list covers: a target cut by `--depth`, by `--max-pages`
 * or by robots.txt. Before SH387 the inventory did not record it at all, so a fidelity report counted
 * it as absent from the *source* rather than absent from the *capture* — opposite claims.
 */
function linkFindings(inventory, served) {
  const rows = Array.isArray(inventory.unfollowed) ? inventory.unfollowed : [];
  const missing = rows
    .map((row) => (typeof row?.path === "string" ? row.path.split("?")[0] : ""))
    .filter(Boolean)
    .filter((path) => !served.has(normalizePath(path)));
  return capped(missing, "source-link-unresolved", inventory, (path, cut) => ({
    message: `the source linked to \`${path}\`, the capture never crawled it, and this tree does `
      + `not hold it either${cut}`,
    fix: "Raise --depth or --max-pages and capture again, or accept that the source's own link goes "
      + "nowhere in the replica. This is the one rule about a gap in the CAPTURE rather than in the "
      + "replica, which is why it is worth telling apart from source-page-missing.",
  }));
}

/**
 * The asset kinds that belong to the site rather than to anything on a page (SH441).
 *
 * <h3>Why `kind` is the whole classifier, and what it therefore misses</h3>
 *
 * <p>An `icon` is a `<link rel="icon">` and a `manifest` is a `<link rel="manifest">`: both live in
 * `<head>`, outside every content root, and no block will ever declare a field for one. That is a
 * certainty rather than a heuristic, which is what makes it safe to give them different advice.</p>
 *
 * <p>A header logo in `div.topbar` is chrome by the same argument and arrives here as `image`, and
 * the capture cannot say otherwise: assets are deduped by URL keeping the first `kind` seen, so the
 * one that is *also* a favicon lands as `icon` and the one that is only a wordmark does not. This
 * set therefore <b>under-reports chrome</b> — the same lower-bound stance `referencedAssets` takes
 * one function down, and the reason `source-asset-stranded` names the site-level case too instead
 * of leaving it to this rule to have caught.</p>
 */
const CHROME_KINDS = new Set(["icon", "manifest"]);

/**
 * A same-origin asset the capture named that the tree does not hold.
 *
 * <p>Two shapes, one finding: an asset with no `file` was never downloaded (`--assets` did not run,
 * or it was skipped), and one with a `file` that is not on disk was downloaded and then lost. The
 * message says which, because the fixes are different commands. **Off-origin assets are not
 * reported** — SH378 settled that mirroring another host's bytes is a licensing decision this tool
 * does not make, so their absence is the designed outcome rather than a gap.
 */
function assetFindings(inventory, present, referenced) {
  const rows = (inventory.assets ?? []).filter((asset) => asset && !asset.external);
  const missing = [];
  const stranded = [];
  const chrome = [];
  for (const asset of rows) {
    // SH473 — a script the operator did not ask for is not a gap. Now that a `<script src>` is an
    // asset row, `--assets` without `--scripts` leaves one behind on purpose, and the download's own
    // note already says so in behaviour's terms. Reporting it here as "was never downloaded", under
    // a fix that names `--assets`, would be a second report of one fact with the wrong instruction
    // on it (P6). A script that *was* taken and then lost from the tree still lands below.
    if (asset.unavailable === BEHAVIOUR_SKIPPED) continue;
    if (!asset.file) {
      missing.push({ path: asset.path, why: "was never downloaded" });
    } else if (!present.has(asset.file)) {
      missing.push({ path: asset.path, why: `was downloaded to \`${asset.file}\`, which is not in the tree` });
    } else if (!referenced.has(asset.file)) {
      (CHROME_KINDS.has(asset.kind) ? chrome : stranded).push(asset);
    }
  }
  return [
    ...capped(missing, "source-asset-missing", inventory, (row, cut) => ({
      message: `the asset \`${row.path}\` ${row.why}${cut}`,
      fix: "Run `shio clone <url> --assets --site <name>` to fetch the bytes, or `shio pull --content` "
        + "if the asset is in the instance and not in this tree.",
    }), (row) => row.path),
    ...capped(stranded, "source-asset-stranded", inventory, (row, cut) => ({
      message: `the asset \`${row.path}\` is in the tree as \`${row.file}\` and no post references `
        + `it, so nothing serves it${cut}`,
      fix: "An asset reaches the instance because a File field names its path (SH145) - the bytes "
        + "alone are never uploaded. Point a field at it and `shio push --content` sends it. A block "
        + "left in FIDELITY mode has no fields at all, so its images stay here: that is the delivery "
        + "half a replica needs, and it is not the download. "
        // SH441 — the escape hatch, on this rule too. `kind` classifies a favicon with certainty and
        // a header logo not at all, so an operator holding one of the ones this rule still catches
        // has to be told that "point a field at it" is not always available.
        + "If this one is site chrome — a wordmark or a header logo, which belongs to no block and "
        + "so to no block's field — it is the site-level case below: declare a site-level post-type "
        + "with a File field and point that at it.",
    }), (row) => row.path),
    ...capped(chrome, "source-chrome-stranded", inventory, (row, cut) => ({
      message: `the chrome asset \`${row.path}\` (${row.kind}) is in the tree as \`${row.file}\` and `
        + `no field can ever name it, so nothing uploads it${cut}`,
      fix: "This is site chrome, so it is site-level content, the way SH389 models a site's scripts "
        + "and SH382 its menu: declare a site-level post-type with a File field (`shio push` sends "
        + "the type), point it at this path, and `shio push --content` sends the bytes. Deciding the "
        + "replica does not need it and deleting the bytes is the other real answer. What will NOT "
        + "work is serving it at the source's own path: Shio delivers a File post from "
        + "`/api/v2/staticfile/{id}` and `/file_source/<id>/<name>`, both id-keyed, and a File post "
        + "is never given a friendly URL — so verbatim markup pointing at the original path stays "
        + "broken until the markup is rewritten.",
    }), (row) => row.path),
  ];
}

/**
 * Every asset path a post's File field names - the readers of the bytes on disk (SH429).
 *
 * <p>Read from the post data rather than from the post-types, because a projected File field holds
 * the asset's **path** (SH90) and that is the same string the capture's `file` is. A value that is
 * not path-shaped is an id already, which is a reference this tree never projected and cannot be
 * matched here; it is left out rather than guessed at, so this set is a lower bound and the rule
 * built on it under-reports rather than inventing a gap.
 */
function referencedAssets(posts) {
  const referenced = new Set();
  for (const post of posts ?? []) {
    for (const value of Object.values(post?.data ?? {})) {
      if (typeof value === "string" && value.includes("/")) referenced.add(value);
    }
  }
  return referenced;
}

/**
 * A form field the source had that no post-type in this tree declares.
 *
 * <h3>The limit of the claim, stated because it is easy to over-read</h3>
 *
 * There is no form storage in this product — SH247 renders a post-type's *declaration* and the
 * receiving half was deliberately not built — so "the field arrived" can only mean **some post-type
 * declares a field of that name**. That is a weaker statement than "the form works", and it is the
 * strongest one provable with no instance. A submit button is skipped: it carries no value.
 */
function formFindings(inventory, declared) {
  const missing = [];
  for (const form of inventory.forms ?? []) {
    for (const field of form?.fields ?? []) {
      const name = typeof field?.name === "string" ? field.name.trim() : "";
      if (!name || field.type === "submit") continue;
      if (declared.has(name.toLowerCase())) continue;
      missing.push({ name, at: form.path ?? "?" });
    }
  }
  return capped(missing, "source-form-field-lost", inventory, (row, cut) => ({
    message: `the form on \`${row.at}\` posted \`${row.name}\`, and no post-type in this tree `
      + `declares a field of that name${cut}`,
    fix: "Add the field to the post-type that receives this form, or accept that the replica's form "
      + "collects less than the original's. Note the limit: this can only check that a field is "
      + "DECLARED — where a submission goes is not something this product stores (SH247).",
  }), (row) => `${row.at} ${row.name}`);
}

/**
 * A locale the source served for which the tree holds no content.
 *
 * <p>Declared locales only (`x-default` is not a language), and matched case-insensitively because
 * `pt-BR` and `pt-br` are one locale spelled two ways — a coverage report that reported a gap over
 * capitalisation would be reporting on the source's HTML style.
 */
function localeFindings(inventory, posts, notes) {
  const held = new Set(
    posts.map((post) => String(post?.locale ?? "").trim().toLowerCase()).filter(Boolean),
  );
  // **An absent `locale:` means the instance default** (SH348: the projection writes the field only
  // for a non-default locale, so a monolingual tree is byte-identical to before that task). Reading
  // omission as absence is what the first draft did, and a smoke run caught it reporting `en` missing
  // on a tree whose only page was English.
  //
  // Which locale the default *is* cannot be known server-free — that constant is the writer's. What
  // the capture knows is the source's own primary: `declared` is its `<html lang>`, `default` is its
  // `x-default`. A replica's default is meant to be that one, so an unlabelled post counts as holding
  // it. The inference is stated in the notes rather than assumed silently, because it is an inference.
  const unlabelled = posts.filter((post) => !String(post?.locale ?? "").trim()).length;
  if (unlabelled) {
    const primary = (inventory.locales ?? [])
      .find((row) => row?.shape === "declared" || row?.shape === "default");
    if (primary?.locale) {
      held.add(String(primary.locale).trim().toLowerCase());
      notes.push(`${unlabelled} post(s) state no locale, which means the instance default — counted `
        + `as covering \`${primary.locale}\`, the locale the source itself declares`);
    } else {
      notes.push(`${unlabelled} post(s) state no locale and the capture names no primary locale, so `
        + "which language they are in is unproven here");
    }
  }
  const missing = (inventory.locales ?? [])
    .map((row) => String(row?.locale ?? "").trim())
    .filter((locale) => locale && locale.toLowerCase() !== "x-default")
    .filter((locale) => !held.has(locale.toLowerCase()));
  return capped(missing, "source-locale-missing", inventory, (locale, cut) => ({
    message: `the source served \`${locale}\` and no post in this tree carries that locale${cut}`,
    fix: "Create the translations with a post.upsert naming `locale` and `translationOf`, or accept "
      + "that the replica ships in fewer languages than the original.",
  }));
}

/**
 * What this group could not check — a warning that names itself, never silence (SH302's rule).
 *
 * <p>Four ways a coverage report can be built on less than it looks like: the capture was
 * **truncated** by a ceiling, so every count in it is a lower bound and "no missing pages" means "no
 * missing pages among the ones we saw"; a page the crawl **could not read** at all; a locale the
 * source signalled in a shape the crawl could not place (`unknown`); and an inventory written by a
 * **newer** version of `shio clone` than this one understands. Each is a warning against the capture
 * itself rather than against a page, because none of them is a defect in the replica.
 */
function uncheckedFindings(inventory, notes) {
  const out = [];
  const version = Number(inventory.version ?? 1);
  const address = originAddress(inventory);

  if (version > READS_VERSION) {
    out.push({
      rule: "replication-unchecked",
      severity: "warning",
      address,
      field: undefined,
      message: `this capture is version ${version} and this command reads ${READS_VERSION}, so `
        + "whatever it added was not checked",
      fix: "Upgrade @viglet/shio. A coverage report from an older reader is a lower bound, which is "
        + "the one thing a coverage report must not be silently.",
      source: REPLICATION_CHECK,
    });
  }
  if (inventory.crawl?.truncated) {
    notes.push("the capture is PARTIAL (a ceiling cut it), so every count here is a lower bound");
    out.push(unchecked(address, "the capture stopped at its ceiling, so the source has pages this "
      + "report never compared", "Raise --depth or --max-pages and capture again. Until then a clean "
      + "run means \"complete among the pages the crawl saw\"."));
  }
  // Each version is named with what it added, rather than one message keyed to the newest: a reader
  // told "this file is older" and not *which rule went quiet* has been given a fact it cannot act on.
  if (version < 2) {
    notes.push(`this capture is version ${version}; \`unfollowed\` arrived in 2, so `
      + "source-link-unresolved checked nothing");
  }
  if (version < 3) {
    notes.push(`this capture is version ${version}; a served non-HTML response was filed as an `
      + "unreadable page before 3, so the count below may include a data surface that is not a gap "
      + "— re-run `shio clone` to separate them");
  }
  const unreadable = (inventory.problems ?? []).length;
  if (unreadable) {
    out.push(unchecked(address, `${unreadable} source page(s) could not be read at all, so their `
      + "links, assets and forms were never compared",
      "Look at `problems` in the inventory: a 500 is the source's, a redirect off-origin is a "
      + "different site, and neither is a gap in the replica."));
  }
  // SH431 — reported, never counted as unchecked. A 200 of application/json is the site's data
  // surface: linked on purpose, served correctly, and never a page. Twenty-four of them turned a
  // complete capture into two dozen phantom gaps and downgraded every count in the report — which is
  // how a coverage report loses the trust that makes it the answer to "is this replication done".
  const served = (inventory.data ?? []).length;
  if (served) {
    notes.push(`${served} served non-HTML response(s) (a data surface) were recorded and not `
      + "compared: they were never pages, so their absence from the replica is a decision rather "
      + "than a gap");
  }
  const vague = (inventory.locales ?? []).filter((row) => row?.shape === "unknown").length;
  if (vague) {
    out.push(unchecked(address, `${vague} source locale(s) appear nowhere in a URL, so the capture `
      + "could not tell how the source selects them",
      "A site keying its locale off a cookie or a header cannot be replicated by URL alone — decide "
      + "what the replica does instead, because this report cannot."));
  }
  return out;
}

function unchecked(address, message, fix) {
  return {
    rule: "replication-unchecked",
    severity: "warning",
    address,
    field: undefined,
    message,
    fix,
    source: REPLICATION_CHECK,
  };
}

// ---------------------------------------------------------------------------------------------
// shared shapes
// ---------------------------------------------------------------------------------------------

/**
 * One rule's rows, capped, with the cut declared **in every message**.
 *
 * <p>Not in a note beside them: a reader who sees nineteen rows and a twentieth saying "and more"
 * has been told; a reader who sees twenty rows and a note three screens down has not. An unreported
 * cut reads as a complete list, which for a coverage report is the failure the whole group exists to
 * prevent.
 */
function capped(rows, rule, inventory, render, key = (row) => row) {
  const unique = [];
  const seen = new Set();
  for (const row of rows) {
    const id = String(key(row));
    if (seen.has(id)) continue;
    seen.add(id);
    unique.push(row);
  }
  unique.sort((a, b) => String(key(a)).localeCompare(String(key(b))));
  const total = unique.length;
  return unique.slice(0, LIMIT).map((row) => {
    const cut = total > LIMIT ? ` (${total} in all; ${LIMIT} shown)` : "";
    const { message, fix } = render(row, cut);
    return {
      rule,
      severity: "warning",
      address: originAddress(inventory),
      field: undefined,
      message,
      fix,
      source: REPLICATION_CHECK,
    };
  });
}

/**
 * The address every finding here hangs on: the **source origin**.
 *
 * <p>Deliberately not a `post:` address, and the reason is that there is nothing to point at — the
 * subject of every rule in this group is something that does *not* exist in the replica. An invented
 * `post:<site>/<path>` would be an address `shio resolve` cannot resolve, which is worse than an
 * honest one naming the capture.
 */
function originAddress(inventory) {
  return `source:${inventory?.source?.origin ?? "?"}`;
}

function pathsOf(rows) {
  return (rows ?? [])
    .map((row) => (typeof row?.path === "string" ? row.path.split("?")[0] : ""))
    .filter(Boolean);
}

/** Every field name any post-type in the tree declares, folded for comparison. */
function declaredFields(defs) {
  const names = new Set();
  for (const def of defs ?? []) {
    for (const field of def?.fields ?? []) {
      if (typeof field?.name === "string") names.add(field.name.trim().toLowerCase());
      for (const child of field?.fields ?? []) {
        if (typeof child?.name === "string") names.add(child.name.trim().toLowerCase());
      }
    }
  }
  return names;
}

/** A path as a comparison key: leading slash, no trailing one, case-folded (SH79). */
function normalizePath(path) {
  const raw = String(path ?? "").trim().toLowerCase();
  const lead = raw.startsWith("/") ? raw : `/${raw}`;
  return lead.length > 1 && lead.endsWith("/") ? lead.slice(0, -1) : lead;
}

/** By rule, then message — so two runs over one tree and one capture read the same (P5). */
function sortFindings(findings) {
  return [...findings].sort((a, b) =>
    (a.rule ?? "").localeCompare(b.rule ?? "") || (a.message ?? "").localeCompare(b.message ?? ""));
}

/** The rules this group owns, for the baseline's coverage split (SH410/SH411). */
export function replicationRules() {
  return new Set([
    "source-page-missing",
    "source-link-unresolved",
    "source-asset-missing",
    "source-asset-stranded",
    "source-chrome-stranded",
    "source-form-field-lost",
    "source-locale-missing",
    "replication-unchecked",
  ]);
}
