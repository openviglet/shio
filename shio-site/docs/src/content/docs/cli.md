---
title: The shio CLI
description: "@viglet/shio, the shio command: scaffold a site, sync content and post types both ways, verify, replicate an existing site, and gate a pipeline on drift."
sidebar:
  label: The shio CLI
---

This page is for developers and agents who drive a Viglet Shio instance from a shell or a CI
job. The `shio` command is the scripted path into an instance: what a pipeline runs, what
`claude mcp add` bridges to, and what you use when a shell fits better than an API call. It has
no runtime dependencies, and every command prints its own flags (`shio verify --help`, or
`shio help verify`).

---

## Connect to an instance

1. Run the CLI without installing it:

   ```bash
   npx @viglet/shio --help
   ```

2. Point it at your instance and sign in, with the variables in [Connecting](#connecting):

   ```bash
   export SHIO_URL=https://cms.example.com
   export SHIO_USER=admin SHIO_PASSWORD=…
   ```

3. Check that you can work there:

   ```bash
   shio doctor
   ```

Each line of the report is `ok`, `FAIL` or `??`, with a fix for each problem (see
[What `shio doctor` checks](#what-shio-doctor-checks)).

## Gate a pull request on drift

Run the three gates in this order, so each failure makes sense after the one before it:

1. Does the model on the server match git?

   ```bash
   shio push --check
   ```

2. Does the content match git?

   ```bash
   shio push --content --check
   ```

3. Is what is there internally consistent and reachable?

   ```bash
   shio verify
   ```

`--check` prints the plan, writes nothing, and **exits non-zero when the plan is not empty**, so
the job fails on drift instead of reconciling it. `verify` exits non-zero on an error. Put
`shio doctor` in front of a longer job: it exits `1` when a check fails, so a wrong URL or a
refused credential stops the job at once with the fix printed. A check it could not answer does
not fail it. Every non-zero exit closes cleanly, so a piped run keeps its last lines.

## Change one field from a script

**Before you start:** a token of **write** scope (`--token`, or `SHIO_WRITE_TOKEN`) as well as
the console user that resolves the address. An agent token cannot write this way.

1. Set the field. The command prints the post's new tag:

   ```bash
   shio post set post:Blog/hello title="Hello again"
   # post:Blog/hello set title (draft) etag="3a22…"
   ```

2. Write again only if nobody changed the post since, by passing that tag:

   ```bash
   shio post set post:Blog/hello summary="Shorter" --if-match '"3a22…"'
   ```

The change goes to the post's draft; `--publish` publishes it too. If the post changed between
the command's read and its write, the write is refused and nothing is written.

## Hand a site to another instance

1. On the source instance, download the site as an exchange package:

   ```bash
   shio export site:Blog --out blog.zip
   ```

2. On the target instance, import it:

   ```bash
   shio import blog.zip
   ```

The import prints how many sites, folders, posts, post types and files arrived, and a line for
each problem: a post skipped because its folder was not in the package, a post type neither the
package nor this instance defines, a file listed without its bytes.

A package carries the site, its folders, every post in both states, the post types those posts
use, the site's notes, and the bytes of its uploaded files. It does not carry users, tokens,
permissions, revisions, releases or schedules, so it is a hand-over, not a backup or a promotion
between environments.

---

## Connecting

| Flag | Environment | Used by |
|---|---|---|
| `--url <baseUrl>` | `SHIO_URL` (default `http://localhost:2710`) | Everything |
| `--user` / `--password` | `SHIO_USER` / `SHIO_PASSWORD` | `pull`, `push`, `deploy` (console Basic auth) |
| `--token <token>` | `SHIO_CDA_TOKEN` | `types`, `dev` (a delivery token) |
| `--token <token>` | `SHIO_WRITE_TOKEN` | `post set` (a token of **write** scope) |

---

## The commands

### Start something

| Command | Does |
|---|---|
| `shio init [name]` | Scaffold a Next.js site wired to the delivery API. `--blueprint <name>` takes the starter from the instance instead; add `--site <site>` to apply its content in the same command |
| `shio blueprints` | List the appliable starting points; `shio blueprints <name>` prints one package's parameters |
| `shio marketplace <list\|show\|install\|uninstall>` | The package catalogue: what this instance could install, and installing it. `install` needs an administrator. See [Blueprints § Where more packages come from](/docs/blueprints/#where-more-packages-come-from) |
| `shio import <file.zip>` | Clone an exchange package into this instance, printing what arrived and a line for each thing it could not bring |
| `shio export site:<name>` | Download a site as an exchange package (`--out <file.zip>`); the same file the console's **Download** saves |

### Discover

| Command | Does |
|---|---|
| `shio doctor` | Checks the instance, the render model, the sites and the local content tree, and prints a fix for each problem. Run it first in a new session |
| `shio status` | The instance in one call: counts, what is waiting, what just happened. The same numbers the console's home shows; a row is printed only when it is not zero |
| `shio site` | List the sites, marking the ones declared disposable. `shio site rm --scratch` deletes every site created with the scratch flag (`shio convert --create-site --scratch`) in one confirmation; a site without the flag is never touched. The delete is **permanent**, so try `--dry-run` first |
| `shio context` | The agent context pack (model, sitemap and conventions) in one call. `--out <file>` writes it |
| `shio resolve <address>` | Resolve `post:` / `folder:` / `site:` / `id:` to the object it names |
| `shio impact <address>` | What a change to this template would affect: layouts, types, page counts |
| `shio changes` | What changed, with a cursor to resume from (`--since`, `--cursor`) |
| `shio remember` | The instance memory: bare lists it, `<key> "<note>"` writes one, `--forget` drops one, `--site` scopes it |
| `shio mcp` | Bridge stdio to this instance's MCP endpoint: `claude mcp add shio -- shio mcp` |

#### What `shio doctor` checks

```text
ok   instance     up at http://localhost:2710, version dev (commit 59ff6b5, built 2026-11-08T14:02:11Z)
ok   model        the render post-types are provisioned and match this build
ok   sites        2: Blog, Docs
??   content      no local content tree (shio/content absent) — `shio pull --content` makes one
```

| Check | Asks |
|---|---|
| `instance` | Does the instance answer, do your credentials work, and **which build is it running**? The build time says whether the server has your latest change |
| `model` | Are the `PageLayout`/`Region`/`Theme` post types provisioned, and do they match this build? |
| `sites` | Which sites the instance holds |
| `content` | Is there a local `shio/content` tree to compare? |

Each line is `ok`, `FAIL` or `??`. `??` means *could not tell*: when the instance does not
answer, the checks that need it say so instead of each reporting a failure. `--json` prints the
same checks as data.

### The content model

| Command | Does |
|---|---|
| `shio pull` | Post types into `shio/post-types/*.json` |
| `shio push` | Local post types back to the instance (`--dry-run`, `--check`, `--prune`). `--prune` never removes a system type |
| `shio build` | Compile local DSL/JSON post types to canonical JSON without touching the server (`--out`) |
| `shio types` | Generate a TypeScript `.d.ts` from the live post types |
| `shio deploy` | `push` then `types` |

`push` and `build` compile TypeScript DSL sources to canonical JSON before diffing, so a model
written as typed TypeScript and one written as JSON push identically.

### Content

| Command | Does |
|---|---|
| `shio pull --content` | Project the site to `shio/content/**` (three-way merge) |
| `shio push --content` | Compile the tree into a desired-state document and apply it |
| `shio apply [file]` | Plan/apply a desired-state document, `--dry-run`, `--check`, `-` for stdin. `--blueprint <name> --site <site>` applies a package instead; `--create-site` makes the site in either mode |
| `shio dev --content` | Watch `shio/content/**` and sync both ways, logging events |
| `shio preview <file>` | Render one content file's page **before you push it**, saving nothing: prints the page's outline and structural hash and writes the markup to a file you can open (`--out`). `--state published` renders against the live row. The page must already exist on the instance; for a new file, push it once or pass `--address` |
| `shio post set <address> <field>=<value>…` | Change some fields of one post's draft and leave the rest. `field:=json` sets a number, `true`, a list or an object; `--publish` publishes after the change; `--if-match <tag>` refuses the write unless the post is still at that tag |

See [Content as Files](/docs/content-as-files/) for the tree, the sidecars and the merge.

### Prove it works

| Command | Does |
|---|---|
| `shio verify` | The content and route lint. **Exits non-zero on an error**: the CI gate. `--strict` fails on warnings too; `--folder` / `--address` / `--since` scope it; `--baseline` / `--accept` subtract findings you have accepted; `--delivery` adds the proof that a published page is really served, **appended** to `--checks` rather than replacing it. The report's `notes` are printed, so a run that covered less than you think says so |
| `shio digest <page>` | One page's structure: outline, landmarks, links, images |
| `shio audit <page>` | What is wrong with a rendered page, as text with a selector each (see below) |
| `shio snapshot <page>` | A PNG for a person, plus whether the page looks different. **A changed page exits 0.** Needs the optional `playwright`; with `pixelmatch` and `pngjs` the diff is per-pixel, otherwise byte-level, and the output says which. A site with no front end is photographed through the preview, and the output says the picture is a draft |
| `shio report` | The curator handoff: what changed, a preview link each, and the warnings as open questions, in Markdown for a pull request body |
| `shio diff --against published` | Which **fields** differ between each draft and the live row, per post. `--address` narrows it. **Exits non-zero when anything would change.** An object whose keys only moved is not a change, as in the console's version dialog |
| `shio gate run --site <name>` | Run the site's publish gates over the addresses named, or every draft. **Exits `1` when a blocking check fails.** `shio gate list` prints the gates the site declares; the same gates run on the instance at every publish |
| `shio index status` | The search routing table, site by site. A site with published pages and no search site reads `UNROUTABLE`, and the command **exits `1`**. `shio index reindex --site <name>` sends that site's published pages again (`--dry-run` counts them). See [Search & Caching](/docs/search-caching/) |
| `shio assets prune` | The assets nothing references and no rendered page loads, with the bytes they occupy. A report only: it never deletes. `--site` / `--folder` scope it |
| `shio redirects` | The site's redirects (where each sends, 301 or 302) and the addresses it served that no longer answer, each with the `post.redirect` that fixes it. **Exits `1` when there is one.** `--no-check` skips the second half. See [Keeping an old address answering](/docs/content-lifecycle/#keeping-an-old-address-answering) |

#### What `shio audit` reports

An **error** exits non-zero; a **warning** does not, so a pipeline can gate on the first without
being stopped by the second.

| Rule | Severity | Fires when | What to do |
|---|---|---|---|
| `request-failed` | error | The page asked for something (an image, a stylesheet, a script) and the server did not give it | Fix the URL or publish the asset. A missing stylesheet explains much of the rest of the report |
| `console-error` | error | The page threw in the browser | Read the message: it is the finding |
| `region-script` | error | A region's `JAVASCRIPT` does not parse | Fix the script. It runs only in the browser, so a region whose script is broken still renders and verifies clean; this is the check that parses it |
| `overflow` | warn | A box is wider than the viewport at one of the tested widths | Usually a fixed width, a long unbroken string, or a table. Reported at the width where it is worst |
| `contrast` | warn | Text fails the WCAG AA ratio against its own background | Darken the text or lighten the background. Large and bold text has a lower floor, and `--contrast` overrides it |
| `zero-size` | warn | An element occupies no space | Either it should not be in the markup, or something it depends on did not load |
| `collapsed` | warn | A box has children and no height | Usually a float or grid container that lost its layout |
| `font-fallback` | warn | A declared font was not available, so the browser substituted one | Ship the font or accept the substitute |
| `no-landmark` | warn | The page has no `main`, `nav`, `header` or `footer` | Usually a page that lost its regions |
| `no-heading` | warn | The page has no heading of any level | Same reading as `no-landmark`; the two usually arrive together |

Two lines are printed on every run, findings or not:

| Line | Says |
|---|---|
| `shape:` | The landmarks and the heading count. An intact page reads `main, navigation, contentinfo · 3 headings`; the same page emptied reads `no landmark · 2 headings` |
| `scripts:` | How many region scripts were parsed; `0 region scripts` tells a page with no script from one whose scripts are all fine |

Each rule stops after a fixed number of findings per page load, and a `note:` line names the rule
and how many it did not examine.

### Working with a curator

| Command | Does |
|---|---|
| `shio review list` | One line per agent run: its intent, its counts, and what is waiting for a person. `show <session>` lists that run's changes with whatever has been decided |
| `shio review decide <session>` | Approve, reject or hold named changes: `--approve` publishes them, `--reject` undoes them and needs `--note "<why>"`, `--hold` parks them. `--dry-run` prints what it would do |
| `shio review mine` | What a curator decided about **your own** runs; rejections by default, each with its reason. Read it when resuming work |
| `shio notes` | What a curator is still asking for, across every site: `--kind request`, `--site` to scope it |

`--strict` on `review list`, `review mine` and `notes` exits `1` while anything is waiting or was
rejected, so a pipeline can refuse to proceed past an unanswered curator.

### Replicate an existing site

| Command | Does |
|---|---|
| `shio clone <url>` | Capture a site that already exists: an inventory, and no content |
| `shio propose` | Which captured blocks could become sections, and what each would lose |
| `shio convert` | Apply the accepted plan: sections, theme and scripts, as **drafts** |

The whole round, from a source URL to a judged replica, is in [Replication](/docs/replication/).

---

## Related pages

| Page | Description |
|---|---|
| [Content as Files](/docs/content-as-files/) | The tree `pull --content` writes and the merge that protects it |
| [Blueprints](/docs/blueprints/) | The packages `init --blueprint` and `apply --blueprint` install |
| [Replication](/docs/replication/) | `clone`, `propose` and `convert` end to end |
| [The Agent Surface](/docs/agent-surface/) | The endpoints every command calls |
| [MCP Server](/docs/mcp/) | `shio mcp`, and connecting a client |
