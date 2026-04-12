import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getWidget } from "@/components/widgets"
import { getWidgetAccentColor } from "@/lib/widget-colors"
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

    const accentColor = getWidgetAccentColor(widgetName)
    const isRequired = attr.shPostTypeAttr?.required === 1
    const isTitle = attr.shPostTypeAttr?.isTitle === 1

    return (
      <div
        key={attr.id || originalIndex}
        className={`rounded-lg border border-l-4 ${accentColor} bg-background p-4 space-y-3 transition-shadow hover:shadow-sm`}
      >
        {/* Field header */}
        <div className="flex items-center gap-2 flex-wrap">
          <Label className="text-sm font-semibold flex items-center gap-1.5">
            {attr.shPostTypeAttr?.label || attr.shPostTypeAttr?.name || `Field ${originalIndex + 1}`}
            {isRequired && (
              <span className="text-destructive">*</span>
            )}
          </Label>
          <div className="flex items-center gap-1.5 ml-auto">
            {isTitle && (
              <BadgeColorful text="Title" className="text-[10px] px-1.5 py-0 h-5" />
            )}
            {attr.shPostTypeAttr?.isSummary === 1 && (
              <BadgeColorful text="Summary" className="text-[10px] px-1.5 py-0 h-5" />
            )}
            <BadgeColorful text={widgetName} className="text-[10px] px-1.5 py-0 h-5 font-medium" />
          </div>
        </div>

        {/* Widget */}
        <Widget attr={attr} onChange={(a) => onAttrChange(originalIndex, a)} postId={postId} />

        {/* Description */}
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
      <TabsList className="mb-2">
        {tabs.map((tab) => (
          <TabsTrigger key={tab.label} value={tab.label}>
            {tab.label}
            <Badge variant="secondary" className="ml-1.5 text-[10px] px-1.5 py-0 h-4">
              {tab.attrs.length}
            </Badge>
          </TabsTrigger>
        ))}
      </TabsList>
      {tabs.map((tab) => (
        <TabsContent key={tab.label} value={tab.label} className="space-y-4 pt-1">
          {tab.attrs.map(renderField)}
        </TabsContent>
      ))}
    </Tabs>
  )
}
