---
title: Content as Files
description: "Project a Shio site to disk as Markdown and edit it with ordinary tools: the tree layout, sidecars, assets, and the three-way merge that protects a curator's edit."
sidebar:
  label: Content as Files
---

An agent's cheapest tools are the ones it already has: **edit a file, write a file, grep
a tree.** Every one of those costs a fraction of an API round trip, and every editor,
diff and version-control tool in existence already works on them.

So Shio projects a site to disk. `shio pull --content` writes the site as Markdown,
you change it however you like, and `shio push --content` sends it back. A rename is a
rename, a search is a grep, and a review is a diff.

The thing that makes this safe rather than reckless is the **three-way merge**: a pull
never clobbers your local edit, and a push never silently reverts what a curator changed
in the console while you were working.

---

![Pull, edit, push, with the three-way merge against a fingerprint base](/img/diagrams/shio-content-files.svg)

## The tree

```
shio/
├── content/
│   └── mysite/
│       ├── index.md                        # the home page (url: "")
│       ├── about.md
│       └── blog/
│           ├── hello-world.md
│           ├── layouts/
│           │   ├── article.md              # a PageLayout is content too
│           │   ├── article.HTML.html       #   …with its template as a sidecar
│           │   └── article.JAVASCRIPT.js
├── folders/
│   └── mysite/
│       └── press/
│           └── _folder.md                  # a folder no post lives in yet
├── assets/
│   └── mysite/
│       └── img/
│           └── hero.png                    # real bytes
├── post-types/
│   └── Article.json                        # the content model
└── .shio/
    └── content-base.json                   # the merge base, do not hand-edit
```

Four roots, each with one job, and they are separate on purpose:

- **`content/`**: one Markdown file per post. **The path is the post's URL**, not its
  folder chain. A URL is unique within a site, so the tree cannot collide.
- **`folders/`**: a marker for a folder that no post names yet. It is a *separate root*
  because under `content/` the directories are URL segments, and a marker there would be
  claiming that a URL prefix is a folder chain, which looks right until the one time the
  two disagree and it silently creates the wrong folder.
- **`assets/`**: the actual bytes, at `assets/<site>/<folder-chain>/<name>`.
- **`post-types/`**: the model, as canonical JSON.

## A post

```markdown
---
site: mysite
url: blog/hello-world
type: Article
folder: /blog
publish: true
body: TEXT
---

First post. The body is the post's first HTML Editor field, and the frontmatter
records **which** field that was: so reading the file back never needs the model.
```

The frontmatter carries exactly ten keys, `site`, `url`, `type`, `folder`, `data`,
`body`, `publish`, `locale`, `movedFrom`, `files`, and everything a post holds that is
*not* projected is declared with a reason in a test, so a field the format cannot carry
can never be dropped quietly.

Four rules worth knowing before you edit one:

| Rule | Why |
|---|---|
| A file whose path and `url:` **disagree** is an error | That is a half-finished rename: a move on one side and a create on the other |
| **Only the `DRAFT` projects.** `publish: true` is an *instruction* on the way in, never state on the way out | A pull would otherwise keep flipping a field you did not set |
| The body is normalized **CRLF → LF**, and nothing else is | Losslessness beats prettiness: a value that would not survive the round trip stays in `data:` as JSON instead of becoming the body |
| `locale:` appears only when the page really declares one | A monolingual tree stays byte-identical, and two locales of one page are two URLs with `translationOf:` naming the anchor |

## Sidecars: code fields as real files

A layout's HTML used to project as an escaped one-liner inside YAML: no line numbers, no
highlighting, nothing `grep` could reach, and a re-escape for every tweak. Code fields now
land **beside** the Markdown:

```
article.md
article.HTML.html
article.JAVASCRIPT.js
```

Five rules, each with a failure behind it:

- **The widget decides which fields get a sidecar, never the post type.** A rule keyed on
  the type stops working the moment somebody models their own layout, which is the
  expected case, since a layout is content.
- **The file name always carries the field name.** `article.html` collides silently the
  day a type declares two HTML fields.
- **The bytes are verbatim.** This is the one place the format normalizes nothing:
  normalizing would make the value differ from the fingerprint taken from the server's
  copy, and every later pull would report a local change nobody made.
- **A file named by `files:` that is missing is a refusal**, not an empty value, 
  "empty" publishes a blank layout, and "absent" would make the push clear the server's
  copy.
- **A sidecar the post no longer needs is removed**, read off the disk rather than
  inferred.

---

## The merge

`shio pull --content` is a **three-way merge** against `shio/.shio/content-base.json`,
the last-synced base. Per field:

