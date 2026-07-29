---
name: shio-content-files
description: Read before touching Shio's content-as-files projection, the shio CLI, or blueprints. Triggers on shio/content/**, shio/folders/**, shio/assets/**, shio/post-types/*.json, content-files.mjs, content-sync.mjs, content-store.mjs, content-assets.mjs, content-notes.mjs, the three-way merge and content-base.json, sidecar files, movedFrom, shio pull/push/apply/verify/report/remember, blueprint.json, ShBlueprintRegistry, and the CI gate order.
---

# Content as files, the CLI, and blueprints

Law P4 in practice: content projects to **one Markdown file per post**, and the
agent's cheapest tools (`Edit`/`Write`/`Grep`) reach it.

**Read the area file before editing:**
- [docs/agents/content-files.md](../../../docs/agents/content-files.md) — the format,
  the three-way merge, sidecars, folders-as-files, moves, assets
- [docs/agents/cli.md](../../../docs/agents/cli.md) — every `shio` command and the
  **CI gate order**
- [docs/agents/blueprints.md](../../../docs/agents/blueprints.md) — the package
  format and its load-time validation

## The rules that are load-bearing

1. **The path is the URL, not the folder chain.** A file whose path and `url:`
   disagree is an error — a half-done rename is a move on one side and a create on
   the other.
2. **Only the DRAFT projects.** `publish:` in a file is an *instruction* push
   forwards, never state pull writes back.
3. **`moves` is the document's one intent channel.** State cannot express "this is
   the same post": without it, `--prune` deletes the old URL and creates a new post
   with a new id and no history. Ambiguity is **refused**; a plausible-but-unprovable
   pair is **printed** with the `movedFrom:` line to add.
4. **The merge happens in the projection's domain (SH253).** Pass a remote document
   through `projectedPost` on the way in, or a normalization the format applies makes
   every pull report a local change nobody made — forever.
5. **A blueprint may not name its own site and may not `prune`.**
6. **Losslessness beats prettiness**, and a value a format cannot hold must be
   **refused**, not trimmed (SH256, SH257).

## Guards that already exist

`cli/test/content-round-trip.test.mjs` asserts the round trip as a **property** over
an adversarial value list × six round trips, with `projectedPost` a fixed point — and
**a declared loss the format no longer has fails the build**. If you add a
normalization in `content-files.mjs`, `content-notes.mjs` or `content-assets.mjs`,
that is the test that will tell you what it costs. `test/emit.test.mjs` fails on
blueprint/front-end drift and asserts the section overlay changes exactly three
files.

## Two things the CLI suite earned the hard way (SH198)

- A fake filesystem **normalizes its keys** (`test/support/paths.mjs`) — the commands
  join with `node:path`, so on Windows a POSIX literal never matches and the test
  fails on a *message* several assertions from the cause. Do not "fix" it by making a
  command emit forward slashes.
- A test that arms a live handle releases it in **`t.after`**, never only on the happy
  path.

Build order note: the CLI suite needs `pnpm -C shio-client run build` first;
`shio-sections`' suite needs `pnpm -C shio-model run build`.
