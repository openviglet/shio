---
title: The Agent Surface
description: "/api/v2/agent/**: one call to discover the instance, addressed reads, an atomic op vocabulary, desired-state apply, and a verification loop that needs nobody to look."
sidebar:
  label: The Agent Surface (REST)
---

`/api/v2/agent/**` is Shio's REST surface for a program that builds and maintains a
site. If you are driving Shio from Claude Code or another MCP client, you want the MCP
server instead: it is the same capabilities in a different shape. This page is for
building your own agent, or for understanding what the MCP tools actually call.

It is **a shape over the same content the CDA and the console read**, not a third
contract. Nothing here can do something the console cannot; what it adds is
discoverability, determinism, and a response an agent can act on without a second call.

---

![The agent loop: discover, plan, write, verify, then hand over](/img/diagrams/shio-agent-loop.svg)

## Authenticating

An agent authenticates with an **API key of `AGENT` scope**, in a `Key` header:

```http
GET /api/v2/agent/manifest HTTP/1.1
Key: 7f3c9a12b4e05d68af1c2903b
```

Three things follow from the scope, and they are the reason it exists:

- **An `AGENT` token may only write on `/api/v2/agent/**` and `/mcp`.** On the CDA it
  stays read-only. An agent's credential must not also be a key to the endpoints that
  patch published content outside this surface's guards.
- **It reads drafts by definition.** Every agent read is draft-preferred, because the
  thing an agent most often needs to look at is what it just wrote.
- **Publishing is a separate permission** (`mayPublish` on the token). A token without
  it can write all day and change nothing a visitor sees.

A logged-in console session also reaches this surface, so you can explore it in a
browser, but the surface is designed to be **stateless**: no cookie, no CSRF token, no
login round trip.

---

## Addresses

Content is reached by **path**, not by id. This is the one piece of vocabulary
everything else on the page assumes:

| Form | Names | Example |
|---|---|---|
| `post:<site>/<friendly-url>` | one post | `post:mysite/blog/hello-world` |
| `folder:<site>/<name-chain>` | one folder and, where a verb says so, its descendants | `folder:mysite/blog` |
| `site:<name>` | a site | `site:mysite` |
| `id:<uuid>` | anything, by its raw id | `id:9f8c…` |

Three rules that are not guessable:

1. **Writes take only `post:` and `folder:`** (plus `site:` on the two site ops). An
   `id:` is a read convenience, addressing a write by id would defeat the point, which
   is that no UUID has to survive between two calls.
2. **A site's home page is `post:<site>/`**: the trailing slash with nothing after it.
   It is the one address the grammar does not spell out, and the most common thing to
   get wrong.
3. `GET /api/v2/agent/resolve?address=…` turns an address into the object it names, for
   the older id-keyed endpoints that still need one.

---

## One call instead of a session

### The manifest

```http
GET /api/v2/agent/manifest
```

The instance describing itself: version, capabilities, limits, and the endpoint index.
Read it once at the start of a session and you know what this deployment can do, 
including what it *cannot*, which is the half a caller otherwise discovers by getting a
404.

| Parameter | Effect |
|---|---|
| `include=` | Comma-separated sections, when you only need one (confirming a single boolean should not cost the whole endpoint index) |
| `format=terse` | Plain text, roughly a third to a half fewer tokens |
| `If-None-Match:` | An unchanged manifest answers `304` and costs nothing |

An unknown `format` is a teaching `400`, not a silent fallback.

`startHere` in the manifest does not list everything: it **routes**. Two facts that are
true whatever you came to do, then a line per intent, so you match your own situation
instead of mapping it onto a catalogue:

```
picking up where a previous session left off → GET /api/v2/agent/changes?site=<site>&format=terse
before deriving a convention, check this instance already worked it out → GET /api/v2/agent/context?format=terse
building a section: check something already applies it → GET /api/v2/agent/blueprints?format=terse
creating or changing content without holding a UUID → POST /api/v2/agent/batch with dryRun:true
proving what you just wrote is correct without asking anyone → GET /api/v2/agent/verify?site=<site>&format=terse
a call answered 5xx and you need to know what failed → GET /api/v2/agent/diagnostics?format=terse
```

