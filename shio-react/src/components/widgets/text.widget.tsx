import { Input } from "@/components/ui/input"
import type { WidgetProps } from "./index"

export function TextWidget({ attr, onChange }: WidgetProps) {
  return (
    <Input
      value={attr.strValue ?? ""}
      onChange={(e) => onChange({ ...attr, strValue: e.target.value })}
      placeholder={attr.shPostTypeAttr?.label || ""}
    />
  )
}
