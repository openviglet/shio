/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { toast } from "@viglet/viglet-design-system"
import { ROUTES } from "@/app/routes.const"
import { reportProblem } from "@/lib/problem"
import { ShAgentFeedService } from "@/services/agent-feed/agent-feed.service"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"

const feedService = new ShAgentFeedService()
const postService = new ShPostUnifiedService()

type Say = (key: string, options?: Record<string, unknown>) => string

/** SH970 — "open" on an arrival report: the address the feed gave, resolved on the click. */
export function openSubmission(address: string, navigate: (to: string) => void, say: Say) {
  feedService.locate(address)
    .then((resolved) => navigate(`${ROUTES.CONTENT_ROOT}/post/${resolved.id}`))
    .catch((error: unknown) => reportProblem(error, say("common.error")))
}

/**
 * SH970 — "move to trash" on a single arrival. A write the curator pressed, so it reports both
 * ways (SH965); the trash is where it can be restored from.
 */
export function trashSubmission(address: string, say: Say) {
  feedService.locate(address)
    .then((resolved) => postService.delete(resolved.id))
    .then(() => toast.success(say("dock.trashed")))
    .catch((error: unknown) => reportProblem(error, say("common.error")))
}
