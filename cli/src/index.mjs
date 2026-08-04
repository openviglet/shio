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
                 (--blueprint <name> takes the starter from the instance instead;
                  add --site <site> to apply its content in the same command)
  context        Print the agent context pack: model + sitemap + conventions (one call)
  mcp            Bridge stdio to this instance's MCP endpoint (claude mcp add shio -- shio mcp)
  resolve <addr> Resolve a content address (post:/folder:/site:/id:) to its object
  impact <addr>  What a change to this template would affect: layouts, types, page counts
  apply [file]   Plan/apply a desired-state content document (--dry-run, --check, - for stdin)
                 or a ready-made package (--blueprint <name> --site <site>;
                  --create-site makes the site, --merge-post-types adds missing fields)
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

Context pack (shio context):
  --format <fmt>       terse (default), json, or agents-md
  --as agents-md       Render the pack as project agent instructions (same as
                       --format agents-md); pair with --out AGENTS.md so the file
                       cannot rot — regenerate it and the diff is the drift
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

Blueprints (shio blueprints / shio apply --blueprint):
  A blueprint is a section of a site you do not have to build: a content model, a folder
  tree, sample pages, the parameters that make it yours, and the promise it keeps about
  what exists afterwards. Applying one is idempotent, so re-running it is safe.
  shio blueprints                 The catalogue, with each package's parameters
  shio blueprints blog            One package in detail (--files for the carried files)
  --blueprint <name>   apply: the package to apply
  --site <site>        apply: the site to apply it into (required)
  --param k=v          apply: one parameter value; repeat for several
  --params <file.json> apply: parameter values from a file (--param wins over it)
  --force              apply: overwrite existing front-end files
  --create-site        apply: create the site (blank) if it does not exist yet
  --merge-post-types   apply: add fields an existing post-type lacks; never changes one
  --dry-run            apply: the whole plan, post-types included, written nowhere
  shio apply --blueprint marketing-site --site Acme --param brand=Acme --dry-run
  shio apply --blueprint marketing-site --site Acme --param brand=Acme --create-site

Verification (shio verify / shio digest):
  shio verify is the content-side type-check: empty required fields, references to posts
  that do not exist, images with no bytes, two pages on one URL, empty folders, links
  that resolve to nothing, and anything that failed asynchronously. Errors exit 1;
  warnings are printed. Run it before handing work over.
  --site <idOrName>    Scope to one site
  --checks <groups>    projection,content,routes,diagnostics,delivery (default all that
                       can run). projection is this tool's own group and reads the file
                       tree — post-types, posts and asset bytes — so --checks projection
                       needs no instance at all and is the cheapest gate there is. Run it
                       first in CI; the full run includes it too.
  --delivery           Also prove each published route by fetching it from the site's
                       own origin — turns "the front end does not serve this" from a
                       warning into an error. Needs the instance's allow-fetch and each
                       site's URL; declare front-end-owned routes with
                       shio remember frontend-routes "/tags/* /search" --site <site>
  --limit <n>          Findings to print (the counts always cover the whole run)
  --strict             Fail on warnings too (off by default — see the severity rule)
  --folder <address>   Scope to a folder subtree (folder:<site>/<chain>)
  --address <a,b>      Scope to named posts (post:<site>/<url>, comma-separated)
  --since <cursor>     Scope to what changed after an SH88 cursor (shio changes --cursor)
  A scoped run says so: the report carries scope/partial, and the counts cover the subset.
  --baseline <file>    Accepted findings to subtract   (default shio/verify-baseline.json)
  --accept             Write this run's findings into the baseline (--reason "why")
  A baseline entry whose finding no longer exists fails the run — remove it when you fix
  the defect, or the file rots into a suppression that hides the next one.
  shio digest <page>   post:<site>/<url>, or a delivery URL when fetching is enabled
  --address / --url    Same, as explicit flags

