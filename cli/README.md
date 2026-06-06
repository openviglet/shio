# `@viglet/shio` — the Shio CMS CLI

The "JS dev, zero Java" workflow: scaffold a site, generate types, and manage
your content model **as code**.

```bash
npm install -g @viglet/shio   # or: npx @viglet/shio <command>
```

## Commands

| Command | What it does | Auth |
|---|---|---|
| `shio init [name]` | Scaffold a Next.js site wired to the CDA (delegates to `create-shio-app`) | — |
| `shio pull` | Pull post-types into `shio/post-types/*.json` | Basic (console) |
| `shio push` | Apply local post-types back to Shio (`--dry-run`, `--prune`) | Basic (console) |
| `shio types` | Generate a `.d.ts` from the live post-types (autocomplete on `post.attrs.*`) | CDA token |
| `shio dev` | Generate types, start the site dev server, refresh types on a timer | CDA token |
| `shio deploy` | `push` (apply) then `types` | both |

## Connection

| Flag | Env | Used by |
|---|---|---|
| `--url <baseUrl>` | `SHIO_URL` (default `http://localhost:2710`) | all |
| `--user` / `--password` | `SHIO_USER` / `SHIO_PASSWORD` | pull / push / deploy |
| `--token <token>` | `SHIO_CDA_TOKEN` | types / dev / deploy |

Editing the content model is an **admin** act, so `pull`/`push` use the console
username/password; `types` uses a CDA delivery token.

## Post-types as code (GitOps)

```bash
shio pull --user admin --password admin        # → shio/post-types/*.json
git add shio/post-types && git commit -m "snapshot content model"
# …edit a JSON: add a field, change a label…
shio push --dry-run                            # preview the diff
shio push                                      # apply (PUT changed/added types)
shio push --prune                              # also delete remote types absent locally (never system types)
```

The JSON is a **lossless** view of each post-type (every field including `Tab`,
verbatim `widgetSettings`, nested Relator sub-fields), so the round-trip
preserves the full authoring model. System post-types are read-only.
