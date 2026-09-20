import { Suspense, lazy } from "react"
import type { WidgetProps } from "./index"

/**
 * SH1044 — the code editor, loaded when there is code to edit.
 *
 * The widget map is imported eagerly by every screen that draws a post's fields, and it named the
 * Ace widget directly — which imports `@uiw/react-codemirror` and two language packages. So every
 * console route paid for a code editor, and because `vite.config.ts` routed every module id
 * containing `codemirror` into the GraphiQL chunk, the way it paid was by statically importing
 * 4.8 MB of GraphiQL and a render-blocking stylesheet for a page most curators never open.
 *
 * The split is per **field kind**, which is the only place the question can be answered: a post with
 * no `Ace Editor - *` field never renders this, and a post-type is not knowable until the form is
 * drawn. Three post-types in the vocabulary carry one — `PageLayout`, `Region` and `Theme` — so the
 * editor arrives exactly when somebody opens a template.
 *
 * The fallback is the editor's own height rather than a spinner: the widget is 250px tall and a
 * form that reflows when the last field arrives is worse to look at than one that reserves the room.
 */
const AceEditor = lazy(() => import("./ace-editor.widget")
  .then((module) => ({ default: module.AceEditorWidget })))

export function AceEditorLazyWidget(props: WidgetProps) {
  return (
    <Suspense fallback={
      <div className="border rounded-md h-[250px] bg-muted/40 animate-pulse" aria-hidden="true" />
    }>
      <AceEditor {...props} />
    </Suspense>
  )
}
