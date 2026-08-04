/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH212 — the first `shio verify` rules that read the **project's own files**.
 *
 * WHY THIS IS A NEW KIND OF FINDING, AND WHY IT IS STILL `verify`
 * Every rule until now was a server-side lint whose findings this CLI merely printed. An
 * asset path a `File` field names is the opposite: it is checkable with no instance at all,
 * because the post-types, the posts and the bytes are all on disk. It used to be caught by
 * `push --content`, which threw — so the whole class had no *report*, only an exception on the
 * first offender, and nothing the SH142 baseline could hold.
 *
 * The decision §X.15 asked for: these are findings in the same report, in their own `checks`
 * group, and **naming `projection` alone skips the server entirely**. That is what actually
 * moves the check earlier — a gate that needs no credentials and no network can run before
 * anything else in the chain, which is where a defect this cheap to find belongs (P9). Keeping
 * it apart in a second command would mean a second report shape and a second baseline for one
 * rule, and an agent would have to learn which command finds which class of defect.
 *
 * WHAT IS DELIBERATELY NOT HERE
 * Anything that needs the server's own state. This module answers questions about the tree
 * alone, and a rule that quietly needed an instance would make the group's whole promise —
 * "this runs with nothing" — a half-truth of the kind SH338 exists to catch.
 */
import { assetLocationOf, fileFields } from "./content-assets.mjs";

/** The group name, in `--checks` and in the report's own `checks` list. */
export const PROJECTION_CHECK = "projection";

/**
 * Lints the file tree's asset references.
 *
 * @param {object} tree
 * @param {object[]} tree.posts   the projected posts (`readLocal`'s values)
 * @param {object[]} tree.defs    local post-type defs (`readLocalDefs`)
 * @param {Iterable<string>} tree.assets asset paths on disk (`readLocalAssets`)
 * @param {string} [tree.assetsDir] named in the fix, so the message points at a real directory
 * @param {object[]} [tree.missingSidecars] `files:` entries naming nothing (SH374)
 * @param {object[]} [tree.duplicates] addresses more than one file claims (SH400)
 * @param {object[]} [tree.unportable] files a case-insensitive filesystem holds as one (SH401)
 * @param {boolean} [tree.scoped] whether the run covers only part of the tree (SH374)
 * @returns {{ findings: object[], checked: number }}
 */
export function lintProjection({
  posts = [],
  defs = [],
  assets = [],
  assetsDir = "shio/assets",
  missingSidecars = [],
  duplicates = [],
  unportable = [],
  scoped = false,
}) {
  const fields = fileFields(defs);
  const present = new Set(assets);
  const findings = [];
  let checked = 0;

  for (const post of [...posts].sort((a, b) => addressOf(a).localeCompare(addressOf(b)))) {
    const names = fields.get(post?.type);
    // A post whose type this tree does not hold is not a clean post — it is an unchecked one,
    // and saying so is the difference between "no findings" and "nothing was looked at".
    if (!names) {
      if (post?.type) findings.push(unknownType(post, assetsDir));
      continue;
    }
    checked += 1;
    for (const name of [...names].sort((a, b) => a.localeCompare(b))) {
      const value = post.data?.[name];
      if (typeof value !== "string" || !value) continue;
      let at;
      try {
        at = assetLocationOf(value);
      } catch (error) {
        // SH256 throws on a path that navigates. Reported rather than propagated: a lint that
        // dies on the first hostile value cannot report the rest of the tree, and this is the
        // one place that reads every asset reference a project has.
        findings.push(finding(post, name, "asset-path-unsafe", error.message,
          "An asset path is `<site>/<folder chain>/<file name>` — no `.` or `..` segments. "
            + "Fix the value in the file."));
        continue;
      }
      // Not path-shaped is an id already (SH145): a reference this tree never projected, which
      // the push sends as it is and the server resolves or reports.
      if (!at) continue;
      if (present.has(value)) continue;
      findings.push(finding(post, name, "asset-missing",
        `the asset \`${value}\` is not in ${assetsDir}`,
        `Fetch it with \`shio pull --content\`, put the file at ${assetsDir}/${value}, or point `
          + "the field at an asset the tree holds. Pushing this would publish a post whose File "
          + "field references nothing."));
    }
  }

  findings.push(...duplicateFindings(duplicates));
  findings.push(...unportableFindings(unportable));
  findings.push(...sidecarFindings(missingSidecars));
  findings.push(...translationFindings(posts, scoped));
  findings.push(...menuItemFindings(posts));
  return { findings: sortFindings(findings), checked };
}

