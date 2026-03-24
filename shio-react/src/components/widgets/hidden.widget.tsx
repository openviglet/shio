import type { WidgetProps } from "./index"

export function HiddenWidget({ attr }: WidgetProps) {
  return <input type="hidden" value={attr.strValue ?? ""} />
}
