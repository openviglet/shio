import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { X } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import type { WidgetProps } from "./index"
import { SectionList } from "./section-list"

export function MultiSelectWidget({ attr, onChange, postId, fieldId, describedBy, required, invalid }: WidgetProps) {
  const { t } = useTranslation()
  const [inputValue, setInputValue] = useState("")
  const values = attr.arrayValue || []

  // SH1205 — a page's Sections are friendly URLs in render order; drawn by name, in order.
  if (attr.shPostTypeAttr?.name === "sections" && postId) {
    return (
      <SectionList postId={postId} values={values} fieldId={fieldId} describedBy={describedBy}
        onChange={(next) => onChange({ ...attr, arrayValue: next })} />
    )
  }

  const addValue = () => {
    const trimmed = inputValue.trim()
    if (trimmed && !values.includes(trimmed)) {
      onChange({ ...attr, arrayValue: [...values, trimmed] })
      setInputValue("")
    }
  }

  const removeValue = (val: string) => {
    onChange({ ...attr, arrayValue: values.filter(v => v !== val) })
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1">
        {values.map(val => (
          <Badge key={val} variant="secondary" className="gap-1 pr-1">
            {val}
            <button
              type="button"
              onClick={() => removeValue(val)}
              aria-label={t("content.removeValue", { value: val })}
              className="hover:text-destructive"
            >
              <X aria-hidden="true" className="h-3 w-3" />
            </button>
          </Badge>
        ))}
      </div>
      <div className="flex gap-2">
        <Input
          id={fieldId}
          aria-describedby={describedBy}
          aria-required={required ? "true" : "false"}
          aria-invalid={invalid ? "true" : "false"}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addValue() } }}
          placeholder={t("content.addValueAndPressEnter")}
          className="text-sm"
        />
      </div>
    </div>
  )
}
