/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { recordRequest } from "@/lib/dock-chat"
import { toast } from "@viglet/viglet-design-system"
import { reportProblem, reportRefusal } from "@/lib/problem"
import { ShReviewService } from "@/services/review/review.service"
import { ShNoteService } from "@/services/note/note.service"

const noteService = new ShNoteService()
const reviewService = new ShReviewService()

type Say = (key: string, options?: Record<string, unknown>) => string

/**
 * SH1006 — a question from the dock, written as a `request` note on the page it is about.
 *
 * <p>Success shows as the question itself, in the transcript the curator is typing into; a
 * refusal goes to the dock as a report with the server's words (SH966).</p>
 */
export function sendRequest(objectId: string, text: string, say: Say, fieldPath?: string): Promise<boolean> {
  return noteService.add({ objectId, kind: "request", body: text, fieldPath })
    .then((note) => {
      recordRequest({ id: note.id, objectId, body: text, fieldPath })
      return true
    })
    .catch((error: unknown) => {
      reportRefusal(error, say("common.error"), { t: say })
      return false
    })
}

/**
 * SH1007 — approve, from the dock, the change a run made to the page a request was about. The
 * review queue's own decisions call, so the queue's permissions decide who may, and an approval
 * publishes the way it does from the queue.
 */
export function approveFromDock(run: string, postId: string, say: Say) {
  reviewService.decide(run, [{ postId, decision: "APPROVE" }])
    .then((outcome) => toast.success(say("review.decided", { count: outcome.changed })))
    .catch((error: unknown) => reportProblem(error, say("common.error")))
}
