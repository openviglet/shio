---
title: Blueprints
description: "Appliable starting points: a blueprint installs a content model, a folder tree, sample pages and a front end in one call, with a plan you can read first."
sidebar:
  label: Blueprints
---

A recipe tells you what to do. A **blueprint** does it.

A blueprint is a section of a site the instance can **apply**: a content model, a folder
tree, sample pages, the parameters that make it yours, and a promise about what exists
afterwards. One call produces a working, verifiable starting point, which is the
difference between "here are eleven steps" and "here is a blog".

```bash
shio blueprints                     # what this instance carries
shio blueprints blog                # one package's parameters
shio apply --blueprint blog --site mysite --param title="My Blog"
```

Over MCP that is `shio_blueprint`; over REST:

```http
GET  /api/v2/agent/blueprints
GET  /api/v2/agent/blueprints/{name}
POST /api/v2/agent/blueprints/{name}/apply
```

Reading the catalogue is deliberately cheap: a name, a version, what applying it gives
you, and the parameter names. The full schema of the one you pick is a second call, so
choosing between six packages does not cost six schemas.

---

## The first-party set

| Blueprint | Gives you |
|---|---|
| `blog` | An `Article` type, a `/blog` section, published samples |
| `docs` | A documentation section with a navigable tree |
| `product-catalog` | A catalogue type and a listing |
| `marketing-site` | A section-composed site, **plus** the Next.js front end |
| `landing` | A single section-composed page, **plus** the front end |
| `nextjs-starter` | The Next.js starter project itself, as a package |

Three of them carry front-end **files**: `nextjs-starter` gets the starter tree, and
`marketing-site` and `landing` get the same tree plus a small section-aware overlay. The
other three deliberately do not: they compose no sections, so the overlay would ship a
dependency they never import.

---

## Where more packages come from

Those six ship with the instance. A **marketplace** catalogue adds more, and it is off
until an operator turns it on:

```properties
shio.marketplace.enabled=true
shio.marketplace.url=https://shio.viglet.org/marketplace/marketplace.json
shio.marketplace.timeout-millis=5000
```

With it off, listing the marketplace still answers, with the six built-ins and a
`source` of `builtin-only`. That is deliberate: "the feature is off" and "this build has
no packages" are different facts, and the listing says which. When a catalogue is
configured but cannot be reached, the source reads `unreachable` and carries the reason,
so a list that quietly shrank cannot be mistaken for a catalogue that shrank.

### The showcase site

The published catalogue also carries **`shio-blueprint-quilhara`**, a finished sample site
that ships with no instance. Quilhara is a fictional trail magazine and guided-trip
outfitter, written in English and Brazilian Portuguese: a home page, trails, stories, a
guide and a trip, each with its own layout, a designed theme, self-hosted fonts and its own
photography. Install it and apply it to a new site to see what Shio renders before you
build your own:

```bash
shio marketplace install shio-blueprint-quilhara
shio apply --blueprint shio-blueprint-quilhara --site quilhara --create-site --merge-post-types
```

Every page exists in both languages, linked as translations, so the language switcher and
`/sites/quilhara/default/pt-br/` work from the first apply. The brand, the places and the
people are invented, and its About page says so.

### From a terminal

```bash
shio marketplace list                        # what is available, and where the list came from
shio marketplace show shio-blueprint-blog    # one entry, with its README
shio marketplace install shio-blueprint-blog
shio apply --blueprint shio-blueprint-blog --site mysite
```

**Installing is not applying.** `install` downloads the package and puts it where the
registry can see it, and stops there: applying it to a site stays `shio apply
--blueprint`. The separation is the point: you can read what you installed before
anything writes to your content. `install --dry-run` goes further and does everything
except the move into place, so an archive that is malformed, or that disagrees with the
catalogue about its own name, is caught before it lands.

To go from a catalogue entry to a site in one command, add `--apply`:

```bash
shio marketplace install shio-blueprint-blog --apply --site mysite --create-site --dry-run
shio marketplace install shio-blueprint-blog --apply --site mysite --create-site
```

It installs the package if it is not there yet (a built-in or already installed package
skips that half), then runs the same apply `shio apply --blueprint` runs, with the same
flags. They stay two operations: if the apply refuses (an unknown site, a missing
parameter), the package stays installed and the message says how to retry the apply.

### From the console and from an agent

The console has the same catalogue under **Admin → Marketplace**. A package's card shows
its first screenshot when the catalogue declares any, and the detail shows all of them
with the README rendered. Screenshots, like the README, are fetched by your instance and
only from URLs your configured catalogue declares, so a catalogue cannot make your browser
contact a host you did not choose.

The detail also turns the package into a site: fill in its parameters, name the site,
and choose **Plan**. The plan lists both halves, the install when the package is not here
yet and everything the apply would create, and writes nothing. The confirm button only
works on a plan for exactly the inputs on screen; change anything and you plan again.

An agent reads the catalogue with
the `shio_marketplace` MCP tool, which lists and describes: it does **not** install.
That is a deliberate boundary, not an omission: installing a stranger's package is the
operator's act, so it stays with a credential the operator holds.

### Who may install

Browsing is open to anyone signed in. **Installing and uninstalling require an
administrator**, over every surface: the console, the CLI and the REST API alike.
The reasoning is that merely re-reading the package directory already needs an
administrator, because it changes what every later apply can do; installing is that act
plus a download, so it cannot be gated more weakly than what it contains.

Uninstall only removes a package that came from a catalogue. The six built-ins are part
of the instance, so "uninstalled" would be a claim the next restart contradicts.

---

## Applying one

`apply` is a two-step by default, because looking before writing is the whole safety
story:

```bash
shio apply --blueprint marketing-site --site mysite --dry-run
```

A plan **writes nothing and needs no write transaction**: it can run on a read-only
credential or a replica. It declares what it *would* create: the site, each post type
with the fields it would carry, every folder and every page. Then:

```bash
shio apply --blueprint marketing-site --site mysite --create-site \
  --param title="Northwind" --param tagline="Tools that last"
```

Everything lands as **drafts**. Publishing is yours.

The two section packages (`marketing-site` and `landing`) arrive **editable on the page**:
open one in the Universal Editor and every section's fields are there, plus the page's own
title and description. A curator changes the words without opening the console form, and
without anybody having built a template first.

| Flag | Effect |
|---|---|
| `--param k=v` | Repeatable |
| `--params <file>` | The same values as JSON |
| `--site <site>` | Which site to apply into, always the caller's choice |
| `--create-site` | Make the site if it is not there |
| `--merge-post-types` | Add fields an existing type is missing |
| `--force` | Overwrite carried files that already exist on disk |

---

## The rules that protect you

A blueprint is code you did not write, applied to content you care about. Seven rules
follow from that, and each is a refusal rather than a convention:

- **A package may not name its own site, and may not prune.** The site is always the
  caller's; a `site` key inside a package is *refused at load*, not ignored: an ignored
  key looks like it worked. Prune's blast radius depends on what the author forgot to
  mention, and that author is not you.
- **The model is provisioned, never overwritten.** A post type that already exists is left
  exactly as it is (including a field a curator added) and the response **names the fields
  it lacks** so you can decide. `--merge-post-types` adds them; nothing silently replaces
  your model. If the package's content needs one of those missing fields and you did not
  pass the flag, the apply stops with `blueprint-needs-merge`, which lists each type and
  the fields it lacks. This is common on a fresh instance, whose seeded `Page` has only a
  title and content. Apply again with `--merge-post-types` (or `"mergePostTypes": true`
  over REST).