| Base vs local vs remote | Result |
|---|---|
| Only the server changed | the server's value |
| Only your file changed | your value, kept |
| Both changed, **different fields** | merged, you get both |
| Both changed, the **same field** | **conflict** |

A conflict **writes nothing and exits non-zero**. You resolve it with `--ours`,
`--theirs`, or `--show-remote` to look at the server's version first.

:::note[There are no conflict markers, on purpose]
`<<<<<<<` inside a field value is *content*, and the next push would publish it. A merge
that cannot be resolved leaves your tree exactly as it was and tells you which fields
disagree.
:::

**The base stores fingerprints, not values.** It is only ever compared, so keeping a
second copy of the whole site on disk (and in every clone, once you commit it) bought
nothing. A fingerprint is a hash *and* the canonical byte length, compared part by part, 
which turns a hash collision into a **reported conflict** rather than a dropped edit:
equal hashes with unequal lengths cannot be the same value. When that happens the remedy
is different from an ordinary conflict: the base cannot describe the value it indexes, so
the base is what has to be rebuilt.

### What is projected but never merged

Not everything `shio pull` writes takes part in that merge, and the difference matters:
an artefact outside the base has no conflict to report, so whoever is authoritative wins
silently. Each one names that side.

| File | In the base | Who wins |
|---|---|---|
| A post's `.md`, and its sidecars | per field | neither: a field both sides changed is a conflict |
| `shio/assets/**` bytes | whole | neither: half an image is not a merge |
| A folder marker | no | the server: that a folder exists is the server's fact |
| `.memory.yaml` (site notes) | per key | neither: neither copy is written |
| `.memory.yaml` (instance notes) | no | the server; push back only with `--adopt-memory` |
| `.notes.yaml` (open notes) | no | the server, always: never parsed, never pushed |

The last row is the sharpest case, and the reason the rule is written rather than assumed.
An open note is the instance's record of what somebody asked for, and it is the one
projected file a curator edits in the console *while* your tree holds a copy, so the tree
reads it and never writes it. A site with nothing open **loses** the file rather than
keeping an empty one, because a stale file listing a closed request reads exactly like a
clean site.

### Pushing back

`shio push --content` compiles the tree into **one desired-state document per site** and
applies it through the agent surface's `plan`/`apply`. It is not a second write path, 
the same guards, the same atomicity, the same confirm tokens.

One rule that is the whole point of the merge: a post the **server** changed is declared
with the **server's** values. Declaring the base would revert a curator's console edit;
omitting the post would expose it to `--prune`.

`shio push --content --check` writes nothing and exits non-zero if the tree and the
server disagree: the drift guard to put in CI.

### Renames

A rename is a **move**, and the tree has to say so. Moving `blog/old-url.md` to
`blog/new-url.md` looks exactly like one file deleted and another created, and guessing
between the two is how a post loses its id, its history and its inbound links.

```markdown
---
site: mysite
url: blog/new-url
movedFrom: blog/old-url
---
```

The same key works on a folder marker, where the whole name chain *is* the address.
Without it, a delete-plus-create is what the push will honestly report, and a
`movedFrom` naming a file that is **still there** is always an error, because that is two
posts claiming one history.

---

## Determinism

Two guarantees you can build a workflow on:

- **A no-op pull is a no-op diff.** Serialization is byte-identical run to run: fixed key
  order, stable field ordering, the same trailing newline. Pull twice and `git status` is
  clean.
- **The round trip is asserted as a property**, not sampled: an adversarial value list
  (trailing whitespace, CRLF, lone `\r`, YAML keywords, numeric strings, BOM, emoji, `..`,
  a bare `---`) is round-tripped through every carrier the format has, post field, body,
  sidecar, asset path, note, and a post's own `type`/`site`.

Two refusals fall out of the same discipline, and both are strict on purpose:

- A **truncated** listing makes the projection partial, so the pull refuses rather than
  writing a tree that a later `--prune` would read as deletions. (`--allow-partial` marks
  the snapshot, and `push --prune` then refuses too.)
- `push --content` over an **empty directory** is an error, not an empty document.

A path that navigates is refused everywhere it could appear: an asset path containing
`.` or `..` is rejected, and a site name that has no faithful directory name is refused
rather than trimmed, because trimming produces a tree whose `site:` disagrees with its
own path, which every later command would reject.

---

## Related Pages

| Page | Description |
|---|---|
| [The `shio` CLI](/docs/cli/) | `pull`, `push`, `verify` and the rest of the verbs |
| [The Agent Surface](/docs/agent-surface/) | The `plan`/`apply` path a push compiles into |
| [Content Modeling](/docs/content-modeling/) | The post types `post-types/*.json` holds |
| [Letting an agent in](/docs/agent-safety/) | Why a push cannot surprise a curator |
