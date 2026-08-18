import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import type { WidgetProps } from "./index"

interface Choice { id: string; label: string }

function parseChoices(shPostTypeAttr?: WidgetProps["attr"]["shPostTypeAttr"]): Choice[] {
  if (!shPostTypeAttr) return []
  try {
    const choicesStr = (shPostTypeAttr as Record<string, unknown>)["widgetSettings"] as string | undefined
    if (!choicesStr) return []
    const settings = JSON.parse(choicesStr)
    const raw = settings.choices || ""
    return raw.split("\n").filter(Boolean).map((line: string) => {
      const [id, ...rest] = line.split(":")
      return { id: id.trim(), label: rest.join(":").trim() || id.trim() }
    })
  } catch { return [] }
}

export function CheckBoxWidget({ attr, onChange }: WidgetProps) {
  const { t } = useTranslation()
  const choices = useMemo(() => parseChoices(attr.shPostTypeAttr), [attr.shPostTypeAttr])
  const selected = new Set(attr.arrayValue || [])

  const handleToggle = (id: string, checked: boolean) => {
    const next = new Set(selected)
    if (checked) next.add(id)
    else next.delete(id)
    onChange({ ...attr, arrayValue: [...next] })
  }

  return (
    <div className="space-y-2">
      {choices.map(choice => (
        <div key={choice.id} className="flex items-center gap-2">
          <Checkbox
            id={`cb-${attr.id}-${choice.id}`}
            checked={selected.has(choice.id)}
            onCheckedChange={(checked) => handleToggle(choice.id, !!checked)}
          />
          <Label htmlFor={`cb-${attr.id}-${choice.id}`} className="text-sm font-normal cursor-pointer">
            {choice.label}
          </Label>
        </div>
      ))}
      {choices.length === 0 && <p className="text-xs text-muted-foreground">{t("content.noChoicesConfigured")}</p>}
    </div>
  )
}