### The context pack

```http
GET /api/v2/agent/context
```

The call that replaces a session of exploration: the content model, the sitemap **by
path**, and the conventions this instance has been told to remember. Every parameter is
optional: the single-site case is a bare `GET`.

| Parameter | Effect |
|---|---|
| `site=` | One site, by id or name; omit for every readable site |
| `depth=` | How deep to walk the folder tree |
| `budget=` | An estimated-token ceiling; `0` is unbounded |
| `include=` | `instance`, `sites`, `model`, `sitemap`, `conventions`, plus `manifest` and `ops`, which must be named explicitly |
| `from=` | Resume cursor: the `budget.truncatedAfter` of a previous call |
| `format=` | `json` (default), `terse`, or **`agents-md`**: the same pack rendered as project agent instructions, for a harness that loads a file instead of making a call |
| `ids=true` | Put the raw UUID back on every row. Off by default: rows are addressable without one, and an id is the most expensive field a row can carry |

A narrowed pack tells you what it left out. `include=instance` returns four scalars and
an `omitted` list naming the sections it dropped, because narrowing a *discovery*
response is done by a caller who does not yet know what is there, and a silent `200` is
worse than an error they would have read. It is separate from `budget.truncated`: one is
your own doing and the remedy is to ask for the section, the other is the instance
hitting its ceiling and the remedy is a bigger budget or the `from=` cursor.

Each post in the sitemap carries `published: true` when it is also live. The pack prefers
the draft row, so without it every page of a fully published site read as `DRAFT`, and a
session that opens with the pack would have been misinformed about all of them. Absent
rather than `false` when a page is not live, so a site with no published page pays
nothing for the field.

---

## Reading

| Call | Returns |
|---|---|
| `GET /agent/find` | Posts as **addresses and titles, never bodies**: filter by `site`, `type`, `folder` (matches beneath it too), `q` full-text, `limit`; walk past the cap with `after=<cursor>` |
| `GET /agent/read` | Whole documents by address. `address` is **repeatable**, so several posts cost one call; `fields=a,b` projects to named fields; `include=impact` attaches a template's blast radius; `state=published` reads what is **live** instead of the draft |
| `GET /agent/changes` | What changed, resumably. Without `since` you get a window and a starting cursor; with it, everything after that position, oldest first. The response's `cursor` is always the next `since`, which makes "what did the curator change while I was away" one call rather than a guess at a window |
| `GET /agent/memory` | What earlier sessions worked out. `PUT` remembers one note idempotently by `(scope, key)`; `DELETE` forgets one |
| `GET /agent/diagnostics` | Recent **asynchronous** failures (webhook deliveries, image transforms, scheduled publishes, 5xx) instead of grepping the logs. The window is bounded and the response says when it started collecting, because "nothing since then" is a different claim from "nothing ever" |

Reads are **draft-preferred**, so you always see your own unpublished edits. `state=`
overrides that: `state=published` returns the live row and `state=draft` the editable one.
Two reads of the same address (one with `state=published`, one without) are the answer to
"what will change when I publish this", and `shio diff --against published` is that pair as
one command.

A `state=` naming a row the post has not got is a **404 that says which**, never a quiet
fall back to the draft: a caller comparing a draft against what is live would otherwise be
handed the draft twice and read it as "nothing changed".

### Which version a read returns

`state` is one grammar on every read that returns a page: `draft`, `published`, or `rev:<n>`
for a revision by the number `shio versions` shows. `GET /agent/find` and `GET /agent/verify`
take `draft` or `published`, which lists or lints just those rows: `/agent/verify?state=draft`
is the report to read before a publish. `GET /agent/render`, `GET /agent/editable` and
`/preview/<site>/<url>` take all three, and a revision is rendered through the page's present
layout, under a banner that says which revision it is. `shio_find`, `shio_verify` and
`shio_digest` take the same `state`; `shio digest --state` and `shio verify --state` pass it
from a terminal. A value outside the grammar is refused with the three spellings, and a list
asked for a revision is refused with the read that serves one.

