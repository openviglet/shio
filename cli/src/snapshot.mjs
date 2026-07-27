/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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

/** Where baselines live: a project directory, reviewable, not a cache. */
export const SNAPSHOT_DIR = "shio/snapshots";

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
 */
export function resolveUrl(target, sites) {
  const raw = String(target ?? "").trim();
  if (/^https?:\/\//.test(raw)) {
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
  if (!origin) {
    return {
      error:
        `Site "${site}" has no front-end URL configured, so there is no page to photograph.\n` +
        `Set the site's URL in the console, or pass the URL directly: shio snapshot https://…${path}`,
    };
  }
  return { url: `${String(origin).replace(/\/+$/, "")}${path}` };
}
