/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useState } from "react"
import { isCancelled } from "@/lib/read-options"
import { ShNoteService } from "@/services/note/note.service"

const noteService = new ShNoteService()

/**
 * SH1006/SH1019 — whether an agent run started on this tenant recently enough to answer.
 *
 * <p>Asked by every control that sends the connected agent a question: the dock's composer and a
 * field's "ask the agent". A control with nobody behind it would be a promise Shio cannot keep, so
 * both stay hidden until this says yes, and a failed read says no.</p>
 */
export function useAgentAvailable(): boolean {
  const [available, setAvailable] = useState(false)
  useEffect(() => {
    const abort = new AbortController()
    noteService.relay({ signal: abort.signal })
      .then((status) => setAvailable(status.agentActive))
      .catch((error: unknown) => {
        if (!isCancelled(error)) setAvailable(false)
      })
    return () => abort.abort()
  }, [])
  return available
}
