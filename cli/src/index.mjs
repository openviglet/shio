#!/usr/bin/env node
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * shio — the Shio CMS command-line interface (SH16). The "JS dev, zero Java"
 * workflow: scaffold a site, generate types, and manage the content model as
 * code (post-types pull/push).
 *
 *   shio init my-site
 *   shio pull --url http://localhost:2710 --user admin --password admin
 *   shio push --dry-run
 *   shio types --token <CDA token> --out lib/shio-types.d.ts
 *   shio dev
 *   shio deploy
 *
 * Env fallbacks: SHIO_URL, SHIO_USER, SHIO_PASSWORD (pull/push/deploy),
 * SHIO_CDA_TOKEN (types/dev).
 */
import { argv } from "node:process";

import { parseArgs } from "./args.mjs";
import { finish } from "./exit.mjs";
import { formatError } from "./format-error.mjs";
import { defaultContext } from "./context.mjs";

const BOOLEANS = [
  "help", "yes", "dry-run", "prune", "check", "skip-types", "id", "ids", "strict",
  // Content-as-files (Block I): `--content` picks the resource, the rest resolve a sync.
  "content", "ours", "theirs", "force", "allow-partial", "show-remote", "cursor", "json",
  // Blueprints (Block J): `--files` asks for the carried front-end bodies, and the two
  // SH151/SH152 opt-ins let one command create the site and reconcile an existing model.
  "files", "create-site", "merge-post-types",
  // Instance memory (SH114): read the store, or drop one note from it.
  "list", "forget",
  // Accepted findings (SH142): `--accept` writes the baseline. `--baseline` is NOT here —
  // it is bare (use the default path) or takes one (use that file), and both are useful.
  "accept",
  // SH141: prove each published route by fetching it, rather than resolving it against the
  // content graph. A flag rather than a value because it is a decision, not a parameter.
  "delivery",
  // SH104: `--mark` records the position a handoff covered; `--no-verify` drops the open
  // questions when the caller only wants the change list.
  "mark", "no-verify",
  // SH102: a visual change is not a failure, so the gate is opt-in — and `--accept` moves the
  // baseline deliberately rather than as a side effect of looking.
  "fail-on-change", "viewport-only",
  // SH102/SH391: force the instance's own preview even for a site that has a front end — the only
  // way to look at a draft.
  "preview",
  // SH377: `--render` drives a browser so a client-rendered page is captured as it renders, and
  // `--ignore-robots` is for your own site — it is recorded in the inventory, so a capture taken
  // without permission cannot later be mistaken for one taken with it.
  "render", "ignore-robots",
  // SH378: download the bytes the inventory names. Opt-in, because SH377's default promise is that
  // a capture writes nothing you have not read the inventory for first.
  "assets",
  // SH379: keep the captured HTML, which is what `shio propose` computes a mapping from. Opt-in for
  // the same reason, and it costs no extra request — the bytes were already in hand.
  "markup",
  // SH469: take the site's own `<script src>` as well, so the replica works and not only renders.
  // Opt-in because `--render` bakes the post-hydration DOM and handing that page its bundle back is
  // a hydration nobody asked for — but the *absence* is reported either way.
  "scripts",
];

// Flags that may be repeated. A blueprint's parameters are a set, so last-one-wins would
// apply a package with defaults the caller believed they had overridden (SH93).
const REPEATABLE = ["param"];

const COMMANDS = {
  init: () => import("./commands/init.mjs"),
  types: () => import("./commands/types.mjs"),
  context: () => import("./commands/context.mjs"),
  mcp: () => import("./commands/mcp.mjs"),
  resolve: () => import("./commands/resolve.mjs"),
  impact: () => import("./commands/impact.mjs"),
  apply: () => import("./commands/apply.mjs"),
  import: () => import("./commands/import.mjs"),
  blueprints: () => import("./commands/blueprints.mjs"),
  verify: () => import("./commands/verify.mjs"),
  changes: () => import("./commands/changes.mjs"),
  report: () => import("./commands/report.mjs"),
  remember: () => import("./commands/remember.mjs"),
  digest: () => import("./commands/digest.mjs"),
  snapshot: () => import("./commands/snapshot.mjs"),
  audit: () => import("./commands/audit.mjs"),
  clone: () => import("./commands/clone.mjs"),
  propose: () => import("./commands/propose.mjs"),
  convert: () => import("./commands/convert.mjs"),
  pull: () => import("./commands/pull.mjs"),
  push: () => import("./commands/push.mjs"),
  build: () => import("./commands/build.mjs"),
  dev: () => import("./commands/dev.mjs"),
  deploy: () => import("./commands/deploy.mjs"),
};

