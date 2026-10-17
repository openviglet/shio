# @viglet/shio — the Viglet Shio CLI

[![npm](https://img.shields.io/npm/v/@viglet/shio?logo=npm)](https://www.npmjs.com/package/@viglet/shio)
[![License](https://img.shields.io/npm/l/@viglet/shio)](https://github.com/openviglet/shio/blob/HEAD/LICENSE)
[![Website](https://img.shields.io/badge/shio.viglet.org-0ea5e9?logo=googlechrome&logoColor=white)](https://shio.viglet.org/)

The command-line interface for **[Viglet Shio](https://shio.viglet.org/)**, the
open source, **agent-ready CMS**. It is three things at once:

- the **"JS dev, zero Java" workflow** — scaffold a site, generate types, manage
  your content model as code;
- the **content-as-files** projection — your whole site as front-mattered
  Markdown on disk, with a three-way merge back;
- the **MCP (Model Context Protocol) bridge** — `shio mcp` puts your CMS in front
  of [Claude Code](https://claude.com/claude-code) or any MCP-capable agent.

```bash
npm install -g @viglet/shio     # or: npx @viglet/shio <command>
```

Requires **Node ≥ 26**.

## Use it from Claude Code

```bash
claude mcp add shio -- npx -y @viglet/shio mcp
```

That exposes 13 tools — `shio_context`, `shio_find`, `shio_read`, `shio_write`,
`shio_apply`, `shio_publish`, `shio_assets`, `shio_blueprint`, `shio_changes`,
`shio_verify`, `shio_digest`, `shio_remember`, `shio_forget` — plus the
`shio://manifest`, `shio://blueprints`, `shio://context` and
`shio://schema/{postType}` resources. There is also a **full Claude Code plugin**
that adds skills and slash commands on top:

```bash
claude plugin marketplace add openviglet/shio
claude plugin install shio
```

See [shio.viglet.org/claude-code](https://shio.viglet.org/claude-code/).

## Commands

Run `shio <command> --help` for a command's own flags.

### Start a project

| Command | What it does |
|---|---|
| `shio init [name]` | Scaffold a Next.js site wired to the Content Delivery API (`--blueprint <name>` takes a starter from the instance instead) |
| `shio blueprints` | List the appliable starting points on the instance |

### Author content

| Command | What it does |
|---|---|
| `shio pull` | Post-types into `shio/post-types/*.json`; `--content` also projects pages to `shio/content/**` |
| `shio push` | Apply local post-types back to Shio (`--dry-run`, `--check`, `--prune`, `--content`) |
| `shio apply [file]` | Plan/apply a desired-state content document, or a ready-made blueprint package |
| `shio publish [addr]` | Make drafts live, or take live pages down (`--unpublish`, `--site … --all`) |
| `shio import <file>` | Clone an exchange package (`.zip`) into this instance |
| `shio build` | Compile local DSL/JSON post-types to canonical JSON |

### Understand the instance

| Command | What it does |
|---|---|
| `shio context` | The agent context pack — model + sitemap + conventions, in **one call** |
| `shio resolve <addr>` | Resolve a content address (`post:` / `folder:` / `site:` / `id:`) |
| `shio impact <addr>` | What a change to this template would affect: layouts, types, page counts |
| `shio changes` | What changed, with a cursor to resume from (`--since`, `--cursor`) |
| `shio remember` | Write down a decision the next session should not re-derive |

### Prove the work

| Command | What it does |
|---|---|
| `shio verify` | Lint the content and its routes; exits non-zero on an error — **the CI gate** |
| `shio digest <page>` | Structural digest of one page: outline, landmarks, links, images |
| `shio audit <page>` | What is wrong with a rendered page, as text with a selector each |
| `shio snapshot <page>` | PNG of a page, and whether it looks different (needs Playwright) |
| `shio diff` | Compare what a replica serves with the capture that declared it |
| `shio report` | The human handoff: what changed, preview links, open questions |

### Replicate an existing website

| Command | What it does |
|---|---|
| `shio clone <url>` | Capture a site that already exists — an inventory, and no content |
| `shio propose` | Which captured blocks could become sections, and what each would lose |
| `shio convert` | Apply the accepted plan: sections, theme and scripts, as **DRAFT** |

### Front-end loop

| Command | What it does |
|---|---|
| `shio types` | Generate a `.d.ts` from the live post-types — autocomplete on `post.attrs.*` |
| `shio dev` | Generate types, start the site dev server, watch the model (`--content` syncs both ways) |
| `shio deploy` | `push` (apply) then `types` |
| `shio mcp` | Bridge stdio to this instance's MCP endpoint |

## Connection

| Flag | Env | Used by |
|---|---|---|
| `--url <baseUrl>` | `SHIO_URL` (default `http://localhost:2710`) | all |
| `--user` / `--password` | `SHIO_USER` / `SHIO_PASSWORD` | pull / push / apply / deploy |
| `--token <token>` | `SHIO_CDA_TOKEN` | types / dev / deploy |

Editing the content model is an **admin** act, so `pull`/`push` use the console
username and password; `types` uses a Content Delivery API token.

## Post-types as code (GitOps)

```bash
shio pull --user admin --password admin   # → shio/post-types/*.json
git add shio/post-types && git commit -m "snapshot content model"
# …edit a JSON: add a field, change a label…
shio push --dry-run                       # preview the diff
shio push                                 # apply
shio push --check                         # CI drift guard: non-zero if the plan is non-empty
shio push --prune                         # also delete remote types absent locally
```

The JSON is a **lossless** view of each post-type (every field including `Tab`,
verbatim `widgetSettings`, nested Relator sub-fields), so the round-trip
preserves the full authoring model. System post-types are read-only.

`shio/post-types/` may also hold `.ts`/`.mjs` sources written with the typed DSL
[`@viglet/shio-model`](https://www.npmjs.com/package/@viglet/shio-model) —
`push` and `build` compile them to canonical JSON before diffing.

## Content as files

```bash
shio pull --content     # the whole site as front-mattered Markdown in shio/content/**
# …edit with your editor, or let an agent use Edit / Write / Grep…
shio push --content     # three-way merge back (--ours, --theirs, --force)
```

This is the point of the design: **files beat APIs for authoring**, because
`Edit`, `Write` and `Grep` are the cheapest tools an agent has.

## Related packages

| Package | For |
|---|---|
| [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client) | Zero-dependency Content Delivery API client |
| [`@viglet/shio-react-sdk`](https://www.npmjs.com/package/@viglet/shio-react-sdk) | React hooks and render components |
| [`@viglet/shio-model`](https://www.npmjs.com/package/@viglet/shio-model) | Author the content model as typed code |
| [`@viglet/shio-sections`](https://www.npmjs.com/package/@viglet/shio-sections) | The page-section vocabulary |
| [`@viglet/shio-editor-cors`](https://www.npmjs.com/package/@viglet/shio-editor-cors) | Inline visual editing with the Universal Editor |
| [`@viglet/create-shio-app`](https://www.npmjs.com/package/@viglet/create-shio-app) | Next.js starter generator |

## License

Apache-2.0 — [Viglet Shio](https://shio.viglet.org/) ·
[github.com/openviglet/shio](https://github.com/openviglet/shio) ·
[docs.viglet.org/shio](https://docs.viglet.org/shio/) ·
part of the [Viglet](https://www.viglet.org) open source family.
