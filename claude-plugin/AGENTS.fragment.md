<!--
  Paste this into your project's AGENTS.md (or CLAUDE.md) when the project's content lives
  in Shio. It is the minimum an agent needs to stop discovering the CMS on every session.

  This fragment is written by hand and therefore rots. SH115 replaces it with
  `shio context --as agents-md`, which renders the same thing from the live instance —
  prefer that once it ships.
-->

## Content lives in Shio

The CMS is Shio, driven over MCP (`shio_*` tools) or the `shio` CLI. The console at
`/console` is for a human to review, not for the agent to click through.

**Every session, first:** `shio_context` — the content model, the sitemap and this
instance's conventions in one call. Keep the `digest` it returns; next session, compare it
before re-reading anything. Then `shio_changes` to see what a human changed while you were
away.

**Before modelling anything by hand:** read `shio://blueprints`. A blueprint applies a whole
section — post-types, folders, sample pages — in one call, and re-applying it changes
nothing.

**Addressing:** `post:<site>/<friendly-url>` and `folder:<site>/<name-chain>`. Never carry a
UUID between calls. A folder path and a friendly URL are separate namespaces.

**Writing:** `shio_apply` with a desired-state document, `dryRun: true` first, then again
without it. Writes land in **DRAFT**; publishing is a separate step and needs the user's say.
To rename or move a page use `post.move` — an upsert at a new address creates a second post
and orphans the first.

**Deleting:** a call whose plan contains a `delete` is refused unless it carries the
`confirm` value the dry run of that same call returned. Dry-run it, show the user what would
go, then re-send with `confirm`. The refusal never contains the token — that is deliberate,
so nothing is deleted that a person was not shown first. Your credential may also be denied
publishing altogether; check `GET /api/v2/agent/session` before planning around it.

**Authoring more than a couple of pages:** `shio pull --content` projects the content to
`shio/content/<site>/<url>.md`. Edit those files with your normal tools, then
`shio push --content --dry-run` and apply. That path costs no API tokens for the editing.

**Before saying you are done:** `shio_verify`. `ok:false` means an error; warnings are things
the CMS cannot prove from the inside. Report what you verified and what you did not.

**The content model is code:** `shio/post-types/*.json` (or `.ts` with
`@viglet/shio-model`), pushed with `shio push`. `shio push --check` is the CI drift guard.
