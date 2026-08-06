/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH391 — `shio audit`. Browser-free: the page load is injected, because a test that launched
 * Chromium would be testing Playwright.
 *
 * What is worth pinning is everything around it, and one of those things is a *decision* rather
 * than a mechanism: the exit code. Errors are what a machine observed and they gate; a warning is
 * a judgment about design intent, and the moment those fail the command, the command that produces
 * the evidence is the one people wrap in `|| true` — which is the trap `shio snapshot` avoided by
 * keeping `--fail-on-change` opt-in.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  AUDIT_RULES,
  CONTRAST_FLOOR,
  DEFAULT_WIDTHS,
  classify,
  contrastFloor,
  contrastRatio,
  describe as describeReport,
  line,
  parseColor,
  parseWidths,
  probe,
} from "../src/audit.mjs";
import * as audit from "../src/commands/audit.mjs";

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

const CONTEXT = { sites: [{ name: "Blog", url: "https://blog.example.com/" }, { name: "Docs", url: "" }] };

function ctxWith({ observed, onAudit } = {}) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    async audit(url, options, flags) {
      onAudit?.({ url, options, flags });
      return observed ?? { probes: [], requests: [], consoleErrors: [] };
    },
    async fetch() {
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async text() {
          return JSON.stringify(CONTEXT);
        },
        async json() {
          return CONTEXT;
        },
      };
    },
  };
}

// --- widths ---

test("--width defaults to desktop plus phone, and normalizes what it is given", () => {
  assert.deepEqual(parseWidths(undefined).widths, DEFAULT_WIDTHS);
  // A bare `--width` with no value parses as boolean true; it means "the default", not an error.
  assert.deepEqual(parseWidths(true).widths, DEFAULT_WIDTHS);
  // De-duplicated and widest-first, so two runs of one command print one order (P5).
  assert.deepEqual(parseWidths("375, 1280,768,375").widths, [1280, 768, 375]);
  assert.deepEqual(parseWidths("900").widths, [900]);
});

test("a width that is not a viewport is a teaching error, not a silent default", () => {
  assert.match(parseWidths("wide").error, /not a viewport width/);
  assert.match(parseWidths("12").error, /between 200 and 4096/);
  assert.match(parseWidths("375.5").error, /whole pixels/);
  assert.match(parseWidths("9000").error, /--width 1280,768,375/);
});

// --- contrast ---

test("the contrast ratio is WCAG's, on the colour forms a computed style produces", () => {
  assert.equal(contrastRatio("rgb(0, 0, 0)", "rgb(255, 255, 255)"), 21);
  assert.equal(contrastRatio("rgb(255, 255, 255)", "rgb(255, 255, 255)"), 1);
  assert.equal(contrastRatio("#777777", "#ffffff"), 4.48);
  // Order does not matter — the ratio is the lighter over the darker either way.
  assert.equal(contrastRatio("#ffffff", "#777777"), 4.48);
});

test("translucent text is composited before it is judged", () => {
  // The declared colour is black, which would read as 21:1. What is on screen is grey.
  const declared = contrastRatio("rgb(0, 0, 0)", "rgb(255, 255, 255)");
  const actual = contrastRatio("rgba(0, 0, 0, 0.35)", "rgb(255, 255, 255)");
  assert.equal(declared, 21);
  assert.ok(actual < 4.5, `translucent black on white measured ${actual}:1`);
});

test("a colour that cannot be read is a skipped check, never a fabricated ratio", () => {
  assert.equal(contrastRatio("color(display-p3 0 0 0)", "#fff"), null);
  assert.equal(contrastRatio("#fff", "not-a-colour"), null);
  // A fully transparent background means the probe found nothing to compare against.
  assert.equal(contrastRatio("#000", "rgba(0, 0, 0, 0)"), null);
  assert.equal(parseColor("transparent").a, 0);
  assert.deepEqual(parseColor("#fff"), { r: 255, g: 255, b: 255, a: 1 });
});

