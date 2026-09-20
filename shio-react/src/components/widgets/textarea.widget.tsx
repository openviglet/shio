import { Textarea } from "@/components/ui/textarea"
import type { WidgetProps } from "./index"

export function TextAreaWidget({ attr, onChange, fieldId, describedBy, required, invalid }: WidgetProps) {
  return (
    <Textarea
      id={fieldId}
      aria-describedby={describedBy}
      aria-required={required ? "true" : "false"}
      aria-invalid={invalid ? "true" : "false"}
      value={attr.strValue ?? ""}
      onChange={(e) => onChange({ ...attr, strValue: e.target.value })}
      rows={6}
      placeholder={attr.shPostTypeAttr?.label || ""}
    />
  )
}
