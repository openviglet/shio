#!/usr/bin/env node
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
import { noticeInstanceNotes } from "./instance-hints.mjs";
import { helpText } from "./help.mjs";

const BOOLEANS = [
  "help", "yes", "dry-run", "prune", "check", "skip-types", "id", "ids", "strict",
  // Content-as-files (Block I): `--content` picks the resource, the rest resolve a sync.
  "content", "ours", "theirs", "force", "allow-partial", "show-remote", "cursor", "json",
  // SH967: `shio changes --follow` holds the stream open. SH989: `--fields` names what each write changed.
  "follow", "fields",
  // Blueprints (Block J): `--files` asks for the carried front-end bodies, and the two
  // SH151/SH152 opt-ins let one command create the site and reconcile an existing model.
  "files", "create-site", "merge-post-types",
  // Instance memory (SH114): read the store, or drop one note from it.
  "list", "forget",
  // SH231: adopt the exporting installation's house rules on a `push --content`. Off by
  // default and explicit by construction — that is the whole safety argument (P7).
  "adopt-memory",
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
  // SH543: `clone` reports where it is to two files beside the inventory, because the caller of a
  // two-hour crawl is not watching its pipe. `--no-progress` is for a run that must write only what
  // it was asked for.
  "no-progress",
  // SH546: `publish` names its targets, and `--all` is the flag that says "name them from a find
  // instead of from my argv" — a decision, so a flag. `--unpublish` is the same verb inverted,
  // spelled the way `shio_publish` spells it rather than as a second command.
  "all", "unpublish",
  // SH591: a site declared disposable by the run that made it, and the only set `site rm` will
  // ever act on. A flag rather than a value because it is a declaration, not a parameter.
  "scratch",
  // SH589: the two instruments `judge` will not run unless asked, each for its own reason.
  "browser", "verbose",
  // SH987: `rollback` plans by default and writes only when told, which is `apply`'s shape for the
  // one write whose subject the caller cannot see — a number naming bytes that are not on screen.
  "apply",
];

// Flags that may be repeated. A blueprint's parameters are a set, so last-one-wins would
// apply a package with defaults the caller believed they had overridden (SH93). `address` is a
// set for the same reason (SH640): `diff --against published --address a --address b` asks about
// both, and last-one-wins would quietly answer about one of them.
const REPEATABLE = ["param", "address"];

const COMMANDS = {
  init: () => import("./commands/init.mjs"),
  types: () => import("./commands/types.mjs"),
  context: () => import("./commands/context.mjs"),
  surfaces: () => import("./commands/surfaces.mjs"),
  mcp: () => import("./commands/mcp.mjs"),
  resolve: () => import("./commands/resolve.mjs"),
  impact: () => import("./commands/impact.mjs"),
  apply: () => import("./commands/apply.mjs"),
  publish: () => import("./commands/publish.mjs"),
  import: () => import("./commands/import.mjs"),
  blueprints: () => import("./commands/blueprints.mjs"),
  marketplace: () => import("./commands/marketplace.mjs"),
  verify: () => import("./commands/verify.mjs"),
  changes: () => import("./commands/changes.mjs"),
  report: () => import("./commands/report.mjs"),
  remember: () => import("./commands/remember.mjs"),
  digest: () => import("./commands/digest.mjs"),
  snapshot: () => import("./commands/snapshot.mjs"),
  audit: () => import("./commands/audit.mjs"),
  annotations: () => import("./commands/annotations.mjs"),
  judge: () => import("./commands/judge.mjs"),
  site: () => import("./commands/site.mjs"),
  "post-type": () => import("./commands/post-type.mjs"),
  clone: () => import("./commands/clone.mjs"),
  propose: () => import("./commands/propose.mjs"),
  convert: () => import("./commands/convert.mjs"),
  diff: () => import("./commands/diff.mjs"),
  versions: () => import("./commands/versions.mjs"),
  schedule: () => import("./commands/schedule.mjs"),
  rollback: () => import("./commands/rollback.mjs"),
  pull: () => import("./commands/pull.mjs"),
  push: () => import("./commands/push.mjs"),
  build: () => import("./commands/build.mjs"),
  dev: () => import("./commands/dev.mjs"),
  deploy: () => import("./commands/deploy.mjs"),
  doctor: () => import("./commands/doctor.mjs"),
};

const HELP = helpText("shio");

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
  const ctx = defaultContext();
  // SH587 — say that this instance carries notes before the command speaks, not after. The
  // notes are what an earlier session worked out about the site the caller is about to act on,
  // so they change what the run does; printed underneath a report they would be a footnote.
  // On stderr, because a command's stdout is somebody's `--json` pipe.
  await noticeInstanceNotes(command, flags, ctx);
  const result = await mod.run(flags, positionals, ctx);
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