test("large text is allowed WCAG's 3:1, and lowering the floor never raises it", () => {
  assert.equal(contrastFloor({ fontSize: 16 }), CONTRAST_FLOOR);
  assert.equal(contrastFloor({ fontSize: 32 }), 3);
  assert.equal(contrastFloor({ fontSize: 19, bold: true }), 3);
  assert.equal(contrastFloor({ fontSize: 19, bold: false }), CONTRAST_FLOOR);
  // A caller who asked for 2 asked for 2 — the large-text allowance is a floor, not an override.
  assert.equal(contrastFloor({ fontSize: 32 }, 2), 2);
});

// --- classification ---

const OVERFLOW = (width, px, selector = ".hero > .grid") => ({
  width,
  overflow: [{ selector, side: "right", px }],
});

test("one element overflowing at three widths is one finding, at its worst", () => {
  const { findings, counts } = classify({
    probes: [OVERFLOW(1280, 12), OVERFLOW(768, 90), OVERFLOW(375, 216)],
  });

  assert.equal(counts.total, 1);
  const [finding] = findings;
  assert.equal(finding.rule, "overflow");
  assert.equal(finding.selector, ".hero > .grid");
  // The worst number is the one that decides whether the fix worked.
  assert.equal(finding.px, 216);
  assert.equal(finding.at, 375);
  assert.deepEqual(finding.widths, [1280, 768, 375]);
});

test("contrast keeps the lowest ratio seen, and clears at the floor", () => {
  const { findings } = classify({
    probes: [
      { width: 1280, text: [{ selector: "a.link", color: "#777", background: "#fff", fontSize: 16 }] },
      { width: 375, text: [{ selector: "a.link", color: "#999", background: "#fff", fontSize: 16 }] },
      { width: 375, text: [{ selector: "p", color: "#000", background: "#fff", fontSize: 16 }] },
    ],
  });

  assert.equal(findings.length, 1, "black on white is not a finding");
  assert.equal(findings[0].selector, "a.link");
  assert.equal(findings[0].ratio, 2.85);
  assert.equal(findings[0].needs, 4.5);
});

test("--contrast moves the floor", () => {
  const probes = [{ width: 1280, text: [{ selector: "p", color: "#777", background: "#fff", fontSize: 16 }] }];
  assert.equal(classify({ probes }).findings.length, 1);
  assert.equal(classify({ probes }, { contrast: 4 }).findings.length, 0);
});

test("a loaded font is not a finding, and one that fell back is", () => {
  const { findings } = classify({
    probes: [
      {
        width: 1280,
        fonts: [{ family: "Inter", loaded: false }, { family: "Georgia", loaded: true }],
      },
    ],
  });

  assert.equal(findings.length, 1);
  assert.equal(findings[0].rule, "font-fallback");
  assert.equal(findings[0].selector, "Inter");
});

test("errors and warnings are separated, errors first, and the order is stable", () => {
  const { findings, counts } = classify({
    probes: [
      { width: 1280, overflow: [{ selector: ".b", side: "right", px: 4 }] },
      { width: 1280, overflow: [{ selector: ".a", side: "right", px: 9 }] },
      { width: 1280, zeroSize: [{ selector: "img.logo", width: 0, height: 0, kind: "img" }] },
    ],
    requests: [{ url: "/assets/logo.png", status: 404, width: 1280 }],
    consoleErrors: [{ text: "TypeError: x is not a function", width: 1280 }],
  });

  assert.deepEqual(counts, { total: 5, errors: 2, warnings: 3 });
  assert.deepEqual(
    findings.map((each) => `${each.severity}/${each.rule}/${each.selector}`),
    [
      "error/console-error/TypeError: x is not a function",
      "error/request-failed//assets/logo.png",
      "warn/overflow/.a",
      "warn/overflow/.b",
      "warn/zero-size/img.logo",
    ],
  );
});

test("the browser's own log of a failed request is not a second finding", () => {
  // Found by running the probe against a fixture rather than by reading it: Chromium logs a
  // console error for every response it could not load, so two broken references produced five
  // error findings for three defects. The response listener knows the URL and the status; the echo
  // knows neither, and both listeners are on the same page — so dropping it is deduplication.
  const { findings, counts } = classify({
    probes: [],
    requests: [{ url: "http://x.test/logo.png", status: 404, width: 1280 }],
    consoleErrors: [
      { text: "Failed to load resource: the server responded with a status of 404 (Not Found)", width: 1280 },
      { text: "missingFunction is not defined", width: 1280 },
    ],
  });

  assert.equal(counts.total, 2);
  assert.deepEqual(findings.map((each) => each.selector), [
    "missingFunction is not defined",
    "http://x.test/logo.png",
  ]);
});

