import { Input } from "@/components/ui/input"
import type { WidgetProps } from "./index"

export function TextWidget({ attr, onChange, fieldId, describedBy, required, invalid }: WidgetProps) {
  return (
    <Input
      id={fieldId}
      aria-describedby={describedBy}
      aria-required={required ? "true" : "false"}
      aria-invalid={invalid ? "true" : "false"}
      value={attr.strValue ?? ""}
      onChange={(e) => onChange({ ...attr, strValue: e.target.value })}
      placeholder={attr.shPostTypeAttr?.label || ""}
    />
  )
}
