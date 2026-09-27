/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1021/SH1145 — the three verbs a selection can take, planned before they are carried out.
 *
 * <p>The plan is the server's own dry run through the same writer the agent's batch uses, so what
 * the sheet names is exactly what the real run will do — not a second opinion computed here. A
 * selection where one post is refused publishes the rest and says which one did not, with the fix
 * its refusal carries.</p>
 *
 * <p>SH1145 — the plan, the run and the two pieces of state between them are one unit; on the page
 * they sat between the row actions and the navigation helpers with nothing marking where one
 * stopped.</p>
 */
import { useState } from "react"
import { toast } from "@viglet/viglet-design-system"

import { headlineOf, problemOf } from "@/lib/problem"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"

import type { BulkPlan } from "./bulk-plan.dialog"
import type { BulkOp } from "./browser-listing"

const unifiedService = new ShPostUnifiedService()

export interface BulkVerbs {
  selectedItems: Set<string>
  clearSelection: () => void
  /** Re-read the page the curator is on, once the run has landed. */
  refreshList: () => Promise<void>
  t: (key: string, options?: Record<string, unknown>) => string
}

export function useBulkVerbs({ selectedItems, clearSelection, refreshList, t }: BulkVerbs) {
  const [bulkPlan, setBulkPlan] = useState<BulkPlan | null>(null)
  const [bulkRunning, setBulkRunning] = useState(false)

  const planBulk = async (op: BulkOp) => {
    if (selectedItems.size === 0) return
    try {
      const result = await unifiedService.bulk(op, [...selectedItems], { dryRun: true })
      setBulkPlan({ op, result })
    } catch (error) {
      toast.error(headlineOf(problemOf(error), t("common.error")))
    }
  }

  const applyBulk = async () => {
    if (!bulkPlan) return
    setBulkRunning(true)
    try {
      // The confirm token is the one this plan returned, so a destructive run cannot be carried out
      // against a plan the curator never saw (SH108).
      const result = await unifiedService.bulk(bulkPlan.op, [...selectedItems], {
        dryRun: false,
        confirm: bulkPlan.result.confirm,
      })
      const refused = result.results.filter((change) => change.problem)
      toast.success(t("content.bulkDone", { count: result.changed }))
      if (refused.length) {
        // Named rather than counted: "3 refused" sends a curator to find out which three.
        toast.error(t("content.bulkRefused", {
          count: refused.length,
          names: refused.map((change) => change.address ?? change.id).join(", "),
        }))
      }
      setBulkPlan(null)
      clearSelection()
      await refreshList()
    } catch (error) {
      toast.error(headlineOf(problemOf(error), t("common.error")))
    } finally {
      setBulkRunning(false)
    }
  }


  return { bulkPlan, setBulkPlan, bulkRunning, planBulk, applyBulk }
}