`GET /agent/find?scheduled=any` lists only posts with a publish or unpublish schedule, and
those rows carry the instants, so a schedule you set can be confirmed; a range such as
`scheduled=2026-09-15T00:00:00Z..2026-09-22T00:00:00Z` narrows it. `include=schedule` on
`GET /agent/read` attaches the schedule to one post. `include=fields` on `GET /agent/changes`,
or `fields` on `shio_changes`, names the fields each update and publish changed.

The delivery API reads the same `state`, within what its token may already see: `published`
works with any token, while `draft` and `rev:<n>` need a **preview** token and are refused
with `403` for a production one rather than answered with the published version.

### When you want to be told instead of asking

`GET /api/v2/events` is the same change feed, held open as [Server-Sent
Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events). Each event is one
page of the feed, and its event `id` is the cursor after it, so a connection that drops and
reconnects with `Last-Event-ID` resumes exactly where it stopped: nothing written in between is
lost. `since=` does the same for a client that cannot set the header, `site=` scopes it, and with
neither you are told about what happens from now on rather than about everything that already did.

The stream reads with **your** rights: an entry you may not read is withheld from your connection
and sent to one whose credentials allow it. Scheduled publishes report their outcome on the same
feed: a failure arrives with the command that retries it, and a form submission arrives marked as
one, with the title and summary its form collected.

On the command line this is `shio changes --follow`: it holds the connection open, prints each
change as it happens, reconnects on its own if the connection drops, and stops if the server
refuses it. Add `--json` for one line per event. `include=fields` on the stream, or
`--fields` on the command, names the fields each write changed, the same as on the feed.

An agent walking the feed with a cursor does not need any of this; a screen that is already open
does.

`PUT /agent/memory` takes the author from the credential, never from the body, and
re-using a key **rewrites** that note rather than adding a second one: the next
session pays for every near-duplicate it has to reconcile.

---

## Writing

### A batch of ops

```http
POST /api/v2/agent/batch
Content-Type: application/json

{
  "dryRun": true,
  "ops": [
    { "op": "folder.upsert", "address": "folder:mysite/blog" },
    { "op": "post.upsert",  "address": "post:mysite/blog/hello-world",
      "type": "Article", "folder": "folder:mysite/blog",
      "data": { "TITLE": "Hello world", "TEXT": "<p>First post.</p>" } }
  ]
}
```

N addressed ops, applied **atomically**: either all of them or none. `dryRun: true`
reports exactly what would change and writes nothing; it is also how you obtain the
`confirm` token a destructive op requires.

The vocabulary is closed. Fifteen ops:

| Op | What it does |
|---|---|
| `site.upsert` | Create a blank site, or fill an empty `url`/`description`/`furl` on one that exists. `data.postTypeLayout` binds a post type to a Page Layout title, per key. `data.reindex: true` back-fills the search index. **Never replaces a value somebody set.** |
| `site.delete` | Trash a site and every folder and post in it. Needs `confirm` |
| `site.restore` | Undo a site trash, with its content. Addressed `site:<name>`: a trashed site keeps its name |
| `folder.upsert` | Create a folder at that whole name chain, or confirm it is there |
| `folder.move` | Rename or relocate a section. `to` is the whole new name chain, so its last segment is the new name. **No post's URL changes** |
| `folder.copy` | Duplicate a section and everything under it, **as drafts**. `to` is the copy's own name chain; an occupied one is refused rather than numbered. Unlike a move, it may cross sites |
| `folder.delete` | Trash a folder and everything beneath it. Needs `confirm`; use `folder.move` to rename |
| `folder.restore` | Undo a folder trash, with its posts. Addressed `id:` |
| `post.upsert` | Declare a post's whole state at that address. Keys the post type does not declare are **refused**; use `post.move` to change the address. `expect` makes the write conditional on the digest you read |
| `post.move` | Change a post's URL, its folder, or both, keeping its id and history. A published post's live URL changes immediately |
| `post.publish` | Publish the draft, or schedule it with an ISO-8601 `when`; `cancel: true` calls off a pending one. Needs a credential with `mayPublish` |
| `post.unpublish` | Withdraw the published row; the draft stays. Takes `when` and `cancel` the same way, so a takedown can be dated and a live page stays up until then |
| `post.delete` | Trash a post. Needs `confirm` |
| `post.restore` | Undo a trash. Addressed `id:`, from `find?trash=true` |
| `render.provision` | Create the `PageLayout`/`Region`/`Theme` post types this instance renders with, idempotently. **Takes no address**: it acts on the instance's model. `data.reconcile: true` also adds fields an older instance is missing; `data.types` installs vocabulary types by name |