/**
 * A `MenuItem` that points nowhere, or at two places at once (SH382).
 *
 * <h3>Why this rule lives here and not on the server</h3>
 *
 * `Menu` and `MenuItem` are the SH95 vocabulary's, which is a **JS package's** convention — the
 * server does not know one section post-type name by string, and teaching it these two would make
 * the render model opinionated about a package instead of a model. The `{{#menu}}` helper is
 * deliberately structural for the same reason. So the convention is checked where the convention
 * ships, in the gate that needs no instance, and a project using its own post-type names is simply
 * not subject to it.
 *
 * <h3>Why it is not a schema rule</h3>
 *
 * The model has no way to express "exactly one of these two", and making either field required
 * would make the other case impossible: a required `target` forbids an external link, a required
 * `href` forbids an internal one. So the constraint has to be a lint, which also means it can
 * explain itself (P6) rather than rejecting a write with "required field empty".
 *
 * <h3>Both is an error and neither is an error, and they are two different mistakes</h3>
 *
 * **Neither** renders as a group heading — a label with no link — which looks exactly like a
 * deliberate nested-menu heading and is therefore the one that hides. **Both** is a silent
 * precedence: `target` wins in the helper, so the `href` somebody typed does nothing and nothing
 * says so. Two messages, because a caller fixes them differently.
 */
function menuItemFindings(posts) {
  const out = [];
  for (const post of posts) {
    if (post?.type !== "MenuItem") continue;
    const target = entriesOf(post.data?.target);
    const href = typeof post.data?.href === "string" ? post.data.href.trim() : "";
    if (target.length && href) {
      out.push({
        ...finding(post, "href", "menu-item-ambiguous-link",
          `it sets both \`target\` (${target[0]}) and \`href\` (${href}), and only \`target\` is used`,
          "Clear whichever one is not meant. `target` wins in `{{#menu}}`, so the `href` here is "
            + "silently doing nothing — which is why this is reported rather than resolved."),
        severity: "error",
      });
      continue;
    }
    if (!target.length && !href) {
      out.push({
        ...finding(post, "target", "menu-item-no-link",
          "it has neither a `target` page nor an `href`, so it renders as a label with no link",
          "Set `target` to the friendly URL of the page it points at, or `href` to an absolute "
            + "URL for an entry that leaves the site. A label with no link is indistinguishable "
            + "from a deliberate group heading, which is why nothing else can report this."),
        severity: "error",
      });
    }
  }
  return out;
}

/** A `references: post` field's entries: a list, or one address, or nothing. */
function entriesOf(value) {
  if (Array.isArray(value)) {
    return value.map((each) => String(each ?? "").trim()).filter(Boolean);
  }
  const single = typeof value === "string" ? value.trim() : "";
  return single ? [single] : [];
}

/**
 * Two files projecting to one address (SH400) — an **error**, and the same defect the push refuses.
 *
 * It cannot be found from `posts`, because by the time this runs the map `readLocal` keys by
 * address has already kept one of them; the pair arrives from the read itself. Unlike
 * `translation-target-missing` the severity does not depend on the scope: both files are in the
 * part of the tree that was read, or there would be no pair to report.
 */
function duplicateFindings(duplicates) {
  return duplicates.map((duplicate) => ({
    rule: "duplicate-address",
    severity: "error",
    address: duplicate.address,
    field: undefined,
    message: `${duplicate.paths.join(" and ")} both project to it`,
    fix: "A trailing `index` segment is dropped from a path's URL, so `<name>/index.md` and "
      + "`<name>.md` name one post. Keep one of them. Pushing this would publish one file's "
      + "body under that URL and drop the other with no report.",
    source: PROJECTION_CHECK,
  }));
}

/**
 * Two files a case-insensitive filesystem holds as one (SH401) — an **error**.
 *
 * The local half of the rule the pull enforces on the way down. These are two valid posts and two
 * valid addresses; what is wrong is the *tree*, which only exists on Linux. The finding is filed
 * against the first path rather than either address, because neither post is at fault and the fix
 * is a rename of one of them.
 */
function unportableFindings(unportable) {
  return unportable.map((entry) => ({
    rule: "path-collision",
    severity: "error",
    address: entry.addresses[0],
    field: undefined,
    message: `${entry.paths.join(" and ")} differ only in case, so they are one file on Windows `
      + `and macOS (${entry.addresses.join(" and ")})`,
    fix: "Rename one of the posts — its `url:` and its file together. A checkout of this tree on "
      + "a case-insensitive filesystem keeps one of the two and loses the other.",
    source: PROJECTION_CHECK,
  }));
}

/**
 * A `files:` entry naming nothing (SH374) — an **error**, and the same defect the push refuses.
 *
 * The refusal on the write side is right and stays: treating a missing sidecar as an empty value
 * publishes a layout with no HTML from a push that reported success, and treating it as an absent
 * field makes `post.upsert` clear the server's copy, because an upsert declares whole state. What
 * was wrong is that it was the *only* place the defect surfaced — an exception on the first
 * offender rather than a report of every one, in a gate that needs an instance to reach.
 */
