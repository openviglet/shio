import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getWidget } from "@/components/widgets"
import type { ShPostAttr } from "@/models/post/post"
import { useMemo } from "react"

interface Props {
  attrs: ShPostAttr[]
  onAttrChange: (index: number, updatedAttr: ShPostAttr) => void
  postId?: string
  postTypeName?: string
}

interface TabGroup {
  label: string
  attrs: { attr: ShPostAttr; originalIndex: number }[]
}

export function PostFormFields({ attrs, onAttrChange, postId, postTypeName }: Props) {
  const sortedAttrs = useMemo(
    () =>
      [...attrs]
        .map((attr, i) => ({ attr, originalIndex: i }))
        .sort((a, b) => (a.attr.shPostTypeAttr?.ordinal ?? 0) - (b.attr.shPostTypeAttr?.ordinal ?? 0)),
    [attrs]
  )

  // Group attrs into tabs
  const tabs = useMemo(() => {
    const groups: TabGroup[] = []
    let currentGroup: TabGroup | null = null

    for (const item of sortedAttrs) {
      const widgetName = item.attr.shPostTypeAttr?.shWidget?.name ?? ""
      if (widgetName === "Tab") {
        currentGroup = {
          label: item.attr.shPostTypeAttr?.label || "Tab",
          attrs: [],
        }
        groups.push(currentGroup)
      } else {
        if (!currentGroup) {
          currentGroup = {
            label: postTypeName || "General",
            attrs: [],
          }
          groups.push(currentGroup)
        }
        currentGroup.attrs.push(item)
      }
    }

    return groups
  }, [sortedAttrs, attrs])


  const renderField = (item: { attr: ShPostAttr; originalIndex: number }) => {
    const { attr, originalIndex } = item
    const widgetName = attr.shPostTypeAttr?.shWidget?.name ?? "Text"
    const isHidden = widgetName === "Hidden"
    const Widget = getWidget(widgetName)

    if (isHidden) {
      return <Widget key={attr.id || originalIndex} attr={attr} onChange={(a) => onAttrChange(originalIndex, a)} />
    }

    return (
      <div key={attr.id || originalIndex} className="space-y-2">
        <Label className="flex items-center gap-2">
          {attr.shPostTypeAttr?.label || attr.shPostTypeAttr?.name || `Field ${originalIndex + 1}`}
          {attr.shPostTypeAttr?.required === 1 && (
            <span className="text-destructive text-xs">*</span>
          )}
        </Label>
        <Widget attr={attr} onChange={(a) => onAttrChange(originalIndex, a)} postId={postId} />
        {attr.shPostTypeAttr?.description && (
          <p className="text-xs text-muted-foreground">{attr.shPostTypeAttr.description}</p>
        )}
      </div>
    )
  }

  // No tabs: just render fields directly
  if (tabs.length <= 1) {
    return <div className="space-y-4">{tabs[0]?.attrs.map(renderField)}</div>
  }

  // Multiple tabs: use shadcn Tabs
  return (
    <Tabs defaultValue={tabs[0]?.label} className="w-full">
      <TabsList>
        {tabs.map((tab) => (
          <TabsTrigger key={tab.label} value={tab.label}>
            {tab.label}
          </TabsTrigger>
        ))}
      </TabsList>
      {tabs.map((tab) => (
        <TabsContent key={tab.label} value={tab.label} className="space-y-4 pt-2">
          {tab.attrs.map(renderField)}
        </TabsContent>
      ))}
    </Tabs>
  )
}
