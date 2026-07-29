---
name: shio-console-ui
description: Read before touching Shio's React console or its security config. Triggers on shio-react/**, shadcn/ui components, the 15 form widgets and WIDGET_MAP, widgetSettings, postFormSchema, post-type.edit.page.tsx, post.edit/new pages, PostFormFields, the content browser, widget colors, sticky save bars, i18n en/pt, ShPostAttr/ShRelatorItem/ShPostType models, CSRF, ShCsrfHeaderFilter, ShStatelessRequestMatcher, and CORS.
---

# Shio console (React) + security config

**Read [docs/agents/console-ui.md](../../../docs/agents/console-ui.md)** (service
layer, context providers, the widget system, editor page patterns, the data model,
widget colours, i18n, theme) and
**[docs/agents/security.md](../../../docs/agents/security.md)** (CSRF, auth, CORS).

## The traps that bite here

- **Always guard a `widgetSettings` parse.** The backend sends the literal string
  `"null"`; `JSON.parse("null")` returns `null`, which passes a truthy check and
  crashes on property access. Use the `parseSettings()` helper.
- **`postFormSchema()` is the one exported entry point** for projecting a post-type's
  `fields` into form widgets (SH178). There used to be a second export with no
  fallback, which is why creating a post worked and editing it did not.
- **A post-type has no `id` and a field has no `id`.** The editor routes on `name`
  and mints an editor-local `key` per row for React/dnd identity, stripping it before
  save — `name` is what the user is typing and cannot double as identity.
- **`tsc -b` fails on unused imports (TS6133).** When you swap a component, remove
  the import.
- **After `pnpm add`/`remove`, clear `node_modules/.vite`** or you get 504
  "Outdated Optimize Dep".
- **Use the shared `@/lib/axios` singleton.** A private `axios.create()` silently
  opts out of every shared interceptor — including CSRF, which is required of a
  session-bearing caller (SH167). That was the Universal Editor save bug (SH233).
- **Add i18n keys to `en` *and* `pt` simultaneously.**

## Security rules that are guarded

`ShCsrfExemptionLintTest` fails the build on a **new blanket CSRF exemption** —
narrow it with `ShStatelessRequestMatcher` instead (exempt for a caller holding no
session cookie, checked for one that does). The same lint fails the day a `Mutation`
appears in `schema.graphqls`, carrying the decision made in advance. Do not widen an
exemption back to a blanket one: the failure mode is a *future* caller, which no
functional test can watch for.

## Design system

Available shadcn components and the per-widget colour table are in the area file.
**Not available — do not import:** accordion, collapsible, popover, command, calendar.
`Switch` for settings toggles, `Checkbox` only inside form widgets.
