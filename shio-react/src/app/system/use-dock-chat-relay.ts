/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useLocation } from "react-router-dom"
import { currentChat, recordReply, replyFor, useDockChat, waiting } from "@/lib/dock-chat"
import { isCancelled } from "@/lib/read-options"
import { ShNoteService } from "@/services/note/note.service"
import { contextOf } from "./bento-commands"
import { sendRequest } from "./dock-chat-send"

const noteService = new ShNoteService()

/** How often an unanswered request's thread is re-read. Only while one is waiting. */
export const REPLY_POLL_MS = 5000

/**
 * SH1006 — what the shell hands the dock so it can open into a chat with the connected agent.
 *
 * <p>An `onSend`, and only when two things hold: an agent run started on this tenant recently
 * (`GET /note/relay`), because a composer with nobody behind it would be a promise Shio cannot
 * keep, and the curator is on a page or a folder, because a request is a note and a note hangs on
 * something. Otherwise the dock stays the status surface SH964 made it.</p>
 *
 * <p>Replies are read by re-reading each waiting request's thread every `REPLY_POLL_MS`, and only
 * while one is waiting. Notes are not on the change feed, so SH967's stream never hears a reply;
 * a poll bounded to the seconds a curator is actually waiting costs less than putting notes on
 * every subscriber's stream to serve a chat most sessions never open.</p>
 */
export function useDockChatRelay(): { onSend?: (text: string) => void } {
  const { t } = useTranslation()
  const location = useLocation()
  const [agentActive, setAgentActive] = useState(false)
  const say = useRef((key: string, options?: Record<string, unknown>) => String(t(key, options)))
  useEffect(() => {
    say.current = (key, options) => String(t(key, options))
  }, [t])

  useEffect(() => {
    const abort = new AbortController()
    noteService.relay({ signal: abort.signal })
      .then((status) => setAgentActive(status.agentActive))
      .catch((error: unknown) => {
        if (!isCancelled(error)) setAgentActive(false)
      })
    return () => abort.abort()
  }, [])

  const chat = useDockChat()
  const pending = waiting(chat).length
  useEffect(() => {
    if (pending === 0) return
    const abort = new AbortController()
    const timer = setInterval(() => {
      for (const turn of waiting(currentChat())) {
        noteService.of(turn.objectId, { signal: abort.signal })
          .then((notes) => {
            const reply = replyFor(turn.requestId, notes)
            if (reply) recordReply(turn.requestId, { id: reply.id, body: reply.body })
          })
          // A missed poll is the next poll's job; nothing here is the curator's to act on.
          .catch(() => undefined)
      }
    }, REPLY_POLL_MS)
    return () => {
      clearInterval(timer)
      abort.abort()
    }
  }, [pending])

  const context = contextOf(location.pathname)
  const anchor = context.postId ?? context.folderId
  return agentActive && anchor
    ? { onSend: (text: string) => sendRequest(anchor, text, say.current) }
    : {}
}
