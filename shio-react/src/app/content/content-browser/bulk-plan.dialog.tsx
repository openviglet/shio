/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1021/SH1145 — a bulk verb's plan, as a sheet naming what will change.
 *
 * <p>It is the SERVER's dry run through the same writer the real run uses, so what this names is
 * what will happen — not a second opinion computed in the browser. Refusals are grouped and each
 * carries the fix its problem document came with, because "3 refused" sends a curator to find out
 * which three.</p>
 *
 * <p>SH1145 — a component because it is only ever rendered from one plan and reads nothing else.
 * `plan` is non-null here rather than nullable: the caller mounts it when there is one, which
 * removes four `?.` and the reading of "changed: 0" as "nothing will change".</p>
 */
import { BentoChangeMark } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { changeStateOf } from "@/lib/change-mark"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import type { ShBulkResult } from "@/services/post-unified/post-unified.service"

import type { BulkOp } from "./browser-listing"

export interface BulkPlan {
  op: BulkOp
  result: ShBulkResult
}

export interface BulkPlanProps {
  plan: BulkPlan
  /** True while the real run is in flight; the confirm button says so and refuses a second click. */
  running: boolean
  t: (key: string, options?: Record<string, unknown>) => string
  onCancel: () => void
  onApply: () => void
}

export function BulkPlanDialog({ plan, running, t, onCancel, onApply }: BulkPlanProps) {
  return (
    <Dialog open onOpenChange={(open) => !open && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {t("content.bulkPlanTitle", {
              op: t(`content.bulkOp.${plan.op}`),
              count: plan.result.changed,
            })}
          </DialogTitle>
        </DialogHeader>
        <div className="space-y-2 max-h-80 overflow-auto text-sm">
          {plan.result.results.map((change) => (
            <div key={`${change.op}-${change.address ?? change.id}`}
              className="flex items-start gap-2">
              {/* SH1178 — a refusal is not a change, so it keeps its badge; a change is marked in the
                  words and tints the diff uses (VDS170), and a "changed" row names what changed. */}
              {change.problem ? (
                <Badge variant="destructive" className="text-[10px] shrink-0">
                  {t("content.bulkRefusedBadge")}
                </Badge>
              ) : (
                <span className="flex shrink-0 items-center gap-1.5">
                  <BentoChangeMark state={changeStateOf(change.action)} />
                  {changeStateOf(change.action) === "changed" && (
                    <span className="text-xs text-muted-foreground">
                      {t(`content.changeAction.${change.action}`, { defaultValue: change.action })}
                    </span>
                  )}
                </span>
              )}
              <div className="min-w-0">
                <div className="truncate">{change.address ?? change.id}</div>
                {change.problem && (
                  <p className="text-xs text-muted-foreground">
                    {change.problem.title}{change.problem.fix ? ` — ${change.problem.fix}` : ""}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onCancel}>{t("common.cancel")}</Button>
          <Button onClick={onApply} disabled={running}>
            {running ? t("common.loading") : t("content.bulkApply")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>

  )
}
