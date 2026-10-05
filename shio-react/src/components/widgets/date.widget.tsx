import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"
import { X } from "lucide-react"
import { useTranslation } from "react-i18next"
import type { WidgetProps } from "./index"

export function DateWidget({ attr, onChange, fieldId, describedBy, required, invalid }: WidgetProps) {
  // SH893 — the clear button is an icon and nothing else, so it needs a name a screen reader can
  // announce. This was the only one of the fifteen widgets with no i18n in it at all.
  const { t } = useTranslation()
  const dateStr = attr.dateValue ? new Date(attr.dateValue).toISOString().split("T")[0] : ""

  return (
    <div className="flex items-center gap-2">
      <Input
        id={fieldId}
        aria-describedby={describedBy}
        aria-required={required ? "true" : "false"}
        aria-invalid={invalid ? "true" : "false"}
        type="date"
        value={dateStr}
        onChange={(e) => onChange({ ...attr, dateValue: e.target.value ? new Date(e.target.value).toISOString() : "" })}
        className="w-auto"
      />
      {dateStr && (
        <Button type="button" variant="ghost" size="icon-sm" aria-label={t("common.clear")} onClick={() => onChange({ ...attr, dateValue: "" })}>
          <X className="h-4 w-4" />
        </Button>
      )}
    </div>
  )
}
