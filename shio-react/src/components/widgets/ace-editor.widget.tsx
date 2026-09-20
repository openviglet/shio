import CodeMirror from "@uiw/react-codemirror"
import { javascript } from "@codemirror/lang-javascript"
import { html } from "@codemirror/lang-html"
import { githubLight, githubDark } from "@uiw/codemirror-theme-github"
import { useTheme } from "@/components/theme-provider"
import type { WidgetProps } from "./index"

export function AceEditorWidget({ attr, onChange, labelledBy, describedBy, required }: WidgetProps) {
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
      aria-required={required ? "true" : "false"}
      className="border rounded-md overflow-hidden"
    >
      <CodeMirror
        value={attr.strValue ?? ""}
        onChange={(value) => onChange({ ...attr, strValue: value })}
        height="250px"
        theme={isDark ? githubDark : githubLight}
        extensions={[isHtml ? html() : javascript()]}
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
