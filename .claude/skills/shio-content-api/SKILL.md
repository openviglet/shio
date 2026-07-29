---
name: shio-content-api
description: Read before touching Shio's content REST API or post types. Triggers on /api/v2/object/{id}/list or /path, ShConsoleListing, ShConsolePost, /api/v2/post-unified/**, copyto/moveto, /api/v2/folder/**, /api/v2/site/**, /api/v2/import, /api/v2/staticfile/**, the trash and untrash/purge, ShFolderWriteService, ShObjectWriteService, ShAddressResolver, and /api/v2/post-type/** including rename.
---

# Shio content API + post types

**Read [docs/agents/content-api.md](../../../docs/agents/content-api.md)** (console
reads, unified posts, copy/move, folders, sites, exchange import, static files, the
trash) and
**[docs/agents/post-types.md](../../../docs/agents/post-types.md)** (the one
name-keyed post-type surface).

## The facts that prevent the common wrong turns

- **There is no `/api/v2/post` controller** — do not add one. Post CRUD is
  `/api/v2/post-unified/**` on `sh_post_unified`; the legacy split
  `ShPost`/`ShPostDraft` were deleted in the nuclear refactor.
- **There is no generic `DELETE /api/v2/object/{id}`.** Delete by type.
- **There is one post-type API and it is keyed by name.** SH117 retired the
  id-keyed, entity-shaped twin at `/api/v2/post/type`; a reference to that path is
  stale. Renaming is `POST /api/v2/post-type/{name}/rename` and nothing else — a
  `PUT` to a new name creates a *second* type and orphans the first while answering
  `200`.
- **Never serialize `ShFolder`/`ShPostUnified` out of a console read.** Console reads
  return the CDA shapes (`ShConsoleListing`, `ShConsolePost`), owned by
  `ShConsoleNavigationMapper` / `ShConsolePostMapper`, mapped **inside** a
  transaction because `shPostType`/`shFolder` are LAZY.
- **Both ends of a move or copy are authorized (SH201)**, and the write service —
  not the controller — owns the refusals (`checkMovable` / `checkCopyable`).
- **A friendly URL is not unique by construction (SH321).** The finder returns an
  ordered `List`: a reader that must serve something takes the first;
  `ShAddressResolver` **refuses** with a teaching 409, because an address is what a
  *write* takes. **A derived `Optional` over a `furl` fails the build** —
  `ShFurlReaderLintTest`, `default` is the one hatch (SH323).
- **`duplicate-url` is locale-blind on purpose (SH323).** An address carries no
  locale, so two posts on one URL are unaddressable whatever tells them apart. A
  translation needs its own URL.

## Guards that already exist

`ShConsoleShapeOverlapTest` fails the build when a component shared between a
console row and its CDA counterpart drifts. `ShFolderSiteInvariantTest` catches
`folder.getShSite()`. `ShRootFolderReaderLintTest` refuses picking from the unordered
`findByShSiteAndRootFolder` `Set` (it permits *iterating*).

## The one rule to carry into any new read

**The order and the filter are the server's.** A client-side sort over a paged
listing sorts one page and reads exactly like a correct answer; an unknown `sort` is
a teaching 400, never a fallback, because a listing answered in another order returns
other *rows*.
