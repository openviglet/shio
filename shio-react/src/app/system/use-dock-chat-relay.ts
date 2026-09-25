/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"
import { useLocation, useNavigate } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { pushReport } from "@/lib/dock-reports"
import { currentChat, recordReply, replyFor, runOf, useDockChat, waiting } from "@/lib/dock-chat"
import { requestApply } from "@/lib/field-suggestions"
import { ShNoteService } from "@/services/note/note.service"
import { contextOf } from "./bento-commands"
import { approveFromDock, sendRequest } from "./dock-chat-send"
import { useAgentAvailable } from "./use-agent-available"

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
  const navigate = useNavigate()
  const go = useRef(navigate)
  useEffect(() => {
    go.current = navigate
  }, [navigate])
  const agentActive = useAgentAvailable()
  const say = useRef((key: string, options?: Record<string, unknown>) => String(t(key, options)))
  useEffect(() => {
    say.current = (key, options) => String(t(key, options))
  }, [t])

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
            if (!reply) return
            const run = runOf(reply)
            recordReply(turn.requestId, { id: reply.id, body: reply.body, run })
            // SH1007 — an answer that drafted something arrives with the change attached: open it
            // in review, or approve it here. Approve goes through the queue's own decisions call
            // and its permissions, so the dock is never a way around review; rejecting needs the
            // reason a rejection must carry, which is written in the queue.
            // SH1019 — a request about a field is answered with the value itself, and the answer
            // offers to put it there: into the form, as an unsaved edit, never straight to the server.
            const field = turn.fieldPath
            if (field) {
              pushReport({
                tone: "attention",
                text: say.current("dock.suggestionReady", { field }),
                actions: [{
                  label: say.current("dock.applySuggestion", { field }),
                  onSelect: () => {
                    requestApply({ postId: turn.objectId, field, value: reply.body })
                    go.current(`${ROUTES.CONTENT_ROOT}/post/${turn.objectId}`)
                  },
                }],
              })
            }
            if (run) {
              pushReport({
                tone: "attention",
                text: say.current("dock.replyChange"),
                actions: [
                  { label: say.current("dock.openChange"),
                    onSelect: () => go.current(`${ROUTES.ADMIN_REVIEW}?session=${encodeURIComponent(run)}`) },
                  { label: say.current("dock.approveChange"),
                    onSelect: () => approveFromDock(run, turn.objectId, say.current) },
                ],
              })
            }
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
