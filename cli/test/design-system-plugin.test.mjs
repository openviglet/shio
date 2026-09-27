/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH944 — the design system reaches this repository as an enabled plugin for its skill, hook and
 * command, and as vendored files only for the artboards a plugin cannot put in the file tree.
 *
 * ## The split was the package's to make, and it had made it
 *
 * `viglet-ds-page-reference` reads `enabledPlugins` (VDS135): with `viglet-ds` on, it stops vendoring
 * the skill and reports a lingering copy as an orphan — two skills named `viglet-ds-pages` is the
 * collision SH949 forbids between Shio's own two sets. So enabling the plugin here and running the bin
 * removed `.claude/skills/viglet-ds-pages/`, and what stays vendored is `docs/design/` alone, still
 * gated by SH941 and SH942.
 *
 * ## What was measured, and is not Shio's to fix
 *
 * The plugin's hook and `/viglet-ds-check` resolve the package from the file or package they are
 * given, so both work in `shio-react/`. Its MCP server starts as `npx --no-install viglet-ds-mcp` at
 * the project root, and here the package is installed in the `shio-react` workspace: that command
 * answers `E404`, and the skill's fallback path `node_modules/@viglet/viglet-design-system/docs/`
 * does not exist at the root either. A consumer that installs the package in a workspace is the
 * design system's case to handle, so it is filed in that repository's roadmap.
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// @scans prose — reason: the subject is one JSON file parsed as data and a few paths checked for
// existence; no text is read as a claim about a rule (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const settings = JSON.parse(readFileSync(join(ROOT, ".claude", "settings.json"), "utf8"));

const MARKETPLACE = "viglet-design-system";
const PLUGIN = `viglet-ds@${MARKETPLACE}`;

test("SH944: the design system's plugin is enabled here, from the repository that publishes it", () => {
  assert.equal(
    settings.enabledPlugins?.[PLUGIN], true,
    `SH944: ${PLUGIN} is not enabled in .claude/settings.json, so a session here writes console`
      + " components with no hook to deny the one the package already exports, and the bin goes"
      + " back to vendoring a skill that then drifts.",
  );
  const source = settings.extraKnownMarketplaces?.[MARKETPLACE]?.source;
  assert.deepEqual(
    source, { source: "github", repo: "openviglet/viglet-design-system" },
    `SH944: the marketplace \`${MARKETPLACE}\` must be declared, or a clone enables a plugin its`
      + " Claude Code cannot find and the setting is silently inert.",
  );
});

test("SH944: only the artboards stay vendored, and no copy of the plugin's skill lingers", () => {
  assert.deepEqual(
    ["docs/design/canvas.json", "docs/design/vds-shell.dc.html"].filter((path) => !existsSync(join(ROOT, path))),
    [],
    "SH944: the artboards are the half a plugin cannot distribute; enabling it is no reason to lose them.",
  );
  assert.equal(
    existsSync(join(ROOT, ".claude", "skills", "viglet-ds-pages", "SKILL.md")), false,
    "SH944: a vendored `viglet-ds-pages` beside the enabled plugin is two skills with one name. Run"
      + " `viglet-ds-page-reference` from the repository root; with the plugin on it removes the copy.",
  );
});
