/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { toast } from "@viglet/viglet-design-system"
import { reportProblem } from "@/lib/problem"
import { ShAgentFeedService } from "@/services/agent-feed/agent-feed.service"

const feedService = new ShAgentFeedService()

/**
 * SH969 — "retry now" on a failed scheduled publish: the incident's own batch op, sent as is.
 *
 * <p>A write a curator presses, so it says how it ended both ways (SH965), and it is not
 * cancelled (`read-options.ts`: aborting a write throws its answer away, not the write).</p>
 */
export function retrySchedule(op: string, address: string,
  say: (key: string, options?: Record<string, unknown>) => string) {
  feedService.retry(op, address)
    .then(() => toast.success(say("dock.retried", { page: address })))
    .catch((error: unknown) => reportProblem(error, say("common.error")))
}
