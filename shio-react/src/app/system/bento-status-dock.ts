/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { createElement, useEffect } from "react"
import { VigletAssistant, useAssistantNotifications } from "@viglet/viglet-design-system"
import { dismissReport, markReportRead, registerDock, useDockAmbient, useDockReports, useMascotStill, useMutedKinds } from "../../lib/dock-reports.ts"
import type { VigletAssistantMessage } from "@viglet/viglet-design-system"
import { markRepliesSeen, transcript, unseenReplies, useDockChat, waiting } from "../../lib/dock-chat.ts"

/**
 * SH964 — the Viglet assistant, mounted as a status surface in the shell's corner.
 *
 * <p>The console reports every outcome through the package's `toast`, about a hundred calls, and
 * none of them had to change: `useAssistantNotifications` reads the live toast list rather than
 * patching `toast`, so each one moves the mascot and becomes the dock's caption. There is no
 * `onSend`, so the dock has no composer and no chat. Shio hosts no model, and a chat box with
 * nothing behind it would be a promise the product cannot keep.</p>
 *
 * <p>The mascot is decorative and hidden from assistive technology, and the caption is a live
 * region, so each outcome is announced once. The shell renders this in its `dock` slot, in the
 * corner stack below back-to-top (VDS133), so neither covers the other.</p>
 *
 * <p>SH966 — and the reports `lib/dock-reports.ts` keeps, which is where a refusal goes instead of
 * a toast. While a toast is on screen it drives the mascot and caption. Otherwise state and caption
 * are left to the dock, which follows its newest unread report and is idle when there is none. So
 * a refused save turns the mascot to error, types the server's detail, and stays until read.</p>
 *
 * <p>Plain `.ts` with `createElement` on purpose: node's type stripping does not read JSX, and this
 * way `bento-status-dock.test.mjs` mounts the component itself rather than a copy of it.</p>
 */
/**
 * SH1006 — given `onSend`, the dock opens into a chat with the connected agent. The shell passes
 * it only while an agent is around and a page is open (`use-dock-chat-relay.ts`); without it the
 * dock has no composer, which is what SH964 made it.
 */
export function BentoStatusDock({ inline = false, onSend }: { inline?: boolean; onSend?: (text: string) => void } = {}) {
  const { state, caption, activity } = useAssistantNotifications()
  const reports = useDockReports()
  const { working, bumps } = useDockAmbient()
  // SH971 — with saves muted, only a failing toast moves the mascot, and the toast list is hidden
  // while a dock is mounted, so a muted success says nothing at all. That is what muting asks for.
  const muted = useMutedKinds()
  const still = useMascotStill()
  const chat = useDockChat()
  // While this is mounted the console's toasts are hidden: the mascot is the one notice (App.tsx).
  useEffect(registerDock, [])
  const toasting = state !== "idle" && (state === "error" || !muted.has("saves"))
  // A toast is the freshest thing and wins; then an agent run that is writing (SH968); then the
  // dock's own reading of its newest unread report, which it does when both are left undefined.
  return createElement(VigletAssistant, {
    state: toasting ? state : working ? "working" : undefined,
    caption: toasting ? caption : working ?? undefined,
    activity: activity + reports.length + bumps + chat.length,
    messages: conversation(chat, reports),
    // SH1006 — a question waits for its reply, and a reply behind a collapsed dock is unread.
    busy: waiting(chat).length > 0,
    unread: reports.filter((report) => !report.read).length + unseenReplies(chat),
    onSend,
    onOpenChange: (open: boolean) => { if (open) markRepliesSeen() },
    onRead: markReportRead,
    onDismiss: dismissReport,
    // SH972 — in flow, for a surface that docks it somewhere of its own (the visual editor).
    inline,
    // SH971 — a curator's still mascot (VDS171 forwards it to the avatar); the caption still reports.
    paused: still,
  })
}

/** Questions, answers and reports together, oldest first, the order the dock reads them in. */
function conversation(chat: ReturnType<typeof useDockChat>, reports: ReturnType<typeof useDockReports>)
  : VigletAssistantMessage[] {
  const timed: { at: number; message: VigletAssistantMessage }[] = []
  const said = transcript(chat)
  for (const turn of chat) {
    const question = said.find((message) => message.id === turn.requestId)
    if (question) timed.push({ at: turn.at, message: question })
    const answer = turn.reply && said.find((message) => message.id === turn.reply?.id)
    if (turn.reply && answer) timed.push({ at: turn.reply.at, message: answer })
  }
  for (const report of reports) timed.push({ at: new Date(report.at).getTime(), message: report })
  return timed.sort((a, b) => a.at - b.at).map((each) => each.message)
}