Every delete goes to the **trash** and has a restore beside it. On any of the three,
`data.purge: true` removes instead of trashing: it frees the file bytes, fires the
`DELETE` webhook, and there is no undo.

Do `render.provision` **once before writing a layout**: `post.upsert` cannot create a
system post type, and until those types exist the preview route has nothing to compose.

The instance publishes this table itself, so it can never go stale on you:
`GET /api/v2/agent/context?include=ops` returns the same vocabulary the server enforces,
one line per op with the keys each one takes.

### Destructive ops need a confirm token

A delete answers `428 Precondition Required` with a `confirm-required` problem carrying
the token its own dry run produced. Pass it back as `confirm` on the batch. The token
describes *that* deletion, so it cannot be reused to approve a different one: an agent
cannot rubber-stamp itself.

### Desired state

```http
POST /api/v2/agent/plan     # what would change; never writes
POST /api/v2/agent/apply    # make it true, atomically
```

Where a batch says *do these things*, a desired-state document says *this is what the
site should look like*:

```json
{
  "site": "mysite",
  "createSite": true,
  "postTypeLayout": { "Article": "Article Layout" },
  "folders": [ { "path": "blog" } ],
  "posts": [
    { "url": "blog/hello-world", "type": "Article",
      "data": { "TITLE": "Hello world" }, "publish": true }
  ],
  "moves": [ { "from": "blog/old-url", "to": "blog/new-url" } ],
  "prune": false
}
```

`plan` is the drift guard: an empty `changed` count means git and the server agree.
`prune: true` also removes what the document does not mention, which is why a plan that
would delete needs `confirm` exactly as a batch does. `moves` exist so a rename is
recorded as a move rather than inferred as a delete plus a create.

---

## Closing the loop without asking anyone

An agent that writes and then asks a person "does it look right?" has not automated
anything. Two mechanisms answer that question in text.

Both write calls take the proof with them, so the closing turn costs no extra round
trip:

| Parameter | On `batch` / `apply` |
|---|---|
| `?verify=true` | Attach the lint report for what this write touched |
| `?digest=true` or `?digest=40` | Attach the two structural hashes for **every page this write affected**, which for an edit to a shared layout is not the address you wrote but every page rendering through it. Capped, because each row is a real render |

And both are endpoints in their own right:

```http
GET /api/v2/agent/verify?site=mysite&checks=content,routes
GET /api/v2/agent/render?address=post:mysite/blog/hello-world
```

`verify` returns a machine-readable report with a **fix per finding**. Five check
groups: `content` (fields, references, assets, friendly URLs are internally consistent),
`routes` (every published URL resolves and links land), `diagnostics` (the asynchronous
failures above), `a11y`, and `delivery`, which *fetches* the published pages to prove they
are really served, and is therefore opt-in by an operator setting, not by a caller.

- **`a11y`** reads the rendered pages the way someone who is not looking at them receives
  them: `heading-skip`, `lang-missing`, `link-no-text`, `landmark-missing` and
  `form-label-missing`. It renders, so it is its own group, asked for by name.