function sidecarFindings(missing) {
  return missing.map((entry) => finding(entry.doc, entry.field, "sidecar-missing",
    `its \`files: ${entry.field}: ${entry.name}\` names a file that is not there ` +
      `(expected ${entry.expected})`,
    "Restore the file, or delete the `files:` line and put the value back in `fields:`."));
}

/**
 * A `translationOf` naming a URL the tree does not hold (SH374).
 *
 * <h3>Why the severity depends on the scope</h3>
 *
 * The server refuses this with a teaching 400 (SH348), because creating an unlinked post and
 * reporting success is a silent drop. So it is a real defect — but only when the tree is supposed
 * to be the whole description of the site. A run scoped with `--site` legitimately holds a
 * translation whose original lives in a site it did not read, so there it is a **warning**: the
 * same distinction `asset-check-skipped` makes for a post-type the tree lacks, and for the same
 * reason — "not checked" and "wrong" are different claims and must not be spelled the same.
 */
function translationFindings(posts, scoped) {
  const held = new Set(posts.map((post) => addressOf(post)));
  const out = [];
  for (const post of posts) {
    const target = post?.translationOf;
    if (typeof target !== "string" || !target) continue;
    const address = `post:${post.site ?? ""}${target}`;
    if (held.has(address)) continue;
    out.push({
      ...finding(post, "translationOf", "translation-target-missing",
        `its \`translationOf: ${target}\` names a post this tree does not hold (${address})`,
        scoped
          ? "Widen the run, or check that the original is in a site this projection covers — the "
            + "server refuses a translation whose target it cannot resolve."
          : "Point it at a post the tree holds, or drop the line. The server refuses a "
            + "translation whose target it cannot resolve, so pushing this fails."),
      // A scoped run is not a statement about the site (SH143's rule), so a target it could not
      // have seen is not evidence of a defect.
      severity: scoped ? "warning" : "error",
    });
  }
  return out;
}

/** By address, then rule, then field — so two runs over one tree read the same (P5). */
function sortFindings(findings) {
  return [...findings].sort((a, b) =>
    (a.address ?? "").localeCompare(b.address ?? "")
    || (a.rule ?? "").localeCompare(b.rule ?? "")
    || (a.field ?? "").localeCompare(b.field ?? ""));
}

/**
 * A post whose type is not in `shio/post-types/` — a **warning**, not an error.
 *
 * The tree may legitimately hold posts of a type it does not own: a projection scoped with
 * `--site` still carries whatever those posts are, and the model lives on the server. So this
 * says "not checked" rather than "wrong", which is the honest claim — and it exists at all
 * because silence here would count an unchecked post as a clean one.
 */
function unknownType(post) {
  return {
    ...finding(post, null, "asset-check-skipped",
      `its type \`${post.type}\` is not in this tree, so its File fields were not checked`,
      "Run `shio pull` to project the post-types, or scope the run to a site whose model the "
        + "tree holds."),
    severity: "warning",
  };
}

function finding(post, field, rule, message, fix) {
  return {
    rule,
    severity: "error",
    address: addressOf(post),
    field: field ?? undefined,
    message,
    fix,
    source: PROJECTION_CHECK,
  };
}

function addressOf(post) {
  return `post:${post?.site ?? ""}${post?.url ?? ""}`;
}

/**
 * Which groups the server should be asked for, and whether it should be asked at all.
 *
 * <h3>Two decisions live here</h3>
 *
 * **`projection` is in the default set.** The check is the cheapest one this tool has and it
 * already fails CI one gate later, as an exception out of `push --content` — so running it by
 * default reports a defect earlier that was going to stop the pipeline anyway, and never fails a
 * tree that was passing. That is the opposite trade from SH141's `delivery`, which joins the
 * default set only when the instance allows it: `delivery` can cover *nothing* and still answer
 * `ok`, while this group either checks the tree or says it could not.
 *
 * **It is never sent to the server.** A vocabulary that does not contain it would answer a
 * teaching 400, correctly (SH320: an unrecognised `checks` value is one that would silently
 * cover nothing). Naming it *alone* is therefore the server-free gate — no credentials, no
 * network, nothing to stand up in CI.
 *
 * @param {string|undefined} checks the caller's `--checks`
 * @returns {{ remote: string|undefined, projection: boolean, callRemote: boolean }}
 */
export function splitChecks(checks) {
  if (checks == null || String(checks).trim() === "") {
    return { remote: undefined, projection: true, callRemote: true };
  }
  const named = String(checks).split(",").map((each) => each.trim()).filter(Boolean);
  const projection = named.includes(PROJECTION_CHECK) || named.includes("all");
  const rest = named.filter((each) => each !== PROJECTION_CHECK);
  return {
    remote: rest.length ? rest.join(",") : undefined,
    projection,
    callRemote: rest.length > 0,
  };
}
