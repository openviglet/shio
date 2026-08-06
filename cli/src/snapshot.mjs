/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH102 — the visual snapshot, as the parts that do not need a browser.
 *
 * ## What this is, and what it is deliberately not
 *
 * §IX.4 frames a PNG as an artifact **for the human handoff** (SH104) rather than as an agent
 * input, and the framing is the whole design rather than a caveat. A digest is the agent's
 * instrument: SH101 hashes a page's *structure*, so a typo fix does not move it and a lost
 * heading does, and it costs a few hundred tokens. A screenshot costs three orders of magnitude
 * more and tells a model less — which is why `visualSnapshot` is reported `false` in the
 * manifest and why this ships as a CLI command with **no MCP tool and no endpoint**.
 *
 * The consequences that follow from that one decision:
 *
 * - **A change is not a failure.** `shio snapshot` exits 0 when the page looks different,
 *   because a page looking different is usually the change you just made. `--fail-on-change`
 *   exists for a caller that wants a gate, and it is opt-in so nobody learns to ignore the
 *   exit code of the command that also produces their evidence.
 * - **The comparison is honest about its resolution.** A real per-pixel diff needs `pixelmatch`
 *   and `pngjs`; without them this still answers "did it change" by comparing bytes, and says
 *   which of the two answers it gave. A ratio invented from a byte comparison would be a number
 *   an agent would threshold on.
 * - **The baseline is a project file.** `shio/snapshots/` sits beside `shio/post-types/` and
 *   `shio/content/` so a reviewer sees the image in the diff. That PNGs are heavy in git is a
 *   real cost, and one more reason the whole command is opt-in.
 */
import { isFrontEnd } from "./front-end.mjs";

/** Where baselines live: a project directory, reviewable, not a cache. */
export const SNAPSHOT_DIR = "shio/snapshots";

/**
 * The page a command was asked about — from `--page`, `--address`, or a bare positional (SH365).
 *
 * <h2>What `--url` used to also mean, and why the rename is a deprecation</h2>
 *
 * <p>`snapshot` and `digest` both read the target as `flags.address ?? flags.url ?? positionals[0]`
 * while `resolveAdmin` reads `flags.url` as the **instance**. One flag named the page in one line and
 * the server in the next. It did not bite — the two readings only meet when a caller passes both, and
 * that is the correct usage (`--address post:Blog/x --url http://localhost:2710`), which resolves the
 * way anybody would expect. What made it worth fixing is SH265: `--url` now also decides
 * `previewBase`, the origin a front-endless site is photographed from, so a flag that silently changes
 * which *server* an artifact came from is the failure `resolveUrl` refuses to risk when it declines to
 * guess an origin.
 *
 * <p><b>A rename with a deprecation, not a break.</b> `shio snapshot --url https://site/page` was a
 * documented way to name a page and is somebody's script, so it still works: when nothing else names
 * a target, `--url` is read as one and the caller is told which flag to use instead (P6). When a
 * target *is* named some other way, `--url` is the instance and only the instance — which is what it
 * means in every other command.
 *
 * <p><b>Passing `warn` is how a command opts into the legacy reading</b>, and that is the whole
 * mechanism rather than a convenience: only `snapshot` and `digest` ever documented `--url` as a page,
 * so only they owe anybody backward compatibility. A command with no history there — `audit`, or the
 * next one — calls this with no `warn` and gets the strict read, because accepting `--url` as a page in
 * a command that never did would *create* the hazard for free: `shio audit --url http://localhost:2710`
 * would quietly audit the instance root instead of saying no page was named.
 *
 * @param flags the parsed flags
 * @param positionals the bare arguments
 * @param warn called with a deprecation message when `--url` was read as the page; omit it to refuse
 *             that reading entirely
 */
export function pageTarget(flags, positionals = [], warn) {
  const named = flags.page ?? flags.address ?? positionals[0];
  if (named !== undefined && named !== null && named !== "" && named !== true) {
    return named;
  }
  if (warn && typeof flags.url === "string" && flags.url) {
    warn?.(
      `--url named the page here, which is deprecated: --url means the instance in every other\n` +
        `  command, and since SH265 it also decides which server a preview is rendered by. Pass\n` +
        `  --page ${flags.url} instead (or the page as a bare argument).`,
    );
    return flags.url;
  }
  return undefined;
}

/**
 * A stable file name for a page.
 *
 * <p>Derived from the address or URL rather than from a counter, so re-running the command
 * compares against the same baseline and two different pages can never collide onto one file —
 * the failure that would make every run report a change.
 */
