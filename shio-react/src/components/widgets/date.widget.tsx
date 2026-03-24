import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { X } from "lucide-react"
import type { WidgetProps } from "./index"

export function DateWidget({ attr, onChange }: WidgetProps) {
  const dateStr = attr.dateValue ? new Date(attr.dateValue).toISOString().split("T")[0] : ""

  return (
    <div className="flex items-center gap-2">
      <Input
        type="date"
        value={dateStr}
        onChange={(e) => onChange({ ...attr, dateValue: e.target.value ? new Date(e.target.value).toISOString() : "" })}
        className="w-auto"
      />
      {dateStr && (
        <Button type="button" variant="ghost" size="icon" className="h-8 w-8" onClick={() => onChange({ ...attr, dateValue: "" })}>
          <X className="h-4 w-4" />
        </Button>
      )}
    </div>
  )
}