The handoff (shio report):
  shio report renders the summary a *human* reads: what changed, a preview link per page,
  and the questions the CMS cannot answer itself. Markdown on stdout — paste it into a PR
  body, or --out FILE. It composes changes + context + verify; there is no new endpoint.
  --since <cursor>     Range start; "last" = where the last --mark left off,
                       "content" = where the last content pull reached. Omit for a window
  --mark               Record this range as reviewed, so the next --since last resumes.
                       Refused when the report could not cover its whole range
  shio snapshot <page> A PNG for a human to look at, and whether it changed (SH102).
                       Baselines live in shio/snapshots/; the displaced one is kept as
                       <slug>.prev.png. A change exits 0 — a page looking different is
                       usually the change you just made — use --fail-on-change for a gate.
                       Optional deps: playwright (required), pixelmatch + pngjs for a
                       per-pixel diff; without them it answers changed/unchanged by bytes.
  --accept             Replace the baseline without reporting a change
  --fail-on-change     Exit 1 when the page looks different
  --width / --height   Viewport (default 1280x800)  --viewport-only  Skip the full page
  --site <name>        One site only
  --out <file>         Write the Markdown instead of printing it
  --title <text>       Heading for the document
  --no-verify          Skip the open-questions section (change list only)
  --json               The report as data (markdown, cursor, changed, caveats)
  shio report --since last --mark --out HANDOFF.md

Auditing a rendered page (shio audit):
  The other browser command, and the one for the agent: it renders the page and prints what is
  wrong with it as **text**, one line per finding with the selector to fix. A screenshot costs
  ~1.6k tokens, cannot be diffed and asks you to judge; twenty findings cost a few hundred, grep
  and diff, and say what to change. Needs playwright (the same optional install as snapshot).
  Errors — a request that 404'd, a console error — exit 1. Warnings — overflow, contrast, a
  collapsed box, a fallback font — are printed and exit 0, because each of those is design intent
  often enough that a gate on them is the exit code people learn to ignore.
  shio audit post:Blog/blog/hello-world
  --page <page>        The page to audit (or a bare positional, or --address).
                       NOT --url: that is the instance, here as in every other command
  --width <list>       Viewport widths, comma-separated  (default 1280,375)
  --height <px>        Viewport height                   (default 800)
  --contrast <ratio>   Contrast floor                    (default 4.5, WCAG AA; large text 3)
  --preview            Audit the DRAFT through this instance's renderer
  --limit <n>          Findings to print (the counts always cover the whole run)
  --strict             Exit 1 on warnings too
  --json               The findings as data