test("a console error's message is the finding, so its line has no dangling dash", () => {
  assert.equal(
    line({ severity: "error", rule: "console-error", selector: "TypeError: nope", widths: [1280] }),
    "error console-error   TypeError: nope [1280]",
  );
});

test("every rule has a severity, and only observed facts are errors", () => {
  // The severity table is the design decision, so it is asserted rather than described: a rule
  // promoted to `error` silently turns this command into a gate on somebody's design choice.
  assert.deepEqual(
    Object.entries(AUDIT_RULES).filter(([, severity]) => severity === "error").map(([rule]) => rule).sort(),
    ["console-error", "request-failed"],
  );
  const { findings } = classify({
    probes: [{ width: 375, collapsed: [{ selector: ".row", children: 3 }] }],
  });
  assert.equal(findings[0].severity, "warn");
});

// --- the report ---

test("a finding line names the element and what to change about it", () => {
  assert.equal(
    line({ severity: "warn", rule: "overflow", selector: ".hero", widths: [375], px: 216, side: "right" }),
    "warn  overflow        .hero — 216px past the right edge [375]",
  );
  assert.match(
    line({
      severity: "warn",
      rule: "contrast",
      selector: "a",
      widths: [1280],
      ratio: 2.85,
      needs: 4.5,
      color: "#999",
      background: "#fff",
      sample: "Read more",
    }),
    /2\.85:1 \(needs 4\.5:1\) #999 on #fff — "Read more"/,
  );
  assert.match(
    line({ severity: "error", rule: "request-failed", selector: "/x.css", widths: [], status: 404 }),
    /^error request-failed  \/x\.css — HTTP 404$/,
  );
  assert.match(
    line({ severity: "error", rule: "request-failed", selector: "/x.css", widths: [], status: 0, failure: "net::ERR_ABORTED" }),
    /request failed: net::ERR_ABORTED/,
  );
  assert.match(
    line({ severity: "warn", rule: "collapsed", selector: ".row", widths: [], children: 1 }),
    /height 0 around 1 laid-out child$/,
  );
});

test("a clean page says so, rather than printing nothing", () => {
  const out = describeReport({
    target: "post:Blog/x",
    url: "https://blog.example.com/x",
    widths: [1280, 375],
    findings: [],
    counts: { total: 0, errors: 0, warnings: 0 },
    capped: {},
  });

  assert.match(out, /0 findings \(0 errors, 0 warnings\) at 1280,375px/);
  assert.match(out, /nothing to fix/);
});

test("--limit bounds what is printed and never what was counted", () => {
  const findings = Array.from({ length: 30 }, (_, index) => ({
    severity: "warn",
    rule: "overflow",
    selector: `.box${index}`,
    widths: [1280],
    px: 10,
    side: "right",
  }));
  const out = describeReport(
    { target: "t", url: "u", widths: [1280], findings, counts: { total: 30, errors: 0, warnings: 30 }, capped: {} },
    { limit: 5 },
  );

  assert.match(out, /30 findings/);
  assert.equal(out.split("\n").filter((each) => each.startsWith("warn")).length, 5);
  // An undeclared cut reads as a clean pass, which is what `shio verify`'s notes exist to prevent.
  assert.match(out, /\(25 more — raise --limit to see them\)/);
});

test("a per-load cap is reported, not swallowed", () => {
  const out = describeReport({
    target: "t",
    url: "u",
    widths: [1280],
    findings: [],
    counts: { total: 0, errors: 0, warnings: 0 },
    capped: { contrast: 118 },
  });

  assert.match(out, /note: contrast stopped at 25 per load, 118 not examined/);
});

// --- the command ---

test("an address resolves through the site's front end and prints the findings", async () => {
  const seen = [];
  const ctx = ctxWith({
    onAudit: (call) => seen.push(call),
    observed: { probes: [OVERFLOW(375, 216)], requests: [], consoleErrors: [] },
  });
  const result = await audit.run({}, ["post:Blog/blog/hello"], ctx);

  assert.equal(result.url, "https://blog.example.com/blog/hello");
  assert.deepEqual(result.widths, [1280, 375]);
  assert.match(ctx.stdout.text, /1 finding \(0 errors, 1 warning\)/);
  assert.match(ctx.stdout.text, /216px past the right edge/);
  // A public host is never handed the instance's admin password.
  assert.equal(seen[0].options.credentials, null);
});

