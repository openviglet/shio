import { Badge } from "@/components/ui/badge"
import { useTranslation } from "react-i18next"
import type { ShPostAttr } from "@/models/post/post"

interface Props {
  attr: ShPostAttr
  onChange: (attr: ShPostAttr) => void
  postId?: string
}

/**
 * Read-only relator view for the unified model. Nested relator items now
 * live inside {@code ShPostUnified.data} as nested JSON arrays; inline
 * editing of relator subtrees is not yet wired through the unified form.
 * For now the widget lists the items with their metadata so the editor
 * still surfaces them, but modifying children is intentionally disabled.
 */
export function RelatorWidget({ attr }: Props) {
  const { t } = useTranslation()
  const items = attr.shChildrenRelatorItems ?? []

  if (items.length === 0) {
    return (
      <p className="text-xs text-muted-foreground">
        {t("content.noRelatorItems", "No relator items.")}
      </p>
    )
  }

  return (
    <div className="space-y-2">
      {items.map((item, index) => (
        <div key={item.id || index} className="rounded-md border bg-muted/30 px-3 py-2 flex items-center gap-2">
          <Badge variant="secondary" className="h-5 px-1.5 text-[10px] shrink-0">#{item.ordinal ?? index}</Badge>
          <div className="flex-1 min-w-0">
            <div className="truncate text-sm font-medium">{item.title || item.id}</div>
            {item.summary && <div className="truncate text-xs text-muted-foreground">{item.summary}</div>}
          </div>
        </div>
      ))}
      <p className="text-[10px] text-muted-foreground">
        Relator editing is read-only in the unified form for now.
      </p>
    </div>
  )
}
