import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Plus, Trash2, GripVertical } from "lucide-react"
import type { WidgetProps } from "./index"
import type { ShRelatorItem } from "@/models/post/post"

export function RelatorWidget({ attr, onChange }: WidgetProps) {
  const items = attr.shChildrenRelatorItems || []

  const removeItem = (index: number) => {
    const next = items.filter((_, i) => i !== index)
    onChange({ ...attr, shChildrenRelatorItems: next })
  }

  const addItem = () => {
    const newItem: ShRelatorItem = {
      id: crypto.randomUUID(),
      ordinal: items.length,
      shChildrenPostAttrs: [],
    }
    onChange({ ...attr, shChildrenRelatorItems: [...items, newItem] })
  }

  return (
    <div className="space-y-2">
      {items.map((item, index) => (
        <Card key={item.id || index}>
          <CardContent className="flex items-center gap-2 py-2 px-3">
            <GripVertical className="h-4 w-4 text-muted-foreground shrink-0 cursor-grab" />
            <Badge variant="outline" className="shrink-0">{index + 1}</Badge>
            <span className="flex-1 text-sm truncate">Item {index + 1}</span>
            <Button type="button" variant="ghost" size="icon" className="h-7 w-7 shrink-0 text-destructive" onClick={() => removeItem(index)}>
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </CardContent>
        </Card>
      ))}
      <Button type="button" variant="outline" size="sm" onClick={addItem}>
        <Plus className="h-4 w-4 mr-2" /> Add Item
      </Button>
    </div>
  )
}
