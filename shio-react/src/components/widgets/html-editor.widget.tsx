import { useRef, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { Bold, Italic, Underline, List, ListOrdered, Link, Code } from "lucide-react"
import type { WidgetProps } from "./index"

export function HtmlEditorWidget({ attr, onChange }: WidgetProps) {
  const { t } = useTranslation()
  const editorRef = useRef<HTMLDivElement>(null)
  const isInitialized = useRef(false)

  useEffect(() => {
    if (editorRef.current && !isInitialized.current) {
      editorRef.current.innerHTML = attr.strValue ?? ""
      isInitialized.current = true
    }
  }, [attr.strValue])

  const execCommand = (command: string, value?: string) => {
    document.execCommand(command, false, value)
    editorRef.current?.focus()
    handleInput()
  }

  const handleInput = () => {
    if (editorRef.current) {
      onChange({ ...attr, strValue: editorRef.current.innerHTML })
    }
  }

  return (
    <div className="border rounded-md overflow-hidden">
      <div className="flex items-center gap-0.5 p-1 border-b bg-muted/30 flex-wrap">
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => execCommand("bold")} title={t("content.editorBold")}>
          <Bold className="h-3.5 w-3.5" />
        </Button>
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => execCommand("italic")} title={t("content.editorItalic")}>
          <Italic className="h-3.5 w-3.5" />
        </Button>
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => execCommand("underline")} title={t("content.editorUnderline")}>
          <Underline className="h-3.5 w-3.5" />
        </Button>
        <div className="w-px h-5 bg-border mx-1" />
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => execCommand("insertUnorderedList")} title={t("content.editorBulletList")}>
          <List className="h-3.5 w-3.5" />
        </Button>
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => execCommand("insertOrderedList")} title={t("content.editorNumberedList")}>
          <ListOrdered className="h-3.5 w-3.5" />
        </Button>
        <div className="w-px h-5 bg-border mx-1" />
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => { const url = prompt(t("content.editorLinkPrompt")); if (url) execCommand("createLink", url) }} title={t("content.editorLink")}>
          <Link className="h-3.5 w-3.5" />
        </Button>
        <Button type="button" variant="ghost" size="icon" className="h-7 w-7" onClick={() => execCommand("formatBlock", "pre")} title={t("content.editorCodeBlock")}>
          <Code className="h-3.5 w-3.5" />
        </Button>
      </div>
      <div
        ref={editorRef}
        contentEditable
        suppressContentEditableWarning
        className="min-h-[200px] p-3 text-sm focus:outline-none prose prose-sm dark:prose-invert max-w-none"
        onInput={handleInput}
        onBlur={handleInput}
      />
    </div>
  )
}
