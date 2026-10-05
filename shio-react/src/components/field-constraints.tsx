import { useTranslation } from "react-i18next"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import type { ShFieldConstraints, ShPostTypeField } from "@/models/post-type/post-type"

/**
 * SH1010 — what a field will accept, edited where the field is.
 *
 * <p>The rules used to live in somebody's head: a slug field accepted spaces, a rating accepted
 * eleven, and the only enforcement anywhere was a `verify` run somebody had to remember to make.
 * The model can say it now, and this is where a curator says it.</p>
 *
 * <h2>Absent, never empty</h2>
 *
 * <p>A key that is cleared is deleted rather than written as `null`, and a block with nothing left
 * in it becomes no block at all. The server stores no block when one says nothing, so a form that
 * materialized `{}` on every field would push a change to every field of every type the first time
 * somebody opened it — and a post-type pulled before this would read as drift on the next pull
 * (P5).</p>
 */
export function FieldConstraints<T extends ShPostTypeField>({ field, onFieldChange }: Readonly<{
  field: T
  onFieldChange: (field: T) => void
}>) {
  const { t } = useTranslation()
  const declared: ShFieldConstraints = field.constraints ?? {}

  /** One key, set or cleared — and the whole block dropped when nothing is left. */
  const put = (key: keyof ShFieldConstraints, value: unknown) => {
    const next: ShFieldConstraints = { ...declared }
    if (value === undefined || value === null || value === "" || value === false) {
      delete next[key]
    } else {
      // The cast is the price of one setter for twelve keys; the union is the model's own.
      (next as Record<string, unknown>)[key] = value
    }
    onFieldChange({
      ...field,
      constraints: Object.keys(next).length > 0 ? next : undefined,
    })
  }

  const number = (key: keyof ShFieldConstraints, label: string) => (
    <div className="flex flex-col gap-1">
      <span className="text-xs text-muted-foreground">{label}</span>
      <Input
        type="number"
        className="h-(--vg-control-dense)"
        value={(declared[key] as number | undefined) ?? ""}
        onChange={(event) => put(key, event.target.value === ""
          ? undefined
          : Number(event.target.value))}
      />
    </div>
  )

  return (
    <div className="space-y-3 rounded-md border border-dashed p-3">
      <span className="text-sm font-medium">{t("content.constraints")}</span>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {number("minLength", t("content.constraintMinLength"))}
        {number("maxLength", t("content.constraintMaxLength"))}
        {number("min", t("content.constraintMin"))}
        {number("max", t("content.constraintMax"))}
        {number("minItems", t("content.constraintMinItems"))}
        {number("maxItems", t("content.constraintMaxItems"))}
        {number("recommendedLength", t("content.constraintRecommendedLength"))}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <span className="text-xs text-muted-foreground">{t("content.constraintPattern")}</span>
          <Input
            className="h-(--vg-control-dense)"
            value={declared.pattern ?? ""}
            onChange={(event) => put("pattern", event.target.value)}
          />
        </div>
        <div className="flex flex-col gap-1">
          {/*
            SH1010 — beside the pattern, not below it: a regular expression quoted back at whoever
            is filling the form is a refusal only its author can act on (P6), and the way to make
            that unlikely is for the two boxes to be one thing on screen.
          */}
          <span className="text-xs text-muted-foreground">
            {t("content.constraintPatternMessage")}
          </span>
          <Input
            className="h-(--vg-control-dense)"
            value={declared.patternMessage ?? ""}
            onChange={(event) => put("patternMessage", event.target.value)}
          />
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <span className="text-xs text-muted-foreground">{t("content.constraintValues")}</span>
          <Input
            className="h-(--vg-control-dense)"
            value={(declared.values ?? []).join(", ")}
            onChange={(event) => put("values", event.target.value
              .split(",")
              .map((value) => value.trim())
              .filter(Boolean))}
          />
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-xs text-muted-foreground">
            {t("content.constraintDefaultValue")}
          </span>
          <Input
            className="h-(--vg-control-dense)"
            value={declared.defaultValue ?? ""}
            onChange={(event) => put("defaultValue", event.target.value)}
          />
        </div>
      </div>

      <div className="flex items-center gap-2.5">
        <Switch
          checked={Boolean(declared.unique)}
          onCheckedChange={(checked) => put("unique", checked)}
        />
        <span className="text-sm">{t("content.constraintUnique")}</span>
      </div>
    </div>
  )
}
