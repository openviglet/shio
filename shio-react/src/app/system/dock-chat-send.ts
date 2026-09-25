/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { recordRequest } from "@/lib/dock-chat"
import { reportRefusal } from "@/lib/problem"
import { ShNoteService } from "@/services/note/note.service"

const noteService = new ShNoteService()

type Say = (key: string, options?: Record<string, unknown>) => string

/**
 * SH1006 — a question from the dock, written as a `request` note on the page it is about.
 *
 * <p>Success shows as the question itself, in the transcript the curator is typing into; a
 * refusal goes to the dock as a report with the server's words (SH966).</p>
 */
export function sendRequest(objectId: string, text: string, say: Say) {
  noteService.add({ objectId, kind: "request", body: text })
    .then((note) => recordRequest({ id: note.id, objectId, body: text }))
    .catch((error: unknown) => reportRefusal(error, say("common.error"), { t: say }))
}
