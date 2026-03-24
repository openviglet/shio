import { Separator } from "@/components/ui/separator"
import type { WidgetProps } from "./index"

export function TabWidget({ attr }: WidgetProps) {
  return (
    <div className="pt-4">
      <Separator />
      <h3 className="text-sm font-semibold mt-4">{attr.shPostTypeAttr?.label || "Tab"}</h3>
    </div>
  )
}