const HELP = `shio — the Shio CMS CLI

Usage:
  shio <command> [options]
  shio <command> --help      That command's own flags, with what each one means
                             (this list names the commands; it stopped describing
                              their flags when each command grew its own help)

Commands:
  init [name]    Scaffold a Next.js site wired to the Shio CDA
                 (--blueprint <name> takes the starter from the instance instead;
                  add --site <site> to apply its content in the same command)
  context        Print the agent context pack: model + sitemap + conventions (one call)
  mcp            Bridge stdio to this instance's MCP endpoint (claude mcp add shio -- shio mcp)
  resolve <addr> Resolve a content address (post:/folder:/site:/id:) to its object
  impact <addr>  What a change to this template would affect: layouts, types, page counts
  apply [file]   Plan/apply a desired-state content document (--dry-run, --check, - for stdin)
                 or a ready-made package (--blueprint <name> --site <site>;
                  --merge-post-types adds missing fields). --create-site makes the site
                  in either mode, and means the same thing on convert
  import <file>  Clone an SH68 exchange package (.zip) into this instance — the way to accept
                 a site somebody hands you (export it with GET /api/v2/site/{id}/export)
  blueprints     List the appliable starting points (blueprints <name> for its parameters)
  verify         Lint the content and its routes; exits non-zero on an error (CI gate)
  changes        What changed, with a cursor to resume from (--since, --cursor)
  report         The human handoff: what changed, preview links, open questions
  remember       Write down a decision the next session should not re-derive
  digest <page>  Structural digest of one page: outline, landmarks, links, images
  snapshot <page> PNG of a page + whether it looks different (needs playwright)
  audit <page>   What is wrong with a rendered page, as text with a selector each
  clone <url>    Capture a site that already exists: an inventory, and no content
  propose        Which captured blocks could become sections, and what each would lose
  convert        Apply the accepted conversion plan: sections, theme and scripts, as DRAFT
  pull           Pull post-types into ./shio/post-types/*.json (--content for pages)
  push           Apply local post-types back to Shio (--dry-run, --prune, --content)
  build          Compile local DSL/JSON post-types to canonical JSON (--out)
  types          Generate a TypeScript .d.ts from the live post-types (CDA token)
  dev            Generate types, start the site dev server, watch the model
                 (--content: sync shio/content/** both ways and log events instead)
  deploy         push (apply) then types

Post-types as code:
  shio/post-types/ may hold *.json or *.ts/*.mjs DSL sources (@viglet/shio-model).
  push and build compile DSL sources to canonical JSON before diffing/emitting.

Connection:
  --url <baseUrl>      Shio base URL          (env SHIO_URL, default http://localhost:2710)
  --user <user>        Console user           (env SHIO_USER)        [pull/push/deploy]
  --password <pass>    Console password       (env SHIO_PASSWORD)    [pull/push/deploy]
  --token <token>      CDA token              (env SHIO_CDA_TOKEN)   [types/dev]

Common options:
  --dir <dir>          Post-types directory   (default shio/post-types) [pull/push/build]
  --out <file>         Output path            (.d.ts for types; dir for pull/build, default shio/.out;
                       a file for context)
  --dry-run            push/apply: print the plan, change nothing
  --check              push/apply: drift guard — exit non-zero if the plan is non-empty (CI)
  --prune              push: delete remote types absent locally (never system types)
  -h, --help           Show help
`;

/**
 * One command's help, from the command itself (SH435).
 *
 * <p>Loading the module to print its help is the whole point: the strings live beside the `flags.`
 * reads they describe, so a new flag and its meaning are one edit apart. A module with no `HELP` is
 * a defect `help.test.mjs` fails the build for — falling back to the global text here would hide it,
 * and the global text is the wrong answer to a question about one command.</p>
 */
async function printCommandHelp(command) {
  const mod = await COMMANDS[command]();
  process.stdout.write(`${mod.HELP.trim()}\n`);
}

async function main() {
  const [command, ...rest] = argv.slice(2);

  // SH435 — `shio help <command>` is the other spelling of `shio <command> --help`, and it is
  // answered here so `help` keeps working as a command name rather than becoming one.
  if (command === "help" && COMMANDS[rest[0]]) {
    await printCommandHelp(rest[0]);
    return;
  }
  if (!command || command === "--help" || command === "-h" || command === "help") {
    process.stdout.write(HELP);
    return;
  }

  const loader = COMMANDS[command];
  if (!loader) {
    process.stderr.write(`shio: unknown command "${command}"\n\n${HELP}`);
    await finish(1);
    return;
  }

  const { flags, positionals } = parseArgs(rest, {
    booleans: BOOLEANS,
    repeatable: REPEATABLE,
  });
  // SH435 — the command's own help, not the global one. This branch used to print `HELP` before
  // `loader()` ran, so no command was ever consulted about its own flags: `shio clone --help`
  // answered with forty lines about every other command and none about clone's fourteen. The cost
  // landed on the agent, which is what makes it a P3 item and not a polish one — learning a
  // command's interface meant grepping its source for `flags.`, having already paid for a help
  // text that answered something else.
  if (flags.help) {
    await printCommandHelp(command);
    return;
  }

  const mod = await loader();
  const result = await mod.run(flags, positionals, defaultContext());
  // Commands may request a non-zero exit (e.g. `push --check` drift guard, SH65).
  if (result && typeof result.exitCode === "number" && result.exitCode !== 0) {
    await finish(result.exitCode);
  }
}

// SH440 — every non-zero exit goes through `finish`, which disarms the request layer first. A
// thrown error is the path that used to abort: it reaches here with a fetch handle still live, and
// `process.exit` then asserted in libuv's teardown rather than exiting. Success returns instead,
// so a pending stdout write is not truncated on the way out.
try {
  await main();
} catch (err) {
  process.stderr.write(formatError(err, { debug: Boolean(process.env.SHIO_DEBUG) }));
  await finish(1);
}
