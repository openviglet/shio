import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { X, FileIcon } from "lucide-react"
import type { WidgetProps } from "./index"

export function FileWidget({ attr, onChange }: WidgetProps) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2">
        <Input
          value={attr.strValue ?? ""}
          onChange={(e) => onChange({ ...attr, strValue: e.target.value })}
          placeholder="File ID"
          className="font-mono text-sm"
        />
        {attr.strValue && (
          <Button type="button" variant="ghost" size="icon" className="h-8 w-8 shrink-0" onClick={() => onChange({ ...attr, strValue: "" })}>
            <X className="h-4 w-4" />
          </Button>
        )}
      </div>
      <p className="text-xs text-muted-foreground flex items-center gap-1">
        <FileIcon className="h-3 w-3" />
        {attr.strValue ? `File: ${attr.strValue}` : "No file selected"}
      </p>
    </div>
  )
}