- **Substitution is `{{name}}` and nothing else.** No conditionals, no expressions: the
  same parameters always produce the same site. `site` is reserved and always available.
  An unknown parameter is a teaching error, never quietly dropped, **except inside a
  template field** (`HTML`, `CSS`, `JAVASCRIPT`), where an unknown name belongs to the
  page renderer and passes through untouched. A package that ships a layout writes
  `{{post.title}}` there and means the renderer's, not yours; the trade is that a
  mistyped parameter *inside a template* is not caught, because in that field an
  undeclared name is the normal case.
- **A malformed package is the package's fault, and says so.** Everything is validated when
  the package loads: a missing referenced file, a path that escapes the package, an
  unknown manifest key, a placeholder with no declared parameter, a promise the content
  never fulfils. That is a `409`, not a `400`: nothing you sent caused it.
- **One package's failure is not the catalogue's.** A package you added to your own
  blueprint directory that will not load is refused **on its own** and reported in
  `GET /api/v2/agent/diagnostics`, with the reason. The packages that shipped with the
  instance keep working, so a package somebody else wrote cannot take your catalogue
  down. It is also how you validate one before publishing it: point an instance at the
  directory, reload, and read the refusal.
- **A package cannot come from a URL.** The only sources are the instance's own classpath
  and one directory an operator names. Whoever set that path is vouching for what is in
  it, and there is no fetch-by-URL: a blueprint writes pages under *your* credentials,
  so moving that decision from a person to a network is the shape of every supply-chain
  incident. The `shio-blueprint-` name prefix is reserved for packages that did not ship
  with the instance, so the name tells you where something came from.
- **The server never writes a project file.** Carried files come back on the response and
  the CLI writes them, never over an existing file without `--force`. A client that cannot
  write files is told what it has *not* got, and each row names the placeholders standing
  in that file, read from the **bytes**, with the resolved value beside it.

---

## What a package looks like

A directory with a `blueprint.json` and the files it references:

```
blog/
├── blueprint.json
├── post-types/Article.json     # the same bytes `shio pull` writes
├── content/blog.json           # a desired-state document
└── files/…                     # optional front-end sources
```

Nothing in that format is a new serialization: the post types are what the CLI already
writes, the content is a desired-state document, and `verify` is the promise the package
keeps. A blueprint is therefore something you can read, diff and author with the tools you
already have.

`blueprint.json` declares the name, the version, the parameters with their defaults, the
files, and a `verify` block naming what must exist when the apply is done, which is what
makes "it worked" checkable instead of asserted.

### Images a package brings

A package can carry its own images. List them under `assets`, from an `assets/` directory
in the package, each inside a folder:

```json
"assets": ["assets/images/hero.webp", "assets/images/team.jpg"]
```

On apply, `assets/images/hero.webp` becomes the file `hero.webp` in the site folder
`/images`. To point a field at it, give a `File` field the value `asset:/images/hero.webp`:
the images are uploaded before the content is written, and each such value is replaced by
that image's id, so the field works with `{{#image}}` and `{{file_url}}` like any image a
curator picked. To describe an image, declare it as an object; the text becomes the image's
alt text in the Media Library:

```json
"assets": [{ "path": "assets/images/hero.webp", "alt": "Two hikers on a cliff path at dawn" }]
```

The plan lists every image
and what would happen to it: `create`, `replace` when your copy's bytes differ (the file
keeps its identity, so everything pointing at it still works, and the old bytes are kept
as a previous version), or `noop` when they are identical, which is every image of a
re-apply. A package's images may total at most 32 MB.

Unlike carried files, which are templates for your project, images belong in the CMS, so
the server writes them.

---

## Related Pages

| Page | Description |
|---|---|
| [The `shio` CLI](/docs/cli/) | `blueprints`, `apply --blueprint`, `init --blueprint` |
| [The Agent Surface](/docs/agent-surface/) | The desired-state document a package compiles into |
| [MCP Server](/docs/mcp/) | `shio_blueprint` and the `shio://blueprints` resources |
| [Content Modeling](/docs/content-modeling/) | The post types a package provisions |
