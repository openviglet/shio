// SH1199 — GraphiQL's editors in the console's one monospace family.
//
// GraphiQL v5 creates its Monaco editors with `fontFamily: '"Fira Code"'` and no
// prop to change it, so the explorer was the one route rendering a second mono
// (the look census counted 122 Fira Code elements there and nowhere else). The
// family is the design system's `--vg-font-mono` (VDS199), read from the root so
// this file names no font of its own. GraphiQL loads Monaco lazily into its own
// store, so the override waits for it and then applies to every editor, the ones
// already open and the ones created after.
//
// The chrome around the editors reads GraphiQL's `--font-family` variables, which
// index.css points at the same tokens.
import { useMonaco } from "@graphiql/react"
import { useEffect } from "react"

export function useConsoleMonoInGraphiQL() {
  const monaco = useMonaco((state) => state.monaco)
  useEffect(() => {
    if (!monaco) return
    const fontFamily = getComputedStyle(document.documentElement).getPropertyValue("--vg-font-mono").trim()
    if (!fontFamily) return
    const apply = (editor: { updateOptions(options: { fontFamily: string }): void }) => editor.updateOptions({ fontFamily })
    monaco.editor.getEditors().forEach(apply)
    const created = monaco.editor.onDidCreateEditor(apply)
    return () => created.dispose()
  }, [monaco])
}
