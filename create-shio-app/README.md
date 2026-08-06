# @viglet/create-shio-app

Scaffold a **Next.js (App Router)** site wired to the **Shio CMS Content Delivery
API**. The flagship "JS dev, zero Java" starter — a JavaScript/TypeScript
developer builds a full site against Shio without ever touching the Java backend.

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

## Develop this generator

```bash
npm test   # node:test — scaffolds into a temp dir and asserts the output
```

## License

GPL-3.0
