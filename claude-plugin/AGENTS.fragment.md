<!--
  Paste this into your project's AGENTS.md (or CLAUDE.md) when the project's content lives
  in Shio. It is the minimum an agent needs to stop discovering the CMS on every session.

  This fragment is written by hand and therefore rots. **Prefer the generated form:**
  `shio context --as agents-md --out AGENTS.md` renders the same guidance plus this
  instance's actual content model, folder tree and remembered conventions, so it cannot
  drift from the CMS. Regenerate it and the diff is the drift. Use this hand-written
  fragment only when the instance is not reachable at authoring time.
-->

## Content lives in Shio

The CMS is Shio, driven over MCP (`shio_*` tools) or the `shio` CLI. The console at
`/console` is for a curator to review, not for the agent to click through.

**Every session, first:** `shio_context` — the content model, the sitemap and this
instance's conventions in one call. Keep the `digest` it returns; next session, compare it
before re-reading anything (over HTTP it is also the `ETag`, so an unchanged instance
answers `304` and costs nothing). Then `shio_changes` to see what a curator changed while you
were away.

**Read the conventions, and add to them.** Lines prefixed `[memory:key]` were written by an
earlier session or by a curator, and the attribution is part of the claim. When you work
something out that the next session would otherwise re-derive — "this site's blog uses
`Article`, not `Post`" — record it: `shio remember blog.post-type "..."`. That is the one
thing that makes session N+1 cheaper than session N.

**Before modelling anything by hand:** read `shio://blueprints`. A blueprint applies a whole
section — post-types, folders, sample pages — in one call, and re-applying it changes
nothing.

**Addressing:** `post:<site>/<friendly-url>` and `folder:<site>/<name-chain>`. Never carry a
UUID between calls. A folder path and a friendly URL are separate namespaces.

**Writing:** `shio_apply` with a desired-state document, `dryRun: true` first, then again
without it. Writes land in **DRAFT**; publishing is a separate step and needs the user's say.
To rename or move a page use `post.move` — an upsert at a new address creates a second post
and orphans the first.

**Authoring more than a couple of pages:** `shio pull --content` projects the content to
`shio/content/<site>/<url>.md`. Edit those files with your normal tools, then
`shio push --content --dry-run` and apply. That path costs no API tokens for the editing.

**Before saying you are done:** `shio_verify`. `ok:false` means an error; warnings are things
the CMS cannot prove from the inside. Report what you verified and what you did not.

**The content model is code:** `shio/post-types/*.json` (or `.ts` with
`@viglet/shio-model`), pushed with `shio push`. `shio push --check` is the CI drift guard.
