/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { BentoDiff, type BentoDiffField } from "@viglet/viglet-design-system/bento"
import { CreatedPagePanel } from "@/components/created-page-panel"
import { reportProblem } from "@/lib/problem"
import { isCancelled } from "@/lib/read-options"
import { DRAFT, PUBLISHED, diffFields } from "@/lib/version-diff"
import { postFormSchema, type ShPostDiff } from "@/models/post-unified/post-unified"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { Spinner } from "@/components/spinner"

const service = new ShPostUnifiedService()

/**
 * SH988 — one review entry's change, inline under its row.
 *
 * <p>Inline rather than a modal: a long agent run is a list of entries, and a modal per entry is a
 * click and a close per entry. The comparison is the server's, published against draft, so a page
 * the run created, which has no published side, draws whole as additions instead of offering no
 * diff at all, the gap this replaces.</p>
 */
export function ReviewEntryDiff({ postId }: { postId: string }) {
  const { t } = useTranslation()
  const [answer, setAnswer] = useState<{ diff: ShPostDiff; fields: BentoDiffField[] } | null>(null)

  useEffect(() => {
    const abort = new AbortController()
    Promise.all([
      service.diff(postId, PUBLISHED, DRAFT, { signal: abort.signal }),
      service.get(postId, "DRAFT").catch(() => service.get(postId, "PUBLISHED")),
    ])
      .then(([diff, post]) => setAnswer({ diff, fields: diffFields(diff, postFormSchema(post.shPostType)) }))
      .catch((error: unknown) => {
        if (!isCancelled(error)) reportProblem(error, t("common.error"))
      })
    return () => abort.abort()
  }, [postId, t])

  if (!answer) {
    return (
      <div className="flex items-center gap-2 py-4 text-sm text-muted-foreground">
        <Spinner labelled className="size-4" /> {t("common.loading")}
      </div>
    )
  }
  return (
    <div className="space-y-3">
      {!answer.diff.before && <CreatedPagePanel postId={postId} />}
      <BentoDiff before={answer.diff.before} after={answer.diff.after} fields={answer.fields} />
    </div>
  )
}
