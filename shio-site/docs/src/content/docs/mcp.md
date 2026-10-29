---
title: MCP Server
description: "Drive Viglet Shio from Claude Code, Cursor or any MCP client: POST /mcp, twelve tools, six resources, four prompts, and the Claude Code plugin."
sidebar:
  label: MCP Server
---

Shio speaks the **Model Context Protocol**. One endpoint:

```http
POST /mcp
```

JSON-RPC 2.0 over streamable HTTP. It answers `initialize`, `tools/list`, `tools/call`,
`resources/list`, `resources/read`, `prompts/list` and `prompts/get`. Two headers matter:
`Mcp-Session-Id` (returned by `initialize`, sent back on every later call) and
`MCP-Protocol-Version`.

This is the surface the product is designed around: the agent that builds your site
talks to Shio here. It is a **shape over the same capabilities** as
[the REST agent surface](/shio/docs/agent-surface/), not a separate contract: a tool call ends
up in the same service the REST endpoint calls, under the same guards.

:::caution[A bare instance has no MCP server registered for your client]
Starting Shio does not make it appear in Claude Code. Until you register it, `POST /mcp`
is just an HTTP endpoint. Two ways to close that gap, both below: **install the plugin**,
or point your client at the CLI's stdio bridge with
`claude mcp add shio -- shio mcp`. Skipping this is worth an afternoon of confusion.
:::

---

## Connecting

### With the Claude Code plugin (recommended)

```bash
claude plugin marketplace add openviglet/shio
claude plugin install shio
```

The plugin registers the MCP server for you and adds the workflows around it, so there
is nothing else to install. What it brings:

| Kind | Contents |
|---|---|
| **MCP server** | `shio`, wired to your instance |
| **Commands** | `/shio-start` (bring up an instance and connect), `/shio-check` (verify what is there), `/shio-blueprint` (apply a starting point) |
| **Skills** | `shio-site` (build and change a site), `shio-content-model` (post types), `shio-verify` (close the loop on a change) |

### With the CLI bridge

If you already have the `shio` CLI on your `PATH`:

```bash
claude mcp add shio -- shio mcp
```

`shio mcp` is deliberately dumb: it frames stdio JSON-RPC into HTTP `POST`s against
`/mcp`. Nothing about the protocol lives in the CLI, so a client that speaks stdio and
one that speaks HTTP reach exactly the same server. It reads `SHIO_URL`, `SHIO_USER` and
`SHIO_PASSWORD` from the environment.

### Directly over HTTP