Capturing a site that exists (shio clone):
  The first of replication's three phases — capture, convert, prove. It walks ONE origin and
  writes NO content: one inventory at shio/.shio/source-inventory.json plus a report naming
  what it found — the pages with their titles and paths, the assets they reference, the locales
  and how each shows up in a URL, the forms and their fields, the navigation as the source
  spells it, and the third-party scripts. Everything downstream is scoped off that file, which
  is what makes a conversion reviewable before it writes anything.
  Four rules it keeps: every link off the host is recorded and never followed; a seed that
  redirects off-host is refused, naming the host it actually got; the site's own robots.txt
  bounds it; and the walk is sequential over a sorted frontier, so a ceiling cuts the same
  pages every run and the inventory can be diffed against the next crawl.
  shio clone https://example.com --depth 3 --max-pages 200
  --depth <n>          How many link levels to follow    (default 3)
  --max-pages <n>      Page ceiling; hitting it marks the inventory PARTIAL (default 200)
  --render             Drive a browser, for a page whose markup arrives only after hydration
                       (needs playwright). Without it the crawl reads what the server sent,
                       and the report warns when a page looks like it renders itself
  --ignore-robots      Skip robots.txt — for your own site. Recorded in the inventory, so a
                       capture taken without permission cannot later look like one taken with it
  --delay <seconds>    Wait between requests (default: the site's own Crawl-delay)
  --out <file>         Inventory path       (default shio/.shio/source-inventory.json)
  --limit <n>          Rows per report section (the inventory always holds everything)
  --json               Print the inventory instead of the report

Content as files (shio pull/push --content, shio dev --content):
  Content projects to shio/content/<site>/<url>.md — YAML frontmatter is the fields,
  the body is the post's rich-text field. Edit with Edit/Write/Grep, then push.
  A pull merges three ways (last-synced base, server, files): only a field both sides
  changed is a conflict, and a conflict writes nothing and exits non-zero.
  Binaries project to shio/assets/<site>/<folder>/<name>, and a File field reads as
  that path instead of a UUID — so the tree is a complete, movable, reviewable
  description of the site. A push uploads what the server lacks, once per path, and an
  asset's bytes are merged three ways like everything else: whichever side edited them
  wins, both sides editing is a conflict that writes nothing, and neither direction
  overwrites the other silently.
  An empty folder is a file too: shio/folders/<site>/<chain>/_folder.md declares a
  folder nothing else does, so you can scaffold the structure before filling it. It is
  a separate root because a folder chain and a friendly URL are separate namespaces.
  Renaming a file is a move, so the post keeps its id, history and references: push
  pairs it up by content, or by movedFrom: <old url> when you rewrote it as well.
  It refuses to guess between two candidates, and warns when it cannot prove a pair.
  Renaming a folder's directory is a move too, so the section keeps its posts: push
  pairs it by subtree shape, or by movedFrom: <old path> in the _folder.md.
  --accept             push: record this run's unprovable renames as reviewed and push
                       nothing. They stop being warned about, and each acceptance is
                       spent by the run that uses it — a delete-plus-create cannot
                       happen twice, so there is nothing left to go stale. Both
                       addresses are the key: accepting one deletion never silences a
                       different arrival. --reason "why" travels with the entry, and
                       --baseline picks the file (default shio/verify-baseline.json,
                       shared with shio verify, which never touches this list).
  --site <name>        Only this site
  --content-dir <dir>  Content root            (default shio/content)
  --folders-dir <dir>  Folder-marker root      (default shio/folders)
  --assets-dir <dir>   Asset root              (default shio/assets)
  --max-ops <n>        push: ops per document; a bigger tree splits (default 1500)
  --max-folder-reads <n>
                       push: how many asset folders to list one at a time before
                       reading a whole site instead (default 8). Measured: a folder
                       listing reads that folder's rows only, so this bounds round
                       trips rather than server work — lower it on a slow link,
                       raise it on a fast one with a large media library.
  --prune              push: delete server posts the file tree does not declare
  --check / --dry-run  push: drift guard / plan only, same meanings as for post-types
  --ours / --theirs    pull: resolve conflicts in one direction
  --show-remote        pull: write the server's copy as <file>.remote next to a conflict
  --allow-partial      pull: accept a truncated post list (marks the snapshot partial)
  --force              push: proceed despite conflicts or a partial snapshot
  The last-synced base and the change cursor live in shio/.shio/content-base.json. The
  base holds a hash per field, not a copy of your content, so it is safe to commit.

Instance memory (shio remember):
  What one session works out, the next is told: notes come back in the context pack's
  conventions, attributed to whoever wrote them. Re-using a key rewrites that note.
  shio remember                          List what is already remembered
  shio remember <key> "<note>"           Remember it
  shio remember <key> --forget           Forget it
  --site <name>        Scope the note to one site (default: the whole instance)
  --limit <n>          Notes to list

Change feed (shio changes):
  --since <cursor>     Only what happened after that position ("last" = last pull's)
  --cursor             Print only the cursor (SINCE=$(shio changes --cursor))
  --site <name>        Scope to one site
  --limit <n>          Entries to print
  --json               Raw JSON instead of lines

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

  const { flags, positionals } = parseArgs(rest, {
    booleans: BOOLEANS,
    repeatable: REPEATABLE,
  });
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
  process.stderr.write(formatError(err, { debug: Boolean(process.env.SHIO_DEBUG) }));
  exit(1);
});
