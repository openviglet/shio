import { Textarea } from "@/components/ui/textarea"
import type { WidgetProps } from "./index"

export function TextAreaWidget({ attr, onChange }: WidgetProps) {
  return (
    <Textarea
      value={attr.strValue ?? ""}
      onChange={(e) => onChange({ ...attr, strValue: e.target.value })}
      rows={6}
      placeholder={attr.shPostTypeAttr?.label || ""}
    />
  )
}
