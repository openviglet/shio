/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useSyncExternalStore } from "react"
import type { VigletAssistantChatMessage } from "@viglet/viglet-design-system"
import type { ShNote } from "../models/note/note.model.ts"

/**
 * SH1006 — the dock's conversation with the connected agent, as a curator sees it.
 *
 * <p>Shio hosts no model. A question sent from the dock is a {@code request} note on the page the
 * curator is on, which the agent reads from {@code shio://requests}, and the answer is its
 * {@code reply} under that note. This holds the requests this curator sent in this session and the
 * replies that have come back, and turns them into the dock's transcript. A module store like
 * `dock-reports.ts`, for the same reason: the shell sends and the dock renders.</p>
 */
export interface ChatTurn {
  requestId: string
  /** The page or folder the request hangs on, where its reply will appear. */
  objectId: string
  /** SH1019 — the field a request is about, when it asks for a value to put in one. */
  fieldPath?: string
  text: string
  at: number
  /** SH1007 — `run` is the agent session that wrote the reply, whose drafts answer it. */
  reply?: { id: string; text: string; at: number; seen: boolean; run?: string }
}

let turns: readonly ChatTurn[] = []
const listeners = new Set<() => void>()

function emit(next: readonly ChatTurn[]) {
  turns = next
  for (const listener of listeners) listener()
}

export function recordRequest(request: { id: string; objectId: string; body: string; fieldPath?: string }) {
  emit([...turns, { requestId: request.id, objectId: request.objectId, fieldPath: request.fieldPath,
    text: request.body, at: Date.now() }])
}

/** The reply to a request among a thread's notes: the first `reply` whose parent is the request. */
export function replyFor(requestId: string, notes: readonly ShNote[]): ShNote | undefined {
  return notes.find((note) => note.parentId === requestId && note.kind.toUpperCase() === "REPLY")
}

export function recordReply(requestId: string, reply: { id: string; body: string; run?: string }) {
  emit(turns.map((turn) => (turn.requestId === requestId && !turn.reply
    ? { ...turn, reply: { id: reply.id, text: reply.body, at: Date.now(), seen: false, run: reply.run } }
    : turn)))
}

/**
 * SH1007 — the run a reply answers with, or nothing. A reply written by an agent session carries
 * it (SH106); one written from a console session, or with none, is plain text.
 */
export function runOf(reply: ShNote): string | undefined {
  const session = reply.agentSession ?? undefined
  return session && !session.startsWith("console:") ? session : undefined
}

export function markRepliesSeen() {
  if (!turns.some((turn) => turn.reply && !turn.reply.seen)) return
  emit(turns.map((turn) => (turn.reply ? { ...turn, reply: { ...turn.reply, seen: true } } : turn)))
}

export function waiting(chat: readonly ChatTurn[]): ChatTurn[] {
  return chat.filter((turn) => !turn.reply)
}

/** The transcript, oldest first: each question, then its answer once it has one. */
export function transcript(chat: readonly ChatTurn[]): VigletAssistantChatMessage[] {
  return chat.flatMap((turn) => [
    { id: turn.requestId, role: "user" as const, text: turn.text },
    ...(turn.reply ? [{ id: turn.reply.id, role: "assistant" as const, text: turn.reply.text }] : []),
  ])
}

export function unseenReplies(chat: readonly ChatTurn[]): number {
  return chat.filter((turn) => turn.reply && !turn.reply.seen).length
}

export function currentChat(): readonly ChatTurn[] {
  return turns
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function useDockChat(): readonly ChatTurn[] {
  return useSyncExternalStore(subscribe, currentChat, currentChat)
}
