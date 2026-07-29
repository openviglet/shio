---
name: shio-renderer
description: Read before touching Shio's preview renderer, page layouts, regions, themes, Handlebars templates, template helpers, or the section vocabulary. Triggers on ShPageRenderService, ShTemplateEngine, ShRenderHelpers, ShTemplateGraph, PageLayout/Region/Theme post-types, sh-region, data-shio-*, /preview/**, /agent/render, the Universal Editor bridge, render digests, unused-region/unused-layout/missing-theme/duplicate-template-title findings, and @viglet/shio-sections.
---

# Shio renderer

`GET /preview/{site}/{url}` composes a page out of content the instance already
holds. Owner: `com.viglet.shio.render`. It is **preview-grade** on purpose —
production delivery is Next.js over the CDA (P10).

**Read [docs/agents/renderer.md](../../../docs/agents/renderer.md) before editing
anything in this area.** It is the authoritative account of:

- the model as content (`PageLayout` / `Region` / `Theme` as system post-types
  created on demand, `POST /api/v2/render/post-types` and its `reconcile` verb)
- how a page finds its layout (`site.postTypeLayout`, by title), and the three
  edges — layout, region, theme
- the deliberately uneven refusal rule: **a missing layout refuses (409), a missing
  region or theme is marked**
- `ShRenderState` — one writer for the DRAFT/PUBLISHED preference, and why the row
  order must be total
- the closed helper set and `register(name, shape, helper)` as its single source
- `ShTemplateValidator` (write-time refusal) vs `template-unknown-field` (a lint)
- `/agent/render`'s structural + appearance digests, and the marker counts

## What is already guarded — you do not need to remember these

| Guard | Fails the build when |
|---|---|
| `ShRenderNoScriptEngineTest` | the no-script-engine, `MapValueResolver`-only or refusing-`TemplateLoader` rules break — *and* asserts the premises, not only the guards |
| `ShTemplateGrammarCoverageTest` | you touch the SH251 scanner and do not add its row (thirty rows, `REPORTED`/`SKIPPED`/`UNREACHABLE`) |
| `ShHandlebarsAstPremiseTest` | a Handlebars.java bump adds scope information and reopens the AST question |
| `ShRenderedDomContractTest` | code emits a `data-shio-*` attribute with no row in the SH185 spec appendix, or a row names one nothing writes |
| `ShRenderModelSourceTest` | the three render post-types drift from `import/post-types.json`, or a declared difference no longer exists |
| `ShRenderSweepBenchmarkTest` | the per-page query count regresses (6/page in a sweep, 19 standalone) |

## The two rules most often broken here

1. **`sh-region`, `sh-field`, `sh-post` survive the render.** Do not "tidy up" by
   stripping them — the rendered DOM is the only place the Universal Editor can
   learn which node maps to which post.
2. **Ownership is the template's job and the engine never infers it.** A region
   renders against the *page's* model; inside a `{{#query}}` block the row's. The
   rendered HTML records neither.

The protocol document is
[docs/specs/SH185-universal-editor-protocol.md](../../../docs/specs/SH185-universal-editor-protocol.md)
(and its appendix is the `data-shio-*` contract). Sections vs regions:
[docs/specs/SH249-sections-and-regions.md](../../../docs/specs/SH249-sections-and-regions.md).