```bash
curl -s http://localhost:2710/mcp \
  -H 'Content-Type: application/json' \
  -H 'Key: 7f3c9a12b4e05d68af1c2903b' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

A tool call runs under the credential on the request, normally an **API key of `AGENT`
scope**. That scope may write here and on `/api/v2/agent/**` and nowhere else, it reads
drafts by definition, and publishing needs `mayPublish` on the token. See
[the agent surface](/shio/docs/agent-surface/#authenticating).

---

## The tools

Twelve tools, and the number is the point: **the tool list is a measured budget, not an open
catalogue.** `tools/list` is paid for on every turn of every conversation, so its total
estimated token size is asserted in CI against a declared ceiling, and the count itself is
capped by a test. Adding a thirteenth tool means either fitting the budget or raising it
deliberately and saying why. That is
also why some capabilities are a *parameter* on an existing tool rather than a tool of
their own.

| Tool | What it does |
|---|---|
| `shio_context` | The one call that replaces a session: content model, sitemap by path, conventions. `format=agents-md` returns the same pack as project instructions, to write to a file |
| `shio_find` | Find posts by site / type / folder / text, addresses and titles, never bodies |
| `shio_read` | Read posts by address, optionally projected to named fields. `digest` adds each post's state digest, the value `shio_write`'s `expect` takes |
| `shio_write` | A batch of addressed ops, applied atomically; `dryRun` reports without writing |
| `shio_apply` | Make a desired-state document true |
| `shio_publish` | Publish or unpublish, now or at `when`; `cancel` calls off what is pending |
| `shio_verify` | Lint content and routes; a fix per finding |
| `shio_digest` | A page's structural digest: the cheap proof a render still looks right. `include=editable` also lists the fields a curator could edit |
| `shio_changes` | What changed since a cursor: what the curator did while you were away. `address` narrows it to one page |
| `shio_remember` | Write the conventions the next session should inherit, and drop one, with `forget` |
| `shio_assets` | The files a site holds |
| `shio_marketplace` | The packages this instance could install. Lists and describes; it does not install. See [Blueprints § Who may install](/shio/docs/blueprints/#who-may-install) |

Three capabilities you might expect as tools are **parameters** instead, and that is the
budget above being spent deliberately rather than an omission. Applying a starting point
is `shio_apply`'s `blueprint` parameter, not a `shio_blueprint` tool; forgetting a
convention is `shio_remember`'s `forget`; asking which fields a curator could edit is
`shio_digest`'s `include=editable`, because it is another reading of a render that tool
already takes. Each would otherwise be a schema paid for on every turn of every session:
the third was measured at 24 tokens as a parameter against 146 as a tool of its own.

`shio_context`'s `format` is the same budget spent the other way, and it documents **one**
value rather than three. `format=json` is deliberately absent: the caller here is a model,
terse is what it should read, and an option nobody should choose is still an option every
turn pays to read. `agents-md` earns its place because it is the one rendering meant to be
*written to a file*, and while it was CLI-only, the client most likely to have a file to
write it to was the one that could not ask for it.

### A call, and what comes back

```json
{
  "jsonrpc": "2.0", "id": 7,
  "method": "tools/call",
  "params": {
    "name": "shio_write",
    "arguments": {
      "dryRun": true,
      "ops": [
        { "op": "post.upsert", "address": "post:mysite/blog/hello-world",
          "type": "Article", "folder": "folder:mysite/blog",
          "data": { "TITLE": "Hello world" } }
      ]
    }
  }
}
```

`ops` is the only required argument. `op` and `address` are required on each row; `type`
and `folder` only when the op creates a post. `dryRun: true` reports what would change
and writes nothing, and is how you obtain the `confirm` token a delete needs.

Four more fields on an op, each optional:

| Field | What it does |
|---|---|
| `locale` | The locale a created post is in. On create only: an upsert that finds the post ignores it |
| `translationOf` | The address of the post a created one translates. On create only |
| `url` | The friendly URL exactly as the source spells it, trailing slash included. On create only |
| `expect` | A digest from `shio_read` with `digest: true`. The op is refused if the post changed since that read, instead of overwriting whatever changed it |

`when` on `post.publish` schedules the publish instead of doing it now.

### When the arguments are wrong

An error is not a refusal, it is instructions. The same problem document the REST surface
returns comes back as the tool result, carrying `fix`, `allowed`, `didYouMean` and
`example`:

```json
{
  "title": "Field 'HEADLINE' is not declared by post-type 'Article'",
  "fix": "Use one of the declared fields, or add HEADLINE to the post-type first.",
  "allowed": ["TITLE", "TEXT", "ABSTRACT", "HERO"],
  "didYouMean": "TITLE",
  "example": { "op": "post.upsert", "address": "post:mysite/blog/hello-world",
               "data": { "TITLE": "…" } }
}
```

That is the difference between an agent that recovers on its next call and one that asks
you what the API wanted.

### Argument names that differ between surfaces

The same concept is spelled differently by `shio_write`, `shio_apply` and the REST
endpoints, because each inherits the shape of what it wraps. This table is the one worth
keeping open:

| Concept | `shio_write` op | `shio_apply` document | REST elsewhere |
|---|---|---|---|
| Post type | `type` | `type` | `postType` on `/api/v2/post-unified` |
| Subject | `address` (a full `post:` address) | `url` (bare, no prefix) | an id |
| Folder | `folder:<site>/<chain>` | `/chain` (bare path) | a folder id |
| Several subjects | `address` , singular name, array value | , |, |
| Memory scope | `site` on `shio_remember` |, | `scope` on `PUT /agent/memory`, `--site` in the CLI |

---

## Resources

Read-only resources, for a client that prefers attaching context to calling a tool:

| URI | Contents |
|---|---|
| `shio://manifest` | What this instance can do |
| `shio://context` | The context pack |
| `shio://ops` | The op vocabulary, with each op's required arguments |
| `shio://schema/{postType}` | One post type's fields |
| `shio://blueprints` | The blueprint catalogue |
| `shio://blueprint/{name}` | One blueprint's full schema |
| `shio://requests` | Questions curators sent from the console's dock, waiting for an answer |

### Answering a curator's question

A curator can ask the connected agent something from the console's
[assistant dock](/shio/docs/content-console/#the-assistant-dock), or from a field of the post form.
Each question is a note of kind `request` on the page it is about, and `shio://requests` lists
the open ones, newest first, one line each: the note id, the page, who asked, and the question.
A line with `field=<name>` asks for a value for that field.

Answer on the same thread with `shio_write`, then mark the request done:

```json
{"op": "note.add", "address": "id:<page>",
 "data": {"kind": "reply", "parent": "<note>", "body": "Done: the intro is two sentences now."}}
{"op": "note.resolve", "address": "id:<page>", "data": {"note": "<note>"}}
```

- **A field request wants the value itself.** Put only the value in `body`. The curator gets
  an **Apply** button that writes it into the form as an unsaved edit.
- **If your answer changes the page, make the change first and reply in the same session.**
  The reply then names your run, and the curator can open the change or approve it from the
  dock. The approval is still theirs: it goes through the same review queue your runs always
  do.

---

## Prompts

Four, each a workflow rather than a sentence: they expand into the ordered tool calls
that get the job done, with the safety defaults already in place (everything is left in
`DRAFT` for a curator to publish):

| Prompt | Arguments | Does |
|---|---|---|
| `new-site` | `site`, `brief` | Build a site from nothing: provision the render model, create the folders and pages, verify |
| `add-section` | `site`, `path`, `brief` | Add a section to a site that exists, matching what is already there |
| `translate-site` | `site`, `locale` | Produce the sibling pages for another language |
| `audit-content` | `site` | Run the checks and report what a curator should look at |

---

## Related Pages

| Page | Description |
|---|---|
| [The Agent Surface](/shio/docs/agent-surface/) | The REST half: every endpoint, the address grammar, the op vocabulary |
| [Pages, Layouts & Regions](/shio/docs/website-development/) | What `render.provision` creates and how a page is composed |
| [Content Modeling](/shio/docs/content-modeling/) | The post types `shio_write` is checked against |
