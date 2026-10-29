---
title: The shio CLI
description: "@viglet/shio, the shio command: scaffold a site, sync content and post types both ways, verify, replicate an existing site, and gate a pipeline on drift."
sidebar:
  label: The shio CLI
---

```bash
npx @viglet/shio --help
```

The `shio` command is the scripted path into an instance: it is what a CI job runs, what
`claude mcp add` bridges to, and what you use when a shell is a better fit than an API
call. Zero runtime dependencies, and every command prints its own flags:

```bash
shio verify --help          # or: shio help verify
```

## Connecting

| Flag | Environment | Used by |
|---|---|---|
| `--url <baseUrl>` | `SHIO_URL` (default `http://localhost:2710`) | everything |
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
| `shio marketplace <list\|show\|install\|uninstall>` | The package catalogue: what this instance could install, and installing it. `install` needs an administrator. See [Blueprints § Where more packages come from](/shio/docs/blueprints/#where-more-packages-come-from) |
| `shio import <file.zip>` | Clone an exchange package into this instance: how you accept a site somebody hands you. It prints what arrived and a line for each thing it could not bring |
| `shio export site:<name>` | Download a site as an exchange package (`--out <file.zip>`); the same file the console's **Download** saves |

**Handing a site over.** An export carries the site, its folders, every post in both states,
the post types those posts use, the site's notes, and the bytes of its uploaded files, so its
images and downloads work on the other instance. It does not carry users, tokens, permissions,
revisions, releases or schedules. That makes it a hand-over, not a backup and not a way to
promote between environments. On import, the report says how many sites, folders, posts, post
types and files arrived, and names each problem: a post skipped because its folder was not in
the package, a post type neither the package nor this instance defines, and a file the package
lists without its bytes.

### Discover

| Command | Does |
|---|---|
| `shio context` | The agent context pack (model, sitemap and conventions) in one call. `--out <file>` writes it |
| `shio resolve <address>` | Resolve `post:` / `folder:` / `site:` / `id:` to the object it names |
| `shio impact <address>` | What a change to this template would affect: layouts, types, page counts |
| `shio changes` | What changed, with a cursor to resume from (`--since`, `--cursor`) |
| `shio remember` | The instance memory: bare lists it, `<key> "<note>"` writes one, `--forget` drops one, `--site` scopes it |
| `shio mcp` | Bridge stdio to this instance's MCP endpoint, `claude mcp add shio -- shio mcp` |

### The content model

| Command | Does |
|---|---|
| `shio pull` | Post types into `shio/post-types/*.json` |
| `shio push` | Local post types back to the instance (`--dry-run`, `--check`, `--prune`) |
| `shio build` | Compile local DSL/JSON post types to canonical JSON without touching the server (`--out`) |
| `shio types` | Generate a TypeScript `.d.ts` from the live post types |
| `shio deploy` | `push` then `types` |

`push` and `build` compile TypeScript DSL sources to canonical JSON before diffing, so a
model authored as typed TS and one hand-written as JSON push identically. `--prune` never
removes a system type.

### Content

| Command | Does |
|---|---|
| `shio pull --content` | Project the site to `shio/content/**` (three-way merge) |
| `shio push --content` | Compile the tree into a desired-state document and apply it |
| `shio apply [file]` | Plan/apply a desired-state document, `--dry-run`, `--check`, `-` for stdin. `--blueprint <name> --site <site>` applies a package instead; `--create-site` makes the site in either mode |
| `shio dev --content` | Watch `shio/content/**` and sync both ways, logging events |
| `shio post set <address> <field>=<value>…` | Change some fields of one post's draft and leave the rest as they are. `field:=json` sets a number, `true`, a list or an object; `--publish` publishes after the change |

See [Content as Files](/shio/docs/content-as-files/) for the tree, the sidecars and the merge.

**Changing one field.** `shio post set` needs a token of write scope (`--token`, or
`SHIO_WRITE_TOKEN`) as well as the console user that resolves the address; an agent token
cannot write this way. It is safe to run while someone else is editing: if the post changed
between the moment the command read it and the moment it writes, the change is refused and
nothing is written. Each write prints the post's new tag, and `--if-match <tag>` refuses the
write unless the post is still at that tag. So a script that reads, decides and writes can be
sure it is writing over what it read:

```bash
shio post set post:Blog/hello title="Hello again"
# post:Blog/hello set title (draft) etag="3a22…"
shio post set post:Blog/hello summary="Shorter" --if-match '"3a22…"'
```

### Prove it works

| Command | Does |
|---|---|
| `shio verify` | The content and route lint. **Exits non-zero on an error**: this is the CI gate. `--strict` fails on warnings too; `--folder` / `--address` / `--since` scope it; `--baseline` / `--accept` subtract findings you have accepted; `--delivery` adds the stronger proof that a published page is really served |
| `shio digest <page>` | One page's structure: outline, landmarks, links, images |
| `shio audit <page>` | What is wrong with a rendered page, as text with a selector each |
| `shio snapshot <page>` | A PNG for a person, plus whether the page looks different |
| `shio report` | The curator handoff: what changed, a preview link each, and the warnings as open questions, Markdown, for a PR body |
| `shio diff --against published` | What a publish would change: which **fields** differ between each draft and the live row, per post. `--address` narrows it. **Exits non-zero when anything would change**, so a pipeline can gate on it. It agrees with the console's version dialog on what counts as a change: an object whose keys only moved is not one |
| `shio redirects` | The site's redirects (where each sends, 301 or 302) and, under them, the addresses it used to serve that no longer answer, each with the `post.redirect` that fixes it. **Exits `1` when there is one.** `--no-check` skips the second half. See [Keeping an old address answering](/shio/docs/content-lifecycle/#keeping-an-old-address-answering) |

Two things about `verify` that matter in a pipeline: the report's `notes` are printed,
because a run that quietly covered less than you think reads exactly like a clean pass;
and `--delivery` **appends** its group rather than replacing `--checks`, since a caller
who wants the stronger proof still wants the lint.

`snapshot` needs `playwright`, which is an **optional** dependency with a teaching error
naming the install, `@viglet/shio` never drags a browser into your project. With
`pixelmatch` and `pngjs` present the diff is per-pixel; without them it is byte-level, and
the output says which. **A changed page exits 0**: a page looking different is usually the
change you just made, and a gate there is one people wrap in `|| true`. A site with no
front end is photographed through the instance's own preview, and the output says the
picture is a draft, because the image cannot.

#### What `shio audit` reports

Ten rules, and two lines the report prints whether or not anything is wrong. An **error**
exits non-zero; a **warning** does not, so a pipeline can gate on the first without being
stopped by the second.

| Rule | Severity | Fires when | What to do |
|---|---|---|---|
| `request-failed` | error | The page asked for something (an image, a stylesheet, a script) and the server did not give it | Fix the URL or publish the asset. A missing stylesheet is why the rest of the report looks strange |
| `console-error` | error | The page threw in the browser | Read the message: it *is* the finding, so this one carries no extra detail |
| `region-script` | error | A region's `JAVASCRIPT` does not parse | The only field whose value is a program. It is shipped to the browser and never run in the JVM, so a region whose script is broken still renders, hashes, lints and verifies clean; this is the one check that runs it |
| `overflow` | warn | A box is wider than the viewport at one of the tested widths | Usually a fixed width, a long unbroken string, or a table. Reported at the width where it is worst |
| `contrast` | warn | Text fails the WCAG AA ratio against its own background | Darken the text or lighten the background. Large and bold text has a lower floor, and `--contrast` overrides it |
| `zero-size` | warn | An element occupies no space | Either it should not be in the markup, or something it depends on did not load |
| `collapsed` | warn | A box has children and no height | Usually a float or grid container that lost its layout |
| `font-fallback` | warn | A declared font was not available, so the browser substituted one | Ship the font or accept the substitute. What you see locally is not what a reader sees |
| `no-landmark` | warn | The page has no `main`, `nav`, `header` or `footer` at all | Almost always a page that lost its regions rather than a page that never had them |
| `no-heading` | warn | The page has no heading of any level | Same reading as `no-landmark`, and the two usually arrive together |

The last two exist because the other eight measure what **is** there: a page that has lost
most of itself has no overflow, perfect contrast, no collapsed box and nothing that fails
to load. Emptiness scored clean.

Two lines are printed on every run, findings or not, for that same reason. A number
nobody prints is a fact nobody joins:

- **`shape:`**: the landmarks and the heading count, read once because they do not change
  with the viewport. An intact page reads `main, navigation, contentinfo · 3 headings`;
  the same page emptied reads `no landmark · 2 headings`. That difference is the whole
  answer to *did this page keep its regions*.
- **`scripts:`**: how many region scripts were parsed. `0 region scripts` is an answer:
  without it, a page carrying no script at all and a page where every script is fine would
  print the same nothing.

Each rule stops after a fixed number of findings per page load, and the report says so in
a `note:` line naming the rule and how many it did not examine: a truncated run must not
read as a clean one.

### Replicate an existing site

| Command | Does |
|---|---|
| `shio clone <url>` | Capture a site that already exists: an inventory, and no content |
| `shio propose` | Which captured blocks could become sections, and what each would lose |
| `shio convert` | Apply the accepted plan: sections, theme and scripts, as **drafts** |

See [Replication](/shio/docs/replication/).

---

## In a pipeline

Three commands are gates, and the order they run in is the order the failures make sense
in:

```bash
shio push --check          # 1. does the model on the server match git?
shio push --content --check # 2. does the content match git?
shio verify                # 3. is what is there internally consistent and reachable?
```

`--check` prints the plan, writes nothing, and **exits non-zero when the plan is not
empty**, so a pull-request job fails on drift instead of silently reconciling it.
`verify` exits non-zero on an error, which is what makes it the last gate rather than a
report.

Every non-zero exit closes cleanly, so a piped run does not lose its last lines.

---

## Related Pages

| Page | Description |
|---|---|
| [Content as Files](/shio/docs/content-as-files/) | The tree `pull --content` writes and the merge that protects it |
| [Blueprints](/shio/docs/blueprints/) | The packages `init --blueprint` and `apply --blueprint` install |
| [Replication](/shio/docs/replication/) | `clone`, `propose` and `convert` end to end |
| [The Agent Surface](/shio/docs/agent-surface/) | The endpoints every command calls |
| [MCP Server](/shio/docs/mcp/) | `shio mcp`, and connecting a client |
