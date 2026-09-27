import { useRef, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { Bold, Italic, Underline, List, ListOrdered, Link, Code } from "lucide-react"
import type { WidgetProps } from "./index"

export function HtmlEditorWidget({ attr, onChange, fieldId, labelledBy, describedBy, required, invalid }: WidgetProps) {
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
      {/*
        SH1052 — `role="textbox"` and the id, because this IS the control: it takes focus, it takes
        typing, and a label pointing at the wrapper would focus nothing.

        The focus ring is back. `focus:outline-none` with nothing in its place is the browser's own
        "you are here" removed and not replaced, which for a keyboard reader is the difference
        between an editor they can find and one they cannot.
      */}
      <div
        ref={editorRef}
        id={fieldId}
        role="textbox"
        aria-multiline="true"
        // SH1054 — `<label for>` names only a real form element, so the label pointing at this div
        // named nothing: axe found the editor unnamed on every post with an HTML field.
        aria-labelledby={labelledBy}
        aria-describedby={describedBy}
        aria-required={required ? "true" : "false"}
        aria-invalid={invalid ? "true" : "false"}
        tabIndex={0}
        contentEditable
        suppressContentEditableWarning
        className="min-h-[200px] p-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 prose prose-sm dark:prose-invert max-w-none"
        onInput={handleInput}
        onBlur={handleInput}
      />
    </div>
  )
}
