import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { X } from "lucide-react"
import { useState } from "react"
import type { WidgetProps } from "./index"

export function MultiSelectWidget({ attr, onChange }: WidgetProps) {
  const [inputValue, setInputValue] = useState("")
  const values = attr.arrayValue || []

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
            <button type="button" onClick={() => removeValue(val)} className="hover:text-destructive">
              <X className="h-3 w-3" />
            </button>
          </Badge>
        ))}
      </div>
      <div className="flex gap-2">
        <Input
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addValue() } }}
          placeholder="Add value and press Enter"
          className="text-sm"
        />
      </div>
    </div>
  )
}
