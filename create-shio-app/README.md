# @viglet/create-shio-app

[![npm](https://img.shields.io/npm/v/@viglet/create-shio-app?logo=npm)](https://www.npmjs.com/package/@viglet/create-shio-app)
[![License](https://img.shields.io/npm/l/@viglet/create-shio-app)](https://github.com/openviglet/shio/blob/HEAD/LICENSE)
[![Website](https://img.shields.io/badge/website-0ea5e9?logo=googlechrome&logoColor=white)](https://openviglet.github.io/shio/)

Scaffold a **Next.js (App Router)** site wired to the Content Delivery API of
**[Viglet Shio](https://openviglet.github.io/shio/)**, the open source, **agent-ready CMS
for coding agents**.

The flagship "JS dev, zero Java" starter — a JavaScript/TypeScript developer
builds a full site against Shio without ever touching the Java backend.

## Usage

Interactive:

```bash
npm create @viglet/shio-app@latest
# or
npx @viglet/create-shio-app
```

Non-interactive (CI / scripted):

```bash
npx @viglet/create-shio-app my-site \
  --url https://cms.example.com \
  --site <site-id> \
  --token <cda-token> \
  --yes
```

| Flag | Maps to |
|---|---|
| `--url` | `SHIO_URL` |
| `--site` | `SHIO_SITE_ID` |
| `--token` | `SHIO_CDA_TOKEN` |
| `--yes`, `-y` | skip prompts (use defaults / flags) |

## What you get

```
my-site/
  app/
    layout.tsx
    page.tsx              # lists published posts (shio.query)
    [...slug]/page.tsx    # CDA path → post (shio.getPostByUrl) + SSG/ISR
  lib/shio.ts             # shared @viglet/shio-client instance
  next.config.mjs
  tsconfig.json
  .env.local              # filled from your answers/flags
  .env.example
  .gitignore
  README.md
```

The generated project depends on [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client)
and talks only to the stable CDA contract (`/api/v2/cda/**`).

## Next steps

```bash
npx @viglet/shio types     # generate a .d.ts so post.attrs.* autocompletes
npx @viglet/shio dev       # types + dev server + model watch
npx @viglet/shio pull --content   # the whole site as Markdown on disk
```

Driving the CMS from [Claude Code](https://claude.com/claude-code)? Add the MCP
server and the agent gets the same surface:

```bash
claude mcp add shio -- npx -y @viglet/shio mcp
```

## Develop this generator

```bash
npm test   # node:test — scaffolds into a temp dir and asserts the output
```

## Related packages

| Package | For |
|---|---|
| [`@viglet/shio`](https://www.npmjs.com/package/@viglet/shio) | The Shio CLI — content as files, type generation, and the MCP bridge |
| [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client) | Zero-dependency Content Delivery API client (what the scaffold uses) |
| [`@viglet/shio-react-sdk`](https://www.npmjs.com/package/@viglet/shio-react-sdk) | React hooks and render components |
| [`@viglet/shio-sections`](https://www.npmjs.com/package/@viglet/shio-sections) | The page-section vocabulary |
| [`@viglet/shio-model`](https://www.npmjs.com/package/@viglet/shio-model) | Author the content model as typed code |
| [`@viglet/shio-editor-cors`](https://www.npmjs.com/package/@viglet/shio-editor-cors) | Inline visual editing with the Universal Editor |

## License

Apache-2.0 — [Viglet Shio](https://openviglet.github.io/shio/) ·
[github.com/openviglet/shio](https://github.com/openviglet/shio) ·
[docs.viglet.org/shio](https://docs.viglet.org/shio/) ·
part of the [Viglet](https://www.viglet.org) open source family.