test("a warning exits 0 and an observed error exits 1", async () => {
  const warn = ctxWith({ observed: { probes: [OVERFLOW(375, 216)], requests: [], consoleErrors: [] } });
  assert.equal((await audit.run({}, ["post:Blog/x"], warn)).exitCode, 0);

  const error = ctxWith({
    observed: { probes: [], requests: [{ url: "/logo.png", status: 404, width: 1280 }], consoleErrors: [] },
  });
  assert.equal((await audit.run({}, ["post:Blog/x"], error)).exitCode, 1);
});

test("--strict is the opt-in gate on warnings", async () => {
  const ctx = ctxWith({ observed: { probes: [OVERFLOW(375, 216)], requests: [], consoleErrors: [] } });

  assert.equal((await audit.run({ strict: true }, ["post:Blog/x"], ctx)).exitCode, 1);
});

test("a front-endless site is audited through the preview, with credentials and a note", async () => {
  const seen = [];
  const ctx = ctxWith({ onAudit: (call) => seen.push(call) });
  const result = await audit.run({}, ["post:Docs/guide"], ctx);

  assert.equal(result.url, "http://shio.test/preview/Docs/guide");
  // SH359 closed the preview, so the browser authenticates like everything else the CLI does —
  // without this every finding would be about the login page.
  assert.deepEqual(seen[0].options.credentials, { user: "admin", password: "secret" });
  assert.match(ctx.stdout.text, /preview: DRAFT through this instance/);
});

test("--page is the target and --url stays the instance (SH365's defect, not inherited)", async () => {
  const seen = [];
  const ctx = ctxWith({ onAudit: (call) => seen.push(call) });
  await audit.run({ page: "post:Docs/guide", url: "http://other.test" }, [], ctx);

  // One flag, one meaning: `--url` moved the instance the preview is rendered by, and did not
  // become the page.
  assert.equal(seen[0].url, "http://other.test/preview/Docs/guide");
});

test("--json is the data, with the same counts as the text", async () => {
  const ctx = ctxWith({
    observed: {
      probes: [OVERFLOW(375, 216)],
      requests: [{ url: "/logo.png", status: 404, width: 375 }],
      consoleErrors: [],
    },
  });
  const result = await audit.run({ json: true }, ["post:Blog/x"], ctx);
  const parsed = JSON.parse(ctx.stdout.text);

  assert.deepEqual(parsed.counts, { total: 2, errors: 1, warnings: 1 });
  assert.equal(parsed.findings.length, 2);
  assert.equal(parsed.url, result.url);
});

test("no page named explains what to pass, and a bad width does too", async () => {
  await assert.rejects(() => audit.run({}, [], ctxWith()), /pass a page/);
  await assert.rejects(() => audit.run({ width: "tiny" }, ["post:Blog/x"], ctxWith()), /not a viewport width/);
  await assert.rejects(() => audit.run({}, ["folder:Blog/blog"], ctxWith()), /is not a page/);
});

// --- the probe ---

test("the probe closes over nothing but its own argument", () => {
  // It is serialized by Playwright and evaluated in the browser, so a reference to module scope
  // is a ReferenceError in a Chromium process — several steps from this file, and only when the
  // command is actually run with a browser installed.
  // Comments are stripped first: the probe's own prose cites `parseColor`'s reasoning, and a name
  // in a comment is not a reference. Checking the raw source would make the lint refuse the
  // documentation the rest of this file insists on.
  const source = probe
    .toString()
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/(^|[^:])\/\/.*$/gm, "$1");
  const moduleScope = ["AUDIT_RULES", "CONTRAST_FLOOR", "PER_RULE_CAP", "DEFAULT_WIDTHS",
    "parseColor", "luminance", "contrastRatio", "classify", "describe(", "line("];
  for (const name of moduleScope) {
    assert.ok(!source.includes(name), `probe references ${name} from module scope`);
  }
  assert.ok(probe.toString().startsWith("function probe(options)"));
});
