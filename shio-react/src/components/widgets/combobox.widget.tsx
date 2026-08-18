import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import type { WidgetProps } from "./index"

interface Choice { id: string; label: string }

function parseChoices(shPostTypeAttr?: WidgetProps["attr"]["shPostTypeAttr"]): { choices: Choice[]; allowNull: boolean } {
  if (!shPostTypeAttr) return { choices: [], allowNull: true }
  try {
    const choicesStr = (shPostTypeAttr as Record<string, unknown>)["widgetSettings"] as string | undefined
    if (!choicesStr) return { choices: [], allowNull: true }
    const settings = JSON.parse(choicesStr)
    const raw = settings.choices || ""
    const choices = raw.split("\n").filter(Boolean).map((line: string) => {
      const [id, ...rest] = line.split(":")
      return { id: id.trim(), label: rest.join(":").trim() || id.trim() }
    })
    return { choices, allowNull: settings.allowNull !== 0 }
  } catch { return { choices: [], allowNull: true } }
}

export function ComboBoxWidget({ attr, onChange }: WidgetProps) {
  const { t } = useTranslation()
  const { choices, allowNull } = useMemo(() => parseChoices(attr.shPostTypeAttr), [attr.shPostTypeAttr])

  return (
    <Select
      value={attr.strValue ?? ""}
      onValueChange={(value) => onChange({ ...attr, strValue: value === "__null__" ? "" : value })}
    >
      <SelectTrigger><SelectValue placeholder={t("content.selectPlaceholder")} /></SelectTrigger>
      <SelectContent>
        {allowNull && <SelectItem value="__null__">-- None --</SelectItem>}
        {choices.map(choice => (
          <SelectItem key={choice.id} value={choice.id}>{choice.label}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
