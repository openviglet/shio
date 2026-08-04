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
 * @param sites   site name → public base URL, or `""` when none is configured
 * @param options `previewBase` — the instance's own origin; `preview` — force the preview
 */
export function resolveUrl(target, sites, options = {}) {
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
  const previewBase = options.previewBase ? String(options.previewBase).replace(/\/+$/, "") : "";
  if (options.preview || !origin) {
    if (!previewBase) {
      return {
        error:
          `Site "${site}" has no front-end URL configured, and this command was not told where\n` +
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
