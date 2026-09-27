import CodeMirror, { EditorView } from "@uiw/react-codemirror"
import { javascript } from "@codemirror/lang-javascript"
import { html } from "@codemirror/lang-html"
import { githubLight, githubDark } from "@uiw/codemirror-theme-github"
import { useTheme } from "@/components/theme-provider"
import type { WidgetProps } from "./index"

/**
 * SH1054 — CodeMirror's editable surface is its own `role="textbox"`, inside the group the wrapper
 * names, and axe found it unnamed on every post with a code field. The label and description are
 * put on the surface itself, and an explicit tab stop makes the scroller's content reachable, which
 * is what axe's scrollable-region rule was asking for.
 */
function namedSurface(labelledBy?: string, describedBy?: string) {
  const attributes: Record<string, string> = { tabindex: "0" }
  if (labelledBy) attributes["aria-labelledby"] = labelledBy
  if (describedBy) attributes["aria-describedby"] = describedBy
  return EditorView.contentAttributes.of(attributes)
}

export function AceEditorWidget({ attr, onChange, labelledBy, describedBy }: WidgetProps) {
  const isHtml = attr.shPostTypeAttr?.shWidget?.name?.includes("HTML")
  const { theme } = useTheme()
  const isDark = theme === "dark" || (theme === "system" && globalThis.matchMedia?.("(prefers-color-scheme: dark)").matches)

  return (
    // SH1052 — a group rather than a control: CodeMirror mounts its own focusable surface inside,
    // so the field's `htmlFor` has nothing here to own and the wrapper names itself by the label.
    <div
      role="group"
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      // SH1054 — no aria-required: a group may not carry it, and its name (the label) already says it
      className="border rounded-md overflow-hidden"
    >
      <CodeMirror
        value={attr.strValue ?? ""}
        onChange={(value) => onChange({ ...attr, strValue: value })}
        height="250px"
        theme={isDark ? githubDark : githubLight}
        extensions={[isHtml ? html() : javascript(), namedSurface(labelledBy, describedBy)]}
        basicSetup={{
          lineNumbers: true,
          highlightActiveLine: true,
          foldGutter: true,
          autocompletion: true,
        }}
      />
    </div>
  )
}
