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

import { uncommented } from "./support/source-text.mjs";

import {
  AUDIT_RULES,
  describeShape,
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
  readRegionScripts,
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

/**
 * SH689 — a finding that cannot be falsified.
 *
 * <p>`shio audit` warned that `Inter Variable` and `Plus Jakarta Sans Variable` *"did not load, so
 * the page fell back"* on a page carrying **33** `@font-face` rules that declare both families
 * twice over: once as `url()` answering 200 with 85,068 bytes, once as an inline
 * `data:font/woff2;base64` payload decoding to a valid wOF2. The fonts were present by two
 * independent routes and the warning asserted the opposite.</p>
 *
 * <p>Either it was wrong or it observed a load that had not finished — a real and different thing —
 * and <b>nothing in the output distinguished those</b>. That is the defect: a causal claim about the
 * rendered result, made by the one instrument holding the rendered result, reporting neither the
 * element it looked at nor what it read there.</p>
 */
test("SH689: the finding carries where it looked, what the CSS asked, and the measurement", () => {
  const { findings } = classify({
    probes: [{
      width: 1280,
      fonts: [{
        family: "Inter Variable",
        loaded: false,
        source: "measured",
        at: "h1.hero",
        asked: '"Inter Variable", system-ui, sans-serif',
        measured: "monospace 940.5=940.5 serif 1002.1=1002.1 sans-serif 1044.8=1044.8",
      }],
    }],
  });

  assert.equal(findings.length, 1);
  const detail = line(findings[0]);
  assert.match(detail, /at h1\.hero/, "where it looked, so the claim can be checked at that element");
  assert.match(detail, /the CSS asks for "Inter Variable", system-ui, sans-serif/);
  assert.match(
    detail,
    /same width as each fallback alone \(monospace 940\.5=940\.5/,
    "and the numbers, which are the reader's way of disagreeing with it — the old line said only "
      + "`did not load, so the page fell back`, which nothing could contradict (SH689)",
  );
  assert.doesNotMatch(
    detail,
    /did not load/,
    "the load event was the wrong subject: it is what let a font present by two routes be reported "
      + "absent, with no way to tell that from having looked too early",
  );
});

test("SH689: the family is still the finding's subject, and evidence does not displace it", () => {
  // Both fields were nearly lost to a name collision while this shipped: `merge` writes the subject
  // into `selector`, and the sampled element was first called `selector` too; `widths` already
  // means the viewport widths a finding appeared at. Two accidental overwrites, both caught by
  // this file — hence the two names `at` and `measured`, and hence this assertion.
  const { findings } = classify({
    probes: [
      { width: 1280, fonts: [{ family: "Inter Variable", loaded: false, at: "h1", measured: "x" }] },
      { width: 375, fonts: [{ family: "Inter Variable", loaded: false, at: "h1", measured: "x" }] },
    ],
  });

  assert.equal(findings[0].selector, "Inter Variable", "the subject is the family");
  assert.deepEqual(findings[0].widths, [1280, 375], "and `widths` is still the viewports");
  assert.equal(findings[0].at, "h1");
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
  // SH392 added the third, and it belongs by this test's own rule rather than despite it: a
  // script that does not compile is an *observed fact* — the browser's parser said so — not a
  // judgement about how somebody chose to write it. The two above are the same shape (the console
  // said it, the network said it), and every `warn` is a threshold this command picked.
  assert.deepEqual(
    Object.entries(AUDIT_RULES).filter(([, severity]) => severity === "error").map(([rule]) => rule).sort(),
    ["console-error", "region-script", "request-failed"],
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
  // SH1147 — the shared per-line reading; the cross-line strip this replaced could open inside a
  // string literal and delete the rest of the probe.
  const source = uncommented(probe.toString());
  const moduleScope = ["AUDIT_RULES", "CONTRAST_FLOOR", "PER_RULE_CAP", "DEFAULT_WIDTHS",
    "parseColor", "luminance", "contrastRatio", "classify", "describe(", "line("];
  for (const name of moduleScope) {
    assert.ok(!source.includes(name), `probe references ${name} from module scope`);
  }
  assert.ok(probe.toString().startsWith("function probe(options)"));
});

// --- SH526: the shape of a page, not a picture of it ---

/**
 * Four instruments looked at a page that had lost its hero, its product grid and its footer, and
 * all four passed it: `verify` said **ok**, `/agent/render` counted `links: 0` and said nothing,
 * this command found **one** warning (a font fallback), and `snapshot` had no baseline to compare.
 *
 * <p>They share a blind spot rather than four separate ones: each measures **what is there**. An
 * almost-empty page has no overflow, perfect contrast, no collapsed box and nothing that fails to
 * load — so the seven rules above are all silent on the one defect that matters most.</p>
 */
test("SH526: a page with no region and no heading is two findings, from the tree", () => {
  const { findings, shape } = classify({
    probes: [{ width: 1280, shape: { landmarks: {}, headings: [] } }],
  });

  assert.deepEqual(
    findings.map((finding) => finding.rule).sort(),
    ["no-heading", "no-landmark"],
    "the accessibility tree is the cheapest thing that would have caught it, and the browser "
      + "already builds one",
  );
  assert.ok(findings.every((finding) => finding.severity === "warn"),
    "a warning: an intact page can legitimately have neither, and a rule that failed the build on "
      + "one would be the rule people wrap in || true");
  assert.deepEqual(shape, { landmarks: {}, headings: [] },
    "and the shape rides the result whether or not it produced a finding");
});

test("SH526: a page that kept its regions produces neither finding", () => {
  const intact = {
    landmarks: { main: 1, navigation: 1, contentinfo: 1 },
    headings: [{ level: 1, text: "Acme" }, { level: 2, text: "Products" }, { level: 2, text: "Contact" }],
  };

  const { findings } = classify({ probes: [{ width: 1280, shape: intact }] });

  assert.deepEqual(findings, []);
  assert.equal(
    describeShape(intact),
    "contentinfo, main, navigation · 3 headings",
    "the intact page in one line — the emptied one reads `no landmark · 2 headings`, and that "
      + "difference is the whole of what the other four instruments could not see",
  );
});

test("SH526: the shape is read once, not merged across widths", () => {
  // A landmark is a landmark at every viewport, so a shape per width would be the same finding
  // three times and a report three lines longer for nothing.
  const { findings } = classify({
    probes: [
      { width: 1280, shape: { landmarks: {}, headings: [] } },
      { width: 375, shape: { landmarks: {}, headings: [] } },
    ],
  });

  assert.equal(findings.filter((finding) => finding.rule === "no-landmark").length, 1);
  assert.deepEqual(findings[0].widths, [], "and it carries no width, because it has none");
});

test("SH526: a run with no shape at all reports nothing about it", () => {
  // The pre-SH526 probe shape, which is what an older run of this command produced. Absent is not
  // empty: reporting "no landmark" for a probe that never looked would be an invented finding.
  const { findings, shape } = classify({ probes: [{ width: 1280, overflow: [] }] });

  assert.deepEqual(findings, []);
  assert.equal(shape, undefined);
});

// --- SH392: the field whose value is a program, finally executed ---

test("SH392: a region script that does not parse is an error naming the region", () => {
  // The case with no symptom anywhere else. `Region.JAVASCRIPT` is shipped to the browser and never
  // evaluated in the JVM (SH248), so a region whose script is not even a program renders, hashes,
  // lints and verifies clean — the browser silently runs none of it and the DOM is exactly what the
  // server sent.
  const { findings, regionScripts } = classify({
    probes: [{
      width: 1280,
      regionScripts: [
        { region: "NAV", post: "p1", empty: false, syntaxError: "Unexpected token ')'" },
        { region: "HERO", post: "p2", empty: false, syntaxError: null },
      ],
    }],
  });

  const broken = findings.filter((each) => each.rule === "region-script");
  assert.equal(broken.length, 1, "one broken region, one finding");
  assert.equal(broken[0].severity, "error",
    "an error and not a warning: unlike every other rule this one reports code that cannot run");
  assert.equal(broken[0].selector, 'script[data-shio-region="NAV"]',
    "the selector names the region, which the composer already annotates onto the script tag");
  assert.match(broken[0].region, /NAV/);
  assert.equal(regionScripts.length, 2, "and the scripts ride the result, broken or not");
});

test("SH392: a region script is judged once, not once per viewport", () => {
  // A script is the same bytes at every width, so two loads must not become two findings — the
  // same argument SH526 makes for the shape one rule up.
  const { findings } = classify({
    probes: [
      { width: 1280, regionScripts: [{ region: "NAV", empty: false, syntaxError: "boom" }] },
      { width: 375, regionScripts: [{ region: "NAV", empty: false, syntaxError: "boom" }] },
    ],
  });
  assert.equal(findings.filter((each) => each.rule === "region-script").length, 1);
});

test("SH392: a runtime throw stays a console-error and is not double-reported", () => {
  // The division of labour: this rule answers "does it parse", `pageerror` answers "did it throw".
  // One defect reported by two rules would be two findings that can disagree about the count.
  const { findings } = classify({
    probes: [{ width: 1280, regionScripts: [{ region: "NAV", empty: false, syntaxError: null }] }],
    consoleErrors: [{ text: "TypeError: x is not a function", width: 1280 }],
  });
  assert.equal(findings.filter((each) => each.rule === "region-script").length, 0);
  assert.equal(findings.filter((each) => each.rule === "console-error").length, 1);
});

test("SH392: the reader compiles without running, so no region is initialised twice", () => {
  // `readRegionScripts` is serialized into the page, so this asserts its SOURCE: `new Function` is
  // a parse, and `eval`/`appendChild` would be an execution. The composer emits each region's
  // script once, keyed by name, precisely so a layout that slots a nav twice does not initialise it
  // twice — a checker that re-ran the body would reintroduce exactly that.
  const source = readRegionScripts.toString();
  assert.match(source, /new Function/, "it compiles the body");
  assert.doesNotMatch(source, /\beval\(/, "and never evaluates it");
  assert.doesNotMatch(source, /appendChild|document\.write|import\(/,
    "and never re-injects it, which would run the script a second time");
});

test("SH392: the scripts line is printed even when nothing is wrong, and when there are none", () => {
  // The whole complaint was that this field is delivered and never run. A report that said nothing
  // when a page carries no region script would be indistinguishable from one where every script is
  // fine — so `0 region scripts` is an answer, not an omission.
  const none = describeReport({
    target: "post:Blog/x", url: "https://x/", widths: [1280],
    findings: [], counts: { total: 0, errors: 0, warnings: 0 }, capped: {}, regionScripts: [],
  });
  assert.match(none, /scripts: 0 region scripts on this page/);

  const some = describeReport({
    target: "post:Blog/x", url: "https://x/", widths: [1280],
    findings: [], counts: { total: 0, errors: 0, warnings: 0 }, capped: {},
    regionScripts: [
      { region: "NAV", empty: false, syntaxError: null },
      { region: "HERO", empty: false, syntaxError: "boom" },
    ],
  });
  assert.match(some, /scripts: 2 region scripts compiled · 1 that does not parse/);
});

test("SH392: the command forwards the shape and the scripts, which no unit test could see", async () => {
  // The defect this closes is a wiring one and it had been live since SH526: `classify` returned a
  // `shape`, the command destructured three keys and not that one, and `describe`'s own comment
  // said the line was "printed whether or not it produced a finding". Both halves were correct in
  // isolation, which is why the unit tests above — each calling one function directly — all passed
  // while the command printed neither line. Asserted through `run`, because that is the only view
  // where the two meet.
  const ctx = ctxWith({
    observed: {
      probes: [{
        width: 1280,
        shape: { landmarks: { main: 1 }, headings: [{ level: 1, text: "Hi" }] },
        regionScripts: [{ region: "NAV", empty: false, syntaxError: "Unexpected token" }],
      }],
      requests: [],
      consoleErrors: [],
    },
  });
  await audit.run({}, ["https://blog.example.com/"], ctx);

  assert.match(ctx.stdout.text, /shape: main · 1 heading/,
    "SH526's shape line reaches the reader (it did not, until SH392 forwarded it)");
  assert.match(ctx.stdout.text, /scripts: 1 region script compiled · 1 that does not parse/);
  assert.match(ctx.stdout.text, /error region-script/);
});

test("SH392: --json carries the shape and the scripts too", async () => {
  const ctx = ctxWith({
    observed: {
      probes: [{
        width: 1280,
        shape: { landmarks: { main: 1 }, headings: [] },
        regionScripts: [{ region: "NAV", empty: false, syntaxError: null }],
      }],
      requests: [],
      consoleErrors: [],
    },
  });
  await audit.run({ json: true }, ["https://blog.example.com/"], ctx);
  const report = JSON.parse(ctx.stdout.text);
  assert.deepEqual(report.shape.landmarks, { main: 1 });
  assert.equal(report.regionScripts.length, 1,
    "an agent reading --json gets the same facts as a human reading the text");
});