export function slugFor(target) {
  const raw = String(target ?? "")
    .replace(/^https?:\/\//, "")
    .replace(/^post:/, "")
    .replace(/[^a-zA-Z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
  return raw || "page";
}

/**
 * Compares two PNG buffers.
 *
 * @param {Buffer|Uint8Array|null} before the baseline, or null when there is none
 * @param {Buffer|Uint8Array} after the capture just taken
 * @param {{ compare?: (a: Uint8Array, b: Uint8Array) => { ratio: number, diff: Uint8Array }|null }} [tools]
 *   an injected per-pixel comparator; absent means the byte-level answer
 * @returns {{ status: "new"|"same"|"changed", ratio: number|null, diff: Uint8Array|null,
 *            resolution: "pixel"|"bytes" }}
 */
export function compareSnapshots(before, after, tools = {}) {
  if (!before) {
    return { status: "new", ratio: null, diff: null, resolution: "bytes" };
  }
  const pixel = tools.compare ? tools.compare(before, after) : null;
  if (pixel) {
    return {
      status: pixel.ratio > 0 ? "changed" : "same",
      ratio: pixel.ratio,
      diff: pixel.diff ?? null,
      resolution: "pixel",
    };
  }
  return {
    status: sameBytes(before, after) ? "same" : "changed",
    // Deliberately null rather than 0 or 1: this comparison cannot measure *how much* changed,
    // and a fabricated magnitude is exactly the number a caller would build a threshold on.
    ratio: null,
    diff: null,
    resolution: "bytes",
  };
}

function sameBytes(a, b) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false;
  }
  return true;
}

/**
 * The widths a run photographs (SH386).
 *
 * <p>A list in both modes, so the flag means one thing — SH384's rule about two spellings of one
 * window, applied to a viewport. `1280` alone is the default because that is what SH102 has always
 * taken and an existing baseline must keep comparing against the same picture.
 */
export function widthsOf(value) {
  if (value === undefined || value === true) return [DEFAULT_WIDTH];
  const widths = String(value)
    .split(",")
    .map((each) => Number(each.trim()))
    .filter((each) => Number.isInteger(each) && each > 0);
  return widths.length ? [...new Set(widths)].sort((a, b) => a - b) : [DEFAULT_WIDTH];
}

/** SH102's viewport, unchanged: a new default would move every baseline in every project. */
export const DEFAULT_WIDTH = 1280;

/**
 * What a replication comparison's three images are called (SH386).
 *
 * <p>Names rather than paths: the command joins them with `node:path`, which is SH198's rule — a
 * module that built a separator itself would work on a fake filesystem keyed on the literal somebody
 * typed and fail on the disk, or the reverse.
 *
 * <p>Width-suffixed <b>always</b> in this mode, unlike the ordinary one where the first width keeps
 * `<slug>.png`. There is no baseline to preserve here — the mode is new — and a name that were
 * suffixed only when a second width happened to be asked for is the conditional-naming defect SH379
 * settled for the markup cache: the file a run writes must not depend on what else the run was asked
 * to do.
 */
/**
 * What the two pages' shapes say, beside what their pixels say (SH526).
 *
 * <h3>Why a percentage was not enough</h3>
 *
 * <p>A screenshot diff answers "identical to a baseline", which is exactly right for **fidelity** —
 * 0.00% is a real gate — and it is the wrong question for a page that lost a region: an emptied
 * page differs from its source in a large number of pixels *and so does a page whose hero image was
 * re-cropped*, and the percentage cannot tell a reader which happened. The tree can, in a sentence:
 * a replica missing a `contentinfo` has lost the footer, whatever the number is.</p>
 *
 * <h3>Lost, not merely different</h3>
 *
 * <p>A landmark the replica **gained** is not a defect — a Shio layout composes a `nav` the source
 * built in script — so the comparison is one-directional, the same rule `shio diff` makes for
 * counts. What it reports is the region that was there and is not.</p>
 *
 * @returns `{lost: string[], headings: {source, replica}, ok: boolean}`
 */
export function compareShapes(source, replica) {
  const before = source?.landmarks ?? {};
  const after = replica?.landmarks ?? {};
  const lost = Object.keys(before)
    .filter((role) => (after[role] ?? 0) < before[role])
    .sort((a, b) => (a < b ? -1 : 1));
  const headings = {
    source: (source?.headings ?? []).length,
    replica: (replica?.headings ?? []).length,
  };
  return { lost, headings, ok: lost.length === 0 && headings.replica >= headings.source };
}

/** One line, printed beside the percentage — the shape is the half a number cannot carry. */
export function describeShapes(comparison) {
  const { lost, headings, ok } = comparison;
  if (ok) {
    return `  shape: every landmark the source has, ${headings.replica} of its ${headings.source} heading(s)`;
  }
  const parts = [];
  if (lost.length) parts.push(`lost ${lost.join(", ")}`);
  if (headings.replica < headings.source) {
    parts.push(`${headings.source} heading(s) became ${headings.replica}`);
  }
  return `  shape: ${parts.join("; ")} — a region that is gone is gone whatever the percentage says`;
}

export function againstNames(slug, width) {
  const at = `${slug}@${width}`;
  return {
    // The source is the *baseline*, and it is named as one so a reviewer opening the directory can
    // tell which of two similar pictures is somebody else's site.
    source: `${at}.source.png`,
    replica: `${at}.replica.png`,
    diff: `${at}.diff.png`,
  };
}

/**
 * The report for a replication comparison (SH386).
 *
 * <p>One line per width plus a total, because a replica that matches at 1280 and breaks at 375 is the
 * ordinary outcome and a single averaged number would hide exactly that. The **resolution** is named
 * per row for `describe`'s reason: "identical" from a byte comparison and from a pixel comparison are
 * different claims, and here the byte answer is much weaker than usual — two browsers photographing
 * two different origins will almost never produce identical bytes, so a byte-level `same` is close to
 * meaningless and a reader has to be told which one they got.
 */
export function describeAgainst(rows, { source, page, limit } = {}) {
  const differing = rows.filter((row) => row.status !== "same");
  // SH443 — only a row a comparator actually measured has a magnitude. The old `ratioOf` read a
  // null ratio as `1` for anything not `same`, so the byte-only path printed `worst 100.00%`: a
  // percentage nothing computed, on the one line a reader takes away and an agent greps, saying
  // "total mismatch" about a replica that may be all but identical.
  const measured = differing.filter((row) => typeof row?.ratio === "number");
  const worst = measured.reduce((most, row) => (row.ratio > most.ratio ? row : most), measured[0]);
  const lines = [];
  lines.push(
    `shio snapshot --against: ${page} vs ${source} — ${rows.length} width(s), `
      + `${differing.length} differing`
      + magnitudeOf(differing, measured, worst),
  );
  const shown = Number.isFinite(Number(limit)) ? Math.max(Number(limit), 1) : rows.length;
  for (const row of rows.slice(0, shown)) {
    const magnitude = row.ratio === null
      ? (row.status === "same" ? "identical bytes" : "differs (bytes only — no magnitude)")
      : `${(row.ratio * 100).toFixed(2)}% of pixels`;
    lines.push(`  ${row.width}px  ${magnitude} (${row.resolution})`
      + (row.diffPath ? `  diff → ${row.diffPath}` : ""));
  }
  if (rows.length > shown) lines.push(`  … ${rows.length - shown} more width(s)`);
  lines.push(`  baseline kept: ${rows.map((row) => row.sourcePath).join(" ")}`);
  return lines.join("\n");
}

/**
 * What the header may claim about how different the widths are (SH443).
 *
 * <p>One run printed `1 differing, worst 100.00% at 1280px` four lines above its own
 * `1280px  differs (bytes only — no magnitude)`. The header stated a magnitude and the row stated
 * there is none; the note at the bottom said it a third time. So the file already knew — one line
 * did not, and it was the line that matters most: the summary a reader takes away and an agent
 * greps, where "worst 100.00%" reads as a total mismatch and is the number that ends a replication
 * early. SH386's own argument is that a byte-level difference is <b>unmeasured</b> rather than
 * large, and this is that argument applied to the summary.</p>
 *
 * <p>Three cases rather than two, because "nothing measured it" and "measured, and zero" are
 * different claims and only the first is unmeasured.</p>
 */
function magnitudeOf(differing, measured, worst) {
  if (!differing.length) return "";
  if (!measured.length) return " (unmeasured)";
  if (!worst || !(worst.ratio > 0)) return "";
  return `, worst ${(worst.ratio * 100).toFixed(2)}% at ${worst.width}px`;
}

/**
 * The line a human reads.
 *
 * <p>Names the resolution of the answer, because "no change" from a byte comparison and "no
 * change" from a pixel comparison are different claims — the first is defeated by any encoder
 * nondeterminism, and a reader who does not know which they got cannot judge it.
 */
export function describe(result, { slug, path, diffPath } = {}) {
  const where = path ? ` → ${path}` : "";
  if (result.status === "new") {
    return `shio snapshot: ${slug} captured (no baseline to compare)${where}`;
  }
  if (result.status === "same") {
    return `shio snapshot: ${slug} unchanged (${result.resolution})${where}`;
  }
  const magnitude = result.ratio === null ? "" : ` — ${(result.ratio * 100).toFixed(2)}% of pixels`;
  const diff = diffPath ? `, diff → ${diffPath}` : "";
  return `shio snapshot: ${slug} CHANGED${magnitude} (${result.resolution})${where}${diff}`;
}

/**
 * The page URL for a target.
 *
 * <p>An absolute URL is taken as given. A `post:` address is resolved against the site URLs the
 * context pack carries — the same source `shio report` uses for preview links, and for the same
 * reason: the CLI must never guess an origin, because a screenshot of the wrong host is a
 * plausible-looking artifact that costs a reviewer their trust in the rest.
 *
 * ## A site with no front end is photographed by Shio itself (SH265)
 *
 * <p>This used to refuse: *"Site X has no front-end URL configured, so there is no page to
 * photograph."* It was true when written, and Block P made it false — SH183 renders
 * `/preview/{site}/{url}` out of content the instance already holds, and SH184 stopped the server
 * answering `400 site has no url` for exactly this reason. The refusal is the same premise left
 * standing one process along: an agent that had just built a site was told its work could not be
 * seen, by the tool whose whole job is to show it.
 *
 * <p>So the instance's own preview is the fallback, and `--preview` forces it even for a site that
 * *has* a front end — which is the only way to photograph a **draft**, since a live URL serves
 * what is published. The picture carries the preview banner: it is inline-styled and deliberately
 * unhideable (P7), and it is in the baseline as well as in the capture, so a diff still means
 * what it says.
 *
 * ## A slug is not a front end (SH442)
 *
 * <p>The branch above read `if (options.preview || !origin)` — the **truthiness** of the site's URL
 * field rather than whether it is a URL. A site's URL field holds three states, not two: absent,
 * absolute, and *a slug*, which is the default for any site created without a public URL. A slug is
 * truthy, so the front-end branch was taken and the slug went to the browser as an absolute address:
 * `snapshot --page post:Viglet2/ --against <source>` failed with playwright's own `Cannot navigate
 * to invalid URL`, call log `navigating to "viglet2/"` — no scheme, no host. `Bootstrap` holds
 * `https://getbootstrap.com/` and worked; `Cafeteria` holds `cafeteria` and did not.</p>
 *
 * <p>SH265's intent was never ambiguous and this predicate was the one place not implementing it, so
 * the test is the one `resolveUrl` already applies to its *target*: `/^https?:\/\//`. What made it
 * survivable is that nothing could have caught it — captures are injected, so no test hands a URL to
 * a browser, and a real run needs a playwright the checkout cannot resolve (SH444), so it refused
 * earlier for that instead. The fixture with three states is therefore the fix as much as the
 * predicate is.</p>
 *
 * @param sites   site name → public base URL, `""` when none is configured, or a slug
 * @param options `previewBase` — the instance's own origin; `preview` — force the preview
 */
export function resolveUrl(target, sites, options = {}) {
  const raw = String(target ?? "").trim();
  if (isFrontEnd(raw)) {
    return { url: raw };
  }
  const match = /^post:([^/]+)(\/.*)$/.exec(raw);
  if (!match) {
    return {
      error:
        `"${raw}" is not a page. Pass post:<site>/<url> or an absolute http(s) URL.\n` +
        `Example: shio snapshot post:Blog/blog/hello-world`,
    };
  }
  const [, site, path] = match;
  const origin = sites?.get?.(site);
  if (origin === undefined) {
    return { error: `Site "${site}" is not one this instance reports. Check the name, or pass a URL.` };
  }
  const previewBase = options.previewBase ? String(options.previewBase).replace(/\/+$/, "") : "";
  // SH442 — absolute, not truthy. A slug in the URL field is not a front end, and handing it to a
  // browser produces a protocol error where the product has a designed answer.
  const frontEnd = isFrontEnd(origin);
  if (options.preview || !frontEnd) {
    if (!previewBase) {
      // Two states, two sentences: "no URL configured" is false for a site whose field holds
      // `cafeteria`, and a refusal that misreports the cause is one the reader cannot act on (P6).
      const cause = origin
        ? `Site "${site}" has \`${origin}\` in its URL field, which is not an absolute http(s) URL,`
        : `Site "${site}" has no front-end URL configured,`;
      return {
        error:
          `${cause} and this command was not told where\n` +
          `the instance is, so it cannot fall back to its preview. Pass --url, set SHIO_URL, or\n` +
          `pass the page URL directly: shio snapshot https://…${path}`,
      };
    }
    // `/preview` renders DRAFT, which is the state the picture is usually wanted in: a curator
    // asks "does my change look right", and the published page is by definition not the change.
    return {
      url: `${previewBase}/preview/${encodeURIComponent(site)}${path}`,
      preview: true,
    };
  }
  return { url: `${String(origin).replace(/\/+$/, "")}${path}` };
}
