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
import { argv, exit } from "node:process";

import { parseArgs } from "./args.mjs";
import { defaultContext } from "./context.mjs";

const BOOLEANS = ["help", "yes", "dry-run", "prune", "check", "skip-types", "id", "ids", "strict"];

const COMMANDS = {
  init: () => import("./commands/init.mjs"),
  types: () => import("./commands/types.mjs"),
  context: () => import("./commands/context.mjs"),
  mcp: () => import("./commands/mcp.mjs"),
  resolve: () => import("./commands/resolve.mjs"),
  apply: () => import("./commands/apply.mjs"),
  verify: () => import("./commands/verify.mjs"),
  digest: () => import("./commands/digest.mjs"),
  pull: () => import("./commands/pull.mjs"),
  push: () => import("./commands/push.mjs"),
  build: () => import("./commands/build.mjs"),
  dev: () => import("./commands/dev.mjs"),
  deploy: () => import("./commands/deploy.mjs"),
};

const HELP = `shio — the Shio CMS CLI

Usage:
  shio <command> [options]

Commands:
  init [name]    Scaffold a Next.js site wired to the Shio CDA
  context        Print the agent context pack: model + sitemap + conventions (one call)
  mcp            Bridge stdio to this instance's MCP endpoint (claude mcp add shio -- shio mcp)
  resolve <addr> Resolve a content address (post:/folder:/site:/id:) to its object
  apply [file]   Plan/apply a desired-state content document (--dry-run, --check, - for stdin)
  verify         Lint the content and its routes; exits non-zero on an error (CI gate)
  digest <page>  Structural digest of one page: outline, landmarks, links, images
  pull           Pull post-types into ./shio/post-types/*.json (Basic auth)
  push           Apply local post-types back to Shio (--dry-run, --prune)
  build          Compile local DSL/JSON post-types to canonical JSON (--out)
  types          Generate a TypeScript .d.ts from the live post-types (CDA token)
  dev            Generate types, start the site dev server, watch the model
  deploy         push (apply) then types

Post-types as code:
  shio/post-types/ may hold *.json or *.ts/*.mjs DSL sources (@viglet/shio-model).
  push and build compile DSL sources to canonical JSON before diffing/emitting.

Connection:
  --url <baseUrl>      Shio base URL          (env SHIO_URL, default http://localhost:2710)
  --user <user>        Console user           (env SHIO_USER)        [pull/push/deploy]
  --password <pass>    Console password       (env SHIO_PASSWORD)    [pull/push/deploy]
  --token <token>      CDA token              (env SHIO_CDA_TOKEN)   [types/dev]

Context pack (shio context):
  --format <fmt>       terse (default) or json
  --site <idOrName>    Scope to one site      (omit for every readable site)
  --include <sections> instance,sites,model,sitemap,conventions (default all)
  --depth <n>          Folder-tree depth to walk
  --budget <tokens>    Estimated-token ceiling (0 = unbounded)
  --from <id>          Resume cursor from a truncated pack
  --ids                Put object ids back on every row (off by default — rows are
                       addressable without one, and a UUID is the priciest field)

MCP (shio mcp):
  Speaks newline-delimited JSON-RPC on stdin/stdout and forwards it to POST /mcp.
  Register it with:  claude mcp add shio -- shio mcp
  Uses the same --url/--user/--password (or SHIO_URL/SHIO_USER/SHIO_PASSWORD).

Content as a document (shio apply):
  A desired-state document says what a site section should contain; applying it
  twice changes nothing the second time, so it survives a compaction.
  --dry-run            Print the plan, write nothing
  --check              Drift guard — plan, and exit non-zero if anything would change
  --ids                Put object ids on the change rows (off by default)
  shio apply site.json --dry-run
  echo '{"site":"Blog","folders":[{"path":"/blog"}]}' | shio apply -

Verification (shio verify / shio digest):
  shio verify is the content-side type-check: empty required fields, references to posts
  that do not exist, images with no bytes, two pages on one URL, empty folders, links
  that resolve to nothing, and anything that failed asynchronously. Errors exit 1;
  warnings are printed. Run it before handing work over.
  --site <idOrName>    Scope to one site
  --checks <groups>    content,routes,diagnostics (default all)
  --limit <n>          Findings to print (the counts always cover the whole run)
  --strict             Fail on warnings too (off by default — see the severity rule)
  shio digest <page>   post:<site>/<url>, or a delivery URL when fetching is enabled
  --address / --url    Same, as explicit flags

Addresses (shio resolve):
  post:<site>/<url>    A post, by its friendly URL
  folder:<site>/<path> A folder, by its chain of folder names
  site:<name>          A site (or site:<name>/<path> to resolve whatever is there)
  id:<uuid>            An object id, when you have one
  --id                 Print only the resolved id (pipeline-friendly)

Common options:
  --dir <dir>          Post-types directory   (default shio/post-types) [pull/push/build]
  --out <file>         Output path            (.d.ts for types; dir for pull/build, default shio/.out;
                       a file for context)
  --dry-run            push/apply: print the plan, change nothing
  --check              push/apply: drift guard — exit non-zero if the plan is non-empty (CI)
  --prune              push: delete remote types absent locally (never system types)
  -h, --help           Show help
`;

async function main() {
  const [command, ...rest] = argv.slice(2);

  if (!command || command === "--help" || command === "-h" || command === "help") {
    process.stdout.write(HELP);
    return;
  }

  const loader = COMMANDS[command];
  if (!loader) {
    process.stderr.write(`shio: unknown command "${command}"\n\n${HELP}`);
    exit(1);
  }

  const { flags, positionals } = parseArgs(rest, { booleans: BOOLEANS });
  if (flags.help) {
    process.stdout.write(HELP);
    return;
  }

  const mod = await loader();
  const result = await mod.run(flags, positionals, defaultContext());
  // Commands may request a non-zero exit (e.g. `push --check` drift guard, SH65).
  if (result && typeof result.exitCode === "number" && result.exitCode !== 0) {
    exit(result.exitCode);
  }
}

main().catch((err) => {
  process.stderr.write(`shio: ${err?.message ?? err}\n`);
  exit(1);
});