- **`routes`** also reports `url-orphaned`, an error: an address that was a page here, no
  longer answers, and is still linked from a published page. Its fix is the `post.redirect`
  op that keeps the old address answering (see
  [Keeping an old address answering](/docs/content-lifecycle/#keeping-an-old-address-answering)).
  With search indexing on, `index-unroutable` warns about a site whose published content is
  routed to no search index, so every push it would make is never made.

Whether a page is findable has its own read: `include=index` on `/agent/read` says whether
indexing is on, which search site the page belongs in, whether it is indexed, and the last
indexing failure recorded against it. `GET /api/v2/agent/index` (`shio://index`, and a card
in the console) is the whole routing table: every site, the search site it feeds, and how
many published pages it has. Scope a
run with `site`, `folder`, `address`, `checks`, `limit` or `since=<cursor>` so a loop
re-checks only what changed. `render` returns a page's structural digest, 
heading outline, landmarks, link and image inventory with alt text, word count, plus an
appearance hash, kept as two separate values so a restyle and a structural break are
never mistaken for each other. It digests the markup Shio holds and needs no front end
running; digesting a live delivery URL instead (`url=`) is off unless an operator turns
it on, because an endpoint that fetches on the server's behalf needs the operator's
consent, not the caller's.

---

## What a curator could change

Building a page is half the job. The other half is whether the person who approves it can
*edit* it afterwards: a page that renders correctly and exposes nothing to change is a
screenshot with extra steps.

```http
GET /api/v2/agent/editable?address=post:mysite/blog/hello-world
```

```json
{
  "address": "post:mysite/blog/hello-world",
  "source": "rendered",
  "fields": [
    { "address": "post:mysite/blog/hello-world", "field": "title", "region": "main" },
    { "address": "post:mysite/blog/hello-world", "field": "html",  "region": "main" }
  ],
  "regions": ["main", "sidebar"]
}
```

Each entry is an address a write already takes and a field name, so the answer goes
straight into `post.upsert` with no second call.

It reads the **rendered page**, not the content model. The two are different questions:
the model says what a field *could* hold, the page says what somebody can actually click,
and only the second one is what a curator experiences. The annotations it reads are the
same ones the Universal Editor uses in the browser, so a front end of your own that emits
them answers this call too.

`source` matters when the list is empty. `rendered` means the page composed and genuinely
has nothing annotated; `content` means it does not render yet (no layout bound, or no
friendly URL), which is the normal state of a site mid-build and not the same answer at
all.

`regions` lists every region the page declares, including empty ones, because an empty
region is where content *can* go.

### Which console screen is which call

The console's own navigation is built from a declaration you can read, so "is there an
agent way to do what that screen does" is a command rather than a question for whoever
wrote the screen:

```
shio surfaces
```

```
shio surfaces: 17
content:
  sites                /bento/content
    browse a site's folders and posts
    → tool shio_find
administration:
  admin                /bento/admin
    the administration hub
    ✗ navigation: a landing page listing the surfaces below it, which is a way of getting
      somewhere rather than a capability
```

Every screen is either answered by a `tool`, an `endpoint` or an `op`, or carries a
reason it is not, from a closed vocabulary: `navigation`, `operator-only`, `rest-only`,
`chrome`, `accessibility`, `cache`, `preference`, `contract-leg`, and `gap` for the ones
that are simply missing. A screen with neither fails the build, so the list cannot quietly
fall behind the rail, and an invented reason fails it too. The same declaration is
`include=surfaces` on the manifest and `shio://surfaces` over MCP.

### Handing a page to a person

A read can carry the two links a handoff needs, and does so only when asked, because they
cost about 26 tokens a row:

```http
GET /api/v2/agent/read?address=post:mysite/blog/hello-world&include=links
```

```json
{
  "address": "post:mysite/blog/hello-world",
  "curatorUrl": "http://localhost:2710/bento/content/post/4f1c…",
  "previewUrl": "http://localhost:2710/mysite/blog/hello-world"
}
```

`shio_read` takes `links`, a batch takes `links: true` for the whole run, and a publish
answers with `curatorUrl` on each change. The URLs are built from the same declaration the
rail is, so a console route that moves moves here too rather than in a string somebody
wrote down once.

---

## Errors are instructions

Every 4xx is an RFC 9457 problem document, and it carries what to do next:

```json
{
  "type": "https://shio.viglet.com/errors/unknown-field",
  "title": "Field 'HEADLINE' is not declared by post-type 'Article'",
  "status": 400,
  "fix": "Use one of the declared fields, or add HEADLINE to the post-type first.",
  "allowed": ["TITLE", "TEXT", "ABSTRACT", "HERO"],
  "didYouMean": "TITLE",
  "example": { "op": "post.upsert", "address": "post:mysite/blog/hello-world", "data": { "TITLE": "…" } }
}
```

`fix`, `allowed`, `didYouMean` and `example` are not decoration: they are what lets an
agent recover on the next call instead of asking a curator what the API wanted.

**A field an op does not have is refused, not ignored.** An op in `POST /agent/batch` that
carries a key the op does not take (a misspelled `cancel`, say) is refused with
`undeclared-op-field`, the op's index and the nearest valid name. Before, the unknown key was
dropped and the op ran without it, so a misspelled cancel published now.

**This is not only the agent surface.** Two kinds of refusal used to answer with nothing
useful, and both are ones a caller meets *before* any other:

- **A rejected credential.** `401` and `403` are written by the security chain, which
  runs before any controller, so they used to carry Spring's own
  `{timestamp, status, error, path}`. They are now problem documents whose `fix` names
  the credential *that path* wants (a `FORM` token for `/cda/form`, an `AGENT` token
  for the agent surface and MCP, a CDA token for the CDA and GraphQL, a console session
  elsewhere) and the call that mints one. `401` and `403` mean different things: no
  usable credential, versus one that authenticated and does not reach here.
- **An unknown post-type name.** `GET /api/v2/post-type/Nope` answered `404` with an
  empty body, so a renamed type and one that never existed were the same answer. It now
  carries `allowed`, `didYouMean` and a `fix` naming both next moves: post-types are
  the one name-keyed surface, so a typo is the likeliest way to arrive.

---

## What a call costs

Responses on this surface carry **measured size budgets**, enforced in CI: a response
that grows fails the build the way a broken test does. Three levers are yours:
`format=terse` (plain text, roughly a third to a half fewer tokens), `If-None-Match` on
every read that supports it (an unchanged answer is a `304` and costs nothing), and
`fields=` / `include=` to ask for less. An `X-Shio-Est-Tokens` header reports the
estimated cost of what you were sent.

---

## Endpoints that do not exist

You cannot grep for an absence, and inventing one of these is the most common wrong
turn:

| Not there | Use instead |
|---|---|
| `POST /api/v2/post` | `post.upsert` on `/agent/batch`, or `/api/v2/post-unified` |
| `DELETE /api/v2/object/{id}` | Delete by type: `/api/v2/post-unified/{id}` or `/api/v2/folder/{id}`, or `post.delete` / `folder.delete` here |
| A console post-type controller (`/api/v2/post/type`) | `/api/v2/post-type/**`: post types are **one name-keyed surface** |
| `GET /agent/impact` | `include=impact` on `/agent/read` |

There is also no endpoint on this surface that uploads **bytes**: `post.upsert` takes
JSON, so a file goes through the static-file API and is then referenced by address.

---

## Related Pages

| Page | Description |
|---|---|
| [Pages, Layouts & Regions](/docs/website-development/) | What `render.provision` creates, and how a page is composed |
| [Content Modeling](/docs/content-modeling/) | Post types and the fields `post.upsert` is checked against |
| [Content Delivery API](/docs/headless/content-delivery-api/) | The read contract this surface is a shape over |
| [Security](/docs/security/) | Token scopes, authentication and CSRF |
