/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useMemo, useState } from "react"
import type { PostConflictField } from "@/lib/post-conflict"
import { useTranslation } from "react-i18next"

import { GradientButton } from "@viglet/viglet-design-system"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

/**
 * SH985 — what a refused save shows the curator.
 *
 * <h2>Why a view rather than a message</h2>
 *
 * The server can say *somebody wrote to this after you read it* and nothing more: it knows the two
 * digests and not which of them the curator wants. A toast carrying that sentence leaves a person
 * holding a form they cannot safely save and no way to find out what they would be discarding —
 * which is how a guard turns into an obstacle and then into a header somebody stops sending.
 *
 * So the refusal opens the comparison. Each field that differs is one row: what is stored now, and
 * what is in the form. The default is **what is stored**, deliberately — the guard exists to stop a
 * save silently discarding the other writer, so the safe answer must not be the discard. Keeping a
 * field is one click and it is a decision the curator made.
 *
 * Fields the two agree on are not shown at all. A conflict over one field is one row, and a
 * conflict view that lists forty unchanged fields around it is the same as no view.
 */
export interface PostConflictDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** The fields that differ, already resolved to readable strings. */
  fields: readonly PostConflictField[]
  /** What the server said, so the curator reads the refusal in the server's own words (P6). */
  detail?: string
  /** The names the curator chose to keep from the form; everything else takes the stored value. */
  onResolve: (keepMine: readonly string[]) => void
  saving?: boolean
}

export function PostConflictDialog({
  open,
  onOpenChange,
  ...rest
}: Readonly<PostConflictDialogProps>) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <PostConflictBody onOpenChange={onOpenChange} {...rest} />
      </DialogContent>
    </Dialog>
  )
}

/**
 * The comparison itself, mounted inside `DialogContent` so closing the dialog unmounts it (SH702's
 * shape). Every time the dialog opens it opens on the stored values, including after a second
 * refusal. SH1136 — that used to be an effect resetting the choice when `open` became true, which ran
 * after the paint that showed the previous choice; a body that did not exist a moment ago has none.
 */
function PostConflictBody({
  onOpenChange,
  fields,
  detail,
  onResolve,
  saving = false,
}: Readonly<Omit<PostConflictDialogProps, "open">>) {
  const { t } = useTranslation()
  const [keepMine, setKeepMine] = useState<readonly string[]>([])

  const rows = useMemo(() => [...fields], [fields])

  const toggle = (name: string) =>
    setKeepMine((chosen) =>
      chosen.includes(name) ? chosen.filter((each) => each !== name) : [...chosen, name]
    )

  return (
    <>
      <DialogHeader>
        <DialogTitle>{t("content.conflictTitle")}</DialogTitle>
        <DialogDescription>{detail ?? t("content.conflictHelp")}</DialogDescription>
      </DialogHeader>

      <div className="max-h-[50vh] space-y-4 overflow-y-auto">
        {rows.length === 0 && (
          <p className="text-sm text-muted-foreground">{t("content.conflictNoFields")}</p>
        )}
        {rows.map((field) => {
          const mineChosen = keepMine.includes(field.name)
          return (
            <div key={field.name} className="rounded-md border p-3">
              <p className="mb-2 text-sm font-medium">{field.label}</p>
              <div className="grid gap-2 sm:grid-cols-2">
                <button
                  type="button"
                  onClick={() => mineChosen && toggle(field.name)}
                  aria-pressed={mineChosen ? "false" : "true"}
                  className={`rounded-sm border p-2 text-left text-sm ${
                    mineChosen ? "opacity-60" : "border-primary"
                  }`}
                >
                  <span className="block text-xs uppercase">{t("content.conflictTheirs")}</span>
                  <span className="break-words">{field.theirs || t("content.conflictEmpty")}</span>
                </button>
                <button
                  type="button"
                  onClick={() => !mineChosen && toggle(field.name)}
                  aria-pressed={mineChosen ? "true" : "false"}
                  className={`rounded-sm border p-2 text-left text-sm ${
                    mineChosen ? "border-primary" : "opacity-60"
                  }`}
                >
                  <span className="block text-xs uppercase">{t("content.conflictMine")}</span>
                  <span className="break-words">{field.mine || t("content.conflictEmpty")}</span>
                </button>
              </div>
            </div>
          )
        })}
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
          {t("common.cancel")}
        </Button>
        <GradientButton type="button" loading={saving} onClick={() => onResolve(keepMine)}>
          {t("content.conflictApply")}
        </GradientButton>
      </DialogFooter>
    </>
  )
}
