/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useSyncExternalStore } from "react"
import type { VigletAssistantReport } from "@viglet/viglet-design-system"

/**
 * SH966 — the reports the dock carries: outcomes a curator can act on, not just read.
 *
 * <p>A toast is a sentence that leaves. A refused save needs to stay until somebody reads it, and
 * to offer what the server suggested: go to the field, take the correction. VDS134 gave the dock a
 * `report` message kind for exactly this, with a tone, a read state and up to three actions. This
 * is the one place the console keeps them. A module-level store rather than a context, because a
 * page reports and the dock renders in two different subtrees, and a store needs no provider that
 * a test or a second shell could forget to mount.</p>
 *
 * <p>Ids and times are for the dock's own read and dismiss bookkeeping. Nothing is persisted: a
 * reload starts empty, the same as the toast list.</p>
 */
export type DockReport = VigletAssistantReport

let reports: readonly DockReport[] = []
const listeners = new Set<() => void>()
let next = 0

function emit(updated: readonly DockReport[]) {
  reports = updated
  for (const listener of listeners) listener()
}

/** The newest ten; a dock is not a log, and the activity page is. */
const KEPT = 10

/**
 * SH971 — the kinds this curator muted. A report of a muted kind is dropped unless it is a
 * failure: tone error always reaches the dock, because a failure is the report least safe to lose,
 * and muting never hides the record (the activity trail and the feed still carry it).
 */
let muted: ReadonlySet<string> = new Set()

export function setMutedKinds(kinds: readonly string[]) {
  muted = new Set(kinds)
  for (const listener of listeners) listener()
}

export function isMuted(kind: string): boolean {
  return muted.has(kind)
}

function currentMuted(): ReadonlySet<string> {
  return muted
}

export function useMutedKinds(): ReadonlySet<string> {
  return useSyncExternalStore(subscribe, currentMuted, currentMuted)
}

/**
 * SH971 — whether this curator froze the mascot. Only the motion stops: the caption still types
 * and the live region still announces, so nothing a report says is lost to a still dock.
 */
let still = false

export function setMascotStill(next: boolean) {
  still = next
  for (const listener of listeners) listener()
}

function currentStill(): boolean {
  return still
}

export function useMascotStill(): boolean {
  return useSyncExternalStore(subscribe, currentStill, currentStill)
}

/**
 * Whether a dock is on screen. The mascot already says what a toast says, so while one is mounted
 * the toast list is hidden rather than repeated beside it. A count, not a flag: the shell and the
 * visual editor each mount their own, and one unmounting must not bring the toasts back for the
 * other. Where no dock is mounted (login, setup), toasts are the only surface and stay visible.
 */
let docks = 0

export function registerDock(): () => void {
  docks += 1
  for (const listener of listeners) listener()
  return () => {
    docks -= 1
    for (const listener of listeners) listener()
  }
}

function currentDocked(): boolean {
  return docks > 0
}

export function useDockMounted(): boolean {
  return useSyncExternalStore(subscribe, currentDocked, currentDocked)
}

export function pushReport(report: Omit<DockReport, "id" | "role" | "at" | "read">, kind?: string): string | undefined {
  if (kind && muted.has(kind) && report.tone !== "error") return undefined
  next += 1
  const id = `report-${Date.now().toString(36)}-${next}`
  emit([...reports, { ...report, id, role: "report" as const, at: Date.now(), read: false }].slice(-KEPT))
  return id
}

export function markReportRead(id: string) {
  if (!reports.some((report) => report.id === id && !report.read)) return
  emit(reports.map((report) => (report.id === id ? { ...report, read: true } : report)))
}

export function dismissReport(id: string) {
  if (!reports.some((report) => report.id === id)) return
  emit(reports.filter((report) => report.id !== id))
}

export function currentReports(): readonly DockReport[] {
  return reports
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function useDockReports(): readonly DockReport[] {
  return useSyncExternalStore(subscribe, currentReports, currentReports)
}

/**
 * SH968 — what the system is doing right now, as opposed to what it reports afterwards. An agent
 * run that is writing sets `working` with its caption; each write it makes bumps `bumps`, so the
 * mascot reacts to smaller events than a state change. `null` when nothing is running.
 */
export interface DockAmbient {
  working: string | null
  bumps: number
}

let ambient: DockAmbient = { working: null, bumps: 0 }

function emitAmbient(next: DockAmbient) {
  ambient = next
  for (const listener of listeners) listener()
}

export function setWorking(caption: string | null) {
  if (ambient.working === caption) return
  emitAmbient({ ...ambient, working: caption })
}

export function bumpActivity() {
  emitAmbient({ ...ambient, bumps: ambient.bumps + 1 })
}

export function currentAmbient(): DockAmbient {
  return ambient
}

export function useDockAmbient(): DockAmbient {
  return useSyncExternalStore(subscribe, currentAmbient, currentAmbient)
}
