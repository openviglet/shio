import CodeMirror from "@uiw/react-codemirror"
import { javascript } from "@codemirror/lang-javascript"
import { html } from "@codemirror/lang-html"
import { githubLight, githubDark } from "@uiw/codemirror-theme-github"
import { useTheme } from "@/components/theme-provider"
import type { WidgetProps } from "./index"

export function AceEditorWidget({ attr, onChange }: WidgetProps) {
  const isHtml = attr.shPostTypeAttr?.shWidget?.name?.includes("HTML")
  const { theme } = useTheme()
  const isDark = theme === "dark" || (theme === "system" && globalThis.matchMedia?.("(prefers-color-scheme: dark)").matches)

  return (
    <div className="border rounded-md overflow-hidden">
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
